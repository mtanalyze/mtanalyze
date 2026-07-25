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
package com.mtanalyze.ui.view;

import com.prowidesoftware.swift.model.SwiftTagListBlock;
import com.prowidesoftware.swift.model.Tag;
import com.mtanalyze.model.Entry;
import com.mtanalyze.model.EntrySelectionListener;
import com.mtanalyze.model.SwiftMessage;
import com.mtanalyze.parser.Lookups;
import com.mtanalyze.parser.MtParser;
import com.mtanalyze.ui.FilterSupport;
import com.mtanalyze.ui.ToolbarIcons;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.*;
import java.util.List;

/**
 * Embeddable panel that compares multiple selected position-table entries side by side.
 * Each entry occupies its own value column; deviating cells are highlighted in red.
 * A toolbar toggle restricts the view to rows with at least one deviation.
 */
public final class DiffPanel extends JPanel implements EntrySelectionListener {

    private static final String PLACEHOLDER_TEXT = "Select 2 or more rows to compare";

    public DiffPanel() {
        super(new BorderLayout());
        showPlaceholder();
    }

    // -----------------------------------------------------------------------
    // EntrySelectionListener
    // -----------------------------------------------------------------------

    @Override
    public void onSingleEntry(Entry entry, SwiftMessage message) {
        showPlaceholder();
    }

