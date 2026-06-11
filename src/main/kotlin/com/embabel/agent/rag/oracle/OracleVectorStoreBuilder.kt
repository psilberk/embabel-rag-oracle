package com.embabel.agent.rag.oracle

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.simple.JdbcClient
import javax.sql.DataSource

data class OracleVectorStoreBuilder(
    private val name: String = "oracle-store",
    private val jdbcClient: JdbcClient? = null,
    private val dataSource: DataSource? = null,
    private val schemaName: String = "",
    private val contentElementTable: String = "content_elements",
    private val embeddingDimension: Int = 1536,
    private val similarityThresholdCeiling: Double = 0.5,
    private val objectMapper: ObjectMapper = ObjectMapper()
) {
    fun withName(name: String): OracleVectorStoreBuilder = copy(name = name)

    fun withJdbcClient(jdbcClient: JdbcClient): OracleVectorStoreBuilder = copy(jdbcClient = jdbcClient)

    fun withDataSource(dataSource: DataSource): OracleVectorStoreBuilder = copy(dataSource = dataSource)

    fun withSchemaName(schemaName: String): OracleVectorStoreBuilder = copy(schemaName = schemaName)

    fun withContentElementTable(contentElementTable: String): OracleVectorStoreBuilder =
        copy(contentElementTable = contentElementTable)

    fun withEmbeddingDimension(embeddingDimension: Int): OracleVectorStoreBuilder {
        require(embeddingDimension > 0) { "embeddingDimension must be positive" }
        return copy(embeddingDimension = embeddingDimension)
    }

    fun withSimilarityThresholdCeiling(similarityThresholdCeiling: Double): OracleVectorStoreBuilder {
        require(similarityThresholdCeiling in 0.0..1.0) { "similarityThresholdCeiling must be in [0,1]" }
        return copy(similarityThresholdCeiling = similarityThresholdCeiling)
    }

    fun build(): OracleVectorStore {
        val resolvedJdbcClient = jdbcClient
            ?: dataSource?.let { JdbcClient.create(it) }
            ?: throw IllegalStateException("Either jdbcClient or dataSource must be provided")

        val store = OracleVectorStore(
            jdbcClient = resolvedJdbcClient,
            properties = OracleVectorStoreProperties(
                name = name,
                schemaName = schemaName,
                contentElementTable = contentElementTable,
                embeddingDimension = embeddingDimension,
                similarityThresholdCeiling = similarityThresholdCeiling
            ),
            objectMapper = objectMapper
        )
        store.provision()
        return store
    }

    fun buildWithoutProvision(): OracleVectorStore {
        val resolvedJdbcClient = jdbcClient
            ?: dataSource?.let { JdbcClient.create(it) }
            ?: throw IllegalStateException("Either jdbcClient or dataSource must be provided")

        return OracleVectorStore(
            jdbcClient = resolvedJdbcClient,
            properties = OracleVectorStoreProperties(
                name = name,
                schemaName = schemaName,
                contentElementTable = contentElementTable,
                embeddingDimension = embeddingDimension,
                similarityThresholdCeiling = similarityThresholdCeiling
            ),
            objectMapper = objectMapper
        )
    }

    companion object {
        @JvmStatic
        fun create(): OracleVectorStoreBuilder = OracleVectorStoreBuilder()
    }
}
