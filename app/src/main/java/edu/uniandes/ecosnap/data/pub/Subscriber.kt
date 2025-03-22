package edu.uniandes.ecosnap.data.pub

interface Subscriber<T> {
    fun onNext(data: T)
    fun onError(error: Throwable)
}