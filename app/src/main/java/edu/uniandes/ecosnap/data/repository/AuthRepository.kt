package edu.uniandes.ecosnap.data.repository

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
        val currentUser = auth.currentUser
        if (currentUser == null) {
            user = null
            authStatus.notifySuccess(null)
            Log.d("UserRepository", "User is null")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("UserRepository", "User found: ${currentUser.uid}")
                if (currentUser.isAnonymous) {
                    Log.d("UserRepository", "User is anonymous")
                    user = UserProfile(
                        email = currentUser.email ?: "",
                        userName = "Anonimo",
                        points = 0,
                        id = currentUser.uid,
                        isAnonymous = true
                    )
                    authStatus.notifySuccess(user)
                } else {
                    Log.d("UserRepository", "User is non-anonymous, fetching profile")
                    val userDoc = firestore.collection("usuarios")
                        .document(currentUser.uid)
                        .get().await()

                    if (userDoc.exists()) {
                        Log.d("UserRepository", "User profile fetched: ${userDoc.data}")
                        user = UserProfile(
                            email = userDoc.getString("email") ?: "",
                            userName = userDoc.getString("name") ?: "",
                            points = userDoc.getLong("points")?.toInt() ?: 0,
                            id = currentUser.uid,
                            isAnonymous = false
                        )
                        authStatus.notifySuccess(user)
                    } else {
                        Log.d("UserRepository", "User profile document does not exist for ${currentUser.uid}")
                        user = null
                        authStatus.notifySuccess(null)
                        authStatus.notifyError(Exception("User profile document not found"))
                    }
                }
            } catch (e: Exception) {
                Log.d("UserRepository", "User fetch failed: ${e.message}")
                user = null
                authStatus.notifySuccess(null)
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
                if (result.user == null) throw Exception("Authentication failed")
                Log.d("UserRepository", "User logged in: ${result.user?.uid}")
                authStatus.notifySuccess(UserProfile())
            } catch (e: Exception) {
                user = null
                authStatus.notifySuccess(null)
                authStatus.notifyError(e)
            }
        }
    }

    fun setAnonymous() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = auth.signInAnonymously().await()
                if (result.user == null) throw Exception("Anonymous authentication failed")
                Log.d("UserRepository", "Anonymous user signed in: ${result.user?.uid}")
                user = UserProfile(
                    id = result.user!!.uid,
                    email = "",
                    userName = "Anonimo",
                    points = 0,
                    isAnonymous = true
                )
                authStatus.notifySuccess(user)

            } catch (e: Exception) {
                Log.d("UserRepository", "Anonymous sign in failed: ${e.message}")
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
            "photoUrl" to null,
            "points" to (userProfile.points ?: 0)
        )

        return firestore.collection("usuarios")
            .document(auth.currentUser!!.uid)
            .set(userForStorage)
    }

    fun register(userProfile: UserProfile) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val authResult = auth.createUserWithEmailAndPassword(userProfile.email, userProfile.password!!).await()
                if (authResult.user == null) throw Exception("Registration failed")
                Log.d("UserRepository", "User registered: ${authResult.user?.uid}")
                create(userProfile).await()
                authStatus.notifySuccess(userProfile)
            } catch (e: Exception) {
                Log.d("UserRepository", "User registration failed: ${e.message}")
                authStatus.notifyError(e)
            }
        }
    }

    fun getCurrentUser() = user
}