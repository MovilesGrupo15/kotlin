package edu.uniandes.ecosnap.data.observer

interface Observer<T> {
    fun onSuccess(data: T)
    fun onError(error: Throwable)
}