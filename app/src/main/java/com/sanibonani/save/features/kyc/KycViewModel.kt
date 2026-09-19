package com.sanibonani.save.features.kyc

import android.net.Uri
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.sanibonani.save.domain.service.KycService
import com.sanibonani.save.viewmodel.BaseViewModel
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
        val s = uiState.value
        viewModelScope.launch {
            kycService.processKycAi(memberId, File(s.idUri?.path!!), File(uri.path!!)).onSuccess {
                updateState { it.copy(step = KycStep.SUCCESS) }
            }
        }
    }
}
