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

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflects over Prowide's generated {@code MT<type>$Sequence<code>} classes to learn,
 * for a given MT type, every SWIFT sequence the standard defines: the letter-path Prowide
 * names it with (e.g. {@code "B1a2A"}) and the {@code :16R:}/{@code :16S:} qualifier it
 * opens (e.g. {@code "SETPRTY"}) -- each such class carries a {@code START_END_16RS}
 * constant holding exactly that qualifier. Both directions are cached per MT type after
 * the first lookup. No hand-maintained table: this stays correct for every MT type
 * Prowide ships, across SRU updates.
 */
public final class ProwideSequences {

    private static final String START_END_16RS_FIELD = "START_END_16RS";
    private static final String SEQUENCE_PREFIX = "Sequence";
    private static final Map<Integer, Map<String, String>> BY_LETTER_PATH = new ConcurrentHashMap<>();
    private static final Map<Integer, Map<String, String>> BY_QUALIFIER = new ConcurrentHashMap<>();

    private ProwideSequences() {}

    /**
     * Letter-path (e.g. {@code "B1a2A"}) -&gt; qualifier name (e.g. {@code "SETPRTY"}) for
     * {@code mt}. Empty when Prowide has no {@code MT<mt>} class for that type, or when
     * {@code mt} has no {@code :16R:}/{@code :16S:} sequences at all (e.g. a flat MT type).
     */
    public static Map<String, String> byLetterPath(int mt) {
        return BY_LETTER_PATH.computeIfAbsent(mt, ProwideSequences::resolveByLetterPath);
    }

    /**
     * Prowide's letter-path (e.g. {@code "B1a2A"}) for the sequence that opens with
     * {@code qualifier} (e.g. {@code "SETPRTY"}) in {@code mt} -- the reverse of
     * {@link #byLetterPath}. {@code null} when {@code mt} is unknown to Prowide or has no
     * sequence with that qualifier.
     */
    public static String letterPathFor(int mt, String qualifier) {
        if (qualifier == null || qualifier.isEmpty()) return null;
        Map<String, String> byQualifier = BY_QUALIFIER.computeIfAbsent(mt, ProwideSequences::resolveByQualifier);
        return byQualifier.get(qualifier);
    }

    private static Map<String, String> resolveByLetterPath(int mt) {
        Class<?> mtClass;
        try {
            String pkg = "com.prowidesoftware.swift.model.mt.mt" + (mt / 100) + "xx";
            mtClass = Class.forName(pkg + ".MT" + "%03d".formatted(mt));
        } catch (ClassNotFoundException e) {
            return Map.of();
        }
        Map<String, String> blocks = new HashMap<>();
        for (Class<?> inner : mtClass.getDeclaredClasses()) {
            String simpleName = inner.getSimpleName();
            if (!simpleName.startsWith(SEQUENCE_PREFIX) || simpleName.length() == SEQUENCE_PREFIX.length()) continue;
            try {
                Field f = inner.getField(START_END_16RS_FIELD);
                blocks.put(simpleName.substring(SEQUENCE_PREFIX.length()), (String) f.get(null));
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                // Not every nested class necessarily carries this constant -- skip it.
            }
        }
        return Map.copyOf(blocks);
    }

    private static Map<String, String> resolveByQualifier(int mt) {
        Map<String, String> byPath = byLetterPath(mt);
        // A qualifier name identifies exactly one place in the standard for a given MT
        // type; putIfAbsent just makes the (never expected) collision deterministic.
        Map<String, String> inverted = new HashMap<>();
        byPath.forEach((letterPath, qualifier) -> inverted.putIfAbsent(qualifier, letterPath));
        return Map.copyOf(inverted);
    }
}
