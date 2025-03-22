package edu.uniandes.ecosnap.data.repository

import edu.uniandes.ecosnap.data.observer.HttpClientProvider
import edu.uniandes.ecosnap.data.observer.Observable
import edu.uniandes.ecosnap.data.observer.ObservableRepository
import edu.uniandes.ecosnap.data.observer.Observer
import edu.uniandes.ecosnap.domain.model.UserProfile
import io.ktor.client.request.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object UserRepository: ObservableRepository<UserProfile> {
    private const val baseUrl = "http://192.168.1.107:8000"
    private val client = HttpClientProvider.createClient()
    private val userProfileObservable = Observable<UserProfile>()

    override fun addObserver(observer: Observer<UserProfile>) {
        userProfileObservable.addObserver(observer)
    }

    override fun removeObserver(observer: Observer<UserProfile>) {
        userProfileObservable.removeObserver(observer)
    }

    override fun fetch() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userProfile = client.get<UserProfile>("$baseUrl/api/user")
                userProfileObservable.notifySuccess(userProfile)
            } catch (e: Exception) {
                userProfileObservable.notifyError(e)
            }
        }
    }
}