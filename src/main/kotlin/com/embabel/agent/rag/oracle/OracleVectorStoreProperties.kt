package com.embabel.agent.rag.oracle

import com.embabel.common.ai.model.EmbeddingService
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "embabel.rag.oracle")
class OracleVectorStoreProperties {
    var name: String = "oracle-store"
    var schemaName: String = ""
    var contentElementTable: String = "content_elements"
    var embeddingDimension: Int = 1536
    var similarityThresholdCeiling: Double = 0.5
    var initializeSchema: Boolean = true
    var createVectorIndex: Boolean = true

    val qualifiedTableName: String
        get() = if (schemaName.isBlank()) contentElementTable else "$schemaName.$contentElementTable"

    internal fun resolved(embeddingService: EmbeddingService?): OracleVectorStoreProperties =
        OracleVectorStoreProperties().also {
            it.name = name
            it.schemaName = schemaName
            it.contentElementTable = contentElementTable
            it.embeddingDimension = embeddingService?.dimensions ?: embeddingDimension
            it.similarityThresholdCeiling = similarityThresholdCeiling
            it.initializeSchema = initializeSchema
            it.createVectorIndex = createVectorIndex
            it.validate()
        }

    internal fun validate() {
        require(name.isNotBlank()) { "name must not be blank" }
        require(embeddingDimension > 0) { "embeddingDimension must be positive" }
        require(similarityThresholdCeiling in 0.0..1.0) {
            "similarityThresholdCeiling must be in [0,1]"
        }
        require(schemaName.isBlank() || ORACLE_IDENTIFIER.matches(schemaName)) {
            "schemaName must be a valid unquoted Oracle identifier"
        }
        require(ORACLE_IDENTIFIER.matches(contentElementTable)) {
            "contentElementTable must be a valid unquoted Oracle identifier"
        }
    }

    private companion object {
        val ORACLE_IDENTIFIER = Regex("""[A-Za-z][A-Za-z0-9_${'$'}#]*""")
    }
}
