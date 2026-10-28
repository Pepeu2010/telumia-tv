package com.nuvio.tv.core.remote

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** In-process bridge from the LAN server to the currently composed TV navigation host. */
internal object PhoneRemoteCommandBus {
    private val mutableCommands = MutableSharedFlow<PhoneRemoteCommand>(extraBufferCapacity = 32)
    val commands = mutableCommands.asSharedFlow()

    fun dispatch(command: PhoneRemoteCommand) {
        mutableCommands.tryEmit(command)
    }
}
