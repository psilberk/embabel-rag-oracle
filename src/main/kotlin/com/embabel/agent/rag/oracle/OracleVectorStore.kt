package com.embabel.agent.rag.oracle

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.SQLException

class OracleVectorStore(
    private val jdbcClient: JdbcClient,
    val properties: OracleVectorStoreProperties,
    private val objectMapper: ObjectMapper = ObjectMapper()
) {
    data class VectorMatch(
        val id: String,
        val uri: String?,
        val text: String,
        val metadata: Map<String, Any?>,
        val score: Double
    )

    private val logger = LoggerFactory.getLogger(OracleVectorStore::class.java)

    fun provision() {
        runDdl("create table", ignorableErrorCodes = setOf(955)) {
            jdbcClient.sql(SqlResourceLoader.load("ddl/create-table", properties)).update()
        }

        runDdl("create vector index", ignorableErrorCodes = setOf(955, 51962)) {
            jdbcClient.sql(SqlResourceLoader.load("ddl/create-vector-index", properties)).update()
        }
    }

    fun upsertChunk(
        id: String,
        uri: String?,
        text: String,
        embedding: FloatArray,
        metadata: Map<String, Any?> = emptyMap()
    ) {
        jdbcClient.sql(SqlResourceLoader.load("operations/upsert-chunk", properties))
            .param("id", id)
            .param("uri", uri)
            .param("text", text)
            .param("embedding", asVectorLiteral(embedding))
            .param("metadata", objectMapper.writeValueAsString(metadata))
            .update()
    }

    fun vectorSearch(
        queryEmbedding: FloatArray,
        topK: Int = 10,
        similarityThreshold: Double = 0.0
    ): List<VectorMatch> {
        val effectiveThreshold = minOf(similarityThreshold, properties.similarityThresholdCeiling)

        return jdbcClient.sql(SqlResourceLoader.load("queries/vector-search", properties))
            .param("embedding", asVectorLiteral(queryEmbedding))
            .param("topK", topK)
            .query { rs, _ ->
                VectorMatch(
                    id = rs.getString("id"),
                    uri = rs.getString("uri"),
                    text = rs.getString("text"),
                    metadata = parseMetadata(rs.getString("metadata")),
                    score = rs.getDouble("score")
                )
            }
            .list()
            .filter { it.score >= effectiveThreshold }
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
