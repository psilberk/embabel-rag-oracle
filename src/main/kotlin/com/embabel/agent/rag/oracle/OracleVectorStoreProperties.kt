package com.embabel.agent.rag.oracle

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "embabel.rag.oracle")
class OracleVectorStoreProperties {
    var name: String = "oracle-store"
    var schemaName: String = ""
    var contentElementTable: String = "content_elements"
    var embeddingDimension: Int = 1536
    var similarityThresholdCeiling: Double = 0.5

    val qualifiedTableName: String
        get() = if (schemaName.isBlank()) contentElementTable else "$schemaName.$contentElementTable"
}
