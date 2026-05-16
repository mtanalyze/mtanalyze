/*
 * Copyright 2026 Centerscout GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mtanalyze.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Static lookup utility for SWIFT MT dictionary data.
 * Descriptions for tags, qualifiers, component labels, and qualifier values
 * are loaded once from CSV resource files at class-initialisation time.
 * CSV files in the classpath root:
 *   dict_tags.csv             – tag;description
 *   dict_qualifiers.csv       – qualifier;description
 *   dict_components.csv       – component_label;description
 *   dict_qualifier_values.csv – qualifier;value;description
 */
public final class HintDictionary {

    private final Map<String, String>         tags            = new HashMap<>();
    private final Map<String, String>         qualifiers      = new HashMap<>();
    private final Map<String, String>         components      = new HashMap<>();
    /** Exact lookup: "QUALIFIER\tVALUE" → description. */
    private final Map<String, String>         qualifierValues  = new HashMap<>();
    /** Contains-fallback: qualifier → list of [value, description] pairs. */
    private final Map<String, List<String[]>> qualifierEntries = new HashMap<>();

    /** User-defined entries (loaded from Preferences at runtime). */
    private final List<String[]>              userEntries           = new ArrayList<>();
    private final Map<String, String>         userQualifierValues  = new HashMap<>();
    private final Map<String, List<String[]>> userQualifierEntries = new HashMap<>();

    public HintDictionary() {
        loadCsvFromFileOrResource("/dict_tags.csv",             2, c -> tags.put(c[0].toUpperCase(), c[1]));
        loadCsvFromFileOrResource("/dict_qualifiers.csv",       2, c -> qualifiers.put(c[0].toUpperCase(), c[1]));
        loadCsvFromFileOrResource("/dict_components.csv",       2, c -> components.put(c[0].toLowerCase(), c[1]));
        loadCsvFromFileOrResource("/dict_qualifier_values.csv", 3, this::registerQualifierValue);
    }

    private void registerQualifierValue(String[] c) {
        String qKey = c[0].toUpperCase();
        String vKey = c[1].toUpperCase();
        qualifierValues.put(qKey + '\t' + vKey, c[2]);
        qualifierEntries.computeIfAbsent(qKey, k -> new ArrayList<>())
                        .add(new String[]{vKey, c[2]});
    }

    /** Description for a SWIFT tag (e.g. {@code "35B"}). */
    public String tagDescription(String tag) {
        return tag != null ? tags.get(tag.toUpperCase()) : null;
    }

    /** Description for a qualifier (e.g. {@code "ISIN"}). */
    public String qualifierDescription(String qualifier) {
        return qualifier != null ? qualifiers.get(qualifier.toUpperCase()) : null;
    }

    /** Description for a component label (e.g. {@code "Place Code"}). */
    public String componentDescription(String label) {
        return label != null ? components.get(label.toLowerCase()) : null;
    }

    /**
     * Description for a value in a qualifier context.
     * User entries take precedence over built-in entries.
     * First tries an exact equals match, then a contains match so that
     * composite cell values (e.g. {@code "FAMT/1000000,"}) are still resolved.
     */
    public String qualifierValueDescription(String qualifier, String value) {
        if (qualifier == null || value == null) return null;
        String qKey   = qualifier.toUpperCase();
        String vUpper = value.toUpperCase();
        String result = userQualifierValues.get(qKey + '\t' + vUpper);
        if (result == null) result = findByContains(userQualifierEntries, qKey, vUpper);
        if (result == null) result = qualifierValues.get(qKey + '\t' + vUpper);
        if (result == null) result = findByContains(qualifierEntries, qKey, vUpper);
        return result;
    }

    private static final String USER_DICT_FILENAME = "user_qualifier_values.csv";
    private static final char   UTF8_BOM           = '\uFEFF';

    /** File where user-defined entries are persisted. */
    public File getUserDictFile() {
        return new File(System.getProperty("user.home"), ".mtanalyze/" + USER_DICT_FILENAME);
    }

    /** Loads user-defined entries from {@link #getUserDictFile()}. */
    public void loadUserEntriesFromFile() {
        File file = getUserDictFile();
        List<String[]> entries = new ArrayList<>();
        if (file.isFile()) {
            loadCsvFromFile(file, 2, c -> {
                String q = c[0].trim();
                String v = c[1].trim();
                if (!q.isEmpty() && !v.isEmpty())
                    entries.add(new String[]{q, v, c.length > 2 ? c[2].trim() : ""});
            });
        }
        setUserEntries(entries);
    }

