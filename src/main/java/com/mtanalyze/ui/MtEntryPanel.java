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
import com.mtanalyze.model.SwiftMessage;
import com.prowidesoftware.swift.model.SwiftTagListBlock;

import com.mtanalyze.parser.HintDictionary;
import com.mtanalyze.ui.view.PanelDecor;
import com.mtanalyze.ui.view.RoundedPanel;
import com.mtanalyze.ui.filter.ColumnFilterRow;
import com.mtanalyze.ui.filter.FinFilterRow;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.*;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.net.URI;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Panel that shows the parsed MT entries in a filterable, sortable table.
 * All interactions with the rest of the application (detail panel,
 * file loading) are routed through the {@link Host} callback interface.
 */
public class MtEntryPanel extends JPanel {

    // -----------------------------------------------------------------------
    // Host interface – callbacks to the surrounding application
    // -----------------------------------------------------------------------
    public interface Host {
        void onRowSelected(int modelRow);
        void onMultipleRowsSelected(List<Entry> entries);
        void onRowDeselected();
        void onFilesDropped(List<File> files);
        boolean isPowerUser();
        void focusDetailTag(ColumnDef cd);
        void switchDetailCard(String card);
        void exportMessageForRow(int modelRow);
        void showAppendTextDialog();
        void setStatus(String message);
    }

    // -----------------------------------------------------------------------
    // Preference-key bundle
    // -----------------------------------------------------------------------
    public record PrefKeys(String colOrder, String colVis) {}


    private static final String TOOLTIP_WRAP_MULTI  = "Entries: multi-line – Click to switch to single-line";
    private static final String TOOLTIP_WRAP_SINGLE = "Entries: single-line – Click to switch to multi-line";


    private static final List<String> REF_SEARCH_QUALIFIERS = List.of("SEME", "RELA", "TRCI", "PREV");

    private static final String DETAIL_CARD_EDITOR = "editor";

    // -----------------------------------------------------------------------
    // Dependencies
    // -----------------------------------------------------------------------
    private final transient Host               host;
    private final transient Preferences        prefs;
    private final transient PrefKeys           prefKeys;
    private final transient HintDictionary dict;

    // -----------------------------------------------------------------------
    // Table components
    // -----------------------------------------------------------------------
    private EntryTableModel                  entryTableModel;
    private JTable                           mtEntryTable;
    private transient TableRowSorter<EntryTableModel> rowSorter;
    private JScrollPane                      mtEntryScrollPane;
    private ColumnFilterRow                  columnFilterRow;
    private FinFilterRow                     finFilterRow;
    @SuppressWarnings("java:S1450") // must be a field so the lambda keeps a live reference and the timer survives GC
    private transient Timer                  repackDebounceTimer;

    // Search bar
    JTextField finSearchField;
    JLabel     finMatchLabel;
    JButton    finPrevBtn;
    JButton    finNextBtn;
    JButton    finClearBtn;
    private String     finSearchText = "";

    // Wrap button – lives in this panel's own toolbar row
    private JButton  wrapBtn;
    private boolean singleLineMode  = false;

    // -----------------------------------------------------------------------
    // Data model
    // -----------------------------------------------------------------------
    private final transient EntryPanelModel model = new EntryPanelModel();

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------
    public MtEntryPanel(Host host, Preferences prefs, PrefKeys prefKeys, HintDictionary dict) {
        super(new BorderLayout());
        this.host     = host;
        this.prefs    = prefs;
        this.prefKeys = prefKeys;
        this.dict     = dict;
    }

    // -----------------------------------------------------------------------
    // Initialisation
    // -----------------------------------------------------------------------

    /** Must be called once before {@link #buildContentPanel()}. */
    public void init() {
        buildFilterButtons();
        setupPositionTable();
    }

