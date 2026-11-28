# Orbit Browser R8 / ProGuard rules

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# WebView @JavascriptInterface bridges
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
-keep class com.example.feature.sitedata.engine.OrbitSiteDataBridge$JsReceiver {
    public <methods>;
}
-keep class com.example.feature.passwords.autofill.WebFormInspector$JsBridge {
    public <methods>;
}

# JNI native adblock-rust bridge
-keepclasseswithmembernames,includedescriptorclasses class com.example.feature.shield.engine.NativeAdblockBridge {
    native <methods>;
}

# Moshi & Retrofit DTOs
-keepclassmembers class * {
    @com.squareup.moshi.JsonClass <methods>;
}
-keep class com.example.feature.sitedata.engine.**JsonAdapter { *; }
-keep class com.example.core.network.**JsonAdapter { *; }
-keepclassmembers class com.example.core.network.** { *; }
-keepclassmembers class com.example.feature.sitedata.engine.**Dto { *; }

# Retrofit & OkHttp optional platform annotations
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.bouncycastle.**
