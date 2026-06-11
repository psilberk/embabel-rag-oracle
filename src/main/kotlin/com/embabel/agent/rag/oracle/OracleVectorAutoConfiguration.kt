package com.embabel.agent.rag.oracle

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.simple.JdbcClient
import javax.sql.DataSource

@AutoConfiguration
@EnableConfigurationProperties(OracleVectorStoreProperties::class)
class OracleVectorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(JdbcClient::class)
    @ConditionalOnBean(DataSource::class)
    fun jdbcClient(dataSource: DataSource): JdbcClient = JdbcClient.create(dataSource)

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JdbcClient::class)
    fun oracleVectorStore(
        jdbcClient: JdbcClient,
        properties: OracleVectorStoreProperties,
        objectMapper: ObjectMapper?
    ): OracleVectorStore {
        val store = OracleVectorStore(
            jdbcClient = jdbcClient,
            properties = properties,
            objectMapper = objectMapper ?: ObjectMapper()
        )
        store.provision()
        return store
    }
}
