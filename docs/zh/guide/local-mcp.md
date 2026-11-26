# Termux 本地 MCP

在同一台 Android 手机上，用 Termux 中的 Agent 操作 LogSleuth。Agent 可以查询实时与已保存的日志、录制和添加书签、查看崩溃，并在 LogSleuth 和 Termux 的文件交换目录之间导入导出报告。

## 安装与配对

1. 安装包含「设置 → 本地 MCP」的 LogSleuth。从同一个 [GitHub Release](https://github.com/shiaho777/LogSleuth/releases) 下载 APK 和桥接包，或用同一份源码构建两者。
2. 安装 [Termux](https://github.com/termux/termux-app#installation) 和 Node.js 24 或更高版本。先在 Termux 运行 `pkg upgrade` 更新运行库，再运行 `pkg install nodejs-lts`，用 `node --version` 检查版本。找到发行附件 `logsleuth-mcp-<应用版本>.tgz`，在文件所在目录运行 `npm install --global ./logsleuth-mcp-<应用版本>.tgz`，把占位部分换成实际文件名。桥接包已包含运行依赖。
3. 在 LogSleuth 打开「设置 → 本地 MCP」，启用服务并生成配对串。在 Termux 运行 `logsleuth-mcp pair`，在两分钟内粘贴配对串。如果 Android 要求授予设备日志权限，请回到 LogSleuth 完成系统授权。
4. 运行 `logsleuth-mcp doctor` 查看连接、日志来源和当前录制状态。

从源码构建桥接包需要 Node.js 24 和 OpenSSL。

```sh
./scripts/package_mcp.sh /tmp/logsleuth-mcp-local.tgz
```

## 连接 Agent

添加一个 stdio MCP 服务，命令为 `logsleuth-mcp`，参数为 `serve`。下面是 [Kopi Agent](https://kopiaiagent.com/docs/user-guide/features/mcp/) 的配置示例。如果 Agent 的命令搜索路径不同，用 `command -v logsleuth-mcp` 查出并填写完整路径。

如果 Agent 在常规 Termux 终端环境外启动工具并提示解释器不可用，将命令改成 `command -v node` 给出的完整路径，两个参数依次填已安装的 `cli.mjs` 完整路径和 `serve`。`npm root -g` 可以找到包含 `logsleuth-mcp/cli.mjs` 的目录。

```yaml
mcp_servers:
  logsleuth:
    command: logsleuth-mcp
    args: [serve]
```

可以把一次排查分成几条指令，中间操作目标应用。

1. 「检查 LogSleuth 状态，开始录制，并告诉我会话 ID。」
2. 操作目标应用，然后让 Agent「添加名为『打开设置』的书签，并结束录制。」
3. 「找出这段会话里的错误，给出行号，并读取最相关的一条的前后日志。」
4. 「把这段会话导出成 ZIP，告诉我保存在手机上的路径。」

还未授予设备日志权限时，可以让 Agent 用 `open_example` 打开内置示例，再查询该会话。会话中的引用是文件行号，实时日志中的引用是当前来源的序号。搜索结果会提供来源 ID，有后续结果时会同时给出分页游标。

## 文件与授权

- `logsleuth-mcp files` 会显示 Termux 私有交换目录。把 ZIP、TXT 或 LOG 放进去，再将文件名交给 `import_report`。导出会返回完整的本地路径。需要分享时，可以将文件移到自己选择的位置。
- 请仅为信任的 Agent 配对。配对允许读取日志、更改设置和删除应用数据。「停止 MCP」会关闭连接，并保存由 MCP 启动的录制。「撤销配对」会让已保存的凭据失效。手机或应用重启后，在设置里重新启用 MCP。
- 桥接通过手机本机回环地址传输。工具结果是否交给模型服务商，取决于 Agent 的模型配置。需要离线分析时，请使用本地运行的模型。

## 常见问题

| 结果 | 处理方式 |
| --- | --- |
| 连接断开 | 打开 LogSleuth 并启用 MCP。修改操作中断时，先用 `doctor` 或查看会话确认结果，再决定是否重试。 |
| 配对过期或证书变化 | 在设置页重新生成配对串，再执行 `pair`。 |
| 缺少日志权限或来源未就绪 | 回到 LogSleuth 检查[日志权限](/zh/guide/log-access)，按系统提示授权。已获准的日志流可在操作其他应用时继续。 |
| 需要前台或系统操作 | 回到 LogSleuth 启动所需服务，或完成 Android 的权限提示。 |
| 游标过期 | 在当前来源重新搜索。实时缓冲区只保留有限历史。 |
