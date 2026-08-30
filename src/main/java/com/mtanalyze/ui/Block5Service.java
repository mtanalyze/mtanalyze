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
package com.mtanalyze.ui;

/**
 * Pure text-based utility for attaching or replacing the MAC tag in a SWIFT FIN Block 5.
 * Works directly on the raw FIN text to preserve all original formatting.
 */
final class Block5Service {

    private Block5Service() {}

    /**
     * Attaches or replaces the MAC tag in Block 5 of a SWIFT FIN text.
     *
     * <ul>
     *   <li>If Block 5 is absent, {@code {5:{MAC:macValue}}} is appended after Block 4.</li>
     *   <li>If Block 5 is present, MAC is replaced (or inserted first if missing); other tags kept.</li>
     * </ul>
     *
     * @param finText  raw SWIFT FIN text
     * @param macValue 8 uppercase hex characters (e.g. {@code 00000000})
     * @return modified FIN text
     * @throws IllegalArgumentException if the Block 4 end marker {@code -}} is not found
     */
    static String attachMac(String finText, String macValue) {
        int block4End = finText.lastIndexOf("-}");
        if (block4End < 0)
            throw new IllegalArgumentException("Block 4 end marker '-}' not found in file.");

        String nl    = finText.contains("\r\n") ? "\r\n" : "\n";
        int    b5Pos = finText.indexOf("{5:", block4End);

        if (b5Pos >= 0) {
            int b5End = findMatchingBrace(finText, b5Pos);
            if (b5End < 0)
                throw new IllegalArgumentException("Malformed Block 5: unmatched braces.");
            String inner    = finText.substring(b5Pos + 3, b5End);
            String newInner = prependMac(inner, macValue);
            return finText.substring(0, b5Pos) + "{5:" + newInner + "}" + finText.substring(b5End + 1);
        }

        // Append Block 5 right after "-}"
        return finText.substring(0, block4End + 2) + nl + "{5:{MAC:" + macValue + "}}";
    }

    /** Returns true if Block 5 is present in the FIN text (searched after Block 4 end). */
    static boolean hasBlock5(String finText) {
        int block4End = finText.lastIndexOf("-}");
        return block4End >= 0 && finText.indexOf("{5:", block4End) >= 0;
    }

    /** Returns the MAC value from Block 5, or {@code null} if Block 5 or MAC tag is absent. */
    static String findExistingMac(String finText) {
        int block4End = finText.lastIndexOf("-}");
        if (block4End < 0) return null;
        int b5Pos = finText.indexOf("{5:", block4End);
        if (b5Pos < 0) return null;
        int macTag = finText.indexOf("{MAC:", b5Pos);
        if (macTag < 0) return null;
        int macEnd = finText.indexOf("}", macTag + 5);
        if (macEnd < 0) return null;
        return finText.substring(macTag + 5, macEnd);
    }

    // -----------------------------------------------------------------------

    /** Removes any existing {MAC:...} from inner Block 5 content and prepends a fresh one. */
    private static String prependMac(String inner, String macValue) {
        // MAC values are 8 hex chars – no '}' inside, so [^}]* is safe
        String withoutMac = inner.replaceAll("\\{MAC:[^}]*}", "");
        return "{MAC:" + macValue + "}" + withoutMac;
    }

    private static int findMatchingBrace(String text, int start) {
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if      (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i;
        }
        return -1;
    }
}