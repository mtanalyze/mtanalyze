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

import com.prowidesoftware.swift.model.Tag;
import com.prowidesoftware.swift.model.field.Field;

public final class Lookups {

    /** All tags are visible by default. */
    public static final boolean DEFAULT_VISIBLE = true;

    /** Returns the qualifier name directly as the sequence label (no mapping). */
    public String seqLabel(String childQualifier) {
        return childQualifier;
    }

    /**
     * Extracts the qualifier of a tag (component "Qualifier" or "Identification Type").
     * Also used by the parser to populate the qualifier column.
     */
    public String extractQualifier(Tag t) {
        Field f = trimmedField(t);
        if (f == null) return "";
        String fromComponent = findQualifierInComponents(f);
        if (fromComponent != null) return fromComponent;
        return findQualifierFromFirstComponent(f, t.getValue());
    }

    /**
     * Returns the tag value without the leading qualifier prefix.
     * Examples:
     *   ":SAFE//EUCLEAR"   → "EUCLEAR"
     *   ":SETT//20231015"  → "20231015"
     *   "ISIN DE000..."    → "DE000..."
     */
    public String valueWithoutQualifier(Tag t) {
        if (t == null) return "";
        String raw = t.getValue() != null ? t.getValue().trim() : "";
        Field f = trimmedField(t);
        if (f != null) {
            String stripped = stripByQualifierComponent(f, raw);
            if (stripped == null) {
                return stripByFirstComponent(f, raw);
            }
            return stripped;
        }
        return raw;
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /** Returns a Field parsed from the trimmed tag value so leading/trailing whitespace
     *  does not interfere with Prowide's component detection (e.g. "ISIN" prefix in 35B). */
    private static Field trimmedField(Tag t) {
        if (t == null) return null;
        String v = t.getValue();
        if (v == null) return t.asField();
        String trimmed = v.trim();
        return trimmed.equals(v) ? t.asField() : new Tag(t.getName(), trimmed).asField();
    }

    private static boolean isQualifierLabel(String lbl) {
        String lo = lbl.toLowerCase();
        return lo.equals("qualifier") || lo.contains("identification type") || lo.contains("type of id");
    }

    /** Returns the qualifier value from the first matching qualifier component, or null if not found. */
    private static String findQualifierInComponents(Field f) {
        for (int c = 1; c <= f.componentsSize(); c++) {
            String lbl = f.getComponentLabel(c);
            if (lbl != null && isQualifierLabel(lbl)) {
                String v = f.getComponent(c);
                return v != null ? v.trim() : "";
            }
        }
        return null;
    }

    /** Fallback: checks if the first component value appears as a word prefix in the raw tag value. */
    private static String findQualifierFromFirstComponent(Field f, String tagValue) {
        String raw = tagValue != null ? tagValue.trim() : "";
        if (f.componentsSize() < 1) return "";
        String v = f.getComponent(1);
        if (v == null || v.trim().isEmpty()) return "";
        String q = v.trim();
        if (raw.startsWith(q + " ") || raw.startsWith(q + "\n")) return q;
        return "";
    }

    /**
     * Strips the qualifier prefix found in the field's qualifier component.
     * Returns the stripped string, the original raw string if a qualifier was found but
     * didn't match any pattern, or null if no qualifier component was found.
     */
    private static String stripByQualifierComponent(Field f, String raw) {
        for (int c = 1; c <= f.componentsSize(); c++) {
            String lbl = f.getComponentLabel(c);
            if (lbl != null && isQualifierLabel(lbl)) {
                String q = f.getComponent(c);
                if (q != null && !q.isEmpty()) {
                    return stripQualifierPrefix(raw, q);
                }
                return raw;
            }
        }
        return null;
    }

    /** Tries the three canonical qualifier prefix patterns and returns the stripped string. */
    private static String stripQualifierPrefix(String raw, String q) {
        String s = raw.replaceFirst("^:\\s*" + q + "//", "");
        if (s.length() < raw.length()) return s;
        s = raw.replaceFirst("^:\\s*" + q + "/[^/]*/", "");
        if (s.length() < raw.length()) return s;
        s = raw.replaceFirst("^" + q + "[ \t]+", "");
        if (s.length() < raw.length()) return s;
        return raw;
    }

    /** Fallback: strips a word-prefix formed by the first component value. */
    private static String stripByFirstComponent(Field f, String raw) {
        String src = raw != null ? raw : "";
        if (f == null || f.componentsSize() < 1) return src;
        String v = f.getComponent(1);
        if (v == null || v.trim().isEmpty()) return src;
        String q = v.trim();
        String s = src.replaceFirst("^" + java.util.regex.Pattern.quote(q) + "[ \t]+", "");
        if (s.length() < src.length()) return s;
        return src;
    }
}