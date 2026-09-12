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

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * Modal dialog showing how many messages are currently indexed in each Repository
 * backend -- the embedded Lucene index and the configured Elasticsearch index --
 * opened via Lucene &gt; Statistics... or Elasticsearch &gt; Statistics....
 * <p>
 * Both document counts are fetched in the background: Lucene is a local directory and
 * returns almost instantly, while Elasticsearch is a real network call that can be slow
 * or fail if the cluster is unreachable. Fetching them independently means a slow/down
 * Elasticsearch cluster never delays the Lucene count from showing, and vice versa.
 */
public final class StatisticsDialog {

    private StatisticsDialog() {}

    public static void show(JFrame owner, MessageIndexService messageIndex, ElasticIndexService elasticIndex) {
        JDialog dlg = new JDialog(owner, "Index Statistics", true);
        dlg.setLayout(new BorderLayout());

        FormPanel fp = new FormPanel();
        JPanel form = fp.panel;
        GridBagConstraints lc = fp.lc;
        GridBagConstraints fc = fp.fc;

        JLabel luceneCount  = new JLabel("Loading…");
        JLabel elasticCount = new JLabel("Loading…");

        addSectionSeparator(form, 0, "Lucene");
        FormPanel.addRow(form, lc, fc, 1, "Index directory:", new JLabel(messageIndex.indexDir().toString()));
        FormPanel.addRow(form, lc, fc, 2, "Indexed messages:", luceneCount);

        addSectionSeparator(form, 3, "Elasticsearch");
        FormPanel.addRow(form, lc, fc, 4, "Cluster:", new JLabel(elasticIndex.connectionLabel()));
        FormPanel.addRow(form, lc, fc, 5, "Indexed messages:", elasticCount);

        dlg.add(form, BorderLayout.CENTER);
        dlg.add(buildButtons(dlg, () -> {
            luceneCount.setText("Loading…");
            elasticCount.setText("Loading…");
            loadCount(luceneCount, messageIndex::documentCount);
            loadCount(elasticCount, elasticIndex::documentCount);
        }), BorderLayout.SOUTH);

        dlg.pack();
        dlg.setMinimumSize(dlg.getSize());
        dlg.setLocationRelativeTo(owner);
        registerEscapeKey(dlg);

        loadCount(luceneCount, messageIndex::documentCount);
        loadCount(elasticCount, elasticIndex::documentCount);

        dlg.setVisible(true);
    }

    // -----------------------------------------------------------------------
    // Background count loading
    // -----------------------------------------------------------------------

    @FunctionalInterface
    private interface CountSupplier {
        long get() throws IOException;
    }

    private static void loadCount(JLabel label, CountSupplier supplier) {
        new SwingWorker<Long, Void>() {
            @Override protected Long doInBackground() throws IOException {
                return supplier.get();
            }
            @Override protected void done() {
                try {
                    long count = get();
                    label.setText(count + (count == 1 ? " message" : " messages"));
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

    private static JPanel buildButtons(JDialog dlg, Runnable onRefresh) {
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> onRefresh.run());
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dlg.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        south.add(refreshBtn);
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
