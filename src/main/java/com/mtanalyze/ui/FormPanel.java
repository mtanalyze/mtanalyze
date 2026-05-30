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
import java.awt.*;

public final class FormPanel {
    public final JPanel             panel;
    public final GridBagConstraints lc;
    public final GridBagConstraints fc;

    public FormPanel() {
        panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 4, 12));
        lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(4, 0, 4, 8);
        fc = new GridBagConstraints();
        fc.fill    = GridBagConstraints.HORIZONTAL;
        fc.weightx = 1.0;
        fc.insets  = new Insets(4, 0, 4, 0);
    }

    public static void addRow(JPanel form, GridBagConstraints lc, GridBagConstraints fc,
                       int row, String label, JComponent field) {
        lc.gridx = 0; lc.gridy = row;
        fc.gridx = 1; fc.gridy = row;
        form.add(new JLabel(label), lc);
        form.add(field, fc);
    }
}