# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.mobilealarm.**$$serializer { *; }
-keepclassmembers class com.mobilealarm.** {
    *** Companion;
}
-keepclasseswithmembers class com.mobilealarm.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room entities
-keep class com.mobilealarm.data.db.** { *; }