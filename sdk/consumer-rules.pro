# The SDK is designed for safe embedding; keep the public API surface.
-keep class io.github.logsleuth.sdk.Sleuth { *; }
-keep class io.github.logsleuth.sdk.SleuthConfig { *; }
-keep class io.github.logsleuth.sdk.SleuthConfig$* { *; }
