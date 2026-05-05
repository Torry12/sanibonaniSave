package com.sanibonani.save.domain.utils

import java.nio.charset.StandardCharsets
import java.util.UUID

object OperationKeys {
    fun stableUuid(namespace: String, vararg parts: Any?): String {
        val normalized = buildString {
            append(namespace.trim())
            parts.forEach { part ->
                append('|')
                append(part?.toString()?.trim().orEmpty())
            }
        }
        return UUID.nameUUIDFromBytes(normalized.toByteArray(StandardCharsets.UTF_8)).toString()
    }

    fun stableSuffix(namespace: String, vararg parts: Any?): String {
        return stableUuid(namespace, *parts).replace("-", "")
    }
}
