package com.termux.app;

import android.content.Context;
import android.os.Build;
import android.widget.Button;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import me.xdrop.fuzzywuzzy.FuzzySearch;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P})
public class SuggestionBarFuzzySearchTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.application;
    }

    @Test
    public void testPrefixMode_keepsStableOrder() {
        SuggestionBarView suggestionBarView = new SuggestionBarView(context, null);
        suggestionBarView.setShowIcons(false);

        List<SuggestionBarButton> buttons = Arrays.asList(
                new TestButton("alpine"),
                new TestButton("alpha"),
                new TestButton("beta")
        );
        suggestionBarView.setSuggestionButtons(buttons);
        suggestionBarView.setMaxButtonCount(2);

        suggestionBarView.reloadWithInput("al", null);

        assertEquals(2, suggestionBarView.getChildCount());
        Button first = (Button) suggestionBarView.getChildAt(0);
        Button second = (Button) suggestionBarView.getChildAt(1);
        assertEquals("alpine", first.getText().toString());
        assertEquals("alpha", second.getText().toString());
    }

    @Test
    public void testFuzzyMode_ranksByRatioAndRespectsTolerance() {
        SuggestionBarView suggestionBarView = new SuggestionBarView(context, null);
        suggestionBarView.setShowIcons(false);
        suggestionBarView.setSearchTolerance(70);

        List<TestButton> buttons = Arrays.asList(
                new TestButton("terminal"),
                new TestButton("termux"),
                new TestButton("remote"),
                new TestButton("zzzz")
        );

        List<TestButton> expected = buildExpectedFuzzyOrder(buttons, "termx", 70);
        suggestionBarView.setSuggestionButtons(new ArrayList<>(buttons));
        suggestionBarView.setMaxButtonCount(expected.size());

        suggestionBarView.reloadWithInput("termx", null);

        assertEquals(expected.size(), suggestionBarView.getChildCount());
        for (int i = 0; i < expected.size(); i++) {
            Button child = (Button) suggestionBarView.getChildAt(i);
            assertEquals(expected.get(i).getText(), child.getText().toString());
        }
    }

    private static List<TestButton> buildExpectedFuzzyOrder(List<TestButton> buttons, String input, int tolerance) {
        List<TestButton> expected = new ArrayList<>();
        String lowered = input == null ? "" : input.toLowerCase();
        for (TestButton button : buttons) {
            int ratio = FuzzySearch.partialRatio(button.getText().toLowerCase(), lowered);
            button.setRatio(ratio);
            if (ratio > tolerance) {
                expected.add(button);
            }
        }
        Collections.sort(expected, new Comparator<TestButton>() {
            @Override
            public int compare(TestButton first, TestButton second) {
                int r1 = first.getRatio();
                int r2 = second.getRatio();
                if (r1 > r2) {
                    return -1;
                } else if (r1 < r2) {
                    return 1;
                }
                return 0;
            }
        });
        return expected;
    }

    private static final class TestButton implements SuggestionBarButton {
        private final String text;
        private int ratio;

        private TestButton(String text) {
            this.text = text;
        }

        @Override
        public String getText() {
            return text;
        }

        @Override
        public Boolean hasIcon() {
            return false;
        }

        @Override
        public android.graphics.drawable.Drawable getIcon() {
            return null;
        }

        @Override
        public void click() {
        }

        @Override
        public int getRatio() {
            return ratio;
        }

        @Override
        public void setRatio(int ratio) {
            this.ratio = ratio;
        }
    }
}
