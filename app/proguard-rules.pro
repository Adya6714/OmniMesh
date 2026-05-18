# Keep ML Kit classes
-keep class com.google.mlkit.** { *; }
-keep class com.google.mediapipe.** { *; }

# Keep TFLite
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.** { *; }

# Keep Firebase
-keep class com.google.firebase.** { *; }

# Keep Gemini / Google AI SDK
-keep class com.google.ai.** { *; }
-keep class com.google.ai.client.generativeai.** { *; }

# Keep Room database subclasses
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }

# Keep annotations (needed by Room, Retrofit, etc.)
-keepattributes *Annotation*

# Keep data classes used with Room and JSON parsing
-keep class omnimesh.command1.data.** { *; }

# Keep Nearby Connections
-keep class com.google.android.gms.nearby.** { *; }

# Prevent stripping of Kotlin metadata needed by reflection
-keepattributes RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }

# Suppress R8 missing class warnings (MediaPipe protos, AutoValue javax types)
-dontwarn com.google.mediapipe.proto.CalculatorProfileProto$CalculatorProfile
-dontwarn com.google.mediapipe.proto.GraphTemplateProto$CalculatorGraphTemplate
-dontwarn javax.lang.model.SourceVersion
-dontwarn javax.lang.model.element.Element
-dontwarn javax.lang.model.element.ElementKind
-dontwarn javax.lang.model.type.TypeMirror
-dontwarn javax.lang.model.type.TypeVisitor
-dontwarn javax.lang.model.util.SimpleTypeVisitor8
