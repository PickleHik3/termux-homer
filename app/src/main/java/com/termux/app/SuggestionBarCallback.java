package com.termux.app;

public interface SuggestionBarCallback {
    void reloadSuggestionBar(char inputChar);
    void reloadSuggestionBar(boolean delete, boolean enter);
}
