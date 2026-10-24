package com.sanibonani.save.features.kyc

import android.net.Uri
import com.sanibonani.save.domain.service.KycService
import com.sanibonani.save.features.common.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject

enum class KycStep { ID_FRONT, SELFIE, SUCCESS }

data class KycUiState(
    val step: KycStep = KycStep.ID_FRONT,
    val idUri: Uri? = null,
    val selfieUri: Uri? = null
)

@HiltViewModel
class KycViewModel @Inject constructor(
    private val kycService: KycService
) : BaseViewModel<KycUiState>(KycUiState()) {

    fun onIdCaptured(uri: Uri) { updateState { it.copy(idUri = uri, step = KycStep.SELFIE) } }

    fun onSelfieCaptured(uri: Uri, memberId: String) {
        updateState { it.copy(selfieUri = uri) }
        val idPath = uiState.value.idUri?.path ?: return
        val selfiePath = uri.path ?: return
        launchJob("process_kyc") {
            kycService.processKycAi(memberId, File(idPath), File(selfiePath)).onSuccess {
                updateState { it.copy(step = KycStep.SUCCESS) }
            }
        }
    }
}
