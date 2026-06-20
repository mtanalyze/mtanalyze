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
package com.mtanalyze.export;

import com.mtanalyze.model.Entry;
import com.mtanalyze.model.SwiftMessage;
import com.prowidesoftware.swift.model.SwiftTagListBlock;
import com.prowidesoftware.swift.model.Tag;
import com.prowidesoftware.swift.model.field.Field;
import com.prowidesoftware.swift.model.field.Field22H;
import com.prowidesoftware.swift.model.field.Field35B;
import com.prowidesoftware.swift.model.field.Field36B;
import com.prowidesoftware.swift.model.field.Field97A;
import com.prowidesoftware.swift.model.mt.AbstractMT;
import com.prowidesoftware.swift.model.mt.mt5xx.MT536;
import com.prowidesoftware.swift.model.mt.mt5xx.MT544;
import com.prowidesoftware.swift.model.mt.mt5xx.MT545;
import com.prowidesoftware.swift.model.mt.mt5xx.MT546;
import com.prowidesoftware.swift.model.mt.mt5xx.MT547;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts a single MT 536 (Statement of Transactions) entry into the matching
 * settlement confirmation message (MT 544 - MT 547), using the typed Prowide
 * model to navigate the statement sequences.
 *
 * <p>The target type is derived from the transaction's settlement indicators in
 * sequence B1a2 (TRANSDET):
 *
 * <pre>
 *   :22H::REDE//   :22H::PAYM//   -&gt;  Confirmation
 *   ------------   ------------       ------------------------------------
 *   RECE           FREE               MT 544  Receive Free
 *   RECE           APMT               MT 545  Receive Against Payment
 *   DELI           FREE               MT 546  Deliver Free
 *   DELI           APMT               MT 547  Deliver Against Payment
 * </pre>
 *
 * <p>The salient settlement fields (financial instrument, safekeeping account,
 * quantity, dates, settlement instruction narrative, transaction type and
 * settlement parties) are carried over from the statement transaction into the
 * standard MT 54x sequences GENL / TRADDET / FIAC / SETDET. The result is a
 * best-effort, human-readable SWIFT message intended for inspection, not
 * straight-through processing.
 */
public final class Mt54xGenerator {

    private static final DateTimeFormatter PREP_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter SEME_FMT = DateTimeFormatter.ofPattern("yyMMddHHmmss");

    private static final String FALLBACK_SND = "SENDERBICXXX";
    private static final String FALLBACK_RCV = "RECVRBICXXXX";

    /** Whether settlement parties that are mere {@code //UNKNOWN} placeholders are dropped. */
    private final boolean noUnknownParty = false;

    private final LocalDateTime now;

    public Mt54xGenerator() {
        this(LocalDateTime.now());
    }

    /** Constructor with a fixed timestamp - useful for reproducible tests. */
    public Mt54xGenerator(LocalDateTime now) {
        this.now = now;
    }

    /**
     * Returns the settlement-confirmation message type (544-547) for the entry,
     * derived from its {@code :22H::REDE//} and {@code :22H::PAYM//} indicators,
     * or {@code null} when the entry carries no transaction details (TRANSDET).
     */
    public static String detectConfirmationType(Entry entry) {
        if (entry == null) return null;
        SwiftTagListBlock td = firstTransDet(entry.sequence());
        if (td == null) return null;
        Field22H rede = (Field22H) td.getFieldByName("22H", "REDE");
        Field22H paym = (Field22H) td.getFieldByName("22H", "PAYM");
        return determineMessageType(rede, paym);
    }

    /**
     * Builds the settlement-confirmation message text for {@code entry}.
     * Returns an empty string when the entry carries no transaction details.
     */
    public String generate(SwiftMessage source, Entry entry) {
        String sender   = orDefault(source.raw().getSender(), FALLBACK_SND);
        String receiver = orDefault(source.raw().getReceiver(), FALLBACK_RCV);

        // 35B (financial instrument) lives at FIN level, 97A::SAFE at SUBSAFE level;
        // both are carried in the entry's parent context, not in the transaction itself.
        Field35B instrument  = (Field35B) entry.parentContext().getFieldByName("35B");
        Field97A subsafeSafe = (Field97A) entry.parentContext().getFieldByName("97A", "SAFE");

        AbstractMT mt = convertTransaction(sender, receiver, instrument, subsafeSafe, entry.sequence());
        return mt != null ? mt.message() : "";
    }

