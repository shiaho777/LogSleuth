import { createHash, randomUUID, timingSafeEqual } from "node:crypto";
import { constants } from "node:fs";
import { mkdir, open, rename, unlink } from "node:fs/promises";
import { homedir } from "node:os";
import { join } from "node:path";
import { connect } from "node:tls";
import { once } from "node:events";

const configDirectory = join(process.env.XDG_CONFIG_HOME || join(homedir(), ".config"), "logsleuth-mcp");
const configFile = join(configDirectory, "pairing.json");
export const transferDirectory = join(configDirectory, "files");
const fingerprintPattern = /^[a-f0-9]{64}$/;
const pathnamePattern = /^[^/\\\u0000-\u001f\u007f]{1,150}\.(zip|txt|log)$/iu;
const maxFrame = 65_536;
const maxFileSize = 256 * 1024 * 1024;
const chunkSize = 32_768;

export class BridgeError extends Error {
  constructor(code, message) {
    super(message);
    this.code = code;
  }
}

export function parsePairing(value) {
  const match = /^lsmcp1\.([a-f0-9]{64})\.([a-f0-9]{48})$/u.exec(value.trim());
  if (!match) throw new BridgeError("INVALID_PAIRING", "Copy a fresh pairing string from LogSleuth.");
  return { fingerprint: match[1], code: match[2] };
}

export async function getPairing() {
  let file;
  try {
    file = await open(configFile, constants.O_RDONLY | constants.O_NOFOLLOW);
    const info = await file.stat();
    if (!info.isFile() || info.size > 1024 || (info.mode & 0o077) !== 0) {
      throw new BridgeError("INVALID_PAIRING", "Run logsleuth-mcp pair to restore the private configuration.");
    }
    const result = JSON.parse(await file.readFile({ encoding: "utf8" }));
    if (fingerprintPattern.test(result.fingerprint) && fingerprintPattern.test(result.token)) return result;
  } catch (error) {
    if (error instanceof BridgeError) throw error;
    if (error.code !== "ENOENT" && !(error instanceof SyntaxError)) {
      throw new BridgeError("PAIRING_UNAVAILABLE", "The private pairing configuration cannot be read.");
    }
  } finally {
    await file?.close();
  }
  throw new BridgeError("UNPAIRED", "Generate a pairing string in LogSleuth and run logsleuth-mcp pair.");
}

export async function pair(value) {
  const { fingerprint, code } = parsePairing(value);
  const result = await withBridge({ fingerprint }, connection => connection.call("pair", { code }));
  if (!fingerprintPattern.test(result.token)) throw new BridgeError("INVALID_RESPONSE", "LogSleuth returned an invalid credential.");
  await mkdir(configDirectory, { recursive: true, mode: 0o700 });
  const tmp = join(configDirectory, randomUUID());
  const file = await open(tmp, "wx", 0o600);
  try {
    try {
      await file.writeFile(JSON.stringify({ fingerprint, token: result.token }) + "\n");
    } finally {
      await file.close();
    }
    await rename(tmp, configFile);
  } catch (error) {
    await unlink(tmp).catch(() => {});
    throw error;
  }
}

async function withBridge(config, run, { signal, port = 47137 } = {}) {
  const socket = connect({
    host: "127.0.0.1", port, minVersion: "TLSv1.2",
    rejectUnauthorized: false,
  });
  let buffer = Buffer.alloc(0);
  let pending;
  let failure;
  const reject = error => {
    failure ??= error;
    const waiting = pending;
    pending = undefined;
    waiting?.reject(failure);
  };
  const offline = () => new BridgeError("DISCONNECTED", "Enable LogSleuth MCP on this phone. Check status before retrying a write.");
  socket.on("error", reject);
  socket.on("close", () => reject(offline()));
  socket.setTimeout(30_000, () => socket.destroy(offline()));
  const abort = () => socket.destroy(new BridgeError("CANCELLED", "The request was cancelled. Check state before retrying a write."));
  signal?.addEventListener("abort", abort, { once: true });
  if (signal?.aborted) abort();
  socket.on("data", chunk => {
    if (!pending || (buffer.length + chunk.length) > maxFrame + 4) {
      socket.destroy(new BridgeError("INVALID_RESPONSE", "Unexpected bridge data."));
      return;
    }
    buffer = Buffer.concat([buffer, chunk]);
    if (buffer.length < 4) return;
    const length = buffer.readUInt32BE(0);
    if (length < 1 || length > maxFrame || buffer.length > length + 4) {
      socket.destroy(new BridgeError("INVALID_RESPONSE", "Invalid response frame."));
      return;
    }
    if (buffer.length !== length + 4) return;
    const waiting = pending;
    pending = undefined;
    try {
      const reply = JSON.parse(buffer.subarray(4).toString("utf8"));
      if (reply.error && typeof reply.error.code === "string" && typeof reply.error.message === "string") {
        waiting.reject(new BridgeError(reply.error.code, reply.error.message));
      } else if (reply.result && !Array.isArray(reply.result) && typeof reply.result === "object") {
        waiting.resolve(reply.result);
      } else {
        waiting.reject(new BridgeError("INVALID_RESPONSE", "Unknown response envelope."));
      }
    } catch {
      waiting.reject(new BridgeError("INVALID_RESPONSE", "LogSleuth returned invalid JSON."));
    }
    buffer = Buffer.alloc(0);
  });
  try {
    await once(socket, "secureConnect");
    const certificate = socket.getPeerCertificate().raw;
    const expected = Buffer.from(config.fingerprint, "hex");
    if (!certificate || expected.length !== 32 ||
        !timingSafeEqual(createHash("sha256").update(certificate).digest(), expected)) {
      throw new BridgeError("IDENTITY_CHANGED", "The local certificate changed. Pair again from LogSleuth.");
    }
    return await run({
      call(tool, args = {}) {
        if (failure) return Promise.reject(failure);
        if (pending) return Promise.reject(new BridgeError("BUSY", "Another request is in progress."));
        const bytes = Buffer.from(JSON.stringify({ version: 1, tool, arguments: args, token: config.token }));
        if (bytes.length > maxFrame) return Promise.reject(new BridgeError("REQUEST_TOO_LARGE", "Reduce the size of the request."));
        const frame = Buffer.allocUnsafe(bytes.length + 4);
        frame.writeUInt32BE(bytes.length);
        bytes.copy(frame, 4);
        return new Promise((resolve, reject) => {
          pending = { resolve, reject };
          socket.write(frame);
        });
      },
    });
  } catch (error) {
    if (error instanceof BridgeError) throw error;
    if (["ECONNRESET", "ECONNREFUSED", "EPIPE", "ERR_SOCKET_CLOSED"].includes(error.code)) throw offline();
    throw error;
  } finally {
    signal?.removeEventListener("abort", abort);
    socket.destroy();
  }
}

