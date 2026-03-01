package com.termux.tooie;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;

import com.termux.privileged.PrivilegedBackend;
import com.termux.privileged.PrivilegedBackendManager;
import com.termux.privileged.PrivilegedPolicyStore;
import com.termux.privileged.ShizukuBackend;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local Tooie API server exposed on localhost for shell integrations.
 */
public class TooieApiServer {
    private static final String LOG_TAG = "TooieApiServer";
    private static final String API_VERSION = "v1";
    private static final String TOOIE_DIR_PATH = TermuxConstants.TERMUX_HOME_DIR_PATH + "/.tooie";
    private static final String TOKEN_FILE_PATH = TOOIE_DIR_PATH + "/token";
    private static final String ENDPOINT_FILE_PATH = TOOIE_DIR_PATH + "/endpoint";
    private static final String CONFIG_FILE_PATH = TOOIE_DIR_PATH + "/config.json";
    private static final String TOOIE_BIN_PATH = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/tooie";

    private static final int MAX_REQUEST_LINE_BYTES = 4096;
    private static final int MAX_HEADER_LINE_BYTES = 4096;
    private static final int MAX_HEADER_LINES = 64;
    private static final int MAX_BODY_BYTES = 16 * 1024;
    private static final int MAX_EXEC_COMMAND_LENGTH = 512;
    private static final int CLIENT_SOCKET_TIMEOUT_MS = 10_000;
    private static final int MIN_BRIGHTNESS = 0;
    private static final int MAX_BRIGHTNESS = 255;
    private static final int DEFAULT_VOLUME_STREAM = AudioManager.STREAM_MUSIC;

    private static TooieApiServer instance;

    private final ThreadPoolExecutor clientExecutor = new ThreadPoolExecutor(
        2, 4, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(64));
    private final SecureRandom random = new SecureRandom();
    private final Map<String, SimpleRateLimiter> rateLimiters = new HashMap<>();

    private volatile boolean running;
    private volatile String token;
    private volatile int port;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private long lastCpuTotalTicks = -1L;
    private long lastCpuIdleTicks = -1L;
    private long lastCpuSampleMs = 0L;
    private Context appContext;

    private TooieApiServer() {
    }

    public static synchronized TooieApiServer getInstance() {
        if (instance == null) {
            instance = new TooieApiServer();
        }
        return instance;
    }

    public synchronized void start(Context context) {
        if (running) {
            return;
        }

        try {
            initializeRateLimiters();
            appContext = context.getApplicationContext();
            token = generateToken();
            serverSocket = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"));
            port = serverSocket.getLocalPort();
            running = true;
            writeClientConfig();
            installTooieCliScript();
            startAcceptLoop(context.getApplicationContext());
            Logger.logInfo(LOG_TAG, "Tooie API listening on 127.0.0.1:" + port);
        } catch (Exception e) {
            running = false;
            Logger.logErrorExtended(LOG_TAG, "Failed to start Tooie API server: " + e.getMessage());
            cleanupSocket();
        }
    }

    public synchronized void stop() {
        running = false;
        cleanupSocket();
        if (acceptThread != null) {
            acceptThread.interrupt();
            acceptThread = null;
        }
        clientExecutor.shutdownNow();
    }

