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
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class HintDictionaryDialog {

    private HintDictionaryDialog() {}

    // -----------------------------------------------------------------------
    // Quick-add dialog (pre-filled from detail table)
    // -----------------------------------------------------------------------

    public static void showAddEntry(Frame owner, String qualifier, String value,
                                    HintDictionary dict) {
        JDialog dlg = new JDialog(owner, "Add to Dictionary", true);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JTextField qualField = new JTextField(qualifier, 20);
        JTextField valField  = new JTextField(value,     20);
        JTextField descField = new JTextField(30);

        JButton addBtn    = new JButton("Add");
        JButton cancelBtn = new JButton("Cancel");
        addBtn.addActionListener(e -> {
            if (onAddEntry(dlg, qualField, valField, descField, dict)) dlg.dispose();
        });
        cancelBtn.addActionListener(e -> dlg.dispose());

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        btnRow.add(addBtn);
        btnRow.add(cancelBtn);

        dlg.setLayout(new BorderLayout());
        dlg.add(buildAddForm(qualField, valField, descField), BorderLayout.CENTER);
        dlg.add(btnRow, BorderLayout.SOUTH);
        dlg.getRootPane().setDefaultButton(addBtn);
        dlg.pack();
        dlg.setResizable(false);
        dlg.setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(descField::requestFocusInWindow);
        dlg.setVisible(true);
    }

    private static JPanel buildAddForm(JTextField qualField, JTextField valField,
                                       JTextField descField) {
        FormPanel fp = new FormPanel();
        FormPanel.addRow(fp.panel, fp.lc, fp.fc, 0, "Qualifier:",   qualField);
        FormPanel.addRow(fp.panel, fp.lc, fp.fc, 1, "Value:",       valField);
        FormPanel.addRow(fp.panel, fp.lc, fp.fc, 2, "Description:", descField);
        return fp.panel;
    }

    private static boolean onAddEntry(JDialog dlg, JTextField qualField, JTextField valField,
                                      JTextField descField, HintDictionary dict) {
        String q = qualField.getText().trim();
        String v = valField.getText().trim();
        String d = descField.getText().trim();
        if (q.isEmpty() || v.isEmpty()) {
            JOptionPane.showMessageDialog(dlg, "Qualifier and Value are required.",
                    "Validation", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        List<String[]> entries = new ArrayList<>(dict.getUserEntries());
        entries.add(new String[]{q, v, d});
        if (!dict.saveUserEntriesToFile(entries)) {
            JOptionPane.showMessageDialog(dlg, "Could not write user dictionary file:\n"
                    + dict.getUserDictFile(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        dict.setUserEntries(entries);
        return true;
    }
}