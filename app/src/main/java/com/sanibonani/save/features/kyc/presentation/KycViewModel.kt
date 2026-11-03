package com.sanibonani.save.features.kyc.presentation

import android.net.Uri
import com.sanibonani.save.domain.service.KycService
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject

enum class KycStep { ID_FRONT, SELFIE, SUCCESS }

data class KycUiState(
    val step: KycStep = KycStep.ID_FRONT,
    val idUri: Uri? = null
)

@HiltViewModel
class KycViewModel @Inject constructor(
    private val kycService: KycService
) : BaseViewModel<KycUiState>(KycUiState()) {

    fun onIdCaptured(uri: Uri) { updateState { it.copy(idUri = uri, step = KycStep.SELFIE) } }

    fun onCaptureFailed(error: Throwable) {
        // The camera already wrote its own exception to the log; the form is what the user can
        // see, and a shutter that produced nothing has to say so.
        handleError(error, "That photo could not be taken. Please try again.")
    }

    fun onSelfieCaptured(uri: Uri, memberId: String) {
        val idPath = uiState.value.idUri?.path
        if (idPath == null) {
            // Nothing on the form to submit: the step stays where it is, so say why rather than
            // leaving the user waiting on a run that never started.
            handleError(
                IllegalStateException("No ID front photo on the KYC form"),
                "Take the photo of your ID first - the selfie cannot be submitted without it."
            )
            return
        }
        val selfiePath = uri.path
        if (selfiePath == null) {
            handleError(
                IllegalStateException("The selfie photo could not be read"),
                "That selfie could not be read. Please take it again."
            )
            return
        }
        launchJob("process_kyc") {
            kycService.processKycAi(memberId, File(idPath), File(selfiePath))
                .onSuccess {
                    updateState { it.copy(step = KycStep.SUCCESS) }
                }
                .onFailure { e ->
                    // Advancing to SUCCESS is the gate that lets a member transact, so only the
                    // service's own answer can move the step.
                    handleError(e, "Your documents could not be verified. Please try again.")
                }
        }
    }
}
