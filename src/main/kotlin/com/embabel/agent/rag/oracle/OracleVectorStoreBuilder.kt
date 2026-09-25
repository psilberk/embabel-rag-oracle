package com.embabel.agent.rag.oracle

import com.embabel.common.ai.model.EmbeddingService
import org.springframework.jdbc.core.simple.JdbcClient
import tools.jackson.databind.ObjectMapper
import javax.sql.DataSource

data class OracleVectorStoreBuilder(
    private val name: String = "oracle-store",
    private val jdbcClient: JdbcClient? = null,
    private val dataSource: DataSource? = null,
    private val embeddingService: EmbeddingService? = null,
    private val schemaName: String = "",
    private val contentElementTable: String = "content_elements",
    private val embeddingDimension: Int = 1536,
    private val similarityThresholdCeiling: Double = 0.5,
    private val initializeSchema: Boolean = true,
    private val createVectorIndex: Boolean = true,
    private val objectMapper: ObjectMapper = ObjectMapper()
) {
    fun withName(name: String): OracleVectorStoreBuilder = copy(name = name)

    fun withJdbcClient(jdbcClient: JdbcClient): OracleVectorStoreBuilder = copy(jdbcClient = jdbcClient)

    fun withDataSource(dataSource: DataSource): OracleVectorStoreBuilder = copy(dataSource = dataSource)

    fun withEmbeddingService(embeddingService: EmbeddingService): OracleVectorStoreBuilder =
        copy(embeddingService = embeddingService)

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

    fun withInitializeSchema(initializeSchema: Boolean): OracleVectorStoreBuilder =
        copy(initializeSchema = initializeSchema)

    fun withCreateVectorIndex(createVectorIndex: Boolean): OracleVectorStoreBuilder =
        copy(createVectorIndex = createVectorIndex)

    fun build(): OracleVectorStore {
        val resolvedJdbcClient = jdbcClient
            ?: dataSource?.let { JdbcClient.create(it) }
            ?: throw IllegalStateException("Either jdbcClient or dataSource must be provided")

        val properties = createProperties()
        val store = OracleVectorStore(
            jdbcClient = resolvedJdbcClient,
            properties = properties,
            objectMapper = objectMapper,
            embeddingService = embeddingService,
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
            properties = createProperties(),
            objectMapper = objectMapper,
            embeddingService = embeddingService,
        )
    }

    private fun createProperties(): OracleVectorStoreProperties {
        return OracleVectorStoreProperties().apply {
            this.name = this@OracleVectorStoreBuilder.name
            this.schemaName = this@OracleVectorStoreBuilder.schemaName
            this.contentElementTable = this@OracleVectorStoreBuilder.contentElementTable
            this.embeddingDimension = embeddingService?.dimensions
                ?: this@OracleVectorStoreBuilder.embeddingDimension
            this.similarityThresholdCeiling = this@OracleVectorStoreBuilder.similarityThresholdCeiling
            this.initializeSchema = this@OracleVectorStoreBuilder.initializeSchema
            this.createVectorIndex = this@OracleVectorStoreBuilder.createVectorIndex
        }.also { it.validate() }
    }

    companion object {
        @JvmStatic
        fun create(): OracleVectorStoreBuilder = OracleVectorStoreBuilder()
    }
}
