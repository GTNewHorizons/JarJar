package com.mitchej123.jarjar.util;

import com.mitchej123.jarjar.Config;

import java.util.concurrent.ForkJoinPool;

/**
 * Shared ForkJoinPool used for Discovery.
 */
public final class DiscoveryPool {

    private static volatile ForkJoinPool pool;

    private DiscoveryPool() {}

    public static ForkJoinPool get() {
        ForkJoinPool p = pool;
        if (p == null) {
            synchronized (DiscoveryPool.class) {
                p = pool;
                if (p == null) {
                    p = new ForkJoinPool(Math.max(1, Config.maxThreads));
                    pool = p;
                }
            }
        }
        return p;
    }

    public static void shutdown() {
        synchronized (DiscoveryPool.class) {
            final ForkJoinPool p = pool;
            if (p != null) {
                p.shutdown();
                pool = null;
            }
        }
    }
}
