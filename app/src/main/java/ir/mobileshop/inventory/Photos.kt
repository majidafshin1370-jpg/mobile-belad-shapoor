package ir.mobileshop.inventory

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/** Changes every time a photo arrives from the server, so lists redraw and show it. */
val LocalPhotoTick = compositionLocalOf { 0 }

/**
 * Product photos live as small JPEG files in the app's private storage, named `<uuid>_<photoAt>.jpg`.
 * The number in the name changes with every new photo, so a replaced photo is never mixed up with the old one.
 */
object ProductPhotos {
    private const val MAX_SIDE = 900
    private const val JPEG_QUALITY = 80

    fun dir(context: Context): File = File(context.filesDir, "photos").apply { mkdirs() }

    fun file(context: Context, uuid: String, photoAt: Long): File = File(dir(context), "${uuid}_$photoAt.jpg")

    /**
     * Shrinks the picture (max 900 px, upright according to its EXIF), stores it for [uuid] and returns the
     * new photo number, or null when the picture cannot be read.
     */
    fun save(context: Context, uuid: String, open: () -> InputStream?): Long? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open()?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE)
            }
            val decoded = open()?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            val rotation = open()?.use { rotationOf(it) } ?: 0

            val scale = MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height)
            val matrix = Matrix().apply {
                if (scale < 1f) postScale(scale, scale)
                if (rotation != 0) postRotate(rotation.toFloat())
            }
            val finalBitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

            val at = System.currentTimeMillis()
            val target = file(context, uuid, at)
            target.outputStream().use { finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            if (finalBitmap !== decoded) decoded.recycle()
            deleteOld(context, uuid, keepAt = at)
            at
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** Removes older photo files of this product. */
    fun deleteOld(context: Context, uuid: String, keepAt: Long) {
        val keep = file(context, uuid, keepAt).name
        dir(context).listFiles()?.forEach {
            if (it.name.startsWith("${uuid}_") && it.name != keep) it.delete()
        }
    }

    /** Loads the photo scaled so that its longer side is about [maxSide] pixels; null if the file is missing. */
    fun load(context: Context, uuid: String, photoAt: Long, maxSide: Int): Bitmap? {
        val f = file(context, uuid, photoAt)
        if (!f.exists()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
            }
            BitmapFactory.decodeFile(f.path, opts)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private fun sampleSize(w: Int, h: Int, target: Int): Int {
        var sample = 1
        while (maxOf(w, h) / (sample * 2) >= target) sample *= 2
        return sample
    }

    private fun rotationOf(stream: InputStream): Int = try {
        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0
    }
}

/** The product photo, decoded off the main thread; null while loading or when there is no photo on this phone yet. */
@Composable
fun rememberProductPhoto(uuid: String, photoAt: Long, maxSide: Int): ImageBitmap? {
    val context = LocalContext.current
    val tick = LocalPhotoTick.current
    val bitmap by produceState<ImageBitmap?>(null, uuid, photoAt, maxSide, tick) {
        value = if (photoAt <= 0L) null else withContext(Dispatchers.IO) {
            ProductPhotos.load(context, uuid, photoAt, maxSide)?.asImageBitmap()
        }
    }
    return bitmap
}

/** Square thumbnail: the photo when there is one, otherwise the category icon. Tap on a photo opens it large. */
@Composable
fun ProductThumb(p: Product, fallback: ImageVector, size: Dp = 64.dp) {
    val photo = rememberProductPhoto(p.uuid, p.photoAt, 256)
    var showLarge by remember { mutableStateOf(false) }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(PageGrey)
            .then(if (photo != null) Modifier.clickable { showLarge = true } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (photo != null) {
            Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(fallback, null, tint = InkGrey, modifier = Modifier.size(size / 2))
        }
    }
    if (showLarge) PhotoDialog(p) { showLarge = false }
}

@Composable
fun PhotoDialog(p: Product, onDismiss: () -> Unit) {
    val photo = rememberProductPhoto(p.uuid, p.photoAt, 1400)
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            if (photo != null) Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}
