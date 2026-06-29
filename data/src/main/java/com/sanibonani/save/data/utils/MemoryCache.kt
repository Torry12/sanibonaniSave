package com.sanibonani.save.data.utils

import java.util.concurrent.ConcurrentHashMap

/**
 * Simple in-memory cache with LRU eviction for repository-level caching.
 * Not for large objects or persistence. Thread-safe.
 */
class MemoryCache<K, V>(private val maxSize: Int = 100) {
    private val map = object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }
    private val lock = Any()

    fun get(key: K): V? = synchronized(lock) { map[key] }
    fun put(key: K, value: V) = synchronized(lock) { map[key] = value }
    fun remove(key: K) = synchronized(lock) { map.remove(key) }
    fun clear() = synchronized(lock) { map.clear() }
    fun snapshot(): Map<K, V> = synchronized(lock) { map.toMap() }
}

