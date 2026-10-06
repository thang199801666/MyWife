# YouTooBee release rules.
# JavaScript calls these methods by name from the WebView bridge; R8 must not rename/remove them.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep the bridge class itself stable for conservative WebView/R8 interoperability.
-keep class com.example.videoshield.VideoShieldBridge { *; }

# ExtraFieldUtils instantiates registered ZIP fields through Class.newInstance().
# First-time Python/FFmpeg extraction fails if R8 removes their public constructors.
-keep,allowobfuscation class * implements org.apache.commons.compress.archivers.zip.ZipExtraField {
    public <init>();
}
