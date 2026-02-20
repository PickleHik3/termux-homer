package com.termux.tooie;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.termux.privileged.PrivilegedBackendManager;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private static final String TOOIE_BIN_PATH = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/tooie";

    private static TooieApiServer instance;

    private final ExecutorService clientExecutor = Executors.newCachedThreadPool();
    private final SecureRandom random = new SecureRandom();

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
                    clientExecutor.submit(() -> handleClient(client, context));
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

            HttpRequest request = parseRequest(input);
            if (request == null) {
                writeResponse(writer, 400, jsonError("bad_request", "Invalid request").toString());
                return;
            }

            if (!isAuthorized(request.headers)) {
                writeResponse(writer, 401, jsonError("unauthorized", "Missing or invalid token").toString());
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
            } else if ("POST".equals(request.method) && "/v1/screen/lock".equals(request.path)) {
                return runLockScreen();
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

        String output;
        try {
            output = PrivilegedBackendManager.getInstance()
                .executeCommand(command)
                .get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            JSONObject error = jsonError("exec_failed", e.getMessage());
            error.put("_statusCode", 500);
            return error;
        }

        JSONObject data = new JSONObject();
        data.put("ok", output != null && !output.startsWith("Error"));
        data.put("command", command);
        data.put("output", output == null ? "" : output);
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
        data.put("ok", output != null && !output.startsWith("Error"));
        data.put("command", used);
        data.put("output", output == null ? "" : output);
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
        return token.equals(value.substring(prefix.length()).trim());
    }

    private HttpRequest parseRequest(InputStream input) throws IOException {
        String requestLine = readLine(input);
        if (requestLine == null || requestLine.isEmpty()) return null;

        String[] lineParts = requestLine.split(" ");
        if (lineParts.length < 2) return null;

        HttpRequest request = new HttpRequest();
        request.method = lineParts[0].trim();
        request.path = lineParts[1].trim();
        request.headers = new HashMap<>();

        String line;
        while ((line = readLine(input)) != null) {
            if (line.isEmpty()) break;
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

        if (contentLength > 0) {
            byte[] bodyBytes = readBytes(input, contentLength);
            request.body = new String(bodyBytes, StandardCharsets.UTF_8);
        } else {
            request.body = "";
        }

        return request;
    }

    private String readLine(InputStream input) throws IOException {
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
            case 404: return "Not Found";
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

    private void writeClientConfig() throws IOException {
        File tooieDir = new File(TOOIE_DIR_PATH);
        if (!tooieDir.exists() && !tooieDir.mkdirs()) {
            throw new IOException("Failed to create tooie dir: " + TOOIE_DIR_PATH);
        }
        writeTextFile(TOKEN_FILE_PATH, token + "\n");
        writeTextFile(ENDPOINT_FILE_PATH, "http://127.0.0.1:" + port + "\n");
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
            "cmd=\"${1:-status}\"\n" +
            "shift || true\n" +
            "json_escape() { printf '%s' \"$1\" | sed 's/\\\\/\\\\\\\\/g; s/\"/\\\\\"/g'; }\n" +
            "case \"$cmd\" in\n" +
            "  status)\n" +
            "    curl -fsS -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/status\"\n" +
            "    ;;\n" +
            "  apps)\n" +
            "    curl -fsS -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/apps\"\n" +
            "    ;;\n" +
            "  media)\n" +
            "    curl -fsS -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/media/now-playing\"\n" +
            "    ;;\n" +
            "  notifications)\n" +
            "    curl -fsS -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/notifications\"\n" +
            "    ;;\n" +
            "  exec)\n" +
            "    [ \"$#\" -gt 0 ] || { echo \"usage: tooie exec <command>\" >&2; exit 2; }\n" +
            "    CMD_ESCAPED=$(json_escape \"$*\")\n" +
            "    curl -fsS -X POST -H \"Authorization: Bearer $TOKEN\" -H \"Content-Type: application/json\" \\\n" +
            "      --data \"{\\\"command\\\":\\\"$CMD_ESCAPED\\\"}\" \"$BASE/v1/exec\"\n" +
            "    ;;\n" +
            "  lock)\n" +
            "    curl -fsS -X POST -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/screen/lock\"\n" +
            "    ;;\n" +
            "  *)\n" +
            "    echo \"usage: tooie {status|apps|media|notifications|exec|lock}\" >&2\n" +
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

    private static class HttpRequest {
        String method;
        String path;
        Map<String, String> headers;
        String body;
    }
}
