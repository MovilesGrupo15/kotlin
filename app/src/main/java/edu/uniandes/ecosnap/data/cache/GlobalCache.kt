package edu.uniandes.ecosnap.data.cache

object GlobalCache {
    val cache = InMemoryCache<String, List<Any>>(maxSize = 20)
}