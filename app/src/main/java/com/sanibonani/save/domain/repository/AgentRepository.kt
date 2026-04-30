package com.sanibonani.save.domain.repository

import com.sanibonani.save.domain.model.AgentTask
import com.sanibonani.save.domain.model.AgentResult
import kotlinx.coroutines.flow.Flow

interface AgentRepository {
    fun submitTask(task: AgentTask): Flow<Result<AgentResult>>
    fun observeResults(taskId: String): Flow<Result<AgentResult>>
}
