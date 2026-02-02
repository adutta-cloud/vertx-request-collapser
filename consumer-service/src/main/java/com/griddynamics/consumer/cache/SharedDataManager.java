package com.griddynamics.consumer.cache;

import io.vertx.core.Vertx;
import io.vertx.core.shareddata.LocalMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SharedDataManager {
    private static final Logger logger = LoggerFactory.getLogger(SharedDataManager.class);
    private static final String DATA_CACHE_NAME = "data-cache";
    private static final String LEADER_MAP_NAME = "leader-election";
    private static final String LEADER_KEY = "elected-leader";

    /**
     * Get the shared data cache for storing Producer data
     * This is a LocalMap shared across all verticles in the same JVM
     */
    public static LocalMap<String, String> getDataCache(Vertx vertx) {
        return vertx.sharedData().getLocalMap(DATA_CACHE_NAME);
    }

    /**
     * Get the current leader node ID
     */
    public static String getLeader(Vertx vertx) {
        LocalMap<String, String> leaderMap = vertx.sharedData().getLocalMap(LEADER_MAP_NAME);
        return leaderMap.get(LEADER_KEY);
    }

    /**
     * Set the leader node ID
     */
    public static void setLeader(Vertx vertx, String nodeId) {
        LocalMap<String, String> leaderMap = vertx.sharedData().getLocalMap(LEADER_MAP_NAME);
        leaderMap.put(LEADER_KEY, nodeId);
        logger.debug("Leader set to: {}", nodeId);
    }

    /**
     * Clear the current leader (for reset/testing)
     */
    public static void clearLeader(Vertx vertx) {
        LocalMap<String, String> leaderMap = vertx.sharedData().getLocalMap(LEADER_MAP_NAME);
        leaderMap.clear();
        logger.debug("Leader election cleared");
    }

    /**
     * Check if a specific key exists in cache
     */
    public static boolean containsKey(Vertx vertx, String key) {
        return getDataCache(vertx).containsKey(key);
    }

    /**
     * Get cached data for a key
     */
    public static String getCachedData(Vertx vertx, String key) {
        return getDataCache(vertx).get(key);
    }

    /**
     * Store data in cache
     */
    public static void cacheData(Vertx vertx, String key, String data) {
        getDataCache(vertx).put(key, data);
        logger.debug("Cached data for key: {}", key);
    }
}
