package com.termux.privileged;

import android.content.Context;
import android.util.Log;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Utility class for testing privileged backend functionality
 */
public class PrivilegedBackendTest {
    private static final String TAG = "PrivilegedBackendTest";
    
    /**
     * Test the privileged backend functionality
     */
    public static CompletableFuture<Boolean> testPrivilegedBackend(Context context) {
        Log.i(TAG, "Testing privileged backend functionality...");
        
        return PrivilegedBackendManager.getInstance().initialize(context)
            .thenCompose(success -> {
                if (!success) {
                    Log.w(TAG, "Backend initialization failed");
                    return CompletableFuture.completedFuture(false);
                }
                
                return runBasicTests();
            })
            .thenApply(result -> {
                Log.i(TAG, "Privileged backend test completed: " + result);
                return result;
            });
    }
    
    /**
     * Run basic tests on the initialized backend
     */
    private static CompletableFuture<Boolean> runBasicTests() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                PrivilegedBackendManager manager = PrivilegedBackendManager.getInstance();
                PrivilegedBackend backend = manager.getBackend();
                
                if (backend == null) {
                    Log.e(TAG, "Backend is null");
                    return false;
                }
                
                // Test 1: Check backend availability
                if (!backend.isAvailable()) {
                    Log.w(TAG, "Backend is not available");
                    return false;
                }
                Log.i(TAG, "✓ Backend is available");
                
                // Test 2: Check backend type
                PrivilegedBackend.Type type = backend.getType();
                Log.i(TAG, "✓ Backend type: " + type);
                
                // Test 3: Check permission status
                boolean hasPermission = backend.hasPermission();
                Log.i(TAG, "✓ Backend has permission: " + hasPermission);
                
                // Test 4: Test basic command execution
                String testCommand = "echo 'Hello from privileged backend'";
                String output = backend.executeCommand(testCommand).join();
                if (output != null && !output.isEmpty()) {
                    Log.i(TAG, "✓ Command execution works: " + output);
                } else {
                    Log.w(TAG, "✗ Command execution failed or returned empty");
                }
                
                // Test 5: Test get installed packages (if we have permission)
                if (hasPermission) {
                    List<String> packages = backend.getInstalledPackages().join();
                    if (packages != null && !packages.isEmpty()) {
                        Log.i(TAG, "✓ Get installed packages works: found " + packages.size() + " packages");
                    } else {
                        Log.w(TAG, "✗ Get installed packages returned empty or null");
                    }
                }
                
                // Test 6: Check status description
                String status = backend.getStatusDescription();
                Log.i(TAG, "✓ Status description: " + status);
                
                Log.i(TAG, "All basic tests completed successfully");
                return true;
                
            } catch (Exception e) {
                Log.e(TAG, "Error during testing", e);
                return false;
            }
        });
    }
    
    /**
     * Test a specific privileged operation
     */
    public static CompletableFuture<String> testSpecificOperation(Context context, PrivilegedOperation operation) {
        return PrivilegedBackendManager.getInstance().initialize(context)
            .thenCompose(success -> {
                if (!success) {
                    return CompletableFuture.completedFuture("Backend initialization failed");
                }
                
                PrivilegedBackend backend = PrivilegedBackendManager.getInstance().getBackend();
                if (backend == null || !backend.isAvailable()) {
                    return CompletableFuture.completedFuture("Backend not available");
                }
                
                return performOperationTest(backend, operation);
            });
    }
    
    /**
     * Perform a specific operation test
     */
    private static CompletableFuture<String> performOperationTest(PrivilegedBackend backend, PrivilegedOperation operation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                StringBuilder result = new StringBuilder();
                result.append("Testing operation: ").append(operation.name()).append("\n");
                
                // Check if operation is supported
                boolean supported = backend.isOperationSupported(operation);
                result.append("Operation supported: ").append(supported).append("\n");
                
                if (!supported) {
                    return result.toString();
                }
                
                // Perform the operation based on type
                switch (operation) {
                    case GET_INSTALLED_PACKAGES:
                        List<String> packages = backend.getInstalledPackages().join();
                        result.append("Found packages: ").append(packages != null ? packages.size() : 0).append("\n");
                        if (packages != null && !packages.isEmpty()) {
                            result.append("Sample packages: ").append(packages.subList(0, Math.min(3, packages.size()))).append("\n");
                        }
                        break;
                        
                    case EXECUTE_COMMAND:
                        String command = "whoami";
                        String output = backend.executeCommand(command).join();
                        result.append("Command '").append(command).append("' output: ").append(output).append("\n");
                        break;
                        
                    default:
                        result.append("Operation test not implemented for: ").append(operation.name()).append("\n");
                        break;
                }
                
                return result.toString();
                
            } catch (Exception e) {
                return "Error testing operation " + operation.name() + ": " + e.getMessage();
            }
        });
    }
    
    /**
     * Simple test that can be called from anywhere
     */
    public static void quickTest(Context context) {
        testPrivilegedBackend(context)
            .thenAccept(success -> {
                String message = success ? "Privileged backend test PASSED" : "Privileged backend test FAILED";
                Log.i(TAG, message);
                
                if (context != null) {
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show();
                }
            })
            .exceptionally(throwable -> {
                Log.e(TAG, "Test failed with exception", throwable);
                return null;
            });
    }
}