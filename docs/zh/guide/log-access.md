# 日志权限

Android 把设备日志当作隐私。一个 App 随便读自己的日志没问题，读别人的必须
由用户明确授权。LogSleuth 支持三种，它们并不等价。

## 三条路径

### Shizuku

Shizuku 是一个在电脑之外维持 ADB 权限的服务，LogSleuth 通过普通的 binder
服务与它通信。

- 需要安装 Shizuku；它会随手机重启而停止（自己再启动一次，或配置自动启动）。
- 能读**所有**缓冲区，包括别的 App 的日志。
- LogSleuth 退到后台后依然有效，所以崩溃监看和悬浮控件都能工作。
- 不改动系统，因此是未 Root 手机上的首选。

### Root

LogSleuth 可以通过 [libsu](https://github.com/topjohnwu/libsu) 以 `su` 身份
执行 shell 命令。

- 默认关闭、需要手动打开：**设置 → 日志权限** 里的 root 开关。没打开之前，
  App 绝不会执行 `su`。
- 能读全部日志，退到后台也有效。
- *自动* 模式优先 Shizuku，所以已经配好 Shizuku 的设备不会打开 root shell。

### ADB 授权

接上电脑，或者在手机上用终端执行：

```bash
adb shell pm grant io.github.shiaho777.logsleuth android.permission.READ_LOGS
```

- 不需要 root，也不需要 Shizuku，但授权只对当前安装的这一份有效。
- **Android 13 及以后：** 以 App 身份启动的 logcat 只在 App 位于前台时才有
  数据。系统在后台启动时会拒绝，并且把这次拒绝记约一分钟，所以授权弹窗有时
  会「迟到」。Shizuku 和 root 不受这条限制。
- Android 12 及更早版本上，退到后台后流仍然继续。

## 怎么选

*设置 → 日志权限* 每条路径一行，当前使用的会被标出来。

- **自动** —— 依次尝试 Shizuku、root、ADB 授权。默认选项，多数人用它就好。
- **Shizuku** —— 强制使用 Shizuku。想让 App 保持安静、永远不提 root 时选它。
- **Root** —— 强制使用 root shell。
- **ADB 授权** —— 只用系统授权。

## 流起不来时

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 一直显示「正在等待日志」，看不到别的 App 的行 | 授权缺失，或刚才被拒绝了 | 检查 **设置 → 日志权限**；后台启动的拒绝会记约一分钟，等一分钟再试 |
| 只有自己的日志 | 在 Android 13+ 上用 ADB 授权，且 App 已退到后台 | 把 LogSleuth 切回前台，或者改用 Shizuku / root |
| 重启后 Shizuku 显示未连接 | Shizuku 随手机重启停止了 | 重新启动 Shizuku，再把 LogSleuth 切回前台 |
| root shell 报 Permission denied | root 没有授予 LogSleuth | 在你的 root 管理器里授权，再把 LogSleuth 里的 root 开关关掉重开 |

## 读了哪些缓冲区

LogSleuth 使用的是 logcat 的默认组合：

```text
-b main -b system -b crash -b events
```

`radio` 不在其中——它太吵，通常没用。每行都带 uid 列，App 用它来按应用分组。
把 uid 变成包名需要 shell，因此只有 Shizuku 和 root 路径才显示应用名。

## App 自己索取的权限

| 权限 | 用途 |
| --- | --- |
| `READ_LOGS` | 读取设备日志（可选，走 ADB 授权） |
| `FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_SPECIAL_USE` | 崩溃监看与录制服务 |
| `POST_NOTIFICATIONS` | 崩溃通知和两个前台通知 |
| `SYSTEM_ALERT_WINDOW` | 可选的悬浮控件 |
| `RECEIVE_BOOT_COMPLETED` | 开机后恢复崩溃监看（前提是你打开了它） |
| `INTERNET`、`REQUEST_INSTALL_PACKAGES` | 应用内检查更新与下载 APK |

SDK（`logsleuth-sdk`）**一个都不申请**。它只读宿主 App 自己的日志。
