package com.lumilink.data

import com.lumilink.model.CameraPhoto
import com.lumilink.network.CameraException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Downloads a photo's original file from the camera and saves it via [MediaStoreSaver], reporting
 * progress. RAW files over the camera's 2.4 GHz Wi-Fi are slow, hence the generous read timeout.
 */
class PhotoDownloader(
    private val mediaStoreSaver: MediaStoreSaver,
    private val httpClient: OkHttpClient = defaultHttpClient(),
) {

    /**
     * @param onProgress fraction 0f..1f for this single file (best-effort; only when size known).
     */
    suspend fun download(photo: CameraPhoto, onProgress: (Float) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(photo.originalUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw CameraException("Download failed: HTTP ${response.code}")
                }
                val body = response.body ?: throw CameraException("Empty download response")
                val totalBytes = photo.sizeBytes ?: body.contentLength().takeIf { it > 0 }

                val filename = fileNameFor(photo)
                val mimeType = if (photo.isRaw) "image/x-panasonic-rw2" else "image/jpeg"

                mediaStoreSaver.save(filename, mimeType, asDownload = photo.isRaw) { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (totalBytes != null && totalBytes > 0) {
                                onProgress((copied.toFloat() / totalBytes).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                onProgress(1f)
            }
        }

    private fun fileNameFor(photo: CameraPhoto): String {
        val extension = if (photo.isRaw) "rw2" else "jpg"
        return "${photo.title}.$extension"
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS) // RAW files can be large over slow Wi-Fi
            .build()
    }
}
