package com.sanibonani.save.data.service

import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.data.remote.GeoapifyService
import com.sanibonani.save.data.utils.LocationUtils
import com.sanibonani.save.domain.service.GeocodedCoordinates
import com.sanibonani.save.domain.service.LocationAddress
import com.sanibonani.save.domain.service.LocationService
import javax.inject.Inject
import javax.inject.Named

class LocationServiceImpl @Inject constructor(
    private val geoapifyService: GeoapifyService,
    @Named("geoapify_api_key") private val apiKey: String
) : LocationService {

    override suspend fun searchAddress(query: String): Result<List<LocationAddress>> = runCatching {
        val response = geoapifyService.autocomplete(query, apiKey)
        response.features.map { feature ->
            val props = feature.properties
            LocationAddress(
                formatted = props.formatted,
                city = props.city ?: props.township ?: props.village,
                province = props.state,
                township = props.suburb ?: props.neighbourhood ?: props.quarter ?: props.township,
                latitude = props.lat ?: feature.geometry.coordinates.getOrNull(1),
                longitude = props.lon ?: feature.geometry.coordinates.getOrNull(0)
            )
        }
    }

    override suspend fun geocodeAddress(city: String, province: String, township: String?): Result<GeocodedCoordinates> = runCatching {
        val address = listOfNotNull(township, city, province)
            .filter { it.isNotBlank() }
            .joinToString(", ")
        
        if (address.isBlank()) throw IllegalArgumentException("Address components are empty")

        val response = geoapifyService.autocomplete("$address, South Africa", apiKey)
        val feature = response.features.firstOrNull() ?: throw Exception("No geocoding results found")

        val props = feature.properties
        val lat = props.lat ?: feature.geometry.coordinates.getOrNull(1)
        val lon = props.lon ?: feature.geometry.coordinates.getOrNull(0)

        if (lat != null && lon != null) {
            GeocodedCoordinates(
                latitude = lat,
                longitude = lon,
                geohash = LocationUtils.encodeGeohash(lat, lon)
            )
        } else {
            throw Exception("Coordinates missing in geocoding result")
        }
    }
}
