package omnimesh.command1.utils

import android.content.Context
import android.provider.Settings
import java.util.UUID

object DeviceUtils {

    // 💡 We need a stable ID per device so we can track packet origins
    // and avoid sending a packet back to the phone that created it.
    // Android ID is unique per device + app install.
    fun getDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        return androidId ?: UUID.randomUUID().toString()
    }

    fun getEndpointName(context: Context): String {
        val deviceId = getDeviceId(context)
        return "OmniMesh_${deviceId.takeLast(6)}"
    }
}
