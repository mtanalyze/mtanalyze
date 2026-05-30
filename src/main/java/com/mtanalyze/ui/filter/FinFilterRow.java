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
import com.mtanalyze.ui.FrameLayout;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * Quick Filter row – one yellow text field per visible column, placed directly
 * below the table header inside the scroll-pane column-header viewport.
 *
 * <p>Filter is activated by pressing Tab (or Enter, or moving focus away).
 * Supported operators: {@code =  !=  !  <>  <  >  <=  >=  ^  !^  $  %  !%}
 * and range {@code lo-hi}. Multiple terms joined with {@code +} are OR'd.
 * Leaving a field empty removes the filter for that column.
 *
 * @see QuickFilterParser
 */
public class FinFilterRow extends AbstractFilterRow {

    // colours resolved dynamically – see applyFieldColors()
    private static final Color FILTER_BG_LIGHT = new Color(255, 255, 180);
    private static final Color FILTER_BG_DARK = new Color(65, 60, 5);
    private static final Color BORDER_CLR_LIGHT = new Color(180, 175, 80);
    private static final Color BORDER_CLR_DARK = new Color(110, 105, 25);
    private static final String TOOLTIP =
            "<html><b>Quick Filter</b><br>"
                    + "=val &nbsp; equal &nbsp;|&nbsp; = &nbsp; empty<br>"
                    + "!=val &nbsp; not equal &nbsp;|&nbsp; != &nbsp; not empty<br>"
                    + "^val &nbsp; begins with &nbsp;|&nbsp; $val &nbsp; ends with<br>"
                    + "%val &nbsp; contains &nbsp;|&nbsp; !%val &nbsp; does not contain<br>"
                    + "&lt;val &nbsp; less &nbsp;|&nbsp; &gt;val &nbsp; greater &nbsp;|"
                    + "&nbsp; lo-hi &nbsp; between<br>"
                    + "Use <b>+</b> to combine values (OR): &nbsp; =EUR+GBP<br>"
                    + "Press <b>Tab</b> or <b>Enter</b> to apply.</html>";

    private final transient Runnable onFilterChanged;
    private final transient Runnable onSaveRequested;
    /**
     * fields.get(modelIdx) is the filter field for model column modelIdx.
     */
    private final List<JTextField> fields = new ArrayList<>();
    private transient List<ColumnDef> visibleCols = new ArrayList<>();
    private boolean orMode = false;

    public FinFilterRow(Runnable onFilterChanged, Runnable onSaveRequested) {
        this.onFilterChanged  = onFilterChanged;
        this.onSaveRequested  = onSaveRequested;
        setLayout(null);
        setPreferredSize(new Dimension(0, ROW_H));
    }

    public boolean isOrMode() { return orMode; }

    public void setOrMode(boolean or) {
        this.orMode = or;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public void rebuild(TableColumnModel colModel,
                 List<ColumnDef> cols) {   // rowData unused but kept for API symmetry
        if (columnModel != null)
            columnModel.removeColumnModelListener(colModelListener);
        columnModel = colModel;
        colModel.addColumnModelListener(colModelListener);

        this.visibleCols = new ArrayList<>(cols);
        removeAll();
        fields.clear();

        for (int i = 0; i < cols.size(); i++) {
            JTextField field = buildField();
            fields.add(field);
            add(field);
        }

        refreshLayout();
        revalidate();
        repaint();
    }

    /** Rebuilds with the given column count; no {@link ColumnDef} metadata needed. */
    public void rebuild(TableColumnModel colModel, int colCount) {
        if (columnModel != null)
            columnModel.removeColumnModelListener(colModelListener);
        columnModel = colModel;
        colModel.addColumnModelListener(colModelListener);

        this.visibleCols = new ArrayList<>();
        removeAll();
        fields.clear();

        for (int i = 0; i < colCount; i++) {
            JTextField field = buildField();
            fields.add(field);
            add(field);
        }

        refreshLayout();
        revalidate();
        repaint();
    }

    public void clearAll() {
        fields.forEach(f -> f.setText(""));
        onFilterChanged.run();
    }

    /**
     * Returns model-column-index → filter expression for every column
     * whose field is non-empty.
     */
    public Map<Integer, String> getActiveFilters() {
        Map<Integer, String> result = new HashMap<>();
        for (int modelIdx = 0; modelIdx < fields.size(); modelIdx++) {
            String text = fields.get(modelIdx).getText().trim();
            if (!text.isEmpty()) result.put(modelIdx, text);
        }
        return result;
    }

    /** Returns storageKey(ColumnDef.key) → expression for every non-empty filter field. */
    public Map<String, String> getActiveFiltersByKey() {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            String text = fields.get(i).getText().trim();
            if (!text.isEmpty() && i < visibleCols.size())
                result.put(storageKey(visibleCols.get(i).key), text);
        }
        return result;
    }

