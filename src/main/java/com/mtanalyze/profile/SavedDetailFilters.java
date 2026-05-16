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
 * Serialization helper for named detail-fields filter profiles.
 *
 * <p>Storage format (one profile per line, fields tab-separated):
 * <pre>name\ts:seq1\ts:seq2\tt:tag1\tt:tag2\n...</pre>
 * where entries prefixed with {@code s:} are hidden sequences and
 * entries prefixed with {@code t:} are hidden tags.
 */
public class SavedDetailFilters {

    public record Profile(Set<String> hiddenSeqs, Set<String> hiddenTags) {
            public Profile(Set<String> hiddenSeqs, Set<String> hiddenTags) {
                this.hiddenSeqs = new LinkedHashSet<>(hiddenSeqs);
                this.hiddenTags = new LinkedHashSet<>(hiddenTags);
            }
        }

    public Map<String, Profile> deserialize(String pref) {
        LinkedHashMap<String, Profile> result = new LinkedHashMap<>();
        if (pref == null || pref.isBlank()) return result;
        for (String line : pref.split("\n", -1)) {
            Map.Entry<String, Profile> entry = parseLine(line);
            if (entry != null) result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static Map.Entry<String, Profile> parseLine(String line) {
        if (line.isBlank()) return null;
        String[] parts = line.split("\t", -1);
        String name = parts[0];
        if (name.isBlank()) return null;
        Set<String> seqs = new LinkedHashSet<>();
        Set<String> tags = new LinkedHashSet<>();
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].startsWith("s:"))      seqs.add(parts[i].substring(2));
            else if (parts[i].startsWith("t:")) tags.add(parts[i].substring(2));
        }
        return new AbstractMap.SimpleEntry<>(name, new Profile(seqs, tags));
    }

    public String serialize(Map<String, Profile> profiles) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Profile> entry : profiles.entrySet()) {
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(entry.getKey());
            for (String seq : entry.getValue().hiddenSeqs) sb.append("\ts:").append(seq);
            for (String tag : entry.getValue().hiddenTags) sb.append("\tt:").append(tag);
        }
        return sb.toString();
    }
}