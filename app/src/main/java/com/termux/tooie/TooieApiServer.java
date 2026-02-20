package com.termux.tooie;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.termux.privileged.PrivilegedBackend;
import com.termux.privileged.PrivilegedBackendManager;
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
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
            } else if ("GET".equals(request.method) && "/v1/media/now-playing".equals(request.path)) {
                return buildNowPlaying();
            } else if ("GET".equals(request.method) && "/v1/notifications".equals(request.path)) {
                return buildNotifications();
            } else if ("POST".equals(request.method) && "/v1/exec".equals(request.path)) {
                return runExec(request.body);
            } else if ("POST".equals(request.method) && "/v1/privileged/request-permission".equals(request.path)) {
                return requestPrivilegedPermission();
            } else if ("POST".equals(request.method) && "/v1/screen/lock".equals(request.path)) {
                return runLockScreen();
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

    private JSONObject buildNowPlaying() throws JSONException {
        JSONObject snapshot = TooieNotificationListener.getNowPlayingSnapshot();
        snapshot.put("ok", true);
        return snapshot;
    }

    private JSONObject buildNotifications() throws JSONException {
        JSONObject snapshot = TooieNotificationListener.getNotificationsSnapshot();
        snapshot.put("ok", true);
        return snapshot;
    }

    private JSONObject runExec(String body) throws JSONException {
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

    private JSONObject requestPrivilegedPermission() throws JSONException {
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

    private JSONObject runLockScreen() throws JSONException {
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
        rateLimiters.put("GET:/v1/media/now-playing", new SimpleRateLimiter(120, 60_000));
        rateLimiters.put("GET:/v1/notifications", new SimpleRateLimiter(120, 60_000));
        rateLimiters.put("POST:/v1/exec", new SimpleRateLimiter(30, 60_000));
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
            "  media)\n" +
            "    curl $CURL_COMMON -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/media/now-playing\"\n" +
            "    ;;\n" +
            "  notifications)\n" +
            "    curl $CURL_COMMON -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/notifications\"\n" +
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
            "    echo \"usage: tooie {status|apps|media|notifications|exec|permission|lock|token rotate}\" >&2\n" +
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
