package com.sanibonani.save.ui.components

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

data class DashboardTabSpec(
    val index: Int,
    val title: String
)

@Composable
fun syncDashboardPager(
    selectedTab: Int,
    pagerState: PagerState,
    tabs: List<DashboardTabSpec>,
    onTabSelected: (Int) -> Unit
) {
    LaunchedEffect(selectedTab) {
        val targetPage = tabs.indexOfFirst { it.index == selectedTab }.takeIf { it >= 0 } ?: 0
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        val pageIndex = tabs.getOrNull(pagerState.currentPage)?.index ?: return@LaunchedEffect
        if (pageIndex != selectedTab) {
            onTabSelected(pageIndex)
        }
    }
}
