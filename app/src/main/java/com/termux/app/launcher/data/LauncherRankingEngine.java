package com.termux.app.launcher.data;

import androidx.annotation.NonNull;

import com.termux.app.launcher.model.LauncherAppEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import me.xdrop.fuzzywuzzy.FuzzySearch;

public final class LauncherRankingEngine {
    private LauncherRankingEngine() {}

    public static List<LauncherAppEntry> filterAndRank(@NonNull List<LauncherAppEntry> entries, @NonNull String query, int tolerance) {
        String input = query.trim().toLowerCase(Locale.US);
        if (input.isEmpty()) {
            return new ArrayList<>(entries);
        }

        final boolean fuzzy = input.length() > 2;
        List<ScoredEntry> scored = new ArrayList<>();
        for (LauncherAppEntry entry : entries) {
            String label = entry.label == null ? "" : entry.label;
            String lower = label.toLowerCase(Locale.US);
            int score;
            if (fuzzy) {
                score = FuzzySearch.partialRatio(input, lower);
                if (score < tolerance) continue;
            } else {
                if (!lower.startsWith(input)) continue;
                score = 100;
            }
            scored.add(new ScoredEntry(entry, score, matchTier(lower, input)));
        }

        Collections.sort(scored, new Comparator<ScoredEntry>() {
            @Override
            public int compare(ScoredEntry a, ScoredEntry b) {
                if (a.score != b.score) return Integer.compare(b.score, a.score);
                if (a.tier != b.tier) return Integer.compare(a.tier, b.tier);
                return a.entry.label.compareToIgnoreCase(b.entry.label);
            }
        });

        List<LauncherAppEntry> out = new ArrayList<>(scored.size());
        for (ScoredEntry item : scored) {
            out.add(item.entry);
        }
        return out;
    }

    private static int matchTier(String label, String input) {
        if (label.equals(input)) return 0;
        if (label.startsWith(input)) return 1;
        String[] words = label.split("\\s+");
        for (String word : words) {
            if (word.startsWith(input)) return 2;
        }
        if (label.contains(input)) return 3;
        return 4;
    }

    private static final class ScoredEntry {
        final LauncherAppEntry entry;
        final int score;
        final int tier;

        ScoredEntry(LauncherAppEntry entry, int score, int tier) {
            this.entry = entry;
            this.score = score;
            this.tier = tier;
        }
    }
}

