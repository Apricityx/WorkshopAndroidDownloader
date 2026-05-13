# Project-specific R8 rules.

# Keep metadata needed by Kotlin and kotlinx.serialization when optimized by R8.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,EnclosingMethod,Signature

# Keep generated serializers for project models.
-keep,includedescriptorclasses class top.apricityx.workshop.**$$serializer { *; }

# Keep serializer entry points generated on companion objects.
-keepclassmembers class top.apricityx.workshop.** {
    *** Companion;
}

-keepclasseswithmembers class top.apricityx.workshop.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep native method names and zstd-jni APIs used by workshop-core decompression.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-keep class com.github.luben.zstd.** { *; }

# protobuf-javalite generated messages are accessed through generated schema metadata.
# Obfuscating these fields breaks Steam CM/CDN request encoding at runtime.
-keep class top.apricityx.workshop.steam.proto.** { *; }

-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
