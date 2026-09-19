package com.sanibonani.save.viewmodel.dynamics

import androidx.lifecycle.viewModelScope
import com.sanibonani.save.analytics.AnalyticsTaxonomy
import com.sanibonani.save.analytics.AppAnalytics
import com.sanibonani.save.domain.utils.*
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.repository.dynamics.*
import com.sanibonani.save.domain.repository.treasury.*
import com.sanibonani.save.domain.repository.disbursement.*
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.usecase.*
import com.sanibonani.save.domain.usecase.dynamics.*
import com.sanibonani.save.domain.usecase.disbursement.*
import com.sanibonani.save.domain.usecase.credit.ProcessLoanDisbursementUseCase
import com.sanibonani.save.domain.service.*
import com.sanibonani.save.data.sync.*
import com.sanibonani.save.viewmodel.BaseViewModel
import com.sanibonani.save.viewmodel.state.UIEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import java.util.Locale
import javax.inject.Inject

data class PlatformAdminUiState(
    val analytics: PlatformAnalytics = PlatformAnalytics(),
    val groups: List<Group> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null,
    val selectedTab: Int = 0,
    val memberCharge: String = "10.0",
    val registrationFee: String = "700.0",
    val selectedGroupMetrics: ActuarialMetrics? = null,
    val selectedGroupHealthScore: GroupHealthScore? = null,
    val payouts: List<PayoutRequest> = emptyList(),
    val escalatedClaims: List<BeneficiaryPayoutClaim> = emptyList(),
    val loanRequestsByGroup: Map<String, List<Loan>> = emptyMap(),
    val memberBehaviorInsights: List<MemberBehaviorInsight> = emptyList(),
    val platformLedger: List<LedgerEntry> = emptyList(),
    val totalRevenueShare: Money = Money.ZERO,
    val backups: List<DatabaseBackup> = emptyList(),
    val isFetchingBackups: Boolean = false
)

@HiltViewModel
class PlatformAdminViewModel @Inject constructor(
    private val platformRepo: PlatformRepository,
    private val payoutRepo: PayoutRepository,
    private val loanRepo: LoanRepository,
    private val processPayoutUseCase: ProcessDisbursementUseCase,
    private val claimRepo: BeneficiaryClaimRepository,
    private val processBurialClaimUseCase: ProcessBurialClaimUseCase,
    private val memberRepo: MemberRepository,
    private val platformConfigRepository: PlatformConfigRepository,
    private val ledgerRepo: LedgerRepository,
    private val reconRepo: ReconciliationRepository,
    private val auditRepo: AuditRepository,
    private val auditLogRepo: AuditLogRepository,
    private val calculateGroupHealthScoreUseCase: CalculateGroupHealthScoreUseCase,
    private val exportRepo: ExportRepository,
    private val dataVerificationService: DataVerificationService,
    private val syncManager: RealtimeSyncManager,
    private val processLoanDisbursementUseCase: ProcessLoanDisbursementUseCase,
    private val supabaseRepo: SupabaseRepository
) : BaseViewModel<PlatformAdminUiState>(PlatformAdminUiState()) {

    val state: StateFlow<PlatformAdminUiState> = uiState
    private var loadDataJob: Job? = null
    private val isActive = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            if (supabaseRepo.getUserRole() != UserRole.PLATFORM_ADMIN) return@launch
            loadData()
        }
    }

    fun loadData() {
        loadDataJob?.cancel()
        loadDataJob = viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            coroutineScope {
                val analytics = platformRepo.getPlatformAnalytics().getOrNull() ?: PlatformAnalytics()
                val groups = platformRepo.getAllGroups().getOrNull() ?: emptyList()
                val claims = claimRepo.getEscalatedClaimsSync().getOrNull() ?: emptyList()
                val ledger = ledgerRepo.getPlatformLedger().getOrNull() ?: emptyList()

                updateState { it.copy(
                    analytics = analytics,
                    groups = groups,
                    escalatedClaims = claims,
                    platformLedger = ledger,
                    isLoading = false
                ) }
            }
        }
    }

    fun setActive(active: Boolean) { isActive.value = active }
}
