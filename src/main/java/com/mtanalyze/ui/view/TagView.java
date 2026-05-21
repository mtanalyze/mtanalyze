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

import com.prowidesoftware.swift.model.SwiftTagListBlock;

import com.mtanalyze.model.Entry;
import com.mtanalyze.model.EntrySelectionListener;
import com.mtanalyze.model.SwiftMessage;
import com.mtanalyze.parser.MtParser;
import com.mtanalyze.ui.MtEntryPanel;
import com.mtanalyze.parser.HintDictionary;
import com.mtanalyze.profile.DataHelper;
import com.mtanalyze.profile.SavedDetailFilters;
import com.mtanalyze.ui.ColumnDef;
import com.mtanalyze.ui.FilterSupport;
import com.mtanalyze.ui.ToolbarIcons;
import com.mtanalyze.ui.ToolWindowButton;
import com.mtanalyze.ui.filter.ColumnFilterRow;
import com.mtanalyze.ui.filter.FinFilterRow;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

public class TagView extends RoundedPanel implements EntrySelectionListener {

    // -----------------------------------------------------------------------
    // Host interface – callbacks to the surrounding application
    // -----------------------------------------------------------------------
    public interface Host {
        boolean isPowerUser();
        JMenuItem makeReferenceSearchItem(String value);
        JMenuItem makeCopyCellItem(JTable table, int viewRow, int viewCol);
        JMenuItem makeCopyTableItem(JTable table);
        void showAddToDictionaryDialog(String qualifier, String value);
        void appendToEntryFilterByQualifier(String qualifier, String value);
        void onDetailValueEdited(DefaultTableModel model, int detailModelRow, String newValue);
    }

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------
    private static final int    DETAIL_MIN_WIDTH            = 380;
    private static final String MT_COL_KEY   = "\t_MT_\t\t1";
    private static final String FILE_COL_KEY = "\t_FILE_\t\t1";
    private static final String NOTE_COL_KEY = Entry.NOTE_COL_KEY;
    private static final String MT_COL_LABEL = "MT";
    private static final String NOTE_LABEL   = "Note";
    private static final String COL_SEQUENCE  = "Sequence";
    private static final String COL_TAG       = "Tag";
    private static final String COL_QUALIFIER = "Qualifier";
    private static final String COL_VALUE     = "Value";
    private static final String COL_COMPONENT = "Component";
    private static final String LABEL_COMPONENTS            = "Components";
    private static final String PREF_DETAIL_COL_VIS_TV      = "detail_col_vis_tv";
    private static final String PREF_DETAIL_COL_VIS_COMP    = "detail_col_vis_comp";
    private static final String PREF_DETAIL_FILTER_PROFILES = "detail_filter_profiles";


    // -----------------------------------------------------------------------
    // Dependencies
    // -----------------------------------------------------------------------
    private final transient Host               host;
    private final transient Preferences        prefs;
    private final transient HintDictionary dict;

    // -----------------------------------------------------------------------
    // Table
    // -----------------------------------------------------------------------
    private DefaultTableModel                              tranDetailModel;
    private JTable                                         tranDetailTable;
    private final JScrollPane                              detailScrollPane;
    private transient TableRowSorter<DefaultTableModel>    detailRowSorter;
    private ColumnFilterRow                                detailColumnFilterRow;
    private FinFilterRow                                   detailFinFilterRow;
    private transient WrapValueRenderer                    detailWrapRenderer;

    // -----------------------------------------------------------------------
    // Filter profiles
    // -----------------------------------------------------------------------
    private JComboBox<String>                                         detailFilterCombo;
    private transient java.awt.event.ActionListener                   detailFilterComboListener;
    private final transient SavedDetailFilters detailFilterCodec  = new SavedDetailFilters();
    private final transient DataHelper         dataHelper         = new DataHelper();

