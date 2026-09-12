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
package com.mtanalyze.profile;

import com.prowidesoftware.swift.model.SwiftTagListBlock;
import com.prowidesoftware.swift.model.Tag;
import com.mtanalyze.parser.Lookups;

import javax.swing.table.DefaultTableModel;
import java.util.*;

public final class DataHelper {

    private static final String COMP_PREFIX = "Comp. ";

    private final Lookups lookups = new Lookups();

    // -----------------------------------------------------------------------
    // Detail table (right panel)
    // -----------------------------------------------------------------------

    public List<String[]> collectAllComponentRows(List<SwiftTagListBlock> seqs,
                                                   List<Map<String, String>> rowData,
                                                   String seqKey) {
        List<String[]> result = new ArrayList<>();
        for (int i = 0; i < seqs.size(); i++) {
            String label = (i < rowData.size()) ? rowData.get(i).getOrDefault(seqKey, "") : "";
            collectEntryComponentRows(result, seqs.get(i), stripTrailingNumericCounter(label),
                                      String.valueOf(i));
        }
        return result;
    }

    private void collectEntryComponentRows(List<String[]> out, SwiftTagListBlock seq,
                                                   String baseSeq, String entry) {
        TagStacks s = new TagStacks();
        for (Tag t : seq.getTags())
            collectTag(out, t, baseSeq, entry, s);
    }

    private void collectTag(List<String[]> out, Tag t, String baseSeq, String entry, TagStacks s) {
        if (handleBoundaryTag(t, "", s)) return;
        String seqLabel  = s.seqLabel.isEmpty() ? baseSeq : s.seqLabel.peek();
        String qualifier = lookups.extractQualifier(t);
        com.prowidesoftware.swift.model.field.Field field = t.asField();
        if (field != null)
            collectFieldComponents(out, field, entry, seqLabel, t.getName(), qualifier);
        else
            out.add(new String[]{entry, seqLabel, t.getName(), qualifier, "", lookups.valueWithoutQualifier(t)});
    }

    private static void collectFieldComponents(List<String[]> out,
                                               com.prowidesoftware.swift.model.field.Field field,
                                               String entry, String seqLabel,
                                               String tagName, String qualifier) {
        boolean firstShown = true;
        for (int c = 1; c <= field.componentsSize(); c++) {
            if (!shouldAddComponentRow(field, c)) continue;
            String lbl = nvl(field.getComponentLabel(c));
            out.add(new String[]{
                entry, seqLabel, tagName,
                firstShown ? qualifier : "",
                lbl.isEmpty() ? (COMP_PREFIX + c) : lbl,
                nvl(field.getValueDisplay(c, null))
            });
            firstShown = false;
        }
    }

    // -----------------------------------------------------------------------
    // Typed component cells (numbers stay as Number — used by Excel export)
    // -----------------------------------------------------------------------

    public record CompCell(String entry, String seqLabel, String tagName, String qualifier,
                           String label, Object value) {}

    /**
     * @param mtKey row-data key holding each entry's message type (e.g. {@code "MT536"}),
     *              used to resolve nested sequences to Prowide's letter path (e.g. {@code "B1"})
     *              the same way the MT Entries table does.
     */
    public List<CompCell> collectAllComponentCells(List<SwiftTagListBlock> seqs,
                                                    List<Map<String, String>> rowData,
                                                    String seqKey, String mtKey) {
        // Prowide logs a WARNING via SwiftFormatUtils whenever getComponentAsNumber()
        // is called on a non-numeric component (e.g. an indicator like "RECE").
        // The exception is handled internally; only the log is noisy. Quiet it for
        // the duration of the typed walk so the export stays silent.
        java.util.logging.Logger swiftFmt =
            java.util.logging.Logger.getLogger("com.prowidesoftware.swift.utils.SwiftFormatUtils");
        java.util.logging.Level previousLevel = swiftFmt.getLevel();
        swiftFmt.setLevel(java.util.logging.Level.SEVERE);
        try {
            List<CompCell> result = new ArrayList<>();
            for (int i = 0; i < seqs.size(); i++) {
                Map<String, String> entryData = i < rowData.size() ? rowData.get(i) : Map.of();
                String label = entryData.getOrDefault(seqKey, "");
                String mt    = entryData.getOrDefault(mtKey, "");
                collectEntryComponentCells(result, seqs.get(i), stripTrailingNumericCounter(label),
                                           String.valueOf(i), mt);
            }
            return result;
        } finally {
            swiftFmt.setLevel(previousLevel);
        }
    }

