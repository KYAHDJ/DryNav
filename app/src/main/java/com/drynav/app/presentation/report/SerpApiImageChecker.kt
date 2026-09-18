package com.drynav.app.presentation.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import com.drynav.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Secondary reverse-image/provenance check using SerpApi + Google Lens.
 *
 * TFLite remains the source of truth for the flood classification. This class
 * only looks for evidence that the captured image (or a visually similar
 * version) already exists online.
 *
 * Important design choice:
 * - EXACT Google Lens results are trusted as exact-match candidates. We do not
 *   reject them just because a Lens thumbnail is resized/recompressed.
 * - VISUAL results are locally compared with the captured image before they
 *   are displayed, so random Lens suggestions are filtered out.
 * - Broken/dead source links are not shown.
 */
class SerpApiImageChecker(private val context: Context) : AutoCloseable {

    suspend fun checkImage(uri: Uri): InternetImageCheck = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.SERPAPI_API_KEY.trim()
        if (apiKey.isBlank()) {
            return@withContext InternetImageCheck.error("SerpApi key is not configured.")
        }

        try {
            val original = decodeForComparison(uri)
            try {
                val uploadBytes = prepareUpload(uri)
                val imageId = uploadImage(uploadBytes, apiKey)

                // Do NOT localize exact matching. Country filtering can cause a
                // known online copy to disappear simply because its source is
                // outside the Philippines.
                val exactJson = lensSearch(imageId, apiKey, "exact_matches", localized = false)

                // Visual search can remain localized because it is only a
                // secondary similarity search and is locally verified below.
                val visualJson = lensSearch(imageId, apiKey, "visual_matches", localized = true)

                val exactMatches = parseExactMatches(exactJson.optJSONArray("exact_matches"))
                    .filter { isLiveUrl(it.link) }
                    .distinctBy { it.link }
                    .take(MAX_RESULTS)

                val visualMatches = parseMatches(visualJson.optJSONArray("visual_matches"), exact = false)
                    .asSequence()
                    .filter { isLiveUrl(it.link) }
                    .mapNotNull { match ->
                        val candidate = downloadBitmap(match.thumbnail) ?: return@mapNotNull null
                        try {
                            val comparison = compareImages(original, candidate)
                            when {
                                comparison.pixelSimilarity >= POSSIBLE_COPY_PIXEL &&
                                    comparison.pHashDistance <= POSSIBLE_COPY_PHASH &&
                                    comparison.dHashDistance <= POSSIBLE_COPY_DHASH ->
                                    match.copy(
                                        similarityScore = comparison.similarityScore,
                                        matchType = InternetMatchType.POSSIBLE_COPY
                                    )
                                else -> null
                            }
                        } finally {
                            candidate.recycle()
                        }
                    }
                    .distinctBy { it.link }
                    .sortedByDescending { it.similarityScore }
                    .take(MAX_RESULTS)
                    .toList()

                InternetImageCheck(
                    exactMatches = exactMatches,
                    visualMatches = visualMatches,
                    error = null
                )
            } finally {
                original.recycle()
            }
        } catch (e: Exception) {
            InternetImageCheck.error(
                e.message?.takeIf { it.isNotBlank() } ?: "The web image check could not be completed."
            )
        }
    }

    private fun uploadImage(bytes: ByteArray, apiKey: String): String {
        val boundary = "----DryNavBoundary${System.currentTimeMillis()}"
        val connection = (URL("https://serpapi.com/image").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
        }

        DataOutputStream(connection.outputStream).use { out ->
            out.writeBytes("--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"drynav.jpg\"\r\n")
            out.writeBytes("Content-Type: image/jpeg\r\n\r\n")
            out.write(bytes)
            out.writeBytes("\r\n--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"api_key\"\r\n\r\n")
            out.writeBytes(apiKey)
            out.writeBytes("\r\n--$boundary--\r\n")
        }

        val body = readResponse(connection)
        val json = JSONObject(body)
        return json.optString("image_id").takeIf { it.isNotBlank() }
            ?: error(json.optString("error").ifBlank { "SerpApi image upload failed." })
    }

    private fun lensSearch(
        imageId: String,
        apiKey: String,
        type: String,
        localized: Boolean
    ): JSONObject {
        val params = mutableListOf(
            "engine" to "google_lens",
            "image_id" to imageId,
            "type" to type,
            "hl" to "en",
            "safe" to "active",
            "no_cache" to "true",
            "api_key" to apiKey
        )

        if (localized) {
            params += "country" to "ph"
        }

        val query = params.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }

        val connection = (URL("https://serpapi.com/search?$query").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("Accept", "application/json")
        }
        return JSONObject(readResponse(connection))
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.use { it.bufferedReader().readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) {
            val message = runCatching { JSONObject(body).optString("error") }.getOrNull()
            error(message?.ifBlank { null } ?: "SerpApi returned HTTP $code")
        }
        return body
    }

    private fun parseExactMatches(array: JSONArray?): List<InternetMatch> =
        parseMatches(array, exact = true).map {
            it.copy(
                exact = true,
                matchType = InternetMatchType.EXACT_COPY,
                similarityScore = 100
            )
        }

    private fun parseMatches(array: JSONArray?, exact: Boolean): List<InternetMatch> {
        if (array == null) return emptyList()
        val result = ArrayList<InternetMatch>(minOf(array.length(), MAX_RESULTS * 2))
        for (i in 0 until minOf(array.length(), MAX_RESULTS * 2)) {
            val item = array.optJSONObject(i) ?: continue
            val title = item.optString("title").ifBlank { "Untitled result" }
            val source = item.optString("source").ifBlank { "Unknown source" }
            val link = item.optString("link")
            if (link.isBlank()) continue
            result += InternetMatch(
                title = title,
                source = source,
                link = link,
                thumbnail = item.optString("thumbnail").takeIf { it.isNotBlank() },
                exact = exact || item.optBoolean("exact_matches", false)
            )
        }
        return result
    }

    /** Only display source URLs that are reachable. */
    private fun isLiveUrl(link: String): Boolean {
        return runCatching {
            val connection = (URL(link).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                instanceFollowRedirects = true
                connectTimeout = 5_000
                readTimeout = 7_000
                setRequestProperty("User-Agent", USER_AGENT)
            }
            val code = connection.responseCode
            connection.disconnect()
            if (code in 200..399) {
                true
            } else if (code == HttpURLConnection.HTTP_BAD_METHOD || code in 400..599) {
                // Some sites reject HEAD. Retry with a tiny GET instead of
                // incorrectly treating a working source as dead.
                val get = (URL(link).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    connectTimeout = 5_000
                    readTimeout = 7_000
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Range", "bytes=0-0")
                }
                val getCode = get.responseCode
                get.inputStream?.close()
                get.disconnect()
                getCode in 200..399
            } else false
        }.getOrDefault(false)
    }

    private fun downloadBitmap(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 7_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (connection.responseCode !in 200..299) {
                connection.disconnect()
                return null
            }
            val bytes = connection.inputStream.use { it.readBytes() }
            connection.disconnect()
            if (bytes.size > MAX_CANDIDATE_BYTES) return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    private fun decodeForComparison(uri: Uri): Bitmap {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Couldn't read the captured photo.")
        return BitmapFactory.decodeByteArray(raw, 0, raw.size)
            ?: error("Couldn't decode the captured photo.")
    }

    /** SerpApi currently accepts a maximum upload size of 500 KB. */
    private fun prepareUpload(uri: Uri): ByteArray {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Couldn't read the captured photo.")
        val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size)
            ?: error("Couldn't decode the captured photo.")

        return try {
            val maxDimension = 900
            val scale = min(1f, maxDimension.toFloat() / max(bitmap.width, bitmap.height).toFloat())
            val resized = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else bitmap

            var quality = 75
            var bytes: ByteArray
            do {
                bytes = ByteArrayOutputStream().use { out ->
                    resized.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    out.toByteArray()
                }
                quality -= 5
            } while (bytes.size > MAX_UPLOAD_BYTES && quality >= 35)

            if (resized !== bitmap) resized.recycle()
            if (bytes.size > MAX_UPLOAD_BYTES) {
                error("Captured photo is too large for the web image check.")
            }
            bytes
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun compareImages(original: Bitmap, candidate: Bitmap): ComparisonResult {
        val normal = normalize(candidate)
        val mirrored = mirror(candidate)
        return try {
            val normalResult = compareNormalized(original, normal)
            val mirroredResult = compareNormalized(original, mirrored)
            if (mirroredResult.similarityScore > normalResult.similarityScore) mirroredResult else normalResult
        } finally {
            normal.recycle()
            mirrored.recycle()
        }
    }

    private fun compareNormalized(original: Bitmap, candidate: Bitmap): ComparisonResult {
        val p1 = perceptualHash(original)
        val p2 = perceptualHash(candidate)
        val d1 = differenceHash(original)
        val d2 = differenceHash(candidate)
        val pDistance = java.lang.Long.bitCount(p1 xor p2)
        val dDistance = java.lang.Long.bitCount(d1 xor d2)
        val pixel = pixelSimilarity(original, candidate)

        val pScore = ((1f - pDistance / 64f) * 100f).coerceIn(0f, 100f)
        val dScore = ((1f - dDistance / 64f) * 100f).coerceIn(0f, 100f)
        val score = (pScore * 0.45f + dScore * 0.25f + pixel * 0.30f).toInt()

        return ComparisonResult(pDistance, dDistance, pixel, score)
    }

    private fun normalize(source: Bitmap): Bitmap {
        val size = 256
        return Bitmap.createScaledBitmap(source, size, size, true)
    }

    private fun mirror(source: Bitmap): Bitmap {
        val matrix = Matrix().apply { preScale(-1f, 1f) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun pixelSimilarity(a: Bitmap, b: Bitmap): Float {
        val size = 32
        val aa = Bitmap.createScaledBitmap(a, size, size, true)
        val bb = Bitmap.createScaledBitmap(b, size, size, true)
        return try {
            var difference = 0L
            val total = size * size * 3L * 255L
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val ca = aa.getPixel(x, y)
                    val cb = bb.getPixel(x, y)
                    difference += abs(((ca shr 16) and 0xff) - ((cb shr 16) and 0xff)).toLong()
                    difference += abs(((ca shr 8) and 0xff) - ((cb shr 8) and 0xff)).toLong()
                    difference += abs((ca and 0xff) - (cb and 0xff)).toLong()
                }
            }
            (100f * (1f - difference.toFloat() / total)).coerceIn(0f, 100f)
        } finally {
            aa.recycle()
            bb.recycle()
        }
    }

    private fun perceptualHash(bitmap: Bitmap): Long {
        val size = 32
        val small = Bitmap.createScaledBitmap(bitmap, size, size, true)
        return try {
            val values = DoubleArray(size * size)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val c = small.getPixel(x, y)
                    val r = (c shr 16) and 0xff
                    val g = (c shr 8) and 0xff
                    val b = c and 0xff
                    values[y * size + x] = 0.299 * r + 0.587 * g + 0.114 * b
                }
            }
            val dct = DoubleArray(64)
            for (v in 0 until 8) {
                for (u in 0 until 8) {
                    var sum = 0.0
                    for (y in 0 until size) {
                        for (x in 0 until size) {
                            sum += values[y * size + x] *
                                kotlin.math.cos((2 * x + 1) * u * Math.PI / (2 * size)) *
                                kotlin.math.cos((2 * y + 1) * v * Math.PI / (2 * size))
                        }
                    }
                    dct[v * 8 + u] = sum
                }
            }
            val sorted = dct.copyOfRange(1, 64).sorted()
            val median = sorted[sorted.size / 2]
            var hash = 0L
            for (i in 0 until 64) {
                if (dct[i] > median) hash = hash or (1L shl i)
            }
            hash
        } finally {
            small.recycle()
        }
    }

    private fun differenceHash(bitmap: Bitmap): Long {
        val width = 9
        val height = 8
        val small = Bitmap.createScaledBitmap(bitmap, width, height, true)
        return try {
            var hash = 0L
            var bit = 0
            for (y in 0 until height) {
                for (x in 0 until width - 1) {
                    val left = gray(small.getPixel(x, y))
                    val right = gray(small.getPixel(x + 1, y))
                    if (left > right) hash = hash or (1L shl bit)
                    bit++
                }
            }
            hash
        } finally {
            small.recycle()
        }
    }

    private fun gray(color: Int): Int {
        val r = (color shr 16) and 0xff
        val g = (color shr 8) and 0xff
        val b = color and 0xff
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }

    override fun close() = Unit

    companion object {
        private const val MAX_UPLOAD_BYTES = 480 * 1024
        private const val MAX_CANDIDATE_BYTES = 2 * 1024 * 1024
        private const val MAX_RESULTS = 5
        private const val POSSIBLE_COPY_PHASH = 18
        private const val POSSIBLE_COPY_DHASH = 22
        private const val POSSIBLE_COPY_PIXEL = 68f
        private const val USER_AGENT = "Mozilla/5.0 (Android; DryNav) AppleWebKit/537.36 Chrome/153.0 Mobile Safari/537.36"
    }
}

data class ComparisonResult(
    val pHashDistance: Int,
    val dHashDistance: Int,
    val pixelSimilarity: Float,
    val similarityScore: Int
)

enum class InternetMatchType {
    EXACT_COPY,
    POSSIBLE_COPY
}

data class InternetMatch(
    val title: String,
    val source: String,
    val link: String,
    val thumbnail: String? = null,
    val exact: Boolean = false,
    val similarityScore: Int = 0,
    val matchType: InternetMatchType? = null
)

data class InternetImageCheck(
    val exactMatches: List<InternetMatch> = emptyList(),
    val visualMatches: List<InternetMatch> = emptyList(),
    val error: String? = null
) {
    val foundExact: Boolean get() = exactMatches.isNotEmpty()
    val foundVisual: Boolean get() = visualMatches.isNotEmpty()
    val hasMatches: Boolean get() = foundExact || foundVisual

    companion object {
        fun error(message: String) = InternetImageCheck(error = message)
    }
}
