package com.college.timetable.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deterministic search tuning. Nothing here is learned at runtime; every alias must be declared
 * by an administrator so that a search can only ever resolve to values that exist in the database.
 */
@ConfigurationProperties(prefix = "college.search")
public class SearchProperties {

    /**
     * Extra spellings that must resolve to an existing programme. Keys and values are compared
     * after punctuation and case removal, for example {@code "BTechCSEAIML": "BTCSEAIML"}.
     */
    private Map<String, String> programAliases = new LinkedHashMap<>();

    /** Section labels treated as "no section filter" rather than a real section name. */
    private java.util.Set<String> nonSectionLabels = new java.util.LinkedHashSet<>(
            java.util.List.of("all", "any", "every"));

    /** Maximum number of candidates returned for one search before the UI asks the user to pick. */
    private int maxSuggestions = 25;

    public Map<String, String> getProgramAliases() {
        return programAliases;
    }

    public void setProgramAliases(Map<String, String> programAliases) {
        this.programAliases = programAliases;
    }

    public java.util.Set<String> getNonSectionLabels() {
        return nonSectionLabels;
    }

    public void setNonSectionLabels(java.util.Set<String> nonSectionLabels) {
        this.nonSectionLabels = nonSectionLabels;
    }

    public int getMaxSuggestions() {
        return maxSuggestions;
    }

    public void setMaxSuggestions(int maxSuggestions) {
        this.maxSuggestions = maxSuggestions;
    }
}