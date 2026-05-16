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
package com.mtanalyze.ui.view;

import com.mtanalyze.export.CsvExport;
import com.mtanalyze.ui.EditMenuContributor;
import com.mtanalyze.ui.FileListTransferHandler;
import com.mtanalyze.ui.FilterSupport;
import com.mtanalyze.ui.ToolbarIcons;
import com.mtanalyze.ui.filter.ColumnFilterRow;
import com.mtanalyze.ui.filter.FinFilterRow;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.KeyEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class PostingPanel extends RoundedPanel implements EditMenuContributor {

    private static final char   BOM         = '\uFEFF';
    private static final int    COL_W       = 120;
    private static final String ERROR_TITLE = "Error";

    private final transient CsvExport.Prefs csvPrefs;
    private final DefaultTableModel  tableModel;
    private final JTable             table;
    private final JPopupMenu         popupMenu;
    private final JMenuItem          copyCellItem;
    private final JMenuItem          addToFilterItem;
    private final JScrollPane        scroll;
    private final ColumnFilterRow    columnFilterRow;
    private final FinFilterRow       finFilterRow;
    private transient TableRowSorter<DefaultTableModel> rowSorter;

    private final String loadDialogTitle;
    private final String exportDialogTitle;
    private final String defaultExportFileName;

    protected PostingPanel(String hintLine,
                           String loadDialogTitle,
                           String exportDialogTitle,
                           String defaultExportFileName,
                           CsvExport.Prefs csvPrefs) {
        super(new BorderLayout(0, 0));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        this.loadDialogTitle       = loadDialogTitle;
        this.exportDialogTitle     = exportDialogTitle;
        this.defaultExportFileName = defaultExportFileName;
        this.csvPrefs              = csvPrefs;

        columnFilterRow = new ColumnFilterRow(this::applyFilters, this::convertDropToQuickFilter);
        finFilterRow    = new FinFilterRow(this::applyFilters, null);

        tableModel = new DefaultTableModel(0, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel) {
            @Override
            protected void configureEnclosingScrollPane() {
                super.configureEnclosingScrollPane();
                if (scroll == null) return;
                JPanel filterRows = new JPanel(new GridLayout(2, 1, 0, 1));
                filterRows.add(columnFilterRow);
                filterRows.add(finFilterRow);
                JPanel colHdr = new JPanel(new BorderLayout());
                colHdr.add(getTableHeader(), BorderLayout.NORTH);
                colHdr.add(filterRows, BorderLayout.SOUTH);
                scroll.setColumnHeaderView(colHdr);
            }
        };
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setRowHeight(22);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JMenuItem copyItem  = new JMenuItem("Copy Table",  ToolbarIcons.menuCopyTable());
        JMenuItem pasteItem = new JMenuItem("Paste Table", ToolbarIcons.menuPaste());
        pasteItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, menuMask));
        copyItem .addActionListener(e -> onCopyTable());
        pasteItem.addActionListener(e -> onPasteFromExcel());

        copyCellItem   = FilterSupport.makeCopyCellItem(table);
        copyCellItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask));
        addToFilterItem  = new JMenuItem("Add to Quick Filter", ToolbarIcons.quickFilter());
        addToFilterItem.addActionListener(e -> addSelectedCellToFilter());
        JMenuItem clearFiltersItem = new JMenuItem("Clear All Filters", ToolbarIcons.menuFilterClear());
        clearFiltersItem.addActionListener(e -> { columnFilterRow.clearAll(); finFilterRow.clearAll(); });

        JMenuItem filterModeItem = new JMenuItem();
        filterModeItem.addActionListener(e -> setFinFilterOrMode(!finFilterRow.isOrMode()));

        popupMenu = new JPopupMenu();
        popupMenu.add(copyCellItem);
        popupMenu.add(copyItem);
        popupMenu.add(pasteItem);
        popupMenu.addSeparator();
        popupMenu.add(addToFilterItem);
        popupMenu.add(clearFiltersItem);
        popupMenu.add(filterModeItem);
        popupMenu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                boolean hasCell = FilterSupport.hasCellSelection(table);
                copyCellItem.setEnabled(hasCell);
                addToFilterItem.setEnabled(hasCell);
                boolean or = finFilterRow.isOrMode();
                filterModeItem.setText(or ? "Quick Filter: OR" : "Quick Filter: AND");
                filterModeItem.setIcon(or ? ToolbarIcons.filterOr() : ToolbarIcons.filterAnd());
            }
            @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) { /* not needed */ }
            @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) { /* not needed */ }
        });

        scroll = new JScrollPane();
        JViewport hintViewport = new JViewport() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (tableModel.getRowCount() > 0) return;
                MessageSourcePanel.paintHintLines(g, this, "Drop a CSV file here", hintLine);
            }
        };
        scroll.setViewport(hintViewport);
        scroll.setViewportView(table);
        tableModel.addTableModelListener(e -> hintViewport.repaint());

        table.setComponentPopupMenu(popupMenu);
        hintViewport.setInheritsPopupMenu(true);

        TransferHandler dropHandler = new FileListTransferHandler() {
            @Override protected boolean handleFiles(java.util.List<File> files) {
                return loadDroppedCsvFiles(files);
            }
        };
        table.setTransferHandler(dropHandler);
        scroll.setTransferHandler(dropHandler);

        add(scroll, BorderLayout.CENTER);
    }

    private void addSelectedCellToFilter() {
        int viewRow = table.getSelectedRow();
        int viewCol = table.getSelectedColumn();
        if (viewRow < 0 || viewCol < 0) return;
        int modelCol = table.convertColumnIndexToModel(viewCol);
        Object val = table.getValueAt(viewRow, viewCol);
        finFilterRow.appendToFilter(modelCol, "=" + (val != null ? val.toString().trim() : ""));
    }

    private boolean loadDroppedCsvFiles(List<File> files) {
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
                try { loadFile(f); return true; }
                catch (IOException ex) {
                    JOptionPane.showMessageDialog(this,
                            "Load failed:\n" + ex.getMessage(), ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
                }
            }
        }
        return false;
    }

    protected void installSafeFilter(Consumer<String> onAddSafe, Predicate<String> hasMapping) {
        JMenuItem safeItem = new JMenuItem("Add SAFE to Quickfilter");
        safeItem.addActionListener(e -> {
            String cellValue = getSelectedCellValue();
            if (cellValue != null && !cellValue.isEmpty())
                onAddSafe.accept(cellValue);
        });
        getPopupMenu().addSeparator();
        getPopupMenu().add(safeItem);
        getPopupMenu().addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                String cv = getSelectedCellValue();
                safeItem.setEnabled(cv != null && !cv.isEmpty() && hasMapping.test(cv));
            }
            @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) { /* not needed */ }
            @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) { /* not needed */ }
        });
    }

    public boolean isFinFilterOrMode() { return finFilterRow.isOrMode(); }

    public void setFinFilterOrMode(boolean or) {
        finFilterRow.setOrMode(or);
        applyFilters();
    }

    public JPopupMenu getPopupMenu() {
        copyCellItem.setEnabled(FilterSupport.hasCellSelection(table));
        return popupMenu;
    }

    protected String getSelectedCellValue() {
        int row = table.getSelectedRow();
        int col = table.getSelectedColumn();
        if (row < 0 || col < 0) return null;
        Object val = table.getValueAt(row, col);
        return val != null ? val.toString().trim() : null;
    }

    // -----------------------------------------------------------------------
    // Load
    // -----------------------------------------------------------------------

    private void onLoad() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(loadDialogTitle);
        fc.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            loadFile(fc.getSelectedFile());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Load failed:\n" + ex.getMessage(),
                    ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadFile(File file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first && !line.isEmpty() && line.charAt(0) == BOM) line = line.substring(1);
                first = false;
                rows.add(parseLine(line));
            }
        }
        if (rows.isEmpty()) return;
        String[] headers = rows.getFirst();
        tableModel.setColumnIdentifiers(headers);
        tableModel.setRowCount(0);
        for (int i = 1; i < rows.size(); i++) {
            String[] src = rows.get(i);
            Object[] row = new Object[headers.length];
            for (int j = 0; j < headers.length; j++)
                row[j] = j < src.length ? src[j] : "";
            tableModel.addRow(row);
        }
        applyColumnWidths();
        rebuildFilters();
    }

    private String[] parseLine(String line) {
        char sep = csvPrefs.fieldSep().charAt(0);
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else if (c == '"') {
                    inQuotes = false;
                } else {
                    sb.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == sep) {
                fields.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
            i++;
        }
        fields.add(sb.toString());
        return fields.toArray(new String[0]);
    }

    // -----------------------------------------------------------------------
    // Copy / Paste (Excel interop)
    // -----------------------------------------------------------------------

    private void onCopyTable() { FilterSupport.copyTableToClipboard(table); }

    public void showLoadDialog()     { onLoad(); }
    public void showExportDialog()   { onExport(); }
    public void pasteFromClipboard() { onPasteFromExcel(); }

    private void onExport() {
        if (tableModel.getRowCount() == 0) return;
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(exportDialogTitle);
        fc.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        fc.setSelectedFile(new File(defaultExportFileName));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".csv"))
            file = new File(file.getAbsolutePath() + ".csv");
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            pw.write(BOM);
            writeTableAsCsv(pw);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Export failed:\n" + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void writeTableAsCsv(PrintWriter pw) {
        String sep = csvPrefs.fieldSep();
        int cols = table.getColumnCount();
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < cols; c++) {
            if (c > 0) sb.append(sep);
            sb.append(table.getColumnName(c));
        }
        pw.println(sb);
        for (int r = 0; r < table.getRowCount(); r++) {
            sb.setLength(0);
            for (int c = 0; c < cols; c++) {
                if (c > 0) sb.append(sep);
                Object val = table.getValueAt(r, c);
                sb.append(val != null ? val.toString() : "");
            }
            pw.println(sb);
        }
    }

    private void onPasteFromExcel() {
        try {
            java.awt.datatransfer.Transferable t =
                Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (t == null || !t.isDataFlavorSupported(DataFlavor.stringFlavor)) return;
            loadFromTsv((String) t.getTransferData(DataFlavor.stringFlavor));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Paste failed:\n" + ex.getMessage(),
                    ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadFromTsv(String text) {
        String[] lines = text.split("\r?\n", -1);
        int lineCount = lines.length;
        while (lineCount > 0 && lines[lineCount - 1].isEmpty()) lineCount--;
        if (lineCount == 0) return;
        String[] headers = lines[0].split("\t", -1);
        tableModel.setColumnIdentifiers(headers);
        tableModel.setRowCount(0);
        for (int i = 1; i < lineCount; i++) {
            String[] src = lines[i].split("\t", -1);
            Object[] row = new Object[headers.length];
            for (int j = 0; j < headers.length; j++)
                row[j] = j < src.length ? src[j] : "";
            tableModel.addRow(row);
        }
        applyColumnWidths();
        rebuildFilters();
    }

    // -----------------------------------------------------------------------
    // Filters
    // -----------------------------------------------------------------------

    private void rebuildFilters() {
        rowSorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(rowSorter);
        columnFilterRow.rebuild(table.getColumnModel(), tableModel);
        finFilterRow.rebuild(table.getColumnModel(), tableModel.getColumnCount());
    }

    private void applyFilters() {
        if (rowSorter == null) return;
        FilterSupport.applyRowFilter(rowSorter,
                columnFilterRow.getActiveFilters(),
                finFilterRow.getActiveFilters(),
                finFilterRow.isOrMode());
    }

    private void convertDropToQuickFilter() {
        FilterSupport.convertDropToQuickFilter(columnFilterRow, finFilterRow);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void applyColumnWidths() {
        for (int i = 0; i < tableModel.getColumnCount(); i++)
            table.getColumnModel().getColumn(i).setPreferredWidth(COL_W);
    }
}