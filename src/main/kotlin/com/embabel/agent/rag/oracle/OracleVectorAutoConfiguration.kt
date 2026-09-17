package com.embabel.agent.rag.oracle

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.JdbcClientAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.simple.JdbcClient

@AutoConfiguration(after = [DataSourceAutoConfiguration::class, JdbcClientAutoConfiguration::class])
@EnableConfigurationProperties(OracleVectorStoreProperties::class)
class OracleVectorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun oracleVectorStore(
        jdbcClient: JdbcClient,
        properties: OracleVectorStoreProperties,
        objectMapperProvider: ObjectProvider<ObjectMapper>
    ): OracleVectorStore {
        val store = OracleVectorStore(
            jdbcClient = jdbcClient,
            properties = properties,
            objectMapper = objectMapperProvider.getIfAvailable { ObjectMapper() }
        )
        store.provision()
        return store
    }
}
