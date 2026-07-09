package com.lumilink.di

import android.content.Context
import com.lumilink.data.CameraCredentialsStore
import com.lumilink.data.MediaStoreSaver
import com.lumilink.data.PhotoDownloader
import com.lumilink.data.PhotoRepository
import com.lumilink.network.CameraClient
import com.lumilink.network.CameraNetworkManager

/**
 * Manual dependency-injection container — plain, greppable wiring instead of an annotation
 * framework (Hilt/Dagger). Holds the app-wide singletons; created once in [com.lumilink.LumiLinkApp].
 *
 * Why manual DI: for a solo project this keeps the whole object graph readable in one file with
 * zero code generation, which was a deliberate goal (see plan §"Code style & readability").
 */
class AppContainer(context: Context) {
    val credentialsStore: CameraCredentialsStore = CameraCredentialsStore(context)
    val cameraNetworkManager: CameraNetworkManager = CameraNetworkManager(context)
    val cameraClient: CameraClient = CameraClient()
    val photoRepository: PhotoRepository = PhotoRepository(cameraClient)
    val photoDownloader: PhotoDownloader = PhotoDownloader(MediaStoreSaver(context))
}
