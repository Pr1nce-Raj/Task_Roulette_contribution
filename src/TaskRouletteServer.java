import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.*;

public class TaskRouletteServer {

    static final int DEFAULT_PORT = 8080;
    static final String DB_URL = getDbUrl();

    static String getDbUrl() {
        String envDb = System.getenv("DB_PATH");
        if (envDb != null && !envDb.isBlank()) {
            return "jdbc:sqlite:" + envDb.trim();
        }
        return "jdbc:sqlite:taskroulette.db";
    }

    public static void main(String[] args) throws Exception {
        int port = DEFAULT_PORT;
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            try {
                port = Integer.parseInt(envPort.trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid PORT environment variable, using default: " + DEFAULT_PORT);
            }
        }

        initDb();
        HttpServer srv = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        srv.createContext("/api/tasks", new TasksHandler());
        srv.createContext("/api/streak", new StreakHandler());
        srv.createContext("/api/user", new UserHandler());
        srv.createContext("/", new StaticHandler());
        srv.setExecutor(null);
        srv.start();
        System.out.println("✅ Task Roulette running on 0.0.0.0:" + port);
    }

    static void initDb() throws Exception {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            // Tasks table with user_id for multi-user isolation
            s.execute("""
                CREATE TABLE IF NOT EXISTS tasks (
                  id          INTEGER PRIMARY KEY AUTOINCREMENT,
                  user_id     TEXT    NOT NULL DEFAULT 'default',
                  text        TEXT    NOT NULL,
                  completed   INTEGER NOT NULL DEFAULT 0,
                  created_at  TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
                  completed_at TEXT
                )""");

            try {
                s.execute("ALTER TABLE tasks ADD COLUMN user_id TEXT NOT NULL DEFAULT 'default'");
            } catch (SQLException ignored) {}

            try {
                s.execute("ALTER TABLE tasks ADD COLUMN completed_at TEXT");
            } catch (SQLException ignored) {}

            // Completion log table with user_id
            s.execute("""
                CREATE TABLE IF NOT EXISTS completion_log (
                  id             INTEGER PRIMARY KEY AUTOINCREMENT,
                  user_id        TEXT    NOT NULL DEFAULT 'default',
                  task_id        INTEGER,
                  task_text      TEXT,
                  completed_date TEXT NOT NULL,
                  completed_time TEXT NOT NULL
                )""");

            try {
                s.execute("ALTER TABLE completion_log ADD COLUMN user_id TEXT NOT NULL DEFAULT 'default'");
            } catch (SQLException ignored) {}

            // Users table
            s.execute("""
                CREATE TABLE IF NOT EXISTS users (
                  id          TEXT PRIMARY KEY,
                  name        TEXT NOT NULL,
                  created_at  TEXT NOT NULL DEFAULT (datetime('now','localtime')),
                  last_active TEXT NOT NULL DEFAULT (datetime('now','localtime'))
                )""");

            System.out.println("📦 SQLite DB ready: " + DB_URL);
        }
    }

    static Connection conn() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    static void cors(HttpExchange ex) {
        var h = ex.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type, X-User-Id");
    }

    static void json(HttpExchange ex, int status, String body) throws IOException {
        cors(ex);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, b.length);
        try (var os = ex.getResponseBody()) { os.write(b); }
    }

    static void err(HttpExchange ex, int status, String msg) throws IOException {
        json(ex, status, "{\"error\":\"" + esc(msg) + "\"}");
    }

    static String body(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    static String getUserId(HttpExchange ex) {
        String h = ex.getRequestHeaders().getFirst("X-User-Id");
        if (h != null && !h.isBlank()) {
            return h.trim();
        }
        String query = ex.getRequestURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2 && "userId".equalsIgnoreCase(pair[0])) {
                    try {
                        return java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8).trim();
                    } catch (Exception ignored) {}
                }
            }
        }
        return "default";
    }

    static String strField(String s, String k) {
        var m = Pattern.compile("\"" + k + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(s);
        return m.find() ? m.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : null;
    }

    static Boolean boolField(String s, String k) {
        var m = Pattern.compile("\"" + k + "\"\\s*:\\s*(true|false)").matcher(s);
        return m.find() ? Boolean.parseBoolean(m.group(1)) : null;
    }

    static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
                                 .replace("\n", "\\n").replace("\r", "\\r");
    }

    static String taskJson(int id, String text, boolean done, String createdAt) {
        return "{\"id\":" + id + ",\"text\":\"" + esc(text) + "\",\"completed\":" + done
                + ",\"createdAt\":\"" + esc(createdAt) + "\"}";
    }

    // ── /api/tasks ─────────────────────────────────────────────────────────────
    static class TasksHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            String method = ex.getRequestMethod();
            if ("OPTIONS".equals(method)) { cors(ex); ex.sendResponseHeaders(204, -1); return; }

            String path = ex.getRequestURI().getPath();
            String[] parts = path.split("/");
            String userId = getUserId(ex);

            if ("DELETE".equals(method) && parts.length >= 4 && "completed".equals(parts[3])) {
                try { deleteCompleted(ex, userId); } catch (Exception e) { err(ex, 500, e.getMessage()); }
                return;
            }

            boolean hasId = parts.length >= 4 && !parts[3].isBlank();
            int id = -1;
            if (hasId) {
                try { id = Integer.parseInt(parts[3]); }
                catch (NumberFormatException e) { err(ex, 400, "Invalid id"); return; }
            }

            try {
                switch (method) {
                    case "GET"    -> { if (hasId) getOne(ex, id, userId); else getAll(ex, userId); }
                    case "POST"   -> create(ex, userId);
                    case "PUT"    -> { if (!hasId) { err(ex, 400, "Missing id"); return; } update(ex, id, userId); }
                    case "DELETE" -> { if (!hasId) { err(ex, 400, "Missing id"); return; } delete(ex, id, userId); }
                    default       -> err(ex, 405, "Method not allowed");
                }
            } catch (Exception e) {
                e.printStackTrace();
                err(ex, 500, e.getMessage() != null ? e.getMessage() : "Internal error");
            }
        }

        void getAll(HttpExchange ex, String userId) throws Exception {
            var rows = new ArrayList<String>();
            try (var c = conn();
                 var ps = c.prepareStatement("SELECT id, text, completed, created_at FROM tasks WHERE user_id=? ORDER BY completed ASC, id DESC")) {
                ps.setString(1, userId);
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(taskJson(rs.getInt(1), rs.getString(2), rs.getInt(3) == 1, rs.getString(4)));
                    }
                }
            }
            json(ex, 200, "[" + String.join(",", rows) + "]");
        }

        void getOne(HttpExchange ex, int id, String userId) throws Exception {
            try (var c = conn();
                 var ps = c.prepareStatement("SELECT id, text, completed, created_at FROM tasks WHERE id=? AND user_id=?")) {
                ps.setInt(1, id);
                ps.setString(2, userId);
                var rs = ps.executeQuery();
                if (!rs.next()) { err(ex, 404, "Task not found"); return; }
                json(ex, 200, taskJson(rs.getInt(1), rs.getString(2), rs.getInt(3) == 1, rs.getString(4)));
            }
        }

        void create(HttpExchange ex, String userId) throws Exception {
            String b = body(ex);
            String text = strField(b, "text");
            if (text == null || text.isBlank()) { err(ex, 400, "text is required"); return; }
            text = text.strip();
            try (var c = conn();
                 var ps = c.prepareStatement(
                     "INSERT INTO tasks(user_id, text) VALUES(?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, userId);
                ps.setString(2, text);
                ps.executeUpdate();
                var keys = ps.getGeneratedKeys();
                int newId = keys.next() ? keys.getInt(1) : -1;
                json(ex, 201, taskJson(newId, text, false, LocalDate.now().toString()));
            }
        }

        void update(HttpExchange ex, int id, String userId) throws Exception {
            String b = body(ex);
            String newText = strField(b, "text");
            Boolean completed = boolField(b, "completed");

            try (var c = conn()) {
                String existingText = "";
                int currentStatus = 0;
                try (var ps = c.prepareStatement("SELECT text, completed FROM tasks WHERE id=? AND user_id=?")) {
                    ps.setInt(1, id);
                    ps.setString(2, userId);
                    var rs = ps.executeQuery();
                    if (!rs.next()) { err(ex, 404, "Task not found"); return; }
                    existingText = rs.getString(1);
                    currentStatus = rs.getInt(2);
                }

                if (newText != null && !newText.isBlank()) {
                    existingText = newText.strip();
                    try (var ps = c.prepareStatement("UPDATE tasks SET text=? WHERE id=? AND user_id=?")) {
                        ps.setString(1, existingText);
                        ps.setInt(2, id);
                        ps.setString(3, userId);
                        ps.executeUpdate();
                    }
                }

                if (completed != null) {
                    int newStatus = completed ? 1 : 0;
                    if (newStatus != currentStatus) {
                        if (newStatus == 1) {
                            try (var ps = c.prepareStatement("UPDATE tasks SET completed=1, completed_at=datetime('now','localtime') WHERE id=? AND user_id=?")) {
                                ps.setInt(1, id);
                                ps.setString(2, userId);
                                ps.executeUpdate();
                            }
                            try (var ps = c.prepareStatement("INSERT INTO completion_log(user_id, task_id, task_text, completed_date, completed_time) VALUES(?, ?, ?, date('now','localtime'), datetime('now','localtime'))")) {
                                ps.setString(1, userId);
                                ps.setInt(2, id);
                                ps.setString(3, existingText);
                                ps.executeUpdate();
                            }
                        } else {
                            try (var ps = c.prepareStatement("UPDATE tasks SET completed=0, completed_at=NULL WHERE id=? AND user_id=?")) {
                                ps.setInt(1, id);
                                ps.setString(2, userId);
                                ps.executeUpdate();
                            }
                            try (var ps = c.prepareStatement("DELETE FROM completion_log WHERE user_id=? AND task_id=? AND completed_date=date('now','localtime')")) {
                                ps.setString(1, userId);
                                ps.setInt(2, id);
                                ps.executeUpdate();
                            }
                        }
                    }
                }

                try (var ps = c.prepareStatement("SELECT id, text, completed, created_at FROM tasks WHERE id=? AND user_id=?")) {
                    ps.setInt(1, id);
                    ps.setString(2, userId);
                    var rs = ps.executeQuery();
                    if (rs.next()) {
                        json(ex, 200, taskJson(rs.getInt(1), rs.getString(2), rs.getInt(3) == 1, rs.getString(4)));
                    }
                }
            }
        }

        void delete(HttpExchange ex, int id, String userId) throws Exception {
            try (var c = conn();
                 var ps = c.prepareStatement("DELETE FROM tasks WHERE id=? AND user_id=?")) {
                ps.setInt(1, id);
                ps.setString(2, userId);
                if (ps.executeUpdate() == 0) err(ex, 404, "Task not found");
                else json(ex, 200, "{\"success\":true}");
            }
        }

        void deleteCompleted(HttpExchange ex, String userId) throws Exception {
            try (var c = conn();
                 var ps = c.prepareStatement("DELETE FROM tasks WHERE completed=1 AND user_id=?")) {
                ps.setString(1, userId);
                int count = ps.executeUpdate();
                json(ex, 200, "{\"success\":true,\"deleted\":" + count + "}");
            }
        }
    }

    // ── /api/streak (Per-user Streak Calculation) ───────────────────────────────
    static class StreakHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { cors(ex); ex.sendResponseHeaders(204, -1); return; }

            String userId = getUserId(ex);

            try (var c = conn()) {
                TreeSet<LocalDate> dates = new TreeSet<>();
                try (var ps = c.prepareStatement("SELECT DISTINCT completed_date FROM completion_log WHERE user_id=? ORDER BY completed_date ASC")) {
                    ps.setString(1, userId);
                    try (var rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String dStr = rs.getString(1);
                            if (dStr != null && !dStr.isBlank()) {
                                try { dates.add(LocalDate.parse(dStr)); } catch (Exception ignored) {}
                            }
                        }
                    }
                }

                int totalCompleted = 0;
                try (var ps = c.prepareStatement("SELECT COUNT(*) FROM completion_log WHERE user_id=?")) {
                    ps.setString(1, userId);
                    try (var rs = ps.executeQuery()) {
                        if (rs.next()) totalCompleted = rs.getInt(1);
                    }
                }

                LocalDate today = LocalDate.now();
                LocalDate yesterday = today.minusDays(1);
                boolean completedToday = dates.contains(today);

                int currentStreak = 0;
                if (completedToday) {
                    currentStreak = 1;
                    LocalDate check = yesterday;
                    while (dates.contains(check)) {
                        currentStreak++;
                        check = check.minusDays(1);
                    }
                } else if (dates.contains(yesterday)) {
                    currentStreak = 1;
                    LocalDate check = yesterday.minusDays(1);
                    while (dates.contains(check)) {
                        currentStreak++;
                        check = check.minusDays(1);
                    }
                } else {
                    currentStreak = 0;
                }

                int bestStreak = 0;
                int run = 0;
                LocalDate prev = null;
                for (LocalDate d : dates) {
                    if (prev != null && ChronoUnit.DAYS.between(prev, d) == 1) {
                        run++;
                    } else {
                        run = 1;
                    }
                    if (run > bestStreak) bestStreak = run;
                    prev = d;
                }
                if (currentStreak > bestStreak) bestStreak = currentStreak;

                StringBuilder daysJson = new StringBuilder("[");
                for (int i = 6; i >= 0; i--) {
                    LocalDate d = today.minusDays(i);
                    boolean isDone = dates.contains(d);
                    boolean isToday = i == 0;
                    String dayName = d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);

                    daysJson.append(String.format(
                        "{\"date\":\"%s\",\"day\":\"%s\",\"completed\":%b,\"isToday\":%b}",
                        d.toString(), dayName, isDone, isToday
                    ));
                    if (i > 0) daysJson.append(",");
                }
                daysJson.append("]");

                String lastDate = dates.isEmpty() ? "" : dates.last().toString();

                String res = String.format(
                    "{\"count\":%d,\"bestStreak\":%d,\"completedToday\":%b,\"active\":%b,\"lastDate\":\"%s\",\"totalCompleted\":%d,\"recentDays\":%s}",
                    currentStreak, bestStreak, completedToday, (currentStreak > 0), lastDate, totalCompleted, daysJson.toString()
                );

                json(ex, 200, res);
            } catch (Exception e) {
                e.printStackTrace();
                err(ex, 500, e.getMessage());
            }
        }
    }

    // ── /api/user (Profile management) ──────────────────────────────────────────
    static class UserHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { cors(ex); ex.sendResponseHeaders(204, -1); return; }

            String method = ex.getRequestMethod();
            String userId = getUserId(ex);

            try (var c = conn()) {
                if ("GET".equals(method)) {
                    String name = "User";
                    try (var ps = c.prepareStatement("SELECT name FROM users WHERE id=?")) {
                        ps.setString(1, userId);
                        var rs = ps.executeQuery();
                        if (rs.next()) {
                            name = rs.getString(1);
                        } else {
                            try (var ins = c.prepareStatement("INSERT OR IGNORE INTO users(id, name) VALUES(?, ?)")) {
                                ins.setString(1, userId);
                                ins.setString(2, "User");
                                ins.executeUpdate();
                            }
                        }
                    }
                    json(ex, 200, "{\"id\":\"" + esc(userId) + "\",\"name\":\"" + esc(name) + "\"}");
                } else if ("POST".equals(method)) {
                    String b = body(ex);
                    String name = strField(b, "name");
                    if (name == null || name.isBlank()) name = "User";
                    name = name.strip();

                    try (var ps = c.prepareStatement("INSERT OR REPLACE INTO users(id, name, last_active) VALUES(?, ?, datetime('now','localtime'))")) {
                        ps.setString(1, userId);
                        ps.setString(2, name);
                        ps.executeUpdate();
                    }
                    json(ex, 200, "{\"id\":\"" + esc(userId) + "\",\"name\":\"" + esc(name) + "\"}");
                } else {
                    err(ex, 405, "Method not allowed");
                }
            } catch (Exception e) {
                err(ex, 500, e.getMessage());
            }
        }
    }

    // ── Static Files ───────────────────────────────────────────────────────────
    static class StaticHandler implements HttpHandler {
        static final Map<String, String> MIME = Map.of(
            "html", "text/html; charset=UTF-8",
            "css",  "text/css; charset=UTF-8",
            "js",   "application/javascript; charset=UTF-8",
            "ico",  "image/x-icon",
            "png",  "image/png",
            "svg",  "image/svg+xml"
        );

        @Override public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            if ("/".equals(path)) path = "/index.html";
            String staticDir = System.getenv("STATIC_DIR");
            Path f;
            if (staticDir != null && !staticDir.isBlank()) {
                f = Paths.get(staticDir, path.startsWith("/") ? path.substring(1) : path);
            } else {
                f = Paths.get("static" + path);
                if (!Files.exists(f) || Files.isDirectory(f)) {
                    f = Paths.get("static", path.startsWith("/") ? path.substring(1) : path);
                }
            }
            if (!Files.exists(f) || Files.isDirectory(f)) { err(ex, 404, "Not found: " + path); return; }
            String ext = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : "";
            String mime = MIME.getOrDefault(ext, "application/octet-stream");
            byte[] data = Files.readAllBytes(f);
            cors(ex);
            ex.getResponseHeaders().set("Content-Type", mime);
            ex.sendResponseHeaders(200, data.length);
            try (var os = ex.getResponseBody()) { os.write(data); }
        }
    }
}
