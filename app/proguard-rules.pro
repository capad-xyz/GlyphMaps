# ── Nothing Glyph Matrix SDK ────────────────────────────────────────────────
# Referenced by the system / SDK at runtime; keep everything.
-keep class com.nothing.ketchum.** { *; }
-dontwarn com.nothing.ketchum.**

# ── System-instantiated components (looked up by name in the manifest) ──────
-keep class com.glyphnavtoy.service.MapsNotificationListener { *; }
-keep class com.glyphnavtoy.service.GlyphRenderService { *; }
-keep class * extends android.service.notification.NotificationListenerService { *; }
-keep class * extends android.app.Service { *; }

# ── Maneuver enum ───────────────────────────────────────────────────────────
# Maneuver.name is put into an Intent and read back with Maneuver.valueOf in the
# render service. Keep the enum + its synthetic values()/valueOf so the constant
# names survive shrinking.
-keepclassmembers enum com.glyphnavtoy.glyph.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep enum com.glyphnavtoy.glyph.Maneuver { *; }

# ── BuildConfig (IS_DEV flag read at runtime) ───────────────────────────────
-keep class com.glyphnavtoy.BuildConfig { *; }

# ── Kotlin / coroutines ─────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**
-keepclassmembernames class kotlinx.** { volatile <fields>; }

# ── Strip all logging from release (belt-and-suspenders behind IS_DEV gates) ─
# Any android.util.Log call left in the user build is removed by R8, so no
# notification/route content can ever reach logcat in production.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
