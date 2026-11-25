# Automation broadcasts

LogSleuth can watch the live log stream for patterns you define and broadcast
each matching line to an automation app — Tasker, MacroDroid, or anything
that can receive an explicit intent. This turns "when my app logs X, do Y"
into a profile, without ADB, without root, and without polling.

## Setting it up

1. **Settings → Automation → Broadcast log matches** turns the watcher on.
   A low-priority notification shows while it runs; it keeps working after
   you leave the app and comes back after a reboot.
2. **Manage automation rules** opens the rule list. A rule has:
   - **Query** — substring (or regex with the switch) matched against the
     tag and the message. Empty matches every line.
   - **Tag** — optional substring constraint on the tag.
   - **Source app** — optional; only lines from that package match.
   - **Target app** — required. The broadcast is *explicit*: it is delivered
     only to this package, never to anyone else.
   - **Action** — the intent action the target listens for. The default is
     `io.github.shiaho777.logsleuth.app.LOG_MATCH`.
   - **Cooldown** — minimum seconds between two broadcasts of the same rule
     (0 = no limit), so a hot log line cannot flood your automation app.

## Receiving in Tasker

Create a profile: **Event → Intent Received**, action = the rule's action
(default above). The broadcast carries these string/int/long extras:

| Extra | Meaning |
| --- | --- |
| `rule_name` | Name of the rule that matched |
| `package` | Package of the app that logged the line (best effort) |
| `level` | `V`/`D`/`I`/`W`/`E`/`F` |
| `tag`, `message`, `raw` | The log line (text capped at 8000 chars) |
| `pid`, `tid`, `uid` | Process/thread/user ids (`uid` = -1 when unknown) |
| `time` | Log timestamp, epoch millis |
| `remote_endpoint` | Capture address for remote logs |

Example: a rule with query `FATAL EXCEPTION` targeting Tasker, plus a
Tasker profile that flashes an alert and saves `%message` to a file, gives
you a crash alarm for any app on the device.

## Privacy

Broadcasts are explicit and go only to the package you picked per rule.
Nothing is sent implicitly, nothing leaves the device, and the watcher is
off until you turn it on.
