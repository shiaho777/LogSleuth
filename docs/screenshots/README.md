# Screenshots

This directory holds the 6 screenshots referenced from the main README files.
They are real emulator captures (1080×2400, API 35, light theme, demo status
bar). Keep the same filenames when replacing them so the README links keep
working.

| File | What it shows |
| --- | --- |
| `01-live-stream.png` | Live logcat stream, level-colored rows scrolling |
| `02-filters.png` | Expanded filter panel + saved preset chips |
| `03-crashes.png` | Crashes & ANRs list with crash cards |
| `04-sessions.png` | Recording sessions list |
| `05-session-replay.png` | Session detail — Logs tab replay with filter applied |
| `06-sdk-sample.png` | Sample app sharing a captured SDK zip |

Capture guidelines:
- Size: 1080×2400 (portrait, ~9:20 aspect) PNG.
- Real device preferred over emulator (cleaner status bar); on the emulator,
  use demo mode (`adb shell settings put global sysui_demo_allowed 1`, then
  `am broadcast -a com.android.systemui.demo -e command …`) for a clean bar.
- Keep theme (light vs dark) consistent across all 6 shots.

The old branded placeholders can be regenerated with:
```bash
python3 scripts/make_placeholders.py
```
