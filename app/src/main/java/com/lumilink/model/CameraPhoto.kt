package com.lumilink.model

/**
 * One photo stored on the camera, as surfaced by the DLNA/UPnP browse.
 *
 * @param id UPnP object id (used as a stable key).
 * @param title display name / filename (e.g. "P1030413").
 * @param isRaw true when the downloadable original is a RAW (RW2) file.
 * @param thumbnailUrl small preview URL for the grid, or null if none was advertised.
 * @param originalUrl full-resolution file to download.
 * @param sizeBytes size of the original in bytes, or null if the camera didn't report it.
 */
data class CameraPhoto(
    val id: String,
    val title: String,
    val isRaw: Boolean,
    val thumbnailUrl: String?,
    val originalUrl: String,
    val sizeBytes: Long?,
)
