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

import java.util.*;
import java.util.regex.*;

public final class MtFileIO {

    private MtFileIO() {}

    private static final String[] MT_TYPE_ITEMS = {
        "Auto-detect", "MT 527", "MT 535", "MT 536", "MT 537",
        "MT 540", "MT 541", "MT 542", "MT 543",
        "MT 544", "MT 545", "MT 546", "MT 547", "MT 548",
        "MT 558", "MT 940", "MT 950"
    };

    public static String[] getMtTypeItems() { return MT_TYPE_ITEMS.clone(); }

    public static final String DEFAULT_LOG_SWIFT_START   = "{1:";
    public static final String DEFAULT_LOG_NEWLINE_TOKEN = "\\n";

    private static final String DEFAULT_ADDR = "BANKBEBBAXXX";
    private static final int    NAME_VALUE_MIN_SEPARATORS = 20;
    private static final String NEWLINE_PATTERN = "\r?\n";

    /** 0-based index of the first content character in append-text lines (column 31). */
    private static final int APPEND_TEXT_CONTENT_COL = 30;
    private static final int APPEND_TEXT_MIN_TAG_LINES = 3;

    private static final Pattern SWIFT_TAG_PAT = Pattern.compile("^:([A-Z0-9]{2,5}):");
    private static final Pattern APPEND_MT_HDR_PAT = Pattern.compile("^(\\d{3}):");
    private static final Pattern XML_CHAR_REF_PAT = Pattern.compile("&#x([\\dA-Fa-f]+);|&#(\\d+);");



    // -----------------------------------------------------------------------
    // Format Detection / Wrapping
    // -----------------------------------------------------------------------

    public static String wrapBlock4IfNeeded(String content, String mtTypeOverride) {
        // Normalize \r\n first; keep standalone \r so embedded CR inside Name-Value
        // field values (e.g. 35B ISIN + description) do not break line detection.
        String crlfNormalized = content.replace("\r\n", "\n");
        String trimmed = crlfNormalized.replace("\r", "\n").trim();
        if (trimmed.startsWith("{1:")) return trimmed;
        String block4Body;
        String effectiveMtOverride = mtTypeOverride;
        String nvCandidate = crlfNormalized.trim();
        if (isAppendTextContent(trimmed)) {
            block4Body = convertAppendTextToBlock4(trimmed);
        } else if (isNameValueContent(nvCandidate)) {
            block4Body = convertNameValueToBlock4(nvCandidate);
            String chunkMt = extractMtTypeFromNameValue(nvCandidate);
            if (chunkMt != null) effectiveMtOverride = chunkMt;
        } else {
            block4Body = stripBlock4Wrapper(trimmed);
        }
        return buildSwiftWrapper(block4Body, effectiveMtOverride);
    }

    private static String buildSwiftWrapper(String block4Body, String mtTypeOverride) {
        String mtType = (mtTypeOverride != null && !mtTypeOverride.isEmpty())
                ? mtTypeOverride
                : detectMtTypeFromBlock4(block4Body);
        return "{1:F01" + DEFAULT_ADDR + "0000000000}"
             + "{2:O" + mtType + "0000000000" + DEFAULT_ADDR + "00000000000000N}"
             + "{3:{108:1}}"
             + "{4:\n" + block4Body + "\n-}";
    }

    private static String detectMtTypeFromBlock4(String block4Body) {
        String detected = matchMtTypeByTags(block4Body);
        return detected != null ? detected : "536";
    }

    private static String matchMtTypeByTags(String body) {
        if (tagPresent(body, ":16R:SUBSAFE"))  return "536";
        if (tagPresent(body, ":16R:TRANSDET")) return "537";
        if (tagPresent(body, ":60F:") || tagPresent(body, ":60M:")) return "940";
        if (tagPresent(body, ":16R:DEALTRAN")) return tagPresent(body, ":16R:STAT") ? "558" : "527";
        return null;
    }

