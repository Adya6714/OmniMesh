package omnimesh.command1.ml

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

private const val TAG = "MotionClassifier"
private const val MODEL_FILE = "Omnimesh Motion Classifier v1.tflite"

// 💡 These must exactly match what the Python training script used.
// SEQUENCE_LENGTH = 5 seconds × 50Hz = 250 samples
// FEATURES = ax, ay, az, gx, gy, gz
private const val SEQUENCE_LENGTH = 250
private const val FEATURES = 6
private const val NUM_CLASSES = 6

class MotionStateClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null

    init {
        try {
            val model = loadModelFromAssets()
            val options = Interpreter.Options().apply {
                numThreads = 2
                useNNAPI = true  // 💡 NNAPI = Android Neural Networks API
                                 // Uses phone's NPU/DSP chip if available — 3-5× faster
            }
            interpreter = Interpreter(model, options)
            Log.d(TAG, "Motion classifier loaded successfully")
        } catch (e: Exception) {
            // 💡 Model file won't exist until Step 8 (ML training).
            // The classifier gracefully returns NORMAL until then.
            Log.w(TAG, "Motion model not found — running in stub mode: ${e.message}")
        }
    }

    fun classify(sensorBuffer: FloatArray): MotionSignal {
        val interp = interpreter ?: return stubNormalSignal()

        // 💡 Reshape flat FloatArray into [1, 250, 6] tensor
        // TFLite expects: [batch_size, sequence_length, features]
        val input = Array(1) {
            Array(SEQUENCE_LENGTH) { t ->
                FloatArray(FEATURES) { f ->
                    val index = t * FEATURES + f
                    if (index < sensorBuffer.size) sensorBuffer[index] else 0f
                }
            }
        }

        val output = Array(1) { FloatArray(NUM_CLASSES) }

        try {
            interp.run(input, output)
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed: ${e.message}")
            return stubNormalSignal()
        }

        val probs = output[0]
        val topIndex = probs.indices.maxByOrNull { probs[it] } ?: 5
        val topClass = MotionClass.entries.getOrNull(topIndex) ?: MotionClass.NORMAL

        return MotionSignal(
            motionClass = topClass,
            confidence = probs[topIndex],
            collapseUnconscious = probs[0],
            collapseMoving = probs[1],
            allProbabilities = probs
        )
    }

    private fun loadModelFromAssets(): MappedByteBuffer {
        val fd = context.assets.openFd(MODEL_FILE)
        val stream = FileInputStream(fd.fileDescriptor)
        return stream.channel.map(
            FileChannel.MapMode.READ_ONLY,
            fd.startOffset,
            fd.declaredLength
        )
    }

    // 💡 Stub returns NORMAL with high confidence — safe default
    // while the real model file isn't bundled yet
    private fun stubNormalSignal() = MotionSignal(
        motionClass = MotionClass.NORMAL,
        confidence = 0.99f,
        collapseUnconscious = 0.01f,
        collapseMoving = 0.01f,
        allProbabilities = FloatArray(NUM_CLASSES) { if (it == 5) 0.99f else 0.01f }
    )

    fun close() {
        interpreter?.close()
    }
}
