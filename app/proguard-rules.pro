# ONNX Runtime loads classes through JNI
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
# Keep our error types (used in user-facing messages)
-keep class com.pureenhance.ai.utilities.EnhanceException { *; }
-keep class com.pureenhance.ai.utilities.EnhanceException$* { *; }
