package com.termux.app.terminal;

public final class AccessoryStackLayoutPolicy {

    private AccessoryStackLayoutPolicy() {}

    public static int computeCombinedHeight(int toolbarHeightPx, int appsBarHeightPx, int azRowHeightPx, int appsBarGapPx) {
        int toolbar = Math.max(0, toolbarHeightPx);
        int apps = Math.max(0, appsBarHeightPx);
        int az = Math.max(0, azRowHeightPx);
        int gap = Math.max(0, appsBarGapPx);
        return toolbar + apps + az + gap;
    }

    public static int computeAppsBarInterRowGapPx(boolean azEnabled, float density, float iconScale) {
        if (!azEnabled)
            return 0;
        float safeDensity = Math.max(0f, density);
        float safeIconScale = Math.max(0f, iconScale);
        return Math.round(safeDensity * (3f + (Math.max(0f, safeIconScale - 1f) * 2f)));
    }
}
