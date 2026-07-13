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
package com.mtanalyze.ui;

import com.mtanalyze.ui.filter.ColumnFilterRow;
import com.mtanalyze.ui.filter.FinFilterRow;
import com.mtanalyze.ui.filter.QuickFilterParser;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class FilterSupport {

    private FilterSupport() {}

    public static boolean passesDropFilter(
            RowFilter.Entry<?, ?> e, Map<Integer, Set<String>> filters) {
        for (Map.Entry<Integer, Set<String>> f : filters.entrySet()) {
            Object v = e.getValue(f.getKey());
            if (!f.getValue().contains(v != null ? v.toString() : "")) return false;
        }
        return true;
    }

    public static boolean passesQuickFilter(
            RowFilter.Entry<?, ?> e, Map<Integer, String> filters) {
        for (Map.Entry<Integer, String> f : filters.entrySet()) {
            Object v = e.getValue(f.getKey());
            if (!QuickFilterParser.matches(f.getValue(), v != null ? v.toString() : "")) return false;
        }
        return true;
    }

    /**
     * Combines drop filters (column value checkboxes) and quick filters (expression
     * text fields) into a single decision. In AND mode every active filter, of
     * either kind, must match. In OR mode a row is included as soon as any single
     * active filter, of either kind, matches — so OR spans both filter rows rather
     * than only the quick-filter row.
     */
    public static boolean passesFilters(
            RowFilter.Entry<?, ?> e,
            Map<Integer, Set<String>> dropFilters,
            Map<Integer, String> quickFilters,
            boolean orMode) {
        if (!orMode) return passesDropFilter(e, dropFilters) && passesQuickFilter(e, quickFilters);
        if (dropFilters.isEmpty() && quickFilters.isEmpty()) return true;
        for (Map.Entry<Integer, Set<String>> f : dropFilters.entrySet()) {
            Object v = e.getValue(f.getKey());
            if (f.getValue().contains(v != null ? v.toString() : "")) return true;
        }
        for (Map.Entry<Integer, String> f : quickFilters.entrySet()) {
            Object v = e.getValue(f.getKey());
            if (QuickFilterParser.matches(f.getValue(), v != null ? v.toString() : "")) return true;
        }
        return false;
    }

    public static void convertDropToQuickFilter(ColumnFilterRow columnFilterRow, FinFilterRow finFilterRow) {
        Map<Integer, Set<String>> dropFilters = columnFilterRow.getActiveFilters();
        if (dropFilters.isEmpty()) return;
        Map<Integer, String> expressions = new HashMap<>();
        for (Map.Entry<Integer, Set<String>> entry : dropFilters.entrySet()) {
            String expr = entry.getValue().stream()
                .map(v -> "=" + v)
                .collect(Collectors.joining("+"));
            expressions.put(entry.getKey(), expr);
        }
        columnFilterRow.clearAll();
        finFilterRow.setFilterByModelIndex(expressions);
    }

    public static <M extends AbstractTableModel> void applyRowFilter(
            TableRowSorter<M> sorter,
            Map<Integer, Set<String>> dropFilters,
            Map<Integer, String> quickFilters,
            boolean orMode) {
        if (dropFilters.isEmpty() && quickFilters.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(new RowFilter<>() {
                @Override
                public boolean include(Entry<? extends M, ? extends Integer> e) {
                    return passesFilters(e, dropFilters, quickFilters, orMode);
                }
            });
        }
    }

    public static String buildTableTsv(JTable table) {
        int cols = table.getColumnCount();
        int rows = table.getRowCount();
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < cols; c++) {
            if (c > 0) sb.append('\t');
            sb.append(table.getColumnName(c));
        }
        sb.append('\n');
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (c > 0) sb.append('\t');
                Object val = table.getValueAt(r, c);
                sb.append(val != null ? val.toString().replace("\t", " ").replace("\n", " ") : "");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public static JMenuItem makeCopyCellItem(JTable table) {
        JMenuItem item = new JMenuItem("Copy", ToolbarIcons.menuCopy());
        item.addActionListener(e -> {
            int row = table.getSelectedRow();
            int col = table.getSelectedColumn();
            if (row < 0 || col < 0 || table.getColumnCount() == 0) return;
            Object val = table.getValueAt(row, col);
            Toolkit.getDefaultToolkit().getSystemClipboard()
                   .setContents(new StringSelection(val != null ? val.toString() : ""), null);
        });
        return item;
    }

    public static boolean hasCellSelection(JTable table) {
        return table.getSelectedRow() >= 0 && table.getSelectedColumn() >= 0
                && table.getColumnCount() > 0;
    }

    public static void copyTableToClipboard(JTable table) {
        if (table.getRowCount() == 0) return;
        Toolkit.getDefaultToolkit().getSystemClipboard()
               .setContents(new StringSelection(buildTableTsv(table)), null);
    }

    public static void installFilterRowsInScrollPane(JScrollPane scrollPane, JTableHeader tableHeader,
                                                     JComponent filterRow1, JComponent filterRow2) {
        if (scrollPane == null) return;
        JPanel filterRows = new JPanel(new GridLayout(2, 1, 0, 1));
        filterRows.add(filterRow1);
        filterRows.add(filterRow2);
        JPanel colHdr = new JPanel(new BorderLayout());
        colHdr.add(tableHeader,  BorderLayout.NORTH);
        colHdr.add(filterRows,   BorderLayout.SOUTH);
        scrollPane.setColumnHeaderView(colHdr);
    }

    public static void addSortMenuItems(JPopupMenu popup, TableRowSorter<?> sorter, int modelCol) {
        JMenuItem sortAsc  = new JMenuItem("Sort ascending",  ToolbarIcons.menuSortAsc());
        sortAsc.addActionListener(ae -> sorter.setSortKeys(Collections.singletonList(
                new RowSorter.SortKey(modelCol, SortOrder.ASCENDING))));
        JMenuItem sortDesc = new JMenuItem("Sort descending", ToolbarIcons.menuSortDesc());
        sortDesc.addActionListener(ae -> sorter.setSortKeys(Collections.singletonList(
                new RowSorter.SortKey(modelCol, SortOrder.DESCENDING))));
        JMenuItem sortReset = new JMenuItem("Clear sort", ToolbarIcons.menuSortClear());
        sortReset.addActionListener(ae -> sorter.setSortKeys(null));
        popup.add(sortAsc);
        popup.add(sortDesc);
        popup.add(sortReset);
    }

    public static void showComboDeletePopup(MouseEvent e, JComboBox<String> combo, Consumer<String> onDelete) {
        if (!e.isPopupTrigger()) return;
        String selected = (String) combo.getSelectedItem();
        if (selected == null || selected.isEmpty()) return;
        JPopupMenu popup = new JPopupMenu();
        JMenuItem deleteItem = new JMenuItem("Delete “" + selected + "”", ToolbarIcons.menuDelete());
        deleteItem.addActionListener(ae -> onDelete.accept(selected));
        popup.add(deleteItem);
        popup.show(e.getComponent(), e.getX(), e.getY());
    }

    public static JLabel makeMatchLabel() {
        JLabel label = new JLabel("  ");
        label.setFont(label.getFont().deriveFont(11f));
        return label;
    }

    public static JButton makeClearButton(JTextField field) {
        JButton btn = new JButton("✕");
        btn.setMargin(new Insets(1, 4, 1, 4));
        btn.setFont(btn.getFont().deriveFont(11f));
        btn.setToolTipText("Clear search");
        btn.addActionListener(e -> field.setText(""));
        return btn;
    }
}