package com.sanibonani.save.features.group.presentation

import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.AdminFeeState
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.usecase.admin.ActivateGroupUseCase
import com.sanibonani.save.domain.usecase.auth.GetAttributableActorIdUseCase
import com.sanibonani.save.domain.usecase.group.CreateGroupUseCase
import com.sanibonani.save.domain.usecase.group.GetGroupByIdUseCase
import com.sanibonani.save.domain.usecase.group.UploadGroupConstitutionUseCase
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
    private val createGroup: CreateGroupUseCase,
    private val activateGroup: ActivateGroupUseCase,
    private val getAttributableActorId: GetAttributableActorIdUseCase,
    private val userProfileCache: UserProfileCacheService?,
    private val memberContextCacheService: MemberGroupContextCacheService?,
    private val uploadGroupConstitution: UploadGroupConstitutionUseCase?,
    private val getGroupById: GetGroupByIdUseCase? = null,
) {

    private var pendingConstitutionBytes: ByteArray? = null

    private fun currentState(host: GroupRegistrationHost): GroupRegistrationState = host.state.value

    fun selectConstitution(fileName: String, fileBytes: ByteArray, host: GroupRegistrationHost) {
        pendingConstitutionBytes = fileBytes
        host.updateState { it.copy(pendingConstitutionName = fileName, useStandardConstitution = false) }
    }

    fun clearPendingConstitution() {
        pendingConstitutionBytes = null
    }

    fun submit(host: GroupRegistrationHost) {
        val state = currentState(host)
        val alreadyCreated = state.createdGroupId
        if (!alreadyCreated.isNullOrBlank()) {
            // The group exists in the database already (the payment sheet was dismissed after
            // creation). Running creation again would register a duplicate group and a second
            // admin account, so resume the step that is still owed for this group instead.
            if (state.platformRegistrationFee == Money.ZERO) {
                host.launchJob("submit_group") {
                    storePendingConstitution(alreadyCreated, host)
                    autoActivateZeroFee(alreadyCreated, host)
                }
            } else {
                host.updateState { it.copy(needsPayment = true, error = null) }
            }
            return
        }

        val groupRes = RegisterGroupValidator.toGroupDraft(state)
        if (groupRes.isFailure) {
            host.updateState {
                it.copy(error = groupRes.exceptionOrNull()?.toUserMessage() ?: "Validation failed. Please check all fields.")
            }
            return
        }
        val group = groupRes.getOrThrow()

        host.launchJob("submit_group") {
            host.updateState { it.copy(isSubmitting = true, error = null) }
            val result = if (state.street.isNotBlank() || state.township.isNotBlank()) {
                createGroup(
                    group = group,
                    adminEmail = state.adminEmail,
                    adminPassword = state.adminPassword,
                    adminFullName = state.adminFullName,
                    adminPhone = state.adminPhone,
                    adminIdNumber = state.adminIdNumber,
                    adminStreet = state.street.takeIf { it.isNotBlank() },
                    adminSuburb = state.township.takeIf { it.isNotBlank() }
                )
            } else {
                createGroup(
                    group = group,
                    adminEmail = state.adminEmail,
                    adminPassword = state.adminPassword,
                    adminFullName = state.adminFullName,
                    adminPhone = state.adminPhone,
                    adminIdNumber = state.adminIdNumber
                )
            }

            result.onSuccess { id ->
                if (state.adminFullName.isNotBlank() && state.adminEmail.isNotBlank()) {
                    userProfileCache?.save(state.adminFullName, state.adminEmail, state.adminPhone)
                }

                storePendingConstitution(id, host)

                if (state.platformRegistrationFee == Money.ZERO) {
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
        val state = currentState(host)
        val groupId = state.createdGroupId
        if (groupId.isNullOrBlank()) {
            AppLogger.e(tag = "GroupRegSubmitter", message = "Payment finalization has no group to activate (txId=$txId).")
            host.updateState {
                it.copy(error = "We could not match this payment to a group. Your group is not active yet - please retry the registration or contact support.")
            }
            return
        }

        host.launchJob("finalize_payment") {
            val alreadyPaidOrActive = getGroupById?.invoke(groupId)?.getOrNull()?.let { group ->
                group.registrationPaid || group.feeStatus == AdminFeeState.PAID || !group.isRegistrationFeeOutstanding
            } == true

            if (alreadyPaidOrActive) {
                AppLogger.w("GroupRegSubmitter", "Skipping duplicate activation for group $groupId; registration is already paid/active.")
                host.updateState { it.copy(isSubmitting = false, success = true, needsPayment = false, error = null) }
                return@launchJob
            }

            if (txId.isNullOrBlank()) {
                AppLogger.w("GroupRegSubmitter", "Payment finalization aborted for group $groupId because no txId was captured.")
                host.updateState { it.copy(isSubmitting = false, error = "Payment was not captured, so the group could not be activated.") }
                return@launchJob
            }

            host.updateState { it.copy(isSubmitting = true, error = null) }
            storePendingConstitution(groupId, host)
            activateGroup(groupId, txId)
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
        val fileName = currentState(host).pendingConstitutionName ?: return
        val useCase = uploadGroupConstitution ?: return
        useCase(groupId, bytes, fileName)
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
        activateGroup(groupId, txId = "FREE_ONBOARDING:$groupId")
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
        val userId = getAttributableActorId()
        if (userId != null) {
            memberContextCacheService?.warmUpForUser(userId)
        }
    }
}
