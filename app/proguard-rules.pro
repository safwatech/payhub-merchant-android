# kotlinx.serialization — keep generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers,allowobfuscation class * {
    @kotlinx.serialization.SerialName <fields>;
}

# Keep the PayHub SDK model classes & their serializers (reflectively used by kotlinx.serialization)
-keep class ly.payhub.** { *; }
-keepclassmembers class ly.payhub.merchant.data.** { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Hilt generated components
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { *; }

# Compose keeps itself; nothing extra needed.
