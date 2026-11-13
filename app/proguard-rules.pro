# Bevara JavascriptInterface-metoder i AndroidBridge vid release-bygge
-keepclassmembers class com.northerngo.duopaper.AndroidBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
