---
layout: home

hero:
  name: LogSleuth
  text: Read the whole device log
  tagline: A root-free logcat viewer with a severity fingerprint of your buffer, plus a zero-permission SDK you can drop into your own app.
  actions:
    - theme: brand
      text: Get started
      link: /guide/
    - theme: alt
      text: Developer docs
      link: /developer/
    - theme: alt
      text: View on GitHub
      link: https://github.com/shiaho777/LogSleuth

features:
  - icon: <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 5h16v14H4z"/><path d="M8 9h8M8 13h5"/></svg>
    title: A rail for the whole buffer
    details: The right edge carries a severity fingerprint of everything you have buffered, a thumb you can drag, and a readout of exactly where you are. Error clusters show up without scrolling or searching.
  - icon: <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l8 4v5c0 5-3.5 8.5-8 10-4.5-1.5-8-5-8-10V7z"/></svg>
    title: No root required
    details: Shizuku, an opt-in root shell, or a single ADB grant. Auto mode prefers Shizuku, so an unrooted phone never sees a grant dialog it did not ask for.
  - icon: <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 18h4l3-9 4 13 2-6h3"/></svg>
    title: Crash and ANR detection
    details: Java crashes, native tombstones and ANRs are detected while you watch, with the surrounding lines kept next to the event.
  - icon: <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="16" rx="2"/><path d="M7 9h10M7 13h6"/></svg>
    title: Record and replay
    details: Sessions record with the filter you are actually looking at, survive a reboot, and replay line by line.
  - icon: <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v12"/><path d="M8 11l4 4 4-4"/><path d="M4 19h16"/></svg>
    title: Zero-permission SDK
    details: logsleuth-sdk records your app's own logs, crashes and ANRs with no permissions and no network, then zips them for the user to send you.
  - icon: <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>
    title: Built for reading
    details: Per-frame appends instead of copies, a 20k-line ring buffer, live column toggles, level sets, rule stacks, and an export that matches what you see.
---

<div class="wta-home">

## Two halves that work together

**The viewer app** is a live reader for the whole device log. Grant it access once
— Shizuku, root, or an ADB grant — and it streams every buffer your phone
writes: `main`, `system`, `crash` and `events`.

**`logsleuth-sdk`** is the other direction. Drop it into your own app and it
records that app's logs, crashes and ANRs with **zero permissions and zero
network**, then packages them into a zip your users can send you. LogSleuth
opens those zips directly.

## Install

| Channel | Where |
| --- | --- |
| GitHub Releases | `https://github.com/shiaho777/LogSleuth/releases/latest` |
| F-Droid | [F-Droid](https://f-droid.org/packages/io.github.shiaho777.logsleuth/) |
| Google Play | [Play Store](https://play.google.com/store/apps/details?id=io.github.shiaho777.logsleuth) |

Requires Android 8.0 (API 26) or newer.

## Where to go next

- [Getting started](/guide/) — install, grant access, read your first lines
- [Log access](/guide/log-access) — Shizuku, root and the ADB grant, compared
- [Filters & rules](/guide/filters) — levels, queries, rule stacks, presets
- [Recording & crashes](/guide/recording-and-crashes) — sessions, detection, export
- [Developer docs](/developer/) — architecture, recipes and the release runbook

</div>
