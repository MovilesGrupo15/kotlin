package edu.uniandes.ecosnap.data.repository

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import edu.uniandes.ecosnap.data.observer.Observable
import edu.uniandes.ecosnap.data.observer.ObservableRepository
import edu.uniandes.ecosnap.data.observer.Observer
import edu.uniandes.ecosnap.domain.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object AuthRepository: ObservableRepository<UserProfile?> {
    private val authStatus = Observable<UserProfile?>()
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var user: UserProfile? = null

    override fun addObserver(observer: Observer<UserProfile?>) {
        authStatus.addObserver(observer)
    }

    override fun removeObserver(observer: Observer<UserProfile?>) {
        authStatus.removeObserver(observer)
    }

    override fun fetch() {
        if (auth.currentUser == null) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userDoc = firestore.collection("usuarios")
                    .document(auth.currentUser!!.uid)
                    .get().await()

                Log.d("UserRepository", "User fetched: ${userDoc.data}")
                user = UserProfile(
                    email = userDoc.getString("email") ?: "",
                    userName = userDoc.getString("name") ?: "",
                    points = userDoc.getLong("points")?.toInt() ?: 0,
                    id = auth.currentUser!!.uid,
                )
                authStatus.notifySuccess(user)
            } catch (e: Exception) {
                Log.d("UserRepository", "User fetch failed")
                authStatus.notifyError(e)
            }
        }
    }

    fun initializeAuth() {
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
    }

    fun login(email: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                if (result.user == null) throw Exception("Unauthenticated")
                Log.d("UserRepository", "User logged in: ${result.user?.uid}")
                authStatus.notifySuccess(UserProfile())
            } catch (e: Exception) {
                user = null
                authStatus.notifySuccess(null)
                authStatus.notifyError(e)
            }
        }
    }

    private fun create(userProfile: UserProfile): Task<Void> {
        val userForStorage = mapOf(
            "email" to userProfile.email,
            "name" to userProfile.userName,
            "id" to auth.currentUser!!.uid,
            "photoUrl" to null
        )

        return firestore.collection("usuarios")
            .document(auth.currentUser!!.uid)
            .set(userForStorage)
    }


    fun register(userProfile: UserProfile) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val authResult = auth.createUserWithEmailAndPassword(userProfile.email, userProfile.password!!).await()
                if (authResult.user == null) throw Exception("Unauthenticated")
                Log.d("UserRepository", "User registered: $authResult")
                create(userProfile).await()
                authStatus.notifySuccess(userProfile)
            } catch (e: Exception) {
                Log.d("UserRepository", "User registered failed")
                authStatus.notifyError(e)
            }
        }
    }

    fun getCurrentUser() = user
}
