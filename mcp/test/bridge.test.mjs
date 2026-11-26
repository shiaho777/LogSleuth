import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdtemp, mkdir, readFile, rm, symlink, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { once } from "node:events";
import { createServer } from "node:tls";
import { test } from "node:test";
import { Client } from "@modelcontextprotocol/client";
import { StdioClientTransport } from "@modelcontextprotocol/client/stdio";

const taskDir = await mkdtemp(join(tmpdir(), "logsleuth-mcp-"));
process.env.XDG_CONFIG_HOME = taskDir;
const { invoke, parsePairing, transferDirectory } = await import("../bridge.mjs");

test("the console exposes typed tools to both MCP protocol eras", async () => {
  for (const mode of ["legacy", "auto"]) {
    const transport = new StdioClientTransport({
      command: process.execPath,
      args: [fileURLToPath(new URL("../cli.mjs", import.meta.url)), "serve"],
      env: { ...process.env },
    });
    const client = new Client({ name: "test", version: "1.0.0" }, { versionNegotiation: { mode } });
    try {
      await client.connect(transport);
      assert.equal(client.getProtocolEra(), mode === "legacy" ? "legacy" : "modern");
      const { tools } = await client.listTools();
      assert.ok(tools.length >= 40);
      assert.ok(tools.find(tool => tool.name === "search_logs").annotations.readOnlyHint);
      assert.ok(tools.find(tool => tool.name === "delete_session").annotations.destructiveHint);
      const response = await client.callTool({ name: "status", arguments: {} });
      assert.ok(response.isError);
      assert.match(response.content[0].text, /UNPAIRED/);
    } finally {
      await client.close();
    }
  }
});

test("pairing strings require a certificate pin and a one-time secret", () => {
  const pin = "2".repeat(64);
  const code = "e".repeat(48);
  assert.deepEqual(parsePairing("lsmcp1." + pin + "." + code), { fingerprint: pin, code });
  assert.throws(() => parsePairing("lsmcp1." + code), { code: "INVALID_PAIRING" });
});

test("verify the certificate before sending credentials and confine file exchange", async () => {
  const key = join(taskDir, "key");
  const cert = join(taskDir, "cert");
  execFileSync("openssl", ["req", "-x509", "-newkey", "ec", "-pkeyopt", "ec_paramgen_curve:P-256",
    "-nodes", "-subj", "/CN=LogSleuth", "-days", "1", "-keyout", key, "-out", cert]);
  const pem = await readFile(cert, "utf8");
  const der = Buffer.from(pem.replace(/-----[^-]+-----|\s+/g, ""), "base64");
  const fingerprint = createHash("sha256").update(der).digest("hex");
  const contents = Buffer.from("example logs\n".repeat(4_000));
  let received = 0;
  let uploaded = Buffer.alloc(0);
  const sockets = new Set();
  const server = createServer({ cert: pem, key: await readFile(key) }, socket => {
    sockets.add(socket);
    socket.on("error", () => {});
    socket.on("close", () => sockets.delete(socket));
    let buffer = Buffer.alloc(0);
    socket.on("data", chunk => {
      buffer = Buffer.concat([buffer, chunk]);
      while (buffer.length >= 4 && buffer.length >= 4 + buffer.readUInt32BE()) {
        const length = buffer.readUInt32BE();
        const request = JSON.parse(buffer.subarray(4, 4 + length));
        buffer = buffer.subarray(4 + length);
        received++;
        assert.equal(request.token, "d".repeat(64));
        const args = request.arguments;
        const result = {
          status: () => ({ live: true }),
          export_session: () => ({ transferId: "1", filename: "example.txt", size: contents.length }),
          transfer_read: () => ({ data: contents.subarray(args.offset, args.offset + 32_768).toString("base64") }),
          transfer_begin: () => ({ transferId: "2" }),
          transfer_write: () => {
            assert.equal(uploaded.length, args.offset);
            uploaded = Buffer.concat([uploaded, Buffer.from(args.data, "base64")]);
            return { offset: uploaded.length };
          },
          transfer_commit: () => ({ sessionId: "9" }),
          transfer_close: () => ({}),
        }[request.tool]();
        const body = Buffer.from(JSON.stringify({ result }));
        const header = Buffer.alloc(4);
        header.writeUInt32BE(body.length);
        socket.write(header.subarray(0, 2));
        socket.write(Buffer.concat([header.subarray(2), body]));
      }
    });
  });
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  const port = server.address().port;
  const token = "d".repeat(64);
  try {
    await assert.rejects(invoke({ fingerprint: "0".repeat(64), token }, "status", {}, { port }), { code: "IDENTITY_CHANGED" });
    assert.equal(received, 0);
    const config = { fingerprint, token };
    assert.deepEqual(await invoke(config, "status", {}, { port }), { live: true });
    const exportResult = await invoke(config, "export_session", { sessionId: "1" }, { port });
    assert.deepEqual(await readFile(exportResult.path), contents);
    await mkdir(transferDirectory, { recursive: true });
    await writeFile(join(transferDirectory, "input.txt"), contents);
    await symlink(exportResult.path, join(transferDirectory, "linked.txt"));
    await assert.rejects(invoke(config, "import_report", { filename: "../input.txt" }, { port }), { code: "INVALID_FILENAME" });
    await assert.rejects(invoke(config, "import_report", { filename: "linked.txt" }, { port }), { code: "FILE_UNAVAILABLE" });
    assert.deepEqual(await invoke(config, "import_report", { filename: "input.txt" }, { port }), { sessionId: "9" });
    assert.deepEqual(uploaded, contents);
  } finally {
    for (const socket of sockets) socket.destroy();
    server.close();
  }
});

test.after(async () => rm(taskDir, { recursive: true, force: true }));
