package com.termux.app.launcher;

import com.termux.app.launcher.data.LauncherConfigRepository;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedFolderItem;
import com.termux.app.launcher.model.PinnedItem;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LauncherConfigRepositoryTest {
    private LauncherConfigRepository repository;

    private static final class FakePreferencesStore implements LauncherConfigRepository.PreferencesStore {
        private String pinnedItemsV2 = "";
        private int schemaVersion = 0;
        private String legacyDefaultButtons = "";

        @Override
        public String getPinnedItemsV2() {
            return pinnedItemsV2;
        }

        @Override
        public void setPinnedItemsV2(String value) {
            pinnedItemsV2 = value;
        }

        @Override
        public void setPinnedItemsSchemaVersion(int version) {
            schemaVersion = version;
        }

        @Override
        public String getLegacyDefaultButtons() {
            return legacyDefaultButtons;
        }
    }

    @Before
    public void setUp() {
        FakePreferencesStore preferences = new FakePreferencesStore();
        repository = new LauncherConfigRepository(preferences);
    }

    @Test
    public void pinnedAppRoundTrip_preservesOrder() {
        List<PinnedItem> items = new ArrayList<>();
        items.add(new PinnedAppItem(new AppRef("com.example.one", "A")));
        items.add(new PinnedAppItem(new AppRef("com.example.two", "B")));
        items.add(new PinnedAppItem(new AppRef("com.example.three", "C")));

        repository.savePinnedItems(items);
        List<PinnedItem> loaded = repository.loadPinnedItems();

        assertEquals(3, loaded.size());
        assertEquals(PinnedItem.TYPE_APP, loaded.get(0).getType());
        assertEquals("com.example.one", ((PinnedAppItem) loaded.get(0)).appRef.packageName);
        assertEquals("com.example.two", ((PinnedAppItem) loaded.get(1)).appRef.packageName);
        assertEquals("com.example.three", ((PinnedAppItem) loaded.get(2)).appRef.packageName);
    }

    @Test
    public void folderRoundTrip_preservesGridAndTint() {
        List<PinnedItem> items = new ArrayList<>();
        PinnedFolderItem folder = new PinnedFolderItem("folder-1", "Media");
        folder.rows = 2;
        folder.cols = 3;
        folder.tintOverrideEnabled = true;
        folder.tintColor = 0xFF1A2B3C;
        folder.apps.add(new AppRef("com.example.music", "Main"));
        folder.apps.add(new AppRef("com.example.podcast", "Main"));
        items.add(folder);

        repository.savePinnedItems(items);
        List<PinnedItem> loaded = repository.loadPinnedItems();

        assertEquals(1, loaded.size());
        assertEquals(PinnedItem.TYPE_FOLDER, loaded.get(0).getType());
        PinnedFolderItem loadedFolder = (PinnedFolderItem) loaded.get(0);
        assertEquals(2, loadedFolder.rows);
        assertEquals(3, loadedFolder.cols);
        assertTrue(loadedFolder.tintOverrideEnabled);
        assertEquals(0xFF1A2B3C, loadedFolder.tintColor);
        assertEquals(2, loadedFolder.apps.size());
    }
}
