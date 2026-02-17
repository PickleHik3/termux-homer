package com.termux.privileged;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import rikka.shizuku.Shizuku;
import rikka.shizuku.Sui;

/**
 * Shizuku-based backend for privileged operations
 * 
 * This implementation uses the Shizuku API to perform privileged operations
 * with proper permission handling and lifecycle management.
 */
public class ShizukuBackend implements PrivilegedBackend {
    private static final String TAG = "ShizukuBackend";
    
    private static final int REQUEST_CODE = 1001;
    private static final long COMMAND_TIMEOUT_SECONDS = 30;
    
    private Context context;
    private boolean isAvailable = false;
    private boolean hasPermission = false;
    private boolean binderReceived = false;
    private boolean suiInitialized = false;
    
    // Listeners for Shizuku events
    private final Shizuku.BinderReceivedListener binderReceivedListener = () -> {
        Log.i(TAG, "Shizuku binder received");
        binderReceived = true;
        checkPermission();
    };
    
    private final Shizuku.BinderDeadListener binderDeadListener = () -> {
        Log.i(TAG, "Shizuku binder dead");
        binderReceived = false;
        hasPermission = false;
    };
    
    private final Shizuku.OnRequestPermissionResultListener permissionResultListener = (requestCode, grantResult) -> {
        if (requestCode == REQUEST_CODE) {
            hasPermission = (grantResult == PackageManager.PERMISSION_GRANTED);
            Log.i(TAG, "Permission request result: " + hasPermission);
        }
    };
    
