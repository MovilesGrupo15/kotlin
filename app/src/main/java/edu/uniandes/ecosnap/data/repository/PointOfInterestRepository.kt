package edu.uniandes.ecosnap.data.repository

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
    private const val baseUrl = "http://192.168.1.107:8000"
    private val client = HttpClientProvider.createClient()
    private val poiObservable = Observable<PointOfInterest>()

    override fun addObserver(observer: Observer<PointOfInterest>) {
        poiObservable.addObserver(observer)
    }

    override fun removeObserver(observer: Observer<PointOfInterest>) {
        poiObservable.removeObserver(observer)
    }

    override fun fetch() {
        if (!poiObservable.hasObservers()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val points = client.get<List<PointOfInterest>>("$baseUrl/api/points")

                points.forEach { point ->
                    poiObservable.notifySuccess(point)
                }
            } catch (e: Exception) {
                poiObservable.notifyError(e)
            }
        }
    }
}