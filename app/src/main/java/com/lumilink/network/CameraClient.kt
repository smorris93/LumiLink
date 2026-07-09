package com.lumilink.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Talks to the camera's reverse-engineered HTTP control API (`cam.cgi`). Every command is a plain
 * GET; the camera replies with a small XML document whose `<result>` we check.
 *
 * All app sockets are already bound to the camera network by [CameraNetworkManager], so these
 * requests automatically go over the camera Wi-Fi.
 *
 * Kotlin note: `suspend fun` marks a function that can pause without blocking a thread; we run the
 * blocking OkHttp call on `Dispatchers.IO` via `withContext`.
 */
class CameraClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val httpClient: OkHttpClient = defaultHttpClient(),
) {

    /**
     * Pairs this app with the camera (access-control grant). Must succeed before other commands
     * are accepted. Uses a stable device UUID so the camera remembers us.
     */
    suspend fun pair(deviceName: String = "LumiLink") {
        val encodedName = URLEncoder.encode(deviceName, "UTF-8")
        get("$baseUrl/cam.cgi?mode=accctrl&type=req_acc&value=$DEVICE_UUID&value2=$encodedName")
    }

    /** Switch the camera to playback mode (required before browsing stored photos). */
    suspend fun enterPlaybackMode() = sendCamCmd("playmode")

    /** Switch the camera to record mode (required before capture/live view — used from MVP2). */
    suspend fun enterRecordMode() = sendCamCmd("recmode")

    private suspend fun sendCamCmd(value: String) {
        get("$baseUrl/cam.cgi?mode=camcmd&value=$value")
    }

    /**
     * Performs the GET, verifies the HTTP status and the camera's `<result>ok</result>`, and
     * returns the raw body. Throws [CameraException] on any non-ok outcome.
     */
    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw CameraException("Camera returned HTTP ${response.code}")
            }
            val result = CamReplyParser.resultOf(body)
            if (!result.equals("ok", ignoreCase = true)) {
                Log.w(TAG, "Command failed: $url -> $body")
                throw CameraException("Camera rejected the command (result='$result')")
            }
            body
        }
    }

    companion object {
        private const val TAG = "CameraClient"
        const val DEFAULT_BASE_URL = "http://192.168.54.1"

        // Stable per-app client id the camera uses to remember us. (Could become per-install later.)
        private const val DEVICE_UUID = "4C554D49-4C69-6E6B-8001-000000000001"

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