    /** Returns the scrollable entries panel to be embedded in the main layout. */
    public JPanel buildContentPanel() {
        finSearchField = new JTextField(16);
        finSearchField.setToolTipText("Search all columns (min. 3 characters)");
        finMatchLabel = FilterSupport.makeMatchLabel();
        finClearBtn   = FilterSupport.makeClearButton(finSearchField);

        finPrevBtn = new JButton("▲");
        finPrevBtn.setMargin(new Insets(1, 4, 1, 4));
        finPrevBtn.setFont(finPrevBtn.getFont().deriveFont(11f));
        finPrevBtn.setToolTipText("Previous match");
        finPrevBtn.setEnabled(false);
        finPrevBtn.addActionListener(e -> navigateFinMatch(-1));

        finNextBtn = new JButton("▼");
        finNextBtn.setMargin(new Insets(1, 4, 1, 4));
        finNextBtn.setFont(finNextBtn.getFont().deriveFont(11f));
        finNextBtn.setToolTipText("Next match");
        finNextBtn.setEnabled(false);
        finNextBtn.addActionListener(e -> navigateFinMatch(+1));

        finSearchField.addActionListener(e -> navigateFinMatch(+1));
        addSearchListener(finSearchField, this::applyFinSearch);

        RoundedPanel panel = new RoundedPanel(new BorderLayout(4, 4));
        panel.setBorder(new EmptyBorder(6, 6, 6, 6));
        panel.add(mtEntryScrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void buildFilterButtons() {
        wrapBtn = new JButton(ToolbarIcons.wrapMulti());
        wrapBtn.setFocusable(false);
        wrapBtn.setToolTipText(TOOLTIP_WRAP_MULTI);
        wrapBtn.addActionListener(e -> {
            singleLineMode = !singleLineMode;
            wrapBtn.setIcon(singleLineMode ? ToolbarIcons.wrapSingle() : ToolbarIcons.wrapMulti());
            wrapBtn.setToolTipText(singleLineMode ? TOOLTIP_WRAP_SINGLE : TOOLTIP_WRAP_MULTI);
            rebuildPositionTable();
            repackPositionRows();
        });
    }

    // -----------------------------------------------------------------------
    // Table setup
    // -----------------------------------------------------------------------

    private void setupPositionTable() {
        entryTableModel = new EntryTableModel(model.allEntries());
        columnFilterRow = new ColumnFilterRow(this::applyFinFilters, this::convertDropToQuickFilter);
        finFilterRow    = new FinFilterRow(this::applyFinFilters);

        mtEntryTable = createMtEntryTable();
        mtEntryTable.setRowHeight(22);
        mtEntryTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        mtEntryTable.setFillsViewportHeight(true);
        mtEntryTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        mtEntryTable.getTableHeader().setReorderingAllowed(true);

        setupPositionTableMouseListeners();
        bindDeleteKeyToPositionTable();

        mtEntryScrollPane = new JScrollPane();
        mtEntryScrollPane.setViewport(createHintViewport());
        mtEntryScrollPane.setViewportView(mtEntryTable);

        rowSorter = new TableRowSorter<>(entryTableModel);
        mtEntryTable.setRowSorter(rowSorter);
        rowSorter.addRowSorterListener(e -> repackPositionRows());
        repackDebounceTimer = new Timer(120, e -> repackPositionRows());
        repackDebounceTimer.setRepeats(false);
        mtEntryTable.getColumnModel().addColumnModelListener(columnMarginListener(() -> repackDebounceTimer.restart()));

        TransferHandler dropHandler = buildEntriesDropHandler();
        mtEntryTable.setTransferHandler(dropHandler);
        mtEntryScrollPane.setTransferHandler(dropHandler);

        mtEntryTable.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                columnFilterRow.refreshLayout();
                finFilterRow.refreshLayout();
            }
        });

