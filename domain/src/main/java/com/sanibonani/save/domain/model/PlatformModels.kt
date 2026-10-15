@file:OptIn(ExperimentalSerializationApi::class)

package com.sanibonani.save.domain.model

import kotlinx.serialization.*

/**
 * Note: `PlatformFees` used to be declared in this file alongside [UserProfile].
 * That created a second singleton with the same fields and the same names as
 * `com.sanibonani.save.domain.config.PlatformFees`, so unqualified references
 * *within this package* silently bound to the wrong one and never saw fee-config
 * updates. It has been removed in favour of the single, canonical
 * [com.sanibonani.save.domain.config.PlatformFees].
 */

@Serializable
data class UserProfile(
    val id: String,
    @SerialName("full_name") val fullName: String?,
    val email: String?,
    val role: String
)
