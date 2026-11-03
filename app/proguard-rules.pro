# Room
-keep class androidx.room.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(); }
# Kotlin metadata
-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.coroutines.**
