package edu.uniandes.ecosnap.data.repository

import android.util.Log
import edu.uniandes.ecosnap.BuildConfig
import edu.uniandes.ecosnap.data.cache.InMemoryCache       // new import
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
    private val baseUrl = "https://${BuildConfig.SERVER_URL}"
    private val client = HttpClientProvider.createClient()
    private val offerObservable = Observable<Offer>()

    // Cache
    private val cache = InMemoryCache<String, List<Offer>>(maxSize = 20)
    private const val CACHE_KEY = "offers"

    override fun addObserver(observer: Observer<Offer>) {
        offerObservable.addObserver(observer)
    }

    override fun removeObserver(observer: Observer<Offer>) {
        offerObservable.removeObserver(observer)
    }

    override fun fetch() {
        // Return instantly if cached
        cache.get(CACHE_KEY)?.let { cached ->
            cached.forEach { offerObservable.notifySuccess(it) }
            return
        }

        // Otherwise fetch from network
        if (!offerObservable.hasObservers()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val offers = client.get<List<Offer>>("$baseUrl/api/offers")

                // store response in cache
                cache.put(CACHE_KEY, offers)

                // emit each Offer
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