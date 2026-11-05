package com.sanibonani.save.features.landing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.sanibonani.save.R
import com.sanibonani.save.features.common.presentation.UIEvent
import com.sanibonani.save.features.landing.ui.components.FeatureDetail
import com.sanibonani.save.features.landing.ui.components.FeatureDetailDialog
import com.sanibonani.save.features.landing.presentation.LandingIntent
import com.sanibonani.save.features.landing.presentation.LandingViewModel
import com.sanibonani.save.ui.components.LanguageSelector
import com.sanibonani.save.ui.components.OfflineBanner
import com.sanibonani.save.ui.theme.Navy
import com.sanibonani.save.ui.utils.ToastUtils
import com.sanibonani.save.ui.utils.rememberClickDebouncer

@Composable
fun LandingScreen(
    onNavigateLogin: (String?) -> Unit,
    onNavigateRegisterGroup: () -> Unit,
    onNavigateBrowseGroups: () -> Unit,
    onNavigateDashboard: () -> Unit,
    onNavigateMemberPortal: () -> Unit,
    viewModel: LandingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clickDebouncer = rememberClickDebouncer()
    val context = LocalContext.current

    var selectedFeature by remember { mutableStateOf<FeatureDetail?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UIEvent.ShowMessage -> ToastUtils.showInfo(context, event.message)
                is UIEvent.ShowError -> ToastUtils.showError(context, event.message)
                else -> Unit
            }
        }
    }

    // Auto-refresh when coming back online
    LaunchedEffect(uiState.isSystemOnline) {
        if (uiState.isSystemOnline && uiState.analytics.getOrNull() == null) {
            // Nobody asked for this by name, so a refresh already reading the same landing answers
            // it; tearing that pass down and starting another is what put three refreshes in the
            // log for one cold start.
            viewModel.refreshData()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            LandingHeader(
                isOnline = uiState.isSystemOnline,
                isAirplaneMode = uiState.isAirplaneMode
            )

            HeroSection(
                uiState = uiState,
                clickDebouncer = clickDebouncer,
                onNavigateLogin = onNavigateLogin,
                onNavigateRegisterGroup = onNavigateRegisterGroup,
                onNavigateBrowseGroups = onNavigateBrowseGroups,
                onNavigateDashboard = onNavigateDashboard,
                onNavigateMemberPortal = onNavigateMemberPortal
            )

            StatsSection(
                analytics = uiState.analytics,
                onRetry = { viewModel.onIntent(LandingIntent.Refresh) }
            )

            FeaturesSection(
                clickDebouncer = clickDebouncer,
                onSelectFeature = { selectedFeature = it },
                onNavigateBrowseGroups = onNavigateBrowseGroups
            )

            ComplianceSection()

            CallToActionSection(
                clickDebouncer = clickDebouncer,
                onNavigateRegisterGroup = onNavigateRegisterGroup
            )

            HowItWorksSection()

            AboutAndHelpSection()

            Spacer(Modifier.height(48.dp))
        }
    }

    selectedFeature?.let { detail ->
        FeatureDetailDialog(
            detail = detail,
            onDismiss = { selectedFeature = null }
        )
    }
}

@Composable
private fun LandingHeader(isOnline: Boolean, isAirplaneMode: Boolean) {
    // ── Network Status ────────────────────────────────────────────────
    if (!isOnline || isAirplaneMode) {
        OfflineBanner(
            isOnline = false,
            modifier = Modifier.statusBarsPadding(),
            message = if (isAirplaneMode) stringResource(R.string.ui_offline_airplane_mode)
                      else stringResource(R.string.ui_offline_cached_mode)
        )
    }

    // ── Language Selector ─────────────────────────────────────────────
    LanguageSelector(
        modifier = Modifier
            .fillMaxWidth()
            .background(Navy)
            .padding(horizontal = 24.dp, vertical = 8.dp)
    )
}
