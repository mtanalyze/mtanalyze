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

import com.mtanalyze.export.CsvExport;
import com.mtanalyze.ui.EditMenuContributor;
import com.mtanalyze.ui.FilterSupport;
import com.mtanalyze.ui.ToolbarIcons;
import com.mtanalyze.ui.filter.ColumnFilterRow;
import com.mtanalyze.ui.filter.FinFilterRow;
import com.mtanalyze.util.FileChoosers;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

public class AccountMappingPanel extends RoundedPanel implements EditMenuContributor {

    private static final char UTF8_BOM = '\uFEFF';

    private static final int COL_SAFE        = 0;
    private static final int COL_SECURITIES  = 1;
    private static final int COL_CASH        = 2;
    private static final int COL_DESCRIPTION = 3;

    private final transient Preferences                        prefs;
    private final String                                       prefKey;
    private final transient CsvExport.Prefs                   csvPrefs;
    private final DefaultTableModel                            tableModel;
    private final transient TableRowSorter<DefaultTableModel>  sorter;
    private final JTable                             table;
    private final ColumnFilterRow                    columnFilterRow;
    private final FinFilterRow                       finFilterRow;
    private       JScrollPane                        scrollPane;

    public AccountMappingPanel(Preferences prefs, String prefKey, CsvExport.Prefs csvPrefs) {
        super(new BorderLayout(0, 0));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        this.prefs    = prefs;
        this.prefKey  = prefKey;
        this.csvPrefs = csvPrefs;

        tableModel      = buildTableModel();
        load();

        sorter          = new TableRowSorter<>(tableModel);
        columnFilterRow = new ColumnFilterRow(this::applyFilters, () -> { /* no convert needed */ });
        finFilterRow    = new FinFilterRow(this::applyFilters, null);
        table           = buildTable();
        bindDeleteKey();

        add(scrollPane, BorderLayout.CENTER);
    }

    public JTable      getTable()     { return table; }
    @Override
    public JPopupMenu  getPopupMenu() { return buildPopup(); }

    public boolean isFinFilterOrMode() { return finFilterRow.isOrMode(); }

    public void setFinFilterOrMode(boolean or) {
        finFilterRow.setOrMode(or);
        applyFilters();
    }

    // -----------------------------------------------------------------------

    private DefaultTableModel buildTableModel() {
        DefaultTableModel m = new DefaultTableModel(
                new String[]{"SAFE", "Securities Account No.", "Cash Account No.", "Description"}, 0) {
            @Override public Class<?> getColumnClass(int c) { return String.class; }
        };
        m.addTableModelListener(e -> {
            save();
            if (columnFilterRow != null) columnFilterRow.updateColumnValues(m);
        });
        return m;
    }

