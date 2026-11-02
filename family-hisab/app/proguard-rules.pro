# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class com.asif.familyhisab.**$$serializer { *; }
-keepclassmembers class com.asif.familyhisab.** { *** Companion; }
-keepclasseswithmembers class com.asif.familyhisab.** { kotlinx.serialization.KSerializer serializer(...); }
