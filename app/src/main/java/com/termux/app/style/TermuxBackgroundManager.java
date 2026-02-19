package com.termux.app.style;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import com.termux.R;
import com.termux.app.RunCommandService;
import com.termux.app.TermuxActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.errors.Error;
import com.termux.shared.file.FileUtils;
import com.termux.shared.image.ImageUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TextStyle;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TermuxBackgroundManager {

    /**
     * Require {@link TermuxActivity} to perform operations.
     */
    private final TermuxActivity mActivity;

    /**
     * A launcher for start the process of executing an {@link ActivityResultContract}.
     */
    private final ActivityResultLauncher<String> mActivityResultLauncher;

    /**
     * Termux app shared preferences manager.
     */
    private final TermuxAppSharedPreferences mPreferences;

    /**
     * ExecutorService to execute task in background.
     */
    private final ExecutorService executor;

    /**
     * Handler allows to send and process {@link  android.os.Message Message}.
     */
    private final Handler handler;

    private static final String LOG_TAG = "TermuxBackgroundManager";

    public TermuxBackgroundManager(TermuxActivity activity) {
        this.mActivity = activity;
        this.mPreferences = activity.getPreferences();
        this.mActivityResultLauncher = registerActivityResultLauncher();
        this.executor = Executors.newSingleThreadExecutor();
        this.handler = new Handler(Looper.getMainLooper());
    }

    /**
     * Registers for activity result launcher. It's safe to call before fragment
     * or activity is created.
     *
     * @return A launcher for executing an {@link ActivityResultContract}.
     */
    private ActivityResultLauncher<String> registerActivityResultLauncher() {
        return mActivity.registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                try {
                    executor.execute(() -> {
                        Bitmap bitmap = ImageUtils.getBitmap(mActivity, uri);
                        if (bitmap == null) {
                            Logger.logErrorAndShowToast(mActivity, LOG_TAG, mActivity.getString(R.string.error_background_image_loading_from_gallery_failed));
                            return;
                        }
                        ImageUtils.compressAndSaveBitmap(bitmap, TermuxConstants.TERMUX_BACKGROUND_IMAGE_PATH);
                        boolean success = generateImageFiles(mActivity, bitmap);
                        if (success) {
                            notifyBackgroundUpdated(true);
                            Logger.logInfo(LOG_TAG, "Image received successfully from the gallary.");
                            Logger.logDebug(LOG_TAG, "Storing background original image to " + TermuxConstants.TERMUX_BACKGROUND_IMAGE_PATH);
                            Logger.logDebug(LOG_TAG, "Storing background portrait image to " + TermuxConstants.TERMUX_BACKGROUND_IMAGE_PORTRAIT_PATH);
                            Logger.logDebug(LOG_TAG, "Storing background landscape image to " + TermuxConstants.TERMUX_BACKGROUND_IMAGE_LANDSCAPE_PATH);
                            maybeSetSystemWallpaper(bitmap);
                        } else {
                            Logger.logErrorAndShowToast(mActivity, LOG_TAG, mActivity.getString(R.string.error_background_image_loading_from_gallery_failed));
                        }
                    });
                } catch (Exception e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failed to load image", e);
                    Logger.showToast(mActivity, mActivity.getString(R.string.error_background_image_loading_from_gallery_failed), true);
                }
            }
        });
    }

    /**
     * Check whether the optimized background image for {@link Configuration#ORIENTATION_PORTRAIT
     * Portrait} and {@link Configuration#ORIENTATION_LANDSCAPE Landscape} display view exist.
     *
     * @param context The context for operation.
     * @return Returns whether the optimized background image exist or not.
     */
    public static boolean isImageFilesExist(@NonNull Context context, boolean shouldGenerate) {
        boolean isLandscape = (ViewUtils.getDisplayOrientation(context) == Configuration.ORIENTATION_LANDSCAPE);
        Point size = ViewUtils.getDisplaySize(context, true);
        String imagePath1 = isLandscape ? TermuxConstants.TERMUX_BACKGROUND_IMAGE_LANDSCAPE_PATH : TermuxConstants.TERMUX_BACKGROUND_IMAGE_PORTRAIT_PATH;
        String imagePath2 = isLandscape ? TermuxConstants.TERMUX_BACKGROUND_IMAGE_PORTRAIT_PATH : TermuxConstants.TERMUX_BACKGROUND_IMAGE_LANDSCAPE_PATH;
        boolean exist = ImageUtils.isImageOptimized(imagePath1, size) && ImageUtils.isImageOptimized(imagePath2, DataUtils.swap(size));
        if (!exist && shouldGenerate && ImageUtils.isImage(TermuxConstants.TERMUX_BACKGROUND_IMAGE_PATH)) {
            Bitmap bitmap = ImageUtils.getBitmap(TermuxConstants.TERMUX_BACKGROUND_IMAGE_PATH);
            return generateImageFiles(context, bitmap);
        }
        return exist;
    }

    /**
     * Enable background image loading. If the image already exist then ask for restore otherwise pick from gallery.
     */
    public void setBackgroundImage() {
        if (!mPreferences.isBackgroundImageEnabled() && isImageFilesExist(mActivity, true)) {
            restoreBackgroundImages();
        } else {
            pickImageFromGallery();
        }
    }

    /**
     * Disable background image loading and notify about the changes.
     * If image files are not deleted then it can be used to restore
     * when resetting background to image.
     *
     * @param deleteFiles The {@code boolean} that decides if it should delete the image files.
     */
    public void removeBackgroundImage(boolean deleteFiles) {
        if (deleteFiles) {
            FileUtils.deleteDirectoryFile(null, TermuxConstants.TERMUX_BACKGROUND_DIR_PATH, true);
        }
        notifyBackgroundUpdated(false);
    }

    /**
     * {@link ActivityResultLauncher#launch(Object) Launch} Activity for result to pick image from gallery.
     */
    private void pickImageFromGallery() {
        ActivityUtils.startActivityForResult(mActivity, mActivityResultLauncher, ImageUtils.ANY_IMAGE_TYPE);
    }

    /**
     * If the background images exist then ask user whether to restore them or not.
     * If denied pick from gallery.
     */
    private void restoreBackgroundImages() {
        AlertDialog.Builder b = new AlertDialog.Builder(mActivity);
        b.setMessage(R.string.title_restore_background_image);
        b.setPositiveButton(com.termux.shared.R.string.action_yes, (dialog, id) -> {
            notifyBackgroundUpdated(true);
        });
        b.setNegativeButton(com.termux.shared.R.string.action_no, ((dialog, id) -> {
            pickImageFromGallery();
        }));
        b.show();
    }

    /**
     * Generate background image files using original image. {@link Context}
     * passed to this method must be of an {@link Activity} to determine the size
     * of display.
     *
     * @param context The context require for the operations.
     * @param bitmap Image bitmap to save as background.
     * @return Returns whether the images generated successfully.
     */
    public static boolean generateImageFiles(@NonNull Context context, Bitmap bitmap) {
        if (bitmap == null || !(context instanceof Activity)) {
            return false;
        }
        Point size = ViewUtils.getDisplaySize(context, true);
        boolean isLandscape = ViewUtils.getDisplayOrientation(context) == Configuration.ORIENTATION_LANDSCAPE;
        Error error;
        if (isLandscape) {
            error = ImageUtils.saveForDisplayResolution(bitmap, size, TermuxConstants.TERMUX_BACKGROUND_IMAGE_LANDSCAPE_PATH, TermuxConstants.TERMUX_BACKGROUND_IMAGE_PORTRAIT_PATH);
        } else {
            error = ImageUtils.saveForDisplayResolution(bitmap, DataUtils.swap(size), TermuxConstants.TERMUX_BACKGROUND_IMAGE_LANDSCAPE_PATH, TermuxConstants.TERMUX_BACKGROUND_IMAGE_PORTRAIT_PATH);
        }
        return error == null;
    }

    /**
     * Updates background to image or solid color. If forced then load again even if
     * the background is already set. Forced update is require when the display orientation
     * is changed.
     *
     * @param forced Force background update task.
     */
    public void updateBackground(boolean forced) {
        if (!mActivity.isVisible())
            return;
        if (mActivity.getPreferences().isBackgroundImageEnabled()) {
            Drawable drawable = mActivity.getWindow().getDecorView().getBackground();
            // If it's not forced update and background is already drawn,
            // then avoid reloading of image.
            if (!forced && ImageUtils.isBitmapDrawable(drawable)) {
                return;
            }
            updateBackgroundImage();
        } else {
            updateBackgroundColor();
        }
    }

    /**
     * Set background to color.
     */
    public void updateBackgroundColor() {
        if (!mActivity.isVisible())
            return;
        TerminalSession session = mActivity.getCurrentSession();
        if (session != null && session.getEmulator() != null) {
            mActivity.getWindow().getDecorView().setBackgroundColor(session.getEmulator().mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND]);
        }
    }

    /**
     * Set background to image corresponding to display orientation.
     */
    public void updateBackgroundImage() {
        boolean isLandscape = ViewUtils.getDisplayOrientation(mActivity) == Configuration.ORIENTATION_LANDSCAPE;
        String imagePath = isLandscape ? TermuxConstants.TERMUX_BACKGROUND_IMAGE_LANDSCAPE_PATH : TermuxConstants.TERMUX_BACKGROUND_IMAGE_PORTRAIT_PATH;
        try {
            // Performing on main Thread may cause ANR and lag.
            executor.execute(() -> {
                if (isImageFilesExist(mActivity, true)) {
                    Drawable drawable = ImageUtils.getDrawable(imagePath);
                    applyBackgroundOverlay(drawable, mActivity.getProperties().getBackgroundOverlayColor());
                    handler.post(() -> mActivity.getWindow().getDecorView().setBackground(drawable));
                } else {
                    Logger.logErrorAndShowToast(mActivity, LOG_TAG, mActivity.getString(R.string.error_background_image_loading_failed));
                    // Image files are unable to load so set background to solid color and notify update.
                    handler.post(this::updateBackgroundColor);
                    notifyBackgroundUpdated(false);
                }
            });
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to load image", e);
            Logger.showToast(mActivity, mActivity.getString(R.string.error_background_image_loading_failed), true);
            // Since loading of image is failed, Set background to solid color.
            updateBackgroundColor();
            notifyBackgroundUpdated(false);
        }
    }

    /**
     * Notify that the background is changed. New background can be image or solid color.
     *
     * @param isImage The {@code boolean} indicates that new background is image or not.
     */
    public void notifyBackgroundUpdated(boolean isImage) {
        mPreferences.setBackgroundImageEnabled(isImage);
        TermuxActivity.updateTermuxActivityStyling(mActivity, true);
    }

    private void applyBackgroundOverlay(@NonNull Drawable drawable, int color) {
        if (mPreferences != null && mPreferences.isMonetOverlayEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                drawable.setColorFilter(new BlendModeColorFilter(color, BlendMode.SRC_OVER));
            } else {
                drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_OVER));
            }
        } else {
            ImageUtils.addOverlay(drawable, color);
        }
    }

    private void maybeSetSystemWallpaper(@NonNull Bitmap bitmap) {
        if (!mPreferences.isUseSystemWallpaperEnabled()) {
            return;
        }
        // Sync system wallpaper when enabled so monet colors update automatically
        boolean setWithTermuxApi = trySetSystemWallpaperWithTermuxApi();
        if (!setWithTermuxApi) {
            setWallpaperWithManager(bitmap);
        }
    }

    private boolean trySetSystemWallpaperWithTermuxApi() {
        String apiError = TermuxUtils.isTermuxAPIAppInstalled(mActivity);
        if (apiError != null) {
            Logger.logDebug(LOG_TAG, "Termux:API not installed, will use WallpaperManager instead");
            return false;
        }

        File commandFile = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "termux-wallpaper");
        if (!commandFile.isFile()) {
            Logger.logDebug(LOG_TAG, "termux-wallpaper command not found, will use WallpaperManager instead");
            return false;
        }

        Intent intent = new Intent(mActivity, RunCommandService.class);
        intent.setAction(RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND);
        intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_COMMAND_PATH, commandFile.getAbsolutePath());
        intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_ARGUMENTS, new String[] { "-f", TermuxConstants.TERMUX_BACKGROUND_IMAGE_PATH });
        intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_BACKGROUND, true);
        intent.putExtra(RUN_COMMAND_SERVICE.EXTRA_COMMAND_LABEL, mActivity.getString(R.string.action_set_system_wallpaper));

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(mActivity, intent);
            } else {
                mActivity.startService(intent);
            }
            Logger.logInfo(LOG_TAG, "Setting system wallpaper via termux-api");
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to start termux-wallpaper, will use WallpaperManager instead", e);
            return false;
        }

        return true;
    }

    private void setWallpaperWithManager(@NonNull Bitmap bitmap) {
        try {
            WallpaperManager.getInstance(mActivity).setBitmap(bitmap);
            Logger.logInfo(LOG_TAG, "System wallpaper set successfully via WallpaperManager");
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to set system wallpaper", e);
            showToast(mActivity.getString(R.string.error_wallpaper_set_failed), true);
        }
    }

    private void showToast(@NonNull String message, boolean longDuration) {
        handler.post(() -> mActivity.showToast(message, longDuration));
    }
}
