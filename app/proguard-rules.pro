# Keep Media3 session/service classes used via reflection by the framework.
-keep class androidx.media3.session.MediaSessionService { *; }
-keep class androidx.media3.session.MediaLibraryService { *; }

# ViewModels are instantiated reflectively by the default ViewModelProvider factory.
-keep class * extends androidx.lifecycle.ViewModel { <init>(); }
-keep class * extends androidx.lifecycle.AndroidViewModel { <init>(android.app.Application); }

# Apache Commons Compress references many optional codec libraries (zstd, brotli, lzma via xz,
# pack200, etc.) that we don't ship - we only use ZIP and 7z (LZMA2 via org.tukaani:xz, which
# is on the classpath). Silence R8's "missing class" warnings for the codecs we don't include
# so the minified release build doesn't fail on them.
-dontwarn org.apache.commons.compress.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.tukaani.xz.**
-dontwarn org.objectweb.asm.**
# Keep the 7z entry points we call reflectively-adjacent codecs through.
-keep class org.tukaani.xz.** { *; }
