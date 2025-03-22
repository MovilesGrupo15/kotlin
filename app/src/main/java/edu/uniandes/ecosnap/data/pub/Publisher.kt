package edu.uniandes.ecosnap.data.pub

data class SubscriptionToken(val id: Long)

interface Publisher<T> {
    fun subscribe(subscriber: Subscriber<T>): SubscriptionToken
    fun unsubscribe(token: SubscriptionToken)
    fun publish(data: T)
}