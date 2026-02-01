package com.griddynamics.consumer;

import com.griddynamics.consumer.election.LeaderElection;
import com.griddynamics.consumer.hazelcast.HazelcastManager;
import com.griddynamics.consumer.models.Node;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;

public class ConsumerVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(ConsumerVerticle.class);
    private static final int NUM_NODES = 50;

    private List<Node> nodes;
    private String nodeId;
    private WebClient webClient;

    @Override
    public void start(Promise<Void> startPromise) {
        this.nodeId = "Consumer-Node-" + System.nanoTime();
        this.webClient = WebClient.create(vertx);

        // Initialize 50 nodes
        this.nodes = new ArrayList<>();
        for (int i = 0; i < NUM_NODES; i++) {
            nodes.add(new Node(vertx, "Node-" + i));
        }

        startHttpServer()
                .onSuccess(v -> {
                    logger.info("Consumer API started successfully on http://localhost:8081/search");
                    startPromise.complete();
                })
                .onFailure(err -> {
                    logger.error("Failed to start Consumer API", err);
                    startPromise.fail(err);
                });
    }

    private Future<Void> startHttpServer() {
        Router router = Router.router(vertx);
        router.get("/search").handler(this::handleSearch);
        router.get("/health").handler(this::handleHealth);

        return vertx.createHttpServer()
                .requestHandler(router)
                .listen(8081)
                .mapEmpty();
    }

    private void handleHealth(RoutingContext ctx) {
        // Check Producer service connectivity
        webClient.get(8080, "localhost", "/")
                .timeout(2000)
                .send()
                .onComplete(ar -> {
                    JsonObject health = new JsonObject()
                            .put("status", "UP")
                            .put("consumer_service", "healthy")
                            .put("hazelcast_cluster", HazelcastManager.getInstance().getCluster().getMembers().size())
                            .put("timestamp", System.currentTimeMillis());

                    if (ar.succeeded()) {
                        health.put("producer_service", "connected")
                              .put("producer_status", ar.result().statusCode());
                    } else {
                        health.put("producer_service", "disconnected")
                              .put("producer_error", ar.cause().getMessage());
                        logger.warn("Producer service health check failed: {}", ar.cause().getMessage());
                    }

                    ctx.response()
                            .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                            .end(health.encodePrettily());
                });
    }

    private void handleSearch(RoutingContext ctx) {
        long startTime = System.currentTimeMillis();
        String searchKey = ctx.request().getParam("key");

        if (searchKey == null || searchKey.isEmpty()) {
            logger.warn("Search request received without 'key' parameter");
            ctx.fail(400, new Throwable("Missing 'key' query parameter"));
            return;
        }

        logger.info("Search request initiated for key: {}", searchKey);

        // Step 1: Elect leader
        String leader = LeaderElection.electLeader(nodeId);

        if (leader.equals(nodeId)) {
            logger.debug("Current node is leader, fetching data from Producer");
            // Step 2a: Leader fetches from Producer and caches
            fetchFromProducerAndCache(searchKey)
                    .onSuccess(v -> queryAllNodes(ctx, searchKey, startTime))
                    .onFailure(err -> {
                        logger.error("Leader failed to fetch data from Producer for key: {}", searchKey, err);

                        JsonObject errorResponse = new JsonObject()
                                .put("error", "Failed to fetch data from Producer service")
                                .put("message", err.getMessage())
                                .put("searched_for", searchKey)
                                .put("timestamp", System.currentTimeMillis());

                        ctx.response()
                                .setStatusCode(503) // Service Unavailable
                                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                                .end(errorResponse.encodePrettily());
                    });
        } else {
            logger.debug("Current node is follower, waiting for leader to populate cache");
            // Step 2b: Non-leader waits for cache to be populated
            vertx.setTimer(100, id -> queryAllNodes(ctx, searchKey, startTime));
        }
    }

    private Future<Void> fetchFromProducerAndCache(String searchKey) {
        Promise<Void> promise = Promise.promise();

        logger.debug("Fetching data from Producer service for key: {}", searchKey);

        webClient.get(8080, "localhost", "/")
                .addQueryParam("key", searchKey)
                .timeout(5000) // 5 second timeout
                .send()
                .onSuccess(response -> {
                    if (response.statusCode() == 200) {
                        JsonArray data = response.bodyAsJsonArray();
                        var cache = HazelcastManager.getDataCache();

                        // Cache each data item
                        for (int i = 0; i < data.size(); i++) {
                            JsonObject item = data.getJsonObject(i);
                            cache.put(searchKey, item.encodePrettily());
                        }

                        logger.info("Successfully cached {} items for key: {}", data.size(), searchKey);
                        promise.complete();
                    } else if (response.statusCode() == 404) {
                        String errorMsg = "Producer service endpoint not found. Please verify:\n" +
                                "  1. Producer service is running on port 8080\n" +
                                "  2. Endpoint /data exists\n" +
                                "  3. Producer service is accessible";
                        logger.error(errorMsg);
                        promise.fail(errorMsg);
                    } else {
                        String errorMsg = "Producer service returned non-200 status: " + response.statusCode();
                        logger.error(errorMsg);
                        promise.fail(errorMsg);
                    }
                })
                .onFailure(err -> {
                    logger.error("Failed to connect to Producer service at http://localhost:8080/data - {}",
                            err.getMessage(), err);
                    promise.fail("Producer service unavailable: " + err.getMessage());
                });

        return promise.future();
    }

    private void queryAllNodes(RoutingContext ctx, String searchKey, long startTime) {
        List<Future<Boolean>> futures = new ArrayList<>();
        for (Node node : nodes) {
            futures.add(node.checkData(searchKey));
        }

        Future.all(futures)
                .onSuccess(v -> {
                    int foundCount = (int) futures.stream()
                            .filter(f -> f.result() != null && f.result())
                            .count();

                    long duration = System.currentTimeMillis() - startTime;

                    JsonObject response = new JsonObject()
                            .put("searched_for", searchKey)
                            .put("total_nodes", NUM_NODES)
                            .put("nodes_found", foundCount)
                            .put("response_time_ms", duration)
                            .put("timestamp", System.currentTimeMillis());

                    ctx.response()
                            .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                            .end(response.encodePrettily());

                    logger.info("Search completed for key: {} | Duration: {}ms | Found in {}/{} nodes",
                            searchKey, duration, foundCount, NUM_NODES);
                })
                .onFailure(err -> {
                    logger.error("Failed to query nodes for key: {}", searchKey, err);
                    ctx.fail(500, err);
                });
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        logger.info("Shutting down Consumer Verticle...");

        for (Node node : nodes) {
            node.close();
        }
        if (webClient != null) {
            webClient.close();
        }
        HazelcastManager.shutdown();

        logger.info("Consumer Verticle stopped successfully");
        stopPromise.complete();
    }
}
