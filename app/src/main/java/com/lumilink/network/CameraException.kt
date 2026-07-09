package com.lumilink.network

/** Thrown when the camera rejects a command or replies with a non-"ok" result. */
class CameraException(message: String) : Exception(message)
