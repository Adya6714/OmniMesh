package omnimesh.command1.ml

// 💡 These mirror the 6 output classes of the LSTM model exactly.
// The order matters — it must match the training label encoding.
enum class MotionClass(val label: String, val priority: Int) {
    COLLAPSE_UNCONSCIOUS("collapse_unconscious", 0),  // highest priority
    COLLAPSE_MOVING("collapse_moving", 1),
    FALL_PHONE("fall_phone", 2),           // phone dropped, person probably fine
    CAR_CRASH("car_crash", 3),
    RUNNING("running", 4),
    NORMAL("normal", 5)
}

data class MotionSignal(
    val motionClass: MotionClass,
    val confidence: Float,
    val collapseUnconscious: Float,  // raw probability — fed into meta-classifier
    val collapseMoving: Float,
    val allProbabilities: FloatArray
)
