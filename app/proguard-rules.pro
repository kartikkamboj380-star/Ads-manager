# Keep the ML Kit and AndroidX model classes; they ship their own rules.
# The app's own code is minified with default optimizations.

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep model classes used across processes (Service / ViewModels)
-keep class com.smartai.adsmanager.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
