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
package com.mtanalyze.profile;

import java.util.*;

/**
 * Serialization helper for named column layout profiles.
 *
 * <p>Storage format (one profile per line, fields tab-separated):
 * <pre>name\tescapedKey1\tvis1\tescapedKey2\tvis2\n...</pre>
 * where escapedKey uses '|' instead of '\t' for composite key parts,
 * and vis is "1" (visible) or "0" (hidden).
 */
public class SavedColumnLayouts {

    private record ParsedProfile(String name, LinkedHashMap<String, String> cols) {
    }

    public Map<String, LinkedHashMap<String, String>> deserialize(String pref) {
        LinkedHashMap<String, LinkedHashMap<String, String>> result = new LinkedHashMap<>();
        if (pref == null || pref.isBlank()) return result;
        for (String line : pref.split("\n", -1)) {
            ParsedProfile profile = parseProfile(line);
            if (profile != null) {
                result.put(profile.name, profile.cols);
            }
        }
        return result;
    }

    private static ParsedProfile parseProfile(String line) {
        if (line.isBlank()) return null;
        String[] parts = line.split("\t", -1);
        String name = parts[0];
        if (name.isBlank()) return null;
        return new ParsedProfile(name, parseColumns(parts));
    }

    private static LinkedHashMap<String, String> parseColumns(String[] parts) {
        LinkedHashMap<String, String> cols = new LinkedHashMap<>();
        for (int i = 1; i + 1 < parts.length; i += 2) {
            if (!parts[i].isEmpty()) {
                cols.put(parts[i], parts[i + 1]);
            }
        }
        return cols;
    }

    public String serialize(Map<String, LinkedHashMap<String, String>> profiles) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, LinkedHashMap<String, String>> entry : profiles.entrySet()) {
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(entry.getKey());
            for (Map.Entry<String, String> col : entry.getValue().entrySet())
                sb.append('\t').append(col.getKey()).append('\t').append(col.getValue());
        }
        return sb.toString();
    }
}