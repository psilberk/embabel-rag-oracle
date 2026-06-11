MERGE INTO {{tableName}} t
USING (
    SELECT
        :id AS id,
        :uri AS uri,
        :text AS text,
        TO_VECTOR(:embedding) AS embedding,
        :metadata AS metadata
    FROM dual
) s
ON (t.id = s.id)
WHEN MATCHED THEN UPDATE SET
    t.uri = s.uri,
    t.text = s.text,
    t.embedding = s.embedding,
    t.metadata = s.metadata,
    t.ingestion_timestamp = SYSTIMESTAMP
WHEN NOT MATCHED THEN
    INSERT (id, uri, text, embedding, metadata, ingestion_timestamp)
    VALUES (s.id, s.uri, s.text, s.embedding, s.metadata, SYSTIMESTAMP)
