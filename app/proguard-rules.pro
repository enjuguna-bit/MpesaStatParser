# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Apache POI
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**

# PDFBox
-keep class org.apache.pdfbox.** { *; }
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn org.apache.pdfbox.**
-dontwarn com.tom_roush.pdfbox.**

# Room
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}