    /**
     * Writes user-defined entries to {@link #getUserDictFile()}.
     * @return true on success, false if the file could not be written
     */
    public boolean saveUserEntriesToFile(List<String[]> entries) {
        File file = getUserDictFile();
        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) return false;
        try (OutputStreamWriter osw = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            osw.write(UTF8_BOM);
            osw.write("Qualifier;Value;Description\n");
            for (String[] e : entries) {
                String q = csvQuote(e[0]);
                String v = csvQuote(e[1]);
                String d = csvQuote(e.length > 2 ? e[2] : "");
                osw.write(q + ";" + v + ";" + d + "\n");
            }
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private static String csvQuote(String s) {
        if (s.contains(";") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    /** Loads user-defined entries from a raw semicolon-delimited CSV string (no header). */
    public void loadUserEntriesFromCsv(String raw) {
        List<String[]> entries = new ArrayList<>();
        if (raw != null && !raw.isEmpty()) {
            for (String line : raw.split("\n")) {
                parseUserEntry(line).ifPresent(entries::add);
            }
        }
        setUserEntries(entries);
    }

    private static Optional<String[]> parseUserEntry(String line) {
        String trimmedLine = line.trim();
        if (shouldSkipUserEntryLine(trimmedLine)) return Optional.empty();

        String[] cols = trimmedLine.split(";", 3);
        if (!hasRequiredUserEntryColumns(cols)) return Optional.empty();

        return Optional.of(new String[]{cols[0].trim(), cols[1].trim(), cols.length > 2 ? cols[2].trim() : ""});
    }

    private static boolean shouldSkipUserEntryLine(String line) {
        return line.isEmpty() || line.startsWith("#");
    }

    private static boolean hasRequiredUserEntryColumns(String[] cols) {
        return cols.length >= 2 && !cols[0].trim().isEmpty() && !cols[1].trim().isEmpty();
    }

    /** Replaces the current user-defined entries with the given list. */
    public void setUserEntries(List<String[]> entries) {
        userEntries.clear();
        userQualifierValues.clear();
        userQualifierEntries.clear();
        for (String[] e : entries) {
            userEntries.add(e.clone());
            String qKey = e[0].toUpperCase();
            String vKey = e[1].toUpperCase();
            String desc = e.length > 2 ? e[2] : "";
            userQualifierValues.put(qKey + '\t' + vKey, desc);
            userQualifierEntries.computeIfAbsent(qKey, k -> new ArrayList<>())
                                .add(new String[]{vKey, desc});
        }
    }

    /** Returns a copy of the current user-defined entries. */
    public List<String[]> getUserEntries() {
        List<String[]> copy = new ArrayList<>(userEntries.size());
        for (String[] e : userEntries) copy.add(e.clone());
        return copy;
    }

    private static String findByContains(Map<String, List<String[]>> map,
                                         String qKey, String vUpper) {
        List<String[]> entries = map.get(qKey);
        if (entries == null) return null;
        for (String[] entry : entries) {
            if (vUpper.contains(entry[0])) return entry[1];
        }
        return null;
    }

    @FunctionalInterface
    private interface RowConsumer { void accept(String[] cols); }

    private static void loadCsvFromFileOrResource(String resource, int minCols, RowConsumer consumer) {
        String fileName = resource.startsWith("/") ? resource.substring(1) : resource;
        File userFile = new File(System.getProperty("user.home"), ".mtanalyze/" + fileName);
        if (userFile.isFile()) {
            loadCsvFromFile(userFile, minCols, consumer);
        } else {
            loadCsv(resource, minCols, consumer);
        }
    }

    private static void loadCsvFromFile(File file, int minCols, RowConsumer consumer) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            boolean header = true;
            String line;
            while ((line = br.readLine()) != null) {
                if (!header) processLine(line, minCols, consumer);
                header = false;
            }
        } catch (Exception e) {
            // dictionary is optional — silently ignore load failures
        }
    }

    private static void loadCsv(String resource, int minCols, RowConsumer consumer) {
        try (InputStream in = HintDictionary.class.getResourceAsStream(resource)) {
            if (in == null) return;
            try (InputStreamReader isr = new InputStreamReader(in, StandardCharsets.UTF_8);
                 BufferedReader br = new BufferedReader(isr)) {
                boolean header = true;
                String line;
                while ((line = br.readLine()) != null) {
                    if (!header) processLine(line, minCols, consumer);
                    header = false;
                }
            }
        } catch (Exception e) {
            // dictionary is optional — silently ignore load failures
        }
    }

    private static void processLine(String line, int minCols, RowConsumer consumer) {
        line = line.trim();
        if (!line.isEmpty() && !line.startsWith("#")) {
            String[] cols = line.split(";", minCols);
            if (cols.length >= minCols) consumer.accept(cols);
        }
    }
}