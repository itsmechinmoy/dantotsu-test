package ani.dantotsu.util

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.PI
import kotlin.math.sin

object ImageScaler {
    private const val TAG = "ImageScaler"
    private var isVipsLoaded = false

    init {
        try {
            System.loadLibrary("vips")
            System.loadLibrary("dantotsuvips")
            isVipsLoaded = initVips()
            Log.d(TAG, "libvips and JNI wrapper loaded successfully: $isVipsLoaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load libvips or JNI wrapper: ${e.message}")
            isVipsLoaded = false
        }
    }

    private external fun initVips(): Boolean
    private external fun scaleAndSharpenVips(bitmap: Bitmap, scaleFactor: Double, sharpenStrength: Double): Bitmap

    fun process(bitmap: Bitmap, scaleEnabled: Boolean, sharpenStrength: Float): Bitmap {
        if (!isVipsLoaded) {
            var processed = bitmap
            if (scaleEnabled) {
                processed = lanczos3Scale(processed, processed.width * 2, processed.height * 2)
            }
            if (sharpenStrength > 0f) {
                processed = sharpen(processed, sharpenStrength)
            }
            return processed
        }

        val scaleFactor = if (scaleEnabled) 2.0 else 1.0
        val sharpenVal = sharpenStrength.toDouble()
        return try {
            scaleAndSharpenVips(bitmap, scaleFactor, sharpenVal)
        } catch (e: Exception) {
            Log.e(TAG, "JNI scaleAndSharpenVips failed: ${e.message}")
            bitmap
        }
    }

    private fun sinc(x: Float): Float {
        if (x == 0f) return 1f
        val px = x * PI.toFloat()
        return sin(px) / px
    }

    private fun lanczosKernel(x: Float, a: Int): Float {
        val ax = kotlin.math.abs(x)
        if (ax < 0.0001f) return 1f
        if (ax >= a) return 0f
        return sinc(ax) * sinc(ax / a)
    }

    fun lanczos3Scale(src: Bitmap, dstW: Int, dstH: Int): Bitmap {
        if (src.width == dstW && src.height == dstH) return src

        val srcW = src.width
        val srcH = src.height
        val scaleX = srcW.toFloat() / dstW
        val scaleY = srcH.toFloat() / dstH
        val a = 3

        val tempPixels = IntArray(dstW * srcH)
        val srcPixels = IntArray(srcW * srcH)
        src.getPixels(srcPixels, 0, srcW, 0, 0, srcW, srcH)

        for (y in 0 until srcH) {
            val srcRowOffset = y * srcW
            val tempRowOffset = y * dstW

            for (x in 0 until dstW) {
                val srcX = x * scaleX
                val srcXFloor = kotlin.math.floor(srcX).toInt()

                var rSum = 0f
                var gSum = 0f
                var bSum = 0f
                var aSum = 0f
                var weightSum = 0f

                val start = (srcXFloor - a + 1).coerceAtLeast(0)
                val end = (srcXFloor + a).coerceAtLeast(0).coerceAtMost(srcW - 1)

                for (i in start..end) {
                    val weight = lanczosKernel(srcX - i, a)
                    if (weight == 0f) continue

                    val pixel = srcPixels[srcRowOffset + i]
                    rSum += ((pixel ushr 16) and 0xFF) * weight
                    gSum += ((pixel ushr 8) and 0xFF) * weight
                    bSum += (pixel and 0xFF) * weight
                    aSum += ((pixel ushr 24) and 0xFF) * weight
                    weightSum += weight
                }

                if (weightSum > 0f) {
                    val r = (rSum / weightSum).toInt().coerceIn(0, 255)
                    val g = (gSum / weightSum).toInt().coerceIn(0, 255)
                    val b = (bSum / weightSum).toInt().coerceIn(0, 255)
                    val alpha = (aSum / weightSum).toInt().coerceIn(0, 255)
                    tempPixels[tempRowOffset + x] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
                } else {
                    tempPixels[tempRowOffset + x] = srcPixels[srcRowOffset + srcXFloor.coerceIn(0, srcW - 1)]
                }
            }
        }

        val dstPixels = IntArray(dstW * dstH)

        for (x in 0 until dstW) {
            for (y in 0 until dstH) {
                val srcY = y * scaleY
                val srcYFloor = kotlin.math.floor(srcY).toInt()

                var rSum = 0f
                var gSum = 0f
                var bSum = 0f
                var aSum = 0f
                var weightSum = 0f

                val start = (srcYFloor - a + 1).coerceAtLeast(0)
                val end = (srcYFloor + a).coerceAtLeast(0).coerceAtMost(srcH - 1)

                for (j in start..end) {
                    val weight = lanczosKernel(srcY - j, a)
                    if (weight == 0f) continue

                    val pixel = tempPixels[j * dstW + x]
                    rSum += ((pixel ushr 16) and 0xFF) * weight
                    gSum += ((pixel ushr 8) and 0xFF) * weight
                    bSum += (pixel and 0xFF) * weight
                    aSum += ((pixel ushr 24) and 0xFF) * weight
                    weightSum += weight
                }

                if (weightSum > 0f) {
                    val r = (rSum / weightSum).toInt().coerceIn(0, 255)
                    val g = (gSum / weightSum).toInt().coerceIn(0, 255)
                    val b = (bSum / weightSum).toInt().coerceIn(0, 255)
                    val alpha = (aSum / weightSum).toInt().coerceIn(0, 255)
                    dstPixels[y * dstW + x] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
                } else {
                    dstPixels[y * dstW + x] = tempPixels[srcYFloor.coerceIn(0, srcH - 1) * dstW + x]
                }
            }
        }

        val output = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        output.setPixels(dstPixels, 0, dstW, 0, 0, dstW, dstH)
        return output
    }

