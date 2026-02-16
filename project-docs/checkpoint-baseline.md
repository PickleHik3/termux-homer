# Termux-Launcher Customization Checkpoint

## Branch State
- **Repository**: termux-launcher
- **Current Commit**: `1a6e190e`
- **Commit Message**: "Clarify optional references as manual only"

---

## 1. AndroidManifest.xml Customizations

### Key Differences vs Upstream termux-app

| Feature | termux-launcher | termux-app |
|---------|-----------------|------------|
| Launcher Mode | `HOME` category + `DEFAULT` category (lines 84-89) | Not present |
| System Wallpaper | `SET_WALLPAPER` permission | Not present |
| Install Packages | `INSTALL_PACKAGES` permission | Not present |
| Foreground Service Type | `specialUse` on both TermuxService and RunCommandService | Only on RunCommandService |
| NativeActivity | Present (lines 160-168) | Not present |
| Task Management | `clearTaskOnLaunch="true"`, `excludeFromRecents="true"`, `taskAffinity` | Not present |

### Permissions Added
- `android.permission.SET_WALLPAPER`
- `android.permission.INSTALL_PACKAGES`
- `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`

### Activity Modifications
- NativeActivity entry point for native code
- HOME category intent filter (makes app a launcher)

---

## 2. Java Source Customizations

### New Files (Not in Upstream)

| File Path | Purpose |
|-----------|---------|
| `app/src/main/java/com/termux/app/style/TermuxBackgroundManager.java` | Custom wallpaper/background management with Material You integration |
| `app/src/main/java/com/termux/app/style/TermuxSystemWallpaperManager.java` | System wallpaper setter via WallpaperManager or termux-api |
| `app/src/main/java/com/termux/app/SuggestionBarView.java` | App launcher suggestion bar with fuzzy search |
| `app/src/main/java/com/termux/app/SuggestionBarButton.java` | Base class for suggestion bar buttons |
| `app/src/main/java/com/termux/app/SuggetionBarAppButton.java` | App button implementation for suggestion bar |
| `app/src/main/java/com/termux/app/SuggestionBarCallback.java` | Callback interface for suggestion bar |

### Modified Files (Need Comparison)
- `TermuxActivity.java` - Likely has integration hooks for suggestion bar and background
- `TermuxApplication.java` - May have custom initialization
- `SettingsActivity.java` - May have additional preferences

---

## 3. Resource Customizations

### Layout Files

| File | Purpose |
|------|---------|
| `res/layout/suggestion_bar.xml` | Suggestion bar UI layout |

### Values-v31 (Android 12+ Material You)

| File | Customization |
|------|---------------|
| `res/values-v31/themes.xml` | Material3 DynamicColors themes |
| `res/values-v31/colors.xml` | System accent color references |
| `res/values-v31/styles.xml` | API 31+ specific styles |

### Theme Categories
- `res/values/themes.xml` - Custom theme with accent colors
- `res/values/colors.xml` - Custom accent color palette
- `res/values-night/themes.xml` - Dark theme variants
- `res/values-night/colors.xml` - Dark color palette

### Key Theme Features
- Material You dynamic colors (Android 12+)
- Custom accent system: main_accent, highlight_accent, opposite_accent
- Wallpaper theme support with transparency
- TermuxActivity drawer styling

---

## 4. Build Configuration

### app/build.gradle Changes

| Aspect | termux-launcher | termux-app |
|--------|-----------------|------------|
| Version Name | `0.119.0-b1+monet36` | `0.118.0` |
| App Name | `Termux:Monet` | `Termux` |
| Java Version | 11 | 8 |
| Additional Dependencies | RealtimeBlurView, fuzzywuzzy | None |

### Dependencies Added
```gradle
implementation "com.github.misakmanukyan:RealtimeBlurView_v2:80d9939272"
implementation "me.xdrop:fuzzywuzzy:1.4.0"
implementation project(":native-entrypoint")
```

### Build Features
- Release builds: minifyEnabled=true, shrinkResources=true, proguard
- Native entrypoint module for native code

---

## 5. Key Customization Categories

### Theme/Material You Integration
1. **Dynamic Colors**: Uses Material3 DynamicColors themes on API 31+
2. **System Wallpaper Integration**: TermuxSystemWallpaperManager + TermuxBackgroundManager
3. **Blur Effects**: RealtimeBlurView dependency
4. **Custom Accent Palette**: Non-dynamic fallback colors

### Launcher-Specific UI Components
1. **Suggestion Bar**: App search/launch bar with fuzzy matching
2. **Custom Button Components**: SuggestionBarButton hierarchy
3. **Background Image Support**: Wallpaper as terminal background

### Navigation/Entry Point Changes
1. **HOME Category**: Makes app a launcher replacement
2. **NativeActivity**: Additional entry point for native code
3. **Task Affinity**: Custom task management for launcher behavior

### Custom Resources
1. **Monet Icons**: ic_monet_dark.svg, ic_monet_light.svg
2. **Custom Launcher Icons**: Multiple icon variants
3. **v31 Theme Overrides**: Material You color system integration

---

## 6. Validation for Rebase

### Must-Preserve Customizations
- [ ] AndroidManifest HOME category and related launcher attributes
- [ ] SuggestionBarView and related classes
- [ ] TermuxBackgroundManager and TermuxSystemWallpaperManager
- [ ] Material3 DynamicColors themes (values-v31/)
- [ ] Custom accent color system (values/colors.xml)
- [ ] Additional permissions (SET_WALLPAPER, INSTALL_PACKAGES)
- [ ] Build.gradle dependencies and version configuration
- [ ] NativeActivity entry point

### Merge Conflict Risk Areas
- AndroidManifest.xml (launcher-specific attributes)
- TermuxActivity.java (suggestion bar integration)
- build.gradle (dependency changes)
- themes.xml (style inheritance)

---

## 7. Test Verification Points

After rebase, verify:
1. App can be set as default launcher
2. Suggestion bar appears and searches apps
3. Background image can be set from gallery
4. System wallpaper sync works
5. Material You colors apply on Android 12+
6. Terminal background shows wallpaper/color correctly

---

*Generated: 2025-02-16*
*Commit: 1a6e190e*
