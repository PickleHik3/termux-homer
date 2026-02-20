package com.termux.tooie;

import android.content.ComponentName;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.termux.shared.logger.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Captures live notification and media-session state for Tooie local API endpoints.
 */
public class TooieNotificationListener extends NotificationListenerService {
    private static final String LOG_TAG = "TooieNotifListener";
    private static final ConcurrentHashMap<String, JSONObject> NOTIFICATIONS = new ConcurrentHashMap<>();

    private static volatile boolean listenerConnected;
    private static volatile JSONObject nowPlaying;

    @Override
    public void onListenerConnected() {
        listenerConnected = true;
        Logger.logInfo(LOG_TAG, "Notification listener connected");
        rebuildNotificationsSnapshot();
        refreshNowPlaying();
    }

    @Override
    public void onListenerDisconnected() {
        listenerConnected = false;
        Logger.logWarn(LOG_TAG, "Notification listener disconnected");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        updateNotification(sbn);
        refreshNowPlaying();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null) {
            NOTIFICATIONS.remove(sbn.getKey());
        }
        refreshNowPlaying();
    }

    public static boolean isListenerConnected() {
        return listenerConnected;
    }

    public static JSONObject getNotificationsSnapshot() {
        JSONObject data = new JSONObject();
        try {
            JSONArray notifications = new JSONArray();
            List<JSONObject> entries = new ArrayList<>(NOTIFICATIONS.values());
            entries.sort((a, b) -> Long.compare(b.optLong("postTime", 0), a.optLong("postTime", 0)));
            for (JSONObject notification : entries) {
                notifications.put(new JSONObject(notification.toString()));
            }
            data.put("listenerConnected", listenerConnected);
            data.put("count", notifications.length());
            data.put("notifications", notifications);
            if (!listenerConnected) {
                data.put("hint", "Enable notification access for Termux:Monet to populate notifications and media endpoints.");
            }
        } catch (JSONException ignored) {
        }
        return data;
    }

    public static JSONObject getNowPlayingSnapshot() {
        JSONObject data = new JSONObject();
        try {
            data.put("listenerConnected", listenerConnected);
            if (nowPlaying != null) {
                data.put("nowPlaying", new JSONObject(nowPlaying.toString()));
            } else {
                data.put("nowPlaying", JSONObject.NULL);
            }
            if (!listenerConnected) {
                data.put("hint", "Enable notification access for Termux:Monet to populate notifications and media endpoints.");
            }
        } catch (JSONException ignored) {
        }
        return data;
    }

    private void rebuildNotificationsSnapshot() {
        try {
            StatusBarNotification[] active = getActiveNotifications();
            NOTIFICATIONS.clear();
            if (active == null) {
                return;
            }
            for (StatusBarNotification sbn : active) {
                updateNotification(sbn);
            }
        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, "Failed to rebuild notification snapshot: " + e.getMessage());
        }
    }

    private void updateNotification(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) {
            return;
        }
        try {
            NOTIFICATIONS.put(sbn.getKey(), toNotificationJson(sbn));
        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, "Failed to parse notification: " + e.getMessage());
        }
    }

    private JSONObject toNotificationJson(StatusBarNotification sbn) throws JSONException {
        JSONObject data = new JSONObject();
        Bundle extras = sbn.getNotification().extras;
        data.put("key", sbn.getKey());
        data.put("packageName", sbn.getPackageName());
        data.put("id", sbn.getId());
        data.put("tag", sbn.getTag() == null ? JSONObject.NULL : sbn.getTag());
        data.put("postTime", sbn.getPostTime());
        data.put("isOngoing", sbn.isOngoing());
        data.put("isClearable", sbn.isClearable());
        data.put("category", sbn.getNotification().category == null ? JSONObject.NULL : sbn.getNotification().category);
        data.put("title", toStringOrNull(extras, "android.title"));
        data.put("text", toStringOrNull(extras, "android.text"));
        data.put("subText", toStringOrNull(extras, "android.subText"));
        data.put("bigText", toStringOrNull(extras, "android.bigText"));
        return data;
    }

    private String toStringOrNull(Bundle extras, String key) {
        if (extras == null) return null;
        CharSequence value = extras.getCharSequence(key);
        return value == null ? null : value.toString();
    }

    private void refreshNowPlaying() {
        JSONObject current = null;
        try {
            MediaSessionManager mediaSessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            if (mediaSessionManager != null) {
                List<MediaController> sessions =
                    mediaSessionManager.getActiveSessions(new ComponentName(this, TooieNotificationListener.class));
                MediaController selected = selectController(sessions);
                if (selected != null) {
                    current = toNowPlayingJson(selected);
                }
            }
        } catch (SecurityException e) {
            Logger.logWarn(LOG_TAG, "Media sessions unavailable without notification listener access");
        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, "Failed to refresh media sessions: " + e.getMessage());
        }
        nowPlaying = current;
    }

    private MediaController selectController(List<MediaController> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return null;
        }
        for (MediaController controller : sessions) {
            PlaybackState state = controller.getPlaybackState();
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                return controller;
            }
        }
        return sessions.get(0);
    }

    private JSONObject toNowPlayingJson(MediaController controller) throws JSONException {
        JSONObject data = new JSONObject();
        PlaybackState state = controller.getPlaybackState();
        MediaMetadata metadata = controller.getMetadata();

        data.put("packageName", controller.getPackageName());
        data.put("sessionTag", controller.getSessionToken() != null ? controller.getSessionToken().toString() : JSONObject.NULL);
        data.put("playbackState", state != null ? state.getState() : PlaybackState.STATE_NONE);
        data.put("playbackStateName", playbackStateName(state != null ? state.getState() : PlaybackState.STATE_NONE));
        data.put("position", state != null ? state.getPosition() : -1);
        data.put("actions", state != null ? state.getActions() : 0);

        if (metadata != null) {
            data.put("title", safeMeta(metadata, MediaMetadata.METADATA_KEY_TITLE));
            data.put("artist", safeMeta(metadata, MediaMetadata.METADATA_KEY_ARTIST));
            data.put("album", safeMeta(metadata, MediaMetadata.METADATA_KEY_ALBUM));
            data.put("duration", metadata.getLong(MediaMetadata.METADATA_KEY_DURATION));
        } else {
            data.put("title", JSONObject.NULL);
            data.put("artist", JSONObject.NULL);
            data.put("album", JSONObject.NULL);
            data.put("duration", -1);
        }
        return data;
    }

    private Object safeMeta(MediaMetadata metadata, String key) {
        CharSequence value = metadata.getText(key);
        return value == null ? JSONObject.NULL : value.toString();
    }

    private String playbackStateName(int state) {
        switch (state) {
            case PlaybackState.STATE_NONE: return "NONE";
            case PlaybackState.STATE_STOPPED: return "STOPPED";
            case PlaybackState.STATE_PAUSED: return "PAUSED";
            case PlaybackState.STATE_PLAYING: return "PLAYING";
            case PlaybackState.STATE_FAST_FORWARDING: return "FAST_FORWARDING";
            case PlaybackState.STATE_REWINDING: return "REWINDING";
            case PlaybackState.STATE_BUFFERING: return "BUFFERING";
            case PlaybackState.STATE_ERROR: return "ERROR";
            case PlaybackState.STATE_CONNECTING: return "CONNECTING";
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS: return "SKIPPING_TO_PREVIOUS";
            case PlaybackState.STATE_SKIPPING_TO_NEXT: return "SKIPPING_TO_NEXT";
            case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM: return "SKIPPING_TO_QUEUE_ITEM";
            default: return "UNKNOWN(" + state + ")";
        }
    }
}
