# Faiza AI ProGuard Rules

# Keep Gson models
-keepclassmembers class com.faiza.ai.** { *; }
-keep class com.faiza.ai.license.** { *; }
-keep class com.faiza.ai.ai.** { *; }

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Vosk / JNA (offline wake word)
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class org.vosk.** { *; }
-dontwarn java.awt.**

# Gson TypeToken generics (lists saved as JSON) and enums read by name
-keep class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keepclassmembers enum * { *; }
# CameraX / Vosk / JNA reflection warnings
-dontwarn org.slf4j.**
