package com.embabel.agent.rag.oracle

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "embabel.rag.oracle")
data class OracleVectorStoreProperties(
    val name: String = "oracle-store",
    val schemaName: String = "",
    val contentElementTable: String = "content_elements",
    val embeddingDimension: Int = 1536,
    val similarityThresholdCeiling: Double = 0.5
) {
    val qualifiedTableName: String
        get() = if (schemaName.isBlank()) contentElementTable else "$schemaName.$contentElementTable"
}
