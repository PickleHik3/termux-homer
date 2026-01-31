package com.termux.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.termux.view.TerminalView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import me.xdrop.fuzzywuzzy.FuzzySearch;

public final class SuggestionBarView extends GridLayout {

    private static final int TEXT_COLOR = 0xFFC0B18B;

    private List<SuggestionBarButton> allSuggestionButtons = new ArrayList<>();
    private List<SuggestionBarButton> defaultButtons;
    private List<String> defaultButtonStrings = new ArrayList<>();
    private int maxButtonCount = 0;
    private float textSize = 12f;
    private boolean showIcons = true;
    private boolean bandW = false;
    private int searchTolerance = 70;

    public SuggestionBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setSuggestionButtons(List<SuggestionBarButton> buttons) {
        if (buttons == null) {
            allSuggestionButtons = new ArrayList<>();
        } else {
            allSuggestionButtons = new ArrayList<>(buttons);
        }
        defaultButtons = null;
        reloadWithInput("", null);
    }

    public void setDefaultButtons(List<String> defaultButtons) {
        if (defaultButtons == null) {
            defaultButtonStrings = new ArrayList<>();
        } else {
            defaultButtonStrings = new ArrayList<>(defaultButtons);
        }
        this.defaultButtons = null;
    }

    public void setMaxButtonCount(int maxButtonCount) {
        this.maxButtonCount = maxButtonCount;
    }

    public void setTextSize(float textSize) {
        this.textSize = textSize;
    }

    public void setShowIcons(boolean showIcons) {
        this.showIcons = showIcons;
    }

    public void setBandW(boolean bandW) {
        this.bandW = bandW;
    }

    public void setSearchTolerance(int searchTolerance) {
        this.searchTolerance = searchTolerance;
    }

    private List<SuggestionBarButton> searchButtons(List<SuggestionBarButton> list, String input, boolean fuzzy) {
        List<SuggestionBarButton> newList = new ArrayList<>();
        if (input == null) {
            input = "";
        }
        String lowered = input.toLowerCase();
        for (int i = 0; i < list.size(); i++) {
            SuggestionBarButton currentButton = list.get(i);
            String buttonText = currentButton.getText() == null ? "" : currentButton.getText();
            if (fuzzy) {
                int ratio = FuzzySearch.partialRatio(buttonText.toLowerCase(), lowered);
                currentButton.setRatio(ratio);
                if (ratio > searchTolerance) {
                    newList.add(currentButton);
                }
            } else if (buttonText.toLowerCase().startsWith(lowered)) {
                newList.add(currentButton);
            }
        }
        if (fuzzy) {
            Collections.sort(newList, new Comparator<SuggestionBarButton>() {
                @Override
                public int compare(SuggestionBarButton suggestionBarButton, SuggestionBarButton t1) {
                    int r1 = suggestionBarButton.getRatio();
                    int r2 = t1.getRatio();
                    if (r1 > r2) {
                        return -1;
                    } else if (r1 < r2) {
                        return 1;
                    } else {
                        return 0;
                    }
                }
            });
        }
        return newList;
    }

    @SuppressLint("ClickableViewAccessibility")
    public void reload() {
        if (allSuggestionButtons.isEmpty()) {
            reloadAllApps();
        } else {
            reloadWithInput("", null);
        }
    }

    public void reloadAllApps() {
        allSuggestionButtons = getInstalledAppButtons();
        defaultButtons = null;
        reloadWithInput("", null);
    }

