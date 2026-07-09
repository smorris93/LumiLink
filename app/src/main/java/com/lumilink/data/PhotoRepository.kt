package com.lumilink.data

import android.util.Log
import com.lumilink.model.CameraPhoto
import com.lumilink.network.CameraClient
import com.lumilink.network.CameraException
import com.lumilink.network.DeviceDescriptionParser
import com.lumilink.network.DidlParser
import com.lumilink.network.SsdpDiscovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Lists the photos stored on the camera by driving the full sequence:
 *   pair → playback mode → SSDP discover → fetch device description → recursive DLNA Browse.
 *
 * The camera organizes photos inside nested containers (e.g. by folder/date), so we breadth-first
 * walk from the root object, collecting photo items and queuing sub-containers, with hard caps so
 * a pathological tree can't loop forever.
 */
class PhotoRepository(
    private val cameraClient: CameraClient,
    private val ssdp: SsdpDiscovery = SsdpDiscovery(),
    private val httpClient: OkHttpClient = defaultHttpClient(),
) {

    suspend fun listPhotos(): List<CameraPhoto> = withContext(Dispatchers.IO) {
        // Handshake + switch to playback so the media server exposes stored photos.
        cameraClient.pair()
        cameraClient.enterPlaybackMode()

        val descriptionUrl = ssdp.discoverMediaServerDescriptionUrl()
            ?: throw CameraException("Couldn't find the camera's photo service on the network")
        val descriptionXml = httpGet(descriptionUrl)
        val controlUrl =
            DeviceDescriptionParser.contentDirectoryControlUrl(descriptionXml, descriptionUrl)
                ?: throw CameraException("Camera didn't expose a photo directory service")

        val photos = mutableListOf<CameraPhoto>()
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>().apply { add(ROOT_OBJECT_ID) }
        var browses = 0

        while (queue.isNotEmpty() && photos.size < MAX_PHOTOS && browses < MAX_BROWSES) {
            val objectId = queue.removeFirst()
            if (!visited.add(objectId)) continue // guard against cycles

            // Page through this container: the camera returns ~50 children per Browse, so keep
            // advancing StartingIndex until we've seen TotalMatches (or a page comes back empty).
            var startIndex = 0
            while (browses < MAX_BROWSES && photos.size < MAX_PHOTOS) {
                val listing = try {
                    DidlParser.parse(browse(controlUrl, objectId, startIndex, PAGE_SIZE))
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping container $objectId at index $startIndex: ${e.message}")
                    break
                }
                browses++
                photos += listing.photos
                listing.containerIds.forEach { if (it !in visited) queue.add(it) }

                if (listing.numberReturned <= 0) break
                startIndex += listing.numberReturned
                if (listing.totalMatches in 1..startIndex) break // fetched everything
            }
        }
        Log.i(TAG, "Listed ${photos.size} photos in $browses browse calls")
        photos.take(MAX_PHOTOS)
    }

    /** POST a UPnP ContentDirectory `Browse` for a page of the direct children of [objectId]. */
    private fun browse(controlUrl: String, objectId: String, startIndex: Int, count: Int): String {
        val body = browseSoapBody(objectId, startIndex, count).toRequestBody(SOAP_MEDIA_TYPE)
        val request = Request.Builder()
            .url(controlUrl)
            .addHeader("SOAPAction", SOAP_ACTION)
            .post(body)
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw CameraException("Browse failed: HTTP ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun httpGet(url: String): String {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw CameraException("GET failed: HTTP ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun browseSoapBody(objectId: String, startIndex: Int, count: Int): String =
        """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
 <s:Body>
  <u:Browse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
   <ObjectID>$objectId</ObjectID>
   <BrowseFlag>BrowseDirectChildren</BrowseFlag>
   <Filter>*</Filter>
   <StartingIndex>$startIndex</StartingIndex>
   <RequestedCount>$count</RequestedCount>
   <SortCriteria></SortCriteria>
  </u:Browse>
 </s:Body>
</s:Envelope>"""

    private companion object {
        const val TAG = "PhotoRepository"
        const val ROOT_OBJECT_ID = "0"
        const val MAX_PHOTOS = 2000
        const val MAX_BROWSES = 300
        const val PAGE_SIZE = 100 // camera may cap lower; we page by the actual NumberReturned
        const val SOAP_ACTION = "\"urn:schemas-upnp-org:service:ContentDirectory:1#Browse\""
        val SOAP_MEDIA_TYPE = "text/xml; charset=\"utf-8\"".toMediaType()

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
