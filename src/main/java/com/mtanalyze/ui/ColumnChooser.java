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

        ChooserState st = buildState(cols);
        JPanel dlgContent = buildDialogContent(st, dict);
        JOptionPane pane = showDialog(owner, dlgContent);
        applyResult(pane, cols, st, savePrefs, rebuildTable);
    }

    /** Working lists/models shared by the dialog's widgets and the OK-result handling. */
    private static final class ChooserState {
        final Map<ColumnDef, Integer> originalIndex = new IdentityHashMap<>();
        final List<ColumnDef> allAvailable = new ArrayList<>();
        final DefaultListModel<ColumnDef> availableModel = new DefaultListModel<>();
        final DefaultListModel<ColumnDef> visibleModel   = new DefaultListModel<>();
    }

    private static ChooserState buildState(List<ColumnDef> cols) {
        ChooserState st = new ChooserState();
        for (int i = 0; i < cols.size(); i++) st.originalIndex.put(cols.get(i), i);
        for (ColumnDef cd : cols) {
            if (cd.isVisible()) st.visibleModel.addElement(cd);
            else                st.allAvailable.add(cd);
        }
        return st;
    }

    private static JPanel buildDialogContent(ChooserState st, HintDictionary dict) {
        JTextField filterField = new JTextField();
        filterField.setToolTipText("Tag, qualifier or segment — filters the Available list instantly");

        JList<ColumnDef> availableList = createColumnList(st.availableModel, dict);
        JList<ColumnDef> visibleList   = createColumnList(st.visibleModel, dict);
        enableDragReorder(visibleList, st.visibleModel);

        Runnable refreshAvailable = () -> refreshAvailable(st, filterField);
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { refreshAvailable.run(); }
            @Override public void removeUpdate(DocumentEvent e)  { refreshAvailable.run(); }
            @Override public void changedUpdate(DocumentEvent e) { refreshAvailable.run(); }
        });
        refreshAvailable.run();

        Runnable toVisible   = () -> moveToVisible(availableList, st, refreshAvailable);
        Runnable toAvailable = () -> moveToAvailable(visibleList, st, refreshAvailable);

        JButton toVisibleBtn   = iconButton(ToolbarIcons.arrowRight(), "Show selected column(s)");
        JButton toAvailableBtn = iconButton(ToolbarIcons.arrowLeft(),  "Hide selected column(s)");
        toVisibleBtn.addActionListener(e -> toVisible.run());
        toAvailableBtn.addActionListener(e -> toAvailable.run());
        availableList.addMouseListener(doubleClickListener(toVisible));
        visibleList.addMouseListener(doubleClickListener(toAvailable));

        JButton moveUpBtn   = iconButton(ToolbarIcons.arrowUp(),   "Move up");
        JButton moveDownBtn = iconButton(ToolbarIcons.arrowDown(), "Move down");
        moveUpBtn  .addActionListener(e -> moveSelectionBy(visibleList, st.visibleModel, -1));
        moveDownBtn.addActionListener(e -> moveSelectionBy(visibleList, st.visibleModel,  1));

        JButton allOn  = new JButton("Show all »");
        JButton allOff = new JButton("« Hide all");
        allOn.addActionListener(e -> showAll(st, refreshAvailable));
        allOff.addActionListener(e -> hideAll(st, refreshAvailable));

        JPanel transferPanel = buildVerticalButtonPanel(toVisibleBtn, toAvailableBtn);
        JPanel orderPanel    = buildVerticalButtonPanel(moveUpBtn, moveDownBtn);

        JPanel availablePanel = titledListPanel("Available", availableList, allOn);
        JPanel visiblePanel   = titledListPanel("Visible",   visibleList,   allOff);

        JPanel listsRow = buildListsRow(availablePanel, transferPanel, visiblePanel, orderPanel);

        JPanel filterRow = new JPanel(new BorderLayout(6, 0));
        filterRow.setBorder(new EmptyBorder(0, 0, 6, 0));
        filterRow.add(new JLabel("Filter: "), BorderLayout.WEST);
        filterRow.add(filterField,             BorderLayout.CENTER);

        JPanel dlgContent = new JPanel(new BorderLayout(4, 4));
        dlgContent.setBorder(new EmptyBorder(8, 8, 4, 8));
        dlgContent.add(filterRow, BorderLayout.NORTH);
        dlgContent.add(listsRow,  BorderLayout.CENTER);
        return dlgContent;
    }

    private static void refreshAvailable(ChooserState st, JTextField filterField) {
        String filter = filterField.getText().trim().toLowerCase(Locale.ROOT);
        st.availableModel.clear();
        for (ColumnDef cd : st.allAvailable) {
            if (filter.isEmpty() || matchesFilter(cd, filter)) st.availableModel.addElement(cd);
        }
    }

    private static void moveToVisible(JList<ColumnDef> availableList, ChooserState st, Runnable refreshAvailable) {
        for (ColumnDef cd : availableList.getSelectedValuesList()) {
            st.allAvailable.remove(cd);
            st.visibleModel.addElement(cd);
        }
        refreshAvailable.run();
    }

    private static void moveToAvailable(JList<ColumnDef> visibleList, ChooserState st, Runnable refreshAvailable) {
        for (ColumnDef cd : visibleList.getSelectedValuesList()) {
            st.visibleModel.removeElement(cd);
            insertSorted(st.allAvailable, cd, st.originalIndex);
        }
        refreshAvailable.run();
    }

    private static void showAll(ChooserState st, Runnable refreshAvailable) {
        for (ColumnDef cd : new ArrayList<>(st.allAvailable)) st.visibleModel.addElement(cd);
        st.allAvailable.clear();
        refreshAvailable.run();
    }

    private static void hideAll(ChooserState st, Runnable refreshAvailable) {
        for (int i = 0; i < st.visibleModel.size(); i++) {
            insertSorted(st.allAvailable, st.visibleModel.get(i), st.originalIndex);
        }
        st.visibleModel.clear();
        refreshAvailable.run();
    }

    private static JPanel buildVerticalButtonPanel(JButton first, JButton second) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(Box.createVerticalGlue());
        panel.add(first);
        panel.add(Box.createVerticalStrut(6));
        panel.add(second);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private static JPanel buildListsRow(JPanel availablePanel, JPanel transferPanel,
            JPanel visiblePanel, JPanel orderPanel) {
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
        return listsRow;
    }

    private static JOptionPane showDialog(Window owner, JPanel dlgContent) {
        JOptionPane pane = new JOptionPane(dlgContent, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        JDialog dialog = pane.createDialog(owner, "Select FIN Table Columns");
        centerOnOwnerScreen(dialog, owner);
        dialog.setVisible(true);
        dialog.dispose();
        return pane;
    }

    private static void applyResult(JOptionPane pane, List<ColumnDef> cols, ChooserState st,
            Runnable savePrefs, Runnable rebuildTable) {
        Object selectedValue = pane.getValue();
        int result = (selectedValue instanceof Integer integer) ? integer : JOptionPane.CLOSED_OPTION;
        if (result != JOptionPane.OK_OPTION) return;

        List<ColumnDef> ordered = new ArrayList<>(cols.size());
        for (int i = 0; i < st.visibleModel.size(); i++) {
            ColumnDef cd = st.visibleModel.get(i);
            cd.setVisible(true);
            ordered.add(cd);
        }
        for (ColumnDef cd : st.allAvailable) {
            cd.setVisible(false);
            ordered.add(cd);
        }
        cols.clear();
        cols.addAll(ordered);
        savePrefs.run();
        rebuildTable.run();
    }

    /**
     * Centers {@code dialog} on the monitor that actually holds {@code owner}, instead of
     * relying on {@link Window#setLocationRelativeTo}. That method falls back to the primary
     * screen whenever it can't resolve the owner's {@link GraphicsConfiguration} at the exact
     * moment it runs — which happens intermittently when this dialog is opened from a popup
     * menu action, causing it to appear on the wrong monitor in multi-monitor setups.
     */
    private static void centerOnOwnerScreen(Window dialog, Window owner) {
        GraphicsConfiguration gc = owner != null ? owner.getGraphicsConfiguration() : null;
        if (gc == null) {
            gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        }
        Rectangle screen = gc.getBounds();
        Dimension size = dialog.getSize();
        int x = screen.x + (screen.width  - size.width)  / 2;
        int y = screen.y + (screen.height - size.height) / 2;
        x = Math.max(screen.x, Math.min(x, screen.x + screen.width  - size.width));
        y = Math.max(screen.y, Math.min(y, screen.y + screen.height - size.height));
        dialog.setLocation(x, y);
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
        list.setCellRenderer(columnListRenderer());
        list.setVisibleRowCount(-1);
        ToolTipManager.sharedInstance().registerComponent(list);
        return list;
    }

    private static ListCellRenderer<ColumnDef> columnListRenderer() {
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
        if (delta < 0) swapUp(model, sel);
        else           swapDown(model, sel, n);
        list.setSelectedIndices(shiftedSelection(sel, delta, n));
    }

    private static void swapUp(DefaultListModel<ColumnDef> model, int[] sel) {
        for (int i : sel) {
            if (i == 0) continue;
            swap(model, i, i - 1);
        }
    }

    private static void swapDown(DefaultListModel<ColumnDef> model, int[] sel, int n) {
        for (int i = sel.length - 1; i >= 0; i--) {
            int idx = sel[i];
            if (idx >= n - 1) continue;
            swap(model, idx, idx + 1);
        }
    }

    private static int[] shiftedSelection(int[] sel, int delta, int n) {
        int[] newSel = new int[sel.length];
        for (int i = 0; i < sel.length; i++) {
            int idx = sel[i];
            if (delta < 0 && idx > 0)          newSel[i] = idx - 1;
            else if (delta > 0 && idx < n - 1) newSel[i] = idx + 1;
            else                                newSel[i] = idx;
        }
        return newSel;
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
