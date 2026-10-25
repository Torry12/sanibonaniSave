package com.sanibonani.save.features.group.presentation

import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.group.GroupDocumentRepository
import com.sanibonani.save.domain.usecase.admin.ActivateGroupUseCase
import com.sanibonani.save.domain.usecase.group.CreateGroupUseCase
import com.sanibonani.save.domain.utils.toUserMessage
import com.sanibonani.save.service.MemberGroupContextCacheService
import com.sanibonani.save.service.UserProfileCacheService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow

internal interface GroupRegistrationHost {
    val state: StateFlow<GroupRegistrationState>
    fun updateState(reducer: (GroupRegistrationState) -> GroupRegistrationState)
    fun launchJob(key: String, block: suspend CoroutineScope.() -> Unit): Job
}

/**
 * Owns the group-creation lifecycle: validation, creation, constitution upload, activation,
 * and resuming after the payment sheet. The constitution bytes are held here rather than in
 * [GroupRegistrationState]: a 3MB PDF does not belong in a StateFlow that Compose recomposes
 * on every keystroke.
 */
internal class GroupRegistrationSubmitter(
    private val createGroupUseCase: CreateGroupUseCase,
    private val activateGroupUseCase: ActivateGroupUseCase,
    private val supabaseRepo: SupabaseRepository,
    private val userProfileCache: UserProfileCacheService?,
    private val memberContextCacheService: MemberGroupContextCacheService?,
    private val documentRepository: GroupDocumentRepository?,
) {

    private var pendingConstitutionBytes: ByteArray? = null

    fun selectConstitution(fileName: String, fileBytes: ByteArray, host: GroupRegistrationHost) {
        pendingConstitutionBytes = fileBytes
        host.updateState { it.copy(pendingConstitutionName = fileName, useStandardConstitution = false) }
    }

    fun clearPendingConstitution() {
        pendingConstitutionBytes = null
    }

    fun submit(host: GroupRegistrationHost) {
        val s = host.state.value
        val alreadyCreated = s.createdGroupId
        if (!alreadyCreated.isNullOrBlank()) {
            // The group exists in the database already (the payment sheet was dismissed after
            // creation). Running creation again would register a duplicate group and a second
            // admin account, so resume the step that is still owed for this group instead.
            if (s.platformRegistrationFee == Money.ZERO) {
                host.launchJob("submit_group") {
                    storePendingConstitution(alreadyCreated, host)
                    autoActivateZeroFee(alreadyCreated, host)
                }
            } else {
                host.updateState { it.copy(needsPayment = true, error = null) }
            }
            return
        }

        val groupRes = RegisterGroupValidator.toGroupDraft(s)
        if (groupRes.isFailure) {
            host.updateState {
                it.copy(error = groupRes.exceptionOrNull()?.toUserMessage() ?: "Validation failed. Please check all fields.")
            }
            return
        }
        val group = groupRes.getOrThrow()

        host.launchJob("submit_group") {
            host.updateState { it.copy(isSubmitting = true, error = null) }
            val result = if (s.street.isNotBlank() || s.township.isNotBlank()) {
                createGroupUseCase(
                    group = group,
                    adminEmail = s.adminEmail,
                    adminPassword = s.adminPassword,
                    adminFullName = s.adminFullName,
                    adminPhone = s.adminPhone,
                    adminIdNumber = s.adminIdNumber,
                    adminStreet = s.street.takeIf { it.isNotBlank() },
                    adminSuburb = s.township.takeIf { it.isNotBlank() }
                )
            } else {
                createGroupUseCase(
                    group = group,
                    adminEmail = s.adminEmail,
                    adminPassword = s.adminPassword,
                    adminFullName = s.adminFullName,
                    adminPhone = s.adminPhone,
                    adminIdNumber = s.adminIdNumber
                )
            }

            result.onSuccess { id ->
                if (s.adminFullName.isNotBlank() && s.adminEmail.isNotBlank()) {
                    userProfileCache?.save(s.adminFullName, s.adminEmail, s.adminPhone)
                }

                storePendingConstitution(id, host)

                if (s.platformRegistrationFee == Money.ZERO) {
                    autoActivateZeroFee(id, host)
                } else {
                    host.updateState { it.copy(createdGroupId = id, isSubmitting = false, needsPayment = true) }
                }
            }.onFailure { e ->
                host.updateState { it.copy(isSubmitting = false, error = e.toUserMessage()) }
            }
        }
    }

    fun finalizeAfterPayment(txId: String?, host: GroupRegistrationHost) {
        val groupId = host.state.value.createdGroupId
        if (groupId.isNullOrBlank()) {
            // The fee may already be captured at this point, so silently returning would take
            // the money and leave the group inactive with nothing on screen to explain it.
            AppLogger.e(tag = "GroupRegSubmitter", message = "Payment finalization has no group to activate (txId=$txId).")
            host.updateState {
                it.copy(error = "We could not match this payment to a group. Your group is not active yet - please retry the registration or contact support.")
            }
            return
        }
        host.launchJob("finalize_payment") {
            host.updateState { it.copy(isSubmitting = true, error = null) }
            storePendingConstitution(groupId, host)
            activateGroupUseCase(groupId, txId)
                .onSuccess {
                    warmUpMemberContext()
                    host.updateState { it.copy(isSubmitting = false, success = true, needsPayment = false) }
                }
                .onFailure { e ->
                    host.updateState { it.copy(isSubmitting = false, error = e.toUserMessage()) }
                }
        }
    }

    /**
     * Losing a completed registration because a PDF could not be stored would discard the
     * group, so the upload is intentionally non-fatal and only logged.
     */
    private suspend fun storePendingConstitution(groupId: String, host: GroupRegistrationHost) {
        val bytes = pendingConstitutionBytes ?: return
        val fileName = host.state.value.pendingConstitutionName ?: return
        val repository = documentRepository ?: return
        repository.uploadConstitution(groupId, bytes, fileName)
            .onSuccess { url ->
                pendingConstitutionBytes = null
                host.updateState { it.copy(constitutionUrl = url) }
            }
            .onFailure { e ->
                AppLogger.w("GroupRegSubmitter", "Constitution upload failed for group $groupId: ${e.message}")
            }
    }

    /**
     * Kept separate from creation so a retry after a failed activation reuses the group that
     * already exists.
     */
    private suspend fun autoActivateZeroFee(groupId: String, host: GroupRegistrationHost) {
        host.updateState { it.copy(isSubmitting = true, error = null) }
        activateGroupUseCase(groupId, txId = "FREE_ONBOARDING:$groupId")
            .onSuccess {
                warmUpMemberContext()
                host.updateState { it.copy(createdGroupId = groupId, isSubmitting = false, success = true, needsPayment = false) }
            }
            .onFailure { e ->
                AppLogger.w("GroupRegSubmitter", "Auto-activation failed for zero-fee group: ${e.message}")
                // Nothing is owed here, so the payment screen would be a dead end;
                // keep the group and surface the failure so the admin can retry.
                host.updateState { it.copy(createdGroupId = groupId, isSubmitting = false, needsPayment = false, error = e.toUserMessage()) }
            }
    }

    private suspend fun warmUpMemberContext() {
        val userId = supabaseRepo.currentUserId
        if (userId != null) {
            memberContextCacheService?.warmUpForUser(userId)
        }
    }
}
