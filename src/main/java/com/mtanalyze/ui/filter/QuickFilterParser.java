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
package com.mtanalyze.ui.filter;

import java.util.Locale;

/**
 * Parses and evaluates Quick Filter expressions against a cell value.
 *
 * <p>Supported operators:
 * <pre>
 *   =val       equal (case-insensitive)
 *   !=val      not equal          (also: !val, &lt;&gt;val)
 *   &lt;val       less than
 *   &gt;val       greater than
 *   &lt;=val      less or equal
 *   &gt;=val      greater or equal
 *   ^val       begins with
 *   !^val      does not begin with
 *   $val       ends with
 *   %val       contains
 *   !%val      does not contain
 *   lo-hi      between lo and hi  (lexicographic, inclusive)
 *   (none)     contains (default)
 * </pre>
 *
 * <p>Multiple terms can be combined with {@code +} (OR semantics):
 * <pre>
 *   =EUR+GBP      value is EUR or GBP
 *   ^DE+^AT       begins with DE or AT
 * </pre>
 */
public final class QuickFilterParser {

    private QuickFilterParser() {}

    /**
     * Returns {@code true} when {@code cellValue} satisfies {@code expression}.
     * An empty or blank expression always matches (no filter active).
     */
    public static boolean matches(String expression, String cellValue) {
        if (expression == null || expression.isBlank()) return true;
        String[] terms = expression.split("\\+", -1);
        for (String term : terms) {
            if (matchesTerm(term.trim(), cellValue)) return true;   // OR semantics
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Term dispatch
    // -----------------------------------------------------------------------

    private static boolean matchesTerm(String term, String cell) {
        if (term.isEmpty()) return true;
        String cv = cell.toLowerCase(Locale.ROOT);
        if (isNegation(term))   return evalNegation(term, cv);
        if (isComparison(term)) return evalComparison(term, cv);
        if (isPattern(term))    return evalPattern(term, cv);
        if (term.startsWith("=")) return cv.equalsIgnoreCase(term.substring(1));
        int dash = term.indexOf('-');
        if (dash > 0 && dash < term.length() - 1) return evalBetween(term, cv, dash);
        return cv.contains(term.toLowerCase(Locale.ROOT));   // default: contains
    }

    // -----------------------------------------------------------------------
    // Negation:  !=  <>  !^  !%  !
    // -----------------------------------------------------------------------

    private static boolean isNegation(String t) {
        return t.startsWith("!=") || t.startsWith("<>")
            || t.startsWith("!^") || t.startsWith("!%")
            || (t.startsWith("!") && t.length() > 1);
    }

    private static boolean evalNegation(String term, String cv) {
        if (term.startsWith("!=") || term.startsWith("<>"))
            return !cv.equalsIgnoreCase(term.substring(2));
        if (term.startsWith("!^"))
            return !cv.startsWith(term.substring(2).toLowerCase(Locale.ROOT));
        if (term.startsWith("!%"))
            return !cv.contains(term.substring(2).toLowerCase(Locale.ROOT));
        return !cv.equalsIgnoreCase(term.substring(1));   // plain !val
    }

    // -----------------------------------------------------------------------
    // Comparison:  >=  <=  >  <
    // -----------------------------------------------------------------------

    private static boolean isComparison(String t) {
        return t.startsWith(">=") || t.startsWith("<=")
            || t.startsWith(">")  || t.startsWith("<");
    }

    private static boolean evalComparison(String term, String cv) {
        if (term.startsWith(">=")) return cv.compareToIgnoreCase(term.substring(2)) >= 0;
        if (term.startsWith("<=")) return cv.compareToIgnoreCase(term.substring(2)) <= 0;
        if (term.startsWith(">"))  return cv.compareToIgnoreCase(term.substring(1)) > 0;
        return cv.compareToIgnoreCase(term.substring(1)) < 0;   // <
    }

    // -----------------------------------------------------------------------
    // Pattern:  ^  $  %
    // -----------------------------------------------------------------------

    private static boolean isPattern(String t) {
        return t.startsWith("^") || t.startsWith("$") || t.startsWith("%");
    }

    private static boolean evalPattern(String term, String cv) {
        String val = term.substring(1).toLowerCase(Locale.ROOT);
        if (term.startsWith("^")) return cv.startsWith(val);
        if (term.startsWith("$")) return cv.endsWith(val);
        return cv.contains(val);   // %
    }

    // -----------------------------------------------------------------------
    // Between:  lo-hi
    // -----------------------------------------------------------------------

    private static boolean evalBetween(String term, String cv, int dashIdx) {
        String lo = term.substring(0, dashIdx).toLowerCase(Locale.ROOT);
        String hi = term.substring(dashIdx + 1).toLowerCase(Locale.ROOT);
        return cv.compareTo(lo) >= 0 && cv.compareTo(hi) <= 0;
    }
}