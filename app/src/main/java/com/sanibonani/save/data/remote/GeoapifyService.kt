package com.sanibonani.save.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable
data class GeoapifyResponse(
    val features: List<Feature> = emptyList()
)

@Serializable
data class Feature(
    val properties: Properties,
    val geometry: Geometry
)

@Serializable
data class Properties(
    val formatted: String = "",
    val city: String? = null,
    val state: String? = null,
    val suburb: String? = null,
    val quarter: String? = null,
    val neighbourhood: String? = null,
    val township: String? = null,
    val village: String? = null,
    val county: String? = null,
    val district: String? = null,
    val postcode: String? = null,
    val street: String? = null,
    val housenumber: String? = null,
    val country: String = "",
    val lon: Double? = null,
    val lat: Double? = null
)

@Serializable
data class Geometry(
    val coordinates: List<Double> = emptyList()
)

interface GeoapifyService {
    @GET("v1/geocode/autocomplete")
    suspend fun autocomplete(
        @Query("text") text: String,
        @Query("apiKey") apiKey: String,
        @Query("filter") filter: String = "countrycode:za", // Filter for South Africa
        @Query("limit") limit: Int = 5
    ): GeoapifyResponse
}
