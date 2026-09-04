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

import com.mtanalyze.model.Entry;
import com.mtanalyze.model.Project;
import com.mtanalyze.model.SwiftMessage;
import com.mtanalyze.parser.MtParser;
import com.prowidesoftware.swift.model.SwiftTagListBlock;
import com.prowidesoftware.swift.model.Tag;
import com.prowidesoftware.swift.model.mt.AbstractMT;

import java.util.*;

/**
 * All data state for the entry panel in one place.
 * No Swing dependency — pure domain + data management.
 */
final class EntryPanelModel {

    // ── Column-key constants ───────────────────────────────────────────────
    static final String FILE_COL_KEY       = "\t_FILE_\t\t1";
    static final String MT_COL_KEY         = "\t_MT_\t\t1";
    static final String MT_COL_LABEL       = "MT";
    static final String TYPE_COL_KEY       = "\t_TYPE_\t\t1";
    static final String GENL_23G_KEY       = "GENL\t23G\t\t1";
    static final String GENL_25D_IPRC_KEY  = "GENL\t25D\tIPRC\t1";
    static final String TRAN_REDE_KEY      = "TRANSDET\t22H\tREDE\t1";
    static final String SECMOVE_INOU_KEY_1 = "SECMOVE\t22H\tINOU\t1";
    static final String SECMOVE_INOU_KEY_2 = "SECMOVE\t22H\tINOU\t2";

    /** Metadata columns pinned to the front of the table, in display order (MT leftmost). */
    private static final List<String> PINNED_FRONT_KEYS = List.of(MT_COL_KEY, TYPE_COL_KEY);

    // ── State ──────────────────────────────────────────────────────────────
    private final Project        project            = new Project();
    private final List<Entry>    allEntries         = new ArrayList<>();
    private final List<ColumnDef> allColumnDefs     = new ArrayList<>();

    // ── Package-private accessors (EntryTableModel + column-pref methods) ─
    List<Entry>     allEntries() { return Collections.unmodifiableList(allEntries); }
    /** Mutable — {@code MtEntryPanel.syncColumnOrder()} reorders this list in place. */
    List<ColumnDef> columnDefs() { return allColumnDefs; }

    // ── Public accessors ───────────────────────────────────────────────────
    public List<SwiftMessage> getLoadedMessages() { return project.messages(); }
    public List<ColumnDef>    getColumnDefs()     { return Collections.unmodifiableList(allColumnDefs); }

    // ── Bulk load ──────────────────────────────────────────────────────────
    public void clear() {
        project.clear();
        allEntries.clear();
        allColumnDefs.clear();
    }

    public void loadBatch(List<SwiftMessage> messages, List<ColumnDef> columnDefs) {
        clear();
        for (SwiftMessage msg : messages) {
            project.addMessage(msg);
            allEntries.addAll(msg.entries());
        }
        mergeColumnDefs(columnDefs);
    }

    public void mergeBatch(List<SwiftMessage> messages, List<ColumnDef> columnDefs) {
        for (SwiftMessage msg : messages) {
            project.addMessage(msg);
            allEntries.addAll(msg.entries());
        }
        mergeColumnDefs(columnDefs);
    }

