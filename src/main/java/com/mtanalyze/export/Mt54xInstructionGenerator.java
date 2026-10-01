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
package com.mtanalyze.export;

import com.mtanalyze.model.SwiftMessage;
import com.prowidesoftware.swift.model.SwiftBlock4;
import com.prowidesoftware.swift.model.Tag;
import com.prowidesoftware.swift.model.mt.AbstractMT;
import com.prowidesoftware.swift.model.mt.mt5xx.MT540;
import com.prowidesoftware.swift.model.mt.mt5xx.MT541;
import com.prowidesoftware.swift.model.mt.mt5xx.MT542;
import com.prowidesoftware.swift.model.mt.mt5xx.MT543;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Reconstructs the original settlement instruction (MT 540 - MT 543) from a
 * settlement confirmation (MT 544 - MT 547):
 *
 * <pre>
 *   Confirmation                       -&gt;  Instruction
 *   ---------------------------------      ----------------------------------
 *   MT 544  Receive Free                   MT 540  Receive Free
 *   MT 545  Receive Against Payment        MT 541  Receive Against Payment
 *   MT 546  Deliver Free                   MT 542  Deliver Free
 *   MT 547  Deliver Against Payment        MT 543  Deliver Against Payment
 * </pre>
 *
 * <p>Both message families share the same sequence layout, so block 4 is copied
 * tag by tag and only the confirmation-specific fields are rewritten:
 * <ul>
 *   <li>GENL: the instruction's {@code :20C::SEME//} is taken from the
 *       confirmation's {@code :20C::RELA//} link (whose LINK block is dropped);
 *       {@code 23G} becomes {@code NEWM} and {@code :98C::PREP//} the current time.</li>
 *   <li>TRADDET: {@code :98a::ESET//} (effective settlement date) is dropped.</li>
 *   <li>FIAC: {@code :36B::ESTT//} becomes {@code :36B::SETT//};
 *       {@code PSTT} / {@code RSTT} quantities are dropped.</li>
 *   <li>AMT: {@code :19A::ESTT//} becomes {@code :19A::SETT//}.</li>
 * </ul>
 * Sender and receiver are swapped, since the instruction travels from the
 * account owner to the account servicer. The result is a best-effort,
 * human-readable SWIFT message intended for inspection, not straight-through
 * processing.
 */
public final class Mt54xInstructionGenerator {

    private static final DateTimeFormatter PREP_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter SEME_FMT = DateTimeFormatter.ofPattern("yyMMddHHmmss");

    private static final String FALLBACK_SND = "SENDERBICXXX";
    private static final String FALLBACK_RCV = "RECVRBICXXXX";

    private static final String TAG_START = "16R";
    private static final String TAG_END   = "16S";

    private final LocalDateTime now;

    public Mt54xInstructionGenerator() {
        this(LocalDateTime.now(ZoneId.systemDefault()));
    }

    /** Constructor with a fixed timestamp - useful for reproducible tests. */
    public Mt54xInstructionGenerator(LocalDateTime now) {
        this.now = now;
    }

    /**
     * Returns the instruction type (540-543) matching the confirmation type
     * ({@code "MT544"} - {@code "MT547"} or {@code "544"} - {@code "547"}),
     * or {@code null} for any other message type.
     */
    public static String instructionTypeFor(String confirmationType) {
        if (confirmationType == null) return null;
        String t = confirmationType.startsWith("MT") ? confirmationType.substring(2) : confirmationType;
        return switch (t) {
            case "544" -> "540";
            case "545" -> "541";
            case "546" -> "542";
            case "547" -> "543";
            default -> null;
        };
    }

    /**
     * Builds the instruction message text for the given confirmation.
     * Returns an empty string when {@code source} is not an MT 544 - 547.
     */
    public String generate(SwiftMessage source) {
        String type = instructionTypeFor(source.mtType());
        SwiftBlock4 block4 = source.raw().getSwiftMessage().getBlock4();
        if (type == null || block4 == null) return "";

        // Confirmation flows servicer -> owner, the instruction owner -> servicer.
        String sender   = orDefault(source.raw().getReceiver(), FALLBACK_SND);
        String receiver = orDefault(source.raw().getSender(), FALLBACK_RCV);

        List<Tag> b4 = convert(block4.getTags());
        return build(type, sender, receiver, b4).message();
    }

    private List<Tag> convert(List<Tag> src) {
        String seme = findRelatedReference(src);
        if (seme == null) seme = now.format(SEME_FMT) + "0001";

        List<Tag> out = new ArrayList<>();
        Deque<String> seq = new ArrayDeque<>();
        for (int i = 0; i < src.size(); i++) {
            Tag t = src.get(i);
            String name = t.getName();
            String qual = qualifier(t);

            if (TAG_START.equals(name)) {
                if ("LINK".equals(t.getValue()) && "GENL".equals(seq.peek())) {
                    int end = indexOfSequenceEnd(src, i, "LINK");
                    if (containsQualified(src.subList(i, end + 1), "20C", "RELA")) {
                        i = end; // the RELA link becomes the instruction's own SEME
                        continue;
                    }
                }
                seq.push(t.getValue());
                out.add(t);
                continue;
            }
            if (TAG_END.equals(name)) {
                seq.poll();
                out.add(t);
                continue;
            }

            String current = seq.peek();
            if ("GENL".equals(current)) {
                if ("20C".equals(name) && "SEME".equals(qual)) {
                    out.add(new Tag("20C", ":SEME//" + seme));
                } else if ("23G".equals(name)) {
                    out.add(new Tag("23G", "NEWM"));
                } else if (name.startsWith("98") && "PREP".equals(qual)) {
                    out.add(new Tag("98C", ":PREP//" + now.format(PREP_FMT)));
                } else {
                    out.add(t);
                }
            } else if (name.startsWith("98") && "ESET".equals(qual)) {
                // effective settlement date only exists in the confirmation
            } else if ("36B".equals(name) && "ESTT".equals(qual)) {
                out.add(new Tag("36B", ":SETT//" + data(t)));
            } else if ("36B".equals(name) && ("PSTT".equals(qual) || "RSTT".equals(qual))) {
                // previously settled / remaining quantities only exist in the confirmation
            } else if ("19A".equals(name) && "ESTT".equals(qual)) {
                out.add(new Tag("19A", ":SETT//" + data(t)));
            } else {
                out.add(t);
            }
        }
        return out;
    }

    /** Value of the first {@code :20C::RELA//} found inside a GENL/LINK block, or {@code null}. */
    private static String findRelatedReference(List<Tag> tags) {
        Deque<String> seq = new ArrayDeque<>();
        for (Tag t : tags) {
            if (TAG_START.equals(t.getName())) { seq.push(t.getValue()); continue; }
            if (TAG_END.equals(t.getName()))   { seq.poll();             continue; }
            if ("LINK".equals(seq.peek()) && "20C".equals(t.getName()) && "RELA".equals(qualifier(t))) {
                String ref = data(t);
                if (!ref.isEmpty() && !"NONREF".equals(ref)) return ref;
            }
        }
        return null;
    }

    private static int indexOfSequenceEnd(List<Tag> tags, int start, String seqName) {
        for (int j = start + 1; j < tags.size(); j++) {
            Tag t = tags.get(j);
            if (TAG_END.equals(t.getName()) && seqName.equals(t.getValue())) return j;
        }
        return tags.size() - 1;
    }

    private static boolean containsQualified(List<Tag> tags, String name, String qualifier) {
        for (Tag t : tags) {
            if (name.equals(t.getName()) && qualifier.equals(qualifier(t))) return true;
        }
        return false;
    }

    /** Qualifier of a generic field value {@code :QUAL//data} or {@code :QUAL/ISSR/data}, else {@code null}. */
    private static String qualifier(Tag t) {
        String v = t.getValue();
        if (v == null || !v.startsWith(":")) return null;
        int slash = v.indexOf('/', 1);
        return slash > 1 ? v.substring(1, slash) : null;
    }

    /** Data part after {@code :QUAL//} of a generic field value. */
    private static String data(Tag t) {
        String v = t.getValue();
        int sep = v.indexOf("//");
        return sep >= 0 ? v.substring(sep + 2) : v;
    }

    private static AbstractMT build(String type, String sender, String receiver, List<Tag> b4) {
        Tag[] tags = b4.toArray(new Tag[0]);
        return switch (type) {
            case "541" -> new MT541(sender, receiver).append(tags);
            case "542" -> new MT542(sender, receiver).append(tags);
            case "543" -> new MT543(sender, receiver).append(tags);
            default -> new MT540(sender, receiver).append(tags);
        };
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isEmpty()) ? fallback : value;
    }
}