    fun sharpen(src: Bitmap, strength: Float): Bitmap {
        val width = src.width
        val height = src.height
        val srcPixels = IntArray(width * height)
        val dstPixels = IntArray(width * height)
        src.getPixels(srcPixels, 0, width, 0, 0, width, height)

        val k = strength
        val center = 1f + 4f * k

        for (y in 1 until height - 1) {
            val offset = y * width
            for (x in 1 until width - 1) {
                val centerPixel = srcPixels[offset + x]
                val topPixel = srcPixels[offset - width + x]
                val bottomPixel = srcPixels[offset + width + x]
                val leftPixel = srcPixels[offset + x - 1]
                val rightPixel = srcPixels[offset + x + 1]

                val r = (Color.red(centerPixel) * center -
                        (Color.red(topPixel) + Color.red(bottomPixel) + Color.red(leftPixel) + Color.red(rightPixel)) * k)
                        .toInt().coerceIn(0, 255)

                val g = (Color.green(centerPixel) * center -
                        (Color.green(topPixel) + Color.green(bottomPixel) + Color.green(leftPixel) + Color.green(rightPixel)) * k)
                        .toInt().coerceIn(0, 255)

                val b = (Color.blue(centerPixel) * center -
                        (Color.blue(topPixel) + Color.blue(bottomPixel) + Color.blue(leftPixel) + Color.blue(rightPixel)) * k)
                        .toInt().coerceIn(0, 255)

                val a = Color.alpha(centerPixel)

                dstPixels[offset + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        for (x in 0 until width) {
            dstPixels[x] = srcPixels[x]
            dstPixels[(height - 1) * width + x] = srcPixels[(height - 1) * width + x]
        }
        for (y in 0 until height) {
            dstPixels[y * width] = srcPixels[y * width]
            dstPixels[y * width + width - 1] = srcPixels[y * width + width - 1]
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(dstPixels, 0, width, 0, 0, width, height)
        return output
    }
}
