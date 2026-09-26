package com.embabel.agent.rag.oracle

import com.embabel.agent.rag.model.Chunk
import com.embabel.agent.rag.model.Fact
import com.embabel.common.ai.model.EmbeddingService
import com.embabel.common.core.types.TextSimilaritySearchRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.jdbc.core.simple.JdbcClient
import org.mockito.ArgumentMatchers.contains

class OracleVectorStoreTest {

    @Test
    fun `supports Embabel chunks only`() {
        val store = OracleVectorStore(mock(JdbcClient::class.java), OracleVectorStoreProperties())

        assertThat(store.supportsType("Chunk")).isTrue()
        assertThat(store.supportsType("Document")).isFalse()
    }

    @Test
    fun `text search requires an embedding service`() {
        val store = OracleVectorStore(mock(JdbcClient::class.java), OracleVectorStoreProperties())
        val request = TextSimilaritySearchRequest.create("oracle vectors", 0.2, 5)

        assertThatIllegalStateException()
            .isThrownBy { store.vectorSearch(request, Chunk::class.java) }
            .withMessageContaining("EmbeddingService")
    }

    @Test
    fun `text search rejects unsupported result types before embedding`() {
        val embeddingService = mock(EmbeddingService::class.java)
        val store = OracleVectorStore(
            jdbcClient = mock(JdbcClient::class.java),
            properties = OracleVectorStoreProperties(),
            embeddingService = embeddingService,
        )
        val request = TextSimilaritySearchRequest.create("oracle vectors", 0.2, 5)

        assertThatIllegalArgumentException()
            .isThrownBy { store.vectorSearch(request, Fact::class.java) }
            .withMessageContaining(Chunk::class.java.name)
    }

    @Test
    fun `builder infers vector dimensions from embedding service`() {
        val embeddingService = mock(EmbeddingService::class.java)
        `when`(embeddingService.dimensions).thenReturn(384)
        val jdbcClient = mock(JdbcClient::class.java)
        val statement = mock(JdbcClient.StatementSpec::class.java)
        `when`(jdbcClient.sql(org.mockito.ArgumentMatchers.anyString())).thenReturn(statement)
        `when`(statement.update()).thenReturn(0)

        OracleVectorStoreBuilder.create()
            .withJdbcClient(jdbcClient)
            .withEmbeddingDimension(1536)
            .withEmbeddingService(embeddingService)
            .build()

        verify(jdbcClient).sql(contains("VECTOR(384, FLOAT32)"))
    }

    @Test
    fun `provision does nothing when schema initialization is disabled`() {
        val jdbcClient = mock(JdbcClient::class.java)
        val properties = OracleVectorStoreProperties().apply {
            initializeSchema = false
        }

        OracleVectorStore(jdbcClient, properties).provision()

        verifyNoInteractions(jdbcClient)
    }

    @Test
    fun `provision creates table but not index when index creation is disabled`() {
        val jdbcClient = mock(JdbcClient::class.java)
        val statement = mock(JdbcClient.StatementSpec::class.java)
        val properties = OracleVectorStoreProperties().apply {
            createVectorIndex = false
        }
        `when`(jdbcClient.sql(org.mockito.ArgumentMatchers.anyString())).thenReturn(statement)
        `when`(statement.update()).thenReturn(0)

        OracleVectorStore(jdbcClient, properties).provision()

        verify(jdbcClient).sql(contains("CREATE TABLE"))
        verify(jdbcClient, never()).sql(contains("CREATE VECTOR INDEX"))
    }

    @Test
    fun `store rejects invalid dimensions`() {
        val properties = OracleVectorStoreProperties().apply {
            embeddingDimension = 0
        }

        assertThatIllegalArgumentException()
            .isThrownBy { OracleVectorStore(mock(JdbcClient::class.java), properties) }
            .withMessageContaining("embeddingDimension")
    }

    @Test
    fun `store rejects unsafe table identifiers`() {
        val properties = OracleVectorStoreProperties().apply {
            contentElementTable = "CONTENT_ELEMENTS; DROP TABLE USERS"
        }

        assertThatIllegalArgumentException()
            .isThrownBy { OracleVectorStore(mock(JdbcClient::class.java), properties) }
            .withMessageContaining("contentElementTable")
    }

    @Test
    fun `store rejects unsafe schema identifiers`() {
        val properties = OracleVectorStoreProperties().apply {
            schemaName = "SCOTT.OTHER"
        }

        assertThatIllegalArgumentException()
            .isThrownBy { OracleVectorStore(mock(JdbcClient::class.java), properties) }
            .withMessageContaining("schemaName")
    }

    @Test
    fun `store rejects similarity thresholds outside the unit interval`() {
        val properties = OracleVectorStoreProperties().apply {
            similarityThresholdCeiling = 1.1
        }

        assertThatIllegalArgumentException()
            .isThrownBy { OracleVectorStore(mock(JdbcClient::class.java), properties) }
            .withMessageContaining("similarityThresholdCeiling")
    }
}
