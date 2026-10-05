# Ktor and kotlinx.serialization use reflection for engines and serializers.
-keep class io.ktor.** { *; }
-keepclassmembers class com.prempoddar.campfire.core.** { *** Companion; *** serializer(...); }
-keep,includedescriptorclasses class com.prempoddar.campfire.**$$serializer { *; }
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn io.netty.**
