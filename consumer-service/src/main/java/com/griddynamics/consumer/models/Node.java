package com.griddynamics.consumer.models;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Node {

    private final String nodeId;
    private final WebClient webClient;
    private static final Logger logger = LoggerFactory.getLogger(Node.class);

    public Node(Vertx vertx, String nodeId) {
        this.nodeId = nodeId;
        this.webClient = WebClient.create(vertx);
    }

    public Future<Boolean> checkData(String targetData) {
        logger.info("[{}] Requesting data from Producer...", nodeId);

        return webClient.get(8080, "localhost", "/")
                .send()
                .compose(response -> {
                    if (response.statusCode() != 200) {
                        return Future.failedFuture("Producer returned status: " + response.statusCode());
                    }

                    JsonArray jsonArray = response.bodyAsJsonArray();
                    boolean found = false;

                    for (int i = 0; i < jsonArray.size(); i++) {
                        JsonObject row = jsonArray.getJsonObject(i);
                        if (row.getString("content").equals(targetData)) {
                            found = true;
                            break;
                        }
                    }
                    logger.info("[{}] Search Result for '{}': {}", nodeId, targetData, found ? "FOUND" : "NOT FOUND");

                    return Future.succeededFuture(found);
                });
    }

    public void close() {
        if (webClient != null) {
            webClient.close();
        }
    }
}
