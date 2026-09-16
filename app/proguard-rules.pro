-allowaccessmodification
-overloadaggressively

-keep class io.github.xiaoyueyoqwq.ims.** { *; }
-keep class com.flyfishxu.kadb.** { *; }
-dontwarn com.flyfishxu.kadb.**
-dontwarn org.bouncycastle.**
-keepclassmembers class * extends android.app.Instrumentation {
    public <init>();
}
