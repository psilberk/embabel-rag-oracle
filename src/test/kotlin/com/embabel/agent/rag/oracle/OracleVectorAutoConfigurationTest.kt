package com.embabel.agent.rag.oracle

import com.embabel.common.ai.model.EmbeddingService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.function.Supplier

class OracleVectorAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(OracleVectorAutoConfiguration::class.java))

    @Test
    fun `runs after Spring Boot 4 JdbcClient auto-configuration`() {
        val annotation = OracleVectorAutoConfiguration::class.java.getAnnotation(AutoConfiguration::class.java)

        assertThat(annotation.afterName).containsExactly(
            "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration"
        )
    }

    @Test
    fun `creates and configures store when JdbcClient is available`() {
        val jdbcClient = mock(JdbcClient::class.java)
        val statement = mock(JdbcClient.StatementSpec::class.java)
        `when`(jdbcClient.sql(anyString())).thenReturn(statement)
        `when`(statement.update()).thenReturn(0)

        contextRunner
            .withBean(JdbcClient::class.java, Supplier { jdbcClient })
            .withPropertyValues(
                "embabel.rag.oracle.name=test-store",
                "embabel.rag.oracle.schema-name=SCOTT",
                "embabel.rag.oracle.content-element-table=TEST_ELEMENTS",
                "embabel.rag.oracle.embedding-dimension=384",
            )
            .run { context ->
                assertThat(context).hasSingleBean(OracleVectorStore::class.java)
                val properties = context.getBean(OracleVectorStoreProperties::class.java)
                assertThat(properties.name).isEqualTo("test-store")
                assertThat(properties.qualifiedTableName).isEqualTo("SCOTT.TEST_ELEMENTS")
                assertThat(properties.embeddingDimension).isEqualTo(384)
                verify(jdbcClient).sql(org.mockito.ArgumentMatchers.contains("VECTOR(384, FLOAT32)"))
            }
    }

    @Test
    fun `infers table vector dimensions from embedding service`() {
        val jdbcClient = mock(JdbcClient::class.java)
        val statement = mock(JdbcClient.StatementSpec::class.java)
        val embeddingService = mock(EmbeddingService::class.java)
        `when`(jdbcClient.sql(anyString())).thenReturn(statement)
        `when`(statement.update()).thenReturn(0)
        `when`(embeddingService.dimensions).thenReturn(3)

        contextRunner
            .withBean(JdbcClient::class.java, Supplier { jdbcClient })
            .withBean(EmbeddingService::class.java, Supplier { embeddingService })
            .run { context ->
                assertThat(context).hasSingleBean(OracleVectorStore::class.java)
                verify(jdbcClient).sql(org.mockito.ArgumentMatchers.contains("VECTOR(3, FLOAT32)"))
            }
    }

    @Test
    fun `does not create store without JdbcClient`() {
        contextRunner.run { context ->
            assertThat(context).doesNotHaveBean(OracleVectorStore::class.java)
        }
    }
}
