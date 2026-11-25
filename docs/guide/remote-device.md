# Reading a second device (wireless ADB)

LogSleuth can stream logs from *another* Android device — a TV, a spare
phone, a tablet — over wireless ADB. The remote device needs no LogSleuth
install and no root: its own `adbd` serves the log stream, exactly like
`adb logcat` from a PC does.

## Setup

1. **On the remote device**: connect it to a PC and run `adb tcpip 5555`
   to enable the legacy TCP listener. Note the device's IP address.
2. **In LogSleuth**: Settings → Remote device → enter host and port →
   **Test connection**.
3. The remote screen may show **"Allow USB debugging?"** — accept it there
   (tick *Always allow* to skip it next time). LogSleuth presents its own
   key, kept in app-private storage.
4. Once the status says *Connected*, pick **Remote device (wireless ADB)**
   in the access-mode list. The stream, filters, recordings and crash
   detection now work on the remote device's logs.

## What you get

- Logs and UID-to-package attribution come from the remote device. An
  ambiguous UID stays unattributed. The stream, session and export show
  the remote endpoint.
- Recordings, crash detection and automation work with remote logs. A
  source change ends the current recording and saves it. When the remote
  link drops, the live view shows the fallback to local access if available.
  To resume remote capture, test the connection again in Settings.

## Limits

- Capture follows one device at a time.
- Remote capture uses the classic TCP ADB listener. Android's TLS pairing
  port and pairing codes require a separate implementation.
- Both devices must be reachable over the network. The connection goes
  directly between the devices.
