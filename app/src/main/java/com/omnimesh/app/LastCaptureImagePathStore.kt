package omnimesh.command1

import java.util.concurrent.atomic.AtomicReference

/** Holds the latest injury-camera JPEG path for background services (e.g. collapse fusion). */
object LastCaptureImagePathStore {
    private val pathRef = AtomicReference<String?>(null)

    fun set(path: String?) {
        pathRef.set(path)
    }

    fun get(): String? = pathRef.get()
}
