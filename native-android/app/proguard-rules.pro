-keep class io.livekit.** { *; }
-dontwarn org.webrtc.**
# Tink references these optional compile-time annotations. They are never used
# at runtime, so suppressing their absence keeps the release shrinker safe.
-dontwarn com.google.errorprone.annotations.**
