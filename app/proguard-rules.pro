# Keep Media3 session/service classes used via reflection by the framework.
-keep class androidx.media3.session.MediaSessionService { *; }
-keep class androidx.media3.session.MediaLibraryService { *; }

# Google API client uses reflection for JSON (de)serialization of model classes.
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault
-keep class com.google.api.services.drive.model.** { *; }
-keep class com.google.api.client.** { *; }
-dontwarn com.google.api.client.**
-dontwarn org.apache.http.**
-dontwarn org.joda.time.**
