# Progress Log

1. Set up backend server and SQLite database connection (`taskroulette.db`). PASS.
2. Implemented real streak calculation (consecutive calendar day tracking, gap reset, 7-day activity history). PASS.
3. Implemented HTML5 Canvas wheel with quintic easing and circular pointer. PASS.
4. User-configurable timer (1–180 minutes) with real-time SVG progress ring. PASS.
5. Full REST API with CORS headers and proper JSON error responses. PASS.
6. Verified local execution on port 8080. PASS.
7. Overhauled Roulette Wheel UX:
   - Fixed text overlap bug where long task names clipped under the center hub.
   - Implemented mathematically safe radial midpoint positioning (`midR`) with clamped `maxWidth`.
   - Added smart 2-line word wrapping for 2-4 tasks so labels are completely visible and clean.
   - Added upright text rotation flip so text is always right-side up regardless of angle.
8. Implemented Audio Volume Slider (Issue 1):
   - Added interactive range slider (0% to 100%, step 0.05) in header with luxury pill container.
   - Connected volume slider to Web Audio API Master GainNode (`masterGainNode.gain.value`).
   - Persisted user volume preference across sessions in `localStorage` (`tr_sound_volume`). PASS.
9. Implemented Task Priority / Tags (Issue 2):
   - Added `priority` column (`TEXT NOT NULL DEFAULT 'MED'`) to SQLite `tasks` table with backward compatibility migration.
   - Updated `POST /api/tasks` and `PUT /api/tasks/{id}` endpoints to validate and persist `HIGH`, `MED`, and `LOW` priorities.
   - Added priority selector dropdown next to Add Task input and rendered colored priority badges (red for HIGH, amber for MED, green for LOW) on task cards. PASS.
10. Implemented Weighted Roulette Wheel Based on Priority (Issue 3):
    - Configured priority weighting algorithm (HIGH: 3x, MED: 2x, LOW: 1x).
    - Dynamically calculated sector arc angles in `drawWheel()` based on priority weight distribution.
    - Synchronized wheel spin deceleration and landing physics so the slice directly under the ruby pointer always matches the selected winner.
    - Updated sector-pass sound ticker to track variable-width weighted sectors. PASS.
