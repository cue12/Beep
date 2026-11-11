# Add project specific ProGuard rules here.
# You can control the set of files used in this configuration using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep the entire package used for Whisper JNI interactions
-keep class com.whispercpp.java.whisper.** { *; }

# General rule to keep names of any native methods and the classes containing them
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Add any project specific keep rules here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve line number information and source file attributes for crash reporting stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile