# Reading a second device (wireless ADB)

LogSleuth can stream logs from *another* Android device — a TV, a spare
phone, a tablet — over wireless ADB. The remote device needs no LogSleuth
install and no root: its own `adbd` serves the log stream, exactly like
`adb logcat` from a PC does.

## Setup

1. **On the remote device**: enable Developer options → Wireless debugging
   (Android 11+), or connect it once to a PC and run `adb tcpip 5555`.
   Note the IP address and port it shows.
2. **In LogSleuth**: Settings → Remote device → enter host and port →
   **Test connection**.
3. The remote screen may show **"Allow USB debugging?"** — accept it there
   (tick *Always allow* to skip it next time). LogSleuth presents its own
   key, kept in app-private storage.
4. Once the status says *Connected*, pick **Remote device (wireless ADB)**
   in the access-mode list. The stream, filters, recordings and crash
   detection now work on the remote device's logs.

## What you get

- The full buffer set with the uid column (the remote shell runs as uid
  2000), so per-app filtering works just like the Shizuku path.
- Recordings, sessions, crash/ANR detection and automation rules all apply
  to the remote stream unchanged.
- If the link drops, LogSleuth falls back to a local access path instead of
  blanking the stream; reconnect from Settings when the remote is back.

## Limits, honestly

- One remote device at a time — the engine owns a single logcat stream.
- The Android 11+ *pairing-code* flow (`adb pair`) is not implemented yet;
  use the legacy `adb tcpip` port, or pair once from a PC and then connect
  to the device's debugging port. Devices that only expose TLS-paired
  connections are rejected with a clear error.
- Both devices must be on the same network (or otherwise reachable);
  nothing is sent to any server — the connection is device-to-device.