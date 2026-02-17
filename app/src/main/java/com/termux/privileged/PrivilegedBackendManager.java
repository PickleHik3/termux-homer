package com.termux.privileged;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manager class for privileged backend operations
 * 
 * This class handles the initialization and management of the appropriate
 * privileged backend (Shizuku or shell fallback) and provides a unified
 * interface for the rest of the application.
 */
public class PrivilegedBackendManager {
    private static final String TAG = "PrivilegedBackendManager";
    
    private static PrivilegedBackendManager instance;
    private PrivilegedBackend currentBackend;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    
    private PrivilegedBackendManager() {
        // Private constructor for singleton
    }
    
    /**
     * Get the singleton instance
     */
    public static synchronized PrivilegedBackendManager getInstance() {
        if (instance == null) {
            instance = new PrivilegedBackendManager();
        }
        return instance;
    }
    
    /**
     * Initialize the privileged backend system
     * @param context Application context
     * @return CompletableFuture that completes when initialization is done
     */
    public CompletableFuture<Boolean> initialize(Context context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.i(TAG, "Initializing privileged backend system...");
                
                // Try to initialize Shizuku backend first
                ShizukuBackend shizukuBackend = new ShizukuBackend();
                if (shizukuBackend.initialize(context).join()) {
                    currentBackend = shizukuBackend;
                    Log.i(TAG, "Shizuku backend initialized successfully");
                    return true;
                }
                
                // Fall back to shell backend
                Log.i(TAG, "Shizuku not available, falling back to shell backend");
                ShellBackend shellBackend = new ShellBackend();
                if (shellBackend.initialize(context).join()) {
                    currentBackend = shellBackend;
                    Log.i(TAG, "Shell backend initialized successfully");
                    return true;
                }
                
                // No backend available
                Log.w(TAG, "No privileged backend available");
                currentBackend = new NoOpBackend();
                return false;
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize privileged backend", e);
                currentBackend = new NoOpBackend();
                return false;
            }
        }, executorService);
    }
    
    /**
     * Get the current backend
     */
    public PrivilegedBackend getBackend() {
        return currentBackend;
    }
    
    /**
     * Check if privileged operations are available
     */
    public boolean isPrivilegedAvailable() {
        return currentBackend != null && currentBackend.isAvailable() && currentBackend.hasPermission();
    }
    
    /**
     * Get the current backend type
     */
    public PrivilegedBackend.Type getBackendType() {
        return currentBackend != null ? currentBackend.getType() : PrivilegedBackend.Type.NONE;
    }
    
    /**
     * Request privileged permissions
     */
    public boolean requestPrivilegedPermission(int requestCode) {
        return currentBackend != null && currentBackend.requestPermission(requestCode);
    }
    
    /**
     * Get installed packages with privileged access
     */
    public CompletableFuture<List<String>> getInstalledPackages() {
        return currentBackend != null ? currentBackend.getInstalledPackages() : 
            CompletableFuture.completedFuture(List.of());
    }
    
    /**
     * Install a package
     */
    public CompletableFuture<Boolean> installPackage(String apkPath) {
        return currentBackend != null ? currentBackend.installPackage(apkPath) :
            CompletableFuture.completedFuture(false);
    }
    
    /**
     * Uninstall a package
     */
    public CompletableFuture<Boolean> uninstallPackage(String packageName) {
        return currentBackend != null ? currentBackend.uninstallPackage(packageName) :
            CompletableFuture.completedFuture(false);
    }
    
    /**
     * Execute a privileged command
     */
    public CompletableFuture<String> executeCommand(String command) {
        return currentBackend != null ? currentBackend.executeCommand(command) :
            CompletableFuture.completedFuture("Backend not available");
    }
    
    /**
     * Get status description for debugging
     */
    public String getStatusDescription() {
        return currentBackend != null ? currentBackend.getStatusDescription() : "No backend initialized";
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        if (currentBackend != null) {
            currentBackend.cleanup();
        }
        executorService.shutdown();
    }
    
    /**
     * No-operation backend for when no privileged backend is available
     */
    private static class NoOpBackend implements PrivilegedBackend {
        @Override
        public CompletableFuture<Boolean> initialize(Context context) {
            return CompletableFuture.completedFuture(false);
        }
        
        @Override
        public boolean isAvailable() {
            return false;
        }
        
        @Override
        public Type getType() {
            return Type.NONE;
        }
        
        @Override
        public boolean hasPermission() {
            return false;
        }
        
        @Override
        public boolean requestPermission(int requestCode) {
            return false;
        }
        
        @Override
        public CompletableFuture<List<String>> getInstalledPackages() {
            return CompletableFuture.completedFuture(List.of());
        }
        
        @Override
        public CompletableFuture<Boolean> installPackage(String apkPath) {
            return CompletableFuture.completedFuture(false);
        }
        
        @Override
        public CompletableFuture<Boolean> uninstallPackage(String packageName) {
            return CompletableFuture.completedFuture(false);
        }
        
        @Override
        public CompletableFuture<Boolean> setComponentEnabled(String packageName, String componentName, boolean enabled) {
            return CompletableFuture.completedFuture(false);
        }
        
        @Override
        public CompletableFuture<String> executeCommand(String command) {
            return CompletableFuture.completedFuture("No privileged backend available");
        }
        
        @Override
        public boolean isOperationSupported(PrivilegedOperation operation) {
            return false;
        }
        
        @Override
        public String getStatusDescription() {
            return "No privileged backend available";
        }
        
        @Override
        public void cleanup() {
            // No-op
        }
    }
}