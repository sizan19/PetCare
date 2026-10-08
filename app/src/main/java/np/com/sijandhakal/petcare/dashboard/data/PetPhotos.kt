package np.com.sijandhakal.petcare.dashboard.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pet photos are stored inline in the pet's Firestore document as a Base64 JPEG string.
 * Firestore documents are capped at 1 MiB, so photos are shrunk to [MAX_SIDE_PX] and
 * compressed before encoding, which keeps each one well under 150 KB.
 */
object PetPhotos {

    private const val MAX_SIDE_PX = 512
    private const val JPEG_QUALITY = 75

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Decoded bitmaps keyed by the Base64 string's hash, so lists don't re-decode on every update.
    private val cache = object : LruCache<Int, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }

    /** Reads, shrinks and encodes a picked image off the main thread; delivers null if it can't be read. */
    fun encode(context: Context, uri: Uri, onResult: (String?) -> Unit) {
        val resolver = context.applicationContext.contentResolver
        executor.execute {
            val encoded = try {
                val source = ImageDecoder.createSource(resolver, uri)
                val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    // Decode straight to the target size instead of loading a full-resolution photo.
                    val scale = MAX_SIDE_PX.toFloat() / max(info.size.width, info.size.height)
                    if (scale < 1f) {
                        decoder.setTargetSize(
                            (info.size.width * scale).roundToInt(),
                            (info.size.height * scale).roundToInt()
                        )
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                toBase64(bitmap)
            } catch (e: Exception) {
                null
            }
            mainHandler.post { onResult(encoded) }
        }
    }

    /** Encodes a bitmap (e.g. a camera preview), shrinking it first if needed. */
    fun encode(bitmap: Bitmap): String {
        val scale = MAX_SIDE_PX.toFloat() / max(bitmap.width, bitmap.height)
        val sized = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt(), true)
        } else {
            bitmap
        }
        return toBase64(sized)
    }

    /** Decodes a stored photo; null for an empty or unreadable string. */
    fun decode(base64: String): Bitmap? {
        if (base64.isBlank()) return null
        val key = base64.hashCode()
        cache.get(key)?.let { return it }
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { cache.put(key, it) }
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun toBase64(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
