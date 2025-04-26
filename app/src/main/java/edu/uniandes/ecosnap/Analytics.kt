package edu.uniandes.ecosnap

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent

object Analytics {
    lateinit var firebaseAnalytics: FirebaseAnalytics
        private set

    fun initialize(firebaseAnalytics: FirebaseAnalytics) {
        this.firebaseAnalytics = firebaseAnalytics
    }

    fun userLoggedIn(userId: String) {
        firebaseAnalytics.setUserId(userId)
    }

    fun buttonEvent(buttonName: String) {

    }

    fun actionEvent(actionName: String) {
        firebaseAnalytics.logEvent(actionName, null)
    }

    fun screenEvent(screenName: String) {
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
            param(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
        }
    }
}