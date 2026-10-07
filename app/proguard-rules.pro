# Maximum obfuscation + library stripping
-optimizationpasses 7
-dontpreverify
-verbose

# Aggressive optimization (inline everything possible)
-optimizations code/simplification/cast,code/removal/variable,method/marking/private,method/removal/parameter

# Maximum name obfuscation
-repackageclasses ''
-allowaccessmodification
-obfuscationdictionary proguard-dict.txt
-classobfuscationdictionary proguard-dict.txt
-packageobfuscationdictionary proguard-dict.txt

# Strip debug
-renamesourcefileattribute PG
-keepattributes Exceptions,InnerClasses,Signature,EnclosingMethod

# STRICT: only keep what's needed
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers

# Suppress all warnings - we know what we're doing
-dontwarn **
-ignorewarnings

# ==== APP ENTRY POINTS ====
-keep public class com.jd_s4nd_b0x.CountMe.MainActivity {
    public <init>();
}

# Keep minimum Android callbacks
-keep public class * extends android.app.Activity {
    public <init>();
}

# Keep native methods (if any)
-keepclasseswithmembernames class * {
    native <methods>;
}

# ==== AGGRESSIVE LIBRARY STRIPPING ====
# Remove ALL library code not directly referenced by app

# Strip AndroidX internals
-dontwarn androidx.**
-dontwarn com.google.android.material.**

# Strip all logging
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
-assumenosideeffects class androidx.appcompat.BuildConfig {
    public static <fields>;
}

# Remove all test & debug code
-dontwarn junit.**
-dontwarn org.junit.**
-dontwarn androidx.test.**
-dontwarn org.mockito.**
-dontwarn com.squareup.picasso.**

# Strip BuildConfig variants
-dontwarn **.BuildConfig

# Maximum class removal
-dontwarn android.**
-dontwarn sun.misc.**
-dontwarn com.android.internal.**
-dontwarn org.apache.**

