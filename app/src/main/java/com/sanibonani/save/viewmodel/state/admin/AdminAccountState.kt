package com.sanibonani.save.viewmodel.state.admin

import com.sanibonani.save.domain.model.Group

data class AdminAccountState(
    val group: Group? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)
