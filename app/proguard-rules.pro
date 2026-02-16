# Keep launcher activities
-keep class com.minimalist.launcher.activity.** { *; }

# Keep Supabase/Ktor/Serialization classes
-keep class com.minimalist.launcher.data.** { *; }
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
