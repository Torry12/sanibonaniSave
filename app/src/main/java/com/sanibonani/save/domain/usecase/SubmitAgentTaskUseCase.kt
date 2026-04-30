package com.sanibonani.save.domain.usecase

import com.sanibonani.save.domain.model.AgentTask
import com.sanibonani.save.domain.model.AgentResult
import com.sanibonani.save.domain.repository.AgentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SubmitAgentTaskUseCase @Inject constructor(
    private val agentRepository: AgentRepository
) {
    operator fun invoke(task: AgentTask): Flow<Result<AgentResult>> =
        agentRepository.submitTask(task)
}