    @Override
    public CompletableFuture<Boolean> initialize(Context context) {
        this.context = context.getApplicationContext();
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.i(TAG, "Initializing Shizuku backend...");
                
                // Check if we can initialize Sui (Magisk module)
                suiInitialized = Sui.init(context.getPackageName());
                Log.i(TAG, "Sui initialization result: " + suiInitialized);
                
                // Add listeners for binder events
                Shizuku.addBinderReceivedListener(binderReceivedListener);
                Shizuku.addBinderDeadListener(binderDeadListener);
                Shizuku.addRequestPermissionResultListener(permissionResultListener);
                
                // Check if we already have a binder
                if (Shizuku.isPreV11()) {
                    Log.w(TAG, "Shizuku pre-v11 not supported");
                    isAvailable = false;
                    return false;
                }
                
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    binderReceived = true;
                    hasPermission = true;
                    Log.i(TAG, "Shizuku permission already granted");
                } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                    Log.i(TAG, "Shizuku permission previously denied");
                    binderReceived = true; // We have a binder but no permission
                    hasPermission = false;
                } else {
                    Log.i(TAG, "Shizuku binder received, permission not yet requested");
                    binderReceived = true; // Binder is received, just need permission
                }
                
                isAvailable = true;
                Log.i(TAG, "Shizuku backend initialized successfully");
                return true;
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize Shizuku backend", e);
                isAvailable = false;
                hasPermission = false;
                return false;
            }
        });
    }
    
    @Override
    public boolean isAvailable() {
        return isAvailable && binderReceived;
    }
    
    @Override
    public Type getType() {
        return Type.SHIZUKU;
    }
    
    @Override
    public boolean hasPermission() {
        return hasPermission;
    }
    
    @Override
    public boolean requestPermission(int requestCode) {
        if (!isAvailable() || Shizuku.isPreV11()) {
            Log.w(TAG, "Cannot request permission: backend not available");
            return false;
        }
        
        if (hasPermission) {
            Log.i(TAG, "Permission already granted");
            return true;
        }
        
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            Log.i(TAG, "User previously denied permission");
            return false;
        }
        
        if (requestCode != REQUEST_CODE) {
            Log.w(TAG, "Ignoring non-standard request code: " + requestCode + ", using " + REQUEST_CODE);
        }

        Log.i(TAG, "Requesting Shizuku permission...");
        Shizuku.requestPermission(REQUEST_CODE);
        return true;
    }
    
    @Override
    public CompletableFuture<List<String>> getInstalledPackages() {
        return CompletableFuture.supplyAsync(() -> {
            if (!hasPermission()) {
                Log.w(TAG, "No permission to get installed packages");
                return List.of();
            }
            
            try {
                List<String> packages = new ArrayList<>();
                
                // Use PackageManager with Shizuku's elevated privileges
                // This would require a UserService for true privileged access
                // For now, we'll use the standard approach with elevated context
                
                // TODO: Implement proper UserService for full privileged access
                // This is a simplified implementation
                
                Log.i(TAG, "Getting installed packages via Shizuku...");
                
                // For demonstration, we'll use a simple shell command approach
                // In a real implementation, this would use proper Shizuku UserService
                String output = executeShizukuCommand(List.of("pm", "list", "packages"));
                
                if (output != null) {
                    String[] lines = output.split("\n");
                    for (String line : lines) {
                        if (line.startsWith("package:")) {
                            packages.add(line.substring("package:".length()));
                        }
                    }
                }
                
                return packages;
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to get installed packages", e);
                return List.of();
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> installPackage(String apkPath) {
        return CompletableFuture.supplyAsync(() -> {
            if (!hasPermission()) {
                Log.w(TAG, "No permission to install packages");
                return false;
            }
            
            try {
                if (apkPath == null || apkPath.trim().isEmpty()) {
                    Log.e(TAG, "Invalid APK path");
                    return false;
                }
                
                Log.i(TAG, "Installing package via Shizuku: " + apkPath);
                
                String output = executeShizukuCommand(List.of("pm", "install", "-r", apkPath));
                
                if (output != null) {
                    boolean success = output.contains("Success") || output.contains("success");
                    Log.i(TAG, "Install result: " + output);
                    return success;
                }
                
                return false;
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to install package: " + apkPath, e);
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> uninstallPackage(String packageName) {
        return CompletableFuture.supplyAsync(() -> {
            if (!hasPermission()) {
                Log.w(TAG, "No permission to uninstall packages");
                return false;
            }
            
            try {
                if (packageName == null || packageName.trim().isEmpty()) {
                    Log.e(TAG, "Invalid package name");
                    return false;
                }
                
                Log.i(TAG, "Uninstalling package via Shizuku: " + packageName);
                
                String output = executeShizukuCommand(List.of("pm", "uninstall", packageName));
                
                if (output != null) {
                    boolean success = output.contains("Success") || output.contains("success");
                    Log.i(TAG, "Uninstall result: " + output);
                    return success;
                }
                
                return false;
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to uninstall package: " + packageName, e);
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> setComponentEnabled(String packageName, String componentName, boolean enabled) {
        return CompletableFuture.supplyAsync(() -> {
            if (!hasPermission()) {
                Log.w(TAG, "No permission to modify components");
                return false;
            }
            
            try {
                if (packageName == null || componentName == null) {
                    Log.e(TAG, "Invalid package or component name");
                    return false;
                }
                
                String action = enabled ? "enable" : "disable";
                Log.i(TAG, "Setting component via Shizuku: " + packageName + "/" + componentName + " to " + action);
                
                String output = executeShizukuCommand(List.of("pm", action, packageName + "/" + componentName));
                
                if (output != null) {
                    boolean success = output.contains("Success") || output.contains("success");
                    Log.i(TAG, "Component " + action + " result: " + output);
                    return success;
                }
                
                return false;
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to set component enabled: " + componentName, e);
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<String> executeCommand(String command) {
        return CompletableFuture.supplyAsync(() -> {
            if (!hasPermission()) {
                return "No permission to execute commands";
            }
            
            try {
                if (command == null || command.trim().isEmpty()) {
                    return "Invalid command";
                }
                
                Log.i(TAG, "Executing command via Shizuku");
                return executeShizukuCommand(List.of("sh", "-c", command));
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to execute command: " + command, e);
                return "Error: " + e.getMessage();
            }
        });
    }
    
    @Override
    public boolean isOperationSupported(PrivilegedOperation operation) {
        // Shizuku backend supports most operations through UserService
        // For now, we'll return true for most operations
        return operation != null;
    }
    
    @Override
    public String getStatusDescription() {
        int uid = Shizuku.getUid();
        String privilegeLevel = (uid == 0) ? "ROOT" : (uid == 2000 ? "ADB" : "UNKNOWN(" + uid + ")");
        
        return String.format("Shizuku backend - Available: %s, HasPermission: %s, SuiInit: %s, Privilege: %s", 
            isAvailable, hasPermission, suiInitialized, privilegeLevel);
    }
    
    @Override
    public void cleanup() {
        try {
            // Remove listeners
            Shizuku.removeBinderReceivedListener(binderReceivedListener);
            Shizuku.removeBinderDeadListener(binderDeadListener);
            Shizuku.removeRequestPermissionResultListener(permissionResultListener);
            
            Log.i(TAG, "Shizuku backend cleaned up");
        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup", e);
        }
    }
    
    /**
     * Check permission status
     */
    private void checkPermission() {
        try {
            int permission = Shizuku.checkSelfPermission();
            hasPermission = (permission == PackageManager.PERMISSION_GRANTED);
            Log.i(TAG, "Permission check result: " + hasPermission);
        } catch (Exception e) {
            Log.e(TAG, "Failed to check permission", e);
            hasPermission = false;
        }
    }
    
    /**
     * Execute a command through Shizuku with proper privilege escalation
     */
    private String executeShizukuCommand(List<String> args) {
        try {
            if (!hasPermission()) {
                return "No Shizuku permission";
            }

            if (args == null || args.isEmpty()) {
                return "Invalid command";
            }

            // Use Shizuku.newProcess() for proper privileged execution
            Process process = Shizuku.newProcess(args.toArray(new String[0]), null, null);

            // Wait for process with timeout
            boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                Log.w(TAG, "Shizuku command timed out");
                return "Error: Command timed out";
            }

            String output = readStream(process.getInputStream());
            String errorOutput = readStream(process.getErrorStream());

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String errorMsg = errorOutput.length() > 0 ? errorOutput : "Exit code: " + exitCode;
                Log.w(TAG, "Shizuku command failed (" + exitCode + ")");
                return "Error (" + exitCode + "): " + errorMsg;
            }

            return output;

        } catch (Exception e) {
            Log.e(TAG, "Failed to execute Shizuku command", e);
            return "Error: " + e.getMessage();
        }
    }

    private String readStream(java.io.InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (output.length() > 0) {
                output.append("\n");
            }
            output.append(line);
        }
        return output.toString();
    }
}
