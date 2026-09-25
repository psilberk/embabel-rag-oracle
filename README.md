# embabel-rag-oracle

Oracle vector similarity search and Spring Boot auto-configuration for Embabel RAG.
This version targets Java 21 and Embabel Agent 1.5.2 and implements Embabel's
`VectorSearch` contract for `Chunk` results.

## Requirements

- Oracle Database 23ai or newer with the `VECTOR` data type and vector functions
- A database user that can select from the configured table
- `CREATE TABLE` when automatic schema initialization is enabled
- `CREATE INDEX` when automatic vector-index creation is enabled
- An Embabel `EmbeddingService` whose dimensions match the stored vectors

Cross-schema configuration also requires the corresponding object privileges in
the target schema.

## Configuration

```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@//localhost:1521/FREEPDB1
    username: scott
    password: tiger

embabel:
  rag:
    oracle:
      name: oracle-store
      schema-name: scott
      content-element-table: CONTENT_ELEMENTS
      similarity-threshold-ceiling: 0.5
      initialize-schema: true
      create-vector-index: true
```

With a `JdbcClient` in the application context, Spring Boot creates an
`OracleVectorStore`. When an `EmbeddingService` is available, its dimensions are
used for table provisioning. Otherwise `embedding-dimension` defaults to `1536`
and can be configured explicitly.

`initialize-schema: false` disables all DDL. With initialization enabled,
`create-vector-index: false` creates the table without the HNSW index. Exact
vector search still works without the index, but it will become slower as the
table grows.

Existing objects (`ORA-00955`) do not prevent startup. Vector-index creation also
tolerates `ORA-51962`, which means the current container has insufficient vector
memory. Other DDL errors remain startup failures.

Only unquoted Oracle schema and table identifiers are accepted. Vector dimensions
must be positive, and `similarity-threshold-ceiling` must be between `0.0` and
`1.0`.

## Embabel Search

Inject the auto-configured store and submit Embabel's text search request:

```kotlin
val results = store.vectorSearch(
    TextSimilaritySearchRequest.create("How does Oracle vector search work?", 0.5, 5),
    Chunk::class.java,
)
```

The store delegates text embedding to the configured `EmbeddingService`, runs a
cosine vector query in Oracle, maps rows to Embabel `Chunk` objects, and applies
the configured similarity ceiling. Calling `vectorSearch` without an
`EmbeddingService` fails with a clear error.

## Current Scope

This first contribution includes:

- Oracle cosine vector similarity search
- Embabel `VectorSearch` integration for `Chunk`
- Spring Boot auto-configuration
- optional table and HNSW vector-index provisioning

The following pgvector-equivalent capabilities are intentionally deferred:

- lexical search using Oracle Text
- fuzzy text search
- hybrid lexical and vector search
- Embabel ingestion and writable-store interfaces
- metadata and entity filtering

## Build And Test

```bash
export JAVA_HOME=/path/to/jdk-21
mvn clean verify
```

The default test suite uses mocks and does not require Oracle. To run the real
Oracle integration test:

```bash
export ORACLE_JDBC_URL='jdbc:oracle:thin:@//localhost:1521/FREEPDB1'
export ORACLE_USERNAME='scott'
export ORACLE_PASSWORD='tiger'
mvn verify -Poracle-integration
```

The integration test creates a temporary table, inserts three-dimensional
vectors, verifies row-to-`Chunk` mapping and threshold filtering, and drops the
table afterward. It deliberately disables index creation so it also runs on
small local databases without a configured vector memory area.