    private void collectEntryComponentCells(List<CompCell> out, SwiftTagListBlock seq,
                                            String baseSeq, String entry, String mt) {
        TagStacks s = new TagStacks();
        for (Tag t : seq.getTags())
            collectTagCells(out, t, baseSeq, entry, mt, s);
    }

    private void collectTagCells(List<CompCell> out, Tag t, String baseSeq, String entry, String mt,
                                  TagStacks s) {
        if (handleBoundaryTag(t, mt, s)) return;
        String seqLabel  = s.seqLabel.isEmpty() ? baseSeq : s.seqLabel.peek();
        String qualifier = lookups.extractQualifier(t);
        com.prowidesoftware.swift.model.field.Field field = t.asField();
        if (field != null)
            collectFieldComponentCells(out, field, entry, seqLabel, t.getName(), qualifier);
        else
            out.add(new CompCell(entry, seqLabel, t.getName(), qualifier, "",
                                 lookups.valueWithoutQualifier(t)));
    }

    private static void collectFieldComponentCells(List<CompCell> out,
                                                    com.prowidesoftware.swift.model.field.Field field,
                                                    String entry, String seqLabel,
                                                    String tagName, String qualifier) {
        boolean firstShown = true;
        for (int c = 1; c <= field.componentsSize(); c++) {
            if (!shouldAddComponentRow(field, c)) continue;
            String lbl = nvl(field.getComponentLabel(c));
            Number num = field.getComponentAsNumber(c);
            Object value = num != null ? num : nvl(field.getComponent(c));
            out.add(new CompCell(entry, seqLabel, tagName,
                                 firstShown ? qualifier : "",
                                 lbl.isEmpty() ? (COMP_PREFIX + c) : lbl,
                                 value));
            firstShown = false;
        }
    }

    public void refreshDetailTable(DefaultTableModel model,
                                    RowContext ctx,
                                    boolean showComponents,
                                    List<String[]> headerEntries, String mt) {
        model.setRowCount(0);
        for (String[] entry : headerEntries)
            addInfoRow(model, entry[0], entry[1], showComponents, entry.length > 2 && "1".equals(entry[2]));
        SwiftTagListBlock seq = validateAndGetSequence(ctx.seqs(), ctx.modelRow());
        if (seq == null) return;

        String baseSeq = extractBaseSequence(ctx.rowData(), ctx.seqKey(), ctx.modelRow());
        processDetailTags(model, seq, baseSeq, showComponents, mt);
    }

    /** The row-table slice {@link #refreshDetailTable} needs to resolve which sequence to detail. */
    public record RowContext(List<SwiftTagListBlock> seqs, List<Map<String, String>> rowData,
                              String seqKey, int modelRow) {}

    private static void addInfoRow(DefaultTableModel model, String label, String value,
                                    boolean showComponents, boolean alwaysAdd) {
        if (!alwaysAdd && (value == null || value.isEmpty())) return;
        String v = value != null ? value : "";
        if (showComponents)
            model.addRow(new Object[]{"", label, "", "", v});
        else
            model.addRow(new Object[]{"", label, "", v});
    }

    private static SwiftTagListBlock validateAndGetSequence(List<SwiftTagListBlock> seqs, int modelRow) {
        if (modelRow < 0 || modelRow >= seqs.size()) return null;
        return seqs.get(modelRow);
    }

    private static String extractBaseSequence(List<Map<String, String>> rowData, String seqKey, int modelRow) {
        String fullLabel = (modelRow < rowData.size())
            ? rowData.get(modelRow).getOrDefault(seqKey, "") : "";
        return stripTrailingNumericCounter(fullLabel);
    }

    private static String stripTrailingNumericCounter(String label) {
        String trimmed = label.trim();
        int closeParen = trimmed.length() - 1;
        if (closeParen < 2 || trimmed.charAt(closeParen) != ')') return trimmed;

        int openParen = trimmed.lastIndexOf('(', closeParen);
        if (openParen < 0) return trimmed;

        for (int i = openParen + 1; i < closeParen; i++) {
            if (!Character.isDigit(trimmed.charAt(i))) return trimmed;
        }
        if (openParen + 1 == closeParen) return trimmed;

        return trimmed.substring(0, openParen).trim();
    }

