package com.griddynamics.producer;

import com.griddynamics.producer.config.DbConfig;
import com.griddynamics.producer.repository.DataRepository;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.jdbcclient.JDBCPool;

public class ProducerVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(ProducerVerticle.class);
    private DataRepository repository;

    @Override
    public void start(Promise<Void> startPromise) {
        JDBCPool dbPool = JDBCPool.pool(vertx, DbConfig.getMySQLConfig());
        this.repository = new DataRepository(dbPool);

        repository.initTable()
                .compose(v -> {
                    // This block only runs if table creation succeeded
                    log.info("Table initialization successful.");
                    return startHttpServer();
                })
                .onSuccess(v -> {
                    log.info("Application is Ready!");
                    startPromise.complete();
                })
                .onFailure(err -> {
                    log.error("Fatal Error during startup", err);
                    startPromise.fail(err);
                });
    }

    private Future<Void> startHttpServer() {
        // Get the router configuration
        Router router = defineRoutes();

        // Start the server
        return vertx.createHttpServer()
                .requestHandler(router)
                .listen(8080)
                .mapEmpty();
    }

    private Router defineRoutes() {
        Router router = Router.router(vertx);

        // Enable reading the Body (JSON) for POST requests
        router.route().handler(BodyHandler.create());

        // Map URLs to specific Java methods
        router.post("/").handler(this::handleStoreDocument);
        router.get("/").handler(this::handleGetAllDocuments);

        return router;
    }

    private void handleStoreDocument(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            ctx.fail(400, new Throwable("Body must be JSON"));
            return;
        }

        String content = body.getString("content", "");

        repository.save(Buffer.buffer(content))
                .onSuccess(id -> {
                    ctx.response()
                            .setStatusCode(201)
                            .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                            .end(new JsonObject().put("id", id).put("status", "Created").encode());
                })
                .onFailure(ctx::fail);
    }

    private void handleGetAllDocuments(RoutingContext ctx) {
        repository.findAll()
                .onSuccess(json -> {
                    ctx.response()
                            .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                            .end(json.encodePrettily());
                })
                .onFailure(ctx::fail);
    }
}
