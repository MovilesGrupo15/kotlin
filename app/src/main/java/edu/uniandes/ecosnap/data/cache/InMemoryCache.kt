package edu.uniandes.ecosnap.data.cache

import java.util.LinkedHashMap

/**
 * Thread-safe in-memory LRU cache.
 *
 * @param maxSize maximum number of entries before evicting least-recently-used.
 */
class InMemoryCache<K, V>(private val maxSize: Int = 100) : Cache<K, V> {

    private val map = object : LinkedHashMap<K, V>(maxSize, 0.75f, /* accessOrder = */ true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }

    @Synchronized
    override fun get(key: K): V? = map[key]

    @Synchronized
    override fun put(key: K, value: V) {
        map[key] = value
    }

    @Synchronized
    override fun remove(key: K) {
        map.remove(key)
    }

    @Synchronized
    override fun clear() {
        map.clear()
    }
}