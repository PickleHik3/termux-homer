package com.termux.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;

public class SuggetionBarAppButton implements SuggestionBarButton {
    private String text;
    private Drawable icon;
    private boolean hasIcon;
    private String packageName;
    private String activityClassName;
    private Context context;
    private int ratio = 0;

    public SuggetionBarAppButton(Context context, String packageName, String appName, Drawable icon) {
        this(context, packageName, null, appName, icon);
    }

    public SuggetionBarAppButton(Context context, String packageName, String appName) {
        this(context, packageName, null, appName);
    }

    public SuggetionBarAppButton(Context context, String packageName, String activityClassName, String appName, Drawable icon) {
        this.text = appName;
        this.icon = icon;
        this.hasIcon = icon != null;
        this.packageName = packageName;
        this.activityClassName = activityClassName;
        this.context = context;
    }

    public SuggetionBarAppButton(Context context, String packageName, String activityClassName, String appName) {
        this.text = appName;
        this.icon = null;
        this.hasIcon = false;
        this.packageName = packageName;
        this.activityClassName = activityClassName;
        this.context = context;
    }

    public int getRatio() {
        return ratio;
    }

    public void setRatio(int ratio) {
        this.ratio = ratio;
    }

    public String getText() {
        return text;
    }

    public Drawable getIcon() {
        return icon;
    }

    public Boolean hasIcon() {
        return hasIcon;
    }

    public void click() {
        Intent launchIntent = null;
        if (activityClassName != null) {
            Intent explicitIntent = new Intent(Intent.ACTION_MAIN);
            explicitIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            explicitIntent.setComponent(new ComponentName(packageName, activityClassName));
            launchIntent = explicitIntent;
        } else {
            launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        }
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launchIntent);
        }
    }
}
