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

import com.mtanalyze.parser.HintDictionary;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * Dialog for choosing which FIN table columns are visible and in which order.
 * Shows two lists — "Available" (hidden) and "Visible" (shown, in display
 * order) — with buttons to move columns between them and to reorder the
 * visible list (also reorderable by dragging).
 */
public final class ColumnChooser {

    private ColumnChooser() {}

    /**
     * Zeigt den Spalten-Auswahl-Dialog.
     * @param savePrefs   wird nach OK aufgerufen, um Prefs zu persistieren
     * @param rebuildTable wird nach OK aufgerufen, um die Tabelle neu aufzubauen
     */
    public static void show(java.awt.Window owner, List<ColumnDef> cols,
                     Runnable savePrefs, Runnable rebuildTable, HintDictionary dict) {
        if (cols.isEmpty()) {
            JOptionPane.showMessageDialog(owner,
                "Please load a SWIFT file first.",
                "No Data", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        final Map<ColumnDef, Integer> originalIndex = new IdentityHashMap<>();
        for (int i = 0; i < cols.size(); i++) originalIndex.put(cols.get(i), i);

        final List<ColumnDef> allAvailable = new ArrayList<>();
        final DefaultListModel<ColumnDef> availableModel = new DefaultListModel<>();
        final DefaultListModel<ColumnDef> visibleModel   = new DefaultListModel<>();
        for (ColumnDef cd : cols) {
            if (cd.isVisible()) visibleModel.addElement(cd);
            else                allAvailable.add(cd);
        }

        JTextField filterField = new JTextField();
        filterField.setToolTipText("Tag, qualifier or segment — filters the Available list instantly");

        JList<ColumnDef> availableList = createColumnList(availableModel, dict);
        JList<ColumnDef> visibleList   = createColumnList(visibleModel, dict);
        enableDragReorder(visibleList, visibleModel);

        Runnable refreshAvailable = () -> {
            String filter = filterField.getText().trim().toLowerCase(Locale.ROOT);
            availableModel.clear();
            for (ColumnDef cd : allAvailable) {
                if (filter.isEmpty() || matchesFilter(cd, filter)) availableModel.addElement(cd);
            }
        };
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { refreshAvailable.run(); }
            @Override public void removeUpdate(DocumentEvent e)  { refreshAvailable.run(); }
            @Override public void changedUpdate(DocumentEvent e) { refreshAvailable.run(); }
        });
        refreshAvailable.run();

        Runnable toVisible = () -> {
            for (ColumnDef cd : availableList.getSelectedValuesList()) {
                allAvailable.remove(cd);
                visibleModel.addElement(cd);
            }
            refreshAvailable.run();
        };
        Runnable toAvailable = () -> {
            for (ColumnDef cd : visibleList.getSelectedValuesList()) {
                visibleModel.removeElement(cd);
                insertSorted(allAvailable, cd, originalIndex);
            }
            refreshAvailable.run();
        };

        JButton toVisibleBtn   = iconButton(ToolbarIcons.arrowRight(), "Show selected column(s)");
        JButton toAvailableBtn = iconButton(ToolbarIcons.arrowLeft(),  "Hide selected column(s)");
        toVisibleBtn.addActionListener(e -> toVisible.run());
        toAvailableBtn.addActionListener(e -> toAvailable.run());
        availableList.addMouseListener(doubleClickListener(toVisible));
        visibleList.addMouseListener(doubleClickListener(toAvailable));

        JButton moveUpBtn   = iconButton(ToolbarIcons.arrowUp(),   "Move up");
        JButton moveDownBtn = iconButton(ToolbarIcons.arrowDown(), "Move down");
        moveUpBtn  .addActionListener(e -> moveSelectionBy(visibleList, visibleModel, -1));
        moveDownBtn.addActionListener(e -> moveSelectionBy(visibleList, visibleModel,  1));

        JButton allOn  = new JButton("Show all »");
        JButton allOff = new JButton("« Hide all");
        allOn.addActionListener(e -> {
            for (ColumnDef cd : new ArrayList<>(allAvailable)) visibleModel.addElement(cd);
            allAvailable.clear();
            refreshAvailable.run();
        });
        allOff.addActionListener(e -> {
            for (int i = 0; i < visibleModel.size(); i++) insertSorted(allAvailable, visibleModel.get(i), originalIndex);
            visibleModel.clear();
            refreshAvailable.run();
        });

        JPanel transferPanel = new JPanel();
        transferPanel.setLayout(new BoxLayout(transferPanel, BoxLayout.Y_AXIS));
        transferPanel.add(Box.createVerticalGlue());
        transferPanel.add(toVisibleBtn);
        transferPanel.add(Box.createVerticalStrut(6));
        transferPanel.add(toAvailableBtn);
        transferPanel.add(Box.createVerticalGlue());

        JPanel orderPanel = new JPanel();
        orderPanel.setLayout(new BoxLayout(orderPanel, BoxLayout.Y_AXIS));
        orderPanel.add(Box.createVerticalGlue());
        orderPanel.add(moveUpBtn);
        orderPanel.add(Box.createVerticalStrut(6));
        orderPanel.add(moveDownBtn);
        orderPanel.add(Box.createVerticalGlue());

        JPanel availablePanel = titledListPanel("Available", availableList, allOn);
        JPanel visiblePanel   = titledListPanel("Visible",   visibleList,   allOff);

        JPanel listsRow = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0; gbc.fill = GridBagConstraints.BOTH;

        gbc.gridx = 0; gbc.weightx = 1; gbc.weighty = 1; gbc.insets = new Insets(0, 0, 0, 0);
        listsRow.add(availablePanel, gbc);

        gbc.gridx = 1; gbc.weightx = 0; gbc.weighty = 0; gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 8, 0, 8);
        listsRow.add(transferPanel, gbc);

