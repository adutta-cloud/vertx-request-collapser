package com.griddynamics.consumer;

import io.vertx.core.Vertx;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Launcher {
    public static void main(String[] args) {
        ConsumerVerticle consumerVerticle = new ConsumerVerticle();

        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(consumerVerticle);
    }
}