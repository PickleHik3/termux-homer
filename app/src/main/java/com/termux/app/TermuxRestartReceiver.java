package com.termux.app;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.jakewharton.processphoenix.ProcessPhoenix;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

public class TermuxRestartReceiver extends BroadcastReceiver {

    private static final String LOG_TAG = "TermuxRestartReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.ACTION_RESTART.equals(intent.getAction())) {
            return;
        }

        Logger.logInfo(LOG_TAG, "Received app restart broadcast");
        Intent restartIntent = new Intent(Intent.ACTION_MAIN);
        restartIntent.setComponent(new ComponentName(
            TermuxConstants.TERMUX_PACKAGE_NAME,
            TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY_NAME
        ));
        restartIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        ProcessPhoenix.triggerRebirth(context, restartIntent);
    }
}
