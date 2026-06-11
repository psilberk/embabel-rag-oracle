package com.embabel.agent.rag.oracle

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.simple.JdbcClient

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

    fun provision() {
        jdbcClient.sql(SqlResourceLoader.load("ddl/create-table", properties)).update()
        jdbcClient.sql(SqlResourceLoader.load("ddl/create-vector-index", properties)).update()
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

    private fun asVectorLiteral(vector: FloatArray): String =
        vector.joinToString(separator = ",", prefix = "[", postfix = "]")

    private fun parseMetadata(metadata: String?): Map<String, Any?> {
        if (metadata.isNullOrBlank()) {
            return emptyMap()
        }
        return objectMapper.readValue(metadata, object : TypeReference<Map<String, Any?>>() {})
    }
}
