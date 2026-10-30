package com.sanibonani.save.domain.usecase.system

import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.service.BackendEndpointProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import javax.inject.Inject

/**
 * A three-second TCP probe of the hosted backend host.
 *
 * [fallbackResult] is returned when the probe cannot run at all: a signed-in app treats an
 * unanswerable probe as "try anyway" and lets the offline cache carry the session.
 */
class CheckBackendReachabilityUseCase @Inject constructor(
    private val endpointProvider: BackendEndpointProvider
) {
    suspend operator fun invoke(fallbackResult: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = URI(endpointProvider.onlineUrl)
            val host = uri.host ?: "google.com"
            val port = if (uri.port != -1) uri.port else 443

            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 3000)
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w("SplashViewModel", "Connectivity check skipped/failed: ${e.message}")
            fallbackResult
        }
    }
}
