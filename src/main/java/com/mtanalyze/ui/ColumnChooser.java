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
import java.util.*;
import java.util.List;

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

        final Map<String, Boolean>    working  = buildWorkingMap(cols);
        final Map<JCheckBox, String>  cbToKey  = new LinkedHashMap<>();

        JPanel      contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        JScrollPane scroll       = new JScrollPane(contentPanel);
        scroll.setPreferredSize(new Dimension(520, 460));
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JTextField filterField = new JTextField();
        filterField.setToolTipText("Tag, qualifier or segment — list is filtered instantly");

        Runnable rebuild = () -> {
            String filter = filterField.getText().trim().toLowerCase(Locale.ROOT);
            Map<String, List<ColumnDef>> segments = filterToSegments(cols, filter);
            repopulatePanel(contentPanel, cbToKey, working, segments, filterField.getText().trim(), dict);
            SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0));
        };

        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { rebuild.run(); }
            @Override public void removeUpdate(DocumentEvent e)  { rebuild.run(); }
            @Override public void changedUpdate(DocumentEvent e) { rebuild.run(); }
        });
        rebuild.run();

        JButton allOn  = new JButton("Show all");
        JButton allOff = new JButton("Hide all");
        allOn .addActionListener(e -> cbToKey.forEach((cb, k) -> { cb.setSelected(true);  working.put(k, true);  }));
        allOff.addActionListener(e -> cbToKey.forEach((cb, k) -> { cb.setSelected(false); working.put(k, false); }));

        JPanel filterRow = new JPanel(new BorderLayout(6, 0));
        filterRow.setBorder(new EmptyBorder(0, 0, 4, 0));
        filterRow.add(new JLabel("Filter: "), BorderLayout.WEST);
        filterRow.add(filterField,             BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btnRow.add(allOn); btnRow.add(allOff);

        JPanel top = new JPanel(new BorderLayout(4, 4));
        top.setBorder(new EmptyBorder(0, 0, 6, 0));
        top.add(filterRow, BorderLayout.NORTH);
        top.add(btnRow,    BorderLayout.SOUTH);

        JPanel dlgContent = new JPanel(new BorderLayout(4, 4));
        dlgContent.setBorder(new EmptyBorder(8, 8, 4, 8));
        dlgContent.add(top,    BorderLayout.NORTH);
        dlgContent.add(scroll, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(owner, dlgContent,
            "Select FIN Table Columns",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            for (ColumnDef cd : cols) cd.setVisible(Boolean.TRUE.equals(working.get(cd.key)));
            savePrefs.run();
            rebuildTable.run();
        }
    }

    // -----------------------------------------------------------------------
    // Panel population
    // -----------------------------------------------------------------------

    private static void repopulatePanel(JPanel panel, Map<JCheckBox, String> cbToKey,
                                        Map<String, Boolean> working,
                                        Map<String, List<ColumnDef>> segments, String query,
                                        HintDictionary dict) {
        panel.removeAll();
        cbToKey.clear();
        if (segments.isEmpty()) {
            addNoMatchLabel(panel, query);
        } else {
            panel.setBorder(new EmptyBorder(4, 6, 4, 6));
            for (Map.Entry<String, List<ColumnDef>> e : segments.entrySet()) {
                panel.add(buildGroupPanel(e.getKey(), e.getValue(), working, cbToKey, dict));
                panel.add(Box.createVerticalStrut(5));
            }
        }
        panel.revalidate();
        panel.repaint();
    }

    private static void addNoMatchLabel(JPanel panel, String query) {
        JLabel none = new JLabel("  No matches for \"" + query + "\"");
        none.setForeground(Color.GRAY);
        none.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(Box.createVerticalStrut(12));
        panel.add(none);
    }

    private static JPanel buildGroupPanel(String name, List<ColumnDef> cols,
                                          Map<String, Boolean> working,
                                          Map<JCheckBox, String> cbToKey,
                                          HintDictionary dict) {
        JPanel grp = new JPanel();
        grp.setLayout(new BoxLayout(grp, BoxLayout.Y_AXIS));
        grp.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), name,
            TitledBorder.LEFT, TitledBorder.TOP,
            grp.getFont().deriveFont(Font.BOLD)));
        grp.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (ColumnDef cd : cols) addCheckBox(grp, cd, working, cbToKey, dict);
        return grp;
    }

    private static void addCheckBox(JPanel grp, ColumnDef cd,
                                    Map<String, Boolean> working,
                                    Map<JCheckBox, String> cbToKey,
                                    HintDictionary dict) {
        JCheckBox cb = new JCheckBox(buildCheckBoxLabel(cd, dict), Boolean.TRUE.equals(working.get(cd.key)));
        cb.setToolTipText(buildColumnTooltip(cd, dict));
        cb.setAlignmentX(Component.LEFT_ALIGNMENT);
        cb.addActionListener(ev -> working.put(cd.key, cb.isSelected()));
        grp.add(cb);
        cbToKey.put(cb, cd.key);
    }

    // -----------------------------------------------------------------------
    // Filtering
    // -----------------------------------------------------------------------

    private static Map<String, Boolean> buildWorkingMap(List<ColumnDef> cols) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (ColumnDef cd : cols) map.put(cd.key, cd.isVisible());
        return map;
    }

    private static Map<String, List<ColumnDef>> filterToSegments(List<ColumnDef> cols, String filter) {
        Map<String, List<ColumnDef>> result = new LinkedHashMap<>();
        for (ColumnDef cd : cols) {
            if (!filter.isEmpty() && !matchesFilter(cd, filter)) continue;
            String seg = cd.seqLabel.isEmpty() ? "(General)" : cd.seqLabel;
            result.computeIfAbsent(seg, k -> new ArrayList<>()).add(cd);
        }
        return result;
    }

    private static boolean matchesFilter(ColumnDef cd, String filter) {
        return cd.tagName.toLowerCase(Locale.ROOT).contains(filter)
            || cd.qualifier.toLowerCase(Locale.ROOT).contains(filter)
            || cd.label.toLowerCase(Locale.ROOT).contains(filter)
            || cd.seqLabel.toLowerCase(Locale.ROOT).contains(filter);
    }

    // -----------------------------------------------------------------------
    // Dictionary labels and tooltips
    // -----------------------------------------------------------------------

    private static String buildCheckBoxLabel(ColumnDef cd, HintDictionary dict) {
        String desc = inlineDescription(cd, dict);
        if (desc == null) return cd.label;
        return "<html>" + escHtml(cd.label)
                + "<br><i><font color='gray'>" + escHtml(desc) + "</font></i></html>";
    }

    private static String inlineDescription(ColumnDef cd, HintDictionary dict) {
        if (!cd.qualifier.isEmpty()) return dict.qualifierDescription(cd.qualifier);
        return dict.tagDescription(cd.tagName);
    }

    private static String buildColumnTooltip(ColumnDef cd, HintDictionary dict) {
        String tagDesc  = dict.tagDescription(cd.tagName);
        String qualDesc = cd.qualifier.isEmpty() ? null : dict.qualifierDescription(cd.qualifier);
        if (tagDesc == null && qualDesc == null) return null;
        if (qualDesc == null) return tagDesc;
        if (tagDesc  == null) return qualDesc;
        return tagDesc + " | " + qualDesc;
    }

    private static String escHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}