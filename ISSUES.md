# 📋 GitHub Issues Roadmap for Task Roulette

You can copy and paste these directly into [GitHub Issues](https://github.com/dhirajkumar-09/Task_Roulette/issues/new) for project tracking, feature planning, and contributor onboarding.

---

### Issue 1: [Feature] Add Audio Volume Slider in UI
- **Label**: `enhancement`, `ui/ux`
- **Description**: Currently, sound can only be toggled ON or OFF (🔊/🔇). Add a volume range slider (0% to 100%) in the header settings modal so users can adjust ticking and chime volume levels without muting completely.
- **Tasks**:
  - [ ] Add `<input type="range" min="0" max="1" step="0.05">` in header.
  - [ ] Connect slider value to Web Audio API `gainNode.gain.value`.
  - [ ] Persist user volume preference in `localStorage`.

---

### Issue 2: [Feature] Add Task Priority / Tags (High, Medium, Low)
- **Label**: `enhancement`, `database`
- **Description**: Allow users to assign priorities (`HIGH`, `MED`, `LOW`) or tags (e.g., `#study`, `#work`, `#fitness`) when adding tasks.
- **Tasks**:
  - [x] Add `priority` column to SQLite `tasks` table (`ALTER TABLE tasks ADD COLUMN priority TEXT DEFAULT 'MED'`).
  - [x] Update POST & PUT endpoints to accept priority parameter.
  - [x] Add colored priority badges in frontend task cards.

---

### Issue 3: [Feature] Weighted Roulette Wheel Based on Priority
- **Label**: `enhancement`, `algorithm`
- **Description**: Make high-priority tasks have wider wheel sectors (higher probability of being picked) on the roulette wheel.
- **Tasks**:
  - [x] Calculate sector arc dynamically based on task priority weights (High: 3x, Med: 2x, Low: 1x).
  - [x] Update `drawWheel()` slice angles according to weight distribution.
  - [x] Adjust winning sector calculation logic to match weighted probability.

---

### Issue 4: [Feature] Sound Effect Options / Sound Themes
- **Label**: `enhancement`, `audio`
- **Description**: Provide multiple sound packs (e.g., Casino Roulette, 8-Bit Arcade, Soft Zen Chimes) instead of a single synthesizer sound set.
- **Tasks**:
  - [ ] Create Web Audio oscillator presets for each theme.
  - [ ] Add a sound theme selector dropdown in settings.
  - [ ] Save selected theme in `localStorage`.

---

### Issue 5: [Bug/Enhancement] Prevent Duplicate Task Names
- **Label**: `bug`, `validation`
- **Description**: Currently, duplicate task names can be added multiple times. Add validation to alert user if an identical incomplete task already exists.
- **Tasks**:
  - [ ] Add backend check in `handlePost` for existing active tasks with identical text.
  - [ ] Show friendly toast warning in UI if duplicate task is entered.

---

### Issue 6: [Feature] Export & Import Tasks (JSON / CSV Backup)
- **Label**: `enhancement`, `data`
- **Description**: Allow users to export all tasks and streak history to a `.json` or `.csv` file and import it back to another device.
- **Tasks**:
  - [x] Create `GET /api/export` endpoint returning full JSON dump.
  - [x] Create `POST /api/import` endpoint to load tasks into SQLite.
  - [x] Add Export/Import buttons in the UI settings panel.

---

### Issue 7: [Feature] Keyboard Shortcuts for Power Users
- **Label**: `enhancement`, `accessibility`
- **Description**: Enable keyboard navigation and shortcuts for faster task management and wheel spinning.
- **Tasks**:
  - [ ] `Spacebar` / `S`: Spin roulette wheel.
  - [ ] `T`: Start / Pause focus timer.
  - [ ] `R`: Reset timer.
  - [ ] `/`: Focus task input field.
  - [ ] `Escape`: Close modals and unfocus.

---

### Issue 8: [Feature] Browser Push Notifications when Focus Timer Finishes
- **Label**: `enhancement`, `notifications`
- **Description**: When the focus timer reaches `00:00`, send a native browser notification (via Notification API) so the user gets notified even if the browser tab is minimized or in the background.
- **Tasks**:
  - [ ] Request `Notification.requestPermission()` on first timer start.
  - [ ] Trigger `new Notification('Focus session complete! 🎉')` at `00:00`.
  - [ ] Play chime sound in background tab if allowed.

---

### Issue 9: [Feature] Task Edit In-Place (Rename Task)
- **Label**: `enhancement`, `ui/ux`
- **Description**: Allow double-clicking or clicking an edit icon ✏️ on an existing task card to edit its text without deleting and re-adding.
- **Tasks**:
  - [ ] Add ✏️ edit button to task action buttons.
  - [ ] Convert task title to editable inline input on click.
  - [ ] Send `PUT /api/tasks/{id}` with new text on Enter or blur.

---

### Issue 10: [Feature] Dark/Light Theme Auto-Sync with System Preference
- **Label**: `enhancement`, `ui/ux`
- **Description**: Automatically detect OS color scheme (`prefers-color-scheme: dark`) on first visit while preserving manual toggle overrides.
- **Tasks**:
  - [ ] Add `window.matchMedia('(prefers-color-scheme: dark)')` listener in frontend JS.
  - [ ] Update theme automatically if user hasn't explicitly set a preference in `localStorage`.

---

### Issue 11: [DevOps] Add Dockerfile and docker-compose.yml
- **Label**: `devops`, `infrastructure`
- **Description**: Containerize the Java application so anyone can run it with a single command without installing local JDK manually.
- **Tasks**:
  - [ ] Create multi-stage `Dockerfile` with Eclipse Temurin Java 17.
  - [ ] Add `docker-compose.yml` mapping port 8080 and mounting SQLite volume for database persistence.
  - [ ] Update `README.md` with Docker run instructions.

---

### Issue 12: [DevOps] Add GitHub Actions CI Workflow for Automated Compilation Tests
- **Label**: `ci/cd`, `automation`
- **Description**: Set up automated tests on pull requests and pushes to ensure code compiles and starts properly on Linux and Windows runners.
- **Tasks**:
  - [x] Create `.github/workflows/ci.yml`.
  - [x] Add steps to compile `TaskRouletteServer.java` with SQLite driver on JDK 17.
  - [x] Run automated API endpoint smoke tests using curl.

---

### Issue 13: [Feature] Daily & Weekly Productivity Stats Modal
- **Label**: `enhancement`, `analytics`
- **Description**: Add an analytics modal displaying total focus hours completed, average daily tasks done, and a monthly heatmap calendar.
- **Tasks**:
  - [ ] Create `GET /api/stats` endpoint summarizing completed tasks by month/week.
  - [ ] Design SVG / Canvas heatmap (similar to GitHub contributions graph).
  - [ ] Add "View Analytics 📊" button in header.

---

### Issue 14: [Security/Performance] Add Rate Limiting and Input Sanitization
- **Label**: `security`, `backend`
- **Description**: Protect endpoints against rapid spamming and sanitize text inputs against XSS and SQL injection.
- **Tasks**:
  - [ ] Enforce max character limit (150 chars) on task text in Java backend.
  - [ ] Add in-memory sliding window rate limiter in `TaskRouletteServer`.
  - [ ] Validate and escape HTML tags properly on server responses.

---

### Issue 15: [Feature] Pomodoro Break Interval (Short & Long Breaks)
- **Label**: `enhancement`, `timer`
- **Description**: Implement a complete Pomodoro cycle (25 min focus $\rightarrow$ 5 min short break $\rightarrow$ repeat 4x $\rightarrow$ 15 min long break) with automatic mode switching.
- **Tasks**:
  - [ ] Add state machine for `FOCUS`, `SHORT_BREAK`, and `LONG_BREAK`.
  - [ ] Add visual badge indicating current cycle (e.g., "🍅 Cycle 2 of 4").
  - [ ] Prompt user when break starts and ends with custom colors.