    /** Checks for a tag marker allowing an optional space before the value, e.g. ":16R: SUBSAFE". */
    private static boolean tagPresent(String body, String tag) {
        if (body.contains(tag)) return true;
        int last = tag.lastIndexOf(':');
        return last > 0 && body.contains(tag.substring(0, last + 1) + " " + tag.substring(last + 1));
    }

    private static String stripBlock4Wrapper(String s) {
        if (s.endsWith("-}"))    s = s.substring(0, s.length() - 2).trim();
        if (s.startsWith("{4:")) s = s.substring(3).trim();
        return s;
    }

    /**
     * Returns true when the content is a single-column CSV where each cell contains
     * a Mainframe-encoded SWIFT message (ä→{ ü→}), OR when the content consists of
     * unquoted Mainframe-encoded SWIFT messages (lines starting with ä = '{').
     * Detection reads only the first cell / first decoded line.
     */
    public static boolean isCsvSwiftContent(String content) {
        String trimmed = content.trim();
        if (trimmed.isEmpty()) return false;
        if (trimmed.charAt(0) == '"') {
            int end = findCsvClosingQuote(trimmed, 1);
            if (end < 0) return false;
            String first = fixMainframeEncoding(trimmed.substring(1, end)).trim();
            return isCompleteSwiftMessage(first);
        }
        // Unquoted Mainframe-encoded content: lines start with ä (= '{') instead of '"',
        // optionally preceded by a short prefix (e.g. "00ä" → "00{1:").
        // Must contain ä or ü; without them fixMainframeEncoding is a no-op and any
        // regular SWIFT message starting with {1: would be falsely detected as CSV.
        if (trimmed.indexOf('ä') < 0 && trimmed.indexOf('ü') < 0) return false;
        String decoded = fixMainframeEncoding(trimmed);
        int idx = decoded.indexOf("{1:");
        return idx >= 0 && idx <= 5 && decoded.contains("{4:");
    }

    /**
     * Parses Mainframe-encoded SWIFT content into individual messages.
     * Handles two formats:
     * - Quoted CSV: each cell (enclosed in "…") contains one SWIFT message
     * - Unquoted: each logical message starts with ä (Mainframe '{') at line start;
     *   the whole content is Mainframe-decoded first and then split like a log file.
     * Multi-line cells and RFC 4180 "" escapes are handled for the quoted case.
     * Only entries that form a complete SWIFT message are returned.
     */
    public static List<String> splitCsvIntoSwiftMessages(String content) {
        String trimmed = content.trim();
        if (!trimmed.isEmpty() && trimmed.charAt(0) != '"') {
            return splitLogIntoSwiftMessages(fixMainframeEncoding(content), "{1:", "");
        }
        List<String> messages = new ArrayList<>();
        int i = 0;
        while (i < content.length()) {
            int start = content.indexOf('"', i);
            if (start < 0) break;
            int end = findCsvClosingQuote(content, start + 1);
            if (end >= 0) {
                String raw   = content.substring(start + 1, end).replace("\"\"", "\"");
                String fixed = fixMainframeEncoding(raw).trim();
                if (isCompleteSwiftMessage(fixed)) messages.add(deduplicateBlockClose(fixed));
                i = end + 1;
            } else {
                i = content.length();
            }
        }
        return messages;
    }

    private static int findCsvClosingQuote(String s, int from) {
        int i = from;
        while (i < s.length()) {
            if (s.charAt(i) == '"') {
                if (i + 1 < s.length() && s.charAt(i + 1) == '"') i += 2;
                else return i;
            } else {
                i++;
            }
        }
        return -1;
    }

