# ProGuard rules for ThiengKin (release build)

# Kotlin metadata
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# kotlinx-serialization
-keepclassmembers class **$$serializer {
    *;
}
-keep,includedescriptorclasses class com.thiengkin.**$$serializer { *; }
-keepclassmembers class com.thiengkin.** {
    *** Companion;
}
-keepclasseswithmembers class com.thiengkin.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Compose
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
