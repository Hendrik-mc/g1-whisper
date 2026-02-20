# Keep BLE / Bluetooth classes
-keep class android.bluetooth.** { *; }

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keepclassmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <methods>;
}

# Keep data models for serialization
-keep class com.evenai.companion.data.model.** { *; }
-keep class com.evenai.companion.domain.model.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Kotlin coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Keep enums
-keepclassmembers enum * { *; }
