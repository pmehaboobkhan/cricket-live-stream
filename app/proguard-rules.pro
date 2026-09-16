# Rules for Jetpack Compose
-keep class androidx.compose.** { *; }
-keep interface androidx.compose.** { *; }
-dontwarn androidx.compose.**

# RootEncoder 2.8.1 (Kotlin + Java)  
-keep class com.pedro.** { *; }
-keepinterfaces class com.pedro.** { *; }
-dontwarn com.pedro.**

# Koin DI
-keep class org.koin.** { *; }
-keep interfaces interface org.koin.** { *; }
-dontwarn org.koin.**

# OkHttp / OKio
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class okio.** { *; }
-dontwarn okio.**

# WebKit WebView for overlay rendering
-keep class android.webkit.** { *; }

# Camera2 API (used by RootEncoder internally when using USB cameras)
-keep class android.hardware.camera2.** { *; }
-dontwarn android.hardware.camera2.**

# Gson / Data Store
-dontwarn com.google.gson.**
