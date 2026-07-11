package com.lumilink.network

import android.util.Log
import com.lumilink.model.CameraExposure
import com.lumilink.model.CameraStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Talks to the camera's reverse-engineered HTTP control API (`cam.cgi`). Every command is a plain
 * GET; the camera replies either with XML (`<result>ok</result>`) or CSV (`ok,...` / `err_...`).
 *
 * All app sockets are already bound to the camera network by [CameraNetworkManager], so these
 * requests automatically go over the camera Wi-Fi.
 */
class CameraClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val httpClient: OkHttpClient = defaultHttpClient(),
) {

    /**
     * Pairs this app with the camera (access-control grant). New devices often need the user to
     * **approve the connection on the camera's own screen**, during which the camera replies
     * `err_user_refused`. So we poll for a while to give the user time to accept.
     */
    suspend fun pair(deviceName: String = "LumiLink") {
        val encodedName = URLEncoder.encode(deviceName, "UTF-8")
        val url = "$baseUrl/cam.cgi?mode=accctrl&type=req_acc&value=$DEVICE_UUID&value2=$encodedName"

        var lastStatus = "unreachable"
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < PAIR_TIMEOUT_MS) {
            val status = try {
                CamReplyParser.resultOf(httpGetRaw(url))
            } catch (e: Exception) {
                // Camera not reachable yet (server still coming up after Wi-Fi associates, or the
                // radio is settling). Keep trying within the timeout rather than failing instantly.
                Log.i(TAG, "Camera not reachable yet: ${e.message}")
                lastStatus = "unreachable"
                delay(PAIR_RETRY_DELAY_MS)
                continue
            }
            when {
                status.equals("ok", ignoreCase = true) -> return
                // Already paired from a previous session — that's fine.
                status.equals("err_already_connected", ignoreCase = true) -> return
                else -> {
                    lastStatus = status
                    Log.i(TAG, "Waiting for camera to accept device (status=$status)…")
                    delay(PAIR_RETRY_DELAY_MS)
                }
            }
        }
        throw CameraException(pairErrorMessage(lastStatus))
    }

    /** Switch the camera to playback mode (required before browsing stored photos). */
    suspend fun enterPlaybackMode() = sendCamCmd("playmode")

    /** Switch the camera to record mode (required before capture/live view — used from MVP2). */
    suspend fun enterRecordMode() = sendCamCmd("recmode")

    /** Trigger the shutter. The camera must already be in record mode. */
    suspend fun capture() = sendCamCmd("capture")

    /** Run a single autofocus cycle. Record mode only. NOTE: the GX80 rejects this (err_reject) —
     *  it wants [touchAf] instead. Kept for reference / other bodies. */
    suspend fun autofocus() = sendCamCmd("oneshot_af")

    /**
     * Touch-to-focus: set the AF point at ([x], [y]) in the camera's 0..1000 live-view coordinate
     * space and focus there. This is how Lumix bodies do remote AF (the live-view stream must be
     * running). Focusing at the centre (500, 500) is the remote equivalent of a half-press.
     */
    suspend fun touchAf(x: Int, y: Int) {
        val body = rawQuery("mode=camctrl&type=touch&value=$x/$y&value2=on")
        val result = CamReplyParser.resultOf(body)
        Log.i(TAG, "touchAf($x,$y) -> $body")
        if (!result.equals("ok", ignoreCase = true)) {
            throw CameraException("Camera rejected touch AF (result='$result')")
        }
    }

    /** Start / stop video recording. Record mode only. */
    suspend fun startVideoRecord() = sendCamCmd("video_recstart")
    suspend fun stopVideoRecord() = sendCamCmd("video_recstop")

    /**
     * Poll the camera's state (battery, rec/play mode, SD status, video-recording flag). Note the
     * GX80's `getstate` does NOT include exposure (ISO/aperture/shutter/EV) — those ride the
     * live-view stream header (MVP3). Returns a best-effort parse; unknown fields stay null.
     */
    suspend fun fetchStatus(): CameraStatus =
        CameraStatusParser.parse(httpGetRaw("$baseUrl/cam.cgi?mode=getstate"))

    /**
     * Fetch the current exposure settings from the camera's menu state. The GX80 reports ISO here
     * (aperture/shutter aren't exposed in Manual mode). The reply is ~20 KB of menu XML.
     */
    suspend fun fetchExposure(): CameraExposure =
        CurMenuParser.parse(httpGetRaw("$baseUrl/cam.cgi?mode=getinfo&type=curmenu"))

    /**
     * Ask the camera to start pushing the live-view MJPEG stream over UDP to [port] on this device.
     * Record mode must already be active. Open the UDP socket *before* calling this so no early
     * frames are missed.
     */
    suspend fun startStream(port: Int) {
        val body = rawQuery("mode=startstream&value=$port")
        val result = CamReplyParser.resultOf(body)
        Log.i(TAG, "startstream($port) -> $body")
        if (!result.equals("ok", ignoreCase = true)) {
            throw CameraException("Camera rejected startstream (result='$result')")
        }
    }

    /** Best-effort stop of the live-view stream (not all firmwares expose it; ignore failures). */
    suspend fun stopStream() {
        runCatching { rawQuery("mode=stopstream") }
            .onFailure { Log.i(TAG, "stopstream failed (ignored): ${it.message}") }
    }

    /**
     * Escape hatch for on-device protocol probing: issue an arbitrary `cam.cgi` query and return
     * the raw body (e.g. `mode=getinfo&type=curmenu`). Not used in normal flows.
     */
    suspend fun rawQuery(query: String): String = httpGetRaw("$baseUrl/cam.cgi?$query")

    private suspend fun sendCamCmd(value: String) {
        val body = httpGetRaw("$baseUrl/cam.cgi?mode=camcmd&value=$value")
        val result = CamReplyParser.resultOf(body)
        if (!result.equals("ok", ignoreCase = true)) {
            Log.w(TAG, "Command '$value' failed -> $body")
            throw CameraException("Camera rejected '$value' (result='$result')")
        }
    }

    /** Performs the GET and returns the raw body, throwing only on transport/HTTP failure. */
    private suspend fun httpGetRaw(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw CameraException("Camera returned HTTP ${response.code}")
            }
            body
        }
    }

    private fun pairErrorMessage(status: String): String = when {
        status == "unreachable" ->
            "Couldn't reach the camera. Make sure it's switched on and set to Remote Shooting & " +
                "View (it may have gone to sleep), then tap Try again."
        status.contains("refused", ignoreCase = true) ->
            "The camera didn't accept the connection. On the camera screen, allow/register this " +
                "device if prompted, then tap Try again."
        else -> "Couldn't pair with the camera (status='$status')."
    }

    private companion object {
        const val TAG = "CameraClient"
        const val DEFAULT_BASE_URL = "http://192.168.54.1"

        // Stable per-app client id the camera uses to remember us. (Could become per-install later.)
        const val DEVICE_UUID = "4C554D49-4C69-6E6B-8001-000000000001"

        const val PAIR_TIMEOUT_MS = 12_000L
        const val PAIR_RETRY_DELAY_MS = 1_500L

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
