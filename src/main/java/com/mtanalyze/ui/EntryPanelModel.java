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
import java.util.regex.Pattern;

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

    // ── State ──────────────────────────────────────────────────────────────
    private final Project        project            = new Project();
    private final List<Entry>    allEntries         = new ArrayList<>();
    private final List<ColumnDef> allColumnDefs     = new ArrayList<>();
    private final List<ColumnDef> allNoSeqColumnDefs = new ArrayList<>();
    private boolean              seqMode            = true;

    // ── Package-private accessors (EntryTableModel + column-pref methods) ─
    List<Entry>    allEntries() { return allEntries; }
    List<ColumnDef> columnDefs() { return allColumnDefs; }

    // ── Public accessors ───────────────────────────────────────────────────
    public Project            getProject()        { return project; }
    public List<SwiftMessage> getLoadedMessages() { return project.messages(); }
    public List<ColumnDef>    getColumnDefs()     { return Collections.unmodifiableList(allColumnDefs); }
    public boolean            isSeqMode()         { return seqMode; }
    public void               setSeqMode(boolean s) { seqMode = s; }

    public List<ColumnDef> activeColumnDefs() {
        return seqMode ? allColumnDefs : allNoSeqColumnDefs;
    }

    public List<Map<String, String>> activeRowData() {
        if (!seqMode) return new AbstractList<>() {
            @Override public Map<String, String> get(int i) { return toNoSeqRow(allEntries.get(i).data()); }
            @Override public int size()                      { return allEntries.size(); }
        };
        return allEntries.stream().map(Entry::data).toList();
    }

    // ── Bulk load ──────────────────────────────────────────────────────────
    public void clear() {
        project.clear();
        allEntries.clear();
        allColumnDefs.clear();
        allNoSeqColumnDefs.clear();
    }

    public void loadBatch(List<SwiftMessage> messages, List<ColumnDef> columnDefs) {
        clear();
        for (SwiftMessage msg : messages) {
            project.addMessage(msg);
            allEntries.addAll(msg.entries());
            accumulateNoSeqColumnDefs(msg.entries().stream().map(Entry::data).toList());
        }
        mergeColumnDefs(columnDefs);
    }

    public void mergeBatch(List<SwiftMessage> messages, List<ColumnDef> columnDefs) {
        for (SwiftMessage msg : messages) {
            project.addMessage(msg);
            allEntries.addAll(msg.entries());
            accumulateNoSeqColumnDefs(msg.entries().stream().map(Entry::data).toList());
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
        MtParser parser = new MtParser(detectRowSequence(msg.raw()));
        parser.parse(msg.raw());
        List<Entry> newEntries = parser.getEntries();

        for (ColumnDef cd : parser.getColumnDefs())
            if (knownKeys.add(cd.key)) outCols.add(cd);

        String mtType = msg.mtType();
        if (!mtType.isEmpty()) {
            if (knownKeys.add(MT_COL_KEY)) outCols.add(new ColumnDef("", "_MT_", "", 1, MT_COL_LABEL));
            newEntries.forEach(e -> e.data().put(MT_COL_KEY, mtType));
        }
        if (knownKeys.add(TYPE_COL_KEY)) outCols.add(0, new ColumnDef("", "_TYPE_", "", 1, "Typ"));
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

    public boolean isFileLoaded(String filePath) {
        return allEntries.stream().anyMatch(e -> filePath.equals(e.getValue(FILE_COL_KEY)));
    }

    public int findRowByTagValue(String qualifier, String value) {
        for (ColumnDef cd : allColumnDefs) {
            if ("20C".equals(cd.tagName)) {
                for (int i = 0; i < allEntries.size(); i++) {
                    String val = allEntries.get(i).data().get(cd.key);
                    if (val != null && !val.isEmpty()
                            && (cd.qualifier.equals(qualifier) || hasQualifierPrefix(val, qualifier))
                            && value.equals(cleanTagValue(val, qualifier)))
                        return i;
                }
            }
        }
        return -1;
    }

    public int findRowByFileAndIsin(String filePath, String isin) {
        for (ColumnDef cd : allColumnDefs) {
            if ("35B".equals(cd.tagName)) {
                for (int i = 0; i < allEntries.size(); i++) {
                    Entry e   = allEntries.get(i);
                    String val = e.data().get(cd.key);
                    if (val != null && !val.isEmpty()
                            && (cd.qualifier.equals("ISIN") || hasQualifierPrefix(val, "ISIN"))
                            && isin.equals(cleanTagValue(val, "ISIN"))
                            && filePath.equals(e.getValue(FILE_COL_KEY)))
                        return i;
                }
            }
        }
        return -1;
    }

    public String findValueByTagQualifier(int modelRow, String tagName, String qualifier) {
        if (modelRow < 0 || modelRow >= allEntries.size()) return "";
        Map<String, String> row = allEntries.get(modelRow).data();
        for (ColumnDef cd : allColumnDefs) {
            if (cd.tagName.equals(tagName)) {
                String val = row.get(cd.key);
                if (val != null && !val.isEmpty()
                        && (cd.qualifier.equals(qualifier) || hasQualifierPrefix(val, qualifier)))
                    return cleanTagValue(val, qualifier);
            }
        }
        return "";
    }

    // ── Column management (package-private, used by panel's prefs methods) ─

    private void mergeColumnDefs(List<ColumnDef> incoming) {
        Set<String> knownKeys = new HashSet<>();
        allColumnDefs.forEach(cd -> knownKeys.add(cd.key));
        for (ColumnDef cd : incoming) {
            if (!knownKeys.add(cd.key)) continue;
            if (TYPE_COL_KEY.equals(cd.key)) allColumnDefs.add(0, cd);
            else allColumnDefs.add(cd);
        }
    }

    private void accumulateNoSeqColumnDefs(List<Map<String, String>> newSeqRows) {
        Set<String> knownKeys = new HashSet<>();
        for (ColumnDef cd : allNoSeqColumnDefs) knownKeys.add(cd.key);
        addSystemNoSeqColumnDefs(knownKeys);
        addDataNoSeqColumnDefs(newSeqRows, knownKeys);
    }

    private void addSystemNoSeqColumnDefs(Set<String> knownKeys) {
        for (ColumnDef cd : allColumnDefs) {
            if (cd.tagName.startsWith("_") && knownKeys.add(cd.key)) {
                if (TYPE_COL_KEY.equals(cd.key)) allNoSeqColumnDefs.add(0, cd);
                else allNoSeqColumnDefs.add(cd);
            }
        }
    }

    private void addDataNoSeqColumnDefs(List<Map<String, String>> newSeqRows, Set<String> knownKeys) {
        for (Map<String, String> seqRow : newSeqRows) {
            Map<String, Integer> localOcc = new LinkedHashMap<>();
            for (String seqKey : seqRow.keySet()) {
                String noSeqKey = toNoSeqKey(seqKey, localOcc);
                if (knownKeys.add(noSeqKey)) {
                    ColumnDef cd = buildNoSeqColDef(noSeqKey);
                    if (cd != null) allNoSeqColumnDefs.add(cd);
                }
            }
        }
    }

    static Map<String, String> toNoSeqRow(Map<String, String> seqRow) {
        Map<String, Integer> localOcc = new LinkedHashMap<>();
        Map<String, String>  result   = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : seqRow.entrySet())
            result.put(toNoSeqKey(e.getKey(), localOcc), e.getValue());
        return result;
    }

    // ── Static helpers ─────────────────────────────────────────────────────

    static String detectRowSequence(AbstractMT mt) {
        com.prowidesoftware.swift.model.SwiftBlock2 b2 = mt.getSwiftMessage().getBlock2();
        if (b2 == null) return "TRAN";
        String type = b2.getMessageType();
        if ("535".equals(type)) return "SUBBAL";
        if (type != null && type.matches("54[0-8]")) return null;
        if ("527".equals(type) || "558".equals(type)) return null;
        if ("940".equals(type) || "950".equals(type)) return "61";
        SwiftTagListBlock b4 = mt.getSwiftMessage().getBlock4();
        if (b4 != null && b4.getTags().stream().noneMatch(t -> "16R".equals(t.getName()))) return null;
        return "TRAN";
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

    static boolean hasQualifierPrefix(String val, String qualifier) {
        return val.startsWith(":" + qualifier + "//")
            || val.startsWith(":" + qualifier + "/")
            || val.startsWith(qualifier + " ")
            || val.startsWith(qualifier + "\t");
    }

    static String cleanTagValue(String val, String qualifier) {
        if (val == null || val.isEmpty()) return "";
        String s = val.trim();
        String q = Pattern.quote(qualifier);
        String t = s.replaceFirst("^:" + q + "//", "");
        if (t.length() < s.length()) { s = t.trim(); }
        else {
            t = s.replaceFirst("^:" + q + "/[^/]*/", "");
            if (t.length() < s.length()) { s = t.trim(); }
            else {
                t = s.replaceFirst("^" + q + "[ \t]+", "");
                if (t.length() < s.length()) s = t.trim();
            }
        }
        int nl = s.indexOf('\n');
        return nl >= 0 ? s.substring(0, nl).trim() : s;
    }

    private static String toNoSeqKey(String seqKey, Map<String, Integer> localOcc) {
        if (countTabs(seqKey) != 3) return seqKey;
        int t1      = seqKey.indexOf('\t');
        String rest = seqKey.substring(t1);
        int lastT   = rest.lastIndexOf('\t');
        String base = rest.substring(0, lastT);
        int n = localOcc.merge(base, 1, Integer::sum);
        return base + "\t" + n;
    }

    private static int countTabs(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\t') n++;
        return n;
    }

    private static ColumnDef buildNoSeqColDef(String noSeqKey) {
        String[] p = noSeqKey.split("\t", -1);
        if (p.length != 4 || p[1].startsWith("_")) return null;
        int occ;
        try { occ = Integer.parseInt(p[3]); } catch (NumberFormatException e) { return null; }
        String label = p[1] + (p[2].isEmpty() ? "" : " / " + p[2]) + (occ > 1 ? " (" + occ + ")" : "");
        return new ColumnDef("", p[1], p[2], occ, label);
    }
}