---
layout: home

hero:
  name: LogSleuth
  text: 读懂整台设备的日志
  tagline: 无需 Root 的 logcat 阅读器，右侧轨道直接画出整个缓冲区的级别指纹；另有一个零权限 SDK，可以直接嵌进你自己的 App。
  actions:
    - theme: brand
      text: 快速开始
      link: /zh/guide/
    - theme: alt
      text: 开发者文档
      link: /developer/
    - theme: alt
      text: 在 GitHub 上查看
      link: https://github.com/shiaho777/LogSleuth

features:
  - icon: <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 5h16v14H4z"/><path d="M8 9h8M8 13h5"/></svg>
    title: 面向整个缓冲区的轨道
    details: 右侧把缓冲区画成一列彩色色带——每段取该段里最高的级别——错误聚集一眼可见，不必滚动也不必搜索。拖动滑块即可跳转，拖动时气泡会显示「第 1,234 行 / 共 20,000 行」和百分比。
  - icon: <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l8 4v5c0 5-3.5 8.5-8 10-4.5-1.5-8-5-8-10V7z"/></svg>
    title: 不需要 Root
    details: Shizuku、可选的 root shell，或者一条 ADB 授权。自动模式优先 Shizuku，未 Root 的手机不会看到没主动要过的授权弹窗。
  - icon: <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 18h4l3-9 4 13 2-6h3"/></svg>
    title: 崩溃与 ANR 侦测
    details: Java 崩溃、native tombstone 和 ANR 都会被捕获，并连同上下文日志一起保存。
  - icon: <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="16" rx="2"/><path d="M7 9h10M7 13h6"/></svg>
    title: 录制与回放
    details: 录制沿用你当前看到的过滤条件，重启后依然保留，并且可以逐行回放。
  - icon: <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v12"/><path d="M8 11l4 4 4-4"/><path d="M4 19h16"/></svg>
    title: 零权限 SDK
    details: logsleuth-sdk 在你的 App 内部记录日志、崩溃与 ANR，不申请任何权限、不联网，最后打包成 zip 交给用户发给你。
  - icon: <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>
    title: 为阅读而生
    details: 逐帧追加而不是整表复制，2 万行环形缓冲，可切换显示列，级别集合、规则栈，导出的内容与你看到的完全一致。
---

<div class="wta-home">

## 两个互相配合的部分

**阅读器 App** 用来读整台设备的日志。授权一次——Shizuku、root 或者 ADB
授权——就能实时看到手机写入的所有缓冲区：`main`、`system`、`crash`、`events`。

**`logsleuth-sdk`** 是反过来的那一半。嵌进你自己的 App，它会记录该 App 的
日志、崩溃与 ANR，**零权限、零网络**，最后打包成 zip 交给用户发给你。LogSleuth
可以直接打开这些 zip。

## 安装

| 渠道 | 地址 |
| --- | --- |
| GitHub Releases | `https://github.com/shiaho777/LogSleuth/releases/latest` |
| F-Droid | [F-Droid](https://f-droid.org/packages/io.github.shiaho777.logsleuth/) |
| Google Play | [Play 商店](https://play.google.com/store/apps/details?id=io.github.shiaho777.logsleuth) |

需要 Android 8.0（API 26）及以上。

## 社区

| 平台 | 入口 |
| --- | --- |
| GitHub | [github.com/shiaho777/LogSleuth](https://github.com/shiaho777/LogSleuth) |
| 哔哩哔哩 | [space.bilibili.com/2127032909](https://space.bilibili.com/2127032909) |
| QQ 群 | 1127256026 —— [点击加入群聊](https://qm.qq.com/q/Gzj7CpirAs) |

## 接下来读什么

- [快速开始](/zh/guide/) —— 安装、授权、读到第一行日志
- [日志权限](/zh/guide/log-access) —— Shizuku、root 与 ADB 授权的差别
- [过滤与规则](/zh/guide/filters) —— 级别、查询、规则栈
- [录制与崩溃](/zh/guide/recording-and-crashes) —— 会话、侦测、导出
- [开发者文档](/developer/) —— 架构、改动配方与发版手册

</div>
