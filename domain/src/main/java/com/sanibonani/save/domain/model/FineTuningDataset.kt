package com.sanibonani.save.domain.model

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
