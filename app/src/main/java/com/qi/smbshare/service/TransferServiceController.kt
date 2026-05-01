package com.qi.smbshare.service

import java.util.concurrent.atomic.AtomicReference

interface TransferServiceControl {
    fun pauseTransfer(taskId: String)
    fun resumeTransfer(taskId: String)
    fun cancelTransfer(taskId: String)
}

object TransferServiceController {
    private val currentService = AtomicReference<TransferServiceControl?>()

    fun register(service: TransferServiceControl) {
        currentService.set(service)
    }

    fun unregister(service: TransferServiceControl) {
        currentService.compareAndSet(service, null)
    }

    fun pause(taskId: String): Boolean {
        return currentService.get()?.let {
            it.pauseTransfer(taskId)
            true
        } ?: false
    }

    fun resume(taskId: String): Boolean {
        return currentService.get()?.let {
            it.resumeTransfer(taskId)
            true
        } ?: false
    }

    fun cancel(taskId: String): Boolean {
        return currentService.get()?.let {
            it.cancelTransfer(taskId)
            true
        } ?: false
    }
}