function validateFileName(value) {
  if (typeof value !== "string" || !pathnamePattern.test(value)) {
    throw new BridgeError("INVALID_FILENAME", "Use a ZIP, TXT, or LOG basename in the private transfer directory.");
  }
}

async function importReport(connection, filename) {
  validateFileName(filename);
  const source = await open(join(transferDirectory, filename), constants.O_RDONLY | constants.O_NOFOLLOW)
    .catch(() => { throw new BridgeError("FILE_UNAVAILABLE", "Place a regular file in the private transfer directory."); });
  let transferId;
  try {
    const stat = await source.stat();
    if (!stat.isFile() || stat.size < 1 || stat.size > maxFileSize) {
      throw new BridgeError("INVALID_FILE", "Use a regular ZIP, TXT, or LOG file up to 256 MiB.");
    }
    ({ transferId } = await connection.call("transfer_begin", { filename, size: stat.size }));
    const chunk = Buffer.alloc(chunkSize);
    for (let offset = 0; offset < stat.size;) {
      const { bytesRead } = await source.read(chunk, 0, Math.min(chunkSize, stat.size - offset), offset);
      if (!bytesRead) throw new BridgeError("FILE_CHANGED", "The source file changed during import.");
      const data = chunk.subarray(0, bytesRead).toString("base64");
      const response = await connection.call("transfer_write", { transferId, offset, data });
      offset += bytesRead;
      if (response.offset !== offset) throw new BridgeError("INVALID_RESPONSE", "Unexpected upload position.");
    }
    const result = await connection.call("transfer_commit", { transferId });
    transferId = undefined;
    return result;
  } finally {
    if (transferId) await connection.call("transfer_close", { transferId }).catch(() => {});
    await source.close();
  }
}

async function exportReport(connection, metadata) {
  const { transferId, filename, size } = metadata;
  let partial;
  try {
    validateFileName(filename);
    if (!Number.isSafeInteger(size) || size < 0) throw new BridgeError("INVALID_RESPONSE", "Unexpected export size.");
    await mkdir(transferDirectory, { recursive: true, mode: 0o700 });
    const target = join(transferDirectory, randomUUID() + "-" + filename);
    partial = target + ".partial";
    const out = await open(partial, "wx", 0o600);
    try {
      for (let offset = 0; offset < size;) {
        const { data } = await connection.call("transfer_read", { transferId, offset });
        const bytes = Buffer.from(data, "base64");
        if (bytes.length !== Math.min(size - offset, chunkSize)) {
          throw new BridgeError("INVALID_RESPONSE", "Unexpected download size.");
        }
        await out.writeFile(bytes);
        offset += bytes.length;
      }
    } finally {
      await out.close();
    }
    await rename(partial, target);
    partial = undefined;
    return { path: target, size };
  } finally {
    await connection.call("transfer_close", { transferId }).catch(() => {});
    if (partial) await unlink(partial).catch(() => {});
  }
}

export function invoke(config, tool, args, options) {
  return withBridge(config, async connection => {
    if (tool === "import_report") return importReport(connection, args.filename);
    const result = await connection.call(tool, args);
    if (tool === "export_session" || tool === "export_crash") return exportReport(connection, result);
    return result;
  }, options);
}
