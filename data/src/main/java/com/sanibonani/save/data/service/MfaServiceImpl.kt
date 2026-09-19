package com.sanibonani.save.data.service

import com.sanibonani.save.domain.model.MfaFactor
import com.sanibonani.save.domain.model.TotpEnrollment
import com.sanibonani.save.domain.repository.system.DataConnectionProtocol
import com.sanibonani.save.domain.service.MfaService
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.mfa.FactorType
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MfaServiceImpl @Inject constructor(
    private val connectionProtocol: DataConnectionProtocol
) : MfaService {

    private val client: SupabaseClient get() = connectionProtocol.getRemoteClient(SupabaseClient::class.java)!!

    override val isMfaVerified: Boolean
        get() = client.auth.currentSessionOrNull()?.user?.appMetadata?.get("aal")?.jsonPrimitive?.contentOrNull == "aal2"

    override suspend fun enrollTotp(friendlyName: String): Result<TotpEnrollment> = runCatching {
        val response = client.auth.mfa.enroll(FactorType.TOTP, friendlyName)
        TotpEnrollment(factorId = response.id, qrCodeSvg = "", secret = "")
    }

    override suspend fun verifyMfa(factorId: String, code: String, saveChallenge: Boolean): Result<Unit> = runCatching {
        val challenge = client.auth.mfa.createChallenge(factorId)
        client.auth.mfa.verifyChallenge(factorId, challenge.id, code, saveChallenge)
    }

    override suspend fun unenrollMfa(factorId: String): Result<Unit> = runCatching {
        client.auth.mfa.unenroll(factorId)
    }

    override suspend fun getMfaFactors(): Result<List<MfaFactor>> = runCatching {
        client.auth.currentUserOrNull()?.factors?.map {
            MfaFactor(
                id = it.id,
                friendlyName = it.friendlyName ?: "Unnamed",
                factorType = it.factorType.toString(),
                status = "active"
            )
        } ?: emptyList()
    }
}
