package com.mixer.xr18.app;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared executor service for the app layer.
 */
public class AppExecutors {
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static ExecutorService getExecutor() {
        return executor;
    }

    public static void shutdown() {
        executor.shutdownNow();
    }
}