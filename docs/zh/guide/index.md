# 快速开始

LogSleuth 读的是设备日志。第一个要做的事情是给它一条读日志的路径，之后就是
一个可以过滤、录制、导出的实时列表。

## 1. 安装

从 [GitHub Releases](https://github.com/shiaho777/LogSleuth/releases/latest)、
[F-Droid](https://f-droid.org/packages/io.github.shiaho777.logsleuth/) 或
[Play 商店](https://play.google.com/store/apps/details?id=io.github.shiaho777.logsleuth)
下载安装包。需要 Android 8.0（API 26）及以上。

## 2. 授予日志权限

Android 不允许 App 随手读取别的 App 的日志，必须由用户明确授权。LogSleuth
支持三种路径，之后可以在 **设置 → 日志权限** 里随时更换：

| 路径 | 需要什么 | 能得到什么 |
| --- | --- | --- |
| **Shizuku** | Shizuku App，重启后需要自己再启动一次 | 全部日志，前台后台都可以 |
| **Root** | 已 Root 的手机，并在 LogSleuth 里打开 root 开关 | 全部日志，前台后台都可以 |
| **ADB 授权** | `adb shell pm grant io.github.shiaho777.logsleuth android.permission.READ_LOGS` | LogSleuth 在前台时可以读全部日志 |

未 Root 的手机**推荐用 Shizuku**：不需要改动系统，而且重启后只要重新启动
Shizuku 就能继续用。默认的「自动」模式会先试 Shizuku，再试 root，最后才用 ADB
授权，所以已经装好 Shizuku 的设备不会莫名其妙弹出 root 提示。

ADB 授权有一个诚实的限制：Android 13 起，以 App 身份启动的 logcat 只在
LogSleuth 位于前台时有效，退到后台流就断了。细节见
[日志权限](/zh/guide/log-access)。

## 3. 跟着引导走一遍

首次启动会走一遍三种授权方式和主界面。不想看可以跳过，之后在
**设置 → 功能引导** 里随时重看。

## 怎么看这个列表

- **右侧那条是轨道，不是普通滚动条。** 它把整个缓冲区画成一列色带，每段取
  该段里最高的级别，所以错误聚集不用滚动就能看见。拖动滑块可以跳转，也可以
  直接点轨道上的某个位置；拖动时气泡会显示「第 1,234 行 / 共 20,000 行」和
  百分比。
- **轨道底部的胶囊**是当前缓冲的总行数。
- **顶栏**依次是暂停、搜索、悬浮控件开关、清空、保存，溢出菜单里有
  **重新读取日志**，用于流看起来卡住的时候。它会保留续读位置，不会把环形
  缓冲再倒一遍。
- **过滤栏**用来收窄：级别、应用、规则栈。见 [过滤与规则](/zh/guide/filters)。

## 日常操作

| 你想… | 这样做 |
| --- | --- |
| 暂停流但不丢缓冲 | 点 **暂停**。横幅会按当前过滤条件统计等待中的行数 |
| 找某一行 | 点放大镜，回车跳到下一条命中 |
| 复制看到的内容 | 长按选中一段，然后复制可见列 |
| 保留一段会话 | 顶栏的 **保存**、快捷设置磁贴，或者悬浮控件 |
| 看一次崩溃 | **崩溃** 标签页，或者侦测到时的通知 |
| 把设置恢复干净 | **设置 → 恢复默认设置**，会先问一句，并写清楚哪些东西会保留 |
| 提个问题 | Q 群和哔哩哔哩在 **设置** 底部，一键可达 |

## 接下来

- [日志权限](/zh/guide/log-access) —— 每条路径能做什么、不能做什么
- [过滤与规则](/zh/guide/filters) —— 在很 busy 的流里收窄范围
- [录制与崩溃](/zh/guide/recording-and-crashes) —— 会话、侦测与导出
