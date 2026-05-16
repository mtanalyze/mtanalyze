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
 * Serialization helper for named quick-filter profiles.
 *
 * <p>Storage format (one profile per line, fields tab-separated):
 * <pre>name\tcolKey1\tval1\tcolKey2\tval2\n...</pre>
 */
public class SavedQuickFilters {

    public Map<String, Map<String, String>> deserialize(String pref) {
        LinkedHashMap<String, Map<String, String>> result = new LinkedHashMap<>();
        if (pref == null || pref.isBlank()) return result;
        for (String line : pref.split("\n", -1)) {
            Map.Entry<String, Map<String, String>> parsed = parseProfileLine(line);
            if (parsed != null) result.put(parsed.getKey(), parsed.getValue());
        }
        return result;
    }

    private static Map.Entry<String, Map<String, String>> parseProfileLine(String line) {
        if (line.isBlank()) return null;
        String[] parts = line.split("\t", -1);
        String name = parts[0];
        if (name.isBlank()) return null;
        return new AbstractMap.SimpleEntry<>(name, parseFilters(parts));
    }

    private static Map<String, String> parseFilters(String[] parts) {
        Map<String, String> filters = new LinkedHashMap<>();
        for (int i = 1; i + 1 < parts.length; i += 2) {
            if (!parts[i].isEmpty() && !parts[i + 1].isEmpty()) {
                filters.put(parts[i], parts[i + 1]);
            }
        }
        return filters;
    }

    public String serialize(Map<String, Map<String, String>> profiles) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Map<String, String>> entry : profiles.entrySet()) {
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(entry.getKey());
            for (Map.Entry<String, String> f : entry.getValue().entrySet())
                sb.append('\t').append(f.getKey()).append('\t').append(f.getValue());
        }
        return sb.toString();
    }
}