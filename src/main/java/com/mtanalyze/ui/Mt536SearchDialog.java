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

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Structured search mask for "Search MT 536 Entries", opened via Lucene ▸ Search MT 536
 * Entries.... Exposes the fields that actually matter for an MT 536 transaction lookup
 * (turned into the tag-value map {@link com.mtanalyze.lucene.MessageIndexService#searchMt536}
 * expects), plus the same free-form Lucene query box the "Search Messages" dialog offers,
 * for anything the structured fields don't cover -- both are ANDed together.
 */
public final class Mt536SearchDialog {

    private static final String TITLE = "Search MT 536 Entries";

    private Mt536SearchDialog() {}

    /** The fields the user filled in: a Logical Terminal (BIC), tag values, and/or a raw query. */
    public record Result(String lt, Map<String, String> tagValues, String query) {}

    /** Shows the modal search mask with an empty query box; returns {@code null} if cancelled. */
    public static Result show(Frame owner) {
        return show(owner, "");
    }

    /**
     * Shows the modal search mask, pre-filling the free-text query box with
     * {@code initialQuery} (e.g. the last query run). Returns {@code null} if cancelled.
     */
    public static Result show(Frame owner, String initialQuery) {
        JTextField refField   = new JTextField(18);
        JTextField isinField  = new JTextField(18);
        JTextField dateField  = new JTextField(18);
        JTextField safeField  = new JTextField(18);
        JTextField narrField  = new JTextField(18);
        JTextField ltField    = new JTextField(18);

        FormPanel fp = new FormPanel();
        JPanel form = fp.panel;
        int row = 0;
        FormPanel.addRow(form, fp.lc, fp.fc, row++, "Reference (20C):", refField);
        FormPanel.addRow(form, fp.lc, fp.fc, row++, "ISIN (35B):", isinField);
        FormPanel.addRow(form, fp.lc, fp.fc, row++, "Settlement/Trade Date (98A, YYYYMMDD):", dateField);
        FormPanel.addRow(form, fp.lc, fp.fc, row++, "Safekeeping Account (97A):", safeField);
        FormPanel.addRow(form, fp.lc, fp.fc, row++, "Narrative contains (70E):", narrField);
        FormPanel.addRow(form, fp.lc, fp.fc, row++, "Sender/Receiver BIC:", ltField);

        JTextArea queryArea = new JTextArea(4, 24);
        queryArea.setLineWrap(true);
        queryArea.setWrapStyleWord(true);
        queryArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        queryArea.setText(initialQuery);
        JScrollPane queryScroll = new JScrollPane(queryArea);

        GridBagConstraints queryLabelC = new GridBagConstraints();
        queryLabelC.gridx = 0; queryLabelC.gridy = row; queryLabelC.gridwidth = 2;
        queryLabelC.anchor = GridBagConstraints.WEST;
        queryLabelC.insets = new Insets(10, 0, 2, 0);
        form.add(new JLabel("Query (Lucene syntax, optional -- ANDed with the fields above):"), queryLabelC);
        row++;

        GridBagConstraints queryFieldC = new GridBagConstraints();
        queryFieldC.gridx = 0; queryFieldC.gridy = row; queryFieldC.gridwidth = 2;
        queryFieldC.fill = GridBagConstraints.BOTH;
        queryFieldC.weightx = 1.0; queryFieldC.weighty = 1.0;
        queryFieldC.insets = new Insets(0, 0, 4, 0);
        form.add(queryScroll, queryFieldC);

        JLabel hint = new JLabel("Fill in one or more fields; everything given must match (AND).");
        hint.setFont(hint.getFont().deriveFont(Font.ITALIC, hint.getFont().getSize2D() - 1f));
        hint.setBorder(BorderFactory.createEmptyBorder(0, 12, 8, 12));

        JPanel content = new JPanel(new BorderLayout());
        content.add(form, BorderLayout.CENTER);
        content.add(hint, BorderLayout.SOUTH);

        JDialog dlg = new JDialog(owner, TITLE, true);
        dlg.setLayout(new BorderLayout());
        dlg.add(content, BorderLayout.CENTER);

        final Result[] result = {null};
        JButton searchBtn = new JButton("Search");
        JButton cancelBtn = new JButton("Cancel");
        searchBtn.addActionListener(e -> {
            Result r = buildResult(refField, isinField, dateField, safeField, narrField, ltField, queryArea);
            if (r == null) {
                JOptionPane.showMessageDialog(dlg,
                    "Enter at least one search criterion.", TITLE, JOptionPane.WARNING_MESSAGE);
                return;
            }
            result[0] = r;
            dlg.dispose();
        });
        cancelBtn.addActionListener(e -> dlg.dispose());

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        south.add(cancelBtn);
        south.add(searchBtn);
        dlg.add(south, BorderLayout.SOUTH);
        dlg.getRootPane().setDefaultButton(searchBtn);
        dlg.getRootPane().registerKeyboardAction(e -> dlg.dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        dlg.pack();
        dlg.setMinimumSize(dlg.getSize());
        dlg.setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(refField::requestFocusInWindow);
        dlg.setVisible(true);
        return result[0];
    }

    private static Result buildResult(JTextField refField, JTextField isinField, JTextField dateField,
            JTextField safeField, JTextField narrField, JTextField ltField, JTextArea queryArea) {
        Map<String, String> tagValues = new LinkedHashMap<>();
        putIfPresent(tagValues, "20C", refField.getText());
        putIfPresent(tagValues, "35B", isinField.getText());
        putIfPresent(tagValues, "98A", dateField.getText());
        putIfPresent(tagValues, "97A", safeField.getText());
        putIfPresent(tagValues, "70E", narrField.getText());
        String lt = ltField.getText().trim().toUpperCase(Locale.ROOT);
        String query = queryArea.getText().trim();
        if (tagValues.isEmpty() && lt.isEmpty() && query.isEmpty()) return null;
        return new Result(lt.isEmpty() ? null : lt, tagValues, query.isEmpty() ? null : query);
    }

    private static void putIfPresent(Map<String, String> map, String tag, String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (!value.isEmpty()) map.put(tag, value);
    }
}
