package edu.uniandes.ecosnap.data.cache

object GlobalCache {
    val cache = InMemoryCache<String, List<Any>>(
        maxMemoryBytes = 2 * 1024 * 1024, // 2MB
        maxEntries = 20,                   // 20 entradas máximo
        ttlMillis = 5 * 60 * 1000         // 5 minutos TTL
    )
}