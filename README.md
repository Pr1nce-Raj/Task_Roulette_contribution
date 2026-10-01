# 🎡 Task Roulette (Pro Edition)

A gamified focus-task manager built with **Java 17 (plain `HttpServer` + SQLite JDBC)** on the backend and **vanilla HTML5 / CSS3 / JavaScript** on the frontend. No external frontend frameworks or heavy dependencies!

---

## ✨ Features & Polish

| Feature | Details |
|---|---|
| 🗄️ **True SQLite Persistence** | All tasks, timestamps, and task completion logs persist in `taskroulette.db`. Survives server restarts. |
| 🔥 **Authentic Real Streak System** | Backed by a dedicated `completion_log` table. Tracks consecutive days mathematically. Displays current streak, best streak record, and an interactive **7-day activity calendar**. |
| 🎯 **High-DPI Retina Roulette** | Crisp canvas rendering with golden rim, vibrant slices, center jewel, and smooth quintic deceleration easing. |
| 🔊 **Web Audio Sound Effects** | Realistic ticking sounds as wheel slices pass the pointer, celebratory victory fanfare on win, and pleasant timer completion chimes (with 🔊/🔇 toggle). |
| 🎊 **Canvas Confetti Cannon** | Physics-based particle explosion when a spin lands or when a task is completed! |
| ⏱️ **Focus Timer with Presets** | Quick preset buttons (`5m Quick`, `10m Focus`, `15m Sprint`, `25m Pomodoro`, `45m Deep`) plus custom minute inputs. Real-time SVG circular countdown ring that shifts color (purple → amber → red). |
| 📋 **Task Management & Filters** | Filter tasks by `All`, `Active`, or `Completed`. "Clear completed" button. When Spin picks a task, it auto-scrolls to the task and highlights it with an "IN FOCUS" badge. |
| 🌓 **Dark & Light Modes** | Theme toggle with persistent `localStorage` preference and glassmorphic cards. |
| 📱 **Mobile Responsive** | Fully responsive from large monitors down to 375px / 320px mobile screens. |

---

## 🚀 How to Run

> **Requires Java 17+**

```powershell
# 1 — Compile backend
javac -cp "lib/sqlite-jdbc.jar" -d out src/TaskRouletteServer.java

# 2 — Run server (Windows)
java -cp "out;lib/sqlite-jdbc.jar" --enable-native-access=ALL-UNNAMED TaskRouletteServer

# (On macOS / Linux use colon ':' instead of semicolon ';')
java -cp "out:lib/sqlite-jdbc.jar" --enable-native-access=ALL-UNNAMED TaskRouletteServer

# 3 — Open browser
http://localhost:8080/
```

---

## 📡 REST API Reference

Base URL: `http://localhost:8080`

| Method | Endpoint | Request Body | Response Description |
|---|---|---|---|
| `GET` | `/api/tasks` | — | Array of tasks with `id`, `text`, `completed`, and `createdAt` |
| `POST` | `/api/tasks` | `{"text":"..."}` | Creates task, returns new task with 201 Created |
| `PUT` | `/api/tasks/{id}` | `{"completed":true}` or `{"text":"..."}` | Updates task completion / text and automatically updates real streak log |
| `DELETE` | `/api/tasks/{id}` | — | Deletes task with `{success: true}` |
| `DELETE` | `/api/tasks/completed` | — | Clears all completed tasks |
| `GET` | `/api/streak` | — | Returns `{count, bestStreak, completedToday, active, totalCompleted, recentDays:[...]}` |

---

## 📁 Project Architecture

```
c:/TASK/
├── src/
│   └── TaskRouletteServer.java   # Java 17 backend with SQLite JDBC & JSON engine
├── static/
│   └── index.html                # Single-page app (vanilla HTML/CSS/JS, Web Audio, Canvas)
├── lib/
│   └── sqlite-jdbc.jar           # Self-contained SQLite JDBC driver (3.36.0.3)
├── out/                          # Compiled Java bytecode
├── taskroulette.db               # SQLite database file (tasks + completion_log)
├── PROGRESS.md                   # Iteration & verification log
└── README.md
```
