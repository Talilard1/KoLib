# No reflection-based libraries are used. Keep line numbers so crash logs stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# The description editor's page calls these methods by name.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Tesseract and Leptonica (text recognition) are reached from their native code by name: keep them as they are.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }

# The on-device language models' native side finds these by name.
-keep class dev.kolib.app.ai.LlamaNative { *; }
-keep interface dev.kolib.app.ai.LlamaNative$Sink { *; }
-keepclassmembers class * implements dev.kolib.app.ai.LlamaNative$Sink { *; }