    private void processDetailTags(DefaultTableModel model, SwiftTagListBlock seq,
                                          String baseSeq, boolean showComponents, String mt) {
        TagStacks s = new TagStacks();
        for (Tag t : seq.getTags())
            processDetailTag(model, t, baseSeq, showComponents, mt, s);
    }

    private void processDetailTag(DefaultTableModel model, Tag t, String baseSeq,
                                         boolean showComponents, String mt, TagStacks s) {
        if (handleBoundaryTag(t, mt, s)) return;
        String seqLabel = s.seqLabel.isEmpty() ? prowideOrFallback(mt, baseSeq) : s.seqLabel.peek();
        addDetailRows(model, t, seqLabel, showComponents);
    }

    /**
     * Prowide's sequence letter-path (e.g. {@code "B1a2A"}) for qualifier {@code qualifier}
     * (e.g. {@code "SETPRTY"}) in message type {@code mt}, or {@code qualifier} itself when
     * Prowide can't resolve it (unknown/blank {@code mt}, e.g. the CSV component-export caller
     * below, which always passes {@code ""}) -- never leaves the Sequence column blank just
     * because the reflective lookup came up empty.
     */
    private String prowideOrFallback(String mt, String qualifier) {
        String label = lookups.prowideSequenceCode(mt, qualifier);
        return label.isEmpty() ? qualifier : label;
    }

    private void handle16RDetailTag(Tag t, String mt, TagStacks s) {
        String seg    = nvl(t.getValue());

        String child  = lookups.seqLabel(seg);
        Map<String, Integer> occ = s.occ.peek();
        if (occ == null) return;
        int n = occ.merge(child, 1, Integer::sum);
        s.qual.push(seg);
        String label = prowideOrFallback(mt, child);
        s.seqLabel.push(n > 1 ? label + "." + n : label);
        s.occ.push(new LinkedHashMap<>());
    }

    private static void handle16SDetailTag(TagStacks s) {
        if (!s.qual.isEmpty())     s.qual.pop();
        if (!s.seqLabel.isEmpty()) s.seqLabel.pop();
        if (s.occ.size() > 1)      s.occ.pop();
    }

    private void addDetailRows(DefaultTableModel model, Tag t, String seqLabel, boolean showComponents) {
        String tagName   = t.getName();
        String qualifier = lookups.extractQualifier(t);
        com.prowidesoftware.swift.model.field.Field field = t.asField();

        if (showComponents && field != null) {
            addComponentRows(model, field, seqLabel, tagName, qualifier);
        } else if (showComponents) {
            model.addRow(new Object[]{seqLabel, tagName, qualifier, "", lookups.valueWithoutQualifier(t)});
        } else {
            model.addRow(new Object[]{seqLabel, tagName, qualifier, lookups.valueWithoutQualifier(t)});
        }
    }

    private static void addComponentRows(DefaultTableModel model, com.prowidesoftware.swift.model.field.Field field,
                                        String seqLabel, String tagName, String qualifier) {
        boolean firstShown = true;
        for (int c = 1; c <= field.componentsSize(); c++) {
            if (shouldAddComponentRow(field, c)) {
                String lbl = nvl(field.getComponentLabel(c));
                String cv = nvl(field.getValueDisplay(c, null));
                model.addRow(new Object[]{
                    seqLabel, tagName,
                    firstShown ? qualifier : "",
                    lbl.isEmpty() ? (COMP_PREFIX + c) : lbl,
                    cv.trim()
                });
                firstShown = false;
            }
        }
    }

    private static boolean shouldAddComponentRow(com.prowidesoftware.swift.model.field.Field field, int c) {
        if (field.getComponent(c) == null) return false;
        String lbl = nvl(field.getComponentLabel(c));
        if ("qualifier".equalsIgnoreCase(lbl)) return false;
        String cv = nvl(field.getValueDisplay(c, null));
        return !cv.trim().isEmpty();
    }

    private static String nvl(String s) { return s != null ? s.trim() : ""; }

    private boolean handleBoundaryTag(Tag t, String mt, TagStacks s) {
        if ("16R".equals(t.getName())) { handle16RDetailTag(t, mt, s); return true; }
        if ("16S".equals(t.getName())) { handle16SDetailTag(s); return true; }
        return false;
    }

    private static final class TagStacks {
        final Deque<String>               qual     = new ArrayDeque<>();
        final Deque<String>               seqLabel = new ArrayDeque<>();
        final Deque<Map<String, Integer>> occ      = new ArrayDeque<>();
        TagStacks() { occ.push(new LinkedHashMap<>()); }
    }
}