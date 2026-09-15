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

import com.mtanalyze.elastic.ElasticIndexService;
import com.mtanalyze.lucene.MessageIndexService;
import com.mtanalyze.model.SwiftMessage;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Modal dialog reporting how many of the active tab's MT Entries messages already have a
 * matching document in each Repository backend -- the embedded Lucene index and the
 * configured Elasticsearch index -- opened via Lucene &gt; Check Repository... or
 * Elasticsearch &gt; Check Repository....
 * <p>
 * A message counts as "already indexed" by the same content-hash identity {@code Index
 * Messages} relies on for idempotent re-indexing (see {@code MtLucene#contentId} /
 * {@code MtElastic#contentId}), so this reports what a fresh "Index Messages" run on this
 * tab would add versus merely replace -- without indexing anything.
 */
public final class CheckRepositoryDialog {

    private static final String CHECKING = "Checking…";
    private static final String DISABLED = "Disabled (see Settings ▸ Advanced)";

    private CheckRepositoryDialog() {}

    @FunctionalInterface
    private interface AlreadyIndexedCount {
        int count() throws IOException;
    }

    public static void show(JFrame owner, List<SwiftMessage> messages,
                             MessageIndexService messageIndex, ElasticIndexService elasticIndex,
                             boolean luceneEnabled, boolean elasticEnabled) {
        JDialog dlg = new JDialog(owner, "Check Repository", true);
        dlg.setLayout(new BorderLayout());

        int total = messages.size();

        FormPanel fp = new FormPanel();
        JPanel form = fp.panel;
        GridBagConstraints lc = fp.lc;
        GridBagConstraints fc = fp.fc;

        JLabel luceneResult  = new JLabel(luceneEnabled ? CHECKING : DISABLED);
        JLabel elasticResult = new JLabel(elasticEnabled ? CHECKING : DISABLED);

        JLabel totalLabel = new JLabel(total + (total == 1 ? " message" : " messages"));
        FormPanel.addRow(form, lc, fc, 0, "Entries in this tab:", totalLabel);

        addSectionSeparator(form, 1, "Lucene");
        FormPanel.addRow(form, lc, fc, 2, "Already indexed:", luceneResult);

        addSectionSeparator(form, 3, "Elasticsearch");
        FormPanel.addRow(form, lc, fc, 4, "Already indexed:", elasticResult);

        dlg.add(form, BorderLayout.CENTER);
        dlg.add(buildButtons(dlg), BorderLayout.SOUTH);

        dlg.pack();
        dlg.setMinimumSize(dlg.getSize());
        dlg.setLocationRelativeTo(owner);
        registerEscapeKey(dlg);

        if (luceneEnabled) {
            checkAgainst(luceneResult, total, () -> messageIndex.countAlreadyIndexed(messages));
        }
        if (elasticEnabled) {
            checkAgainst(elasticResult, total, () -> elasticIndex.countAlreadyIndexed(messages));
        }

        dlg.setVisible(true);
    }

    // -----------------------------------------------------------------------
    // Background check
    // -----------------------------------------------------------------------

    private static void checkAgainst(JLabel label, int total, AlreadyIndexedCount supplier) {
        new SwingWorker<Integer, Void>() {
            @Override protected Integer doInBackground() throws IOException {
                return supplier.count();
            }
            @Override protected void done() {
                try {
                    int alreadyIndexed = get();
                    int newCount = total - alreadyIndexed;
                    label.setText(alreadyIndexed + " of " + total
                        + (total == 1 ? " message" : " messages")
                        + " (" + newCount + (newCount == 1 ? " new)" : " new)"));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    String msg = cause != null ? cause.getMessage() : ex.getMessage();
                    label.setText("Unavailable" + (msg != null && !msg.isBlank() ? " (" + msg + ")" : ""));
                    label.setForeground(UIManager.getColor("Label.disabledForeground"));
                }
            }
        }.execute();
    }

    // -----------------------------------------------------------------------
    // Dialog chrome
    // -----------------------------------------------------------------------

    private static JPanel buildButtons(JDialog dlg) {
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dlg.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        south.add(closeBtn);
        dlg.getRootPane().setDefaultButton(closeBtn);
        return south;
    }

    private static void registerEscapeKey(JDialog dlg) {
        dlg.getRootPane().registerKeyboardAction(
            e -> dlg.dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private static void addSectionSeparator(JPanel form, int row, String title) {
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
}