    private JTable buildTable() {
        JTable t = new JTable(tableModel) {
            @Override
            protected void configureEnclosingScrollPane() {
                super.configureEnclosingScrollPane();
                FilterSupport.installFilterRowsInScrollPane(scrollPane, getTableHeader(), columnFilterRow, finFilterRow);
            }
        };
        t.setRowSorter(sorter);
        t.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        t.setFillsViewportHeight(true);
        t.setRowHeight(22);
        t.getColumnModel().getColumn(COL_SAFE).setPreferredWidth(120);
        t.getColumnModel().getColumn(COL_SECURITIES).setPreferredWidth(160);
        t.getColumnModel().getColumn(COL_CASH).setPreferredWidth(160);
        t.getColumnModel().getColumn(COL_DESCRIPTION).setPreferredWidth(300);

        columnFilterRow.rebuild(t.getColumnModel(), tableModel);
        finFilterRow.rebuild(t.getColumnModel(), tableModel.getColumnCount());

        t.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                columnFilterRow.refreshLayout();
                finFilterRow.refreshLayout();
            }
        });

        t.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed (MouseEvent e) { maybeShow(e); }
            @Override public void mouseReleased(MouseEvent e) { maybeShow(e); }
            private void maybeShow(MouseEvent e) {
                if (e.isPopupTrigger()) buildPopup().show(e.getComponent(), e.getX(), e.getY());
            }
        });

        scrollPane = new JScrollPane(t);
        return t;
    }

    private JPopupMenu buildPopup() {
        JPopupMenu popup = new JPopupMenu();

        JMenuItem addItem = new JMenuItem("Add Account Mapping", ToolbarIcons.menuAddDict());
        addItem.addActionListener(e -> onAdd());

        JMenuItem deleteItem = new JMenuItem("Delete Account Mapping", ToolbarIcons.menuDelete());
        deleteItem.setEnabled(table.getSelectedRowCount() > 0);
        deleteItem.addActionListener(e -> onDelete());

        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JMenuItem copyCellItem = FilterSupport.makeCopyCellItem(table);
        copyCellItem.setEnabled(FilterSupport.hasCellSelection(table));
        copyCellItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask));
        popup.add(copyCellItem);

        JMenuItem copyItem  = new JMenuItem("Copy Table",  ToolbarIcons.menuCopyTable());
        copyItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, menuMask));
        copyItem.addActionListener(e -> onCopyTable());
        JMenuItem pasteItem = new JMenuItem("Paste Table", ToolbarIcons.menuPaste());
        pasteItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, menuMask));
        pasteItem.addActionListener(e -> pasteFromClipboard());
        popup.add(copyItem);
        popup.add(pasteItem);
        popup.addSeparator();

        popup.add(addItem);
        popup.add(deleteItem);
        popup.addSeparator();

        int viewRow = table.getSelectedRow();
        int viewCol = table.getSelectedColumn();
        boolean hasCell = viewRow >= 0 && viewCol >= 0;
        JMenuItem addToFilterItem = new JMenuItem("Add to Quick Filter", ToolbarIcons.quickFilter());
        addToFilterItem.setEnabled(hasCell);
        if (hasCell) {
            int modelCol = table.convertColumnIndexToModel(viewCol);
            Object val = table.getValueAt(viewRow, viewCol);
            String expr = "=" + (val != null ? val.toString().trim() : "");
            addToFilterItem.addActionListener(e -> finFilterRow.appendToFilter(modelCol, expr));
        }
        popup.add(addToFilterItem);

        JMenuItem clearFiltersItem = new JMenuItem("Clear All Filters", ToolbarIcons.menuFilterClear());
        clearFiltersItem.addActionListener(e -> { columnFilterRow.clearAll(); finFilterRow.clearAll(); });
        popup.add(clearFiltersItem);

        boolean or = finFilterRow.isOrMode();
        JMenuItem filterModeItem = new JMenuItem(
                or ? "Quick Filter: OR" : "Quick Filter: AND",
                or ? ToolbarIcons.filterOr() : ToolbarIcons.filterAnd());
        filterModeItem.addActionListener(e -> setFinFilterOrMode(!finFilterRow.isOrMode()));
        popup.add(filterModeItem);

        return popup;
    }

    // -----------------------------------------------------------------------

    private void applyFilters() {
        if (sorter == null) return;
        Map<Integer, Set<String>> dropFilters =
                columnFilterRow != null ? columnFilterRow.getActiveFilters() : Collections.emptyMap();
        Map<Integer, String> quickFilters =
                finFilterRow != null ? finFilterRow.getActiveFilters() : Collections.emptyMap();
        boolean orMode = finFilterRow != null && finFilterRow.isOrMode();
        FilterSupport.applyRowFilter(sorter, dropFilters, quickFilters, orMode);
    }

    // -----------------------------------------------------------------------

    private void onAdd() {
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
        tableModel.addRow(new Object[]{"", "", "", ""});
        int row = tableModel.getRowCount() - 1;
        table.scrollRectToVisible(table.getCellRect(row, COL_SAFE, true));
        table.editCellAt(row, COL_SAFE);
        table.requestFocusInWindow();
    }

    private void onDelete() {
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
        int[] rows = table.getSelectedRows();
        for (int i = rows.length - 1; i >= 0; i--)
            tableModel.removeRow(table.convertRowIndexToModel(rows[i]));
    }

    private void onCopyTable() { FilterSupport.copyTableToClipboard(table); }

    public void pasteFromClipboard() {
        try {
            java.awt.datatransfer.Transferable t =
                    Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (t == null || !t.isDataFlavorSupported(DataFlavor.stringFlavor)) return;
            String text = (String) t.getTransferData(DataFlavor.stringFlavor);
            if (table.isEditing()) table.getCellEditor().stopCellEditing();
            pasteText(text);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                    "Paste failed:\n" + ex.getMessage(), "Paste Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void pasteText(String text) {
        String[] lines = text.split("\r?\n", -1);
        int lineCount = lines.length;
        while (lineCount > 0 && lines[lineCount - 1].isBlank()) lineCount--;
        if (lineCount == 0) return;
        int start = lines[0].split("\t", -1)[0].trim().equalsIgnoreCase("SAFE") ? 1 : 0;
        Set<String> existing = existingKeys();
        int added = 0;
        int skipped = 0;
        for (int i = start; i < lineCount; i++) {
            String[] cols = lines[i].split("\t", -1);
            String safe = cols.length > 0 ? cols[0].trim() : "";
            if (!safe.isEmpty()) {
                if (existing.add(safe)) {
                    tableModel.addRow(buildPasteRow(safe, cols));
                    added++;
                } else {
                    skipped++;
                }
            }
        }
        if (skipped > 0)
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                    added + " entries pasted, " + skipped + " duplicates skipped.",
                    "Paste Table", JOptionPane.INFORMATION_MESSAGE);
    }

    private static Object[] buildPasteRow(String safe, String[] cols) {
        return new Object[]{safe,
            cols.length > 1 ? cols[1].trim() : "",
            cols.length > 2 ? cols[2].trim() : "",
            cols.length > 3 ? cols[3].trim() : ""};
    }

    public void showImportDialog() { onImport(); }
    public void showExportDialog() { onExport(); }

    private void onImport() {
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
        JFileChooser fc = FileChoosers.create();
        fc.setDialogTitle("Import Account Mapping");
        fc.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        if (fc.showOpenDialog(SwingUtilities.getWindowAncestor(this)) != JFileChooser.APPROVE_OPTION) return;
        try {
            Set<String> existing = existingKeys();
            int added = 0;
            int skipped = 0;
            for (String[] row : readCsv(fc.getSelectedFile(), csvPrefs.fieldSep())) {
                if (existing.add(row[COL_SAFE])) {
                    tableModel.addRow(new Object[]{row[0], row[1], row[2], row[3]});
                    added++;
                } else {
                    skipped++;
                }
            }
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                    added + " entries imported, " + skipped + " duplicates skipped.",
                    "Import", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                    "Import failed:\n" + ex.getMessage(), "Import Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onExport() {
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
        JFileChooser fc = FileChoosers.create();
        fc.setDialogTitle("Export Account Mapping");
        fc.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        fc.setSelectedFile(new File("account_mapping.csv"));
        if (fc.showSaveDialog(SwingUtilities.getWindowAncestor(this)) != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".csv"))
            file = new File(file.getAbsolutePath() + ".csv");
        String fs = csvPrefs.fieldSep();
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            pw.write(UTF8_BOM);
            pw.println("SAFE" + fs + "Securities Account No." + fs + "Cash Account No." + fs + "Description");
            for (String[] e : collectEntries())
                pw.println(csvQuote(e[0], fs) + fs + csvQuote(e[1], fs) + fs + csvQuote(e[2], fs) + fs + csvQuote(e[3], fs));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                    "Export failed:\n" + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // -----------------------------------------------------------------------

    private void load() {
        tableModel.setRowCount(0);
        String raw = prefs.get(prefKey, "");
        for (String line : raw.split("\n", -1)) {
            if (line.isEmpty()) continue;
            String[] parts = line.split(";", -1);
            tableModel.addRow(new Object[]{field(parts, 0), field(parts, 1), field(parts, 2), field(parts, 3)});
        }
    }

    private void save() {
        List<String[]> entries = collectEntries();
        StringBuilder sb = new StringBuilder();
        for (String[] e : entries)
            sb.append(e[0]).append(';').append(e[1]).append(';').append(e[2]).append(';').append(e[3]).append('\n');
        try {
            prefs.put(prefKey, sb.toString());
        } catch (IllegalArgumentException ignored) {
            // too many entries for prefs storage — silent, user will notice on export
        }
    }

    public String lookupSafeBySecuritiesAccount(String accountNo) {
        return lookupSafe(accountNo, COL_SECURITIES);
    }

    public String lookupSafeByCashAccount(String accountNo) {
        return lookupSafe(accountNo, COL_CASH);
    }

    private String lookupSafe(String accountNo, int col) {
        if (accountNo == null || accountNo.isBlank()) return null;
        for (int r = 0; r < tableModel.getRowCount(); r++) {
            if (accountNo.equalsIgnoreCase(nvl(tableModel.getValueAt(r, col))))
                return nvl(tableModel.getValueAt(r, COL_SAFE));
        }
        return null;
    }

    private List<String[]> collectEntries() {
        List<String[]> result = new ArrayList<>();
        for (int r = 0; r < tableModel.getRowCount(); r++) {
            String safe = nvl(tableModel.getValueAt(r, COL_SAFE));
            if (!safe.isEmpty())
                result.add(new String[]{safe,
                    nvl(tableModel.getValueAt(r, COL_SECURITIES)),
                    nvl(tableModel.getValueAt(r, COL_CASH)),
                    nvl(tableModel.getValueAt(r, COL_DESCRIPTION))});
        }
        return result;
    }

    private Set<String> existingKeys() {
        Set<String> keys = new HashSet<>();
        for (int r = 0; r < tableModel.getRowCount(); r++)
            keys.add(nvl(tableModel.getValueAt(r, COL_SAFE)));
        return keys;
    }

    // -----------------------------------------------------------------------

    private void bindDeleteKey() {
        table.getInputMap(JComponent.WHEN_FOCUSED)
             .put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteRows");
        table.getActionMap().put("deleteRows", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { onDelete(); }
        });
    }

    // -----------------------------------------------------------------------
    // CSV helpers
    // -----------------------------------------------------------------------

    private static List<String[]> readCsv(File file, String fieldSep) throws IOException {
        List<String[]> result = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (!line.isEmpty() && line.charAt(0) == UTF8_BOM) line = line.substring(1);
                boolean isHeader = first && line.startsWith("SAFE");
                first = false;
                if (!isHeader) {
                    String[] parts = line.split(java.util.regex.Pattern.quote(fieldSep), -1);
                    String safe = csvUnquote(parts, 0);
                    if (!safe.isEmpty())
                        result.add(new String[]{safe, csvUnquote(parts, 1), csvUnquote(parts, 2), csvUnquote(parts, 3)});
                }
            }
        }
        return result;
    }

    private static String csvUnquote(String[] parts, int index) {
        if (index >= parts.length) return "";
        String s = parts[index].trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2)
            return s.substring(1, s.length() - 1).replace("\"\"", "\"");
        return s;
    }

    private static String csvQuote(String value, String fieldSep) {
        if (value.contains(fieldSep) || value.contains("\"") || value.contains("\n"))
            return "\"" + value.replace("\"", "\"\"") + "\"";
        return value;
    }

    private static String field(String[] parts, int index) {
        return index < parts.length ? parts[index].trim() : "";
    }

    private static String nvl(Object o) { return o != null ? o.toString().trim() : ""; }
}