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

import com.prowidesoftware.swift.model.SwiftBlock2Input;
import com.prowidesoftware.swift.model.SwiftBlock2Output;
import com.prowidesoftware.swift.model.Tag;
import com.prowidesoftware.swift.model.field.Field;
import com.prowidesoftware.swift.model.mt.AbstractMT;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Converts one line of a back-office Name-Value export
 * ({@code MT=548;SWIFTABS=...;SWIFTEMP=...;A2_20C:TRRF=...;...}) into the
 * matching {@link AbstractMT} SWIFT MT message.
 *
 * <p>Unlike the CSV / Name-Value formats handled by {@link MtFileIO}, this
 * format carries no explicit {@code :16R:}/{@code :16S:} sequence markers -
 * each field is prefixed with a bare sequence code (e.g. {@code A}, {@code A1},
 * {@code C1a1B1a1}) instead. {@link #translateSequence} maps that code to the
 * SWIFT block name it opens for the message's MT type, and the 16R/16S pair
 * is synthesized from a stack of currently open sequences as fields arrive.
 * The code -&gt; block-name mapping itself is not hand-maintained: {@link ProwideSequences}
 * reads it via reflection from Prowide's generated {@code MT<type>$Sequence<code>}
 * classes, so every MT type Prowide ships is supported automatically and stays correct
 * across SRU updates.
 */
public final class NameValueConverter {

    /** Strips the literal midnight time-of-day suffix appended to date-only values. */
    private static final Pattern MIDNIGHT_SUFFIX = Pattern.compile(" 00:00:00\\.000000");

    /**
     * Tag order shared by the repeating party subsequences {@code SETPRTY},
     * {@code CSHPRTY} and {@code CONFPRTY}. Such a subsequence repeats under the
     * same bare sequence code, so the boundary between two parties is only visible
     * from the tags: a field whose tag is not positioned after the previous
     * field's tag starts the next party and forces a synthesized
     * {@code :16S:}/{@code :16R:} pair.
     */
    private static final List<String> SETPRTY_TAG_ORDER = List.of("95", "97", "98", "20", "70");

    // SWIFT block names compared against translateSequence()'s result below.
    private static final String SETPRTY = "SETPRTY";
    private static final String CSHPRTY = "CSHPRTY";
    private static final String CONFPRTY = "CONFPRTY";

    private final ArrayList<String> sequenceStack = new ArrayList<>();
    private final Logger logger = Logger.getLogger(getClass().getName());

    /**
     * True when {@code content} is a single-line Name-Value message that uses bare
     * sequence codes (e.g. {@code A2_20C:TRRF=...}) instead of explicit
     * {@code :16R:}/{@code :16S:} markers, and therefore needs its sequence boundaries
     * synthesized by {@link #convert} rather than being handled by
     * {@link MtFileIO#convertNameValueToBlock4}, which expects those markers to
     * already be present in the source.
     */
    public static boolean isSequenceCodeFormat(String content) {
        boolean hasMt = false;
        boolean hasSequencedField = false;
        for (String part : content.split(";")) {
            String key = part.trim();
            int eq = key.indexOf('=');
            if (eq < 0) continue;
            String name = key.substring(0, eq).trim();
            if ("MT".equals(name)) { hasMt = true; continue; }
            int us = name.indexOf('_');
            if (us < 0) continue;
            String tag = name.substring(us + 1).split(":")[0].trim();
            if (!tag.matches("\\d{2}[A-Z]+|5R")) continue;
            if ("16R".equals(tag) || "16S".equals(tag)) return false;
            hasSequencedField = true;
        }
        return hasMt && hasSequencedField;
    }

    /**
     * Maps a Name-Value sequence code (e.g. {@code "A1"}) to the SWIFT block name it opens
     * for {@code mt}. Throws when {@code mt} is unknown to Prowide, or when {@code sequence}
     * does not match any of {@code mt}'s sequence codes -- silently falling back to a flat,
     * unstructured field or a placeholder block name would hide a real mismatch (typo,
     * unsupported export convention) instead of surfacing it as a parse error.
     */
    public String translateSequence(int mt, String sequence) {
        Map<String, String> blocks = ProwideSequences.byLetterPath(mt);
        if (blocks == null) {
            throw new IllegalArgumentException("Message type %d is not supported by Prowide".formatted(mt));
        }
        String block = blocks.get(sequence);
        if (block == null) {
            throw new IllegalArgumentException(
                ("MT %d: unknown sequence code '%s' -- no matching :16R:/:16S: block "
                    + "in the SWIFT standard for this message type").formatted(mt, sequence));
        }
        return block;
    }

    /** Converts one Name-Value line into the corresponding SWIFT MT message. */
    public AbstractMT convert(String line) {
        AbstractMT swiftMessage = AbstractMT.create(599);

        String lastSequence = "";
        int setPrtyTagIndex = -1;

        int mt = 0;
        line = line.replace("&#x0d;", "\n");
        line = MIDNIGHT_SUFFIX.matcher(line).replaceAll("");
        // Known export quirk: MT 558's RELA reference sometimes arrives without its
        // A3 (LINK) sequence prefix.
        if (line.contains("MT=558")) line = line.replace(";_20C:RELA", ";A3_20C:RELA");
        String[] fields = line.split(";");

        for (String field : fields) {
            String[] nameValues = field.split("=");
            if (nameValues.length >= 2) {
                String name = nameValues[0];
                String value = nameValues[1].stripLeading();

                switch (name) {
                    case "MT" -> {
                        mt = Integer.parseInt(value);

                        swiftMessage = AbstractMT.create(mt);

                        // A fresh Block2 carries no message type of its own; without it
                        // getMessageType() returns null downstream, which breaks MT-type-based
                        // row-sequence detection (e.g. flat vs. sequenced parsing).
                        if (mt == 527 || (mt >= 540 && mt <= 544)) {
                            SwiftBlock2Input block2 = new SwiftBlock2Input();
                            block2.setMessageType(value);
                            swiftMessage.getSwiftMessage().setBlock2(block2);
                        } else {
                            SwiftBlock2Output block2 = new SwiftBlock2Output();
                            block2.setMessageType(value);
                            swiftMessage.getSwiftMessage().setBlock2(block2);
                        }
                    }
                    case "SWIFTABS" -> {
                        if (swiftMessage.getSwiftMessage().getBlock2().isInput()) {
                            SwiftBlock2Input block2 = (SwiftBlock2Input) swiftMessage.getSwiftMessage().getBlock2();
                            block2.setReceiver(value);
                        } else {
                            SwiftBlock2Output block2 = (SwiftBlock2Output) swiftMessage.getSwiftMessage().getBlock2();
                            block2.setSender(value);
                        }
                    }
                    case "SWIFTEMP" -> swiftMessage.getSwiftMessage().getBlock1().setSender(value);
                    default -> {
                        String[] seqs = nameValues[0].split("_");
                        if (seqs.length >= 2) {
                            String seq = seqs[0];
                            String tag = seqs[1].replace(" ", "");
                            String[] tagFields = tag.split(":");

                            if (!seq.contains(lastSequence) && !sequenceStack.isEmpty()) {
                                String last = sequenceStack.get(sequenceStack.size() - 1);
                                closeSequence("16S", mt, last, swiftMessage);
                                sequenceStack.remove(sequenceStack.size() - 1);
                                if (!sequenceStack.isEmpty()) {
                                    last = sequenceStack.get(sequenceStack.size() - 1);
                                    if (!seq.contains(last)) {
                                        closeSequence("16S", mt, last, swiftMessage);
                                        sequenceStack.remove(sequenceStack.size() - 1);
                                    }
                                }
                            }

                            boolean freshSequence = !lastSequence.equals(seq);
                            if (freshSequence) {
                                closeSequence("16R", mt, seq, swiftMessage);
                                sequenceStack.add(seq);
                                setPrtyTagIndex = -1;
                            }

                            // A SETPRTY / CSHPRTY / CONFPRTY subsequence can occur several times in a
                            // row under the same bare sequence code. Its fields arrive in the fixed
                            // order 95a, 97a, 98a, 20C, 70a, so a tag that is not after the previous
                            // one belongs to the next party: close the running party and open a fresh one.
                            String blockName = translateSequence(mt, seq);
                            if (SETPRTY.equals(blockName) || CSHPRTY.equals(blockName) || CONFPRTY.equals(blockName)) {
                                int tagIndex = setPrtyTagOrder(tagFields[0]);
                                if (tagIndex >= 0) {
                                    if (!freshSequence && tagIndex <= setPrtyTagIndex) {
                                        closeSequence("16S", mt, seq, swiftMessage);
                                        closeSequence("16R", mt, seq, swiftMessage);
                                    }
                                    setPrtyTagIndex = tagIndex;
                                }
                            }

                            if (tagFields.length >= 2) {
                                try {
                                    if (tagFields[0].startsWith("98")) {
                                        value = value.replace("-", "");
                                        value = value.replace(" ", "");
                                        value = value.replace(":", "");
                                    }
                                    if (tagFields[0].startsWith("5R")) {
                                        tagFields[0] = "95R";
                                    }
                                    swiftMessage.append(Field.getField(tagFields[0], tagFields[1] + "//" + value));
                                } catch (Exception ex) {
                                    logger.warning(line);
                                    logger.severe(ex.getMessage());
                                }
                            }
                            lastSequence = seq;
                        }
                    }
                }
            }
        }
        while (!sequenceStack.isEmpty()) {
            String last = sequenceStack.get(sequenceStack.size() - 1);
            closeSequence("16S", mt, last, swiftMessage);
            sequenceStack.remove(sequenceStack.size() - 1);
        }
        return swiftMessage;
    }

    /**
     * Position of {@code tagName} within {@link #SETPRTY_TAG_ORDER}, matched on the
     * leading digits ({@code 95P} -&gt; {@code 95}), or {@code -1} when the tag is
     * not part of the SETPRTY ordering.
     */
    private static int setPrtyTagOrder(String tagName) {
        String prefix = tagName.length() >= 2 ? tagName.substring(0, 2) : tagName;
        return SETPRTY_TAG_ORDER.indexOf(prefix);
    }

    private void closeSequence(String name, int mt, String last, AbstractMT swiftMessage) {
        Tag closeTag = new Tag();
        closeTag.setNameValue(name, translateSequence(mt, last));
        swiftMessage.append(closeTag);
    }
}
