package com.sanibonani.save.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

internal class GroupContextWarmupCoordinator {

    private val jobs = ConcurrentHashMap<String, Job>()

    fun launch(
        groupId: String,
        scope: CoroutineScope,
        replaceActive: Boolean = false,
        block: suspend () -> Unit
    ) {
        var scheduled: Job? = null
        jobs.compute(groupId) { _, current ->
            if (!replaceActive && current?.isActive == true) {
                current
            } else {
                current?.cancel()
                scope.launch(start = CoroutineStart.LAZY) {
                    try {
                        block()
                    } finally {
                        jobs.remove(groupId, coroutineContext[Job])
                    }
                }.also { scheduled = it }
            }
        }
        scheduled?.start()
    }

    suspend fun <T> runExclusive(groupId: String, block: suspend () -> T): T = coroutineScope {
        val refreshJob = checkNotNull(coroutineContext[Job])
        jobs.compute(groupId) { _, current ->
            current?.cancel()
            refreshJob
        }
        try {
            block()
        } finally {
            jobs.remove(groupId, refreshJob)
        }
    }

    fun cancel(groupId: String) {
        jobs.remove(groupId)?.cancel()
    }

    fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }

    fun retainOnly(groupIds: Set<String>) {
        jobs.keys
            .filterNot(groupIds::contains)
            .forEach(::cancel)
    }
}
