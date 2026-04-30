package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.model.AgentTask
import com.sanibonani.save.domain.model.AgentResult
import com.sanibonani.save.domain.repository.AgentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class AgentRepositoryImpl @Inject constructor() : AgentRepository {
    override fun submitTask(task: AgentTask): Flow<Result<AgentResult>> = flow {
        // TODO: Integrate with real agent API (REST, WebSocket, etc.)
        emit(Result.success(AgentResult(taskId = task.id, status = "pending", output = "", createdAt = null)))
    }

    override fun observeResults(taskId: String): Flow<Result<AgentResult>> = flow {
        // TODO: Integrate with real agent API for result streaming
        emit(Result.success(AgentResult(taskId = taskId, status = "complete", output = "Sample output", createdAt = null)))
    }
}
