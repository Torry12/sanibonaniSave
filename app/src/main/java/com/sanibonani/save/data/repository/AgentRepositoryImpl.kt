package com.sanibonani.save.data.repository

import com.sanibonani.save.data.remote.EdgeFunctionGateway
import com.sanibonani.save.domain.model.AgentTask
import com.sanibonani.save.domain.model.AgentResult
import com.sanibonani.save.domain.repository.AgentRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject

class AgentRepositoryImpl @Inject constructor(
    private val edgeFunctionGateway: EdgeFunctionGateway
) : AgentRepository {
    override fun submitTask(task: AgentTask): Flow<Result<AgentResult>> = flow {
        val submitted = edgeFunctionGateway.invoke(
            functionName = "agent-orchestrator",
            payload = buildJsonObject {
                put("action", "submit")
                put("task_id", task.id)
                put("type", task.type)
                put("payload", task.payload)
            }
        ).mapCatching { it.toAgentResult() }

        emit(submitted)

        val initial = submitted.getOrNull() ?: return@flow
        if (initial.status.equals("completed", ignoreCase = true) || initial.status.equals("failed", ignoreCase = true)) {
            return@flow
        }

        observeResults(initial.taskId).collect { emit(it) }
    }

    override fun observeResults(taskId: String): Flow<Result<AgentResult>> = flow {
        repeat(MAX_POLLS) { attempt ->
            val result = edgeFunctionGateway.invoke(
                functionName = "agent-orchestrator",
                payload = buildJsonObject {
                    put("action", if (attempt == MAX_POLLS - 1) "result" else "status")
                    put("task_id", taskId)
                }
            ).mapCatching { it.toAgentResult() }

            emit(result)

            val current = result.getOrNull() ?: return@flow
            if (current.status.equals("completed", ignoreCase = true) || current.status.equals("failed", ignoreCase = true)) {
                return@flow
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun kotlinx.serialization.json.JsonObject.toAgentResult(): AgentResult {
        return AgentResult(
            taskId = this["task_id"]?.jsonPrimitive?.content.orEmpty(),
            status = this["status"]?.jsonPrimitive?.content.orEmpty(),
            output = this["output"]?.jsonPrimitive?.content.orEmpty(),
            createdAt = (this["created_at"] as? JsonPrimitive)?.content
        )
    }

    private companion object {
        const val MAX_POLLS = 6
        const val POLL_INTERVAL_MS = 1_500L
    }
}
