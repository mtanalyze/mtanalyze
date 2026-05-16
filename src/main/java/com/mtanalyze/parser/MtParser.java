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

import com.mtanalyze.model.Entry;
import com.prowidesoftware.swift.model.SwiftTagListBlock;
import com.prowidesoftware.swift.model.Tag;
import com.prowidesoftware.swift.model.mt.AbstractMT;
import com.mtanalyze.ui.ColumnDef;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MtParser {

    /**
     * Reserved column key for the synthetic sequence label.
     */
    public static final String SEQ_KEY = "\t_SEQ_\t";

    /** Name of the leaf sequence that becomes one table row (e.g. "TRAN" for MT 536, "SUBBAL" for MT 535). */
    private final String rowSeqName;

    private final Lookups          lookups    = new Lookups();
    private final List<Entry>      entries    = new ArrayList<>();
    private final List<ColumnDef>  columnDefs = new ArrayList<>();

    public MtParser(String rowSeqName)   { this.rowSeqName = rowSeqName; }

    public List<Entry>     getEntries()    { return entries; }
    public List<ColumnDef> getColumnDefs() { return columnDefs; }

    /**
     * Populates the instance fields from the SWIFT message.
     * Each rowSeqName block (e.g. TRAN / SUBBAL) becomes one row;
     * FIN-level fields are carried into every row.
     * parentSequences[i] holds the ancestor context (GENL + SUBSAFE outer + FIN level)
     * for display in the detail panel; sequences[i] holds only the row-sequence tags (used for deletion).
     */
    public void parse(AbstractMT mt) {
        entries.clear();
        columnDefs.clear();

        SwiftTagListBlock b4 = mt.getSwiftMessage().getBlock4();
        if (b4 == null || b4.getTags().isEmpty()) return;

        if (rowSeqName == null) {
            parseFlatMode(b4);
        } else if ("61".equals(rowSeqName)) {
            parse61Mode(b4);
        } else {
            ParseState state = new ParseState();
            for (Tag t : b4.getTags()) processTag(t, state);
        }
    }

    /**
     * Statement mode (MT 940/950): each :61: tag starts a new row.
     * Tags before the first :61: are header fields inherited by every row.
     */
    private void parse61Mode(SwiftTagListBlock b4) {
        List<Tag>            headerTags   = new ArrayList<>();
        Map<String, String>  headerData   = new LinkedHashMap<>();
        Map<String, Integer> headerCounts = new LinkedHashMap<>();
        Map<String, String>  currentRow   = null;
        List<Tag>            currentTags  = null;
        Map<String, Integer> rowCounts    = null;
        int rowNum = 0;

        for (Tag t : b4.getTags()) {
            if (is61Tag(t)) {
                if (currentRow != null) commit61Row(currentRow, currentTags, headerTags);
                rowNum++;
                currentRow  = new LinkedHashMap<>(headerData);
                rowCounts   = new LinkedHashMap<>();
                currentTags = new ArrayList<>();
                currentRow.put(SEQ_KEY, "61 (" + rowNum + ")");
                registerTag("", t, currentRow, rowCounts);
                currentTags.add(t);
            } else if (currentRow != null) {
                registerTag("", t, currentRow, rowCounts);
                currentTags.add(t);
            } else {
                registerTag("", t, headerData, headerCounts);
                headerTags.add(t);
            }
        }
        if (currentRow != null) commit61Row(currentRow, currentTags, headerTags);
    }

    private static boolean is61Tag(Tag t) {
        return "61".equals(t.getName() != null ? t.getName() : "");
    }

    private void commit61Row(Map<String, String> row, List<Tag> rowTags, List<Tag> headerTags) {
        entries.add(new Entry(row, new SwiftTagListBlock(rowTags), new SwiftTagListBlock(new ArrayList<>(headerTags))));
    }

    /** Flat mode: entire block4 becomes one row (used for message types with no inner repeat, e.g. MT 54x). */
    private void parseFlatMode(SwiftTagListBlock b4) {
        Map<String, String>  row       = new LinkedHashMap<>();
        Map<String, Integer> occCounts = new LinkedHashMap<>();
        Deque<String>        seqStack  = new ArrayDeque<>();

        for (Tag t : b4.getTags()) {
            String name = t.getName() != null ? t.getName() : "";
            if ("16R".equals(name)) {
                seqStack.push(nvl(t.getValue()));
            } else if ("16S".equals(name)) {
                if (!seqStack.isEmpty()) seqStack.pop();
            } else {
                String seq = seqStack.isEmpty() ? "" : seqStack.peek();
                registerTag(seq, t, row, occCounts);
            }
        }

        if (!row.isEmpty()) {
            row.put(SEQ_KEY, "MSG (1)");
            entries.add(new Entry(row, b4, new SwiftTagListBlock(new ArrayList<>())));
        }
    }

    // -----------------------------------------------------------------------
    // Tag dispatch
    // -----------------------------------------------------------------------

    private void processTag(Tag t, ParseState state) {
        if ("16R".equals(t.getName())) {
            processSegmentOpen(t, state);
        } else if ("16S".equals(t.getName())) {
            processSegmentClose(t, state);
        } else {
            processDataTag(t, state);
        }
    }

    // -----------------------------------------------------------------------
    // Segment open
    // -----------------------------------------------------------------------

    private void processSegmentOpen(Tag t, ParseState state) {
        String seg = nvl(t.getValue());
        int depth = state.segStack.size() + 1;

        Map<String, Integer> cnt = state.cntStack.peek();
        if (cnt == null) return;
        cnt.merge(seg, 1, Integer::sum);
        int idx = cnt.get(seg);
        String label = seqLabel(seg) + " (" + idx + ")";

        state.seqLabelStack.push(seqLabel(seg));
        state.segStack.push(seg);
        state.cntStack.push(new LinkedHashMap<>());

        collectAncestorOpen(t, state, seg, depth);
        processFinTranOpen(t, state, seg, depth, label);
    }

    private void collectAncestorOpen(Tag t, ParseState state, String seg, int depth) {
        collectGenlOpen(t, state, seg);
        collectSubSafeOpen(t, state, seg, depth);
    }

    private void collectGenlOpen(Tag t, ParseState state, String seg) {
        if (state.genlCollectDepth > 0) {
            state.genlTags.add(t);
            state.genlCollectDepth++;
        } else if ("GENL".equals(seg)) {
            state.genlCollectDepth = 1;
            state.genlTags.add(t);
        }
    }

    private void collectSubSafeOpen(Tag t, ParseState state, String seg, int depth) {
        if ("SUBSAFE".equals(seg) && state.subSafeDepth < 0) {
            state.subSafeDepth = depth;
            state.subSafeOuterTags = new ArrayList<>();
            state.subSafeOuterTags.add(t);
            state.subSafeData = new LinkedHashMap<>();
            state.subSafeOccCounts = new LinkedHashMap<>();
        } else if (isSubSafeOuterContext(state) && !"FIN".equals(seg)) {
            state.subSafeOuterTags.add(t);
        }
    }

    private void processFinTranOpen(Tag t, ParseState state, String seg, int depth, String label) {
        if ("FIN".equals(seg) && state.finContextData == null) {
            state.finContextData = new LinkedHashMap<>();
            state.finContextData.put(SEQ_KEY, label);
            state.finOccCounts = new LinkedHashMap<>();
            state.finDepth = depth;
            state.tranCount = 0;
            state.finLevelTags = new ArrayList<>();
            state.finLevelTags.add(t);
            state.finLevelFrozen = false;
        } else if (rowSeqName.equals(seg) && state.finContextData != null && state.tranData == null) {
            state.finLevelFrozen = true;
            state.tranCount++;
            state.tranData = new LinkedHashMap<>(state.genlData);
            state.tranData.putAll(state.subSafeData);
            state.tranData.putAll(state.finContextData);
            state.tranData.put(SEQ_KEY, rowSeqName + " (" + state.tranCount + ")");
            state.tranTags = new ArrayList<>();
            state.tranTags.add(t);
            state.tranOccCounts = new LinkedHashMap<>();
            state.tranDepth = depth;
        } else if (state.tranData != null) {
            state.tranTags.add(t);
        } else if (state.finLevelTags != null && !state.finLevelFrozen) {
            state.finLevelTags.add(t);
        }
    }

    // -----------------------------------------------------------------------
    // Segment close
    // -----------------------------------------------------------------------

    private void processSegmentClose(Tag t, ParseState state) {
        int curDep = state.segStack.size();
        String seg = nvl(t.getValue());

        if (state.tranData != null) state.tranTags.add(t);

        finalizeTranOnClose(state, seg, curDep);
        finalizeFinOnClose(state, seg, curDep);
        collectAncestorClose(t, state, seg, curDep);

        if (!state.segStack.isEmpty())    state.segStack.pop();
        if (!state.seqLabelStack.isEmpty()) state.seqLabelStack.pop();
        if (state.cntStack.size() > 1)    state.cntStack.pop();
    }

    private void finalizeTranOnClose(ParseState state, String seg, int curDep) {
        if (!rowSeqName.equals(seg) || state.tranDepth != curDep || state.tranData == null) return;

        List<Tag> parentTags = buildParentTags(state);
        entries.add(new Entry(state.tranData, new SwiftTagListBlock(state.tranTags), new SwiftTagListBlock(parentTags)));

        state.tranData = null;
        state.tranTags = null;
        state.tranOccCounts = null;
        state.tranDepth = -1;
    }

    private List<Tag> buildParentTags(ParseState state) {
        List<Tag> parentTags = new ArrayList<>();
        parentTags.addAll(state.genlTags);
        parentTags.addAll(state.subSafeOuterTags);
        if (state.finLevelTags != null) parentTags.addAll(state.finLevelTags);
        return parentTags;
    }

    private void finalizeFinOnClose(ParseState state, String seg, int curDep) {
        if (!"FIN".equals(seg) || state.finDepth != curDep || state.finContextData == null) return;
        state.finContextData = null;
        state.finOccCounts = null;
        state.finDepth = -1;
        state.tranCount = 0;
        state.finLevelTags = null;
        state.finLevelFrozen = false;
    }

    private void collectAncestorClose(Tag t, ParseState state, String seg, int curDep) {
        collectGenlClose(t, state);
        collectSubSafeClose(t, state, seg, curDep);
        collectFinLevelClose(t, state, seg);
    }

    private void collectGenlClose(Tag t, ParseState state) {
        if (state.genlCollectDepth > 0) {
            state.genlTags.add(t);
            state.genlCollectDepth--;
        }
    }

    private void collectSubSafeClose(Tag t, ParseState state, String seg, int curDep) {
        if ("SUBSAFE".equals(seg) && state.subSafeDepth == curDep) {
            state.subSafeDepth = -1;
        } else if (isSubSafeOuterContext(state) && !"FIN".equals(seg)) {
            state.subSafeOuterTags.add(t);
        }
    }

    private void collectFinLevelClose(Tag t, ParseState state, String seg) {
        if (state.finLevelTags != null && !state.finLevelFrozen
                && !"FIN".equals(seg) && !rowSeqName.equals(seg)) {
            state.finLevelTags.add(t);
        }
    }

    // -----------------------------------------------------------------------
    // Data tag
    // -----------------------------------------------------------------------

    private void processDataTag(Tag t, ParseState state) {
        String seg = state.segStack.isEmpty() ? "" : state.segStack.peek();
        collectAncestorData(t, state, seg);
        if (state.tranData != null) {
            registerTag(seg, t, state.tranData, state.tranOccCounts);
            state.tranTags.add(t);
        } else if (state.finContextData != null) {
            registerTag(seg, t, state.finContextData, state.finOccCounts);
            if (state.finLevelTags != null && !state.finLevelFrozen) state.finLevelTags.add(t);
        }
    }

    private void collectAncestorData(Tag t, ParseState state, String seg) {
        if (state.genlCollectDepth > 0) {
            state.genlTags.add(t);
            registerTag("GENL", t, state.genlData, state.genlOccCounts);
        } else if (isSubSafeOuterContext(state)) {
            state.subSafeOuterTags.add(t);
            registerTag(seg, t, state.subSafeData, state.subSafeOccCounts);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** True when we are inside a SUBSAFE block but outside any FIN. */
    private static boolean isSubSafeOuterContext(ParseState state) {
        return state.subSafeDepth >= 0
                && state.genlCollectDepth == 0
                && state.finContextData == null;
    }

    private static class ParseState {
        final Deque<String>              segStack      = new ArrayDeque<>();
        final Deque<String>              seqLabelStack = new ArrayDeque<>();
        final Deque<Map<String, Integer>> cntStack     = new ArrayDeque<>();

        // FIN context (for table column data)
        Map<String, String>  finContextData = null;
        Map<String, Integer> finOccCounts   = null;
        int finDepth = -1;
        int tranCount = 0;

        // Current row sequence
        Map<String, String>  tranData      = null;
        List<Tag>            tranTags      = null;
        Map<String, Integer> tranOccCounts = null;
        int tranDepth = -1;

        // Ancestor context: GENL (collected via nesting depth counter)
        final List<Tag>            genlTags       = new ArrayList<>();
        int                  genlCollectDepth = 0;
        final Map<String, String>  genlData       = new LinkedHashMap<>();
        final Map<String, Integer> genlOccCounts  = new LinkedHashMap<>();

        // Ancestor context: SUBSAFE outer (tags in SUBSAFE but outside FIN)
        List<Tag>            subSafeOuterTags  = new ArrayList<>();
        int                  subSafeDepth      = -1;
        Map<String, String>  subSafeData       = new LinkedHashMap<>();
        Map<String, Integer> subSafeOccCounts  = new LinkedHashMap<>();

        // Ancestor context: FIN-level tags (outside row sequence, frozen on first row open)
        List<Tag> finLevelTags   = null;
        boolean   finLevelFrozen = false;

        ParseState() {
            cntStack.push(new LinkedHashMap<>());
        }
    }

    private void registerTag(String seqLabel, Tag t,
                             Map<String, String> rowDataMap, Map<String, Integer> occCounts) {
        String qualifier = lookups.extractQualifier(t);
        String tagName   = t.getName();
        String baseKey   = seqLabel + "\t" + tagName + "\t" + qualifier;

        int n = occCounts.merge(baseKey, 1, Integer::sum);
        String key = baseKey + "\t" + n;

        boolean known = false;
        for (ColumnDef cd : columnDefs)
            if (cd.key.equals(key)) { known = true; break; }

        if (!known) {
            String label = seqLabel.trim() + " / " + tagName.trim()
                    + (qualifier.isEmpty() ? "" : " / " + qualifier.trim())
                    + (n > 1 ? " (" + n + ")" : "");
            columnDefs.add(new ColumnDef(seqLabel, tagName, qualifier, n, label
            ));
        }

        rowDataMap.put(key, lookups.valueWithoutQualifier(t));
    }

    private String seqLabel(String seg) { return lookups.seqLabel(seg); }
    private static String nvl(String s)        { return s != null ? s.trim() : ""; }
}