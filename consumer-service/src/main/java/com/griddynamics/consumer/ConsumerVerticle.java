package com.griddynamics.consumer;

import com.griddynamics.consumer.cache.SharedDataManager;
import com.griddynamics.consumer.election.LeaderElection;
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

public class ConsumerVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(ConsumerVerticle.class);

    private String nodeId;
    private WebClient webClient;

    @Override
    public void start(Promise<Void> startPromise) {
        this.nodeId = "Consumer-Node-" + System.nanoTime();
        this.webClient = WebClient.create(vertx);

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
                            .put("node_id", nodeId)
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
            ctx.fail(400, new Throwable("Missing 'key' parameter"));
            return;
        }

        logger.info("Search request initiated for key: {}", searchKey);

        // Check cache first
        String cachedData = SharedDataManager.getCachedData(vertx, searchKey);
        if (cachedData != null) {
            logger.debug("Cache HIT for key: {}", searchKey);
            respondWithCacheResults(ctx, searchKey, cachedData, startTime);
            return;
        }

        logger.debug("Cache MISS for key: {}", searchKey);

        // Cache miss - elect leader to fetch data
        String leader = LeaderElection.electLeader(vertx, nodeId);

        if (leader.equals(nodeId)) {
            logger.debug("Current node is leader, fetching data from Producer");
            fetchFromProducerAndCache(searchKey)
                    .onSuccess(v -> {
                        String data = SharedDataManager.getCachedData(vertx, searchKey);
                        respondWithCacheResults(ctx, searchKey, data, startTime);
                    })
                    .onFailure(err -> {
                        logger.error("Leader failed to fetch data from Producer for key: {}", searchKey, err);

                        JsonObject errorResponse = new JsonObject()
                                .put("error", "Failed to fetch data from Producer service")
                                .put("message", err.getMessage())
                                .put("searched_for", searchKey)
                                .put("timestamp", System.currentTimeMillis());

                        ctx.response()
                                .setStatusCode(503)
                                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                                .end(errorResponse.encodePrettily());
                    });
        } else {
            logger.debug("Current node is follower, waiting for leader to populate cache");
            vertx.setTimer(50, id -> {
                String data = SharedDataManager.getCachedData(vertx, searchKey);
                if (data != null) {
                    respondWithCacheResults(ctx, searchKey, data, startTime);
                } else {
                    logger.warn("Cache still empty after wait for key: {}", searchKey);
                    JsonObject errorResponse = new JsonObject()
                            .put("error", "Data not available yet")
                            .put("message", "Leader is still fetching data, please retry")
                            .put("searched_for", searchKey)
                            .put("timestamp", System.currentTimeMillis());

                    ctx.response()
                            .setStatusCode(503)
                            .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                            .end(errorResponse.encodePrettily());
                }
            });
        }
    }

    private Future<Void> fetchFromProducerAndCache(String searchKey) {
        Promise<Void> promise = Promise.promise();

        logger.debug("Fetching data from Producer service for key: {}", searchKey);

        webClient.get(8080, "localhost", "/")
                .timeout(5000)
                .send()
                .onSuccess(response -> {
                    if (response.statusCode() == 200) {
                        JsonArray data = response.bodyAsJsonArray();

                        // Cache the entire result as a single entry
                        SharedDataManager.cacheData(vertx, searchKey, data.encode());

                        logger.info("Successfully cached {} items for key: {}", data.size(), searchKey);
                        promise.complete();
                    } else if (response.statusCode() == 404) {
                        String errorMsg = "Producer service endpoint not found. Please verify Producer is running on port 8080";
                        logger.error(errorMsg);
                        promise.fail(errorMsg);
                    } else {
                        String errorMsg = "Producer service returned non-200 status: " + response.statusCode();
                        logger.error(errorMsg);
                        promise.fail(errorMsg);
                    }
                })
                .onFailure(err -> {
                    logger.error("Failed to connect to Producer service at http://localhost:8080 - {}",
                            err.getMessage(), err);
                    promise.fail("Producer service unavailable: " + err.getMessage());
                });

        return promise.future();
    }

    private void respondWithCacheResults(RoutingContext ctx, String searchKey, String cachedData, long startTime) {
        long duration = System.currentTimeMillis() - startTime;

        JsonObject response = new JsonObject()
                .put("searched_for", searchKey)
                .put("found", cachedData != null)
                .put("response_time_ms", duration)
                .put("timestamp", System.currentTimeMillis());

        if (cachedData != null) {
            response.put("data", new JsonArray(cachedData));
        }

        ctx.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .end(response.encodePrettily());

        logger.info("Search completed for key: {} | Duration: {}ms | Found: {}",
                searchKey, duration, cachedData != null);
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        logger.info("Shutting down Consumer Verticle...");

        if (webClient != null) {
            webClient.close();
        }

        logger.info("Consumer Verticle stopped successfully");
        stopPromise.complete();
    }
}