    @Override
    public void onMultipleEntries(List<Entry> entries) {
        if (entries.size() < 2) { showPlaceholder(); return; }
        List<String> labels   = new ArrayList<>();
        List<List<String[]>> rows = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Entry e    = entries.get(i);
            String seq = e.getValue(MtParser.SEQ_KEY);
            labels.add("Entry " + (i + 1));
            rows.add(collectEntryRows(e.fullDisplaySequence(), baseSeq(seq)));
        }
        removeAll();
        add(buildPanel(labels, rows), BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    @Override
    public void onDeselect() {
        showPlaceholder();
    }

    // -----------------------------------------------------------------------

    private void showPlaceholder() {
        removeAll();
        JLabel lbl = new JLabel(PLACEHOLDER_TEXT, SwingConstants.CENTER);
        lbl.setForeground(UIManager.getColor("Label.disabledForeground"));
        add(lbl, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private static String baseSeq(String full) {
        int p = full.lastIndexOf(" (");
        return (p > 0 && full.endsWith(")")) ? full.substring(0, p).trim() : full.trim();
    }

    private static final int FIXED_COLS = 3; // Sequence | Tag | Qualifier
    private static final Lookups LOOKUPS = new Lookups();

    // -----------------------------------------------------------------------
    // Model construction
    // -----------------------------------------------------------------------

    private static DefaultTableModel buildModel(List<String> labels, List<List<String[]>> entryRows) {
        LinkedHashMap<String, String[]> keyMeta = mergeKeys(entryRows);
        Map<String, String[]>           values  = collectValues(labels.size(), entryRows, keyMeta);
        DefaultTableModel model = new DefaultTableModel(buildColumns(labels), 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        populateRows(model, keyMeta, values, labels.size());
        return model;
    }

    /** Merges all row keys from all entries in first-seen order. */
    private static LinkedHashMap<String, String[]> mergeKeys(List<List<String[]>> entryRows) {
        LinkedHashMap<String, String[]> keyMeta = new LinkedHashMap<>();
        for (List<String[]> rows : entryRows)
            for (String[] r : rows)
                keyMeta.computeIfAbsent(r[0], k -> new String[]{r[1], r[2], r[3]});
        return keyMeta;
    }

    /** Builds a value-array (one slot per entry) for every known key. */
    private static Map<String, String[]> collectValues(int numEntries,
            List<List<String[]>> entryRows, LinkedHashMap<String, String[]> keyMeta) {
        Map<String, String[]> valueMap = new LinkedHashMap<>();
        for (String key : keyMeta.keySet())
            valueMap.put(key, new String[numEntries]);
        for (int e = 0; e < entryRows.size(); e++)
            for (String[] r : entryRows.get(e)) {
                String[] vals = valueMap.get(r[0]);
                if (vals != null) vals[e] = r[4];
            }
        return valueMap;
    }

    private static String[] buildColumns(List<String> labels) {
        String[] cols = new String[FIXED_COLS + labels.size()];
        cols[0] = "Sequence"; cols[1] = "Tag"; cols[2] = "Qualifier";
        for (int i = 0; i < labels.size(); i++) cols[FIXED_COLS + i] = labels.get(i);
        return cols;
    }

    private static void populateRows(DefaultTableModel model,
            LinkedHashMap<String, String[]> keyMeta,
            Map<String, String[]> valueMap, int numEntries) {
        for (Map.Entry<String, String[]> e : valueMap.entrySet()) {
            String[] meta = keyMeta.get(e.getKey());
            String[] vals = e.getValue();
            Object[] row  = new Object[FIXED_COLS + numEntries];
            row[0] = meta[0]; row[1] = meta[1]; row[2] = meta[2];
            for (int i = 0; i < numEntries; i++)
                row[FIXED_COLS + i] = vals[i] != null ? vals[i] : "";
            model.addRow(row);
        }
    }

    // -----------------------------------------------------------------------
    // Table
    // -----------------------------------------------------------------------

    private static JTable buildTable(DefaultTableModel model,
            TableRowSorter<DefaultTableModel> sorter, int numEntries) {
        JTable table = new JTable(model);
        table.setRowSorter(sorter);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(22);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        CompareRenderer renderer = new CompareRenderer(numEntries);
        applyColumnWidthsAndRenderers(table, numEntries, renderer);
        SwingUtilities.invokeLater(() -> adjustRowHeights(table));
        sorter.addRowSorterListener(e -> {
            if (e.getType() == javax.swing.event.RowSorterEvent.Type.SORTED)
                adjustRowHeights(table);
        });
        return table;
    }

    private static void adjustRowHeights(JTable table) {
        for (int row = 0; row < table.getRowCount(); row++) {
            int h = table.getRowHeight();
            for (int col = 0; col < table.getColumnCount(); col++) {
                Component c = table.prepareRenderer(table.getCellRenderer(row, col), row, col);
                h = Math.max(h, c.getPreferredSize().height);
            }
            table.setRowHeight(row, h);
        }
    }

    private static void applyColumnWidthsAndRenderers(JTable table, int numEntries,
            CompareRenderer renderer) {
        TableColumnModel tcm = table.getColumnModel();
        setCol(tcm, 0, 80,  renderer);
        setCol(tcm, 1, 60,  renderer);
        setCol(tcm, 2, 100, renderer);
        for (int i = 0; i < numEntries; i++)
            setCol(tcm, FIXED_COLS + i, 220, renderer);
    }

    private static void setCol(TableColumnModel tcm, int idx, int width,
            TableCellRenderer renderer) {
        TableColumn col = tcm.getColumn(idx);
        col.setPreferredWidth(width);
        col.setCellRenderer(renderer);
    }

    // -----------------------------------------------------------------------
    // Table popup and close panel
    // -----------------------------------------------------------------------

    private static void addTablePopup(JTable table, TableRowSorter<DefaultTableModel> sorter,
                                       int numEntries) {
        JCheckBoxMenuItem diffOnlyItem = new JCheckBoxMenuItem("Differences only", ToolbarIcons.menuDiff(), false);
        diffOnlyItem.addActionListener(e ->
            sorter.setRowFilter(diffOnlyItem.isSelected() ? buildDiffFilter(numEntries) : null));
        JMenuItem copyItem = new JMenuItem("Copy Table", ToolbarIcons.menuCopyTable());
        copyItem.addActionListener(e -> copyTableWithHighlights(table, numEntries));
        JPopupMenu popup = new JPopupMenu();
        popup.add(diffOnlyItem);
        popup.add(copyItem);
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed (java.awt.event.MouseEvent e) { maybeShow(e); }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) { maybeShow(e); }
            private void maybeShow(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) popup.show(e.getComponent(), e.getX(), e.getY());
            }
        });
    }

    // -----------------------------------------------------------------------
    // Clipboard export with diff highlights (for pasting into Word/Outlook)
    // -----------------------------------------------------------------------