    private void startAcceptLoop(Context context) {
        acceptThread = new Thread(() -> {
            while (running && serverSocket != null && !serverSocket.isClosed()) {
                try {
                    Socket client = serverSocket.accept();
                    try {
                        clientExecutor.submit(() -> handleClient(client, context));
                    } catch (RejectedExecutionException rejected) {
                        closeQuietly(client);
                    }
                } catch (IOException e) {
                    if (running) {
                        Logger.logErrorExtended(LOG_TAG, "Accept failed: " + e.getMessage());
                    }
                }
            }
        }, "tooie-api-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void handleClient(Socket socket, Context context) {
        try (Socket client = socket;
             BufferedInputStream input = new BufferedInputStream(client.getInputStream());
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))) {
            client.setSoTimeout(CLIENT_SOCKET_TIMEOUT_MS);

            HttpRequest request;
            try {
                request = parseRequest(input);
            } catch (HttpParseException e) {
                writeResponse(writer, e.statusCode, jsonError(e.errorCode, e.getMessage()).toString());
                return;
            }

            if (!isAuthorized(request.headers)) {
                writeResponse(writer, 401, jsonError("unauthorized", "Missing or invalid token").toString());
                return;
            }

            if (!allowRequest(request)) {
                writeResponse(writer, 429, jsonError("rate_limited", "Too many requests; retry later").toString());
                return;
            }

            JSONObject response = routeRequest(context, request);
            int statusCode = response.optInt("_statusCode", 200);
            response.remove("_statusCode");
            writeResponse(writer, statusCode, response.toString());

        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, "Request handling failed: " + e.getMessage());
        }
    }

    private JSONObject routeRequest(Context context, HttpRequest request) {
        try {
            if ("GET".equals(request.method) && "/v1/status".equals(request.path)) {
                return buildStatus();
            } else if ("GET".equals(request.method) && "/v1/apps".equals(request.path)) {
                return buildApps(context);
            } else if ("GET".equals(request.method) && "/v1/system/resources".equals(request.path)) {
                return buildSystemResources(context);
            } else if ("GET".equals(request.method) && "/v1/media/now-playing".equals(request.path)) {
                return buildNowPlaying();
            } else if ("GET".equals(request.method) && "/v1/media/art".equals(request.path)) {
                return buildNowPlayingArt();
            } else if ("GET".equals(request.method) && "/v1/notifications".equals(request.path)) {
                return buildNotifications();
            } else if ("POST".equals(request.method) && "/v1/exec".equals(request.path)) {
                return runExec(context, request.body);
            } else if ("POST".equals(request.method) && "/v1/system/brightness".equals(request.path)) {
                return runBrightness(context, request.body);
            } else if ("POST".equals(request.method) && "/v1/system/volume".equals(request.path)) {
                return runVolume(context, request.body);
            } else if ("POST".equals(request.method) && "/v1/privileged/request-permission".equals(request.path)) {
                return requestPrivilegedPermission(context);
            } else if ("POST".equals(request.method) && "/v1/screen/lock".equals(request.path)) {
                return runLockScreen(context);
            } else if ("POST".equals(request.method) && "/v1/auth/rotate".equals(request.path)) {
                return rotateAuthToken();
            }

            JSONObject notFound = jsonError("not_found", "Unknown endpoint");
            notFound.put("_statusCode", 404);
            return notFound;
        } catch (Exception e) {
            JSONObject error = jsonError("internal_error", e.getMessage());
            try {
                error.put("_statusCode", 500);
            } catch (JSONException ignored) {
            }
            return error;
        }
    }

    private JSONObject buildStatus() throws JSONException {
        PrivilegedBackendManager manager = PrivilegedBackendManager.getInstance();
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("apiVersion", API_VERSION);
        data.put("backendType", String.valueOf(manager.getBackendType()));
        data.put("backendState", String.valueOf(manager.getBackendState()));
        data.put("statusReason", String.valueOf(manager.getStatusReason()));
        data.put("statusMessage", manager.getStatusMessage());
        data.put("isPrivilegedAvailable", manager.isPrivilegedAvailable());
        data.put("notificationListenerConnected", TooieNotificationListener.isListenerConnected());
        data.put("execPolicy", describeExecPolicy());
        data.put("privilegedPolicy", describePrivilegedPolicy());
        return data;
    }

    private JSONObject buildApps(Context context) throws JSONException {
        PackageManager packageManager = context.getPackageManager();
        List<PackageInfo> packages = packageManager.getInstalledPackages(0);
        JSONArray apps = new JSONArray();
        for (PackageInfo info : packages) {
            if (info.packageName == null) continue;
            JSONObject item = new JSONObject();
            ApplicationInfo appInfo = info.applicationInfo;
            CharSequence label = appInfo != null ? packageManager.getApplicationLabel(appInfo) : info.packageName;
            item.put("packageName", info.packageName);
            item.put("label", label != null ? label.toString() : info.packageName);
            item.put("systemApp", appInfo != null && (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
            apps.put(item);
        }
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("count", apps.length());
        data.put("apps", apps);
        return data;
    }

    private JSONObject buildSystemResources(Context context) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("apiVersion", API_VERSION);
        data.put("timestampMs", System.currentTimeMillis());
        data.put("cpuCores", Runtime.getRuntime().availableProcessors());

        double[] loadAverage = readLoadAverage();
        if (loadAverage != null) {
            data.put("loadAvg1m", loadAverage[0]);
            data.put("loadAvg5m", loadAverage[1]);
            data.put("loadAvg15m", loadAverage[2]);
        }
        double cpuPercent = readCPUPercent(loadAverage, Runtime.getRuntime().availableProcessors());
        if (cpuPercent >= 0) {
            data.put("cpuPercent", cpuPercent);
        }

        Map<String, Long> memInfoKb = readMemInfoKb();
        if (!memInfoKb.isEmpty()) {
            long memTotalKb = memInfoKb.get("MemTotal") != null ? memInfoKb.get("MemTotal") : 0L;
            long memAvailableKb = memInfoKb.get("MemAvailable") != null ? memInfoKb.get("MemAvailable") : 0L;
            long memFreeKb = memInfoKb.get("MemFree") != null ? memInfoKb.get("MemFree") : 0L;
            long memUsedKb = memTotalKb > 0 && memAvailableKb > 0 ? (memTotalKb - memAvailableKb) : 0L;
            data.put("memTotalBytes", memTotalKb * 1024L);
            data.put("memAvailableBytes", memAvailableKb * 1024L);
            data.put("memFreeBytes", memFreeKb * 1024L);
            data.put("memUsedBytes", memUsedKb * 1024L);

            JSONObject memory = new JSONObject();
            memory.put("totalBytes", memTotalKb * 1024L);
            memory.put("availableBytes", memAvailableKb * 1024L);
            memory.put("freeBytes", memFreeKb * 1024L);
            memory.put("usedBytes", memUsedKb * 1024L);
            putMemInfoBytes(memory, "buffersBytes", memInfoKb, "Buffers");
            putMemInfoBytes(memory, "cachedBytes", memInfoKb, "Cached");
            putMemInfoBytes(memory, "swapCachedBytes", memInfoKb, "SwapCached");
            putMemInfoBytes(memory, "activeBytes", memInfoKb, "Active");
            putMemInfoBytes(memory, "inactiveBytes", memInfoKb, "Inactive");
            putMemInfoBytes(memory, "shmemBytes", memInfoKb, "Shmem");
            putMemInfoBytes(memory, "slabBytes", memInfoKb, "Slab");
            putMemInfoBytes(memory, "swapTotalBytes", memInfoKb, "SwapTotal");
            putMemInfoBytes(memory, "swapFreeBytes", memInfoKb, "SwapFree");
            data.put("memory", memory);
        }

        Runtime runtime = Runtime.getRuntime();
        long javaHeapUsedBytes = runtime.totalMemory() - runtime.freeMemory();
        data.put("javaHeapUsedBytes", javaHeapUsedBytes);
        data.put("javaHeapMaxBytes", runtime.maxMemory());
        data.put("javaHeapFreeBytes", runtime.freeMemory());
        data.put("javaHeapTotalBytes", runtime.totalMemory());

        JSONObject runtimeInfo = new JSONObject();
        runtimeInfo.put("availableProcessors", runtime.availableProcessors());
        runtimeInfo.put("javaHeapUsedBytes", javaHeapUsedBytes);
        runtimeInfo.put("javaHeapFreeBytes", runtime.freeMemory());
        runtimeInfo.put("javaHeapTotalBytes", runtime.totalMemory());
        runtimeInfo.put("javaHeapMaxBytes", runtime.maxMemory());
        data.put("runtime", runtimeInfo);

        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);
            data.put("lowMemory", memoryInfo.lowMemory);
            data.put("memoryThresholdBytes", memoryInfo.threshold);
            data.put("memoryClassMb", activityManager.getMemoryClass());
            data.put("largeMemoryClassMb", activityManager.getLargeMemoryClass());
        }

        JSONObject uptime = readUptimeInfo();
        if (uptime.length() > 0) {
            data.put("uptime", uptime);
        }

        JSONArray storage = readStorageStats(context);
        if (storage.length() > 0) {
            data.put("storage", storage);
        }

        JSONObject battery = readBatteryInfo(context);
        if (battery.length() > 0) {
            data.put("battery", battery);
        }

        JSONArray network = readNetworkStats();
        if (network.length() > 0) {
            data.put("network", network);
        }

        JSONArray thermal = readThermalZones();
        if (thermal.length() > 0) {
            data.put("thermal", thermal);
        }

        PrivilegedBackendManager manager = PrivilegedBackendManager.getInstance();
        data.put("backendType", String.valueOf(manager.getBackendType()));
        data.put("backendState", String.valueOf(manager.getBackendState()));
        data.put("statusReason", String.valueOf(manager.getStatusReason()));
        data.put("statusMessage", manager.getStatusMessage());
        data.put("isPrivilegedAvailable", manager.isPrivilegedAvailable());
        data.put("execPolicy", describeExecPolicy());
        data.put("privilegedPolicy", describePrivilegedPolicy());
        return data;
    }

    private JSONObject buildNowPlaying() throws JSONException {
        JSONObject snapshot = TooieNotificationListener.getNowPlayingSnapshot();
        snapshot.put("ok", true);
        return snapshot;
    }

    private JSONObject buildNowPlayingArt() throws JSONException {
        JSONObject snapshot = TooieNotificationListener.getNowPlayingArtSnapshot();
        snapshot.put("ok", true);
        return snapshot;
    }

    private JSONObject buildNotifications() throws JSONException {
        JSONObject snapshot = TooieNotificationListener.getNotificationsSnapshot();
        snapshot.put("ok", true);
        return snapshot;
    }

    private JSONObject runExec(Context context, String body) throws JSONException {
        JSONObject endpointGuard = ensurePrivilegedEndpointEnabled(context, PrivilegedPolicyStore.Endpoint.EXEC, "/v1/exec");
        if (endpointGuard != null) return endpointGuard;
        JSONObject request = body != null && !body.isEmpty() ? new JSONObject(body) : new JSONObject();
        String command = request.optString("command", "").trim();
        if (command.isEmpty()) {
            JSONObject error = jsonError("bad_request", "Missing command");
            error.put("_statusCode", 400);
            return error;
        }
        if (command.length() > MAX_EXEC_COMMAND_LENGTH) {
            JSONObject error = jsonError("bad_request", "Command too long");
            error.put("_statusCode", 400);
            return error;
        }
        if (containsControlChars(command)) {
            JSONObject error = jsonError("bad_request", "Command contains unsupported control characters");
            error.put("_statusCode", 400);
            return error;
        }

        ExecPolicy execPolicy = loadExecPolicy();
        if (!execPolicy.execEnabled) {
            JSONObject error = jsonError("forbidden", "Exec endpoint disabled by policy");
            error.put("_statusCode", 403);
            return error;
        }
        if (!isCommandAllowed(command, execPolicy.allowedCommandPrefixes)) {
            JSONObject error = jsonError("forbidden", "Command not allowed by policy");
            error.put("_statusCode", 403);
            return error;
        }

        PrivilegedBackendManager manager = PrivilegedBackendManager.getInstance();
        if (manager.getBackendType() == PrivilegedBackend.Type.SHIZUKU && !manager.getBackend().hasPermission()) {
            boolean requested = manager.requestPrivilegedPermission(ShizukuBackend.PERMISSION_REQUEST_CODE);
            JSONObject error = jsonError("permission_required",
                "Shizuku permission is required. Grant it, then retry command.");
            error.put("_statusCode", 403);
            error.put("permissionRequested", requested);
            error.put("backendState", String.valueOf(manager.getBackendState()));
            error.put("statusReason", String.valueOf(manager.getStatusReason()));
            error.put("statusMessage", manager.getStatusMessage());
            return error;
        }

        String output;
        try {
            output = manager.executeCommand(command).get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            JSONObject error = jsonError("exec_failed", e.getMessage());
            error.put("_statusCode", 500);
            return error;
        }

        JSONObject data = new JSONObject();
        data.put("ok", isSuccessfulCommandOutput(output));
        data.put("command", command);
        data.put("output", output == null ? "" : output);
        return data;
    }

    private JSONObject requestPrivilegedPermission(Context context) throws JSONException {
        JSONObject endpointGuard = ensurePrivilegedEndpointEnabled(context, PrivilegedPolicyStore.Endpoint.REQUEST_PERMISSION, "/v1/privileged/request-permission");
        if (endpointGuard != null) return endpointGuard;
        PrivilegedBackendManager manager = PrivilegedBackendManager.getInstance();
        boolean requested = manager.requestPrivilegedPermission(ShizukuBackend.PERMISSION_REQUEST_CODE);

        JSONObject data = new JSONObject();
        data.put("ok", requested || manager.getBackend().hasPermission());
        data.put("requested", requested);
        data.put("backendType", String.valueOf(manager.getBackendType()));
        data.put("backendState", String.valueOf(manager.getBackendState()));
        data.put("statusReason", String.valueOf(manager.getStatusReason()));
        data.put("statusMessage", manager.getStatusMessage());
        data.put("hasPermission", manager.getBackend().hasPermission());
        return data;
    }

    private JSONObject runLockScreen(Context context) throws JSONException {
        JSONObject endpointGuard = ensurePrivilegedEndpointEnabled(context, PrivilegedPolicyStore.Endpoint.LOCK_SCREEN, "/v1/screen/lock");
        if (endpointGuard != null) return endpointGuard;
        String first = executePrivileged("input keyevent 223");
        String used = "input keyevent 223";
        String output = first;
        if (first != null && first.startsWith("Error")) {
            used = "input keyevent 26";
            output = executePrivileged(used);
        }

        JSONObject data = new JSONObject();
        data.put("ok", isSuccessfulCommandOutput(output));
        data.put("command", used);
        data.put("output", output == null ? "" : output);
        return data;
    }

    private JSONObject rotateAuthToken() throws JSONException {
        token = generateToken();
        try {
            writeClientConfig();
        } catch (IOException e) {
            JSONObject error = jsonError("rotate_failed", "Failed to persist rotated token: " + e.getMessage());
            error.put("_statusCode", 500);
            return error;
        }
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("rotated", true);
        return data;
    }

    private JSONObject runBrightness(Context context, String body) throws JSONException {
        JSONObject endpointGuard = ensurePrivilegedEndpointEnabled(context, PrivilegedPolicyStore.Endpoint.BRIGHTNESS, "/v1/system/brightness");
        if (endpointGuard != null) return endpointGuard;
        JSONObject request = body != null && !body.isEmpty() ? new JSONObject(body) : new JSONObject();
        Integer targetBrightness = readOptionalInteger(request, "brightness", "value");
        if (targetBrightness != null && (targetBrightness < MIN_BRIGHTNESS || targetBrightness > MAX_BRIGHTNESS)) {
            JSONObject error = jsonError("bad_request", "brightness must be between 0 and 255");
            error.put("_statusCode", 400);
            return error;
        }

        String setOutput = "";
        if (targetBrightness != null) {
            setOutput = executePrivileged("settings put system screen_brightness " + targetBrightness);
            if (!isSuccessfulCommandOutput(setOutput)) {
                JSONObject error = jsonError("set_failed", setOutput);
                error.put("_statusCode", 500);
                return error;
            }
        }

        String readOutput = executePrivileged("settings get system screen_brightness");
        Integer currentBrightness = parseFirstInteger(readOutput);

        JSONObject data = new JSONObject();
        data.put("ok", currentBrightness != null);
        data.put("setRequested", targetBrightness != null);
        data.put("targetBrightness", targetBrightness != null ? targetBrightness : JSONObject.NULL);
        data.put("currentBrightness", currentBrightness != null ? currentBrightness : JSONObject.NULL);
        data.put("rangeMin", MIN_BRIGHTNESS);
        data.put("rangeMax", MAX_BRIGHTNESS);
        data.put("rawReadOutput", readOutput == null ? "" : readOutput.trim());
        if (targetBrightness != null) {
            data.put("rawSetOutput", setOutput == null ? "" : setOutput.trim());
        }
        try {
            data.put("canWriteSystemSettings", Settings.System.canWrite(context));
        } catch (Exception ignored) {
        }
        return data;
    }

    private JSONObject runVolume(Context context, String body) throws JSONException {
        JSONObject endpointGuard = ensurePrivilegedEndpointEnabled(context, PrivilegedPolicyStore.Endpoint.VOLUME, "/v1/system/volume");
        if (endpointGuard != null) return endpointGuard;
        JSONObject request = body != null && !body.isEmpty() ? new JSONObject(body) : new JSONObject();
        int stream = request.optInt("stream", DEFAULT_VOLUME_STREAM);
        Integer targetVolume = readOptionalInteger(request, "volume", "value");

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            JSONObject error = jsonError("unavailable", "AudioManager unavailable");
            error.put("_statusCode", 500);
            return error;
        }

        int minVolume = 0;
        int maxVolume;
        try {
            maxVolume = audioManager.getStreamMaxVolume(stream);
        } catch (Exception e) {
            JSONObject error = jsonError("bad_request", "Invalid stream type");
            error.put("_statusCode", 400);
            return error;
        }

        if (targetVolume != null && (targetVolume < minVolume || targetVolume > maxVolume)) {
            JSONObject error = jsonError("bad_request",
                "volume must be between " + minVolume + " and " + maxVolume + " for stream " + stream);
            error.put("_statusCode", 400);
            return error;
        }

        try {
            if (targetVolume != null) {
                audioManager.setStreamVolume(stream, targetVolume, 0);
            }
            int currentVolume = audioManager.getStreamVolume(stream);
            JSONObject data = new JSONObject();
            data.put("ok", true);
            data.put("setRequested", targetVolume != null);
            data.put("stream", stream);
            data.put("targetVolume", targetVolume != null ? targetVolume : JSONObject.NULL);
            data.put("currentVolume", currentVolume);
            data.put("rangeMin", minVolume);
            data.put("rangeMax", maxVolume);
            return data;
        } catch (SecurityException securityException) {
            JSONObject error = jsonError("forbidden", "Volume control requires MODIFY_AUDIO_SETTINGS permission");
            error.put("_statusCode", 403);
            return error;
        } catch (Exception e) {
            JSONObject error = jsonError("volume_failed", e.getMessage());
            error.put("_statusCode", 500);
            return error;
        }
    }
    private JSONObject ensurePrivilegedEndpointEnabled(Context context, PrivilegedPolicyStore.Endpoint endpoint, String endpointPath) throws JSONException {
        if (context == null) {
            JSONObject error = jsonError("unavailable", "Context unavailable for privileged policy check");
            error.put("_statusCode", 500);
            return error;
        }
        if (!PrivilegedPolicyStore.isMasterEnabled(context)) {
            JSONObject error = jsonError("forbidden", "Privileged features disabled by settings");
            error.put("_statusCode", 403);
            error.put("endpoint", endpointPath);
            return error;
        }
        if (!PrivilegedPolicyStore.isEndpointEnabled(context, endpoint)) {
            JSONObject error = jsonError("forbidden", "Endpoint disabled by privileged policy");
            error.put("_statusCode", 403);
            error.put("endpoint", endpointPath);
            return error;
        }
        return null;
    }

    private JSONObject describePrivilegedPolicy() throws JSONException {
        Context context = appContext;
        JSONObject info = new JSONObject();
        if (context == null) {
            info.put("available", false);
            return info;
        }

        info.put("available", true);
        info.put("masterEnabled", PrivilegedPolicyStore.isMasterEnabled(context));
        info.put("preferShizuku", PrivilegedPolicyStore.isPreferShizuku(context));
        info.put("allowShellFallback", PrivilegedPolicyStore.isShellFallbackEnabled(context));

        JSONObject endpoints = new JSONObject();
        endpoints.put("requestPermission", PrivilegedPolicyStore.isEndpointEnabled(context, PrivilegedPolicyStore.Endpoint.REQUEST_PERMISSION));
        endpoints.put("exec", PrivilegedPolicyStore.isEndpointEnabled(context, PrivilegedPolicyStore.Endpoint.EXEC));
        endpoints.put("brightness", PrivilegedPolicyStore.isEndpointEnabled(context, PrivilegedPolicyStore.Endpoint.BRIGHTNESS));
        endpoints.put("volume", PrivilegedPolicyStore.isEndpointEnabled(context, PrivilegedPolicyStore.Endpoint.VOLUME));
        endpoints.put("lockScreen", PrivilegedPolicyStore.isEndpointEnabled(context, PrivilegedPolicyStore.Endpoint.LOCK_SCREEN));
        info.put("endpoints", endpoints);

        return info;
    }
    private String executePrivileged(String command) {
        try {
            return PrivilegedBackendManager.getInstance().executeCommand(command).get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private boolean isAuthorized(Map<String, String> headers) {
        if (token == null || token.isEmpty()) return false;
        String value = headers.get("authorization");
        if (value == null) return false;
        String prefix = "Bearer ";
        if (!value.startsWith(prefix)) return false;
        return secureEquals(token, value.substring(prefix.length()).trim());
    }

    private boolean allowRequest(HttpRequest request) {
        SimpleRateLimiter limiter = rateLimiters.get(request.method + ":" + request.path);
        return limiter == null || limiter.allow();
    }

    private HttpRequest parseRequest(InputStream input) throws IOException, HttpParseException {
        String requestLine = readLine(input, MAX_REQUEST_LINE_BYTES);
        if (requestLine == null || requestLine.isEmpty()) {
            throw new HttpParseException(400, "bad_request", "Missing request line");
        }

        String[] lineParts = requestLine.split(" ");
        if (lineParts.length < 2) {
            throw new HttpParseException(400, "bad_request", "Malformed request line");
        }

        HttpRequest request = new HttpRequest();
        request.method = lineParts[0].trim();
        request.path = lineParts[1].trim();
        request.headers = new HashMap<>();

        int headerCount = 0;
        String line;
        while ((line = readLine(input, MAX_HEADER_LINE_BYTES)) != null) {
            if (line.isEmpty()) break;
            headerCount++;
            if (headerCount > MAX_HEADER_LINES) {
                throw new HttpParseException(400, "bad_request", "Too many headers");
            }
            int index = line.indexOf(':');
            if (index <= 0) continue;
            String key = line.substring(0, index).trim().toLowerCase();
            String value = line.substring(index + 1).trim();
            request.headers.put(key, value);
        }

        int contentLength = 0;
        try {
            String value = request.headers.get("content-length");
            if (value != null) contentLength = Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
        }

        if (contentLength < 0) {
            throw new HttpParseException(400, "bad_request", "Invalid content length");
        }
        if (contentLength > MAX_BODY_BYTES) {
            throw new HttpParseException(413, "payload_too_large", "Request body too large");
        }

        if (contentLength > 0) {
            byte[] bodyBytes = readBytes(input, contentLength);
            if (bodyBytes.length != contentLength) {
                throw new HttpParseException(400, "bad_request", "Incomplete request body");
            }
            request.body = new String(bodyBytes, StandardCharsets.UTF_8);
        } else {
            request.body = "";
        }

        return request;
    }

    private String readLine(InputStream input, int maxBytes) throws IOException, HttpParseException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int prev = -1;
        while (true) {
            int b = input.read();
            if (b == -1) break;
            if (b == '\n') break;
            if (prev == '\r') {
                buffer.write('\r');
            }
            if (b != '\r') {
                buffer.write(b);
            }
            if (buffer.size() > maxBytes) {
                throw new HttpParseException(413, "payload_too_large", "Header line too large");
            }
            prev = b;
        }
        if (buffer.size() == 0 && prev == -1) return null;
        return buffer.toString(StandardCharsets.UTF_8.name()).trim();
    }

    private byte[] readBytes(InputStream input, int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(data, offset, length - offset);
            if (read < 0) break;
            offset += read;
        }
        if (offset == length) return data;
        byte[] trimmed = new byte[offset];
        System.arraycopy(data, 0, trimmed, 0, offset);
        return trimmed;
    }

    private void writeResponse(BufferedWriter writer, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        writer.write("HTTP/1.1 " + statusCode + " " + statusMessage(statusCode) + "\r\n");
        writer.write("Content-Type: application/json; charset=utf-8\r\n");
        writer.write("Connection: close\r\n");
        writer.write("Content-Length: " + bytes.length + "\r\n");
        writer.write("\r\n");
        writer.write(body);
        writer.flush();
    }

    private String statusMessage(int code) {
        switch (code) {
            case 200: return "OK";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 413: return "Payload Too Large";
            case 429: return "Too Many Requests";
            default: return "Internal Server Error";
        }
    }

    private JSONObject jsonError(String code, String message) {
        JSONObject error = new JSONObject();
        try {
            error.put("ok", false);
            error.put("error", code);
            error.put("message", message == null ? "" : message);
        } catch (JSONException ignored) {
        }
        return error;
    }

    private String generateToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        StringBuilder tokenBuilder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            tokenBuilder.append(String.format("%02x", b & 0xff));
        }
        return tokenBuilder.toString();
    }

    private void initializeRateLimiters() {
        rateLimiters.clear();
        rateLimiters.put("GET:/v1/status", new SimpleRateLimiter(120, 60_000));
        rateLimiters.put("GET:/v1/apps", new SimpleRateLimiter(60, 60_000));
        rateLimiters.put("GET:/v1/system/resources", new SimpleRateLimiter(120, 60_000));
        rateLimiters.put("GET:/v1/media/now-playing", new SimpleRateLimiter(120, 60_000));
        rateLimiters.put("GET:/v1/media/art", new SimpleRateLimiter(60, 60_000));
        rateLimiters.put("GET:/v1/notifications", new SimpleRateLimiter(120, 60_000));
        rateLimiters.put("POST:/v1/exec", new SimpleRateLimiter(30, 60_000));
        rateLimiters.put("POST:/v1/system/brightness", new SimpleRateLimiter(30, 60_000));
        rateLimiters.put("POST:/v1/system/volume", new SimpleRateLimiter(30, 60_000));
        rateLimiters.put("POST:/v1/screen/lock", new SimpleRateLimiter(20, 60_000));
        rateLimiters.put("POST:/v1/auth/rotate", new SimpleRateLimiter(5, 60_000));
    }

    private void writeClientConfig() throws IOException {
        File tooieDir = new File(TOOIE_DIR_PATH);
        if (!tooieDir.exists() && !tooieDir.mkdirs()) {
            throw new IOException("Failed to create tooie dir: " + TOOIE_DIR_PATH);
        }
        writeTextFile(TOKEN_FILE_PATH, token + "\n");
        writeTextFile(ENDPOINT_FILE_PATH, "http://127.0.0.1:" + port + "\n");
        ensureDefaultConfigFile();
    }

    private void installTooieCliScript() {
        String script =
            "#!/data/data/com.termux/files/usr/bin/sh\n" +
            "set -eu\n" +
            "TOOIE_DIR=\"$HOME/.tooie\"\n" +
            "TOKEN_FILE=\"$TOOIE_DIR/token\"\n" +
            "ENDPOINT_FILE=\"$TOOIE_DIR/endpoint\"\n" +
            "if [ ! -r \"$TOKEN_FILE\" ] || [ ! -r \"$ENDPOINT_FILE\" ]; then\n" +
            "  echo \"tooie: missing $TOKEN_FILE or $ENDPOINT_FILE\" >&2\n" +
            "  exit 1\n" +
            "fi\n" +
            "TOKEN=$(cat \"$TOKEN_FILE\")\n" +
            "BASE=$(cat \"$ENDPOINT_FILE\")\n" +
            "CURL_COMMON=\"-fsS --connect-timeout 2 --max-time 10\"\n" +
            "cmd=\"${1:-status}\"\n" +
            "shift || true\n" +
            "json_escape() { printf '%s' \"$1\" | sed 's/\\\\/\\\\\\\\/g; s/\"/\\\\\"/g'; }\n" +
            "case \"$cmd\" in\n" +
            "  status)\n" +
            "    curl $CURL_COMMON -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/status\"\n" +
            "    ;;\n" +
            "  apps)\n" +
            "    curl $CURL_COMMON -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/apps\"\n" +
            "    ;;\n" +
            "  resources)\n" +
            "    curl $CURL_COMMON -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/system/resources\"\n" +
            "    ;;\n" +
            "  media)\n" +
            "    curl $CURL_COMMON -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/media/now-playing\"\n" +
            "    ;;\n" +
            "  art)\n" +
            "    curl $CURL_COMMON -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/media/art\"\n" +
            "    ;;\n" +
            "  notifications)\n" +
            "    curl $CURL_COMMON -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/notifications\"\n" +
            "    ;;\n" +
            "  brightness)\n" +
            "    if [ \"$#\" -gt 0 ]; then\n" +
            "      curl $CURL_COMMON -X POST -H \"Authorization: Bearer $TOKEN\" -H \"Content-Type: application/json\" \\\n" +
            "        --data \"{\\\"brightness\\\":$1}\" \"$BASE/v1/system/brightness\"\n" +
            "    else\n" +
            "      curl $CURL_COMMON -X POST -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/system/brightness\"\n" +
            "    fi\n" +
            "    ;;\n" +
            "  volume)\n" +
            "    if [ \"$#\" -gt 1 ]; then\n" +
            "      curl $CURL_COMMON -X POST -H \"Authorization: Bearer $TOKEN\" -H \"Content-Type: application/json\" \\\n" +
            "        --data \"{\\\"volume\\\":$1,\\\"stream\\\":$2}\" \"$BASE/v1/system/volume\"\n" +
            "    elif [ \"$#\" -gt 0 ]; then\n" +
            "      curl $CURL_COMMON -X POST -H \"Authorization: Bearer $TOKEN\" -H \"Content-Type: application/json\" \\\n" +
            "        --data \"{\\\"volume\\\":$1}\" \"$BASE/v1/system/volume\"\n" +
            "    else\n" +
            "      curl $CURL_COMMON -X POST -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/system/volume\"\n" +
            "    fi\n" +
            "    ;;\n" +
            "  exec)\n" +
            "    [ \"$#\" -gt 0 ] || { echo \"usage: tooie exec <command>\" >&2; exit 2; }\n" +
            "    CMD_ESCAPED=$(json_escape \"$*\")\n" +
            "    curl $CURL_COMMON -X POST -H \"Authorization: Bearer $TOKEN\" -H \"Content-Type: application/json\" \\\n" +
            "      --data \"{\\\"command\\\":\\\"$CMD_ESCAPED\\\"}\" \"$BASE/v1/exec\"\n" +
            "    ;;\n" +
            "  permission)\n" +
            "    curl $CURL_COMMON -X POST -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/privileged/request-permission\"\n" +
            "    ;;\n" +
            "  lock)\n" +
            "    curl $CURL_COMMON -X POST -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/screen/lock\"\n" +
            "    ;;\n" +
            "  token)\n" +
            "    sub=\"${1:-}\"; shift || true\n" +
            "    [ \"$sub\" = \"rotate\" ] || { echo \"usage: tooie token rotate\" >&2; exit 2; }\n" +
            "    curl $CURL_COMMON -X POST -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/auth/rotate\"\n" +
            "    ;;\n" +
            "  *)\n" +
            "    echo \"usage: tooie {status|apps|resources|media|art|notifications|brightness [value]|volume [value] [stream]|exec|permission|lock|token rotate}\" >&2\n" +
            "    exit 2\n" +
            "    ;;\n" +
            "esac\n";

        try {
            writeTextFile(TOOIE_BIN_PATH, script);
            File tooieBin = new File(TOOIE_BIN_PATH);
            if (tooieBin.exists()) {
                tooieBin.setExecutable(true, false);
                tooieBin.setReadable(true, false);
            }
        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, "Failed to install tooie cli: " + e.getMessage());
        }
    }

    private void writeTextFile(String path, String content) throws IOException {
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create dir for " + path);
        }
        try (FileOutputStream stream = new FileOutputStream(file, false)) {
            stream.write(content.getBytes(StandardCharsets.UTF_8));
        }
        file.setReadable(false, false);
        file.setWritable(false, false);
        file.setReadable(true, true);
        file.setWritable(true, true);
    }

    private void ensureDefaultConfigFile() {
        File file = new File(CONFIG_FILE_PATH);
        if (file.exists()) {
            return;
        }

        JSONObject defaultConfig = new JSONObject();
        try {
            defaultConfig.put("execEnabled", false);
            JSONArray prefixes = new JSONArray();
            prefixes.put("id");
            prefixes.put("pm list packages");
            prefixes.put("cmd package list packages");
            defaultConfig.put("allowedCommandPrefixes", prefixes);
            writeTextFile(CONFIG_FILE_PATH, defaultConfig.toString(2) + "\n");
        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, "Failed to write default Tooie config: " + e.getMessage());
        }
    }

    private ExecPolicy loadExecPolicy() {
        ExecPolicy policy = new ExecPolicy();
        policy.execEnabled = false;
        policy.allowedCommandPrefixes = new ArrayList<>();
        policy.allowedCommandPrefixes.add("id");
        policy.allowedCommandPrefixes.add("pm list packages");
        policy.allowedCommandPrefixes.add("cmd package list packages");

        File configFile = new File(CONFIG_FILE_PATH);
        if (!configFile.exists()) {
            return policy;
        }
        try {
            byte[] bytes = readAllBytes(configFile);
            JSONObject config = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            policy.execEnabled = config.optBoolean("execEnabled", policy.execEnabled);
            JSONArray allowed = config.optJSONArray("allowedCommandPrefixes");
            if (allowed != null) {
                List<String> prefixes = new ArrayList<>();
                for (int i = 0; i < allowed.length(); i++) {
                    String entry = allowed.optString(i, "").trim();
                    if (!entry.isEmpty()) {
                        prefixes.add(entry);
                    }
                }
                if (!prefixes.isEmpty()) {
                    policy.allowedCommandPrefixes = prefixes;
                }
            }
        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, "Failed to parse Tooie config, using defaults: " + e.getMessage());
        }
        return policy;
    }

    private JSONObject describeExecPolicy() throws JSONException {
        ExecPolicy policy = loadExecPolicy();
        JSONObject info = new JSONObject();
        info.put("enabled", policy.execEnabled);
        JSONArray prefixes = new JSONArray();
        for (String prefix : policy.allowedCommandPrefixes) {
            prefixes.put(prefix);
        }
        info.put("allowedCommandPrefixes", prefixes);
        return info;
    }

    private byte[] readAllBytes(File file) throws IOException {
        try (InputStream stream = new FileInputStream(file)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private Map<String, Long> readMemInfoKb() {
        Map<String, Long> values = new HashMap<>();
        try {
            String content = new String(readAllBytes(new File("/proc/meminfo")), StandardCharsets.UTF_8);
            String[] lines = content.split("\n");
            for (String line : lines) {
                int colon = line.indexOf(':');
                if (colon <= 0) continue;
                String key = line.substring(0, colon).trim();
                String valuePart = line.substring(colon + 1).trim();
                if (valuePart.isEmpty()) continue;
                String[] parts = valuePart.split("\\s+");
                if (parts.length == 0) continue;
                try {
                    values.put(key, Long.parseLong(parts[0]));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return values;
    }

    private double[] readLoadAverage() {
        try {
            String content = new String(readAllBytes(new File("/proc/loadavg")), StandardCharsets.UTF_8).trim();
            if (content.isEmpty()) return null;
            String[] parts = content.split("\\s+");
            if (parts.length < 3) return null;
            return new double[] {
                Double.parseDouble(parts[0]),
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]),
            };
        } catch (Exception ignored) {
            return null;
        }
    }

    private double readCPUPercent(double[] loadAverage, int cpuCores) {
        long[] sample = readProcStatCpuTicks();
        if (sample != null) {
            long total = sample[0];
            long idle = sample[1];
            long now = System.currentTimeMillis();
            synchronized (this) {
                if (lastCpuTotalTicks > 0 && total > lastCpuTotalTicks && now > lastCpuSampleMs) {
                    long totalDelta = total - lastCpuTotalTicks;
                    long idleDelta = idle - lastCpuIdleTicks;
                    if (totalDelta > 0) {
                        double percent = 100.0 * (1.0 - ((double) idleDelta / (double) totalDelta));
                        lastCpuTotalTicks = total;
                        lastCpuIdleTicks = idle;
                        lastCpuSampleMs = now;
                        return clampPercent(percent);
                    }
                }
                lastCpuTotalTicks = total;
                lastCpuIdleTicks = idle;
                lastCpuSampleMs = now;
            }

            // First sample has no delta yet. Take a short second sample to compute cpuPercent.
            try {
                Thread.sleep(120);
            } catch (InterruptedException ignored) {
            }
            long[] second = readProcStatCpuTicks();
            if (second != null) {
                long total2 = second[0];
                long idle2 = second[1];
                long totalDelta = total2 - total;
                long idleDelta = idle2 - idle;
                if (totalDelta > 0) {
                    synchronized (this) {
                        lastCpuTotalTicks = total2;
                        lastCpuIdleTicks = idle2;
                        lastCpuSampleMs = System.currentTimeMillis();
                    }
                    double percent = 100.0 * (1.0 - ((double) idleDelta / (double) totalDelta));
                    return clampPercent(percent);
                }
            }
        }

        // Fallback to load average based approximation.
        if (loadAverage != null && loadAverage.length >= 1 && cpuCores > 0) {
            return clampPercent((loadAverage[0] / cpuCores) * 100.0);
        }
        return -1;
    }

    private long[] readProcStatCpuTicks() {
        long[] direct = readProcStatCpuTicksFromContent(readFileAsString("/proc/stat"));
        if (direct != null) {
            return direct;
        }

        // Fallback through privileged backend if direct procfs read is unavailable.
        String privileged = executePrivileged("cat /proc/stat");
        return readProcStatCpuTicksFromContent(privileged);
    }

    private long[] readProcStatCpuTicksFromContent(String content) {
        if (content == null || content.isEmpty()) return null;
        try {
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (!line.startsWith("cpu ")) continue;
                String[] fields = line.trim().split("\\s+");
                if (fields.length < 5) return null;
                long total = 0L;
                for (int i = 1; i < fields.length; i++) {
                    try {
                        total += Long.parseLong(fields[i]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                long idle = Long.parseLong(fields[4]);
                if (fields.length > 5) {
                    try {
                        idle += Long.parseLong(fields[5]); // iowait as idle-like time
                    } catch (NumberFormatException ignored) {
                    }
                }
                return new long[] {total, idle};
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String readFileAsString(String path) {
        try {
            return new String(readAllBytes(new File(path)), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }
    }

    private JSONObject readUptimeInfo() {
        JSONObject uptime = new JSONObject();
        try {
            uptime.put("processUptimeMs", SystemClock.elapsedRealtime());
            uptime.put("processUptimeSec", SystemClock.elapsedRealtime() / 1000.0);
            String content = new String(readAllBytes(new File("/proc/uptime")), StandardCharsets.UTF_8).trim();
            String[] parts = content.split("\\s+");
            if (parts.length >= 1) {
                double uptimeSec = Double.parseDouble(parts[0]);
                uptime.put("systemUptimeSec", uptimeSec);
                uptime.put("systemUptimeMs", (long) (uptimeSec * 1000.0));
            }
        } catch (Exception ignored) {
        }
        return uptime;
    }

    private JSONArray readStorageStats(Context context) {
        JSONArray storage = new JSONArray();
        addStoragePath(storage, "root", "/");
        addStoragePath(storage, "data", "/data");

        File filesDir = context.getFilesDir();
        if (filesDir != null) {
            addStoragePath(storage, "appFiles", filesDir.getAbsolutePath());
        }
        File cacheDir = context.getCacheDir();
        if (cacheDir != null) {
            addStoragePath(storage, "appCache", cacheDir.getAbsolutePath());
        }
        File extDir = context.getExternalFilesDir(null);
        if (extDir != null) {
            addStoragePath(storage, "externalFiles", extDir.getAbsolutePath());
        }
        addStoragePath(storage, "shared", "/storage/emulated/0");
        return storage;
    }

    private void addStoragePath(JSONArray storage, String label, String path) {
        try {
            File file = new File(path);
            if (!file.exists()) return;
            StatFs statFs = new StatFs(path);
            long total = statFs.getTotalBytes();
            long free = statFs.getFreeBytes();
            long available = statFs.getAvailableBytes();
            long used = total > free ? (total - free) : 0L;
            JSONObject item = new JSONObject();
            item.put("label", label);
            item.put("path", path);
            item.put("totalBytes", total);
            item.put("freeBytes", free);
            item.put("availableBytes", available);
            item.put("usedBytes", used);
            storage.put(item);
        } catch (Exception ignored) {
        }
    }

    private JSONObject readBatteryInfo(Context context) {
        JSONObject battery = new JSONObject();
        try {
            Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (intent == null) return battery;
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
            int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            int tempTenthsC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            int voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);

            if (level >= 0 && scale > 0) {
                battery.put("levelPercent", (level * 100.0) / scale);
                battery.put("level", level);
                battery.put("scale", scale);
            }
            battery.put("status", batteryStatusToString(status));
            battery.put("health", batteryHealthToString(health));
            battery.put("charging", status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
            battery.put("plugged", plugged != 0);
            battery.put("plugType", batteryPlugToString(plugged));
            if (tempTenthsC != Integer.MIN_VALUE) {
                battery.put("temperatureC", tempTenthsC / 10.0);
            }
            if (voltageMv >= 0) {
                battery.put("voltageMv", voltageMv);
            }
        } catch (Exception ignored) {
        }
        return battery;
    }

    private JSONArray readNetworkStats() {
        JSONArray network = new JSONArray();
        try {
            String content = new String(readAllBytes(new File("/proc/net/dev")), StandardCharsets.UTF_8);
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (!line.contains(":")) continue;
                String[] split = line.split(":");
                if (split.length != 2) continue;
                String iface = split[0].trim();
                if (iface.isEmpty()) continue;
                String[] fields = split[1].trim().split("\\s+");
                if (fields.length < 16) continue;
                JSONObject item = new JSONObject();
                item.put("interface", iface);
                item.put("rxBytes", parseLongSafe(fields[0]));
                item.put("rxPackets", parseLongSafe(fields[1]));
                item.put("rxErrors", parseLongSafe(fields[2]));
                item.put("rxDropped", parseLongSafe(fields[3]));
                item.put("txBytes", parseLongSafe(fields[8]));
                item.put("txPackets", parseLongSafe(fields[9]));
                item.put("txErrors", parseLongSafe(fields[10]));
                item.put("txDropped", parseLongSafe(fields[11]));
                network.put(item);
            }
        } catch (Exception ignored) {
        }
        return network;
    }

    private JSONArray readThermalZones() {
        JSONArray thermal = new JSONArray();
        try {
            File root = new File("/sys/class/thermal");
            File[] zones = root.listFiles((dir, name) -> name != null && name.startsWith("thermal_zone"));
            if (zones == null || zones.length == 0) return thermal;
            Arrays.sort(zones, (a, b) -> a.getName().compareTo(b.getName()));
            int limit = Math.min(zones.length, 24);
            for (int i = 0; i < limit; i++) {
                File zone = zones[i];
                String type = readSingleLine(new File(zone, "type"));
                String tempRaw = readSingleLine(new File(zone, "temp"));
                if (tempRaw == null || tempRaw.isEmpty()) continue;
                long temp = parseLongSafe(tempRaw);
                // Most Android kernels expose millidegree C.
                double tempC = temp > 1000 ? (temp / 1000.0) : (double) temp;
                JSONObject item = new JSONObject();
                item.put("zone", zone.getName());
                item.put("type", type == null ? "" : type);
                item.put("tempC", tempC);
                thermal.put(item);
            }
        } catch (Exception ignored) {
        }
        return thermal;
    }

    private void putMemInfoBytes(JSONObject json, String fieldName, Map<String, Long> memInfoKb, String key) throws JSONException {
        Long valueKb = memInfoKb.get(key);
        if (valueKb != null && valueKb >= 0) {
            json.put(fieldName, valueKb * 1024L);
        }
    }

    private String readSingleLine(File file) {
        try {
            String text = new String(readAllBytes(file), StandardCharsets.UTF_8);
            int newline = text.indexOf('\n');
            if (newline >= 0) {
                text = text.substring(0, newline);
            }
            return text.trim();
        } catch (Exception ignored) {
            return null;
        }
    }

    private long parseLongSafe(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private double clampPercent(double value) {
        if (value < 0) return 0;
        if (value > 100) return 100;
        return value;
    }

    private String batteryStatusToString(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                return "charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                return "discharging";
            case BatteryManager.BATTERY_STATUS_FULL:
                return "full";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                return "not_charging";
            case BatteryManager.BATTERY_STATUS_UNKNOWN:
            default:
                return "unknown";
        }
    }

    private String batteryHealthToString(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD:
                return "good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                return "overheat";
            case BatteryManager.BATTERY_HEALTH_DEAD:
                return "dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                return "over_voltage";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                return "unspecified_failure";
            case BatteryManager.BATTERY_HEALTH_COLD:
                return "cold";
            case BatteryManager.BATTERY_HEALTH_UNKNOWN:
            default:
                return "unknown";
        }
    }

    private String batteryPlugToString(int plugged) {
        switch (plugged) {
            case BatteryManager.BATTERY_PLUGGED_AC:
                return "ac";
            case BatteryManager.BATTERY_PLUGGED_USB:
                return "usb";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                return "wireless";
            default:
                return "none";
        }
    }

    private boolean isCommandAllowed(String command, List<String> allowedPrefixes) {
        if (allowedPrefixes == null || allowedPrefixes.isEmpty()) return false;
        for (String prefix : allowedPrefixes) {
            if (command.equals(prefix) || command.startsWith(prefix + " ")) {
                return true;
            }
        }
        return false;
    }

    private boolean containsControlChars(String command) {
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c < 32 && c != '\t') {
                return true;
            }
        }
        return false;
    }

    private boolean secureEquals(String expected, String actual) {
        byte[] e = expected.getBytes(StandardCharsets.UTF_8);
        byte[] a = actual.getBytes(StandardCharsets.UTF_8);
        if (e.length != a.length) return false;
        int result = 0;
        for (int i = 0; i < e.length; i++) {
            result |= (e[i] ^ a[i]);
        }
        return result == 0;
    }

    private boolean isSuccessfulCommandOutput(String output) {
        if (output == null) return false;
        String trimmed = output.trim();
        if (trimmed.isEmpty()) return true;
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("error")) return false;
        if (lower.contains("permission required")) return false;
        if (lower.contains("no privileged backend")) return false;
        return true;
    }

    private Integer readOptionalInteger(JSONObject json, String... keys) {
        if (json == null || keys == null) return null;
        for (String key : keys) {
            if (key == null || key.isEmpty() || !json.has(key) || json.isNull(key)) {
                continue;
            }
            Object value = json.opt(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) return null;
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer parseFirstInteger(String text) {
        if (text == null) return null;
        Matcher matcher = Pattern.compile("-?\\d+").matcher(text);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void cleanupSocket() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            } finally {
                serverSocket = null;
            }
        }
    }

    private void closeQuietly(Socket socket) {
        if (socket == null) return;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private static class HttpRequest {
        String method;
        String path;
        Map<String, String> headers;
        String body;
    }

    private static class HttpParseException extends Exception {
        final int statusCode;
        final String errorCode;

        HttpParseException(int statusCode, String errorCode, String message) {
            super(message);
            this.statusCode = statusCode;
            this.errorCode = errorCode;
        }
    }

    private static class SimpleRateLimiter {
        private final int maxRequests;
        private final long windowMs;
        private final Deque<Long> timestamps = new ArrayDeque<>();

        SimpleRateLimiter(int maxRequests, long windowMs) {
            this.maxRequests = maxRequests;
            this.windowMs = windowMs;
        }

        synchronized boolean allow() {
            long now = System.currentTimeMillis();
            while (!timestamps.isEmpty() && (now - timestamps.peekFirst()) > windowMs) {
                timestamps.removeFirst();
            }
            if (timestamps.size() >= maxRequests) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    private static class ExecPolicy {
        boolean execEnabled;
        List<String> allowedCommandPrefixes;
    }
}
