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
package com.mtanalyze.ui.filter;

import com.mtanalyze.ui.ColumnDef;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

/**
 * Dropdown filter row – one multi-select button per visible column.
 * Clicking a button opens a checkbox popup (Excel-style).
 * Placed directly below the table header, above the Quick Filter row.
 */
public class ColumnFilterRow extends AbstractFilterRow {
    private static final Logger LOG = Logger.getLogger(ColumnFilterRow.class.getName());

    private static final int POPUP_ROW_H   = 20;
    private static final int POPUP_MAX_VIS = 15;
    private static final int POPUP_MIN_W   = 140;
    public static final String ALL = "(All)";

    private final transient Runnable onFilterChanged;
    private final transient Runnable onConvertToQuickFilter;

    /** buttons.get(modelIdx) = filter button for that model column. */
    private final List<JButton>      buttons      = new ArrayList<>();
    /** activeFilters.get(modelIdx) = selected values – empty ⇒ no filter. */
    private final List<Set<String>>  activeFilters = new ArrayList<>();
    /** columnValues.get(modelIdx) = all unique sorted values for that column. */
    private final List<List<String>> columnValues  = new ArrayList<>();
    /** colKeys.get(modelIdx) = ColumnDef.key for that model column (empty when built from DefaultTableModel). */
    private final List<String>       colKeys       = new ArrayList<>();