    /** Sets filter fields from a model-column-index → expression map and triggers the filter. */
    public void setFilterByModelIndex(Map<Integer, String> filters) {
        for (Map.Entry<Integer, String> entry : filters.entrySet()) {
            int modelIdx = entry.getKey();
            if (modelIdx < fields.size())
                fields.get(modelIdx).setText(entry.getValue());
        }
        onFilterChanged.run();
    }

    /** Clears all fields, then sets each field whose column qualifier is in {@code qualifiers}
     *  to {@code "=" + value}, and triggers the filter. */
    public void setFilterByQualifiers(List<String> qualifiers, String value) {
        fields.forEach(f -> f.setText(""));
        String expr = value.isEmpty() ? "" : "=" + value;
        for (int i = 0; i < fields.size() && i < visibleCols.size(); i++) {
            if (qualifiers.contains(visibleCols.get(i).qualifier))
                fields.get(i).setText(expr);
        }
        onFilterChanged.run();
    }

    /** Appends {@code value} to every filter field whose column qualifier equals {@code qualifier},
     *  joining with {@code +} if the field is non-empty. */
    public void appendToFilterByQualifier(String qualifier, String value) {
        for (int i = 0; i < fields.size() && i < visibleCols.size(); i++) {
            if (visibleCols.get(i).qualifier.equals(qualifier)) {
                String current = fields.get(i).getText().trim();
                fields.get(i).setText(current.isEmpty() ? value : current + "+" + value);
            }
        }
        onFilterChanged.run();
    }

    /** Appends {@code value} to the filter field at {@code modelIndex}, joining with {@code +} if non-empty. */
    public void appendToFilter(int modelIndex, String value) {
        if (modelIndex < 0 || modelIndex >= fields.size()) return;
        JTextField field = fields.get(modelIndex);
        String current = field.getText().trim();
        field.setText(current.isEmpty() ? value : current + "+" + value);
        onFilterChanged.run();
    }

    /** Clears all fields, then sets each field whose column qualifier is a key in {@code qualifierToExpr}
     *  to the corresponding expression, and triggers the filter. */
    public void setFilterByQualifierMap(Map<String, String> qualifierToExpr) {
        fields.forEach(f -> f.setText(""));
        for (int i = 0; i < fields.size() && i < visibleCols.size(); i++) {
            String expr = qualifierToExpr.get(visibleCols.get(i).qualifier);
            if (expr != null) fields.get(i).setText(expr);
        }
        onFilterChanged.run();
    }

    /** Sets filter fields from a storageKey → expression map and triggers the filter. */
    public void applyFiltersByKey(Map<String, String> filters) {
        for (int i = 0; i < fields.size(); i++) {
            String key = i < visibleCols.size() ? storageKey(visibleCols.get(i).key) : null;
            fields.get(i).setText(key != null ? filters.getOrDefault(key, "") : "");
        }
        onFilterChanged.run();
    }

    /** ColumnDef.key uses tabs internally; replace them so the storage format stays unambiguous. */
    private static String storageKey(String colKey) {
        return colKey.replace('\t', '|');
    }