    public void reloadWithInput(String input, final TerminalView terminalView) {
        List<SuggestionBarButton> suggestionButtons = new ArrayList<>(allSuggestionButtons);
        removeAllViews();

        int buttonCount = maxButtonCount > 0 ? maxButtonCount : suggestionButtons.size();
        if (buttonCount <= 0 || suggestionButtons.isEmpty()) {
            setRowCount(1);
            setColumnCount(0);
            return;
        }

        if (input != null && input.length() > 0 && !" ".equals(input)) {
            suggestionButtons = searchButtons(suggestionButtons, input, input.length() > 2);
        } else if (!defaultButtonStrings.isEmpty()) {
            if (defaultButtons == null) {
                defaultButtons = new ArrayList<>();
                ArrayList<String> reversedDefaults = new ArrayList<>(defaultButtonStrings);
                Collections.reverse(reversedDefaults);
                for (int i = 0; i < reversedDefaults.size(); i++) {
                    List<SuggestionBarButton> currentButtons = searchButtons(suggestionButtons, reversedDefaults.get(i), false);
                    if (!currentButtons.isEmpty()) {
                        SuggestionBarButton currentButton = currentButtons.get(0);
                        if (suggestionButtons.indexOf(currentButton) >= 0) {
                            suggestionButtons.remove(suggestionButtons.indexOf(currentButton));
                        }
                        suggestionButtons.add(0, currentButton);
                        defaultButtons.add(currentButton);
                    }
                }
            } else {
                for (int i = 0; i < defaultButtons.size(); i++) {
                    SuggestionBarButton currentButton = defaultButtons.get(i);
                    if (suggestionButtons.indexOf(currentButton) >= 0) {
                        suggestionButtons.remove(suggestionButtons.indexOf(currentButton));
                    }
                    suggestionButtons.add(0, currentButton);
                }
            }
        }

        setRowCount(1);
        setColumnCount(buttonCount);

        int addedCount = 0;
        for (int col = 0; col < suggestionButtons.size() && col < buttonCount; col++) {
            final SuggestionBarButton currentButton = suggestionButtons.get(col);
            LayoutParams param = new GridLayout.LayoutParams();
            param.width = 0;
            param.height = ViewGroup.LayoutParams.MATCH_PARENT;
            param.setMargins(0, 0, 0, 0);
            param.columnSpec = GridLayout.spec(col, GridLayout.FILL, 1.f);
            param.rowSpec = GridLayout.spec(0, GridLayout.FILL, 1.f);
            if (currentButton.hasIcon() && showIcons) {
                ImageButton imageButton = new ImageButton(getContext(), null, android.R.attr.buttonBarButtonStyle);
                imageButton.setImageDrawable(currentButton.getIcon());
                imageButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                imageButton.setAdjustViewBounds(true);
                imageButton.setBackground(null);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    imageButton.setImageTintList(null);
                }
                if (bandW) {
                    float[] colorMatrix = {
                        0.33f, 0.33f, 0.33f, 0, 0,
                        0.33f, 0.33f, 0.33f, 0, 0,
                        0.33f, 0.33f, 0.33f, 0, 0,
                        0, 0, 0, 1, 0
                    };
                    ColorFilter colorFilter = new ColorMatrixColorFilter(colorMatrix);
                    imageButton.setColorFilter(colorFilter);
                }
                imageButton.setLayoutParams(param);
                imageButton.setOnClickListener(v -> {
                    currentButton.click();
                    if (terminalView != null) {
                        reloadWithInput("", terminalView);
                    }
                });
                addView(imageButton);
            } else {
                Button button = new Button(getContext(), null, android.R.attr.buttonBarButtonStyle);
                button.setTextSize(textSize);
                button.setText(currentButton.getText());
                button.setTextColor(TEXT_COLOR);
                button.setPadding(0, 0, 0, 0);
                button.setLayoutParams(param);
                button.setOnClickListener(v -> {
                    currentButton.click();
                    if (terminalView != null) {
                        reloadWithInput("", terminalView);
                    }
                });
                addView(button);
            }
            addedCount++;
        }

        int missingButtons = buttonCount - addedCount;
        if (!suggestionButtons.isEmpty() && missingButtons > 0) {
            Drawable filler = suggestionButtons.get(0).getIcon();
            for (int i = 0; i < missingButtons; i++) {
                LayoutParams param = new GridLayout.LayoutParams();
                param.width = 0;
                param.height = 0;
                param.setMargins(0, 0, 0, 0);
                param.columnSpec = GridLayout.spec(i + addedCount, GridLayout.FILL, 1.f);
                param.rowSpec = GridLayout.spec(0, GridLayout.FILL, 1.f);
                ImageButton imageButton = new ImageButton(getContext(), null, android.R.attr.buttonBarButtonStyle);
                imageButton.setImageDrawable(filler);
                imageButton.setLayoutParams(param);
                imageButton.setVisibility(INVISIBLE);
                addView(imageButton);
            }
        }
    }

    private List<ResolveInfo> getInstalledApps() {
        PackageManager packageManager = getContext().getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launchables = packageManager.queryIntentActivities(main, 0);
        Collections.sort(launchables, new ResolveInfo.DisplayNameComparator(packageManager));
        return launchables;
    }

    private List<SuggestionBarButton> getInstalledAppButtons() {
        PackageManager packageManager = getContext().getPackageManager();
        List<ResolveInfo> launchables = getInstalledApps();
        List<SuggestionBarButton> buttons = new ArrayList<>();
        for (int i = 0; i < launchables.size(); i++) {
            ResolveInfo resolveInfo = launchables.get(i);
            ActivityInfo info = resolveInfo.activityInfo;
            if (info == null || info.packageName == null || info.name == null) {
                continue;
            }
            String packageName = info.packageName;
            String className = info.name;
            CharSequence label = info.loadLabel(packageManager);
            String appName = label != null ? label.toString() : packageName;
            Drawable icon = resolveInfo.loadIcon(packageManager);
            if (icon == null) {
                buttons.add(new SuggetionBarAppButton(getContext(), packageName, className, appName));
            } else {
                buttons.add(new SuggetionBarAppButton(getContext(), packageName, className, appName, icon));
            }
        }
        return buttons;
    }
}
