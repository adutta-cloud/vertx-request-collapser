package com.griddynamics.producer.repository;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

public class DataRepository {
    private final JDBCPool dbPool;

    public DataRepository(JDBCPool dbPool) {
        this.dbPool = dbPool;
    }

    public Future<Void> initTable() {
        String schemaSql = """
            CREATE TABLE IF NOT EXISTS data_storage (
                id INT AUTO_INCREMENT PRIMARY KEY,
                data LONGBLOB
            )
            """;

        return dbPool.query(schemaSql)
                .execute()
                .mapEmpty(); // We don't care about the result, just success/fail
    }

    public Future<Long> save(Buffer data) {
        String sql = "INSERT INTO data_storage (data) VALUES (?)";

        return dbPool.preparedQuery(sql)
                .execute(Tuple.of(data))
                .map(rows -> rows.property(JDBCPool.GENERATED_KEYS).getLong(0));
    }

    public Future<JsonArray> findAll() {
        // WARNING: SELECT * on a table with BLOBs is dangerous in production!
        String sql = "SELECT id, data FROM data_storage";

        return dbPool.query(sql)
                .execute()
                .map(rows -> {
                    JsonArray result = new JsonArray();

                    for (Row row : rows) {
                        JsonObject doc = new JsonObject();
                        doc.put("id", row.getLong("id"));

                        if (row.getBuffer("data") != null) {
                            doc.put("content", row.getBuffer("data").toString());
                        }

                        result.add(doc);
                    }
                    return result;
                });
    }
}
