package com.griddynamics.consumer.election;

import com.griddynamics.consumer.cache.SharedDataManager;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LeaderElection {
    private static final Logger logger = LoggerFactory.getLogger(LeaderElection.class);

    /**
     * Elect a leader using Vert.x SharedData
     * This is synchronized to ensure thread-safety within a single JVM
     */
    public static synchronized String electLeader(Vertx vertx, String nodeId) {
        String currentLeader = SharedDataManager.getLeader(vertx);

        if (currentLeader == null || currentLeader.isEmpty()) {
            SharedDataManager.setLeader(vertx, nodeId);
            logger.info("Node {} elected as LEADER", nodeId);
            return nodeId;
        }

        logger.debug("Node {} joined | Current leader: {}", nodeId, currentLeader);
        return currentLeader;
    }

    /**
     * Reset leader election (useful for testing)
     */
    public static void resetLeader(Vertx vertx) {
        SharedDataManager.clearLeader(vertx);
        logger.info("Leader election reset completed");
    }
}
