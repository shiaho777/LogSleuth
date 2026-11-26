# Local MCP in Termux

Use an Agent running in Termux on the same Android phone to work with LogSleuth. It can query live and saved logs, record a session, add bookmarks, inspect crashes, and move reports between LogSleuth and the Termux transfer directory.

## Install and pair

1. Install a LogSleuth APK that includes **Settings → Local MCP**. Use the APK and bridge from the same [GitHub Release](https://github.com/shiaho777/LogSleuth/releases), or build both from the same source revision.
2. Install [Termux](https://github.com/termux/termux-app#installation) and Node.js 24 or later. In Termux, run `pkg upgrade` to update its runtime libraries, then `pkg install nodejs-lts` and check `node --version`. The release asset is named `logsleuth-mcp-<app-version>.tgz`. From the directory where you saved it, install it with `npm install --global ./logsleuth-mcp-<app-version>.tgz`, substituting the actual filename. Its runtime dependencies are bundled.
3. Open **Settings → Local MCP**, tap **Enable MCP**, and generate a pairing code. In Termux, run `logsleuth-mcp pair` and paste the code at the prompt within two minutes. Open LogSleuth again if Android asks you to approve device log access.
4. Run `logsleuth-mcp doctor` to check the connection, source, and recording state.

To build the bridge from a source checkout, use Node.js 24 and OpenSSL.

```sh
./scripts/package_mcp.sh /tmp/logsleuth-mcp-local.tgz
```

## Connect an Agent

Configure a stdio MCP server with command `logsleuth-mcp` and the single argument `serve`. For example, add this entry to [Kopi Agent’s Termux configuration](https://kopiaiagent.com/docs/user-guide/features/mcp/). Use the full path printed by `command -v logsleuth-mcp` if the Agent has a separate command search path.

If the Agent starts tools outside a normal Termux shell and reports a bad interpreter, use the full path from `command -v node` as the command. Use the installed `cli.mjs` and `serve` as its two arguments. `npm root -g` locates the directory containing `logsleuth-mcp/cli.mjs`.

```yaml
mcp_servers:
  logsleuth:
    command: logsleuth-mcp
    args: [serve]
```

Try these as separate instructions so you can operate the target app between steps.

1. “Check LogSleuth’s status and start a recording. Tell me the session ID.”
2. Perform the actions you want to inspect, then ask “Add a bookmark called ‘Opened settings’ and finish the recording.”
3. “Find the errors in this session. Cite the line number and read the surrounding lines for the most relevant one.”
4. “Export that session as a ZIP and give me the local path.”

For read-only exploration before granting device log access, ask the Agent to open the bundled example with `open_example` and query that session. Saved-session references are physical line numbers. Live references are sequence numbers in a particular source. Search results include the source ID and a cursor when more results are available.

## Files and access

- `logsleuth-mcp files` prints the private Termux transfer directory. Put a ZIP, TXT, or LOG there and pass its basename to `import_report`. Exports return their full local path. To share externally, move the file to a location you choose.
- Pair only an Agent you trust. Pairing permits reading logs, changing settings, and deleting LogSleuth data. **Stop MCP** closes the listener and saves a recording started by it. **Revoke pairing** also invalidates the saved credential. Re-enable MCP from the app after a device or app restart.
- The bridge uses the phone’s loopback address. Your Agent’s model configuration determines whether tool results are sent to a model provider. Use a locally running model for an offline analysis workflow.

## Troubleshooting

| Result | What to do |
| --- | --- |
| Disconnected | Open LogSleuth and enable MCP. For an interrupted write, run `doctor` or inspect the session before retrying. |
| Pairing expired or certificate changed | Generate a fresh code on the settings screen and run `pair` again. |
| Log access required or source unavailable | Return to LogSleuth, check [log access](/guide/log-access), and approve the Android prompt if shown. An already approved source can continue while you use another app. |
| Foreground required or user action required | Open LogSleuth to start the requested Android service or complete the system permission prompt. |
| Cursor expired | Run a new search against the active source. The live buffer retains a limited history. |
