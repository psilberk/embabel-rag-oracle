package com.embabel.agent.rag.oracle

import com.embabel.common.ai.model.EmbeddingService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.simple.JdbcClient
import tools.jackson.databind.ObjectMapper

@AutoConfiguration(
    afterName = ["org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration"]
)
@EnableConfigurationProperties(OracleVectorStoreProperties::class)
class OracleVectorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JdbcClient::class)
    fun oracleVectorStore(
        jdbcClient: JdbcClient,
        properties: OracleVectorStoreProperties,
        objectMapperProvider: ObjectProvider<ObjectMapper>,
        embeddingServiceProvider: ObjectProvider<EmbeddingService>,
    ): OracleVectorStore {
        val embeddingService = embeddingServiceProvider.getIfAvailable()
        val store = OracleVectorStore(
            jdbcClient = jdbcClient,
            properties = properties.resolved(embeddingService),
            objectMapper = objectMapperProvider.getIfAvailable { ObjectMapper() },
            embeddingService = embeddingService,
        )
        store.provision()
        return store
    }
}
