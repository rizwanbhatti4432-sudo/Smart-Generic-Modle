package com.example.data.photo

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object ImageEditorUtils {

    fun applyEditsToBitmap(sourceBitmap: Bitmap, settings: PhotoEditSettings): Bitmap {
        // 1. Compute overall ColorMatrix
        val masterMatrix = ColorMatrix()

        // Apply Preset Filter Base Matrix
        val filterMatrix = getFilterColorMatrix(settings.filter)
        masterMatrix.postConcat(filterMatrix)

        // Apply Brightness
        if (settings.brightness != 0f) {
            val brightnessMatrix = ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, settings.brightness,
                    0f, 1f, 0f, 0f, settings.brightness,
                    0f, 0f, 1f, 0f, settings.brightness,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            masterMatrix.postConcat(brightnessMatrix)
        }

        // Apply Contrast
        if (settings.contrast != 1.0f) {
            val scale = settings.contrast
            val translate = (-0.5f * scale + 0.5f) * 255f
            val contrastMatrix = ColorMatrix(
                floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            masterMatrix.postConcat(contrastMatrix)
        }

        // Apply Saturation
        if (settings.saturation != 1.0f) {
            val satMatrix = ColorMatrix()
            satMatrix.setSaturation(settings.saturation)
            masterMatrix.postConcat(satMatrix)
        }

        // Apply Warmth (Red boost, Blue reduction)
        if (settings.warmth != 0f) {
            val warmVal = settings.warmth
            val warmthMatrix = ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, warmVal * 0.8f,
                    0f, 1f, 0f, 0f, warmVal * 0.3f,
                    0f, 0f, 1f, 0f, -warmVal * 0.8f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            masterMatrix.postConcat(warmthMatrix)
        }

        // 2. Compute Transform Matrix (Rotation & Flipping)
        val transformMatrix = Matrix().apply {
            if (settings.flipHorizontal || settings.flipVertical) {
                val sx = if (settings.flipHorizontal) -1f else 1f
                val sy = if (settings.flipVertical) -1f else 1f
                preScale(sx, sy)
            }
            if (settings.rotationDegrees != 0f) {
                postRotate(settings.rotationDegrees)
            }
        }

        // 3. Create transformed intermediate or filtered bitmap
        val transformedSource = if (!transformMatrix.isIdentity) {
            Bitmap.createBitmap(
                sourceBitmap,
                0,
                0,
                sourceBitmap.width,
                sourceBitmap.height,
                transformMatrix,
                true
            )
        } else {
            sourceBitmap
        }

        val resultBitmap = Bitmap.createBitmap(
            transformedSource.width,
            transformedSource.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(resultBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(masterMatrix)
        }
        canvas.drawBitmap(transformedSource, 0f, 0f, paint)

        return resultBitmap
    }

    private fun getFilterColorMatrix(filter: PhotoFilter): ColorMatrix {
        val cm = ColorMatrix()
        when (filter) {
            PhotoFilter.ORIGINAL -> {
                // Identity
            }
            PhotoFilter.VIBRANT -> {
                cm.setSaturation(1.4f)
                val boost = ColorMatrix(
                    floatArrayOf(
                        1.08f, 0f, 0f, 0f, 6f,
                        0f, 1.08f, 0f, 0f, 6f,
                        0f, 0f, 1.08f, 0f, 6f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(boost)
            }
            PhotoFilter.WARM_SUNSET -> {
                val warmMatrix = ColorMatrix(
                    floatArrayOf(
                        1.18f, 0f, 0f, 0f, 22f,
                        0f, 1.05f, 0f, 0f, 12f,
                        0f, 0f, 0.88f, 0f, -15f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(warmMatrix)
            }
            PhotoFilter.COOL_OCEAN -> {
                val coolMatrix = ColorMatrix(
                    floatArrayOf(
                        0.88f, 0f, 0f, 0f, -12f,
                        0f, 1.08f, 0f, 0f, 10f,
                        0f, 0f, 1.25f, 0f, 24f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(coolMatrix)
            }
            PhotoFilter.CYBERPUNK -> {
                val cyberMatrix = ColorMatrix(
                    floatArrayOf(
                        1.28f, 0f, 0.2f, 0f, 15f,
                        0f, 0.9f, 0.1f, 0f, -10f,
                        0.15f, 0.2f, 1.35f, 0f, 25f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(cyberMatrix)
            }
            PhotoFilter.NOIR_BW -> {
                cm.setSaturation(0f)
                val scale = 1.35f
                val translate = (-0.5f * scale + 0.5f) * 255f
                val contrast = ColorMatrix(
                    floatArrayOf(
                        scale, 0f, 0f, 0f, translate,
                        0f, scale, 0f, 0f, translate,
                        0f, 0f, scale, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(contrast)
            }
            PhotoFilter.SEPIA -> {
                val sepiaMatrix = ColorMatrix(
                    floatArrayOf(
                        0.393f, 0.769f, 0.189f, 0f, 0f,
                        0.349f, 0.686f, 0.168f, 0f, 0f,
                        0.272f, 0.534f, 0.131f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(sepiaMatrix)
            }
            PhotoFilter.DRAMATIC -> {
                val scale = 1.45f
                val translate = (-0.5f * scale + 0.5f) * 255f - 8f
                val dramatic = ColorMatrix(
                    floatArrayOf(
                        scale, 0f, 0f, 0f, translate,
                        0f, scale, 0f, 0f, translate,
                        0f, 0f, scale, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(dramatic)
                val sat = ColorMatrix()
                sat.setSaturation(0.85f)
                cm.postConcat(sat)
            }
        }
        return cm
    }

    fun decodeBitmapFromUri(context: Context, uri: Uri, maxDim: Int = 1440): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            var inSampleSize = 1
            if (options.outHeight > maxDim || options.outWidth > maxDim) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= maxDim && (halfWidth / inSampleSize) >= maxDim) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun decodeBitmapFromResource(context: Context, resId: Int, maxDim: Int = 1440): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, resId, options)

        var inSampleSize = 1
        if (options.outHeight > maxDim || options.outWidth > maxDim) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while ((halfHeight / inSampleSize) >= maxDim && (halfWidth / inSampleSize) >= maxDim) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeResource(context.resources, resId, decodeOptions)
    }

    fun bitmapToBase64(bitmap: Bitmap, maxDim: Int = 1024, quality: Int = 85): String {
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val targetW: Int
            val targetH: Int
            if (ratio > 1f) {
                targetW = maxDim
                targetH = (maxDim / ratio).toInt()
            } else {
                targetH = maxDim
                targetW = (maxDim * ratio).toInt()
            }
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        } else {
            bitmap
        }

        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        val byteArray = stream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, title: String): Uri? {
        val filename = "${title}_${System.currentTimeMillis()}.jpg"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SmartGenericModle")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                    uri
                } else null
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appDir = File(picturesDir, "SmartGenericModle").apply { mkdirs() }
                val file = File(appDir, filename)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            null
        }
    }
}