    private AbstractMT convertTransaction(String sender, String receiver,
                                          Field35B instrument, Field97A subsafeSafe,
                                          SwiftTagListBlock tran) {

        // B1a1 = LINK (references) and B1a2 = TRANSDET (transaction details)
        List<SwiftTagListBlock> links = new ArrayList<>(MT536.getSequenceB1a1List(tran));
        List<SwiftTagListBlock> transdets = new ArrayList<>(MT536.getSequenceTRANSDETList(tran));
        if (transdets.isEmpty()) {
            return null; // no details -> no confirmation
        }
        SwiftTagListBlock td = transdets.get(0);

        Field22H rede = (Field22H) td.getFieldByName("22H", "REDE");
        Field22H paym = (Field22H) td.getFieldByName("22H", "PAYM");
        String messageType = determineMessageType(rede, paym);

        List<Tag> b4 = new ArrayList<>();

        // ---------------------------------------------------------------
        // Sequence A - GENL (General Information)
        // ---------------------------------------------------------------
        b4.add(new Tag("16R", "GENL"));
        b4.add(new Tag("20C", ":SEME//" + generateReference()));
        b4.add(new Tag("23G", "NEWM"));
        b4.add(new Tag("98C", ":PREP//" + now.format(PREP_FMT)));
        // A1 - LINK: carry over the reference blocks of the MT 536
        for (SwiftTagListBlock link : links) {
            b4.addAll(link.getTags());
        }
        b4.add(new Tag("16S", "GENL"));

        // ---------------------------------------------------------------
        // Sequence B - TRADDET (Trade Details)
        // ---------------------------------------------------------------
        b4.add(new Tag("16R", "TRADDET"));
        copyTagIfPresent(b4, td, "98C", "ESET");   // Effective settlement date/time
        copyTagIfPresent(b4, td, "98A", "SETT");   // Settlement date
        copyTagIfPresent(b4, td, "98A", "TRAD");   // Trade date
        if (instrument != null) {
            b4.add(instrument.asTag());            // 35B financial instrument
        }
        // 70E SPRO (Settlement Instruction Processing) derived from 70E TRDE.
        // Only the free text after /FREE is taken from the structured narrative.
        Field trde = td.getFieldByName("70E", "TRDE");
        if (trde != null) {
            b4.add(buildSpro(trde).asTag());
        }
        b4.add(new Tag("16S", "TRADDET"));

        // ---------------------------------------------------------------
        // Sequence C - FIAC (Financial Instrument / Account)
        // ---------------------------------------------------------------
        b4.add(new Tag("16R", "FIAC"));
        Field36B posted = (Field36B) td.getFieldByName("36B", "PSTA"); // posted quantity
        if (posted != null) {
            String qtc = orDefault(posted.getQuantityTypeCode(), "FAMT");
            String qty = orDefault(posted.getQuantity(), "0,");
            b4.add(new Tag("36B", ":ESTT//" + qtc + "/" + qty)); // settled quantity
            b4.add(new Tag("36B", ":PSTT//" + qtc + "/0,"));     // previously settled
            b4.add(new Tag("36B", ":RSTT//" + qtc + "/0,"));     // remaining
        }
        // Safekeeping account of the account owner = SAFE at the SUBSAFE level of the MT 536.
        if (subsafeSafe != null) {
            b4.add(subsafeSafe.asTag());
        }
        b4.add(new Tag("16S", "FIAC"));

        // ---------------------------------------------------------------
        // Sequence E - SETDET (Settlement Details)
        // ---------------------------------------------------------------
        b4.add(new Tag("16R", "SETDET"));
        // 22F STCO settlement transaction condition (default: non-partial)
        b4.add(new Tag("22F", ":STCO//NPAR"));
        copyTagIfPresent(b4, td, "22F", "SETR"); // type of settlement transaction
        // For deliver/receive against payment carry over the settlement amount, if present
        if ("545".equals(messageType) || "547".equals(messageType)) {
            copyTagIfPresent(b4, td, "19A", "SETT");
        }
        // E1 - SETPRTY: carry over the settlement parties from the MT 536.
        // Pure placeholder blocks (only :95Q::...//UNKNOWN) are dropped.
        for (SwiftTagListBlock party : MT536.getSequenceSETPRTYList(td)) {
            if (noUnknownParty && isUnknownPlaceholderParty(party)) {
                continue;
            }
            b4.addAll(party.getTags());
        }
        b4.add(new Tag("16S", "SETDET"));

        return build(messageType, sender, receiver, b4);
    }

