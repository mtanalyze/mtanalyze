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
import java.util.HashMap;
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

    /**
     * Name-Value sequence code -&gt; SWIFT block name it opens, keyed by MT type.
     * MT types that share an identical sequence layout (e.g. 540-543) map to the
     * same inner map instance.
     */
    // SWIFT block names referenced from more than one MT layout below.
    private static final String ADDINFO = "ADDINFO";
    private static final String BREAK = "BREAK";
    private static final String TRANSDET = "TRANSDET";
    private static final String SETPRTY = "SETPRTY";
    private static final String CSHPRTY = "CSHPRTY";
    private static final String CONFPRTY = "CONFPRTY";
    private static final String SETDET = "SETDET";
    private static final String COLLPRTY = "COLLPRTY";
    private static final String SECMOVE = "SECMOVE";
    private static final String CASHMOVE = "CASHMOVE";
    private static final String USECU = "USECU";

    private static final Map<Integer, Map<String, String>> SEQUENCE_BLOCKS = buildSequenceBlocks();

    private final ArrayList<String> sequenceStack = new ArrayList<>();
    private final Logger logger = Logger.getLogger(getClass().getName());

    private static Map<Integer, Map<String, String>> buildSequenceBlocks() {
        Map<Integer, Map<String, String>> byMt = new HashMap<>();

        byMt.put(530, Map.ofEntries(
                Map.entry("A", "GENL"),
                Map.entry("B", "REQD"),
                Map.entry("B1", "LINK"),
                Map.entry("C", ADDINFO),
                Map.entry("C1", "STAT"),
                Map.entry("C1a", "REAS")));

        byMt.put(535, Map.ofEntries(
                Map.entry("A", "GENL"),
                Map.entry("A1", "LINK"),
                Map.entry("B", "SUBSAFE"),
                Map.entry("B1", "FIN"),
                Map.entry("B1a", "FIA"),
                Map.entry("B1b", "SUBBAL"),
                Map.entry("B1b1", BREAK),
                Map.entry("B1c", BREAK),
                Map.entry("C", ADDINFO)));

        byMt.put(537, Map.ofEntries(
                Map.entry("A", "GENL"),
                Map.entry("A1", "LINK"),
                Map.entry("B", "STAT"),
                Map.entry("B1", "REAS"),
                Map.entry("B2", "TRAN"),
                Map.entry("B2a", "LINK"),
                Map.entry("B2b", TRANSDET),
                Map.entry("B2b1", SETPRTY),
                Map.entry("C", "TRANS"),
                Map.entry("C1", "LINK"),
                Map.entry("C2", TRANSDET),
                Map.entry("C2a", SETPRTY),
                Map.entry("C3", "STAT"),
                Map.entry("C3a", "REAS"),
                Map.entry("D", "PENA"),
                Map.entry("D1", "PENACUR"),
                Map.entry("D1a", "PENACOUNT"),
                Map.entry("D1a1", "PENDET"),
                Map.entry("D1a1A", "CALDET"),
                Map.entry("D1a1A1", "FIA"),
                Map.entry("D1a1B", "RELTRAN"),
                Map.entry("D1a1B1", "TRAN"),
                Map.entry("D1a1B1a", "STAT"),
                Map.entry("D1a1B1a1", "REAS"),
                Map.entry("E", ADDINFO)));

        byMt.put(527, Map.ofEntries(
                Map.entry("A", "GENL"),
                Map.entry("A1", COLLPRTY),
                Map.entry("A2", "LINK"),
                Map.entry("B", "DEALTRAN"),
                Map.entry("B1", BREAK),
                Map.entry("C", SECMOVE),
                Map.entry("D", CASHMOVE),
                Map.entry("E", ADDINFO)));

        byMt.put(509, Map.ofEntries(
                Map.entry("A", "GENL"),
                Map.entry("A1", "LINK"),
                Map.entry("A2", "STAT"),
                Map.entry("A2a", "REAS"),
                Map.entry("B", "TRADE"),
                Map.entry("B1", "TRADPRTY"),
                Map.entry("C", ADDINFO)));

        // MT 514 (Trade Allocation Instruction) and MT 518 (Market-Side Securities
        // Trade Confirmation) share an identical sequence layout.
        Map<String, String> tradeConfirm = Map.ofEntries(
                Map.entry("A", "GENL"),
                Map.entry("A1", "LINK"),
                Map.entry("B", "CONFDET"),
                Map.entry("B1", CONFPRTY),
                Map.entry("B2", "FIA"),
                Map.entry("C", SETDET),
                Map.entry("C1", SETPRTY),
                Map.entry("C2", CSHPRTY),
                Map.entry("C3", "AMT"),
                Map.entry("D", "OTHRPRTY"),
                Map.entry("E", "REPO"));
        putAll(byMt, tradeConfirm, 514, 518);

        byMt.put(515, Map.ofEntries(
                Map.entry("A", "GENL"),
                Map.entry("A1", "LINK"),
                Map.entry("B", "PAFILL"),
                Map.entry("C", "CONFDET"),
                Map.entry("C1", CONFPRTY),
                Map.entry("C2", "FIA"),
                Map.entry("D", SETDET),
                Map.entry("D1", SETPRTY),
                Map.entry("D2", CSHPRTY),
                Map.entry("D3", "AMT"),
                Map.entry("E", "OTHRPRTY"),
                Map.entry("F", "REPO")));

        byMt.put(517, Map.ofEntries(
                Map.entry("A", "GENL"),
                Map.entry("A1", "LINK")));

        Map<String, String> deliverReceive = Map.ofEntries(
                Map.entry("A", "GENL"),
                Map.entry("A1", "LINK"),
                Map.entry("B", "TRADDET"),
                Map.entry("B1", "FIA"),
                Map.entry("C", "FIAC"),
                Map.entry("D", "REPO"),
                Map.entry("E", SETDET),
                Map.entry("E1", SETPRTY),
                Map.entry("E2", CSHPRTY),
                Map.entry("E3", "AMT"));
        putAll(byMt, deliverReceive, 540, 541, 542, 543);

        Map<String, String> confirmation = Map.ofEntries(
                Map.entry("A", "GENL"),
                Map.entry("A1", "LINK"),
                Map.entry("B", TRANSDET),
                Map.entry("B1", "FIA"),
                Map.entry("C", "FIAC"),
                Map.entry("E", SETDET),
                Map.entry("E1", SETPRTY),
                Map.entry("E2", CSHPRTY),
                Map.entry("E3", "AMT"));
        putAll(byMt, confirmation, 544, 545, 546, 547);

        byMt.put(548, Map.ofEntries(
                Map.entry("A", "GENL"),
                Map.entry("A1", "LINK"),
                Map.entry("A2", "STAT"),
                Map.entry("A2a", "REAS"),
                Map.entry("B", "SETTRAN"),
                Map.entry("B1", SETPRTY),
                Map.entry("C", "PENA"),
                Map.entry("C1", "PENACUR"),
                Map.entry("C1a", "PENACOUNT"),
                Map.entry("C1a1", "PENDET"),
                Map.entry("C1a1A", "CALDET"),
                Map.entry("C1a1A1", "FIA"),
                Map.entry("C1a1B", "RELTRAN"),
                Map.entry("C1a1B1", "TRAN"),
                Map.entry("C1a1B1a", "STAT"),
                Map.entry("C1a1B1a1", "REAS"),
                Map.entry("D", ADDINFO)));

        byMt.put(558, Map.ofEntries(
                Map.entry("A", "GENL"),
                Map.entry("A1", COLLPRTY),
                Map.entry("A2", "STAT"),
                Map.entry("A2a", "REAS"),
                Map.entry("A3", "LINK"),
                Map.entry("B", "DEALTRAN"),
                Map.entry("B1", BREAK),
                Map.entry("C", SECMOVE),
                Map.entry("D", CASHMOVE),
                Map.entry("E", ADDINFO)));

        byMt.put(564, Map.ofEntries(
                Map.entry("A", "GENL"),
                Map.entry("A1", "LINK"),
                Map.entry("A2", "REVR"),
                Map.entry("B", USECU),
                Map.entry("B1", "FIA"),
                Map.entry("B2", "ACCTINFO"),
                Map.entry("C", "INTSEC"),
                Map.entry("D", "CADETL"),
                Map.entry("E", "CAOPTN"),
                Map.entry("E1", SECMOVE),
                Map.entry("E1a", "FIA"),
                Map.entry("E2", CASHMOVE),
                Map.entry("F", ADDINFO)));

        byMt.put(565, Map.ofEntries(
                Map.entry("A", "GENL"),
                Map.entry("A1", "LINK"),
                Map.entry("B", USECU),
                Map.entry("B1", "FIA"),
                Map.entry("B2", "ACCTINFO"),
                Map.entry("C", "BENODET"),
                Map.entry("D", "CAINST"),
                Map.entry("E", ADDINFO)));

        byMt.put(567, Map.ofEntries(
                Map.entry("A", "GENL"),
                Map.entry("A1", "LINK"),
                Map.entry("A2", "STAT"),
                Map.entry("A2a", "REAS"),
                Map.entry("B", "CADETL"),
                Map.entry("C", ADDINFO)));

        byMt.put(568, Map.ofEntries(
                Map.entry("A", "GENL"),
                Map.entry("A1", "LINK"),
                Map.entry("B", USECU),
                Map.entry("B1", "FIA"),
                Map.entry("C", ADDINFO)));

        byMt.put(569, Map.ofEntries(
                Map.entry("A", "GENL"),
                Map.entry("A1", COLLPRTY),
                Map.entry("A2", "LINK"),
                Map.entry("B", "SUMM"),
                Map.entry("C", "SUME"),
                Map.entry("C1", "SUMC"),
                Map.entry("C1a", TRANSDET),
                Map.entry("C1a1", "VALDET"),
                Map.entry("C1a1A", "SECDET"),
                Map.entry("D", ADDINFO)));

        byMt.put(578, Map.ofEntries(
                Map.entry("A", "GENL"),
                Map.entry("A1", "LINK"),
                Map.entry("B", "TRADDET"),
                Map.entry("B1", "FIA"),
                Map.entry("C", "FIAC"),
                Map.entry("C1", BREAK),
                Map.entry("D", "REPO"),
                Map.entry("E", SETDET),
                Map.entry("E1", SETPRTY),
                Map.entry("E2", CSHPRTY),
                Map.entry("E3", "AMT"),
                Map.entry("F", ADDINFO)));

        return Map.copyOf(byMt);
    }

    private static void putAll(Map<Integer, Map<String, String>> target,
                               Map<String, String> blocks, int... mts) {
        for (int mt : mts) {
            target.put(mt, blocks);
        }
    }

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

    /** Maps a Name-Value sequence code (e.g. {@code "A1"}) to the SWIFT block name it opens for {@code mt}. */
    public String translateSequence(int mt, String sequence) {
        Map<String, String> blocks = SEQUENCE_BLOCKS.get(mt);
        if (blocks == null) {
            throw new IllegalArgumentException("Message type %s is not implemented".formatted(mt));
        }
        String block = blocks.get(sequence);
        if (block == null) {
            logger.warning("Unknown MT %s sequence: %s".formatted(mt, sequence));
            return "XXX";
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
