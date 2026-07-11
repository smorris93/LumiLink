package com.lumilink.model

/**
 * Current exposure-ish settings read from the camera's `getinfo&type=curmenu` reply.
 *
 * On the GX80, curmenu reliably reports **ISO** (and other picture settings), but NOT the aperture
 * or shutter speed in Manual mode — those are set on the camera's own dials and aren't exposed over
 * the remote API. Unknown fields stay null and render as "—".
 */
data class CameraExposure(
    val iso: String? = null,
    val aperture: String? = null,
    val shutter: String? = null,
    val exposureComp: String? = null,
) {
    companion object {
        val UNKNOWN = CameraExposure()
    }
}
