# Optimized R8 rules for better performance and memory

# General project rules
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Remove logging in release builds for performance and security
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Room
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public <init>(...);
}
-keep class * extends androidx.room.RoomDatabase
-keep class androidx.room.paging.LimitOffsetDataSource

# Retrofit
# Retrofit provides its own consumer rules, so we only need to keep specific attributes
-keepattributes Signature, InnerClasses, *Annotation*

# Moshi
# Assuming use of codegen (KSP), we don't need to keep the entire library.
# Keeping only what's necessary for reflection-less operation.
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}
# Keep generated JsonAdapters
-keep class *JsonAdapter { *; }

# Ktor / Coroutines / Serialization
-dontwarn kotlinx.serialization.json.internal.**
# rules for kotlinx-serialization
-keepclassmembers class ** {
    *** Companion;
    *** serializer(...);
}
-keepclassmembers class * extends kotlinx.serialization.internal.GeneratedSerializer {
    *** INSTANCE;
}

# SLF4J (Common dependency of Ktor/Supabase)
-dontwarn org.slf4j.**
