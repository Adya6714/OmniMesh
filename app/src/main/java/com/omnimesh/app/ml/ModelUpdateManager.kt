package omnimesh.command1.ml

import android.content.Context
import android.util.Log
import com.google.firebase.ml.modeldownloader.CustomModel
import com.google.firebase.ml.modeldownloader.CustomModelDownloadConditions
import com.google.firebase.ml.modeldownloader.DownloadType
import com.google.firebase.ml.modeldownloader.FirebaseModelDownloader
import kotlinx.coroutines.tasks.await

private const val TAG = "ModelUpdateManager"

// 💡 Firebase ML Model Hosting lets you upload new .tflite files
// to Firebase Console → Machine Learning → Custom Models.
// This class downloads them silently in background.
// On next app launch, the new model is used automatically.
class ModelUpdateManager(private val context: Context) {

    private val conditions = CustomModelDownloadConditions.Builder()
        .requireWifi()  // Only download on WiFi — models can be 5-10MB
        .build()

    suspend fun checkAndUpdateModel(modelName: String): String? {
        return try {
            val model: CustomModel = FirebaseModelDownloader.getInstance()
                .getModel(modelName, DownloadType.LOCAL_MODEL_UPDATE_IN_BACKGROUND, conditions)
                .await()

            val modelFile = model.file
            if (modelFile != null) {
                Log.d(TAG, "Using updated model: $modelName (${modelFile.length() / 1024}KB)")
                modelFile.absolutePath
            } else {
                Log.d(TAG, "Using bundled model: $modelName")
                null  // null = use bundled asset
            }
        } catch (e: Exception) {
            Log.w(TAG, "Model update check failed for $modelName: ${e.message}")
            null
        }
    }
}
