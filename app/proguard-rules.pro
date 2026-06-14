# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.homehub.** {
    *** Companion;
}
-keepclasseswithmembers class com.homehub.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# OkHttp / Retrofit
-dontwarn okhttp3.**
-dontwarn retrofit2.**
