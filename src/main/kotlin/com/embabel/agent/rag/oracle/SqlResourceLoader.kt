package com.embabel.agent.rag.oracle

object SqlResourceLoader {

    fun load(path: String, properties: OracleVectorStoreProperties): String {
        val fullPath = "/sql/$path.sql"
        val template = SqlResourceLoader::class.java.getResourceAsStream(fullPath)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw IllegalArgumentException("SQL resource not found: $fullPath")

        return template
            .replace("{{tableName}}", properties.qualifiedTableName)
            .replace("{{embeddingDimension}}", properties.embeddingDimension.toString())
    }
}
