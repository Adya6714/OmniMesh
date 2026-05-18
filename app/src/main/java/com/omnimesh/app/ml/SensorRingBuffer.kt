package omnimesh.command1.ml

// 💡 A ring buffer overwrites old data with new data.
// When it's full (250 samples), the oldest sample gets replaced
// by the newest one. We always have the freshest 5 seconds.
class SensorRingBuffer(private val capacity: Int = 250, private val features: Int = 6) {

    private val buffer = Array(capacity) { FloatArray(features) }
    private var head = 0       // where next write goes
    private var count = 0      // how many samples we've collected so far

    fun push(ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float) {
        buffer[head] = floatArrayOf(ax, ay, az, gx, gy, gz)
        head = (head + 1) % capacity
        if (count < capacity) count++
    }

    // 💡 True once we have a full 5 seconds of data.
    // Don't run inference before this — partial data gives garbage results.
    fun isFull(): Boolean = count >= capacity

    // Flatten to [250 × 6 = 1500] float array for TFLite input
    fun toFlatArray(): FloatArray {
        val result = FloatArray(capacity * features)
        for (i in 0 until capacity) {
            val bufferIndex = (head - capacity + i + capacity) % capacity
            val sample = buffer[bufferIndex]
            for (f in 0 until features) {
                result[i * features + f] = sample[f]
            }
        }
        return result
    }

    // Quick check: has there been any large spike recently?
    // 💡 Pre-filter — only run the expensive LSTM when G-force spikes above 4G.
    // Saves battery by skipping inference during normal movement.
    fun hasRecentSpike(threshold: Float = 4.0f): Boolean {
        val recentSamples = minOf(count, 50)  // last 1 second
        for (i in 0 until recentSamples) {
            val idx = (head - 1 - i + capacity) % capacity
            val sample = buffer[idx]
            val magnitude = Math.sqrt(
                (sample[0] * sample[0] + sample[1] * sample[1] + sample[2] * sample[2]).toDouble()
            ).toFloat()
            if (magnitude > threshold) return true
        }
        return false
    }

    fun clear() {
        head = 0
        count = 0
    }
}
