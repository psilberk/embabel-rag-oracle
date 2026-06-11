CREATE TABLE {{tableName}} (
    id VARCHAR2(128) PRIMARY KEY,
    uri VARCHAR2(1024),
    text CLOB NOT NULL,
    embedding VECTOR({{embeddingDimension}}, FLOAT32),
    metadata CLOB,
    ingestion_timestamp TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL
)
