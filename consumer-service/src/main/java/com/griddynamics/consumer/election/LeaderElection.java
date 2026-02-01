package com.griddynamics.consumer.election;

import com.griddynamics.consumer.hazelcast.HazelcastManager;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.lock.FencedLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LeaderElection {
    private static final Logger logger = LoggerFactory.getLogger(LeaderElection.class);
    private static final String LEADER_LOCK_NAME = "leader-election-lock";
    private static final String LEADER_KEY = "elected-leader";

    public static String electLeader(String nodeId) {
        HazelcastInstance hz = HazelcastManager.getInstance();
        FencedLock lock = hz.getCPSubsystem().getLock(LEADER_LOCK_NAME);

        lock.lock();
        try {
            var leaderRef = hz.getCPSubsystem().getAtomicReference(LEADER_KEY);
            String currentLeader = (String) leaderRef.get();

            if (currentLeader == null || currentLeader.isEmpty()) {
                leaderRef.set(nodeId);
                logger.info("Node {} elected as LEADER", nodeId);
                return nodeId;
            }

            logger.debug("Node {} joined cluster | Current leader: {}", nodeId, currentLeader);
            return currentLeader;
        } finally {
            lock.unlock();
        }
    }

    public static void resetLeader() {
        HazelcastInstance hz = HazelcastManager.getInstance();
        var leaderRef = hz.getCPSubsystem().getAtomicReference(LEADER_KEY);
        leaderRef.set("");
        logger.info("Leader election reset completed");
    }
}
