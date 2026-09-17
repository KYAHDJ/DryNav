package com.drynav.app.presentation.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Small on-device classifier exported from Google Teachable Machine.
 *
 * The model is intentionally kept local to the app: the captured image never
 * needs to be uploaded to a server just to perform this check.
 */
class FloodImageClassifier(
    private val context: Context
) : AutoCloseable {

    private val interpreter: Interpreter
    private val labels: List<String>
    private val inputWidth: Int
    private val inputHeight: Int
    private val inputType: DataType
    private val inputQuantScale: Float
    private val inputQuantZeroPoint: Int
    private val outputType: DataType
    private val outputQuantScale: Float
    private val outputQuantZeroPoint: Int

    init {
        val modelBytes = context.assets.open(MODEL_FILE).use { it.readBytes() }
        val modelBuffer = ByteBuffer.allocateDirect(modelBytes.size)
            .order(ByteOrder.nativeOrder())
        modelBuffer.put(modelBytes)
        modelBuffer.rewind()

        interpreter = Interpreter(
            modelBuffer,
            Interpreter.Options().apply { setNumThreads(2) }
        )

        labels = context.assets.open(LABELS_FILE).bufferedReader().useLines { lines ->
            lines.map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { line ->
                    // Teachable Machine labels are normally "0 FLOOD".
                    line.replaceFirst(Regex("^\\d+\\s+"), "")
                }
                .toList()
        }

        val input = interpreter.getInputTensor(0)
        val shape = input.shape()
        require(shape.size == 4) { "Expected a 4D image input, got ${shape.contentToString()}" }
        inputHeight = shape[1]
        inputWidth = shape[2]
        inputType = input.dataType()
        inputQuantScale = input.quantizationParams().scale
        inputQuantZeroPoint = input.quantizationParams().zeroPoint

        val output = interpreter.getOutputTensor(0)
        outputType = output.dataType()
        outputQuantScale = output.quantizationParams().scale
        outputQuantZeroPoint = output.quantizationParams().zeroPoint
    }

    fun classify(uri: Uri): ImageAnalysis {
        val bitmap = decodeBitmap(uri)
            ?: return ImageAnalysis.error("We couldn't read that photo. Please take it again.")

        return try {
            val resized = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
            val input = createInputBuffer(resized)
            val outputElements = interpreter.getOutputTensor(0).numElements()

            val rawOutput: Any = when (outputType) {
                DataType.FLOAT32 -> Array(1) { FloatArray(outputElements) }
                DataType.UINT8, DataType.INT8 -> Array(1) { ByteArray(outputElements) }
                else -> Array(1) { FloatArray(outputElements) }
            }

            interpreter.run(input, rawOutput)

            val scores = FloatArray(outputElements)
            when (rawOutput) {
                is Array<*> -> {
                    val row = rawOutput[0]
                    when (row) {
                        is FloatArray -> row.copyInto(scores)
                        is ByteArray -> row.forEachIndexed { i, v ->
                            val raw = if (outputType == DataType.UINT8) v.toInt() and 0xFF else v.toInt()
                            scores[i] = (raw - outputQuantZeroPoint) * outputQuantScale
                        }
                    }
                }
            }

            val normalized = normalizeScores(scores)
            val bestIndex = normalized.indices.maxByOrNull { normalized[it] } ?: -1
            if (bestIndex < 0 || bestIndex >= labels.size) {
                return ImageAnalysis.error("The AI model returned an invalid result. Please retake the photo.")
            }

            val bestLabel = normalizeLabel(labels[bestIndex])
            val confidence = (normalized[bestIndex] * 100f).roundToInt().coerceIn(1, 100)

            buildAnalysis(bestLabel, confidence)
        } catch (_: Exception) {
            ImageAnalysis.error("The image check couldn't finish. Please retake the photo.")
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun createInputBuffer(bitmap: Bitmap): ByteBuffer {
        val channels = interpreter.getInputTensor(0).shape()[3]
        val pixels = IntArray(inputWidth * inputHeight)
        bitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        return if (inputType == DataType.FLOAT32) {
            ByteBuffer.allocateDirect(inputWidth * inputHeight * channels * 4)
                .order(ByteOrder.nativeOrder())
                .also { buffer ->
                    for (pixel in pixels) {
                        val r = (pixel shr 16) and 0xff
                        val g = (pixel shr 8) and 0xff
                        val b = pixel and 0xff
                        buffer.putFloat((r - 127.5f) / 127.5f)
                        buffer.putFloat((g - 127.5f) / 127.5f)
                        buffer.putFloat((b - 127.5f) / 127.5f)
                        if (channels == 4) buffer.putFloat(1f)
                    }
                    buffer.rewind()
                }
        } else {
            ByteBuffer.allocateDirect(inputWidth * inputHeight * channels)
                .order(ByteOrder.nativeOrder())
                .also { buffer ->
                    for (pixel in pixels) {
                        val r = (pixel shr 16) and 0xff
                        val g = (pixel shr 8) and 0xff
                        val b = pixel and 0xff
                        if ((inputType == DataType.UINT8 || inputType == DataType.INT8) && inputQuantScale > 0f) {
                            buffer.put(quantize(r))
                            buffer.put(quantize(g))
                            buffer.put(quantize(b))
                        } else {
                            buffer.put(r.toByte())
                            buffer.put(g.toByte())
                            buffer.put(b.toByte())
                        }
                        if (channels == 4) buffer.put(255.toByte())
                    }
                    buffer.rewind()
                }
        }
    }

    private fun quantize(value: Int): Byte {
        val q = (value / inputQuantScale + inputQuantZeroPoint).roundToInt()
            .coerceIn(
                if (inputType == DataType.UINT8) 0 else -128,
                if (inputType == DataType.UINT8) 255 else 127
            )
        return q.toByte()
    }

    private fun normalizeScores(raw: FloatArray): FloatArray {
        // Most Teachable Machine image models already return probabilities.
        // If this model returns logits, softmax them; this keeps the integration
        // robust without changing the exported model.
        val clipped = raw.map { it.coerceIn(-50f, 50f) }.toFloatArray()
        val sum = clipped.sum()
        val looksLikeProbabilities =
            clipped.all { it in 0f..1f } && sum in 0.98f..1.02f
        if (looksLikeProbabilities) return clipped

        val maxValue = clipped.maxOrNull() ?: 0f
        val exps = clipped.map { kotlin.math.exp((it - maxValue).toDouble()).toFloat() }
        val total = exps.sum().coerceAtLeast(1e-6f)
        return FloatArray(exps.size) { exps[it] / total }
    }

    private fun buildAnalysis(label: String, confidence: Int): ImageAnalysis {
        return when (label) {
            "FLOOD" -> {
                if (confidence >= FLOOD_READY_THRESHOLD) {
                    ImageAnalysis(
                        label = label,
                        confidence = confidence,
                        title = "Your image is ready to go",
                        message = "Flood detected with $confidence% AI confidence. You can continue to the manual flood pin.",
                        canSubmit = true
                    )
                } else {
                    ImageAnalysis(
                        label = label,
                        confidence = confidence,
                        title = "Possible flooding — take another photo",
                        message = "DryNav sees possible flooding, but the confidence is not high enough yet. A clearer photo is recommended.",
                        canSubmit = false
                    )
                }
            }

            "NOT_FLOOD" -> ImageAnalysis(
                label = label,
                confidence = confidence,
                title = "Flooding wasn't detected",
                message = "This image looks more like a normal/non-flooded scene. Please take a photo that clearly shows the flooded area.",
                canSubmit = false
            )

            "BLURRY_UNUSABLE" -> ImageAnalysis(
                label = label,
                confidence = confidence,
                title = "Your image is too blurry",
                message = "DryNav can't reliably inspect this photo. Please hold the camera steady and take another picture.",
                canSubmit = false
            )

            "UNCERTAIN" -> ImageAnalysis(
                label = label,
                confidence = confidence,
                title = "The image is uncertain",
                message = "DryNav can't confidently determine the flood condition. Please take a clearer photo showing more of the road.",
                canSubmit = false
            )

            else -> ImageAnalysis.error("DryNav couldn't classify this image. Please take it again.")
        }
    }

    private fun normalizeLabel(value: String): String =
        value.trim().uppercase().replace(' ', '_')

    private fun decodeBitmap(uri: Uri): Bitmap? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return try {
            val exif = ExifInterface(ByteArrayInputStream(bytes))
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val rotation = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (rotation == 0f) raw
            else Bitmap.createBitmap(
                raw, 0, 0, raw.width, raw.height,
                Matrix().apply { postRotate(rotation) },
                true
            ).also { rotated ->
                if (rotated !== raw) raw.recycle()
            }
        } catch (_: Exception) {
            raw
        }
    }

    override fun close() {
        interpreter.close()
    }

    companion object {
        private const val MODEL_FILE = "model_unquant.tflite"
        private const val LABELS_FILE = "labels.txt"
        // Conservative first-pass threshold to reduce false flood positives.
        const val FLOOD_READY_THRESHOLD = 80
    }
}

data class ImageAnalysis(
    val label: String,
    val confidence: Int,
    val title: String,
    val message: String,
    val canSubmit: Boolean,
    val error: Boolean = false
) {
    companion object {
        fun error(message: String) = ImageAnalysis(
            label = "ERROR",
            confidence = 0,
            title = "Image check unavailable",
            message = message,
            canSubmit = false,
            error = true
        )
    }
}
