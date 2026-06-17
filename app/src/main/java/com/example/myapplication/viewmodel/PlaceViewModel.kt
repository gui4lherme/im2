package com.example.myapplication.viewmodel

import androidx.lifecycle.*
import com.example.myapplication.data.Place
import com.example.myapplication.data.PlaceRepository
import kotlinx.coroutines.launch

class PlaceViewModel(private val repository: PlaceRepository) : ViewModel() {

    val allPlaces: LiveData<List<Place>> = repository.allPlaces.asLiveData()

    fun insert(place: Place) = viewModelScope.launch {
        repository.insert(place)
    }

    fun update(place: Place) = viewModelScope.launch {
        repository.update(place)
    }

    fun delete(place: Place) = viewModelScope.launch {
        repository.delete(place)
    }

    fun deleteAll() = viewModelScope.launch {
        repository.deleteAll()
    }
}

class PlaceViewModelFactory(private val repository: PlaceRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlaceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PlaceViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