    /**
     * Parses entries from {@code msg}, decorates them with MT/TYPE/FILE metadata columns,
     * adds entries to the message, and merges any new column definitions into {@code outCols}
     * using {@code knownKeys} for deduplication.
     * Returns the list of new entries (empty if the message has no block 4 tags).
     */
    static List<Entry> parseAndDecorate(SwiftMessage msg, Set<String> knownKeys, List<ColumnDef> outCols) {
        SwiftTagListBlock b4 = msg.raw().getSwiftMessage().getBlock4();
        if (b4 == null || b4.getTags().isEmpty()) return List.of();

        String rowSeq = detectRowSequence(msg.raw());
        MtParser parser = new MtParser(rowSeq);
        parser.parse(msg.raw());
        List<Entry> newEntries = parser.getEntries();

        for (ColumnDef cd : parser.getColumnDefs())
            if (knownKeys.add(cd.key)) outCols.add(cd);

        String mtType = msg.mtType();
        if (!mtType.isEmpty()) {
            if (knownKeys.add(MT_COL_KEY)) insertPinned(outCols, new ColumnDef("", "_MT_", "", 1, MT_COL_LABEL));
            newEntries.forEach(e -> e.data().put(MT_COL_KEY, mtType));
        }
        if (knownKeys.add(TYPE_COL_KEY)) insertPinned(outCols, new ColumnDef("", "_TYPE_", "", 1, "Typ"));
        newEntries.forEach(e -> e.data().put(TYPE_COL_KEY, computeEntryType(e.data())));
        String fileLabel = fileLabel(msg);
        if (fileLabel != null) {
            if (knownKeys.add(FILE_COL_KEY)) outCols.add(new ColumnDef("", "_FILE_", "", 1, "File"));
            newEntries.forEach(e -> e.data().put(FILE_COL_KEY, fileLabel));
        }
        newEntries.forEach(msg::addEntry);
        return newEntries;
    }

    private static String fileLabel(SwiftMessage msg) {
        return switch (msg.origin()) {
            case SWIFT_FILE -> msg.sourceFile() != null ? msg.sourceFile().getAbsolutePath() : null;
            case LOG_FILE   -> "Log";
            case NAME_VALUE -> "Name/Value";
            case CLIPBOARD  -> "Clipboard";
        };
    }

    /** Removes the entry at {@code modelRow} from all backing structures. Caller fires the table event. */
    public void deleteRow(int modelRow) {
        int msgIdx         = messageIndexForRow(modelRow);
        SwiftMessage owner = project.messages().get(msgIdx);
        Entry entry        = allEntries.get(modelRow);

        Set<Tag> finSet = Collections.newSetFromMap(new IdentityHashMap<>());
        finSet.addAll(entry.sequence().getTags());
        owner.raw().getSwiftMessage().getBlock4().getTags().removeIf(finSet::contains);

        owner.removeEntry(entry);
        allEntries.remove(modelRow);

        if (owner.entries().isEmpty()) project.removeMessage(msgIdx);
    }

    private int messageIndexForRow(int modelRow) {
        int offset = 0;
        List<SwiftMessage> msgs = project.messages();
        for (int i = 0; i < msgs.size(); i++) {
            offset += msgs.get(i).entries().size();
            if (modelRow < offset) return i;
        }
        throw new IndexOutOfBoundsException("modelRow=" + modelRow);
    }

    // ── Row queries ────────────────────────────────────────────────────────

    public SwiftMessage getMessageForRow(int modelRow) {
        return project.messages().get(messageIndexForRow(modelRow));
    }

    public Entry getEntryForRow(int modelRow) {
        if (modelRow < 0 || modelRow >= allEntries.size()) return null;
        return allEntries.get(modelRow);
    }

    public String getRowValue(int modelRow, String key) {
        if (modelRow < 0 || modelRow >= allEntries.size()) return "";
        return allEntries.get(modelRow).getValue(key);
    }

    public List<Map<String, String>> getRowData() {
        return new AbstractList<>() {
            @Override public Map<String, String> get(int i) { return allEntries.get(i).data(); }
            @Override public int size()                      { return allEntries.size(); }
        };
    }

    public List<SwiftTagListBlock> getFullDisplaySequences() {
        return new AbstractList<>() {
            @Override public SwiftTagListBlock get(int i) { return allEntries.get(i).fullDisplaySequence(); }
            @Override public int size()                   { return allEntries.size(); }
        };
    }

    // ── Column management (package-private, used by panel's prefs methods) ─

    private void mergeColumnDefs(List<ColumnDef> incoming) {
        Set<String> knownKeys = new HashSet<>();
        allColumnDefs.forEach(cd -> knownKeys.add(cd.key));
        for (ColumnDef cd : incoming) {
            if (!knownKeys.add(cd.key)) continue;
            if (PINNED_FRONT_KEYS.contains(cd.key)) insertPinned(allColumnDefs, cd);
            else allColumnDefs.add(cd);
        }
    }

