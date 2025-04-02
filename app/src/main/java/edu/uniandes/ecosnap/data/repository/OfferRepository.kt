package edu.uniandes.ecosnap.data.repository

import android.util.Log
import edu.uniandes.ecosnap.data.observer.HttpClientProvider
import edu.uniandes.ecosnap.data.observer.Observable
import edu.uniandes.ecosnap.data.observer.ObservableRepository
import edu.uniandes.ecosnap.data.observer.Observer
import edu.uniandes.ecosnap.domain.model.Offer
import io.ktor.client.request.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import edu.uniandes.ecosnap.BuildConfig



object OfferRepository: ObservableRepository<Offer> {
    private val baseUrl = "http://${BuildConfig.SERVER_URL}"
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
                Log.d("OfferRepository", "Error fetching offers: ${e.message}")
                offerObservable.notifyError(e)
            }
        }
    }
}