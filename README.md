# 🎡 Task Roulette (Pro Edition)

A gamified focus-task manager built with **Java 17 (plain `HttpServer` + SQLite JDBC)** on the backend and **vanilla HTML5 / CSS3 / JavaScript** on the frontend. No heavy frameworks or external npm/node dependencies required!

---

## ✨ Features & Polish

| Feature | Details |
|---|---|
| 👤 **Multi-User Isolation** | Each user/profile has their own completely isolated tasks, roulette wheel, and daily streak. Users can rename and switch profiles seamlessly. |
| 🗄️ **True SQLite Persistence** | All tasks, timestamps, and task completion logs persist in `taskroulette.db`. Survives server restarts. |
| 🔥 **Authentic Real Streak System** | Backed by a dedicated `completion_log` table per user. Tracks consecutive days mathematically. Displays current streak, best streak record, and an interactive **7-day activity calendar**. |
| 🎯 **High-DPI Retina Roulette** | Crisp canvas rendering with golden rim, vibrant slices, center hub, zero-overlap text algorithm, and smooth quintic deceleration easing. |
| 🔊 **Web Audio Sound Effects** | Realistic ticking sounds as wheel slices pass the pointer, celebratory victory fanfare on win, and pleasant timer completion chimes (with 🔊/🔇 toggle). |
| 🎊 **Canvas Confetti Cannon** | Physics-based particle explosion when a spin lands or when a task is completed! |
| ⏱️ **Focus Timer with Presets** | Quick preset buttons (`5 min`, `10 min`, `15 min`, `25 min`, `45 min`) plus custom minute inputs. Real-time SVG circular countdown ring that shifts color (purple → amber → red). |
| 📋 **Task Management & Filters** | Filter tasks by `All`, `Active`, or `Completed`. "Clear completed" button. When Spin picks a task, it auto-scrolls to the task and highlights it with an "IN FOCUS" badge. |
| 🌓 **Dark & Light Modes** | Theme toggle with persistent `localStorage` preference and glassmorphic cards. |
| 📱 **Mobile Responsive** | Fully responsive from large monitors down to 375px mobile screens. |
| 🐳 **Docker & Render Ready** | Includes multi-stage Dockerfile (Eclipse Temurin 17) and automatic `PORT` binding (`0.0.0.0`) for instant cloud deployment. |

---

## 🚀 How to Run Locally

### Option A: Standard Java 17

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

### Option B: Using Docker

```bash
# Build Docker image
docker build -t task-roulette .

# Run container
docker run -p 8080:8080 task-roulette

# Open browser
http://localhost:8080/
```

---

## ☁️ Deployment on Render

1. Go to [Render Dashboard](https://dashboard.render.com/) and click **New +** $\rightarrow$ **Web Service**.
2. Connect your GitHub repository: `dhirajkumar-09/Task_Roulette`.
3. In service settings:
   - **Environment**: `Docker`
   - **Branch**: `main`
   - **Plan**: `Free`
4. Click **Create Web Service**.
5. Render will automatically build the Dockerfile and launch your application!

---

## 📡 REST API Reference

Base URL: `http://localhost:8080` (Supports `X-User-Id` header for per-user isolation)

| Method | Endpoint | Headers | Request Body | Response Description |
|---|---|---|---|---|
| `GET` | `/api/tasks` | `X-User-Id` | — | Array of tasks for the active user |
| `POST` | `/api/tasks` | `X-User-Id` | `{"text":"..."}` | Creates task for active user, returns 201 Created |
| `PUT` | `/api/tasks/{id}` | `X-User-Id` | `{"completed":true}` or `{"text":"..."}` | Updates task completion / text and automatically updates real streak log |
| `DELETE` | `/api/tasks/{id}` | `X-User-Id` | — | Deletes task with `{success: true}` |
| `DELETE` | `/api/tasks/completed` | `X-User-Id` | — | Clears all completed tasks for active user |
| `GET` | `/api/streak` | `X-User-Id` | — | Returns user streak `{count, bestStreak, completedToday, active, totalCompleted, recentDays:[...]}` |
| `GET` | `/api/user` | `X-User-Id` | — | Returns profile `{id, name}` |
| `POST` | `/api/user` | `X-User-Id` | `{"name":"..."}` | Updates display name for profile |

---

## 📁 Project Architecture

```
c:/TASK/
├── src/
│   └── TaskRouletteServer.java   # Java 17 backend with SQLite JDBC, multi-user, & JSON engine
├── static/
│   └── index.html                # Single-page app (vanilla HTML/CSS/JS, Web Audio, Canvas)
├── lib/
│   └── sqlite-jdbc.jar           # Self-contained SQLite JDBC driver (3.36.0.3)
├── Dockerfile                    # Multi-stage Docker build for Render / container deployments
├── .dockerignore                 # Docker build exclusions
├── taskroulette.db               # SQLite database file (tasks + completion_log + users)
├── README.md                     # Documentation & setup guide
└── PROGRESS.md                   # Iteration & verification log
```