    /**
     * Parses a log file and extracts embedded SWIFT messages.
     * Handles both single-line messages (newlines encoded via newlineToken) and
     * multi-line messages (real line breaks, continuation lines have no swiftStart).
     * Only complete messages containing {1:, {4:, and ending with } are returned.
     */
    public static List<String> splitLogIntoSwiftMessages(String content, String swiftStart, String newlineToken) {
        List<String> messages = new ArrayList<>();
        StringBuilder pending = null;
        for (String line : content.split(NEWLINE_PATTERN)) {
            int idx = swiftStart.isEmpty() ? -1 : line.indexOf(swiftStart);
            if (idx >= 0) {
                if (pending != null) finalizeMessage(pending.toString(), newlineToken, messages);
                pending = new StringBuilder(line.substring(idx));
            } else if (pending != null) {
                pending.append('\n').append(line);
            }
            if (pending != null) {
                String candidate = processRaw(pending.toString(), newlineToken);
                if (isCompleteSwiftMessage(candidate)) {
                    messages.add(deduplicateBlockClose(candidate));
                    pending = null;
                }
            }
        }
        if (pending != null) finalizeMessage(pending.toString(), newlineToken, messages);
        return messages;
    }

    private static String processRaw(String raw, String newlineToken) {
        String s = newlineToken.isEmpty() ? raw : raw.replace(newlineToken, "\n");
        int lastBrace = s.lastIndexOf('}');
        return (lastBrace >= 0 ? s.substring(0, lastBrace + 1) : s).trim();
    }

    private static boolean isCompleteSwiftMessage(String s) {
        return s.contains("{1:") && s.contains("{4:") && s.endsWith("}");
    }

    private static void finalizeMessage(String raw, String newlineToken, List<String> messages) {
        String processed = processRaw(raw, newlineToken);
        if (isCompleteSwiftMessage(processed)) messages.add(deduplicateBlockClose(processed));
    }

    /**
     * Removes consecutive identical :16S:XXX lines that source systems sometimes
     * produce when splitting a single MT into multiple individual messages.
     */
    static String deduplicateBlockClose(String msg) {
        String[] lines = msg.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        String prev16S = null;
        for (String line : lines) {
            String t = line.strip();
            if (t.startsWith(":16S:") && t.equals(prev16S)) continue;
            prev16S = t.startsWith(":16S:") ? t : null;
            sb.append(line).append('\n');
        }
        return !sb.isEmpty() ? sb.substring(0, sb.length() - 1) : msg;
    }

    /**
     * Splits the file content into individual messages:
     * - Name-Value (first line has &gt;20 semicolons): each such line is one message
     * - SWIFT or block 4: a single message
     */
    public static List<String> splitIntoMessages(String content) {
        String trimmed = content.trim();
        if (isNameValueContent(trimmed)) {
            List<String> msgs = new ArrayList<>();
            for (String line : trimmed.split(NEWLINE_PATTERN)) {
                line = line.trim();
                if (isNameValueLine(line)) msgs.add(line);
            }
            return msgs.isEmpty() ? Collections.singletonList(trimmed) : msgs;
        }
        return Collections.singletonList(trimmed);
    }

    /** Returns true if the first non-empty line of content has more than NAME_VALUE_MIN_SEPARATORS semicolons. */
    private static boolean isNameValueContent(String content) {
        for (String line : content.split(NEWLINE_PATTERN)) {
            String trimmedLine = line.trim();
            if (!trimmedLine.isEmpty()) return isNameValueLine(trimmedLine);
        }
        return false;
    }

