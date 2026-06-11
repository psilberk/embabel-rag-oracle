SELECT *
FROM (
    SELECT
        id,
        uri,
        text,
        metadata,
        1 - VECTOR_DISTANCE(embedding, TO_VECTOR(:embedding), COSINE) AS score
    FROM {{tableName}}
    WHERE embedding IS NOT NULL
    ORDER BY VECTOR_DISTANCE(embedding, TO_VECTOR(:embedding), COSINE)
)
WHERE ROWNUM <= :topK