        gbc.gridx = 2; gbc.weightx = 1; gbc.weighty = 1; gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 0, 0);
        listsRow.add(visiblePanel, gbc);

        gbc.gridx = 3; gbc.weightx = 0; gbc.weighty = 0; gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 8, 0, 0);
        listsRow.add(orderPanel, gbc);

        listsRow.setPreferredSize(new Dimension(680, 420));

        JPanel filterRow = new JPanel(new BorderLayout(6, 0));
        filterRow.setBorder(new EmptyBorder(0, 0, 6, 0));
        filterRow.add(new JLabel("Filter: "), BorderLayout.WEST);
        filterRow.add(filterField,             BorderLayout.CENTER);

        JPanel dlgContent = new JPanel(new BorderLayout(4, 4));
        dlgContent.setBorder(new EmptyBorder(8, 8, 4, 8));
        dlgContent.add(filterRow, BorderLayout.NORTH);
        dlgContent.add(listsRow,  BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(owner, dlgContent,
            "Select FIN Table Columns",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            List<ColumnDef> ordered = new ArrayList<>(cols.size());
            for (int i = 0; i < visibleModel.size(); i++) {
                ColumnDef cd = visibleModel.get(i);
                cd.setVisible(true);
                ordered.add(cd);
            }
            for (ColumnDef cd : allAvailable) {
                cd.setVisible(false);
                ordered.add(cd);
            }
            cols.clear();
            cols.addAll(ordered);
            savePrefs.run();
            rebuildTable.run();
        }
    }

    // -----------------------------------------------------------------------
    // List construction
    // -----------------------------------------------------------------------

    private static JList<ColumnDef> createColumnList(DefaultListModel<ColumnDef> model, HintDictionary dict) {
        JList<ColumnDef> list = new JList<>(model) {
            @Override public String getToolTipText(MouseEvent e) {
                int idx = locationToIndex(e.getPoint());
                if (idx < 0 || !getCellBounds(idx, idx).contains(e.getPoint())) return null;
                return buildColumnTooltip(getModel().getElementAt(idx), dict);
            }
        };
        list.setCellRenderer(columnListRenderer(dict));
        list.setVisibleRowCount(-1);
        ToolTipManager.sharedInstance().registerComponent(list);
        return list;
    }

    private static ListCellRenderer<ColumnDef> columnListRenderer(HintDictionary dict) {
        DefaultListCellRenderer base = new DefaultListCellRenderer();
        return (list, value, index, isSelected, cellHasFocus) -> {
            JLabel c = (JLabel) base.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            c.setText(value.label);
            c.setBorder(new EmptyBorder(3, 6, 3, 6));
            return c;
        };
    }

    private static JPanel titledListPanel(String title, JList<ColumnDef> list, JButton bulkButton) {
        JScrollPane scroll = new JScrollPane(list);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(titleLabel, BorderLayout.WEST);
        header.add(bulkButton, BorderLayout.EAST);

        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(header, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private static JButton iconButton(Icon icon, String tooltip) {
        JButton btn = new JButton(icon);
        btn.setToolTipText(tooltip);
        btn.setFocusable(false);
        Dimension size = new Dimension(30, 30);
        btn.setPreferredSize(size);
        btn.setMaximumSize(size);
        return btn;
    }

    private static MouseAdapter doubleClickListener(Runnable action) {
        return new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) action.run();
            }
        };
    }

    // -----------------------------------------------------------------------
    // Move / reorder logic
    // -----------------------------------------------------------------------

    /** Inserts {@code cd} into {@code list}, keeping it sorted by original column order. */
    private static void insertSorted(List<ColumnDef> list, ColumnDef cd, Map<ColumnDef, Integer> originalIndex) {
        int idx = originalIndex.get(cd);
        int pos = 0;
        while (pos < list.size() && originalIndex.get(list.get(pos)) < idx) pos++;
        list.add(pos, cd);
    }

    private static void moveSelectionBy(JList<ColumnDef> list, DefaultListModel<ColumnDef> model, int delta) {
        int[] sel = list.getSelectedIndices();
        if (sel.length == 0) return;
        int n = model.size();
        if (delta < 0) {
            for (int i : sel) {
                if (i == 0) continue;
                swap(model, i, i - 1);
            }
        } else {
            for (int i = sel.length - 1; i >= 0; i--) {
                int idx = sel[i];
                if (idx >= n - 1) continue;
                swap(model, idx, idx + 1);
            }
        }
        int[] newSel = new int[sel.length];
        for (int i = 0; i < sel.length; i++) {
            int idx = sel[i];
            if (delta < 0 && idx > 0)          newSel[i] = idx - 1;
            else if (delta > 0 && idx < n - 1) newSel[i] = idx + 1;
            else                                newSel[i] = idx;
        }
        list.setSelectedIndices(newSel);
    }

    private static void swap(DefaultListModel<ColumnDef> model, int i, int j) {
        ColumnDef tmp = model.get(i);
        model.set(i, model.get(j));
        model.set(j, tmp);
    }

    /** Lets the given list's rows be reordered by dragging them within the list. */
    private static void enableDragReorder(JList<ColumnDef> list, DefaultListModel<ColumnDef> model) {
        list.setDragEnabled(true);
        list.setDropMode(DropMode.INSERT);
        list.setTransferHandler(new TransferHandler() {
            private int[] draggedIndices;

            @Override public int getSourceActions(JComponent c) { return MOVE; }

            @Override protected Transferable createTransferable(JComponent c) {
                draggedIndices = list.getSelectedIndices();
                return new StringSelection("columns");
            }

            @Override public boolean canImport(TransferSupport support) {
                return support.isDrop() && support.getComponent() == list;
            }

            @Override public boolean importData(TransferSupport support) {
                if (!canImport(support) || draggedIndices == null || draggedIndices.length == 0) return false;
                int insertIndex = ((JList.DropLocation) support.getDropLocation()).getIndex();

                List<ColumnDef> moving = new ArrayList<>();
                for (int i : draggedIndices) moving.add(model.get(i));

                int[] sorted = draggedIndices.clone();
                Arrays.sort(sorted);
                for (int i = sorted.length - 1; i >= 0; i--) {
                    if (sorted[i] < insertIndex) insertIndex--;
                    model.remove(sorted[i]);
                }
                for (int i = 0; i < moving.size(); i++) model.add(insertIndex + i, moving.get(i));
                list.setSelectionInterval(insertIndex, insertIndex + moving.size() - 1);
                return true;
            }

            @Override protected void exportDone(JComponent c, Transferable data, int action) {
                draggedIndices = null;
            }
        });
    }

    // -----------------------------------------------------------------------
    // Filtering
    // -----------------------------------------------------------------------

    private static boolean matchesFilter(ColumnDef cd, String filter) {
        return cd.tagName.toLowerCase(Locale.ROOT).contains(filter)
            || cd.qualifier.toLowerCase(Locale.ROOT).contains(filter)
            || cd.label.toLowerCase(Locale.ROOT).contains(filter)
            || cd.seqLabel.toLowerCase(Locale.ROOT).contains(filter)
            || cd.seqDisplay.toLowerCase(Locale.ROOT).contains(filter);
    }

    // -----------------------------------------------------------------------
    // Dictionary labels and tooltips
    // -----------------------------------------------------------------------

    private static String buildColumnTooltip(ColumnDef cd, HintDictionary dict) {
        String tagDesc  = dict.tagDescription(cd.tagName);
        String qualDesc = cd.qualifier.isEmpty() ? null : dict.qualifierDescription(cd.qualifier);
        if (tagDesc == null && qualDesc == null) return null;
        if (qualDesc == null) return tagDesc;
        if (tagDesc  == null) return qualDesc;
        return tagDesc + " | " + qualDesc;
    }
}
