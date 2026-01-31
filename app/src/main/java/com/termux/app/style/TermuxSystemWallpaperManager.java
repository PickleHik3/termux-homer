package com.termux.app.style;

import android.app.WallpaperManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.webkit.MimeTypeMap;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import com.termux.R;
import com.termux.app.RunCommandService;
import com.termux.app.TermuxActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.image.ImageUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE;
import com.termux.shared.termux.TermuxUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TermuxSystemWallpaperManager {

    private static final String LOG_TAG = "TermuxSystemWallpaper";

    private final TermuxActivity mActivity;
    private final ActivityResultLauncher<String> mActivityResultLauncher;
    private final ExecutorService executor;
    private final Handler handler;

    public TermuxSystemWallpaperManager(TermuxActivity activity) {
        this.mActivity = activity;
        this.mActivityResultLauncher = registerActivityResultLauncher();
        this.executor = Executors.newSingleThreadExecutor();
        this.handler = new Handler(Looper.getMainLooper());
    }

    private ActivityResultLauncher<String> registerActivityResultLauncher() {
        return mActivity.registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                executor.execute(() -> handleSelectedImage(uri));
            }
        });
    }

    public void setSystemWallpaper() {
        pickImageFromGallery();
    }

    private void pickImageFromGallery() {
        ActivityUtils.startActivityForResult(mActivity, mActivityResultLauncher, ImageUtils.ANY_IMAGE_TYPE);
    }

    private void handleSelectedImage(@NonNull Uri uri) {
        Bitmap bitmap = ImageUtils.getBitmap(mActivity, uri);
        if (bitmap == null) {
            showToast(mActivity.getString(R.string.error_wallpaper_image_read_failed), true);
            return;
        }

        boolean setWithTermuxApi = trySetWallpaperWithTermuxApi(uri);
        if (!setWithTermuxApi) {
            setWallpaperWithManager(bitmap);
        }
    }

    private boolean trySetWallpaperWithTermuxApi(@NonNull Uri uri) {
        String apiError = TermuxUtils.isTermuxAPIAppInstalled(mActivity);
        if (apiError != null) {
            showToast(mActivity.getString(R.string.error_termux_api_not_installed), true);
            return false;
        }

        File imageFile = copyImageToTemp(uri);
        if (imageFile == null) {
            return false;
        }

        File commandFile = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "termux-wallpaper");
        if (!commandFile.isFile()) {
            showToast(mActivity.getString(R.string.error_termux_wallpaper_not_found), true);
            return false;
        }

        Intent intent = new Intent(mActivity, RunCommandService.class);
        intent.setAction(RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND);
        intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_COMMAND_PATH, commandFile.getAbsolutePath());
        intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_ARGUMENTS, new String[] { "-f", imageFile.getAbsolutePath() });
        intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_BACKGROUND, true);
        intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_COMMAND_LABEL, mActivity.getString(R.string.action_set_system_wallpaper));

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(mActivity, intent);
            } else {
                mActivity.startService(intent);
            }
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to start termux-wallpaper", e);
            showToast(mActivity.getString(R.string.error_termux_wallpaper_start_failed), true);
            return false;
        }

        return true;
    }

    private File copyImageToTemp(@NonNull Uri uri) {
        String extension = getExtensionFromUri(uri);
        File targetDir = new File(TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH);
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            showToast(mActivity.getString(R.string.error_wallpaper_image_write_failed), true);
            return null;
        }

        File targetFile = new File(targetDir, "termux-wallpaper-" + System.currentTimeMillis() + "." + extension);
        try (InputStream inputStream = mActivity.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                showToast(mActivity.getString(R.string.error_wallpaper_image_read_failed), true);
                return null;
            }
            try (OutputStream outputStream = new FileOutputStream(targetFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
            }
        } catch (SecurityException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Image access denied", e);
            showToast(mActivity.getString(R.string.error_wallpaper_image_read_failed), true);
            return null;
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to copy image", e);
            showToast(mActivity.getString(R.string.error_wallpaper_image_write_failed), true);
            return null;
        }

        return targetFile;
    }

    private String getExtensionFromUri(@NonNull Uri uri) {
        String mimeType = mActivity.getContentResolver().getType(uri);
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        if (extension == null || extension.isEmpty()) {
            extension = "img";
        }
        return extension;
    }

    private void setWallpaperWithManager(@NonNull Bitmap bitmap) {
        try {
            WallpaperManager.getInstance(mActivity).setBitmap(bitmap);
        } catch (IOException | SecurityException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to set wallpaper", e);
            showToast(mActivity.getString(R.string.error_wallpaper_set_failed), true);
        }
    }

    private void showToast(@NonNull String message, boolean longDuration) {
        handler.post(() -> mActivity.showToast(message, longDuration));
    }
}
