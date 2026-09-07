# LogSleuth —— 安卓日志记录工具 实现方案

**定位**:开源(Apache-2.0)、免 root 的安卓日志工具,双模式:
- **模式一(查看器)**:完整 logcat 查看器,经 Shizuku 或 ADB 一次性授权读取全设备日志(对标并超越 LogFox)
- **模式二(内嵌 SDK)**:开发者把 `logsleuth-sdk` 集成进自己的 App,真正零权限记录日志/闪退/ANR,用户一键导出上报

包名 `io.github.shiaho777.logsleuth.app`(正式发布前可换成你的 GitHub 用户名,F-Droid 要求 applicationId 稳定)。minSdk 26,targetSdk 最新。

---

## 一、仓库与工程结构

```
LogSleuth/
├── app/                  # 主应用(Compose UI + 日志引擎)
├── sdk/                  # logsleuth-sdk(开发者集成的 AAR,纯 Kotlin,零权限零网络)
├── sample/               # 演示 SDK 用法的示例 App(也用于联调)
├── gradle/libs.versions.toml   # 版本目录
├── .github/workflows/    # CI:assemble + lint + 单测 + Release 构建
├── fastlane/metadata/    # F-Droid / Play 商店元数据(标题、描述、截图、changelog)
├── LICENSE(Apache-2.0)、README.md(EN)、README.zh-CN.md、CONTRIBUTING.md、
├── issue/PR 模板、AGENTS.md、.gitignore
```

技术栈:Kotlin、Jetpack Compose + Material 3(动态取色、深/浅色)、Navigation-Compose、Hilt(DI)、Room(会话/过滤器存储)、DataStore(设置)、Coroutines/Flow(日志流)、Shizuku API(rikka.shizuku)。

## 二、核心:日志引擎(app 模块)

**采集路径(自动探测 + 引导设置)**:
1. **Shizuku 路径**(优先推荐):检测 Shizuku 存活与授权 → 以 shell(uid 2000)身份运行 `logcat`,支持 `logcat --uid <uid>` 服务端过滤,可用 `ps` 做 PID→包名映射
2. **ADB 授权路径**:检测 `READ_LOGS` 是否已授予;未授予则在引导页展示可复制的一键命令 `adb shell pm grant io.github.shiaho777.logsleuth.app android.permission.READ_LOGS`,授予后 App 自身进程直接跑 `logcat`
3. 两者都没有 → 引导页逐步教学(无线调试激活 Shizuku 图解 / ADB 命令页),这是免 root 下的平台限制,与 LogFox 等产品一致

**解析与流式管道**:`logcat -v threadtime` 逐行解析(日期/时间/PID/TID/级别/Tag/多行消息),容错处理异常行;Flow + Channel 缓冲,背压安全;内存环形缓冲(默认 10 万行,可配);录制时同步落盘。

**已知限制(如实写进 README)**:按"应用"过滤依赖 Shizuku(无 shell 权限时 PID→包名映射被系统隐藏);仅 READ_LOGS 模式提供级别/Tag/关键字/正则过滤。

## 三、查看器功能(v1 全部实现)

1. **实时日志流**:级别着色(V/D/I/W/E/F)、暂停/继续、自动滚底 + 上滑悬停时"回到底部"FAB、大列表 LazyColumn 性能优化
2. **过滤系统**:级别、Tag、关键字、正则、排除规则;过滤器可保存/命名/快速切换;Shizuku 下按应用过滤(App 选择器)
3. **搜索**:流内全文搜索、上下跳转命中
4. **录制**:前台服务 + 通知栏控制 + Quick Settings Tile 快捷开关;录制为 session(Room 元数据 + 文件)
5. **回放与导出**:历史会话只读回放;导出 txt 或 zip(附设备信息:型号/Android 版本/App 版本);FileProvider 系统分享
6. **崩溃/ANR 侦测**:识别 `FATAL EXCEPTION`、AndroidRuntime、`ANR in`/`am_anr`,时间线高亮 + 本地通知,崩溃事件列表页
7. **悬浮气泡**(可选,SYSTEM_ALERT_WINDOW,默认关闭):一键打时间戳书签/开始停止录制,用户现场反馈时精准定位"出问题那一刻"
8. **设置**:缓冲区大小、保留策略、主题、导出格式、气泡开关

## 四、SDK 模块(sdk 模块,零权限零网络)

- `Sleuth.init(context, config)`:接管 UncaughtExceptionHandler(并链式回调原 handler)、启动**仅捕获本进程**的 logcat 读取(读自己日志无需任何权限)、ANR 看门狗(主线程心跳)
- 滚动落盘:App 私有目录,环形轮换(默认 3×2MB 可配),含设备/App 信息头
- `Sleuth.shareLogs(activity)`:打包 zip → 系统分享面板,用户一键发给开发者
- 提供 onCrash 回调(下次启动时通知宿主,便于宿主自行上报)
- LogSleuth App 注册 zip 打开器,可直接渲染 SDK 导出的日志包(打通两个模式)
- sample 模块演示集成;发布到 Maven Central 列为后续项

## 五、UI/UX

Compose + M3 动态主题,页面:引导设置向导 → 实时日志流(主页) → 过滤器管理 → 会话列表/回放详情 → 崩溃事件 → 设置。遵循 Compose 动效最佳实践(spring 动画、共享元素过渡、大列表优化),空态/错误态/权限缺失态都有完整设计。中英文双语字符串。

## 六、开源配套(随代码一次到位)

Apache-2.0 LICENSE;中英 README(功能截图、Shizuku/ADB 两种授权图文教程、SDK 集成指南);GitHub Actions CI(构建+lint+单测,Release 构建可选签名);Issue/PR 模板;fastlane 元数据(F-Droid 与 Play 复用);版本号与 changelog 规范。F-Droid 收录与 Play 上架在仓库公开 + 打 tag 后进行(需你手动提交)。

## 七、实施顺序(每步可验证)

1. 仓库初始化:git init、LICENSE、README 骨架、Gradle 多模块(app/sdk/sample)、版本目录、CI
2. App 骨架:M3 主题、导航、Hilt、Room、DataStore、页面脚手架
3. 日志引擎:LogcatSource 抽象 + 本机/Shizuku 两种实现 + 解析器 + Flow 管道(含解析器单测)
4. 授权引导向导(Shizuku 检测、ADB 命令页)
5. 实时流 UI + 过滤/搜索
6. 录制服务 + 会话存储 + 导出分享
7. 崩溃/ANR 侦测 + 通知 + 快捷开关磁贴
8. 悬浮气泡(可选功能)
9. SDK 模块 + sample 联调 + App 打开 SDK 导出包
10. 打磨:动效、空态、双语文案、README 截图、fastlane 元数据、打 tag

## 八、验证

- 解析器/过滤器单元测试(JUnit)
- 用本机 Android 模拟器实际构建运行:引导页、Shizuku/ADB 授权模拟、日志流滚动、过滤、录制导出、崩溃侦测逐项截图验证
- 模拟崩溃(测试按钮)验证 SDK 捕获与导出闭环

**不做(v1 范围外)**:云端上报后台、SDK 远程下发配置、Root 模式(Shizuku 已覆盖)、iOS。