    /** Derives the message type from REDE/PAYM. */
    static String determineMessageType(Field22H rede, Field22H paym) {
        boolean receive = rede == null || !"DELI".equals(rede.getIndicator()); // default RECE
        boolean againstPayment = paym != null && "APMT".equals(paym.getIndicator());
        if (receive) {
            return againstPayment ? "545" : "544";
        } else {
            return againstPayment ? "547" : "546";
        }
    }

    private static AbstractMT build(String type, String sender, String receiver, List<Tag> b4) {
        Tag[] tags = b4.toArray(new Tag[0]);
        return switch (type) {
            case "545" -> new MT545(sender, receiver).append(tags);
            case "546" -> new MT546(sender, receiver).append(tags);
            case "547" -> new MT547(sender, receiver).append(tags);
            default -> new MT544(sender, receiver).append(tags);
        };
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** First TRANSDET (B1a2) sub-sequence of the given transaction block, or {@code null}. */
    private static SwiftTagListBlock firstTransDet(SwiftTagListBlock tran) {
        if (tran == null) return null;
        List<SwiftTagListBlock> transdets = new ArrayList<>(MT536.getSequenceTRANSDETList(tran));
        return transdets.isEmpty() ? null : transdets.get(0);
    }

    /**
     * Checks whether a SETPRTY sequence consists solely of the placeholder
     * {@code :95Q::<qual>//UNKNOWN} (with no further fields). Such blocks carry
     * no information and are dropped from the confirmation.
     */
    private static boolean isUnknownPlaceholderParty(SwiftTagListBlock party) {
        List<Tag> content = new ArrayList<>();
        for (Tag t : party.getTags()) {
            // ignore the sequence delimiters
            if ("16R".equals(t.getName()) || "16S".equals(t.getName())) {
                continue;
            }
            content.add(t);
        }
        if (content.size() != 1) {
            return false; // further fields present -> keep the block
        }
        Tag only = content.get(0);
        return "95Q".equals(only.getName())
                && only.getValue() != null
                && only.getValue().endsWith("//UNKNOWN");
    }

    private static void copyTagIfPresent(List<Tag> target, SwiftTagListBlock src,
                                         String name, String qualifier) {
        Field f = src.getFieldByName(name, qualifier);
        if (f != null) {
            target.add(f.asTag());
        }
    }

    /**
     * Builds the confirmation's 70E::SPRO from the MT 536's 70E::TRDE. From the
     * structured narrative (e.g. {@code /ACKY 01/STBL N/ISTR COLI/FREE <text>})
     * only the free text after the code FREE is taken. When FREE is absent the
     * data part after the qualifier is kept unchanged.
     */
    private static Field buildSpro(Field trde) {
        // remove line breaks of the MT 536 (e.g. "TRIP\nARTY" -> "TRIPARTY")
        String text = trde.getValue().replaceAll("[\\r\\n]", "");
        int free = text.indexOf("/FREE");
        String narrative;
        if (free >= 0) {
            narrative = text.substring(free + "/FREE".length())
                            .replaceFirst("^[\\s/]+", "")   // leading separators/spaces away
                            .trim();
        } else {
            int sep = text.indexOf("//");
            narrative = sep >= 0 ? text.substring(sep + 2) : text;
        }
        return Field.getField("70E", ":SPRO//" + narrative);
    }

    private String generateReference() {
        return now.format(SEME_FMT) + "0001";
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isEmpty()) ? fallback : value;
    }
}