    /** Inserts {@code cd} among the leading pinned columns, keeping {@link #PINNED_FRONT_KEYS} order. */
    private static void insertPinned(List<ColumnDef> cols, ColumnDef cd) {
        int priority = PINNED_FRONT_KEYS.indexOf(cd.key);
        int insertAt = 0;
        while (insertAt < cols.size()) {
            int existingPriority = PINNED_FRONT_KEYS.indexOf(cols.get(insertAt).key);
            if (existingPriority < 0 || existingPriority > priority) break;
            insertAt++;
        }
        cols.add(insertAt, cd);
    }


    // ── Static helpers ─────────────────────────────────────────────────────

    static String detectRowSequence(AbstractMT mt) {
        com.prowidesoftware.swift.model.SwiftBlock2 b2 = mt.getSwiftMessage().getBlock2();
        if (b2 == null) return "TRAN";
        String type = b2.getMessageType();
        if ("535".equals(type)) return "SUBBAL";
        if ("537".equals(type)) return "TRANS";
        if ("564".equals(type)) return "CAOPTN";
        if ("530".equals(type)) return "REQD";
        if ("567".equals(type)) return "STAT";
        if ("569".equals(type)) return detect569RowSequence(mt.getSwiftMessage().getBlock4());
        if (type != null && type.matches("54[0-8]")) return null;
        if ("527".equals(type) || "558".equals(type) || "578".equals(type)
                || "565".equals(type) || "566".equals(type) || "568".equals(type)
                || "509".equals(type) || "514".equals(type) || "515".equals(type)
                || "517".equals(type) || "518".equals(type)) return null;
        if ("940".equals(type) || "950".equals(type)) return "61";
        SwiftTagListBlock b4 = mt.getSwiftMessage().getBlock4();
        if (b4 != null && b4.getTags().stream().noneMatch(t -> "16R".equals(t.getName()))) return null;
        return "TRAN";
    }

    /**
     * MT 569: VALDET and SECDET are both optional (only TRANSDET is mandatory). SECDET is
     * always nested inside VALDET (Sequence C1a1A under C1a1), and VALDET itself repeats
     * (one per valuation within a transaction), so VALDET is the row: any SECDET(s) it
     * contains fold into that row as nested tags, same as CAOPTN folding in LINK/SETPRTY.
     * Picking SECDET as the row instead would make VALDET a repeating header wrapper,
     * whose fields would then wrongly accumulate across VALDET occurrences (see header
     * folding in {@link MtParser#parse}).
     */
    private static String detect569RowSequence(SwiftTagListBlock b4) {
        if (b4 == null) return "TRANSDET";
        if (has16R(b4, "VALDET")) return "VALDET";
        if (has16R(b4, "SECDET")) return "SECDET";
        return "TRANSDET";
    }

    private static boolean has16R(SwiftTagListBlock b4, String value) {
        return b4.getTags().stream().anyMatch(t ->
                "16R".equals(t.getName()) && value.equals(t.getValue() != null ? t.getValue().trim() : ""));
    }

    static String computeEntryType(Map<String, String> row) {
        String func = row.getOrDefault(GENL_23G_KEY, "");
        if ("CANC".equals(func)) return "CANC";
        if ("REJT".equals(row.getOrDefault(GENL_25D_IPRC_KEY, ""))) return "REJT";
        String rede = row.getOrDefault(TRAN_REDE_KEY, "");
        if ("RECE".equals(rede)) return "RECE";
        if ("DELI".equals(rede)) return "DELI";
        if ("REJE".equals(rede)) return "REJT";
        if ("COLI".equals(row.getOrDefault(SECMOVE_INOU_KEY_1, ""))) return "DELI";
        if ("COLO".equals(row.getOrDefault(SECMOVE_INOU_KEY_2, ""))) return "RECE";
        return "";
    }

}