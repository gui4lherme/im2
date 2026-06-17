package com.example.myapplication.data

import kotlinx.coroutines.flow.Flow

class PlaceRepository(private val placeDao: PlaceDao) {
    val allPlaces: Flow<List<Place>> = placeDao.getAllPlaces()

    suspend fun insert(place: Place) {
        placeDao.insert(place)
    }

    suspend fun update(place: Place) {
        placeDao.update(place)
    }

    suspend fun delete(place: Place) {
        placeDao.delete(place)
    }

    suspend fun deleteAll() {
        placeDao.deleteAll()
    }
}
