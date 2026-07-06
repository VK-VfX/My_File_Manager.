# Keep Media3 session/service classes used via reflection by the framework.
-keep class androidx.media3.session.MediaSessionService { *; }
-keep class androidx.media3.session.MediaLibraryService { *; }

# ViewModels are instantiated reflectively by the default ViewModelProvider factory.
-keep class * extends androidx.lifecycle.ViewModel { <init>(); }
-keep class * extends androidx.lifecycle.AndroidViewModel { <init>(android.app.Application); }
