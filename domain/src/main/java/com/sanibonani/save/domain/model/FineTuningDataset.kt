package com.sanibonani.save.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a single training example for Supervised Fine-Tuning (SFT).
 * Compatible with Azure AI Foundry JSONL format.
 */
@Serializable
data class SftTrainingExample(
    val messages: List<SftMessage>
)

@Serializable
data class SftMessage(
    val role: String,
    val content: String
)

/**
 * Represents a single training example for Direct Preference Optimization (DPO).
 */
@Serializable
data class DpoTrainingExample(
    val prompt: String,
    val chosen: String,
    val rejected: String
)

/**
 * Helper for exporting behavior data to ML formats.
 */
@Serializable
data class behaviorMLFeatureSet(
    @SerialName("member_id") val memberId: String,
    @SerialName("behavior_score") val behaviorScore: Double,
    @SerialName("fraud_score") val fraudScore: Double,
    @SerialName("risk_level") val riskLevel: String,
    @SerialName("metrics") val metrics: Map<String, Double>
)
