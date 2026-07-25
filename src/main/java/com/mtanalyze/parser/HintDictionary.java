/*
 * Copyright 2026 Ralf Schwarz
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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 *   pset.csv                  – Market;Country;Name;BIC (Place of Settlement directory)
 */
public final class HintDictionary {

    private final Map<String, String>         tags            = new HashMap<>();
    private final Map<String, String>         qualifiers      = new HashMap<>();
    private final Map<String, String>         components      = new HashMap<>();
    /** Exact lookup: "QUALIFIER\tVALUE" → description. */
    private final Map<String, String>         qualifierValues  = new HashMap<>();
    /** Contains-fallback: qualifier → list of [value, description] pairs. */
    private final Map<String, List<String[]>> qualifierEntries = new HashMap<>();
    /** PSET directory: full BIC (as given in pset.csv) → [name, market, countryCode]. */
    private final Map<String, String[]>       psetByBic       = new HashMap<>();
    /** PSET directory fallback: 8-char BIC prefix → [name, market, countryCode]. */
    private final Map<String, String[]>       psetByBic8      = new HashMap<>();

    /** User-defined entries (loaded from Preferences at runtime). */
    private final List<String[]>              userEntries           = new ArrayList<>();
    private final Map<String, String>         userQualifierValues  = new HashMap<>();
    private final Map<String, List<String[]>> userQualifierEntries = new HashMap<>();

    private static final int QUAL_VAL_CACHE_SIZE = 512;
    /** LRU result cache for qualifierValueDescription — same qualifier/value pairs repeat on every render. */
    @SuppressWarnings("java:S2160")
    private final Map<String, Optional<String>> qualValCache =
            Collections.synchronizedMap(new LinkedHashMap<>(QUAL_VAL_CACHE_SIZE + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Optional<String>> e) {
                    return size() > QUAL_VAL_CACHE_SIZE;
                }
            });

    public HintDictionary() {
        loadCsvFromFileOrResource("/dict_tags.csv",             2, c -> tags.put(c[0].toUpperCase(), c[1]));
        loadCsvFromFileOrResource("/dict_qualifiers.csv",       2, c -> qualifiers.put(c[0].toUpperCase(), c[1]));
        loadCsvFromFileOrResource("/dict_components.csv",       2, c -> components.put(c[0].toLowerCase(), c[1]));
        loadCsvFromFileOrResource("/dict_qualifier_values.csv", 3, this::registerQualifierValue);
        loadCsvFromFileOrResource("/pset.csv",                  4, this::registerPset);
    }

    private void registerQualifierValue(String[] c) {
        String qKey = c[0].toUpperCase();
        String vKey = c[1].toUpperCase();
        qualifierValues.put(qKey + '\t' + vKey, c[2]);
        qualifierEntries.computeIfAbsent(qKey, k -> new ArrayList<>())
                        .add(new String[]{vKey, c[2]});
    }

    private void registerPset(String[] c) {
        String market  = c[0].trim();
        String country = c[1].trim();
        String name    = c[2].trim();
        String bic     = c[3].trim().toUpperCase();
        if (bic.isEmpty()) return;
        String[] info = {name, market, country};
        psetByBic.put(bic, info);
        String bic8 = bic.length() >= 8 ? bic.substring(0, 8) : bic;
        psetByBic8.putIfAbsent(bic8, info);
    }

    /**
     * Description for a Place-of-Settlement BIC (e.g. field 95a::PSET//xxx), resolved
     * from the pset.csv directory. Tries an exact BIC match first, then falls back to
     * the 8-character institution prefix so both 8- and 11-character BICs resolve.
     */
    public String psetDescription(String bic) {
        if (bic == null) return null;
        String v = bic.trim().toUpperCase();
        if (v.isEmpty()) return null;
        String[] info = psetByBic.get(v);
        if (info == null) {
            String key8 = v.length() >= 8 ? v.substring(0, 8) : v;
            info = psetByBic8.get(key8);
        }
        if (info == null) return null;
        return info[0] + " — " + info[1] + " (" + info[2] + ")";
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
        String qKey     = qualifier.toUpperCase();
        String vUpper   = value.toUpperCase();
        String cacheKey = qKey + '\t' + vUpper;
        if (qualValCache.containsKey(cacheKey)) return qualValCache.get(cacheKey).orElse(null);
        String result = userQualifierValues.get(cacheKey);
        if (result == null) result = findByContains(userQualifierEntries, qKey, vUpper);
        if (result == null) result = qualifierValues.get(cacheKey);
        if (result == null) result = findByContains(qualifierEntries, qKey, vUpper);
        qualValCache.put(cacheKey, Optional.ofNullable(result));
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
        qualValCache.clear();
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