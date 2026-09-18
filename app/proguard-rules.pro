# Mapbox
-keep class com.mapbox.** { *; }
-dontwarn com.mapbox.**
# Firestore model (reflection-based deserialization)
-keep class com.drynav.app.domain.model.** { *; }
