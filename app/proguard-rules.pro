# R8 rules for release builds. R8 shrinks and renames the whole app in one pass, so these cover
# the library modules too (UserLAndLibrary, terminal, vnc, adbndk).
#
# Libraries that ship their own rules need nothing here: Room, the Moshi-generated adapters
# (@JsonClass), Play Billing, kotlinx.coroutines. proguard-android-optimize.txt already keeps
# native method names and Parcelable CREATORs.

# Line numbers in crash reports. Play receives the mapping file with the bundle to turn the
# renamed classes back into readable ones.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Called by name from native code (libdroid_files.c), so they must keep their names.
-keepclassmembers class tech.ula.library.ServerService {
    void open(int, int, java.lang.String, int, int);
    int staticMethod(int);
}

# Named as argument types in navigation/nav_graph.xml, which the Navigation library loads by
# class name.
-keepnames class tech.ula.library.model.entities.App
-keepnames class tech.ula.library.model.entities.Filesystem
-keepnames class tech.ula.library.model.entities.Session

# Gson works by reflection. App is stored in preferences as JSON by field name, and older
# versions' saved JSON has to keep loading, so its fields keep their names.
-keepclassmembers class tech.ula.library.model.entities.App {
    <fields>;
}
# TypeToken reads the generic type from the anonymous subclass's signature.
-keepattributes Signature
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.reflect.TypeToken { *; }

# Optional dependencies that are not bundled and never used: commons-compress's zstd, brotli and
# xz codecs (OCI layers are gzip), and OkHttp's alternative TLS providers.
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.tukaani.xz.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
