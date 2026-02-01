package com.griddynamics.producer.config;

import io.vertx.core.json.JsonObject;

public class DbConfig {

    public static JsonObject getMySQLConfig() {
        return new JsonObject()
                .put("url", "jdbc:mysql://localhost:3306/producer_db?useInformationSchema=false&generateSimpleParameterMetadata=true")
                .put("driver_class", "com.mysql.cj.jdbc.Driver")
                .put("user", "root")
                .put("password", "anus@7986") // Update with your password
                .put("max_pool_size", 5);
    }
}
