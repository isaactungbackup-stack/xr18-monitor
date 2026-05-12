# Add project specific ProGuard rules here.

# Keep WING wapi classes
-keep class com.mixer.wing.wapi.** { *; }
-keep class com.mixer.wing.discovery.** { *; }
-keep class com.mixer.wing.util.** { *; }

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}