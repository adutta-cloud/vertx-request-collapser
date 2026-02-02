package com.griddynamics.consumer.models;

import com.griddynamics.consumer.cache.SharedDataManager;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Node {

    private final String nodeId;
    private final WebClient webClient;
    private final Vertx vertx;
    private static final Logger logger = LoggerFactory.getLogger(Node.class);

    public Node(Vertx vertx, String nodeId) {
        this.vertx = vertx;
        this.nodeId = nodeId;
        this.webClient = WebClient.create(vertx);
    }

    public Future<Boolean> checkData(String searchKey) {
        logger.debug("[{}] Checking cache for key: {}", nodeId, searchKey);

        if (SharedDataManager.containsKey(vertx, searchKey)) {
            logger.debug("[{}] Cache HIT for key: {}", nodeId, searchKey);
            return Future.succeededFuture(true);
        }

        logger.debug("[{}] Cache MISS for key: {}", nodeId, searchKey);
        return Future.succeededFuture(false);
    }

    public void close() {
        if (webClient != null) {
            webClient.close();
            logger.debug("WebClient closed for node: {}", nodeId);
        }
    }
}
