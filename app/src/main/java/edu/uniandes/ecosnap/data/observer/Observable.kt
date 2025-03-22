package edu.uniandes.ecosnap.data.observer

class Observable<T> {
    private val observers = mutableListOf<Observer<T>>()

    fun addObserver(observer: Observer<T>) {
        if (!observers.contains(observer)) {
            observers.add(observer)
        }
    }

    fun removeObserver(observer: Observer<T>) {
        observers.remove(observer)
    }

    fun notifySuccess(data: T) {
        observers.forEach { it.onSuccess(data) }
    }

    fun notifyError(error: Throwable) {
        observers.forEach { it.onError(error) }
    }

    fun hasObservers(): Boolean = observers.isNotEmpty()
}