    /**
     * Creates the dropdown-based column filter row.
     *
     * @param onFilterChanged callback invoked whenever dropdown filter selections change,
     *                        so the table filtering can be reapplied.
     * @param onConvertToQuickFilter callback invoked when the user converts dropdown
     *                               filters to quick filters.
     */
    public ColumnFilterRow(Runnable onFilterChanged, Runnable onConvertToQuickFilter) {
        this.onFilterChanged = onFilterChanged;
        this.onConvertToQuickFilter = onConvertToQuickFilter;
        setLayout(null);
        setPreferredSize(new Dimension(0, ROW_H));
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Rebuilds the column filter row from the currently visible columns and row data.
     * <p>
     * This clears and recreates one filter button per visible model column, computes the
     * distinct non-empty values available in each column, and resets active selections.
     * Call this after table structure, visible columns, or backing row data changes and
     * before the user interacts with column filters.
     *
     * @param colModel the current table column model used to align filter controls with visible columns
     * @param visibleCols the visible column definitions, in display/model order, used to build filter buttons
     * @param rowData the table rows used to collect unique values for each column's filter popup
     */
    public void rebuild(TableColumnModel colModel,
                 List<ColumnDef> visibleCols,
                 List<Map<String, String>> rowData) {
        beginRebuild(colModel);
        for (ColumnDef cd : visibleCols) {
            Set<String> vals = new TreeSet<>();
            for (Map<String, String> row : rowData) {
                String v = row.getOrDefault(cd.key, "");
                if (!v.isEmpty()) vals.add(v);
            }
            colKeys.add(cd.key);
            columnValues.add(new ArrayList<>(vals));
            activeFilters.add(new LinkedHashSet<>());
            int modelIdx = buttons.size();
            JButton btn = makeButton(modelIdx);
            buttons.add(btn);
            add(btn);
        }
        finishRebuild();
        validateColumnStateInvariant();
    }

    /** Returns ColumnDef.key → selected values for every active dropdown filter. */
    public Map<String, Set<String>> getActiveFiltersByKey() {
        validateColumnStateInvariant();
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (int i = 0; i < activeFilters.size(); i++) {
            if (!activeFilters.get(i).isEmpty())
                result.put(colKeys.get(i), new LinkedHashSet<>(activeFilters.get(i)));
        }
        return result;
    }

    /** Restores dropdown filter selections by ColumnDef.key, keeping only values still present in the data. */
    public void applyActiveFiltersByKey(Map<String, Set<String>> saved) {
        boolean changed = false;
        for (int i = 0; i < activeFilters.size(); i++) {
            Set<String> savedVals = saved.get(colKeys.get(i));
            if (savedVals == null || savedVals.isEmpty()) continue;
            Set<String> available = new HashSet<>(columnValues.get(i));
            for (String v : savedVals) {
                if (available.contains(v)) { activeFilters.get(i).add(v); changed = true; }
            }
            if (!activeFilters.get(i).isEmpty()) refreshLabel(i);
        }
        if (changed) onFilterChanged.run();
    }

    /**
     * Rebuilds from a {@link DefaultTableModel}.
     * <p>
     * Unlike the other {@code rebuild(...)} overload that receives {@code List<ColumnDef>} and uses
     * {@code ColumnDef.key} values, this overload identifies columns by their model index (as string keys).
     * Use this variant when no {@code ColumnDef} metadata is available.
     */
    public void rebuild(TableColumnModel colModel, DefaultTableModel model) {
        beginRebuild(colModel);
        for (int i = 0; i < model.getColumnCount(); i++) {
            columnValues.add(collectColValues(model, i));
            activeFilters.add(new LinkedHashSet<>());
            JButton btn = makeButton(i);
            buttons.add(btn);
            add(btn);
        }
        finishRebuild();
    }

    /**
     * Refreshes the set of available values shown in each dropdown filter from the current table model.
     * <p>
     * This updates only the selectable values per column and intentionally does not clear or modify
     * existing active selections in {@code activeFilters}.
     * </p>
     * <p>
     * Call this after underlying table data changes (for example, rows added/removed/edited) when
     * the distinct values available for filtering may have changed.
     * </p>
     *
     * @param model the table model providing the current column values
     */
    public void updateColumnValues(DefaultTableModel model) {
        int modelColumnCount = model.getColumnCount();
        int valueColumnCount = columnValues.size();
        if (modelColumnCount != valueColumnCount) {
            throw new IllegalStateException(
                    "Column filter state is out of sync with table model: model columns="
                            + modelColumnCount + ", filter columns=" + valueColumnCount
                            + ". Rebuild filters after structural column changes.");
        }
        for (int i = 0; i < valueColumnCount; i++) {
            columnValues.set(i, collectColValues(model, i));
        }
    }

    private void beginRebuild(TableColumnModel colModel) {
        if (columnModel != null)
            columnModel.removeColumnModelListener(colModelListener);
        columnModel = colModel;
        colModel.addColumnModelListener(colModelListener);
        removeAll();
        buttons.clear();
        activeFilters.clear();
        columnValues.clear();
        colKeys.clear();
    }

    private void finishRebuild() {
        refreshLayout();
        revalidate();
        repaint();
    }

    private void validateColumnStateInvariant() {
        int expected = activeFilters.size();
        if (buttons.size() != expected || columnValues.size() != expected || colKeys.size() != expected) {
            throw new IllegalStateException(
                    "Column filter state out of sync: buttons=" + buttons.size()
                            + ", activeFilters=" + activeFilters.size()
                            + ", columnValues=" + columnValues.size()
                            + ", colKeys=" + colKeys.size());
        }
    }

    private static List<String> collectColValues(DefaultTableModel model, int col) {
        Set<String> vals = new TreeSet<>();
        for (int r = 0; r < model.getRowCount(); r++) {
            Object v = model.getValueAt(r, col);
            if (v != null && !v.toString().isEmpty()) vals.add(v.toString());
        }
        return new ArrayList<>(vals);
    }

    public void clearAll() {
        boolean changed = false;
        for (int i = 0; i < activeFilters.size(); i++) {
            if (!activeFilters.get(i).isEmpty()) {
                activeFilters.get(i).clear();
                if (i < buttons.size()) refreshLabel(i);
                changed = true;
            }
        }
        if (changed) onFilterChanged.run();
    }

    /** Returns model-column-index → selected values for every active filter. */
    public Map<Integer, Set<String>> getActiveFilters() {
        Map<Integer, Set<String>> result = new LinkedHashMap<>();
        for (int i = 0; i < activeFilters.size(); i++) {
            if (!activeFilters.get(i).isEmpty())
                result.put(i, new HashSet<>(activeFilters.get(i)));
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Layout
    // -----------------------------------------------------------------------

    @Override
    public void refreshLayout() {
        if (columnModel == null) return;
        int x = 0;
        for (int viewCol = 0; viewCol < columnModel.getColumnCount(); viewCol++) {
            TableColumn tc  = columnModel.getColumn(viewCol);
            int modelIdx    = tc.getModelIndex();
            int w           = effectiveWidth(tc);
            if (modelIdx >= 0 && modelIdx < buttons.size()) {
                buttons.get(modelIdx).setBounds(x, 0, w, ROW_H);
            } else {
                LOG.log(Level.FINE,
                        "Column model index out of sync with filter buttons: modelIdx={0}, buttons.size={1}",
                        new Object[]{modelIdx, buttons.size()});
            }
            x += w;
        }
        setPreferredSize(new Dimension(x, ROW_H));
    }

    // -----------------------------------------------------------------------
    // Button + popup
    // -----------------------------------------------------------------------

    private JButton makeButton(int modelIdx) {
        JButton btn = new JButton(labelFor(modelIdx));
        btn.setFont(btn.getFont().deriveFont(10f));
        btn.setMargin(new Insets(0, 3, 0, 3));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setFocusable(false);
        btn.addActionListener(e -> showPopup(modelIdx, btn));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed (java.awt.event.MouseEvent e) { maybeShowContextMenu(e, btn); }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) { maybeShowContextMenu(e, btn); }
        });
        return btn;
    }

