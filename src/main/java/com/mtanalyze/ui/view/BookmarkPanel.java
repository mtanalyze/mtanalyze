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

import com.mtanalyze.bookmark.BookmarkManager;
import com.mtanalyze.bookmark.Bookmark;
import com.mtanalyze.ui.BookmarkTableRenderer;
import com.mtanalyze.ui.EditMenuContributor;
import com.mtanalyze.ui.ToolbarIcons;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;

public class BookmarkPanel extends RoundedPanel implements EditMenuContributor {

    private static final String[] COLUMNS = {"File", "ISIN", "Note"};

    private final transient BookmarkManager    manager;
    private final transient Consumer<Bookmark> onNavigate;
    private final transient Runnable           onCollapse;
    private final DefaultTableModel  tableModel;
    private final JTable             table;

    public BookmarkPanel(BookmarkManager manager, Consumer<Bookmark> onNavigate, Runnable onCollapse) {
        super(new BorderLayout(0, 0));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        this.manager    = manager;
        this.onNavigate = onNavigate;
        this.onCollapse = onCollapse;

        tableModel = buildTableModel();
        table      = buildTable();
        bindDeleteKey();
        add(new JScrollPane(table), BorderLayout.CENTER);
        refresh();
    }

    public void refresh() {
        tableModel.setRowCount(0);
        for (Bookmark b : manager.items())
            tableModel.addRow(new Object[]{b.fileDisplayName(), b.isin(), b.note()});
    }

    // -----------------------------------------------------------------------

    private DefaultTableModel buildTableModel() {
        DefaultTableModel m = new DefaultTableModel(COLUMNS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == 2; }
        };
        m.addTableModelListener(e -> {
            if (e.getColumn() != 2) return;
            int row = e.getFirstRow();
            if (row >= 0 && row < manager.items().size()) {
                String note = (String) m.getValueAt(row, 2);
                manager.update(row, manager.items().get(row).withNote(note != null ? note : ""));
            }
        });
        return m;
    }

    private JTable buildTable() {
        JTable t = new JTable(tableModel);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setFillsViewportHeight(true);
        t.setRowHeight(22);
        int[] widths = {160, 90, 200};
        for (int i = 0; i < widths.length; i++)
            t.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        t.setDefaultRenderer(Object.class, new BookmarkTableRenderer(manager));
        t.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) navigateToSelected();
            }
            @Override public void mousePressed (MouseEvent e) { maybeShowPopup(e); }
            @Override public void mouseReleased(MouseEvent e) { maybeShowPopup(e); }
            private void maybeShowPopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int row = t.rowAtPoint(e.getPoint());
                if (row >= 0) t.setRowSelectionInterval(row, row);
                getPopupMenu().show(e.getComponent(), e.getX(), e.getY());
            }
        });
        return t;
    }

    private void navigateToSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= manager.items().size()) return;
        onNavigate.accept(manager.items().get(row));
        onCollapse.run();
    }

    @Override
    public JPopupMenu getPopupMenu() {
        boolean hasSelection = table.getSelectedRow() >= 0;
        JPopupMenu popup = new JPopupMenu();
        popup.add(makeCopyMenuItem(hasSelection));
        JMenuItem deleteItem = new JMenuItem("Delete", ToolbarIcons.menuDelete());
        deleteItem.setEnabled(hasSelection);
        deleteItem.addActionListener(ae -> deleteSelected());
        popup.add(deleteItem);
        return popup;
    }

    private JMenuItem makeCopyMenuItem(boolean hasSelection) {
        JMenuItem copyItem = new JMenuItem("Copy", ToolbarIcons.menuCopy());
        copyItem.setEnabled(hasSelection && table.getSelectedColumn() >= 0);
        copyItem.addActionListener(ae -> {
            int row = table.getSelectedRow();
            int col = table.getSelectedColumn();
            if (row < 0 || col < 0) return;
            Object val = table.getValueAt(row, col);
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(val != null ? val.toString() : ""), null);
        });
        return copyItem;
    }

    private void deleteSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= manager.items().size()) return;
        manager.remove(row);
        refresh();
    }

    private void bindDeleteKey() {
        table.getInputMap(JComponent.WHEN_FOCUSED)
             .put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteBookmark");
        table.getActionMap().put("deleteBookmark", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { deleteSelected(); }
        });
    }

}