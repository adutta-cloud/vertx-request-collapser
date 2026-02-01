package com.griddynamics.consumer;

import com.griddynamics.consumer.models.Node;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.ArrayList;
import java.util.List;

public class ConsumerVerticle extends AbstractVerticle {
    private List<Node> nodes;
    private static final int NUM_NODES = 50;

    @Override
    public void start(Promise<Void> startPromise) {
        // 1. Initialize the Node Logic
        // We use a random ID or a fixed one for the server
        this.nodes = new ArrayList<>();
        for (int i = 0; i < NUM_NODES; i++) {
            nodes.add(new Node(vertx, "Consumer-Node-" + i));
        }

        // 2. Start HTTP Server (Port 8081)
        startHttpServer()
                .onSuccess(v -> {
                    System.out.println("Consumer API ready at http://localhost:8081/search");
                    startPromise.complete();
                })
                .onFailure(startPromise::fail);
    }

    private io.vertx.core.Future<Void> startHttpServer() {
        Router router = Router.router(vertx);

        // Define the Search Endpoint
        router.get("/search").handler(this::handleSearch);

        return vertx.createHttpServer()
                .requestHandler(router)
                .listen(8081) // <--- Different Port than Producer!
                .mapEmpty();
    }

    private void handleSearch(RoutingContext ctx) {
        String searchKey = ctx.request().getParam("key");

        if (searchKey == null || searchKey.isEmpty()) {
            ctx.fail(400, new Throwable("Missing 'key' query parameter"));
            return;
        }

        System.out.println("Received search request for: " + searchKey);

        List<Future<Boolean>> futures = new ArrayList<>();
        for (Node node : nodes) {
            futures.add(node.checkData(searchKey));
        }

        Future.all(futures)
                .onSuccess(v -> {
                    // Count how many nodes found the data
                    int foundCount = 0;
                    for (int i = 0; i < futures.size(); i++) {
                        if (futures.get(i).result()) {
                            foundCount++;
                        }
                    }

                    JsonObject response = new JsonObject()
                            .put("searched_for", searchKey)
                            .put("total_nodes", NUM_NODES)
                            .put("nodes_found", foundCount)
                            .put("timestamp", System.currentTimeMillis());

                    ctx.response()
                            .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                            .end(response.encodePrettily());
                })
                .onFailure(err -> {
                    ctx.fail(500, err);
                });
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        for (Node node : nodes) {
            node.close();
        }
        stopPromise.complete();
    }
}
