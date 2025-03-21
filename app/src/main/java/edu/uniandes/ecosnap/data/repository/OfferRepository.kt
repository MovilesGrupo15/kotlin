package edu.uniandes.ecosnap.data.repository

import edu.uniandes.ecosnap.data.observer.HttpClientProvider
import edu.uniandes.ecosnap.data.observer.Observable
import edu.uniandes.ecosnap.data.observer.ObservableRepository
import edu.uniandes.ecosnap.data.observer.Observer
import edu.uniandes.ecosnap.domain.model.Offer
import io.ktor.client.request.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


object OfferRepository: ObservableRepository<Offer> {
    private const val baseUrl = "http://192.168.1.107:8000"
    private val client = HttpClientProvider.createClient()
    private val offerObservable = Observable<Offer>()

    override fun addObserver(observer: Observer<Offer>) {
        offerObservable.addObserver(observer)
    }

    override fun removeObserver(observer: Observer<Offer>) {
        offerObservable.removeObserver(observer)
    }

    override fun fetch() {
        if (!offerObservable.hasObservers()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val offers = client.get<List<Offer>>("$baseUrl/api/offers")

                offers.forEach { offer ->
                    offerObservable.notifySuccess(offer)
                }
            } catch (e: Exception) {
                offerObservable.notifyError(e)
            }
        }
    }
}