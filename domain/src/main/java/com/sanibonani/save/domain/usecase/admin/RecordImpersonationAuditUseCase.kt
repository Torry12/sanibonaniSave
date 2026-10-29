package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.AuditLog
import com.sanibonani.save.domain.repository.admin.AuditLogRepository
import com.sanibonani.save.domain.usecase.auth.GetAttributableActorIdUseCase
import com.sanibonani.save.domain.utils.safeRunCatching
import javax.inject.Inject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Records that support asked to open a member's screen.
 *
 * A support session is a human decision, so it is written against the signed-in administrator or
 * not at all: `audit_logs.actor_id` answering "who did this" is the whole point of the row, and a
 * placeholder names an innocent principal. [AuditLogRepository] still surfaces a failed write to the
 * log instead of returning it, and the authoritative trail for anything that moves money is the
 * server-side one.
 */
class RecordImpersonationAuditUseCase @Inject constructor(
    private val auditLogRepository: AuditLogRepository,
    private val getAttributableActorId: GetAttributableActorIdUseCase
) {
    suspend operator fun invoke(memberId: String, groupId: String): Result<Unit> = safeRunCatching {
        val actorId = getAttributableActorId()
            ?: error("This support session could not be recorded: the signed-in administrator is unknown.")
        auditLogRepository.logEvent(
            AuditLog(
                actorId = actorId,
                targetMemberId = memberId,
                targetGroupId = groupId,
                action = ACTION,
                details = buildJsonObject {
                    put("source", SOURCE)
                    put("timestamp", System.currentTimeMillis())
                }
            )
        )
    }

    private companion object {
        const val ACTION = "IMPERSONATION_REQUESTED"
        const val SOURCE = "PlatformAdminPortal"
    }
}