    // -----------------------------------------------------------------------
    // Layout
    // -----------------------------------------------------------------------

    @Override
    public void refreshLayout() {
        if (columnModel == null) return;
        int x = 0;
        for (int viewCol = 0; viewCol < columnModel.getColumnCount(); viewCol++) {
            TableColumn tc = columnModel.getColumn(viewCol);
            int modelIdx = tc.getModelIndex();
            int w = effectiveWidth(tc);
            if (modelIdx < fields.size())
                fields.get(modelIdx).setBounds(x, 1, w, ROW_H - 2);
            x += w;
        }
        setPreferredSize(new Dimension(x, ROW_H));
    }

    // -----------------------------------------------------------------------
    // Field construction
    // -----------------------------------------------------------------------

    /**
     * Re-applies theme-aware colours on every L&F switch.
     */
    private static void applyFieldColors(JTextField f) {
        boolean dark = isDarkTheme();
        f.setBackground(dark ? FILTER_BG_DARK : FILTER_BG_LIGHT);
        f.setBorder(new LineBorder(dark ? BORDER_CLR_DARK : BORDER_CLR_LIGHT, 1));
        Color fg = UIManager.getColor("TextField.foreground");
        if (fg != null) f.setForeground(fg);
    }

    private static boolean isDarkTheme() {
        Color bg = UIManager.getColor("TextField.background");
        if (bg == null) return false;
        return (bg.getRed() + bg.getGreen() + bg.getBlue()) / 3 < 128;
    }

    private JTextField buildField() {
        JTextField field = new JTextField() {
            @Override
            public void updateUI() {
                super.updateUI();
                applyFieldColors(this);
            }
        };
        field.setFont(field.getFont().deriveFont(10f));
        field.setToolTipText(TOOLTIP);

        // Enter key → apply immediately
        field.addActionListener(e -> onFilterChanged.run());


        // Focus lost → apply (e.g. user clicks into the table)
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                onFilterChanged.run();
            }
        });

        field.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed (MouseEvent e) { if (e.isPopupTrigger()) showFieldPopup(e); }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) showFieldPopup(e); }
        });

        wireTabKey(field);
        return field;
    }

    private void showFieldPopup(MouseEvent e) {
        JTextField src = (JTextField) e.getSource();
        JPopupMenu popup = new JPopupMenu();

        boolean hasSelection = src.getSelectedText() != null && !src.getSelectedText().isEmpty();
        JMenuItem copyItem  = new JMenuItem("Copy");
        JMenuItem cutItem   = new JMenuItem("Cut");
        JMenuItem pasteItem = new JMenuItem("Paste");
        FrameLayout.wireTextMenuItems(src, hasSelection, copyItem, cutItem, pasteItem, popup::add);
        popup.addSeparator();

        if (onSaveRequested != null) {
            JMenuItem saveItem = new JMenuItem("Save Quick Filter…");
            saveItem.addActionListener(ae -> onSaveRequested.run());
            popup.add(saveItem);
            popup.addSeparator();
        }
        JMenuItem clearItem = new JMenuItem("Clear Quick Filter");
        clearItem.addActionListener(ae -> clearAll());
        popup.add(clearItem);
        popup.show(e.getComponent(), e.getX(), e.getY());
    }

    /**
     * Overrides Tab / Shift+Tab so the filter fires before focus moves.
     */
    private void wireTabKey(JTextField field) {
        bindTabAction(field, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0),
                "quickFilter.tab", field::transferFocus);
        bindTabAction(field, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK),
                "quickFilter.shiftTab", field::transferFocusBackward);
    }

    private void bindTabAction(JTextField field, KeyStroke key, String name, Runnable focusOp) {
        field.getInputMap(JComponent.WHEN_FOCUSED).put(key, name);
        field.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                onFilterChanged.run();
                focusOp.run();
            }
        });
    }

}