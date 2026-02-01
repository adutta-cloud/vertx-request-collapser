package com.griddynamics.consumer.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.IAtomicReference;
import com.hazelcast.map.IMap;

public class HazelcastManager {
    private static HazelcastInstance instance;

    public static synchronized HazelcastInstance getInstance() {
        if (instance == null) {
            Config config = new Config();
            config.setInstanceName("consumer-cluster");
            config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(true);

            instance = Hazelcast.newHazelcastInstance(config);
        }
        return instance;
    }

    public static IMap<String, String> getDataCache() {
        return getInstance().getMap("data-cache");
    }

    public static IAtomicReference<String> getLeaderReference() {
        return getInstance().getCPSubsystem().getAtomicReference("elected-leader");
    }

    public static void shutdown() {
        if (instance != null) {
            instance.shutdown();
        }
    }
}
