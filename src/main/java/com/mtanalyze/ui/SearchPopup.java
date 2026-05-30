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

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Inline search popup shown below a toolbar anchor button. */
public final class SearchPopup {

    private SearchPopup() {}

    /**
     * Shows a popup containing {@code field} and any {@code extra} components,
     * anchored below {@code anchor}.  Focuses and selects all text in {@code field}.
     */
    public static void show(JButton anchor, JTextField field, JComponent... extra) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        panel.setBorder(new EmptyBorder(2, 4, 2, 4));
        panel.add(field);
        for (JComponent c : extra) panel.add(c);

        JPopupMenu popup = new JPopupMenu();
        popup.add(panel);
        popup.show(anchor, 0, anchor.getHeight());
        SwingUtilities.invokeLater(() -> {
            field.requestFocusInWindow();
            field.selectAll();
        });
    }
}