        setupEntryTableSelectionListener();
    }

    private JTable createMtEntryTable() {
        return new JTable(entryTableModel) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override
            protected JTableHeader createDefaultTableHeader() {
                return new JTableHeader(columnModel) {
                    @Override
                    public String getToolTipText(java.awt.event.MouseEvent e) {
                        int col = columnAtPoint(e.getPoint());
                        if (col < 0) return null;
                        int modelCol = mtEntryTable.convertColumnIndexToModel(col);
                        ColumnDef cd = visibleColumnDefAt(modelCol);
                        return cd != null ? buildColumnHeaderTooltip(cd) : null;
                    }
                };
            }
            @Override
            protected void configureEnclosingScrollPane() {
                super.configureEnclosingScrollPane();
                FilterSupport.installFilterRowsInScrollPane(mtEntryScrollPane, getTableHeader(), columnFilterRow, finFilterRow);
            }
        };
    }

    private JViewport createHintViewport() {
        return new JViewport() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (!model.allEntries().isEmpty()) return;
                PanelDecor.paintHintLines(g, this,
                    "Drop a SWIFT MT file here", "to load it into the Entries view");
            }
        };
    }

    private void setupEntryTableSelectionListener() {
        mtEntryTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int[] viewRows = mtEntryTable.getSelectedRows();
            if (viewRows.length == 0) { host.onRowDeselected(); return; }
            if (viewRows.length > 1) {
                notifyMultipleSelected(viewRows);
                return;
            }
            host.onRowSelected(mtEntryTable.convertRowIndexToModel(viewRows[0]));
        });
    }

    private void notifyMultipleSelected(int[] viewRows) {
        List<Entry> entries = new ArrayList<>();
        for (int vr : viewRows) {
            com.mtanalyze.model.Entry entry = model.getEntryForRow(mtEntryTable.convertRowIndexToModel(vr));
            if (entry != null) entries.add(entry);
        }
        host.onMultipleRowsSelected(entries);
    }

    private TransferHandler buildEntriesDropHandler() {
        return new FileListTransferHandler() {
            @Override
            protected boolean handleFiles(List<File> files) {
                List<File> fileFiles = new ArrayList<>();
                for (File f : files) if (f.isFile()) fileFiles.add(f);
                if (fileFiles.isEmpty()) return false;
                host.onFilesDropped(fileFiles);
                return true;
            }
        };
    }

    private void bindDeleteKeyToPositionTable() {
        String actionKey = "deleteSelectedRow";
        mtEntryTable.getInputMap(JComponent.WHEN_FOCUSED)
                     .put(KeyStroke.getKeyStroke("DELETE"), actionKey);
        mtEntryTable.getActionMap().put(actionKey, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (!host.isPowerUser()) return;
                int viewRow = mtEntryTable.getSelectedRow();
                if (viewRow < 0) return;
                deleteFinRow(mtEntryTable.convertRowIndexToModel(viewRow));
            }
        });
    }

    // -----------------------------------------------------------------------
    // Mouse listeners – header and row context menus
    // -----------------------------------------------------------------------

    private void setupPositionTableMouseListeners() {
        mtEntryTable.getTableHeader().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed (java.awt.event.MouseEvent e) { maybeShowHeaderPopup(e); }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) { syncColumnOrder(); maybeShowHeaderPopup(e); }
        });
        mtEntryTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed (java.awt.event.MouseEvent e) { maybeShowRowPopup(e); }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) { maybeShowRowPopup(e); }
            @Override public void mouseClicked (java.awt.event.MouseEvent e) { onRowDoubleClick(e); }
        });
    }

    private void maybeShowHeaderPopup(java.awt.event.MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        int viewCol = mtEntryTable.getTableHeader().columnAtPoint(e.getPoint());
        if (viewCol < 0) return;
        int modelCol = mtEntryTable.convertColumnIndexToModel(viewCol);
        ColumnDef cd = visibleColumnDefAt(viewCol);
        if (cd == null) return;
        showHeaderContextMenu(e, viewCol, modelCol, cd);
    }

    private void maybeShowRowPopup(java.awt.event.MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        int viewRow = mtEntryTable.rowAtPoint(e.getPoint());
        int viewCol = mtEntryTable.columnAtPoint(e.getPoint());
        if (viewRow < 0) return;
        mtEntryTable.setRowSelectionInterval(viewRow, viewRow);
        int modelRow = mtEntryTable.convertRowIndexToModel(viewRow);
        buildRowContextMenu(modelRow, viewRow, viewCol).show(e.getComponent(), e.getX(), e.getY());
    }

    private void onRowDoubleClick(java.awt.event.MouseEvent e) {
        if (e.getClickCount() != 2 || e.isPopupTrigger()) return;
        int viewCol = mtEntryTable.columnAtPoint(e.getPoint());
        if (viewCol < 0) return;
        ColumnDef cd = visibleColumnDefAt(viewCol);
        if (cd != null) host.focusDetailTag(cd);
    }

    private void showHeaderContextMenu(java.awt.event.MouseEvent e, int viewCol, int modelCol, ColumnDef cd) {
        JPopupMenu popup = new JPopupMenu();
        FilterSupport.addSortMenuItems(popup, rowSorter, modelCol);
        popup.addSeparator();
        JMenuItem moveFirst = new JMenuItem("Move to Start",  ToolbarIcons.menuMoveFirst());
        moveFirst.addActionListener(ae -> {
            mtEntryTable.getColumnModel().moveColumn(viewCol, 0);
            syncColumnOrder();
        });
        popup.add(moveFirst);
        popup.addSeparator();
        JMenuItem hide = new JMenuItem("Hide Column",         ToolbarIcons.menuHideColumn());
        hide.addActionListener(ae -> { cd.setVisible(false); rebuildPositionTable(); });
        popup.add(hide);
        JMenuItem hideEmpty = new JMenuItem("Hide Empty Columns", ToolbarIcons.menuHideColumn());
        hideEmpty.addActionListener(ae -> hideEmptyColumns());
        popup.add(hideEmpty);
        JMenuItem colChooserItem = new JMenuItem("Show/Hide Columns…", ToolbarIcons.menuShowColumns());
        colChooserItem.addActionListener(ae -> ColumnChooser.show(
            SwingUtilities.getWindowAncestor(this), model.columnDefs(),
            this::saveColumnPrefs, this::rebuildPositionTable, dict));
        popup.add(colChooserItem);
        popup.show(e.getComponent(), e.getX(), e.getY());
    }

    /** Builds the right-click context menu for an entry row. */
    public JPopupMenu buildRowContextMenu(int modelRow, int viewRow, int viewCol) {
        JPopupMenu popup = new JPopupMenu();

        // ── Clipboard ─────────────────────────────────────────────────────
        popup.add(makeCopyCellItem(viewRow, viewCol));
        popup.add(makeCopyTableItem());
        JMenuItem appendItem = new JMenuItem("Paste", ToolbarIcons.menuPaste());
        appendItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        appendItem.addActionListener(ae -> host.showAppendTextDialog());
        popup.add(appendItem);

        // ── Navigate / View ───────────────────────────────────────────────
        popup.addSeparator();
        ColumnDef hoveredCd = visibleColumnDefAt(viewCol);
        if (hoveredCd != null) {
            JMenuItem showInDetail = new JMenuItem("Goto Tag", ToolbarIcons.menuSelectDetail());
            showInDetail.addActionListener(ae -> host.focusDetailTag(hoveredCd));
            popup.add(showInDetail);
        }
        addShowInEditorMenuItem(popup, modelRow);
        Entry rowEntry = model.getEntryForRow(modelRow);
        if (rowEntry != null) {
            String mtVal = rowEntry.data().get(EntryPanelModel.MT_COL_KEY);
            if (mtVal != null && mtVal.length() > 2) popup.add(makeIsoDocItem(mtVal));
            addGenerateConfirmationItem(popup, modelRow, rowEntry);
        }

        // ── Display ───────────────────────────────────────────────────────
        popup.addSeparator();
        popup.add(makeMultilineRowItem());

        // ── Search & Filter ───────────────────────────────────────────────
        popup.addSeparator();
        addReferenceSearchMenuItem(popup, viewRow, viewCol);
        addAppendToFilterMenuItem(popup, viewRow, viewCol);
        JMenuItem clearFiltersItem = new JMenuItem("Clear All Filters", ToolbarIcons.menuFilterClear());
        clearFiltersItem.addActionListener(ae -> clearAllFilters());
        popup.add(clearFiltersItem);

        // ── Export ───────────────────────────────────────────────────────
        if (host.isPowerUser()) {
            popup.addSeparator();
            popup.add(makeExportMessageMenuItem(modelRow));
        }

        // ── Destructive ───────────────────────────────────────────────────
        if (host.isPowerUser()) {
            popup.addSeparator();
            JMenuItem deleteItem = new JMenuItem("Delete Row", ToolbarIcons.menuDelete());
            deleteItem.addActionListener(ae -> deleteFinRow(modelRow));
            popup.add(deleteItem);
        }
        return popup;
    }

    private JCheckBoxMenuItem makeMultilineRowItem() {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem("Multi-line Rows");
        item.setSelected(!singleLineMode);
        item.addActionListener(ae -> {
            singleLineMode = !item.isSelected();
            wrapBtn.setIcon(singleLineMode ? ToolbarIcons.wrapSingle() : ToolbarIcons.wrapMulti());
            wrapBtn.setToolTipText(singleLineMode ? TOOLTIP_WRAP_SINGLE : TOOLTIP_WRAP_MULTI);
            rebuildPositionTable();
            repackPositionRows();
        });
        return item;
    }

    private void addShowInEditorMenuItem(JPopupMenu popup, int modelRow) {
        String path = getRowValue(modelRow, EntryPanelModel.FILE_COL_KEY);
        if (path.isEmpty()) return;
        JMenuItem item = new JMenuItem("View Source", ToolbarIcons.menuViewSource());
        item.addActionListener(ae -> host.switchDetailCard(DETAIL_CARD_EDITOR));
        popup.add(item);
    }

    private JMenuItem makeIsoDocItem(String mtVal) {
        String mtNum = mtVal.substring(2);
        JMenuItem item = new JMenuItem("Lookup ISO 15022 Doku (" + mtVal + ")", ToolbarIcons.menuIsoDoc());
        item.addActionListener(ae -> {
            try {
                Desktop.getDesktop().browse(new URI(
                    "https://www.iso20022.org/15022/uhb/finmt" + mtNum.toLowerCase() + ".htm"));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(MtEntryPanel.this,
                    "Browser could not be opened:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        return item;
    }

    /**
     * Adds the "Generate MT 54x Settlement Confirmation" item for MT 536 rows.
     * The concrete target type (544-547) is derived from the transaction's
     * {@code :22H::REDE//} and {@code :22H::PAYM//} settlement indicators; when no
     * transaction details are present the item is shown disabled so the feature
     * stays discoverable.
     */
    private void addGenerateConfirmationItem(JPopupMenu popup, int modelRow, Entry rowEntry) {
        if (!"MT536".equals(rowEntry.data().get(EntryPanelModel.MT_COL_KEY))) return;
        String confirmType = com.mtanalyze.export.Mt54xGenerator.detectConfirmationType(rowEntry);
        if (confirmType == null) {
            JMenuItem disabled = new JMenuItem("Generate MT 54x (no transaction details found)",
                    ToolbarIcons.menuViewSource());
            disabled.setEnabled(false);
            popup.add(disabled);
            return;
        }
        JMenuItem item = new JMenuItem(
                "Generate MT " + confirmType + " (Settlement Confirmation)",
                ToolbarIcons.menuViewSource());
        item.addActionListener(ae -> generateConfirmation(modelRow, confirmType));
        popup.add(item);
    }

    private void generateConfirmation(int modelRow, String confirmType) {
        Entry entry      = model.getEntryForRow(modelRow);
        SwiftMessage msg = model.getMessageForRow(modelRow);
        if (entry == null || msg == null) {
            host.setStatus("No SWIFT message for selected entry.");
            return;
        }
        String text = new com.mtanalyze.export.Mt54xGenerator().generate(msg, entry);
        showGeneratedSource("Generated MT " + confirmType + " – Settlement Confirmation", text);
        host.setStatus("Generated MT " + confirmType + " from MT 536 entry.");
    }

    /** Opens a non-modal source window showing the given SWIFT message text. */
    private void showGeneratedSource(String title, String text) {
        com.mtanalyze.ui.view.SourcePanel sourcePanel = new com.mtanalyze.ui.view.SourcePanel();
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), title,
                Dialog.ModalityType.MODELESS);
        dialog.getContentPane().add(sourcePanel);
        dialog.setSize(660, 580);
        dialog.setLocationRelativeTo(this);
        sourcePanel.showMessage(text);
        dialog.setVisible(true);
    }

    private JMenuItem makeExportMessageMenuItem(int modelRow) {
        JMenuItem item = new JMenuItem("Export Message…", ToolbarIcons.menuExport());
        item.addActionListener(ae -> host.exportMessageForRow(modelRow));
        return item;
    }

    // -----------------------------------------------------------------------
    // Context-menu helpers – filter & search
    // -----------------------------------------------------------------------

    private void addReferenceSearchMenuItem(JPopupMenu popup, int viewRow, int viewCol) {
        if (!host.isPowerUser()) return;
        if (viewRow < 0 || viewRow >= mtEntryTable.getRowCount()) return;
        Object cellVal = mtEntryTable.getValueAt(viewRow, viewCol);
        String value = cellVal != null ? cellVal.toString().trim() : "";
        if (value.isEmpty()) return;
        String label = value.length() > 30 ? value.substring(0, 27) + "..." : value;
        JMenuItem item = new JMenuItem("Reference Search: " + label, ToolbarIcons.menuSearch());
        item.addActionListener(ae -> applyReferenceSearch(value));
        popup.add(item);
    }

    private void addAppendToFilterMenuItem(JPopupMenu popup, int viewRow, int viewCol) {
        if (viewRow < 0 || viewCol < 0 || mtEntryTable.getColumnCount() == 0) return;
        Object cellVal = mtEntryTable.getValueAt(viewRow, viewCol);
        String raw = cellVal != null ? cellVal.toString().trim() : "";
        int nl = raw.indexOf('\n');
        String value = nl >= 0 ? raw.substring(0, nl).trim() : raw;
        if (value.isEmpty()) return;
        int modelCol = mtEntryTable.convertColumnIndexToModel(viewCol);
        String label = value.length() > 25 ? value.substring(0, 22) + "…" : value;
        JMenuItem item = new JMenuItem("Add to Quick Filter: " + label, ToolbarIcons.menuFilterAdd());
        item.addActionListener(ae -> {
            if (finFilterRow != null) finFilterRow.appendToFilter(modelCol, value);
        });
        popup.add(item);
    }

    // -----------------------------------------------------------------------
    // Clipboard helpers
    // -----------------------------------------------------------------------

    private JMenuItem makeCopyCellItem(int viewRow, int viewCol) {
        JMenuItem item = new JMenuItem("Copy", ToolbarIcons.menuCopy());
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        item.addActionListener(ae -> {
            Object value = mtEntryTable.getValueAt(viewRow, viewCol);
            String text  = value != null ? value.toString() : "";
            Toolkit.getDefaultToolkit().getSystemClipboard()
                   .setContents(new StringSelection(text), null);
            host.setStatus("Cell value copied to clipboard.");
        });
        return item;
    }

    private JMenuItem makeCopyTableItem() {
        JMenuItem item = new JMenuItem("Copy Table", ToolbarIcons.menuCopyTable());
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T,
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        item.addActionListener(ae -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                   .setContents(new StringSelection(tableToTsv()), null);
            host.setStatus("Table copied to clipboard.");
        });
        return item;
    }

    private String tableToTsv() {
        return buildTableTsv(mtEntryTable);
    }

    static String buildTableTsv(JTable table) {
        return FilterSupport.buildTableTsv(table);
    }

    // -----------------------------------------------------------------------
    // Filter & search logic
    // -----------------------------------------------------------------------

    private void applyFinSearch(String query) {
        finSearchText = query.length() >= 3 ? query.toLowerCase(Locale.ROOT) : "";
        applyFinFilters();
    }

    private void navigateFinMatch(int direction) {
        int rowCount = mtEntryTable.getRowCount();
        if (rowCount == 0) return;
        int current = mtEntryTable.getSelectedRow();
        int next;
        if (current < 0) {
            next = direction > 0 ? 0 : rowCount - 1;
        } else {
            next = (current + direction + rowCount) % rowCount;
        }
        mtEntryTable.setRowSelectionInterval(next, next);
        mtEntryTable.scrollRectToVisible(mtEntryTable.getCellRect(next, 0, true));
        mtEntryTable.requestFocusInWindow();
    }

    private void applyFinFilters() {
        if (rowSorter == null) return;
        final String lq = finSearchText;
        final Map<Integer, Set<String>> dropFilters =
                columnFilterRow != null ? columnFilterRow.getActiveFilters() : Collections.emptyMap();
        final Map<Integer, String> quickFilters =
                finFilterRow != null ? finFilterRow.getActiveFilters() : Collections.emptyMap();

        boolean anyFilter = !dropFilters.isEmpty() || !quickFilters.isEmpty();

        if (lq.isEmpty() && !anyFilter) {
            rowSorter.setRowFilter(null);
            if (finMatchLabel != null) finMatchLabel.setText("  ");
            setFinNavEnabled(false);
        } else {
            applyActiveFinFilter(lq, dropFilters, quickFilters);
        }
        mtEntryTable.repaint();
    }

    private void applyActiveFinFilter(String lq,
            Map<Integer, Set<String>> dropFilters, Map<Integer, String> quickFilters) {
        final boolean orMode = finFilterRow != null && finFilterRow.isOrMode();
        rowSorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends EntryTableModel, ? extends Integer> e) {
                return FilterSupport.passesFilters(e, dropFilters, quickFilters, orMode)
                    && passesTextSearch(e, lq);
            }
        });
        int n = rowSorter.getViewRowCount();
        setFinNavEnabled(n > 0 && !lq.isEmpty());
        if (finMatchLabel != null) {
            finMatchLabel.setText(n == 0
                ? "<html><font color='red'>No matches</font></html>"
                : n + " matches");
        }
    }

    private void setFinNavEnabled(boolean enabled) {
        if (finPrevBtn != null) { finPrevBtn.setEnabled(enabled); finNextBtn.setEnabled(enabled); }
    }

    @SuppressWarnings("java:S4968")
    private static boolean passesTextSearch(
            RowFilter.Entry<? extends EntryTableModel, ? extends Integer> e, String lq) {
        if (lq.isEmpty()) return true;
        for (int i = 0; i < e.getValueCount(); i++) {
            Object v = e.getValue(i);
            if (v != null && v.toString().toLowerCase(Locale.ROOT).contains(lq)) return true;
        }
        return false;
    }

    private void convertDropToQuickFilter() {
        if (columnFilterRow == null || finFilterRow == null) return;
        FilterSupport.convertDropToQuickFilter(columnFilterRow, finFilterRow);
    }

    /** Used internally by {@link #applyReferenceSearch} — Reference Search needs OR
     *  semantics across its qualifier columns since a row only ever carries the searched
     *  value in one of them. There is no user-facing AND/OR toggle any more. */
    private void setOrMode(boolean orMode) {
        if (finFilterRow != null) finFilterRow.setOrMode(orMode);
    }

    public void applyReferenceSearch(String value) {
        setOrMode(true);
        if (finFilterRow != null) finFilterRow.setFilterByQualifiers(REF_SEARCH_QUALIFIERS, value);
    }

    public void clearAllFilters() {
        if (columnFilterRow  != null) columnFilterRow.clearAll();
        if (finFilterRow     != null) finFilterRow.clearAll();
        setOrMode(false);
    }

    public void appendToEntryFilterByQualifier(String qualifier, String value) {
        if (finFilterRow != null) finFilterRow.appendToFilterByQualifier(qualifier, value);
    }

    // -----------------------------------------------------------------------
    // Column order / prefs
    // -----------------------------------------------------------------------

    private void syncColumnOrder() {
        int n = mtEntryTable.getColumnModel().getColumnCount();
        if (n == 0) return;
        List<ColumnDef> active = model.columnDefs();
        List<ColumnDef> reordered = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String lbl = String.valueOf(mtEntryTable.getColumnModel().getColumn(i).getHeaderValue());
            for (ColumnDef cd : active)
                if (cd.isVisible() && cd.label.equals(lbl)) { reordered.add(cd); break; }
        }
        for (ColumnDef cd : active) if (!cd.isVisible()) reordered.add(cd);
        active.clear(); active.addAll(reordered);
        saveColumnPrefs();
    }

    public ColumnDef visibleColumnDefAt(int viewIndex) {
        int count = 0;
        for (ColumnDef cd : model.columnDefs()) {
            if (cd.isVisible()) {
                if (count == viewIndex) return cd;
                count++;
            }
        }
        return null;
    }

    private void saveColumnPrefs() {
        StringBuilder order = new StringBuilder();
        StringBuilder vis   = new StringBuilder();
        for (ColumnDef cd : model.columnDefs()) {
            if (!order.isEmpty()) { order.append('\n'); vis.append('\n'); }
            order.append(cd.key.replace('\t', '|'));
            vis.append(cd.isVisible() ? '1' : '0');
        }
        putChunked(prefKeys.colOrder(), order.toString());
        putChunked(prefKeys.colVis(),   vis.toString());
    }

    /** Splits {@code value} across {@code baseKey}, {@code baseKey.1}, ... to stay under
     *  {@link Preferences#MAX_VALUE_LENGTH}, which {@link Preferences#put} would otherwise reject. */
    private void putChunked(String baseKey, String value) {
        int len = value.length();
        int chunkCount = 0;
        for (int start = 0; start < len || chunkCount == 0; start += Preferences.MAX_VALUE_LENGTH, chunkCount++) {
            String key = chunkCount == 0 ? baseKey : baseKey + "." + chunkCount;
            prefs.put(key, value.substring(start, Math.min(start + Preferences.MAX_VALUE_LENGTH, len)));
        }
        for (int i = chunkCount; prefs.get(baseKey + "." + i, null) != null; i++) {
            prefs.remove(baseKey + "." + i);
        }
    }

    private String getChunked(String baseKey) {
        StringBuilder sb = new StringBuilder(prefs.get(baseKey, ""));
        for (int i = 1; ; i++) {
            String chunk = prefs.get(baseKey + "." + i, null);
            if (chunk == null) break;
            sb.append(chunk);
        }
        return sb.toString();
    }

    public void applyColumnPrefs() {
        String orderPref = getChunked(prefKeys.colOrder());
        String visPref   = getChunked(prefKeys.colVis());
        if (orderPref.isEmpty()) return;
        String[] savedKeys = orderPref.split("\n", -1);
        String[] savedVis  = visPref.split("\n", -1);
        List<ColumnDef> cols = new ArrayList<>(model.columnDefs());
        Map<String, ColumnDef> byKey = new LinkedHashMap<>();
        for (ColumnDef cd : cols) byKey.put(cd.key, cd);
        List<ColumnDef> ordered = new ArrayList<>(cols.size());
        for (int i = 0; i < savedKeys.length; i++) {
            ColumnDef cd = byKey.remove(savedKeys[i].replace('|', '\t'));
            if (cd == null) continue;
            if (i < savedVis.length) cd.setVisible("1".equals(savedVis[i]));
            ordered.add(cd);
        }
        ordered.addAll(byKey.values());
        cols.clear(); cols.addAll(ordered);
    }

    /** Hides every currently visible column that has no non-blank value in the currently filtered rows. */
    private void hideEmptyColumns() {
        int rowCount = mtEntryTable.getRowCount();
        if (rowCount == 0) return;
        List<ColumnDef> visibleDefs = new ArrayList<>();
        for (ColumnDef cd : model.columnDefs()) if (cd.isVisible()) visibleDefs.add(cd);
        boolean changed = false;
        for (int modelCol = 0; modelCol < visibleDefs.size(); modelCol++) {
            boolean hasValue = false;
            for (int viewRow = 0; viewRow < rowCount; viewRow++) {
                int modelRow = mtEntryTable.convertRowIndexToModel(viewRow);
                Object val = entryTableModel.getValueAt(modelRow, modelCol);
                if (val != null && !val.toString().isBlank()) { hasValue = true; break; }
            }
            if (!hasValue) { visibleDefs.get(modelCol).setVisible(false); changed = true; }
        }
        if (!changed) return;
        rebuildPositionTable();
        saveColumnPrefs();
    }

    public void rebuildPositionTable() {
        List<ColumnDef> activeDefs = model.columnDefs();
        List<Map<String, String>> activeRows = model.getRowData();
        List<ColumnDef> visible = new ArrayList<>();
        for (ColumnDef cd : activeDefs) if (cd.isVisible()) visible.add(cd);
        entryTableModel.update(visible);
        configureTableColumns(visible);
        TableColumnModel tcm = mtEntryTable.getColumnModel();
        Map<String, Set<String>> savedDropFilters = columnFilterRow != null
                ? columnFilterRow.getActiveFiltersByKey() : Collections.emptyMap();
        Map<String, String> savedFinFilters = finFilterRow != null
                ? finFilterRow.getActiveFiltersByKey() : Collections.emptyMap();
        if (columnFilterRow != null) {
            columnFilterRow.rebuild(tcm, visible, activeRows);
            if (!savedDropFilters.isEmpty()) columnFilterRow.applyActiveFiltersByKey(savedDropFilters);
        }
        if (finFilterRow != null) {
            finFilterRow.rebuild(tcm, visible);
            if (!savedFinFilters.isEmpty()) finFilterRow.applyFiltersByKey(savedFinFilters);
        }
        if (finSearchField  != null) applyFinSearch(finSearchField.getText());
    }

    private void configureTableColumns(List<ColumnDef> visible) {
        for (int i = 0; i < visible.size(); i++) {
            TableColumn col = mtEntryTable.getColumnModel().getColumn(i);
            if (EntryPanelModel.TYPE_COL_KEY.equals(visible.get(i).key)) {
                col.setPreferredWidth(36);
                col.setMinWidth(36);
                col.setMaxWidth(36);
                col.setCellRenderer(new TypeIconRenderer());
            } else {
                col.setPreferredWidth(visible.get(i).qualifier.isEmpty() ? 110 : 140);
                col.setCellRenderer(new PositionCellTooltipRenderer(
                        () -> finSearchText, visible.get(i), () -> singleLineMode, dict));
            }
        }
    }

    public static TableColumnModelListener columnMarginListener(Runnable r) {
        return new TableColumnModelListener() {
            @Override public void columnAdded(TableColumnModelEvent e)   { /* not needed */ }
            @Override public void columnRemoved(TableColumnModelEvent e) { /* not needed */ }
            @Override public void columnMoved(TableColumnModelEvent e)   { /* not needed */ }
            @Override public void columnSelectionChanged(javax.swing.event.ListSelectionEvent e) { /* not needed */ }
            @Override public void columnMarginChanged(ChangeEvent e)     { r.run(); }
        };
    }

    private void repackPositionRows() {
        if (mtEntryTable == null) return;
        if (singleLineMode) {
            for (int vr = 0; vr < mtEntryTable.getRowCount(); vr++)
                mtEntryTable.setRowHeight(vr, 22);
            return;
        }
        FontMetrics fm  = mtEntryTable.getFontMetrics(mtEntryTable.getFont());
        int lineH       = fm.getHeight();
        int colCount    = mtEntryTable.getColumnCount();
        for (int vr = 0; vr < mtEntryTable.getRowCount(); vr++) {
            int maxLines = 1;
            for (int vc = 0; vc < colCount; vc++) {
                Object val = mtEntryTable.getValueAt(vr, vc);
                String text = val != null ? val.toString() : "";
                if (text.contains("\n")) maxLines = Math.max(maxLines, countLines(text));
            }
            mtEntryTable.setRowHeight(vr, Math.max(22, maxLines * lineH + 4));
        }
    }

    private static int countLines(String text) {
        int n = 1;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') n++;
        return n;
    }

    // -----------------------------------------------------------------------
    // Data model – delegates to EntryPanelModel
    // -----------------------------------------------------------------------

    public void clear()                                                    { model.clear(); }

    /** Clears the Find/search field; the document listener resets the filter and match label. */
    public void clearSearch() {
        if (finSearchField != null) finSearchField.setText("");
    }

    public void loadBatch(List<SwiftMessage> msgs, List<ColumnDef> cols)   { model.loadBatch(msgs, cols); }
    public void mergeBatch(List<SwiftMessage> msgs, List<ColumnDef> cols)  { model.mergeBatch(msgs, cols); }

    public void deleteFinRow(int modelRow) {
        model.deleteRow(modelRow);
        entryTableModel.rowDeleted(modelRow);
        host.setStatus("Row deleted.");
    }

    // -----------------------------------------------------------------------
    // Data-access helpers (called by the main app)
    // -----------------------------------------------------------------------

    public JTable                    getTable()                              { return mtEntryTable; }
    public JTextField                getSearchField()                        { return finSearchField; }
    public List<SwiftMessage>        getLoadedMessages()                    { return model.getLoadedMessages(); }
    public List<SwiftMessage>        getVisibleMessages()                   {
        LinkedHashSet<SwiftMessage> visible = new LinkedHashSet<>();
        for (int viewRow = 0; viewRow < mtEntryTable.getRowCount(); viewRow++)
            visible.add(model.getMessageForRow(mtEntryTable.convertRowIndexToModel(viewRow)));
        return new ArrayList<>(visible);
    }
    public List<ColumnDef>           getColumnDefs()                        { return model.getColumnDefs(); }
    public List<Map<String, String>> getRowData()                           { return model.getRowData(); }
    public List<SwiftTagListBlock>   getFullDisplaySequences()              { return model.getFullDisplaySequences(); }
    public SwiftMessage              getMessageForRow(int r)                { return model.getMessageForRow(r); }
    public com.mtanalyze.model.Entry getEntryForRow(int r)                 { return model.getEntryForRow(r); }
    public String                    getRowValue(int r, String key)         { return model.getRowValue(r, key); }

    // -----------------------------------------------------------------------
    // Static helpers
    // -----------------------------------------------------------------------

    private String buildColumnHeaderTooltip(ColumnDef cd) {
        String tagDesc  = dict.tagDescription(cd.tagName);
        String qualDesc = cd.qualifier.isEmpty() ? null
                        : dict.qualifierDescription(cd.qualifier);
        String desc;
        if (tagDesc != null && qualDesc != null) desc = tagDesc + " | " + qualDesc;
        else if (tagDesc  != null) desc = tagDesc;
        else if (qualDesc != null) desc = qualDesc;
        else return "<html><body style='width:300px'>" + cd.label + "</body></html>";
        return "<html><body style='width:300px'><b>" + cd.label + "</b><br>" + desc + "</body></html>";
    }

    public static void addSearchListener(JTextField field, java.util.function.Consumer<String> onSearch) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { onSearch.accept(field.getText()); }
            @Override public void removeUpdate(DocumentEvent e)  { onSearch.accept(field.getText()); }
            @Override public void changedUpdate(DocumentEvent e) { /* NOP */ }
        });
    }

    // -----------------------------------------------------------------------
    // Cell renderers
    // -----------------------------------------------------------------------

    private static final class TypeIconRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            setHorizontalAlignment(CENTER);
            setVerticalAlignment(TOP);
            String type = value != null ? value.toString() : "";
            switch (type) {
                case "RECE": setText("<html><font color='#009950'>▼</font></html>"); setToolTipText("Receive");      break;
                case "DELI": setText("<html><font color='#DC6400'>▲</font></html>"); setToolTipText("Deliver");      break;
                case "CANC": setText("<html><font color='#CC1111'>✖</font></html>"); setToolTipText("Cancellation"); break;
                case "REJT": setText("<html><font color='#8B00BB'>⛔</font></html>"); setToolTipText("Rejection");    break;
                default:     setText(""); setToolTipText(null); break;
            }
            return this;
        }
    }

    private static class PositionCellTooltipRenderer extends HighlightCellRenderer {
        private final transient ColumnDef columnDef;
        private final transient java.util.function.Supplier<Boolean> singleLine;
        private final transient HintDictionary dict;

        PositionCellTooltipRenderer(java.util.function.Supplier<String> searchText,
                                    ColumnDef columnDef,
                                    java.util.function.Supplier<Boolean> singleLine,
                                    HintDictionary dict) {
            super(searchText);
            this.columnDef  = columnDef;
            this.singleLine = singleLine;
            this.dict       = dict;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            Object display = value;
            if (Boolean.TRUE.equals(singleLine.get()) && value != null) {
                String s = value.toString();
                int nl = s.indexOf('\n');
                if (nl >= 0) display = s.substring(0, nl);
            }
            super.getTableCellRendererComponent(table, display, isSelected, hasFocus, row, col);
            String raw = value != null ? value.toString() : "";
            String desc;
            if ("PSET".equalsIgnoreCase(columnDef.qualifier)) {
                desc = dict.psetDescription(raw);
                if (desc == null) desc = dict.qualifierValueDescription(columnDef.qualifier, raw);
            } else {
                desc = columnDef.qualifier.isEmpty() ? null
                        : dict.qualifierValueDescription(columnDef.qualifier, raw);
            }
            String rawTip = raw.isEmpty() ? null : raw;
            String tip = desc != null ? desc : rawTip;
            setToolTipText(tip);
            return this;
        }
    }

    public static class HighlightCellRenderer extends DefaultTableCellRenderer {
        private final transient java.util.function.Supplier<String> searchText;

        public HighlightCellRenderer(java.util.function.Supplier<String> searchText) {
            this.searchText = searchText;
            setVerticalAlignment(SwingConstants.TOP);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            String st   = searchText.get();
            String text = value != null ? value.toString() : "";
            if (!st.isEmpty() && text.toLowerCase(Locale.ROOT).contains(st)) {
                setText("<html>" + highlight(text, st) + "</html>");
            } else if (text.contains("\n")) {
                setText("<html>" + esc(text) + "</html>");
            } else {
                setText(text);
            }
            return this;
        }

        private static String highlight(String text, String query) {
            StringBuilder sb = new StringBuilder();
            String lower = text.toLowerCase(Locale.ROOT);
            int pos = 0;
            int idx;
            while ((idx = lower.indexOf(query, pos)) >= 0) {
                sb.append(esc(text.substring(pos, idx)));
                sb.append("<span style='background:#FFD700;color:#111111'>");
                sb.append(esc(text.substring(idx, idx + query.length())));
                sb.append("</span>");
                pos = idx + query.length();
            }
            sb.append(esc(text.substring(pos)));
            return sb.toString();
        }

        static String esc(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\n", "<br>");
        }

        public static String resolveValueTooltip(JTable table, String value, int viewRow,
                                                  HintDictionary dict) {
            if (value.isEmpty()) return null;
            int modelRow = table.convertRowIndexToModel(viewRow);
            int compIdx = indexOfColumn(table, "Component");
            if (compIdx >= 0) {
                Object comp = table.getModel().getValueAt(modelRow, compIdx);
                if (comp != null && !comp.toString().isEmpty()) {
                    String desc = dict.qualifierValueDescription(comp.toString(), value);
                    if (desc != null) return desc;
                }
            }
            int qualIdx = indexOfColumn(table, "Qualifier");
            if (qualIdx < 0) return null;
            Object qual = table.getModel().getValueAt(modelRow, qualIdx);
            if (qual == null || qual.toString().isEmpty()) return null;
            return dict.qualifierValueDescription(qual.toString(), value);
        }

        static int indexOfColumn(JTable table, String name) {
            for (int c = 0; c < table.getColumnCount(); c++)
                if (name.equals(table.getColumnName(c))) return c;
            return -1;
        }
    }
}