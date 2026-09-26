package com.embabel.agent.rag.oracle

import com.embabel.agent.rag.model.Chunk
import com.embabel.common.ai.model.EmbeddingService
import com.embabel.common.core.types.TextSimilaritySearchRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DriverManagerDataSource

class OracleVectorStoreIT {

    private lateinit var jdbcClient: JdbcClient
    private lateinit var store: OracleVectorStore
    private lateinit var tableName: String

    @BeforeEach
    fun setUp() {
        val url = System.getenv("ORACLE_JDBC_URL")
        val username = System.getenv("ORACLE_USERNAME")
        val password = System.getenv("ORACLE_PASSWORD")
        assumeTrue(
            listOf(url, username, password).all { !it.isNullOrBlank() },
            "Set ORACLE_JDBC_URL, ORACLE_USERNAME, and ORACLE_PASSWORD to run Oracle integration tests"
        )

        tableName = "CE_IT_${System.currentTimeMillis()}"
        jdbcClient = JdbcClient.create(DriverManagerDataSource(url!!, username!!, password!!))
        val embeddingService = mock(EmbeddingService::class.java)
        `when`(embeddingService.dimensions).thenReturn(3)
        `when`(embeddingService.embed("Oracle indexing")).thenReturn(floatArrayOf(1f, 0f, 0f))

        val properties = OracleVectorStoreProperties().apply {
            contentElementTable = tableName
            embeddingDimension = 3
            similarityThresholdCeiling = 0.5
            createVectorIndex = false
        }
        store = OracleVectorStore(jdbcClient, properties, embeddingService = embeddingService)
        store.provision()
        jdbcClient.sql(
            """
            INSERT INTO $tableName (id, uri, text, embedding, metadata)
            VALUES ('doc-1', 'urn:test:oracle', 'Oracle vector index', TO_VECTOR('[1,0,0]'),
                    '{"parent_id":"manual","topic":"oracle"}')
            """.trimIndent()
        ).update()
        jdbcClient.sql(
            """
            INSERT INTO $tableName (id, uri, text, embedding, metadata)
            VALUES ('doc-2', 'urn:test:rag', 'RAG context', TO_VECTOR('[0,1,0]'), '{"topic":"rag"}')
            """.trimIndent()
        ).update()
    }

    @AfterEach
    fun tearDown() {
        if (::jdbcClient.isInitialized && ::tableName.isInitialized) {
            jdbcClient.sql("DROP TABLE $tableName PURGE").update()
        }
    }

    @Test
    fun `maps Oracle rows to chunks and applies similarity threshold`() {
        val request = TextSimilaritySearchRequest.create("Oracle indexing", 0.2, 5)

        val results = store.vectorSearch(request, Chunk::class.java)

        assertThat(results).hasSize(1)
        assertThat(results.single().match.id).isEqualTo("doc-1")
        assertThat(results.single().match.text).isEqualTo("Oracle vector index")
        assertThat(results.single().match.metadata)
            .containsEntry("parent_id", "manual")
            .containsEntry("url", "urn:test:oracle")
        assertThat(results.single().score).isEqualTo(1.0)
    }
}
