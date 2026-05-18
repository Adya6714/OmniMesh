package omnimesh.command1.responder

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import omnimesh.command1.data.PacketRepository
import omnimesh.command1.data.TriagePacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class QrTriageCardManager(private val repository: PacketRepository) {

    companion object {
        private const val TAG = "QrTriageCard"
        private const val QR_SIZE_PX = 400
    }

    suspend fun generateQrBitmap(packet: TriagePacket): Bitmap? =
        withContext(Dispatchers.Default) {
            try {
                val card = TriageCard(
                    packetId = packet.id,
                    urgency = packet.urgency,
                    injuryText = packet.injury,
                    foundAt = packet.ts,
                    foundByDeviceId = packet.originDeviceId,
                )
                val payload = card.toQrPayload()
                encodeQr(payload, QR_SIZE_PX)
            } catch (e: Exception) {
                Log.w(TAG, "QR generation failed: ${e.message}")
                null
            }
        }

    suspend fun resolveScannedQr(qrPayload: String): TriagePacket? =
        withContext(Dispatchers.IO) {
            val card = TriageCard.fromQrPayload(qrPayload) ?: return@withContext null
            Log.d(TAG, "QR scanned for packet: ${card.packetId}")
            repository.getPacketById(card.packetId)
        }

    private fun encodeQr(content: String, size: Int): Bitmap? {
        return try {
            val hints = mapOf(
                com.google.zxing.EncodeHintType.ERROR_CORRECTION to
                    com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M,
                com.google.zxing.EncodeHintType.MARGIN to 2,
            )
            val bitMatrix = com.google.zxing.MultiFormatWriter().encode(
                content,
                com.google.zxing.BarcodeFormat.QR_CODE,
                size,
                size,
                hints
            )
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            Log.w(TAG, "ZXing encoding failed: ${e.message}")
            generatePlaceholderQr(content, size)
        }
    }

    private fun generatePlaceholderQr(content: String, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        val hash = content.hashCode()
        for (x in 0 until size) {
            for (y in 0 until size) {
                val cell = ((x / 10) + (y / 10) + hash) % 2 == 0
                bitmap.setPixel(x, y, if (cell) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
