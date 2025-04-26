package edu.uniandes.ecosnap.data.cache

/**
 * Generic cache interface.
 */
interface Cache<K, V> {
    fun get(key: K): V?
    fun put(key: K, value: V)
    fun remove(key: K)
    fun clear()
}