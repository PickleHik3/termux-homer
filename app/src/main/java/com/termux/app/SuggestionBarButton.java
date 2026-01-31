package com.termux.app;

import android.graphics.drawable.Drawable;

public interface SuggestionBarButton {
    String getText();
    Boolean hasIcon();
    Drawable getIcon();
    void click();
    int getRatio();
    void setRatio(int ratio);
}
