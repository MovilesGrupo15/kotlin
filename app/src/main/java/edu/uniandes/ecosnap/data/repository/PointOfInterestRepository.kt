package edu.uniandes.ecosnap.data.repository

import android.util.Log
import edu.uniandes.ecosnap.BuildConfig
import edu.uniandes.ecosnap.data.cache.GlobalCache
import edu.uniandes.ecosnap.data.observer.HttpClientProvider
import edu.uniandes.ecosnap.data.observer.Observable
import edu.uniandes.ecosnap.data.observer.ObservableRepository
import edu.uniandes.ecosnap.data.observer.Observer
import edu.uniandes.ecosnap.domain.model.PointOfInterest
import io.ktor.client.request.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object PointOfInterestRepository: ObservableRepository<PointOfInterest> {
    private const val baseUrl = "https://${BuildConfig.SERVER_URL}"
    private val client = HttpClientProvider.createClient()
    private val poiObservable = Observable<PointOfInterest>()

    // Cache key
    private const val CACHE_KEY = "points"

    override fun addObserver(observer: Observer<PointOfInterest>) {
        poiObservable.addObserver(observer)
    }

    override fun removeObserver(observer: Observer<PointOfInterest>) {
        poiObservable.removeObserver(observer)
    }

    override fun fetch() {
        if (!poiObservable.hasObservers()) return

        // Return instantly if cached
        GlobalCache.cache.get(CACHE_KEY)?.let { cached ->
            cached.forEach { poiObservable.notifySuccess(it as PointOfInterest) }
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val points = client.get<List<PointOfInterest>>("$baseUrl/api/points")

                points.forEach { point ->
                    poiObservable.notifySuccess(point)
                }
            } catch (e: Exception) {
                Log.d("PointOfInterestRepository", "Error fetching points of interest", e)
                poiObservable.notifyError(e)
            }
        }
    }
}