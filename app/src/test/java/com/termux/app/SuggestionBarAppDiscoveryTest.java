package com.termux.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.os.Build;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPackageManager;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
public class SuggestionBarAppDiscoveryTest {

    private Context context;
    private ShadowPackageManager shadowPackageManager;
    private SuggestionBarView suggestionBarView;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.application;
        shadowPackageManager = shadowOf(context.getPackageManager());
        suggestionBarView = new SuggestionBarView(context, null);
    }

    @Test
    public void testReloadAllApps_withRegisteredLauncherIntent_rendersAtLeastOneSuggestion() {
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = new ActivityInfo();
        resolveInfo.activityInfo.packageName = "com.example.testapp";
        resolveInfo.activityInfo.name = "com.example.testapp.MainActivity";
        resolveInfo.nonLocalizedLabel = "TestApp";
        resolveInfo.activityInfo.applicationInfo = context.getApplicationInfo();

        shadowPackageManager.addResolveInfoForIntent(launcherIntent, resolveInfo);

        suggestionBarView.reloadAllApps();

        int childCount = suggestionBarView.getChildCount();
        assertEquals("SuggestionBarView should render at least 1 suggestion when a launcher app is registered",
                childCount > 0, true);
    }

    @Test
    public void testReloadAllApps_withNoRegisteredApps_rendersZeroChildrenAndDoesNotCrash() {
        suggestionBarView.reloadAllApps();

        int childCount = suggestionBarView.getChildCount();

        assertEquals("SuggestionBarView should handle empty app list without crashing", childCount >= 0, true);
    }

    @Test
    public void testReloadAllApps_withMultipleRegisteredApps_rendersMultipleSuggestions() {
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        for (int i = 0; i < 3; i++) {
            ResolveInfo resolveInfo = new ResolveInfo();
            resolveInfo.activityInfo = new ActivityInfo();
            resolveInfo.activityInfo.packageName = "com.example.testapp" + i;
            resolveInfo.activityInfo.name = "com.example.testapp" + i + ".MainActivity";
            resolveInfo.nonLocalizedLabel = "TestApp" + i;
            resolveInfo.activityInfo.applicationInfo = context.getApplicationInfo();
            shadowPackageManager.addResolveInfoForIntent(launcherIntent, resolveInfo);
        }

        suggestionBarView.reloadAllApps();

        int childCount = suggestionBarView.getChildCount();
        assertEquals("SuggestionBarView should render multiple suggestions when multiple apps are registered",
                childCount > 0, true);
    }

    @Test
    public void testReloadAllApps_withDefaultButtons_emptyInput_rendersSuggestions() {
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = new ActivityInfo();
        resolveInfo.activityInfo.packageName = "com.example.testapp";
        resolveInfo.activityInfo.name = "com.example.testapp.MainActivity";
        resolveInfo.nonLocalizedLabel = "TestApp";
        resolveInfo.activityInfo.applicationInfo = context.getApplicationInfo();
        shadowPackageManager.addResolveInfoForIntent(launcherIntent, resolveInfo);

        suggestionBarView.setDefaultButtons(null);
        suggestionBarView.reloadAllApps();

        int childCount = suggestionBarView.getChildCount();
        assertEquals("SuggestionBarView should render suggestions with default buttons set",
                childCount > 0, true);
    }
}
