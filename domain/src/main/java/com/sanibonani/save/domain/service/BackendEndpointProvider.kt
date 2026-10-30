package com.sanibonani.save.domain.service

/**
 * Where the hosted backend lives, without exposing the build configuration that holds it.
 */
interface BackendEndpointProvider {
    val onlineUrl: String
}
