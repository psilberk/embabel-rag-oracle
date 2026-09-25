package com.embabel.agent.rag.oracle

import com.embabel.agent.rag.model.Chunk
import com.embabel.agent.rag.model.Retrievable
import com.embabel.agent.rag.service.VectorSearch
import com.embabel.common.ai.model.EmbeddingService
import com.embabel.common.core.types.SimilarityResult
import com.embabel.common.core.types.TextSimilaritySearchRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.simple.JdbcClient
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.sql.SQLException

class OracleVectorStore(
    private val jdbcClient: JdbcClient,
    private val properties: OracleVectorStoreProperties,
    private val objectMapper: ObjectMapper = ObjectMapper(),
    private val embeddingService: EmbeddingService? = null,
) : VectorSearch {
    private val logger = LoggerFactory.getLogger(OracleVectorStore::class.java)

    init {
        properties.validate()
    }

    override fun supportsType(type: String): Boolean = type == Chunk::class.java.simpleName

    override fun <T : Retrievable> vectorSearch(
        request: TextSimilaritySearchRequest,
        clazz: Class<T>,
    ): List<SimilarityResult<T>> {
        require(clazz == Chunk::class.java) {
            "OracleVectorStore only supports ${Chunk::class.java.name}, got ${clazz.name}"
        }

        val queryEmbedding = embeddingService?.embed(request.query)
            ?: throw IllegalStateException("EmbeddingService required for vector search")

        val effectiveThreshold = minOf(request.similarityThreshold, properties.similarityThresholdCeiling)

        val results = jdbcClient.sql(SqlResourceLoader.load("queries/vector-search", properties))
            .param("embedding", asVectorLiteral(queryEmbedding))
            .param("topK", request.topK)
            .query { rs, _ ->
                val id = rs.getString("id")
                val uri = rs.getString("uri")
                val text = rs.getString("text")
                val storedMetadata = parseMetadata(rs.getString("metadata"))
                val metadata = if (uri == null) storedMetadata else storedMetadata + ("url" to uri)
                val chunk = Chunk.create(
                    text = text,
                    parentId = metadata["parent_id"]?.toString() ?: id,
                    metadata = metadata,
                    id = id,
                    urtext = text,
                )
                SimilarityResult(chunk, rs.getDouble("score"))
            }
            .list()
            .filter { it.score >= effectiveThreshold }

        @Suppress("UNCHECKED_CAST")
        return results as List<SimilarityResult<T>>
    }

    fun provision() {
        if (!properties.initializeSchema) {
            logger.info("Schema initialization is disabled for store '{}'", properties.name)
            return
        }

        runDdl("create table", ignorableErrorCodes = setOf(955)) {
            jdbcClient.sql(SqlResourceLoader.load("ddl/create-table", properties)).update()
        }

        if (!properties.createVectorIndex) {
            logger.info("Vector index creation is disabled for store '{}'", properties.name)
            return
        }

        runDdl("create vector index", ignorableErrorCodes = setOf(955, 51962)) {
            jdbcClient.sql(SqlResourceLoader.load("ddl/create-vector-index", properties)).update()
        }
    }

    private fun runDdl(step: String, ignorableErrorCodes: Set<Int>, ddl: () -> Unit) {
        try {
            ddl()
        } catch (ex: DataAccessException) {
            val sqlException = findSqlException(ex)
            if (sqlException != null && sqlException.errorCode in ignorableErrorCodes) {
                logger.warn(
                    "Ignoring Oracle error {} during {} for store '{}': {}",
                    sqlException.errorCode,
                    step,
                    properties.name,
                    sqlException.message
                )
                return
            }
            throw ex
        }
    }

    private fun findSqlException(ex: Throwable): SQLException? {
        var current: Throwable? = ex
        while (current != null) {
            if (current is SQLException) {
                return current
            }
            current = current.cause
        }
        return null
    }

    private fun asVectorLiteral(vector: FloatArray): String =
        vector.joinToString(separator = ",", prefix = "[", postfix = "]")

    private fun parseMetadata(metadata: String?): Map<String, Any?> {
        if (metadata.isNullOrBlank()) {
            return emptyMap()
        }
        return objectMapper.readValue(metadata, object : TypeReference<Map<String, Any?>>() {})
    }
}
