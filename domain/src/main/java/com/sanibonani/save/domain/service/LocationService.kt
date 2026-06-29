package com.sanibonani.save.domain.service

data class LocationAddress(
    val formatted: String,
    val city: String?,
    val province: String?,
    val township: String?,
    val latitude: Double?,
    val longitude: Double?
)

data class GeocodedCoordinates(
    val latitude: Double,
    val longitude: Double,
    val geohash: String
)

interface LocationService {
    suspend fun searchAddress(query: String): Result<List<LocationAddress>>
    suspend fun geocodeAddress(city: String, province: String, township: String? = null): Result<GeocodedCoordinates>
}
