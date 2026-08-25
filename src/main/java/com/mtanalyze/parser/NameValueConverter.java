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

import com.prowidesoftware.swift.model.SwiftBlock2Input;
import com.prowidesoftware.swift.model.SwiftBlock2Output;
import com.prowidesoftware.swift.model.Tag;
import com.prowidesoftware.swift.model.field.Field;
import com.prowidesoftware.swift.model.mt.AbstractMT;

import java.util.ArrayList;
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

    private final ArrayList<String> sequenceStack = new ArrayList<>();
    private final Logger logger = Logger.getLogger(getClass().getName());

    /**
     * True when {@code content} is a single-line Name-Value message that uses bare
     * sequence codes (e.g. {@code A2_20C:TRRF=...}) instead of explicit
     * {@code :16R:}/{@code :16S:} markers, and therefore needs its sequence boundaries
     * synthesized by {@link #process} rather than being handled by
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
        switch (mt) {
            case 527:
                switch (sequence) {
                    case "A":
                        return "GENL";
                    case "A1":
                        return "COLLPRTY";
                    case "A2":
                        return "LINK";
                    case "B":
                        return "DEALTRAN";
                    case "B1":
                        return "BREAK";
                    case "C":
                        return "SECMOVE";
                    case "D":
                        return "CASHMOVE";
                    case "E":
                        return "ADDINFO";
                    default:
                }
                break;
            case 540, 541, 542, 543:
                switch (sequence) {
                    case "A":
                        return "GENL";
                    case "A1":
                        return "LINK";
                    case "B":
                        return "TRADDET";
                    case "B1":
                        return "FIA";
                    case "C":
                        return "FIAC";
                    case "D":
                        return "REPO";
                    case "E":
                        return "SETDET";
                    case "E1":
                        return "SETPRTY";
                    case "E3":
                        return "AMT";
                    default:
                        logger.warning("Unknown MT %s sequence: %s".formatted(mt, sequence));
                }
                break;
            case 544, 545, 546, 547:
                switch (sequence) {
                    case "A":
                        return "GENL";
                    case "A1":
                        return "LINK";
                    case "B":
                        return "TRANSDET";
                    case "B1":
                        return "FIA";
                    case "C":
                        return "FIAC";
                    case "E":
                        return "SETDET";
                    case "E1":
                        return "SETPRTY";
                    case "E3":
                        return "AMT";
                    default:
                        logger.warning("Unknown MT %s sequence: %s".formatted(mt, sequence));
                }
                break;
            case 548:
                switch (sequence) {
                    case "A":
                        return "GENL";
                    case "A1":
                        return "LINK";
                    case "A2":
                        return "STAT";
                    case "A2a":
                        return "REAS";
                    case "B":
                        return "SETTRAN";
                    case "B1":
                        return "SETPRTY";
                    case "C":
                        return "PENA";
                    case "C1":
                        return "PENACUR";
                    case "C1a":
                        return "PENACOUNT";
                    case "C1a1":
                        return "PENDET";
                    case "C1a1A":
                        return "CALDET";
                    case "C1a1A1":
                        return "FIA";
                    case "C1a1B":
                        return "RELTRAN";
                    case "C1a1B1":
                        return "TRAN";
                    case "C1a1B1a":
                        return "STAT";
                    case "C1a1B1a1":
                        return "REAS";
                    case "D":
                        return "ADDINFO";
                    default:
                        logger.warning("Unknown MT %s sequence: %s".formatted(mt, sequence));
                }
                break;
            case 558:
                switch (sequence) {
                    case "A":
                        return "GENL";
                    case "A1":
                        return "COLLPRTY";
                    case "A2":
                        return "STAT";
                    case "A2a":
                        return "REAS";
                    case "A3":
                        return "LINK";
                    case "B":
                        return "DEALTRAN";
                    case "B1":
                        return "BREAK";
                    case "C":
                        return "SECMOVE";
                    case "D":
                        return "CASHMOVE";
                    case "E":
                        return "ADDINFO";
                    default:
                }
                break;
            default:
                throw new IllegalArgumentException("Message type %s is not implemented".formatted(mt));
        }
        return "XXX";
    }

    /** Converts one Name-Value line into the corresponding SWIFT MT message. */
    public AbstractMT convert(String line) {
        AbstractMT swiftMessage = AbstractMT.create(599);

        String lastSequence = "";

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

                            if (!lastSequence.equals(seq)) {
                                closeSequence("16R", mt, seq, swiftMessage);
                                sequenceStack.add(seq);
                            }
                            String[] tagFields = tag.split(":");
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

    private void closeSequence(String name, int mt, String last, AbstractMT swiftMessage) {
        Tag closeTag = new Tag();
        closeTag.setNameValue(name, translateSequence(mt, last));
        swiftMessage.append(closeTag);
    }
}
