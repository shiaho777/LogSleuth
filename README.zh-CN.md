# LogSleuth

**强大、免 Root 的安卓日志查看器 + 内嵌日志 SDK。**
把“用户说 App 出问题了,但你复现不了”变成可以定位。

[English](README.md)

## LogSleuth 是什么?

LogSleuth 是一个开源(Apache-2.0)的安卓日志工具,包含两种模式:

### 1. 日志查看器(App 本体)

面向全设备的 Logcat 实时查看器,无需 Root:

| 功能 | 说明 |
| --- | --- |
| 实时日志流 | 级别着色、暂停/继续、自动滚底 |
| 过滤系统 | 级别 / Tag / 关键字 / 正则 / 排除过滤,可保存过滤器预设 |
| 按应用过滤 | 只看你关心的 App(需 Shizuku) |
| 录制回放 | 后台录制日志会话,随时回放 |
| 崩溃 / ANR 侦测 | 自动高亮 `FATAL EXCEPTION` 与 ANR 并通知 |
| 时间戳书签 | 可选悬浮气泡,出问题那一刻一键打点 |
| 导出分享 | 导出 `.txt` 或 `.zip`(附设备信息)发给开发者 |

### 2. 内嵌 SDK(`logsleuth-sdk`)

给 App 开发者:把 SDK 集成进你自己的应用,它会**零权限、零网络**地记录
你 App 的日志、崩溃和 ANR。用户点一下“分享日志”就能把 zip 发给你,
你终于能看到他们设备上到底发生了什么。

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Sleuth.init(this)
    }
}

// App 内任意位置:
Sleuth.shareLogs(activity)   // 弹出系统分享面板,打包好的日志 zip
```

LogSleuth 查看器 App 可以直接打开并渲染 SDK 导出的日志包。

## 为什么“免 Root”还需要一次性授权?

从 Android 4.1 开始,应用无法读取其他 App 的日志,这是平台限制,对所有
日志类 App 一视同仁。只有两条路(只需授权一次):

1. **Shizuku(推荐)**——无需 Root,Android 11+ 可通过“无线调试”在手机上直接激活,无需电脑。安装 [Shizuku](https://shizuku.rikka.app/),启动一次,授权 LogSleuth 即可。
2. **ADB 一次性授权**——有电脑的话,执行一次即可(卸载前一直有效):
   ```bash
   adb shell pm grant io.github.logsleuth.app android.permission.READ_LOGS
   ```

**内嵌 SDK 模式什么都不需要**——App 永远可以读取自己的日志。

## 项目结构

```
app/      LogSleuth 查看器 App(Kotlin + Jetpack Compose, Material 3)
sdk/      logsleuth-sdk —— 可内嵌的零权限日志库
sample/   演示 SDK 集成的示例 App
```

## 构建

要求:JDK 17+,Android SDK 35。

```bash
./gradlew assembleDebug        # 构建 App
./gradlew :sdk:assemble        # 构建 SDK AAR
./gradlew test                 # 单元测试
```

## 下载

- GitHub Releases(见 Releases 页面)
- F-Droid(首个公开版本发布后上架)
- Google Play(计划中)

## 参与贡献

见 [CONTRIBUTING.md](CONTRIBUTING.md)。欢迎通过 GitHub Issues 提交
Bug 报告和功能建议。

## 许可证

[Apache-2.0](LICENSE)
