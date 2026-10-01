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

    static final int PORT = 8080;
    static final String DB_URL = "jdbc:sqlite:taskroulette.db";

    public static void main(String[] args) throws Exception {
        initDb();
        HttpServer srv = HttpServer.create(new InetSocketAddress(PORT), 0);
        srv.createContext("/api/tasks", new TasksHandler());
        srv.createContext("/api/streak", new StreakHandler());
        srv.createContext("/", new StaticHandler());
        srv.setExecutor(null);
        srv.start();
        System.out.println("✅ Task Roulette running → http://localhost:" + PORT);
    }

    static void initDb() throws Exception {
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS tasks (
                  id          INTEGER PRIMARY KEY AUTOINCREMENT,
                  text        TEXT    NOT NULL,
                  completed   INTEGER NOT NULL DEFAULT 0,
                  created_at  TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
                  completed_at TEXT
                )""");

            try {
                s.execute("ALTER TABLE tasks ADD COLUMN completed_at TEXT");
            } catch (SQLException ignored) {
                // Column already exists
            }

            s.execute("""
                CREATE TABLE IF NOT EXISTS completion_log (
                  id             INTEGER PRIMARY KEY AUTOINCREMENT,
                  task_id        INTEGER,
                  task_text      TEXT,
                  completed_date TEXT NOT NULL,
                  completed_time TEXT NOT NULL
                )""");

            // Migrate if tasks already exist with completed=1 but no completion_log entries
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM completion_log")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    s.execute("""
                        INSERT INTO completion_log (task_id, task_text, completed_date, completed_time)
                        SELECT id, text, date('now','localtime'), datetime('now','localtime')
                        FROM tasks WHERE completed = 1
                    """);
                }
            }

            System.out.println("📦 SQLite DB ready: taskroulette.db");
        }
    }

    static Connection conn() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    static void cors(HttpExchange ex) {
        var h = ex.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type");
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

            String path = ex.getRequestURI().getPath(); // /api/tasks or /api/tasks/5 or /api/tasks/completed
            String[] parts = path.split("/");

            if ("DELETE".equals(method) && parts.length >= 4 && "completed".equals(parts[3])) {
                try { deleteCompleted(ex); } catch (Exception e) { err(ex, 500, e.getMessage()); }
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
                    case "GET"    -> { if (hasId) getOne(ex, id); else getAll(ex); }
                    case "POST"   -> create(ex);
                    case "PUT"    -> { if (!hasId) { err(ex, 400, "Missing id"); return; } update(ex, id); }
                    case "DELETE" -> { if (!hasId) { err(ex, 400, "Missing id"); return; } delete(ex, id); }
                    default       -> err(ex, 405, "Method not allowed");
                }
            } catch (Exception e) {
                e.printStackTrace();
                err(ex, 500, e.getMessage() != null ? e.getMessage() : "Internal error");
            }
        }

        void getAll(HttpExchange ex) throws Exception {
            var rows = new ArrayList<String>();
            try (var c = conn();
                 var rs = c.createStatement()
                           .executeQuery("SELECT id, text, completed, created_at FROM tasks ORDER BY completed ASC, id DESC")) {
                while (rs.next()) {
                    rows.add(taskJson(rs.getInt(1), rs.getString(2), rs.getInt(3) == 1, rs.getString(4)));
                }
            }
            json(ex, 200, "[" + String.join(",", rows) + "]");
        }

        void getOne(HttpExchange ex, int id) throws Exception {
            try (var c = conn();
                 var ps = c.prepareStatement("SELECT id, text, completed, created_at FROM tasks WHERE id=?")) {
                ps.setInt(1, id);
                var rs = ps.executeQuery();
                if (!rs.next()) { err(ex, 404, "Task not found"); return; }
                json(ex, 200, taskJson(rs.getInt(1), rs.getString(2), rs.getInt(3) == 1, rs.getString(4)));
            }
        }

        void create(HttpExchange ex) throws Exception {
            String b = body(ex);
            String text = strField(b, "text");
            if (text == null || text.isBlank()) { err(ex, 400, "text is required"); return; }
            text = text.strip();
            try (var c = conn();
                 var ps = c.prepareStatement(
                     "INSERT INTO tasks(text) VALUES(?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, text);
                ps.executeUpdate();
                var keys = ps.getGeneratedKeys();
                int newId = keys.next() ? keys.getInt(1) : -1;
                json(ex, 201, taskJson(newId, text, false, LocalDate.now().toString()));
            }
        }

        void update(HttpExchange ex, int id) throws Exception {
            String b = body(ex);
            String newText = strField(b, "text");
            Boolean completed = boolField(b, "completed");

            try (var c = conn()) {
                String existingText = "";
                int currentStatus = 0;
                try (var ps = c.prepareStatement("SELECT text, completed FROM tasks WHERE id=?")) {
                    ps.setInt(1, id);
                    var rs = ps.executeQuery();
                    if (!rs.next()) { err(ex, 404, "Task not found"); return; }
                    existingText = rs.getString(1);
                    currentStatus = rs.getInt(2);
                }

                if (newText != null && !newText.isBlank()) {
                    existingText = newText.strip();
                    try (var ps = c.prepareStatement("UPDATE tasks SET text=? WHERE id=?")) {
                        ps.setString(1, existingText);
                        ps.setInt(2, id);
                        ps.executeUpdate();
                    }
                }

                if (completed != null) {
                    int newStatus = completed ? 1 : 0;
                    if (newStatus != currentStatus) {
                        if (newStatus == 1) {
                            try (var ps = c.prepareStatement("UPDATE tasks SET completed=1, completed_at=datetime('now','localtime') WHERE id=?")) {
                                ps.setInt(1, id);
                                ps.executeUpdate();
                            }
                            try (var ps = c.prepareStatement("INSERT INTO completion_log(task_id, task_text, completed_date, completed_time) VALUES(?, ?, date('now','localtime'), datetime('now','localtime'))")) {
                                ps.setInt(1, id);
                                ps.setString(2, existingText);
                                ps.executeUpdate();
                            }
                        } else {
                            try (var ps = c.prepareStatement("UPDATE tasks SET completed=0, completed_at=NULL WHERE id=?")) {
                                ps.setInt(1, id);
                                ps.executeUpdate();
                            }
                            // remove today's completion entry for this specific task
                            try (var ps = c.prepareStatement("DELETE FROM completion_log WHERE task_id=? AND completed_date=date('now','localtime')")) {
                                ps.setInt(1, id);
                                ps.executeUpdate();
                            }
                        }
                    }
                }

                try (var ps = c.prepareStatement("SELECT id, text, completed, created_at FROM tasks WHERE id=?")) {
                    ps.setInt(1, id);
                    var rs = ps.executeQuery();
                    if (rs.next()) {
                        json(ex, 200, taskJson(rs.getInt(1), rs.getString(2), rs.getInt(3) == 1, rs.getString(4)));
                    }
                }
            }
        }

        void delete(HttpExchange ex, int id) throws Exception {
            try (var c = conn();
                 var ps = c.prepareStatement("DELETE FROM tasks WHERE id=?")) {
                ps.setInt(1, id);
                if (ps.executeUpdate() == 0) err(ex, 404, "Task not found");
                else json(ex, 200, "{\"success\":true}");
            }
        }

        void deleteCompleted(HttpExchange ex) throws Exception {
            try (var c = conn();
                 var s = c.createStatement()) {
                int count = s.executeUpdate("DELETE FROM tasks WHERE completed=1");
                json(ex, 200, "{\"success\":true,\"deleted\":" + count + "}");
            }
        }
    }

    // ── /api/streak (REAL Mathematical Consecutive Day Calculation) ────────────
    static class StreakHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { cors(ex); ex.sendResponseHeaders(204, -1); return; }

            try (var c = conn()) {
                // 1. Fetch all distinct completion dates
                TreeSet<LocalDate> dates = new TreeSet<>();
                try (var s = c.createStatement();
                     var rs = s.executeQuery("SELECT DISTINCT completed_date FROM completion_log ORDER BY completed_date ASC")) {
                    while (rs.next()) {
                        String dStr = rs.getString(1);
                        if (dStr != null && !dStr.isBlank()) {
                            try { dates.add(LocalDate.parse(dStr)); } catch (Exception ignored) {}
                        }
                    }
                }

                // 2. Total completed count
                int totalCompleted = 0;
                try (var s = c.createStatement();
                     var rs = s.executeQuery("SELECT COUNT(*) FROM completion_log")) {
                    if (rs.next()) totalCompleted = rs.getInt(1);
                }

                LocalDate today = LocalDate.now();
                LocalDate yesterday = today.minusDays(1);
                boolean completedToday = dates.contains(today);

                // 3. Calculate current streak
                int currentStreak = 0;
                if (completedToday) {
                    currentStreak = 1;
                    LocalDate check = yesterday;
                    while (dates.contains(check)) {
                        currentStreak++;
                        check = check.minusDays(1);
                    }
                } else if (dates.contains(yesterday)) {
                    // Streak alive from yesterday, awaiting today's completion!
                    currentStreak = 1;
                    LocalDate check = yesterday.minusDays(1);
                    while (dates.contains(check)) {
                        currentStreak++;
                        check = check.minusDays(1);
                    }
                } else {
                    currentStreak = 0; // Broken streak or brand new
                }

                // 4. Calculate best streak across all history
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

                // 5. Build 7-day activity history (from today - 6 to today)
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
            if (path.contains("..")) { err(ex, 400, "Bad path"); return; }
            Path f = Paths.get("static" + path);
            if (!Files.exists(f) || Files.isDirectory(f)) { err(ex, 404, "Not found"); return; }
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
