package com.pureenhance.ai.utilities

/** Every failure the user can see. [userMessage] is always human-friendly. */
sealed class EnhanceException(val userMessage: String, cause: Throwable? = null) : Exception(userMessage, cause) {
    class ModelMissing(val modelName: String) :
        EnhanceException("The AI model \"$modelName\" is not included in this build. See README.md > Models.")

    class ModelLoadFailed(val modelName: String, cause: Throwable?) :
        EnhanceException("The AI engine couldn't start on this device. Please try again or restart the app.", cause)

    class UnsupportedImage(cause: Throwable? = null) :
        EnhanceException("This image can't be opened. It may be damaged or in an unsupported format.", cause)

    class OutOfMemory(cause: Throwable? = null) :
        EnhanceException(
            "This photo is too large for this device, even in optimized mode. Try 2× scale or a smaller photo.",
            cause,
        )

    class Overheated :
        EnhanceException("Your device is getting too hot. Let it cool down for a few minutes and try again.")

    class StorageFailed(cause: Throwable? = null) :
        EnhanceException("Couldn't save the photo. Check that your device has free storage and try again.", cause)

    class InferenceFailed(cause: Throwable? = null) :
        EnhanceException("The AI engine stopped unexpectedly while processing this photo.", cause)
}
