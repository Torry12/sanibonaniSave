package com.sanibonani.save.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class RecoveryMethod {
    EMAIL,
    WHATSAPP
}

/** A single enrolled MFA factor (e.g. an authenticator-app TOTP secret). */
@Parcelize
data class MfaFactor(
    val id: String,
    val friendlyName: String,
    val type: String,
    val status: String
) : Parcelable

/** Result of enrolling a new TOTP factor, carrying the QR seeding data. */
@Parcelize
data class TotpEnrollment(
    val id: String,
    val qrCode: String,
    val secret: String,
    val uri: String
) : Parcelable

/**
 * Domain-owned view of an authenticated Supabase session.
 *
 * The domain interfaces used to expose the SDK's `UserSession` /
 * `SessionStatus` types directly, which meant every consumer of the port had to
 * depend on the Supabase SDK. Only a handful of fields are ever read, so the
 * domain now defines its own minimal shape and `:data` maps the SDK object to it.
 */
data class AuthSession(
    val userId: String,
    val email: String? = null,
    val accessToken: String? = null
)

/**
 * Domain-owned session lifecycle.
 *
 * Mirrors the two states the app reacts to (authenticated vs not); anything
 * richer (refresh tokens, session source, expiry) stays in `:data`.
 */
sealed interface AuthSessionStatus {
    data class Authenticated(val session: AuthSession) : AuthSessionStatus
    data class NotAuthenticated(val hasSession: Boolean = false) : AuthSessionStatus
}