    private final LinkedHashMap<String, SavedDetailFilters.Profile>   savedDetailFilters = new LinkedHashMap<>();

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------
    private transient Entry              currentEntry;
    private transient Consumer<Boolean>  onComponentsToggled;
    protected boolean                    showComponents;
    private String              detailSearchText = "";
    private boolean[]           detailColVisible;
    private final Set<String>   detailHiddenTags = new LinkedHashSet<>();
    private final Set<String>   detailHiddenSeqs = new LinkedHashSet<>();

    // -----------------------------------------------------------------------
    // Search / toolbar controls (exposed to the host for the search popup)
    // -----------------------------------------------------------------------
    private JTextField     detailSearchField;
    private JLabel         detailMatchLabel;
    private JButton        detailClearBtn;
    private ToolWindowButton btnComp;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------
    public TagView(Host host, Preferences prefs, HintDictionary dict) {
        super(new BorderLayout(4, 4));
        this.showComponents = false;
        this.host  = host;
        this.prefs = prefs;
        this.dict  = dict;
        setBorder(new EmptyBorder(6, 6, 6, 6));
        setMinimumSize(new Dimension(DETAIL_MIN_WIDTH, 0));

        setupTable();
        setupSearchControls();
        buildFilterCombo();

        detailScrollPane = new JScrollPane();
        detailScrollPane.setViewportView(tranDetailTable);
        tranDetailTable.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                if (detailColumnFilterRow != null) detailColumnFilterRow.refreshLayout();
                if (detailFinFilterRow    != null) detailFinFilterRow.refreshLayout();
            }
        });
        add(detailScrollPane, BorderLayout.CENTER);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------
    public JTable            getTable()              { return tranDetailTable; }
    public ToolWindowButton  componentsToggle()      { return btnComp; }
    public JTextField        getSearchField()        { return detailSearchField; }
    public JButton           getClearButton()        { return detailClearBtn; }
    public JLabel            getMatchLabel()         { return detailMatchLabel; }
    public boolean           isComponentsMode()      { return showComponents; }

    public void setOnComponentsToggled(Consumer<Boolean> callback) { this.onComponentsToggled = callback; }
    public void setComponentsButtonSelected(boolean selected)      { btnComp.setSelected(selected); }

    public void refresh(List<SwiftTagListBlock> displaySeqs, List<Map<String, String>> rowData,
                        String seqKey, int modelRow, List<String[]> detailHeaders) {
        dataHelper.refreshDetailTable(tranDetailModel, displaySeqs, rowData,
                showComponents, seqKey, modelRow, detailHeaders);
        updateFilterValues();
        applyDetailSearch(detailSearchField.getText());
    }

    public void clear() {
        currentEntry = null;
        tranDetailModel.setRowCount(0);
    }

    public void rebuildModel(boolean withComponents) {
        showComponents  = withComponents;
        tranDetailModel = buildDetailTableModel(withComponents);
        tranDetailTable.setModel(tranDetailModel);
        loadDetailColVisible();
        applyDetailColVisibility();
        setupDetailSorterAndRenderer();
        if (currentEntry != null) refreshFromCurrentEntry();
        else applyDetailSearch(detailSearchField.getText());
    }

    // -----------------------------------------------------------------------
    // EntrySelectionListener
    // -----------------------------------------------------------------------

    @Override
    public void onSingleEntry(Entry entry, SwiftMessage message) {
        currentEntry = entry;
        refreshFromCurrentEntry();
    }

    @Override public void onMultipleEntries(List<Entry> entries) { clear(); }
    @Override public void onDeselect()                           { clear(); }

    private void refreshFromCurrentEntry() {
        refresh(
            List.of(currentEntry.fullDisplaySequence()),
            List.of(currentEntry.data()),
            MtParser.SEQ_KEY, 0, buildHeaders(currentEntry)
        );
    }

    private static List<String[]> buildHeaders(Entry entry) {
        List<String[]> h = new ArrayList<>();
        String mt = entry.getValue(MT_COL_KEY);
        String fn = entry.getValue(FILE_COL_KEY);
        if (!mt.isEmpty()) h.add(new String[]{MT_COL_LABEL, mt});
        if (!fn.isEmpty()) h.add(new String[]{"File", fn});
        if (entry.data().containsKey(NOTE_COL_KEY))
            h.add(new String[]{NOTE_LABEL, entry.getValue(NOTE_COL_KEY), "1"});
        return h;
    }

    public void updateFilterValues() {
        if (detailColumnFilterRow == null || tranDetailModel == null) return;
        detailColumnFilterRow.updateColumnValues(tranDetailModel);
    }

    public JPopupMenu buildContextMenu(int viewRow, int viewCol) {
        int modelRow = viewRow >= 0 ? tranDetailTable.convertRowIndexToModel(viewRow) : -1;
        return buildDetailContextMenu(tranDetailTable, modelRow, viewRow, viewCol);
    }

    public void activateNoteEditing() {
        int valueCol = findDetailValueColumn();
        if (valueCol < 0) return;
        for (int r = 0; r < tranDetailModel.getRowCount(); r++) {
            Object tag = tranDetailModel.getValueAt(r, 1);
            if (!NOTE_LABEL.equals(tag != null ? tag.toString() : "")) continue;
            int viewRow = detailRowSorter != null ? detailRowSorter.convertRowIndexToView(r) : r;
            if (viewRow < 0) return;
            tranDetailTable.scrollRectToVisible(tranDetailTable.getCellRect(viewRow, valueCol, true));
            tranDetailTable.editCellAt(viewRow, valueCol);
            tranDetailTable.requestFocusInWindow();
            return;
        }
    }

    public void focusTag(ColumnDef cd) {
        if (tranDetailTable == null || detailRowSorter == null) return;
        String[] keyParts = cd.key.split("\t", -1);
        int targetOcc;
        try {
            targetOcc = keyParts.length >= 4 ? Integer.parseInt(keyParts[3]) : 1;
        } catch (NumberFormatException e) {
            targetOcc = 1;
        }
        int matchCount = 0;
        int targetModelRow = -1;
        for (int r = 0; r < tranDetailModel.getRowCount(); r++) {
            Object seq  = tranDetailModel.getValueAt(r, 0);
            Object tag  = tranDetailModel.getValueAt(r, 1);
            Object qual = tranDetailModel.getValueAt(r, 2);
            if (cd.seqLabel.equals(seq) && cd.tagName.equals(tag)
                    && cd.qualifier.equals(qual != null ? qual.toString() : "")
                    && ++matchCount == targetOcc) { targetModelRow = r; break; }
        }
        if (targetModelRow < 0) return;
        int viewRow = detailRowSorter.convertRowIndexToView(targetModelRow);
        if (viewRow < 0) return;
        tranDetailTable.setRowSelectionInterval(viewRow, viewRow);
        tranDetailTable.scrollRectToVisible(tranDetailTable.getCellRect(viewRow, 0, true));
        tranDetailTable.requestFocusInWindow();
    }

    // -----------------------------------------------------------------------
    // Setup
    // -----------------------------------------------------------------------
    private void setupTable() {
        tranDetailModel       = buildDetailTableModel(showComponents);
        detailColumnFilterRow = new ColumnFilterRow(this::applyDetailFilters, this::convertDetailDropToQuickFilter);
        detailFinFilterRow    = new FinFilterRow(this::applyDetailFilters, null);

        tranDetailTable = new JTable(tranDetailModel) {
            @Override
            protected void configureEnclosingScrollPane() {
                super.configureEnclosingScrollPane();
                FilterSupport.installFilterRowsInScrollPane(detailScrollPane, getTableHeader(), detailColumnFilterRow, detailFinFilterRow);
            }
        };
        tranDetailTable.setRowHeight(22);
        tranDetailTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tranDetailTable.setFillsViewportHeight(true);
        tranDetailTable.getTableHeader().setReorderingAllowed(false);
        attachDetailPopup(tranDetailTable);
        loadDetailColVisible();
        applyDetailColVisibility();
        setupDetailSorterAndRenderer();
    }

    private void setupSearchControls() {
        detailSearchField = new JTextField(14);
        detailSearchField.setToolTipText("Search all fields (min. 3 characters)");
        detailMatchLabel = FilterSupport.makeMatchLabel();
        detailClearBtn   = FilterSupport.makeClearButton(detailSearchField);

        MtEntryPanel.addSearchListener(detailSearchField, this::applyDetailSearch);

        btnComp = new ToolWindowButton(LABEL_COMPONENTS, ToolbarIcons.splitValues());
        btnComp.addActionListener(e -> {
            if (onComponentsToggled != null) onComponentsToggled.accept(btnComp.isSelected());
        });
    }

    private void buildFilterCombo() {
        savedDetailFilters.putAll(
            detailFilterCodec.deserialize(prefs.get(PREF_DETAIL_FILTER_PROFILES, "")));
        detailFilterCombo = new JComboBox<>();
        detailFilterCombo.addItem("");
        savedDetailFilters.keySet().forEach(detailFilterCombo::addItem);
        detailFilterCombo.setPreferredSize(new Dimension(150, 24));
        detailFilterCombo.setMaximumSize(new Dimension(150, 24));
        detailFilterCombo.setToolTipText("Load a saved detail fields filter (right-click to delete)");
        detailFilterComboListener = e -> onDetailFilterComboSelected();
        detailFilterCombo.addActionListener(detailFilterComboListener);
        detailFilterCombo.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed (java.awt.event.MouseEvent e) { maybeShowDetailFilterComboPopup(e); }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) { maybeShowDetailFilterComboPopup(e); }
        });
    }

    // -----------------------------------------------------------------------
    // Table model
    // -----------------------------------------------------------------------
    private DefaultTableModel buildDetailTableModel(boolean withComponents) {
        String[] cols = withComponents
            ? new String[]{COL_SEQUENCE, COL_TAG, COL_QUALIFIER, COL_COMPONENT, COL_VALUE}
            : new String[]{COL_SEQUENCE, COL_TAG, COL_QUALIFIER, COL_VALUE};
        return new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) {
                if (withComponents || c != getColumnCount() - 1) return false;
                Object seq = getValueAt(r, 0);
                if (seq != null && !seq.toString().isEmpty()) return false;
                Object tag = getValueAt(r, 1);
                return NOTE_LABEL.equals(tag != null ? tag.toString() : "");
            }
            @Override public void setValueAt(Object aValue, int row, int column) {
                super.setValueAt(aValue, row, column);
                if (withComponents || column != getColumnCount() - 1) return;
                host.onDetailValueEdited(this, row, aValue != null ? aValue.toString() : "");
            }
        };
    }

    // -----------------------------------------------------------------------
    // Sorter and renderer
    // -----------------------------------------------------------------------
    private void setupDetailSorterAndRenderer() {
        detailRowSorter = new TableRowSorter<>(tranDetailModel);
        tranDetailTable.setRowSorter(detailRowSorter);
        detailRowSorter.addRowSorterListener(e -> repackDetailRows());
        tranDetailTable.getTableHeader().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed (java.awt.event.MouseEvent e) { maybeShowDetailHeaderPopup(e); }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) { maybeShowDetailHeaderPopup(e); }
        });

        DictionaryTooltipRenderer hlr = new DictionaryTooltipRenderer(() -> detailSearchText);
        int last = tranDetailTable.getColumnCount() - 1;
        for (int i = 0; i < last; i++)
            tranDetailTable.getColumnModel().getColumn(i).setCellRenderer(hlr);
        detailWrapRenderer = new WrapValueRenderer(() -> detailSearchText, dict);
        tranDetailTable.getColumnModel().getColumn(last).setCellRenderer(detailWrapRenderer);

        tranDetailTable.getColumnModel().addColumnModelListener(
            MtEntryPanel.columnMarginListener(this::repackDetailRows));
        if (detailColumnFilterRow != null) rebuildDetailFilterRows();
    }

    private void maybeShowDetailHeaderPopup(java.awt.event.MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        int viewCol = tranDetailTable.getTableHeader().columnAtPoint(e.getPoint());
        if (viewCol < 0) return;
        int modelCol = tranDetailTable.convertColumnIndexToModel(viewCol);
        JPopupMenu popup = new JPopupMenu();
        FilterSupport.addSortMenuItems(popup, detailRowSorter, modelCol);
        popup.show(e.getComponent(), e.getX(), e.getY());
    }

    private void repackDetailRows() {
        if (tranDetailTable == null || detailWrapRenderer == null) return;
        if (tranDetailModel == null || tranDetailModel.getRowCount() == 0) return;
        int valueCol = findDetailValueColumn();
        if (valueCol < 0) return;
        int colW = tranDetailTable.getColumnModel().getColumn(valueCol).getWidth();
        JTextArea probe = detailWrapRenderer.getProbeArea();
        probe.setFont(tranDetailTable.getFont());
        for (int vr = 0; vr < tranDetailTable.getRowCount(); vr++) {
            Object val = tranDetailTable.getValueAt(vr, valueCol);
            probe.setText(val != null ? val.toString() : "");
            probe.setSize(Math.max(1, colW), Short.MAX_VALUE);
            int h = Math.max(22, probe.getPreferredSize().height + 4);
            tranDetailTable.setRowHeight(vr, h);
        }
    }

    private int findDetailValueColumn() {
        for (int c = 0; c < tranDetailTable.getColumnCount(); c++) {
            if (COL_VALUE.equals(tranDetailTable.getColumnName(c))) return c;
        }
        return -1;
    }

    // -----------------------------------------------------------------------
    // Context menu
    // -----------------------------------------------------------------------
    private void attachDetailPopup(JTable table) {
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed (java.awt.event.MouseEvent e) { maybeShow(e); }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) { maybeShow(e); }
            private void maybeShow(java.awt.event.MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int viewRow = table.rowAtPoint(e.getPoint());
                int viewCol = table.columnAtPoint(e.getPoint());
                if (viewRow < 0 || viewCol < 0) return;
                table.setRowSelectionInterval(viewRow, viewRow);
                int modelRow = table.convertRowIndexToModel(viewRow);
                buildDetailContextMenu(table, modelRow, viewRow, viewCol)
                    .show(e.getComponent(), e.getX(), e.getY());
            }
        });
    }

    private JPopupMenu buildDetailContextMenu(JTable table, int modelRow, int viewRow, int viewCol) {
        JPopupMenu popup = new JPopupMenu();
        boolean powerUser    = host.isPowerUser();
        int valueCol         = findColumnIndexByName(table, COL_VALUE);
        int qualifierCol     = findColumnIndexByName(table, COL_QUALIFIER);
        String  refValue     = getDetailCellStringByModelColumn(table, modelRow, valueCol);
        String  qualifier    = getDetailCellStringByModelColumn(table, modelRow, qualifierCol);
        boolean hasQualifier = !qualifier.isEmpty();

        popup.add(host.makeCopyCellItem(table, viewRow, viewCol));
        popup.add(host.makeCopyTableItem(table));

        if (!refValue.isEmpty() && (powerUser || hasQualifier)) {
            popup.addSeparator();
            if (powerUser)    popup.add(host.makeReferenceSearchItem(refValue));
            if (hasQualifier) popup.add(makeDetailAppendToFilterItem(qualifier, refValue));
        }

        if (powerUser) {
            popup.addSeparator();
            popup.add(makeAddToDictItem(table, viewRow));
        }

        return popup;
    }

    private JMenuItem makeDetailAppendToFilterItem(String qualifier, String value) {
        int nl = value.indexOf('\n');
        String filterValue = nl >= 0 ? value.substring(0, nl).trim() : value;
        String label = filterValue.length() > 25 ? filterValue.substring(0, 22) + "…" : filterValue;
        JMenuItem item = new JMenuItem("Add to Quick Filter: " + label, ToolbarIcons.menuFilterAdd());
        item.addActionListener(ae -> host.appendToEntryFilterByQualifier(qualifier, filterValue));
        return item;
    }

    private JMenuItem makeAddToDictItem(JTable table, int viewRow) {
        JMenuItem item = new JMenuItem("Add to Dictionary...", ToolbarIcons.menuAddDict());
        item.addActionListener(e -> {
            int modelRow     = table.convertRowIndexToModel(viewRow);
            int qualifierCol = findColumnIndexByName(table, COL_QUALIFIER);
            int valueCol     = findColumnIndexByName(table, COL_VALUE);
            String qual      = getDetailCellStringByModelColumn(table, modelRow, qualifierCol);
            String value     = getDetailCellStringByModelColumn(table, modelRow, valueCol);
            host.showAddToDictionaryDialog(qual, value);
        });
        return item;
    }

    private static int findColumnIndexByName(JTable table, String colName) {
        for (int c = 0; c < table.getColumnCount(); c++) {
            if (colName.equals(table.getColumnName(c))) return c;
        }
        return -1;
    }

    private static String getDetailCellStringByModelColumn(JTable table, int modelRow, int modelCol) {
        if (modelCol < 0) return "";
        Object v = table.getModel().getValueAt(modelRow, modelCol);
        return v != null ? v.toString().trim() : "";
    }

    // -----------------------------------------------------------------------
    // Filtering
    // -----------------------------------------------------------------------
    private void applyDetailSearch(String query) {
        detailSearchText = query.length() >= 3 ? query.toLowerCase(Locale.ROOT) : "";
        applyDetailFilters();
    }

    private void rebuildDetailFilterRows() {
        if (detailColumnFilterRow == null || detailFinFilterRow == null || tranDetailModel == null) return;
        TableColumnModel tcm = tranDetailTable.getColumnModel();
        detailColumnFilterRow.rebuild(tcm, tranDetailModel);
        detailFinFilterRow.rebuild(tcm, tranDetailModel.getColumnCount());
    }

    private void convertDetailDropToQuickFilter() {
        if (detailColumnFilterRow == null || detailFinFilterRow == null) return;
        Map<Integer, Set<String>> dropFilters = detailColumnFilterRow.getActiveFilters();
        if (dropFilters.isEmpty()) return;
        Map<Integer, String> expressions = new HashMap<>();
        for (Map.Entry<Integer, Set<String>> entry : dropFilters.entrySet()) {
            String expr = entry.getValue().stream()
                .map(v -> "=" + v)
                .collect(java.util.stream.Collectors.joining("+"));
            expressions.put(entry.getKey(), expr);
        }
        detailFinFilterRow.setFilterByModelIndex(expressions);
        detailColumnFilterRow.clearAll();
    }

    private void applyDetailFilters() {
        if (detailRowSorter == null) return;
        List<RowFilter<DefaultTableModel, Integer>> filters = new ArrayList<>();
        buildSearchFilter(filters);
        buildTagFilter(filters);
        buildSeqFilter(filters);
        buildDetailDropFilter(filters);
        buildDetailQuickFilter(filters);
        applyDetailRowFilter(filters);
        updateDetailMatchLabel();
        tranDetailTable.repaint();
    }

    private void buildDetailDropFilter(List<RowFilter<DefaultTableModel, Integer>> filters) {
        if (detailColumnFilterRow == null) return;
        Map<Integer, Set<String>> dropFilters = detailColumnFilterRow.getActiveFilters();
        if (dropFilters.isEmpty()) return;
        filters.add(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> e) {
                return FilterSupport.passesDropFilter(e, dropFilters);
            }
        });
    }

    private void buildDetailQuickFilter(List<RowFilter<DefaultTableModel, Integer>> filters) {
        if (detailFinFilterRow == null) return;
        Map<Integer, String> quickFilters = detailFinFilterRow.getActiveFilters();
        if (quickFilters.isEmpty()) return;
        filters.add(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> e) {
                return FilterSupport.passesQuickFilter(e, quickFilters);
            }
        });
    }

    private void buildSearchFilter(List<RowFilter<DefaultTableModel, Integer>> filters) {
        if (detailSearchText.isEmpty()) return;
        final String lq = detailSearchText;
        filters.add(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> e) {
                for (int i = 0; i < e.getValueCount(); i++) {
                    Object v = e.getValue(i);
                    if (v != null && v.toString().toLowerCase(Locale.ROOT).contains(lq)) return true;
                }
                return false;
            }
        });
    }

    private void buildTagFilter(List<RowFilter<DefaultTableModel, Integer>> filters) {
        if (detailHiddenTags.isEmpty()) return;
        filters.add(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> e) {
                Object tag = e.getValue(1);
                return tag == null || !detailHiddenTags.contains(tag.toString());
            }
        });
    }

    private void buildSeqFilter(List<RowFilter<DefaultTableModel, Integer>> filters) {
        if (detailHiddenSeqs.isEmpty()) return;
        filters.add(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> e) {
                Object seq = e.getValue(0);
                return seq == null || !detailHiddenSeqs.contains(seq.toString());
            }
        });
    }

    private void applyDetailRowFilter(List<RowFilter<DefaultTableModel, Integer>> filters) {
        if (filters.isEmpty()) {
            detailRowSorter.setRowFilter(null);
        } else if (filters.size() == 1) {
            detailRowSorter.setRowFilter(filters.get(0));
        } else {
            detailRowSorter.setRowFilter(RowFilter.andFilter(filters));
        }
    }

    private void updateDetailMatchLabel() {
        boolean anyFilter = !detailSearchText.isEmpty()
                         || !detailHiddenTags.isEmpty()
                         || !detailHiddenSeqs.isEmpty()
                         || (detailColumnFilterRow != null && !detailColumnFilterRow.getActiveFilters().isEmpty())
                         || (detailFinFilterRow    != null && !detailFinFilterRow.getActiveFilters().isEmpty());
        if (!anyFilter) {
            detailMatchLabel.setText("  ");
            return;
        }
        int n = detailRowSorter.getViewRowCount();
        detailMatchLabel.setText(n == 0
            ? "<html><font color='red'>No matches</font></html>"
            : n + " rows");
    }

    // -----------------------------------------------------------------------
    // Column visibility
    // -----------------------------------------------------------------------
    private void loadDetailColVisible() {
        int n = tranDetailModel.getColumnCount();
        String key = showComponents ? PREF_DETAIL_COL_VIS_COMP : PREF_DETAIL_COL_VIS_TV;
        String saved = prefs.get(key, "");
        String[] parts = saved.isEmpty() ? new String[0] : saved.split(",", -1);
        detailColVisible = new boolean[n];
        for (int i = 0; i < n; i++)
            detailColVisible[i] = (i >= parts.length) || "1".equals(parts[i]);
    }

    private void applyDetailColVisibility() {
        if (detailColVisible == null) loadDetailColVisible();
        TableColumnModel tcm = tranDetailTable.getColumnModel();
        for (int i = 0; i < tcm.getColumnCount(); i++) {
            boolean vis = (i < detailColVisible.length) && detailColVisible[i];
            applyDetailColWidth(tcm.getColumn(i), i, vis);
        }
    }

    private void applyDetailColWidth(TableColumn col, int idx, boolean visible) {
        if (!visible) {
            col.setMinWidth(0); col.setMaxWidth(0); col.setPreferredWidth(0);
        } else {
            col.setMinWidth(15);
            col.setMaxWidth(Integer.MAX_VALUE);
            col.setPreferredWidth(defaultDetailColWidth(idx));
        }
    }

    private int defaultDetailColWidth(int colIndex) {
        return switch (colIndex) {
            case 0, 2 -> 80;
            case 1    -> 50;
            case 3    -> showComponents ? 130 : 350;
            case 4    -> 250;
            default   -> 100;
        };
    }

    // -----------------------------------------------------------------------
    // Filter profiles
    // -----------------------------------------------------------------------
    private void maybeShowDetailFilterComboPopup(java.awt.event.MouseEvent e) {
        FilterSupport.showComboDeletePopup(e, detailFilterCombo, this::deleteDetailFilterProfile);
    }

    private void deleteDetailFilterProfile(String name) {
        savedDetailFilters.remove(name);
        prefs.put(PREF_DETAIL_FILTER_PROFILES, detailFilterCodec.serialize(savedDetailFilters));
        refreshDetailFilterCombo();
    }

    private void onDetailFilterComboSelected() {
        String name = (String) detailFilterCombo.getSelectedItem();
        if (name == null || name.isEmpty()) return;
        SavedDetailFilters.Profile profile = savedDetailFilters.get(name);
        if (profile == null) return;
        detailHiddenSeqs.clear();
        detailHiddenSeqs.addAll(profile.hiddenSeqs());
        detailHiddenTags.clear();
        detailHiddenTags.addAll(profile.hiddenTags());
        applyDetailFilters();
    }

    private void refreshDetailFilterCombo() {
        detailFilterCombo.removeActionListener(detailFilterComboListener);
        detailFilterCombo.removeAllItems();
        detailFilterCombo.addItem("");
        savedDetailFilters.keySet().forEach(detailFilterCombo::addItem);
        detailFilterCombo.addActionListener(detailFilterComboListener);
    }

    // -----------------------------------------------------------------------
    // Cell renderers
    // -----------------------------------------------------------------------

    private static class WrapValueRenderer implements TableCellRenderer {

        private static final javax.swing.text.Highlighter.HighlightPainter HIGHLIGHT_PAINTER =
            new javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(new Color(0xFF, 0xD7, 0x00));

        private final JTextArea area = new JTextArea();
        private final java.util.function.Supplier<String> searchText;
        private final HintDictionary dict;

        WrapValueRenderer(java.util.function.Supplier<String> searchText, HintDictionary dict) {
            this.searchText = searchText;
            this.dict       = dict;
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setOpaque(true);
            area.setMargin(new Insets(0, 0, 0, 0));
            area.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        }

        JTextArea getProbeArea() { return area; }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            String text = value != null ? value.toString() : "";
            area.setText(text);
            area.setFont(table.getFont());
            if (isSelected) {
                area.setBackground(table.getSelectionBackground());
                area.setForeground(table.getSelectionForeground());
            } else {
                Color alt = UIManager.getColor("Table.alternateRowColor");
                area.setBackground(alt != null && row % 2 != 0 ? alt : table.getBackground());
                area.setForeground(table.getForeground());
            }
            area.setToolTipText(MtEntryPanel.HighlightCellRenderer.resolveValueTooltip(table, text, row, dict));
            applyHighlight(text);
            return area;
        }

        private void applyHighlight(String text) {
            area.getHighlighter().removeAllHighlights();
            String st = searchText.get();
            if (st.isEmpty()) return;
            String lower = text.toLowerCase(Locale.ROOT);
            int idx = lower.indexOf(st);
            while (idx >= 0) {
                try {
                    area.getHighlighter().addHighlight(idx, idx + st.length(), HIGHLIGHT_PAINTER);
                } catch (javax.swing.text.BadLocationException ex) {
                    throw new AssertionError("Highlight position out of range – idx derived from same text", ex);
                }
                idx = lower.indexOf(st, idx + 1);
            }
        }
    }

    private class DictionaryTooltipRenderer extends MtEntryPanel.HighlightCellRenderer {

        DictionaryTooltipRenderer(java.util.function.Supplier<String> searchText) {
            super(searchText);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int col) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            setToolTipText(resolveTooltip(table, value, row, col));
            return this;
        }

        private String resolveTooltip(JTable table, Object value, int row, int col) {
            if (value == null) return null;
            String text = value.toString().trim();
            if (text.isEmpty()) return null;
            String colName = table.getColumnName(col);
            if (COL_SEQUENCE.equals(colName))  return dict.tagDescription(text);
            if (COL_TAG.equals(colName))       return dict.tagDescription(text);
            if (COL_QUALIFIER.equals(colName)) return dict.qualifierDescription(text);
            if (COL_COMPONENT.equals(colName)) return dict.componentDescription(text);
            if (COL_VALUE.equals(colName))     return resolveValueTooltip(table, text, row, dict);
            return null;
        }
    }
}