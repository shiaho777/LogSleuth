# Keep Shizuku API (it is consumed via reflection by the Shizuku binder)
-keep class dev.rikka.shizuku.** { *; }
-keep interface dev.rikka.shizuku.** { *; }

# Room / Hilt defaults are handled by their own keep rules.