    /** Returns true if the line contains more than NAME_VALUE_MIN_SEPARATORS semicolons. */
    private static boolean isNameValueLine(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ';') count++;
        }
        return count > NAME_VALUE_MIN_SEPARATORS;
    }

    /**
     * Returns true if the content is bare block4 (needs an MT-type selection).
     * False for complete SWIFT messages ({1:…}), Name-Value, and Append-Text format.
     */
    public static boolean needsMtTypeOverride(String content) {
        String trimmed = content.trim();
        return !trimmed.startsWith("{1:") && !isNameValueContent(trimmed) && !isAppendTextContent(trimmed);
    }

    /**
     * Returns the MT type number (e.g. "536") when content contains unambiguous markers,
     * or null if the type cannot be determined with confidence.
     * For Name-Value content the MT= entry (e.g. ;MT=536;) is used when present.
     * Returns null for complete SWIFT messages ({1:…}).
     */
    public static String tryDetectMtType(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("{1:")) return null;
        if (isNameValueContent(trimmed)) return extractMtTypeFromNameValue(trimmed);
        if (isAppendTextContent(trimmed)) return detectMtTypeFromAppendText(trimmed);
        return matchMtTypeByTags(stripBlock4Wrapper(trimmed));
    }

    /** Extracts the MT type from a Name-Value line containing ;MT=536; (returns null if absent). */
    private static String extractMtTypeFromNameValue(String content) {
        for (String line : content.split(NEWLINE_PATTERN)) {
            if (!isNameValueLine(line.trim())) continue;
            for (String part : decodeXmlCharRefs(line).split(";")) {
                part = part.trim();
                if (part.startsWith("MT=")) {
                    String mt = part.substring(3).trim();
                    if (mt.matches("\\d{3}")) return mt;
                }
            }
        }
        return null;
    }

    /** Extracts MT type from the append-text header line (e.g. "536: ABSENDER ..."). */
    private static String detectMtTypeFromAppendText(String content) {
        for (String line : content.split(NEWLINE_PATTERN)) {
            Matcher m = APPEND_MT_HDR_PAT.matcher(line.trim());
            if (m.find()) return m.group(1);
        }
        return detectMtTypeFromBlock4(convertAppendTextToBlock4(content));
    }

    /**
     * Decodes XML/HTML numeric character references (&#xHH; and &#DDD;) so that
     * the trailing semicolon of an entity like &#x0d; is not mistaken for a
     * Name-Value field separator during conversion.
     */
    static String decodeXmlCharRefs(String s) {
        Matcher m = XML_CHAR_REF_PAT.matcher(s);
        if (!m.find()) return s;
        StringBuilder sb = new StringBuilder();
        m.reset();
        int last = 0;
        while (m.find()) {
            sb.append(s, last, m.start());
            int cp = m.group(1) != null
                ? Integer.parseInt(m.group(1), 16)
                : Integer.parseInt(m.group(2));
            sb.appendCodePoint(cp);
            last = m.end();
        }
        sb.append(s, last, s.length());
        return sb.toString();
    }

    /**
     * Fixes the classic Mainframe EBCDIC CP273 (German) → CP1252 misconversion
     * that affects SWIFT block delimiters: ä→{  ü→}
     */
    public static String fixMainframeEncoding(String text) {
        return text.replace('ä', '{').replace('ü', '}');
    }

    public static String stripIndentation(String text) {
        StringBuilder sb = new StringBuilder();
        for (String line : text.split(NEWLINE_PATTERN, -1)) {
            sb.append(line.trim()).append('\n');
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Append-Text Conversion
    // -----------------------------------------------------------------------

    /**
     * Returns true when content looks like a fixed-width "Append Text" printout:
     * lines starting with a SWIFT tag followed by a description in columns 6-30
     * and the actual content starting at column 31 (index 30).
     */
    public static boolean isAppendTextContent(String content) {
        int matches = 0;
        for (String line : content.split(NEWLINE_PATTERN)) {
            if (isAppendTextTagLine(line) && ++matches >= APPEND_TEXT_MIN_TAG_LINES) return true;
        }
        return false;
    }

    /**
     * A line qualifies as an append-text tag line when it carries a SWIFT tag,
     * is long enough to contain content at column 31, and has a space at index 29
     * (end of the description column) followed by a non-space content character.
     */
    private static boolean isAppendTextTagLine(String line) {
        if (line.length() <= APPEND_TEXT_CONTENT_COL) return false;
        if (!SWIFT_TAG_PAT.matcher(line).find()) return false;
        return line.charAt(APPEND_TEXT_CONTENT_COL - 1) == ' '
            && line.charAt(APPEND_TEXT_CONTENT_COL) != ' ';
    }

    /**
     * Converts an append-text block into SWIFT block4 content.
     * Only tag lines are processed; description columns are discarded;
     * the content starting at column 31 becomes the tag value.
     */
    public static String convertAppendTextToBlock4(String content) {
        StringBuilder sb = new StringBuilder();
        for (String line : content.split(NEWLINE_PATTERN)) {
            if (!isAppendTextTagLine(line)) continue;
            Matcher m = SWIFT_TAG_PAT.matcher(line);
            if (m.find()) {
                String tag   = m.group(1);
                String value = line.substring(APPEND_TEXT_CONTENT_COL).trim();
                if (!value.isEmpty()) {
                    sb.append(':').append(tag).append(':').append(value).append('\n');
                }
            }
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Name-Value Conversion
    // -----------------------------------------------------------------------

    /**
     * Converts the Name-Value format (...;N_TAG:QUAL=VALUE;...)
     * into SWIFT block 4 content.
     * Section prefix format: {Letter}{optional-digit}_{tag}, e.g. A_28E, A1_95P, B_98B.
     * Optional whitespace before the colon separator is tolerated.
     */
    public static String convertNameValueToBlock4(String content) {
        StringBuilder sb  = new StringBuilder();
        Pattern       pat = Pattern.compile("^[A-Z]\\d*_(\\d{2}[A-Z]+\\s*:.*)$");
        String decoded = decodeXmlCharRefs(content.trim());
        for (String part : decoded.split(";")) {
            processNameValuePart(part.trim(), sb, pat);
        }
        return sb.toString();
    }

    private static void processNameValuePart(String part, StringBuilder sb, Pattern pat) {
        if (part.isEmpty()) return;
        int eqIdx = part.indexOf('=');
        if (eqIdx < 0) return;
        String key = part.substring(0, eqIdx).trim();
        String val = normalizeEmbeddedCr(part.substring(eqIdx + 1).trim())
                         .replaceAll("(\\d{4})-(\\d{2})-(\\d{2})", "$1$2$3")
                         .replaceFirst("^/+", "");
        Matcher m = pat.matcher(key);
        if (!m.matches()) return;
        String tagAndSub = m.group(1).trim();
        int ci = tagAndSub.indexOf(':');
        if (ci < 0) return;
        appendTag(tagAndSub.substring(0, ci).trim(), tagAndSub.substring(ci + 1).trim(), val, sb);
    }

    private static String normalizeEmbeddedCr(String val) {
        if (val.indexOf('\r') < 0) return val;
        StringBuilder sb = new StringBuilder();
        for (String segment : val.split("\r", -1)) {
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(segment.trim());
        }
        return sb.toString();
    }

    private static void appendTag(String tag, String sub, String val, StringBuilder sb) {
        if ("16R".equals(tag) || "16S".equals(tag)) {
            sb.append(':').append(tag).append(':').append(sub).append('\n');
        } else if ("35B".equals(tag)) {
            append35B(sb, sub, val);
        } else if ("28E".equals(tag)) {
            sb.append(":28E:").append(sub);
            if (!val.isEmpty()) sb.append('/').append(val);
            sb.append('\n');
        } else if (val.isEmpty()) {
            sb.append(':').append(tag).append(':').append(sub).append('\n');
        } else if (sub.isEmpty()) {
            sb.append(':').append(tag).append(':').append(val).append('\n');
        } else {
            sb.append(':').append(tag).append("::").append(sub)
              .append("//").append(val).append('\n');
        }
    }

    private static void append35B(StringBuilder sb, String sub, String val) {
        sb.append(":35B:");
        if (!sub.isEmpty()) {
            sb.append(sub);
            if (!val.isEmpty()) sb.append(' ');
        }
        sb.append(val).append('\n');
    }
}