package edu.uniandes.ecosnap.data.observer

interface ObservableRepository<T> {
    fun addObserver(observer: Observer<T>)
    fun removeObserver(observer: Observer<T>)
    fun fetch()
}