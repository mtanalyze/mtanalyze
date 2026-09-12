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
package com.mtanalyze.elastic;

import com.prowidesoftware.swift.io.parser.SwiftParser;
import com.prowidesoftware.swift.model.SwiftBlock4;
import com.prowidesoftware.swift.model.SwiftMessage;
import com.prowidesoftware.swift.model.Tag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for SWIFT MT (ISO 15022) messages based on Prowide Core.
 * <p>
 * Prowide splits the message into blocks 1-5 and exposes the individual fields
 * of block 4 (text block) as {@link Tag} objects (name + value), including
 * correct handling of multi-line fields and repeated fields.
 * <p>
 * We deliberately use only the generic tag level (no specific MTxxx model),
 * because we only want to search and return the complete original document --
 * not validate it semantically.
 * <p>
 * Ported from the standalone {@code mtelastic} command-line tool.
 */
public final class MessageParser {

    private MessageParser() {
    }

    /** Parses the raw text into a {@link SwiftMessage}; null on empty/invalid input. */
    private static SwiftMessage parse(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return null;
        }
        try {
            SwiftMessage msg = new SwiftParser(rawMessage).message();
            return (msg != null && msg.getBlockCount() > 0) ? msg : null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Extracts all fields from block 4 of the given SWIFT MT raw text.
     * Returns a map of tag -> list of trimmed values (insertion order is
     * preserved, both for tags and for the values within a tag).
     * Repeated tags (common in category 5: {@code :16R:} / {@code :16S:},
     * {@code :20C:}, {@code :98A:} ...) keep every occurrence, so each value
     * stays individually searchable in Elasticsearch.
     */
    public static Map<String, List<String>> extractTags(String rawMessage) {
        Map<String, List<String>> tags = new LinkedHashMap<>();
        SwiftMessage msg = parse(rawMessage);
        if (msg == null) {
            return tags;
        }
        SwiftBlock4 block4 = msg.getBlock4();
        if (block4 == null) {
            return tags;
        }
        for (Tag tag : block4.getTags()) {
            String value = tag.getValue();
            tags.computeIfAbsent(tag.getName(), k -> new ArrayList<>())
                    .add(value == null ? "" : value.trim());
        }
        return tags;
    }

    /** Message type from the application header (block 2), e.g. "536" for MT 536. */
    public static String extractMessageType(String rawMessage) {
        SwiftMessage msg = parse(rawMessage);
        if (msg == null) {
            return null;
        }
        String type = msg.getType();
        return (type == null || type.isBlank()) ? null : type;
    }

    /**
     * Logical Terminal (LT address) of the sender, e.g. "BANKUS33AXXX".
     * Prowide accounts for the direction: from block 1 for input, from block 2 for output.
     */
    public static String extractSender(String rawMessage) {
        SwiftMessage msg = parse(rawMessage);
        return msg == null ? null : blankToNull(msg.getSender());
    }

    /**
     * Logical Terminal (LT address) of the receiver, e.g. "BANKBEBBAXXX".
     * Prowide accounts for the direction: from block 2 for input, from block 1 for output.
     */
    public static String extractReceiver(String rawMessage) {
        SwiftMessage msg = parse(rawMessage);
        return msg == null ? null : blankToNull(msg.getReceiver());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
