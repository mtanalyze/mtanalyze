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
import java.util.concurrent.ExecutionException;

/**
 * Chrome shared by the small modal report dialogs (e.g. {@link StatisticsDialog},
 * {@link CheckRepositoryDialog}): the Escape-to-close binding, the bold section
 * separator label used between "Lucene" and "Elasticsearch" blocks, the right-aligned
 * button bar, and the "Unavailable (...)" text shown when a background repository
 * call fails.
 */
final class DialogSupport {

    static final String DISABLED = "Disabled (see Settings ▸ Advanced)";

    private DialogSupport() {}

    static void registerEscapeKey(JDialog dlg) {
        dlg.getRootPane().registerKeyboardAction(
            e -> dlg.dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    static void addSectionSeparator(JPanel form, int row, String title) {
        GridBagConstraints sc = new GridBagConstraints();
        sc.gridx = 0; sc.gridy = row; sc.gridwidth = 2;
        sc.fill = GridBagConstraints.HORIZONTAL;
        sc.insets = new Insets(row == 0 ? 0 : 10, 0, 2, 0);
        JLabel sep = new JLabel(title);
        sep.setFont(sep.getFont().deriveFont(Font.BOLD));
        sep.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0,
                UIManager.getColor("Separator.foreground")));
        form.add(sep, sc);
    }

    /** Right-aligned button bar; the last button becomes the dialog's default button. */
    static JPanel buildButtonBar(JDialog dlg, JButton... buttons) {
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        for (JButton b : buttons) south.add(b);
        dlg.getRootPane().setDefaultButton(buttons[buttons.length - 1]);
        return south;
    }

    /** Renders an {@link ExecutionException} from a background repository call onto a result label. */
    static void showUnavailable(JLabel label, ExecutionException ex) {
        Throwable cause = ex.getCause();
        String msg = cause != null ? cause.getMessage() : ex.getMessage();
        label.setText("Unavailable" + (msg != null && !msg.isBlank() ? " (" + msg + ")" : ""));
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
    }
}