    /**
     * Copies the table to the clipboard in two flavors at once: plain TSV text
     * (so Excel/Notepad get tab-separated values) and HTML with the same
     * yellow diff highlighting used on screen (so Word/Outlook paste a
     * formatted table instead of raw text).
     */
    private static void copyTableWithHighlights(JTable table, int numEntries) {
        if (table.getRowCount() == 0) return;
        String plain = FilterSupport.buildTableTsv(table);
        String html  = buildHtmlTable(table, numEntries);
        DataFlavor htmlFlavor;
        try {
            htmlFlavor = new DataFlavor("text/html;class=java.lang.String");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
        final DataFlavor hf = htmlFlavor;
        Transferable transferable = new Transferable() {
            @Override public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{ hf, DataFlavor.stringFlavor };
            }
            @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
                return flavor.equals(hf) || flavor.equals(DataFlavor.stringFlavor);
            }
            @Override public Object getTransferData(DataFlavor flavor) {
                return flavor.equals(hf) ? html : plain;
            }
        };
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, null);
    }

    private static String buildHtmlTable(JTable table, int numEntries) {
        TableModel model    = table.getModel();
        int        viewRows = table.getRowCount();
        int        viewCols = table.getColumnCount();
        StringBuilder sb = new StringBuilder();
        sb.append("<table style=\"border-collapse:collapse;font-family:Segoe UI,Arial,sans-serif;font-size:12px\">");
        sb.append("<tr>");
        for (int vc = 0; vc < viewCols; vc++)
            sb.append("<th style=\"border:1px solid #999;background-color:#f0f0f0;padding:4px 8px;text-align:left\">")
              .append(escHtml(table.getColumnName(vc)))
              .append("</th>");
        sb.append("</tr>");
        for (int vr = 0; vr < viewRows; vr++) {
            int     modelRow = table.convertRowIndexToModel(vr);
            boolean diff     = numEntries >= 2 && rowHasDiff(model, modelRow, numEntries);
            String  ref      = str(model.getValueAt(modelRow, FIXED_COLS));
            sb.append("<tr>");
            for (int vc = 0; vc < viewCols; vc++) {
                int    modelCol = table.convertColumnIndexToModel(vc);
                String text     = str(model.getValueAt(modelRow, modelCol));
                sb.append("<td style=\"border:1px solid #ccc;padding:4px 8px;vertical-align:top;white-space:pre-wrap\">");
                if (diff && modelCol >= FIXED_COLS)
                    sb.append(modelCol == FIXED_COLS ? "<b>" + escHtml(text) + "</b>" : inlineDiff(text, ref));
                else
                    sb.append(escHtml(text));
                sb.append("</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    private static RowFilter<DefaultTableModel, Integer> buildDiffFilter(int numEntries) {
        return new RowFilter<>() {
            @Override
            public boolean include(RowFilter.Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                if (numEntries < 2) return true;
                Object first = entry.getValue(FIXED_COLS);
                for (int i = 1; i < numEntries; i++)
                    if (!Objects.equals(first, entry.getValue(FIXED_COLS + i))) return true;
                return false;
            }
        };
    }

    // -----------------------------------------------------------------------
    // Embeddable panel (used by the inline Compare view in MtAnalyze)
    // -----------------------------------------------------------------------

    public static JPanel buildPanel(List<String> labels, List<List<String[]>> entryRows) {
        int numEntries = labels.size();
        DefaultTableModel model = buildModel(labels, entryRows);
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        JTable table = buildTable(model, sorter, numEntries);

        addTablePopup(table, sorter, numEntries);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    // -----------------------------------------------------------------------
    // Static data collection (called by MtAnalyze)
    // -----------------------------------------------------------------------

    /**
     * Parses a {@link SwiftTagListBlock} and returns one {@code String[]} per
     * non-boundary tag: {@code [key, seqLabel, tagName, qualifier, value]}.
     * The key uniquely identifies the tag occurrence within the entry.
     */
    public static List<String[]> collectEntryRows(SwiftTagListBlock seq, String baseSeq) {
        List<String[]>             rows   = new ArrayList<>();
        Deque<String>              seqStk = new ArrayDeque<>();
        Deque<String>              qlStk  = new ArrayDeque<>();
        Deque<Map<String, Integer>> occStk = new ArrayDeque<>();
        occStk.push(new LinkedHashMap<>());
        Map<String, Integer> occCount = new LinkedHashMap<>();

        for (Tag t : seq.getTags()) {
            if (isBoundaryTag(t, qlStk, seqStk, occStk)) continue;
            String seqLabel  = seqStk.isEmpty() ? baseSeq : seqStk.peek();
            String tagName   = t.getName();
            String qualifier = LOOKUPS.extractQualifier(t);
            String value     = LOOKUPS.valueWithoutQualifier(t);
            String occKey    = seqLabel + "\t" + tagName + "\t" + qualifier;
            int    occ       = occCount.merge(occKey, 1, Integer::sum);
            rows.add(new String[]{occKey + "\t" + occ, seqLabel, tagName, qualifier, value});
        }
        return rows;
    }

    private static boolean isBoundaryTag(Tag t, Deque<String> qlStk,
            Deque<String> seqStk, Deque<Map<String, Integer>> occStk) {
        if ("16R".equals(t.getName())) { push16R(t, qlStk, seqStk, occStk); return true; }
        if ("16S".equals(t.getName())) { pop16S(qlStk, seqStk, occStk);     return true; }
        return false;
    }

    private static void push16R(Tag t, Deque<String> qlStk,
            Deque<String> seqStk, Deque<Map<String, Integer>> occStk) {
        String seg   = t.getValue() != null ? t.getValue().trim() : "";
        String child = LOOKUPS.seqLabel(seg);
        Map<String, Integer> top = occStk.peek();
        int n = top != null ? top.merge(child, 1, Integer::sum) : 1;
        qlStk.push(seg);
        seqStk.push(n > 1 ? child + "." + n : child);
        occStk.push(new LinkedHashMap<>());
    }

    private static void pop16S(Deque<String> qlStk,
            Deque<String> seqStk, Deque<Map<String, Integer>> occStk) {
        if (!qlStk.isEmpty())     qlStk.pop();
        if (!seqStk.isEmpty())    seqStk.pop();
        if (occStk.size() > 1)   occStk.pop();
    }

    // -----------------------------------------------------------------------
    // Cell renderer
    // -----------------------------------------------------------------------

    private static final class CompareRenderer extends DefaultTableCellRenderer {

        private final int numEntries;

        CompareRenderer(int numEntries) {
            this.numEntries = numEntries;
            setVerticalAlignment(TOP);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            setVerticalAlignment(TOP);
            String text = value != null ? value.toString() : "";
            if (!isSelected && col >= FIXED_COLS && hasDiff(table, row))
                setText("<html>" + buildDiffHtml(table, row, col, text) + "</html>");
            else if (text.contains("\n"))
                setText("<html>" + escHtml(text) + "</html>");
            return this;
        }

        private String buildDiffHtml(JTable table, int viewRow, int col, String text) {
            int modelRow = table.convertRowIndexToModel(viewRow);
            String ref   = str(table.getModel().getValueAt(modelRow, FIXED_COLS));
            if (col == FIXED_COLS)
                return "<b>" + escHtml(text) + "</b>";
            String val = str(table.getModel().getValueAt(modelRow, col));
            return inlineDiff(val, ref);
        }

        private boolean hasDiff(JTable table, int viewRow) {
            if (numEntries < 2) return false;
            int modelRow = table.convertRowIndexToModel(viewRow);
            return rowHasDiff(table.getModel(), modelRow, numEntries);
        }
    }

    /** Shared by the on-screen renderer and the clipboard HTML export. */
    private static boolean rowHasDiff(TableModel model, int modelRow, int numEntries) {
        Object first = model.getValueAt(modelRow, FIXED_COLS);
        for (int i = 1; i < numEntries; i++)
            if (!Objects.equals(first, model.getValueAt(modelRow, FIXED_COLS + i)))
                return true;
        return false;
    }

    private static String inlineDiff(String val, String ref) {
        if (val.equals(ref)) return escHtml(val);
        int pre = prefixLen(val, ref);
        int suf = suffixLen(val, ref, pre);
        String head = val.substring(0, pre);
        String mid  = val.substring(pre, val.length() - suf);
        String tail = val.substring(val.length() - suf);
        String result = escHtml(head);
        if (!mid.isEmpty())
            result += "<span style=\"background-color:#FFE082;color:#0000CC;text-decoration:underline\">" + escHtml(mid) + "</span>";
        result += escHtml(tail);
        return result;
    }

    private static int prefixLen(String a, String b) {
        int n = Math.min(a.length(), b.length());
        for (int i = 0; i < n; i++) if (a.charAt(i) != b.charAt(i)) return i;
        return n;
    }

    private static int suffixLen(String a, String b, int pre) {
        int max = Math.min(a.length(), b.length()) - pre;
        int s = 0;
        while (s < max && a.charAt(a.length() - 1 - s) == b.charAt(b.length() - 1 - s)) s++;
        return s;
    }

    private static String str(Object o)    { return o != null ? o.toString().trim() : ""; }
    private static String escHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\n", "<br>");
    }
}