    private void maybeShowContextMenu(java.awt.event.MouseEvent e, JButton anchor) {
        if (!e.isPopupTrigger()) return;
        JPopupMenu menu = new JPopupMenu();
        JMenuItem convertItem = new JMenuItem("Convert to Quick Filter");
        convertItem.setEnabled(!getActiveFilters().isEmpty());
        convertItem.addActionListener(ae -> onConvertToQuickFilter.run());
        menu.add(convertItem);
        JMenuItem clearItem = new JMenuItem("Clear Auto Filter");
        clearItem.setEnabled(!getActiveFilters().isEmpty());
        clearItem.addActionListener(ae -> clearAll());
        menu.add(clearItem);
        menu.show(anchor, e.getX(), e.getY());
    }

    private void showPopup(int modelIdx, JButton anchor) {
        List<String> vals    = columnValues.get(modelIdx);
        Set<String>  current = activeFilters.get(modelIdx);

        JCheckBox allCb = new JCheckBox(ALL, current.isEmpty());
        allCb.setFont(allCb.getFont().deriveFont(Font.BOLD, 11f));

        List<JCheckBox> valCbs = new ArrayList<>(vals.size());
        for (String v : vals) {
            JCheckBox cb = new JCheckBox(v, current.contains(v));
            cb.setFont(cb.getFont().deriveFont(10f));
            valCbs.add(cb);
        }

        wireAllCheckbox(allCb, valCbs, modelIdx);
        wireValueCheckboxes(valCbs, vals, allCb, modelIdx);

        JPanel cbPanel = new JPanel();
        cbPanel.setLayout(new BoxLayout(cbPanel, BoxLayout.Y_AXIS));
        cbPanel.setBorder(new EmptyBorder(2, 4, 2, 4));
        cbPanel.add(allCb);
        cbPanel.add(new JSeparator());
        for (JCheckBox cb : valCbs) cbPanel.add(cb);

        int popupH = Math.min(vals.size() + 2, POPUP_MAX_VIS) * POPUP_ROW_H + 8;
        int popupW = Math.max(POPUP_MIN_W, anchor.getWidth());

        JScrollPane scroll = new JScrollPane(cbPanel,
                VERTICAL_SCROLLBAR_AS_NEEDED,
                HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(popupW, popupH));
        scroll.setBorder(null);

        JPopupMenu popup = new JPopupMenu();
        popup.setLayout(new BorderLayout());
        popup.add(scroll, BorderLayout.CENTER);
        popup.show(anchor, 0, anchor.getHeight());
    }

    private void wireAllCheckbox(JCheckBox allCb, List<JCheckBox> valCbs,
                                  int modelIdx) {
        allCb.addActionListener(e -> {
            if (allCb.isSelected()) {
                valCbs.forEach(c -> c.setSelected(false));
                activeFilters.get(modelIdx).clear();
                refreshLabel(modelIdx);
                onFilterChanged.run();
            } else {
                allCb.setSelected(true);  // cannot uncheck without selecting values
            }
        });
    }

    private void wireValueCheckboxes(List<JCheckBox> valCbs, List<String> vals,
                                      JCheckBox allCb, int modelIdx) {
        for (int i = 0; i < valCbs.size(); i++) {
            final int idx = i;
            final String val = vals.get(i);
            valCbs.get(i).addActionListener(e -> {
                Set<String> sel = activeFilters.get(modelIdx);
                if (valCbs.get(idx).isSelected()) {
                    sel.add(val);
                    allCb.setSelected(false);
                } else {
                    sel.remove(val);
                    if (sel.isEmpty()) allCb.setSelected(true);
                }
                refreshLabel(modelIdx);
                onFilterChanged.run();
            });
        }
    }

    private void refreshLabel(int modelIdx) {
        if (modelIdx < buttons.size()) buttons.get(modelIdx).setText(labelFor(modelIdx));
    }

    private String labelFor(int modelIdx) {
        if (modelIdx >= activeFilters.size() || activeFilters.get(modelIdx).isEmpty())
            return ALL + " ▾";
        return activeFilters.get(modelIdx).size() + " ✓ ▾";
    }

}
