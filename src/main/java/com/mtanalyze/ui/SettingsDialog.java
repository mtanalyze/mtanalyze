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

import com.mtanalyze.config.SystemConfig;
import com.mtanalyze.export.CsvExport;
import com.mtanalyze.parser.HintDictionary;
import com.mtanalyze.parser.MtFileIO;
import com.mtanalyze.util.FileChoosers;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.prefs.Preferences;

final class SettingsDialog {

    private static final char UTF8_BOM = '\uFEFF';

    private SettingsDialog() {}

    record Config(CsvKeys csv, ThemeConfig theme, SystemKeys system, PowerUserConfig powerUser,
                  LuceneConfig lucene, ElasticConfig elastic) {
        record CsvKeys(String fieldSep, String decimalSep) {
        }

        /**
         * Lucene "Index Messages" / "Search Messages" settings, stored directly in
         * {@link Preferences}. {@code onChange} re-applies them to the running service.
         */
        record LuceneConfig(String dirPrefKey, String maxHitsPrefKey,
                             String defaultDir, int defaultMaxHits, Runnable onChange) {
        }

        /**
         * Elasticsearch "Index Messages" / "Search Messages" connection settings, stored
         * directly in {@link Preferences}. {@code onChange} re-applies them to the running
         * service. The password is the exception: it goes through {@code getPassword}/
         * {@code savePassword}, which store it in the OS keyring (see
         * {@link com.mtanalyze.config.ElasticSecretStore}) rather than in plain text.
         */
        record ElasticConfig(String hostPrefKey, String portPrefKey, String schemePrefKey,
                              String usernamePrefKey, Supplier<String> getPassword, Consumer<String> savePassword,
                              String indexPrefKey, String maxHitsPrefKey,
                              String defaultHost, int defaultPort, String defaultScheme,
                              String defaultIndex, int defaultMaxHits, Runnable onChange) {
        }

        record SystemKeys(Supplier<String> getSender, Supplier<String> getReceiver,
                           Supplier<Integer> getMaxEntries,
                           Supplier<String> getLogSwiftStart, Supplier<String> getLogNewlineToken,
                           Saver save) {
        }

        @FunctionalInterface
        interface Saver {
            void save(String sender, String receiver, int maxEntries,
                       String logSwiftStart, String logNewlineToken);
        }


        record ThemeConfig(String prefKey, Consumer<String> onChange) {
        }

        record PowerUserConfig(String prefKey, Runnable onChange) {
        }

    }

    private record FormFields(JTextField fieldSep, JTextField decimalSep, JTextField sender, JTextField receiver,
                               JTextField maxEntries, JTextField logSwiftStart, JTextField logNewlineToken,
                               JTextField luceneDir, JTextField luceneMaxHits,
                               JTextField elasticHost, JTextField elasticPort, JTextField elasticScheme,
                               JTextField elasticUsername, JPasswordField elasticPassword,
                               JTextField elasticIndex, JTextField elasticMaxHits) {
    }

    static void show(Frame owner, Preferences prefs, Config cfg, HintDictionary dict) {

        JDialog dlg = new JDialog(owner, "Settings", true);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        // ---- General tab ----
        String currentTheme = prefs.get(cfg.theme.prefKey, "Dark");
        JCheckBox darkModeCheck  = new JCheckBox("Dark Mode",       MtAnalyzeFrame.isDarkTheme(currentTheme));
        JCheckBox powerUserCheck = new JCheckBox("Power User Mode", prefs.getBoolean(cfg.powerUser.prefKey, false));

        JTextField fieldSepField = new JTextField(
                prefs.get(cfg.csv.fieldSep, CsvExport.DEFAULT_FIELD_SEP), 4);
        JTextField decimalSepField = new JTextField(
                prefs.get(cfg.csv.decimalSep, CsvExport.DEFAULT_DECIMAL_SEP), 4);
        JTextField senderField = new JTextField(
                cfg.system.getSender.get(), 14);
        JTextField receiverField = new JTextField(
                cfg.system.getReceiver.get(), 14);
        JTextField maxEntriesField = new JTextField(
                String.valueOf(cfg.system.getMaxEntries.get()), 6);
        JTextField logSwiftStartField = new JTextField(
                cfg.system.getLogSwiftStart.get(), 10);
        JTextField logNewlineTokenField = new JTextField(
                cfg.system.getLogNewlineToken.get(), 10);
        JTextField luceneDirField = new JTextField(
                prefs.get(cfg.lucene.dirPrefKey, cfg.lucene.defaultDir), 24);
        luceneDirField.setToolTipText("Leave empty for the default: " + cfg.lucene.defaultDir);
        JTextField luceneMaxHitsField = new JTextField(
                String.valueOf(prefs.getInt(cfg.lucene.maxHitsPrefKey, cfg.lucene.defaultMaxHits)), 6);
        JTextField elasticHostField = new JTextField(
                prefs.get(cfg.elastic.hostPrefKey, cfg.elastic.defaultHost), 14);
        JTextField elasticPortField = new JTextField(
                String.valueOf(prefs.getInt(cfg.elastic.portPrefKey, cfg.elastic.defaultPort)), 6);
        JTextField elasticSchemeField = new JTextField(
                prefs.get(cfg.elastic.schemePrefKey, cfg.elastic.defaultScheme), 6);
        JTextField elasticUsernameField = new JTextField(
                prefs.get(cfg.elastic.usernamePrefKey, ""), 14);
        JPasswordField elasticPasswordField = new JPasswordField(
                cfg.elastic.getPassword.get(), 14);
        JTextField elasticIndexField = new JTextField(
                prefs.get(cfg.elastic.indexPrefKey, cfg.elastic.defaultIndex), 18);
        JTextField elasticMaxHitsField = new JTextField(
                String.valueOf(prefs.getInt(cfg.elastic.maxHitsPrefKey, cfg.elastic.defaultMaxHits)), 6);
        FormFields fields = new FormFields(
                fieldSepField, decimalSepField,
                senderField, receiverField,
                maxEntriesField, logSwiftStartField, logNewlineTokenField,
                luceneDirField, luceneMaxHitsField,
                elasticHostField, elasticPortField, elasticSchemeField,
                elasticUsernameField, elasticPasswordField, elasticIndexField, elasticMaxHitsField);

        JPanel generalPanel  = buildGeneralPanel(darkModeCheck, powerUserCheck, fields);
        JPanel advancedPanel = buildAdvancedPanel(fields, cfg.lucene, cfg.elastic);

        // ---- User Dictionary tab ----
        DefaultTableModel dictModel = new DefaultTableModel(
                new String[]{COL_QUALIFIER, "Value", "Description"}, 0) {
            @Override public Class<?> getColumnClass(int c) { return String.class; }
        };
        for (String[] e : dict.getUserEntries())
            dictModel.addRow(new Object[]{e[0], e[1], e[2]});
        JTable dictTable = buildDictTable(dictModel);

        JLabel dictHint = new JLabel(
                "Custom entries extend and override the built-in dictionary. Qualifier and Value are required.");
        dictHint.setFont(dictHint.getFont().deriveFont(Font.PLAIN, dictHint.getFont().getSize() - 1f));
        dictHint.setBorder(BorderFactory.createEmptyBorder(6, 6, 4, 6));
        dictHint.setForeground(UIManager.getColor("Label.disabledForeground"));

        JPanel dictPanel = buildTablePanel(
                dictTable, new Dimension(660, 300),
                () -> onAddRow(dictTable, dictModel),
                () -> onDeleteRows(dictTable, dictModel),
                () -> onImportDict(dlg, dictModel, prefs.get(cfg.csv.fieldSep, CsvExport.DEFAULT_FIELD_SEP)),
                () -> onExportDict(dlg, dictTable, dictModel, prefs.get(cfg.csv.fieldSep, CsvExport.DEFAULT_FIELD_SEP)),
                dictHint);

        // ---- Assemble tabs ----
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("General",         generalPanel);
        tabs.addTab("Advanced",        advancedPanel);
        tabs.addTab("User Dictionary", dictPanel);

        JButton ok     = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8));
        buttons.add(ok);
        buttons.add(cancel);

        ok.addActionListener(e -> {
            if (!saveSettings(dlg, prefs, cfg, fields)) return;
            if (!saveDictEntries(dlg, dictTable, dictModel, dict)) return;
            String newTheme = darkModeCheck.isSelected() ? "Dark" : "Light";
            prefs.put(cfg.theme.prefKey, newTheme);
            cfg.theme.onChange.accept(newTheme);
            prefs.putBoolean(cfg.powerUser.prefKey, powerUserCheck.isSelected());
            cfg.powerUser.onChange.run();
            dlg.dispose();
        });
        cancel.addActionListener(e -> dlg.dispose());

        dlg.getRootPane().setDefaultButton(ok);
        dlg.setLayout(new BorderLayout());
        dlg.add(tabs,    BorderLayout.CENTER);
        dlg.add(buttons, BorderLayout.SOUTH);
        dlg.pack();
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
    }

    // -----------------------------------------------------------------------
    // General tab
    // -----------------------------------------------------------------------

    private static JPanel buildGeneralPanel(JCheckBox darkModeCheck, JCheckBox powerUserCheck,
                                             FormFields fields) {
        FormPanel fp = new FormPanel();
        JPanel form = fp.panel;
        GridBagConstraints lc = fp.lc;
        GridBagConstraints fc = fp.fc;

        addSectionSeparator(form, 0,  "Appearance");
        FormPanel.addRow(form, lc, fc, 1,  "",                    darkModeCheck);
        FormPanel.addRow(form, lc, fc, 2,  "",                    powerUserCheck);

        addSectionSeparator(form, 3,  "CSV Export");
        FormPanel.addRow(form, lc, fc, 4,  "Field separator:",    fields.fieldSep);
        FormPanel.addRow(form, lc, fc, 5,  "Decimal separator:",  fields.decimalSep);

        JButton resetSeparators = new JButton("Use system defaults");
        resetSeparators.setToolTipText("Reset separators to the values for "
                + Locale.getDefault(Locale.Category.FORMAT).getDisplayName()
                + " (\"" + CsvExport.DEFAULT_FIELD_SEP + "\" / \"" + CsvExport.DEFAULT_DECIMAL_SEP + "\")");
        resetSeparators.addActionListener(e -> {
            fields.fieldSep.setText(CsvExport.DEFAULT_FIELD_SEP);
            fields.decimalSep.setText(CsvExport.DEFAULT_DECIMAL_SEP);
        });
        JPanel resetWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        resetWrap.add(resetSeparators);
        FormPanel.addRow(form, lc, fc, 6,  "",                    resetWrap);

        addSectionSeparator(form, 7,  "MT Export");
        FormPanel.addRow(form, lc, fc, 8,  "Sender BIC:",         fields.sender);
        FormPanel.addRow(form, lc, fc, 9,  "Receiver BIC:",       fields.receiver);

        return form;
    }

    // -----------------------------------------------------------------------
    // Advanced tab
    // -----------------------------------------------------------------------

    private static JPanel buildAdvancedPanel(FormFields fields, Config.LuceneConfig lucene,
                                              Config.ElasticConfig elastic) {
        FormPanel fp = new FormPanel();
        JPanel form = fp.panel;
        GridBagConstraints lc = fp.lc;
        GridBagConstraints fc = fp.fc;

        addSectionSeparator(form, 0, "Import Limits");
        FormPanel.addRow(form, lc, fc, 1, "Max. entries:", fields.maxEntries);

        addSectionSeparator(form, 2, "Log File Import");
        FormPanel.addRow(form, lc, fc, 3, "SWIFT start marker:", fields.logSwiftStart);
        FormPanel.addRow(form, lc, fc, 4, "Newline token:",      fields.logNewlineToken);

        JButton resetLogTokens = new JButton("Reset to defaults");
        resetLogTokens.addActionListener(e -> {
            fields.maxEntries.setText(String.valueOf(SystemConfig.DEFAULT_MAX_ENTRIES));
            fields.logSwiftStart.setText(MtFileIO.DEFAULT_LOG_SWIFT_START);
            fields.logNewlineToken.setText(MtFileIO.DEFAULT_LOG_NEWLINE_TOKEN);
        });
        JPanel resetWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        resetWrap.add(resetLogTokens);
        FormPanel.addRow(form, lc, fc, 5, "", resetWrap);

        addSectionSeparator(form, 6, "Lucene Search");

        JButton browse = new JButton("Browse…");
        browse.addActionListener(e -> {
            JFileChooser fcr = FileChoosers.create();
            fcr.setDialogTitle("Select Lucene Index Directory");
            fcr.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            String cur = fields.luceneDir.getText().trim();
            if (!cur.isEmpty()) fcr.setSelectedFile(new File(cur));
            if (fcr.showOpenDialog(SwingUtilities.getWindowAncestor(form)) == JFileChooser.APPROVE_OPTION)
                fields.luceneDir.setText(fcr.getSelectedFile().getAbsolutePath());
        });
        JPanel dirRow = new JPanel(new BorderLayout(4, 0));
        dirRow.add(fields.luceneDir, BorderLayout.CENTER);
        dirRow.add(browse,           BorderLayout.EAST);
        FormPanel.addRow(form, lc, fc, 7, "Index directory:", dirRow);
        FormPanel.addRow(form, lc, fc, 8, "Search hits (max.):", fields.luceneMaxHits);

        JButton resetLucene = new JButton("Reset to defaults");
        resetLucene.addActionListener(e -> {
            fields.luceneDir.setText(lucene.defaultDir);
            fields.luceneMaxHits.setText(String.valueOf(lucene.defaultMaxHits));
        });
        JPanel resetLuceneWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        resetLuceneWrap.add(resetLucene);
        FormPanel.addRow(form, lc, fc, 9, "", resetLuceneWrap);

        addSectionSeparator(form, 10, "Elasticsearch");
        FormPanel.addRow(form, lc, fc, 11, "Host:",               fields.elasticHost);
        FormPanel.addRow(form, lc, fc, 12, "Port:",                fields.elasticPort);
        FormPanel.addRow(form, lc, fc, 13, "Scheme (http/https):", fields.elasticScheme);
        FormPanel.addRow(form, lc, fc, 14, "Username:",            fields.elasticUsername);
        FormPanel.addRow(form, lc, fc, 15, "Password:",            fields.elasticPassword);
        FormPanel.addRow(form, lc, fc, 16, "Index name:",          fields.elasticIndex);
        FormPanel.addRow(form, lc, fc, 17, "Search hits (max.):",  fields.elasticMaxHits);

        JButton resetElastic = new JButton("Reset to defaults");
        resetElastic.addActionListener(e -> {
            fields.elasticHost.setText(elastic.defaultHost);
            fields.elasticPort.setText(String.valueOf(elastic.defaultPort));
            fields.elasticScheme.setText(elastic.defaultScheme);
            fields.elasticUsername.setText("");
            fields.elasticPassword.setText("");
            fields.elasticIndex.setText(elastic.defaultIndex);
            fields.elasticMaxHits.setText(String.valueOf(elastic.defaultMaxHits));
        });
        JPanel resetElasticWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        resetElasticWrap.add(resetElastic);
        FormPanel.addRow(form, lc, fc, 18, "", resetElasticWrap);

        return form;
    }

    private static final String INVALID_INPUT   = "Invalid Input";
    private static final String LBL_IMPORT      = "Import";
    private static final String CSV_FILE_FILTER = "CSV files (*.csv)";
    private static final String COL_QUALIFIER   = "Qualifier";

    private static boolean saveSettings(JDialog dlg, Preferences prefs, Config cfg, FormFields fields) {
        String fieldSep   = fields.fieldSep.getText();
        String decimalSep = fields.decimalSep.getText();
        String sender     = fields.sender.getText().trim().toUpperCase(Locale.ROOT);
        String receiver   = fields.receiver.getText().trim().toUpperCase(Locale.ROOT);
        String logSwiftStart   = fields.logSwiftStart.getText();
        String logNewlineToken = fields.logNewlineToken.getText();

        String sepError = validateSeparators(fieldSep, decimalSep);
        if (sepError != null) {
            JOptionPane.showMessageDialog(dlg, sepError, INVALID_INPUT, JOptionPane.WARNING_MESSAGE);
            return false;
        }
        String bicError = validateBic(sender, receiver);
        if (bicError != null) {
            JOptionPane.showMessageDialog(dlg, bicError, INVALID_INPUT, JOptionPane.WARNING_MESSAGE);
            return false;
        }
        Integer maxEntries = parsePositiveInt(fields.maxEntries.getText().trim());
        if (maxEntries == null) {
            JOptionPane.showMessageDialog(dlg, "Max. entries must be a positive whole number.",
                    INVALID_INPUT, JOptionPane.WARNING_MESSAGE);
            return false;
        }
        Integer luceneMaxHits = parsePositiveInt(fields.luceneMaxHits.getText().trim());
        if (luceneMaxHits == null) {
            JOptionPane.showMessageDialog(dlg, "Search hits (max.) must be a positive whole number.",
                    INVALID_INPUT, JOptionPane.WARNING_MESSAGE);
            return false;
        }
        String luceneDir = fields.luceneDir.getText().trim();
        String luceneDirError = validateDirectory(luceneDir);
        if (luceneDirError != null) {
            JOptionPane.showMessageDialog(dlg, luceneDirError, INVALID_INPUT, JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if (logSwiftStart.isEmpty()) {
            JOptionPane.showMessageDialog(dlg, "SWIFT start marker must not be empty.",
                    INVALID_INPUT, JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if (logNewlineToken.isEmpty()) {
            JOptionPane.showMessageDialog(dlg, "Newline token must not be empty.",
                    INVALID_INPUT, JOptionPane.WARNING_MESSAGE);
            return false;
        }
        Integer elasticPort = parsePositiveInt(fields.elasticPort.getText().trim());
        if (elasticPort == null) {
            JOptionPane.showMessageDialog(dlg, "Elasticsearch port must be a positive whole number.",
                    INVALID_INPUT, JOptionPane.WARNING_MESSAGE);
            return false;
        }
        String elasticScheme = fields.elasticScheme.getText().trim();
        if (!elasticScheme.isEmpty() && !elasticScheme.equalsIgnoreCase("http")
                && !elasticScheme.equalsIgnoreCase("https")) {
            JOptionPane.showMessageDialog(dlg, "Elasticsearch scheme must be \"http\" or \"https\".",
                    INVALID_INPUT, JOptionPane.WARNING_MESSAGE);
            return false;
        }
        Integer elasticMaxHits = parsePositiveInt(fields.elasticMaxHits.getText().trim());
        if (elasticMaxHits == null) {
            JOptionPane.showMessageDialog(dlg, "Elasticsearch search hits (max.) must be a positive whole number.",
                    INVALID_INPUT, JOptionPane.WARNING_MESSAGE);
            return false;
        }

        prefs.put(cfg.csv.fieldSep,     fieldSep);
        prefs.put(cfg.csv.decimalSep,   decimalSep);
        cfg.system.save.save(sender, receiver, maxEntries, logSwiftStart, logNewlineToken);

        prefs.put(cfg.lucene.dirPrefKey, luceneDir);
        prefs.putInt(cfg.lucene.maxHitsPrefKey, luceneMaxHits);
        cfg.lucene.onChange.run();

        prefs.put(cfg.elastic.hostPrefKey, fields.elasticHost.getText().trim());
        prefs.putInt(cfg.elastic.portPrefKey, elasticPort);
        prefs.put(cfg.elastic.schemePrefKey, elasticScheme.isEmpty() ? cfg.elastic.defaultScheme : elasticScheme);
        prefs.put(cfg.elastic.usernamePrefKey, fields.elasticUsername.getText().trim());
        cfg.elastic.savePassword.accept(new String(fields.elasticPassword.getPassword()));
        prefs.put(cfg.elastic.indexPrefKey, fields.elasticIndex.getText().trim());
        prefs.putInt(cfg.elastic.maxHitsPrefKey, elasticMaxHits);
        cfg.elastic.onChange.run();
        return true;
    }

    /** Empty means "use the default"; otherwise the folder must exist (or be creatable) and be writable. */
    private static String validateDirectory(String dir) {
        if (dir.isEmpty()) return null;
        File f = new File(dir);
        if (f.exists() && !f.isDirectory()) return "Index directory is not a folder:\n" + dir;
        if (!f.exists() && !f.mkdirs())     return "Index directory could not be created:\n" + dir;
        if (!f.canWrite())                  return "Index directory is not writable:\n" + dir;
        return null;
    }

    private static Integer parsePositiveInt(String s) {
        try {
            int v = Integer.parseInt(s);
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String validateSeparators(String fieldSep, String decimalSep) {
        if (fieldSep.isEmpty())   return "Field separator must not be empty.";
        if (decimalSep.isEmpty()) return "Decimal separator must not be empty.";
        if (fieldSep.length() > 1)   return "Field separator must be a single character.";
        if (decimalSep.length() > 1) return "Decimal separator must be a single character.";
        if (fieldSep.equals(decimalSep))
            return "Field separator and decimal separator must be different characters.";
        return null;
    }

    private static String validateBic(String sender, String receiver) {
        String err = checkBic("Sender BIC", sender);
        return err != null ? err : checkBic("Receiver BIC", receiver);
    }

    private static String checkBic(String label, String bic) {
        if (bic.isEmpty()) return null;
        if (bic.length() != 8 && bic.length() != 11)
            return label + " must be 8 or 11 characters (or leave empty for default).";
        if (!bic.matches("[A-Z0-9]+"))
            return label + " must contain only uppercase letters and digits.";
        return null;
    }

    // -----------------------------------------------------------------------
    // Table tab builder (shared by User Dictionary and Account Mapping)
    // -----------------------------------------------------------------------

    private static JPanel buildTablePanel(JTable table, Dimension scrollSize,
                                           Runnable onAdd, Runnable onDelete,
                                           Runnable onImport, Runnable onExport,
                                           JLabel hint) {
        table.setFillsViewportHeight(true);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(scrollSize);

        JMenuItem addItem    = new JMenuItem("Add Row");
        JMenuItem deleteItem = new JMenuItem("Delete Selected");
        JMenuItem importItem = new JMenuItem("Import CSV…");
        JMenuItem exportItem = new JMenuItem("Export CSV…");

        addItem.addActionListener(e    -> onAdd.run());
        deleteItem.addActionListener(e -> onDelete.run());
        importItem.addActionListener(e -> onImport.run());
        exportItem.addActionListener(e -> onExport.run());

        JPopupMenu popup = new JPopupMenu();
        popup.add(addItem);
        popup.add(deleteItem);
        popup.addSeparator();
        popup.add(importItem);
        popup.add(exportItem);

        java.awt.event.MouseAdapter popupListener = new java.awt.event.MouseAdapter() {
            @Override public void mousePressed (java.awt.event.MouseEvent e) { maybeShow(e); }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) { maybeShow(e); }
            private void maybeShow(java.awt.event.MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0 && !table.isRowSelected(row))
                    table.setRowSelectionInterval(row, row);
                deleteItem.setEnabled(table.getSelectedRowCount() > 0);
                popup.show(e.getComponent(), e.getX(), e.getY());
            }
        };
        table.addMouseListener(popupListener);
        scroll.getViewport().addMouseListener(popupListener);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
        if (hint != null) panel.add(hint, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private static void onAddRow(JTable table, DefaultTableModel model) {
        Object[] row = new Object[model.getColumnCount()];
        Arrays.fill(row, "");
        model.addRow(row);
        int r = model.getRowCount() - 1;
        table.scrollRectToVisible(table.getCellRect(r, 0, true));
        table.editCellAt(r, 0);
        table.requestFocusInWindow();
    }

    private static void onDeleteRows(JTable table, DefaultTableModel model) {
        int[] rows = table.getSelectedRows();
        for (int i = rows.length - 1; i >= 0; i--)
            model.removeRow(table.convertRowIndexToModel(rows[i]));
    }

    // -----------------------------------------------------------------------
    // User Dictionary tab
    // -----------------------------------------------------------------------

    private static JTable buildDictTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setRowHeight(22);
        table.getColumnModel().getColumn(0).setPreferredWidth(120); // Qualifier
        table.getColumnModel().getColumn(1).setPreferredWidth(100); // Value
        table.getColumnModel().getColumn(2).setPreferredWidth(420); // Description
        return table;
    }

    private static List<String[]> collectDictEntries(DefaultTableModel model) {
        List<String[]> entries = new ArrayList<>();
        for (int r = 0; r < model.getRowCount(); r++) {
            String q = nvl(model.getValueAt(r, 0));
            String v = nvl(model.getValueAt(r, 1));
            String d = nvl(model.getValueAt(r, 2));
            if (!q.isEmpty() && !v.isEmpty()) entries.add(new String[]{q, v, d});
        }
        return entries;
    }

    private static boolean saveDictEntries(JDialog dlg, JTable table, DefaultTableModel model,
                                            HintDictionary dict) {
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
        List<String[]> entries = collectDictEntries(model);
        if (!dict.saveUserEntriesToFile(entries)) {
            JOptionPane.showMessageDialog(dlg, "Could not write user dictionary file:\n"
                    + dict.getUserDictFile(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        dict.setUserEntries(entries);
        return true;
    }

    private static void onExportDict(JDialog dlg, JTable table, DefaultTableModel model, String fieldSep) {
        exportCsvFile(dlg, table, pw -> {
            pw.println(COL_QUALIFIER + fieldSep + "Value" + fieldSep + "Description");
            for (String[] e : collectDictEntries(model))
                pw.println(csvQuote(e[0], fieldSep) + fieldSep + csvQuote(e[1], fieldSep) + fieldSep + csvQuote(e[2], fieldSep));
        });
    }

    private static void onImportDict(JDialog dlg, DefaultTableModel model, String fieldSep) {
        JFileChooser fc = FileChoosers.create();
        fc.setDialogTitle("Import User Dictionary");
        fc.setFileFilter(new FileNameExtensionFilter(CSV_FILE_FILTER, "csv"));
        if (fc.showOpenDialog(dlg) != JFileChooser.APPROVE_OPTION) return;
        try {
            Set<String> existing = existingDictKeys(model);
            int[] counts = {0, 0}; // [added, skipped]
            readCsvLines(fc.getSelectedFile(), fieldSep, parts -> {
                String q = csvField(parts, 0);
                String v = csvField(parts, 1);
                if (q.isEmpty() || v.isEmpty()) return;
                if (existing.add(q + '\t' + v)) {
                    model.addRow(new Object[]{q, v, csvField(parts, 2)});
                    counts[0]++;
                } else {
                    counts[1]++;
                }
            });
            JOptionPane.showMessageDialog(dlg,
                    counts[0] + " entries imported, " + counts[1] + " duplicates skipped.",
                    LBL_IMPORT, JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(dlg, "Import failed:\n" + ex.getMessage(),
                    "Import Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static Set<String> existingDictKeys(DefaultTableModel model) {
        Set<String> keys = new HashSet<>();
        for (int r = 0; r < model.getRowCount(); r++)
            keys.add(nvl(model.getValueAt(r, 0)) + '\t' + nvl(model.getValueAt(r, 1)));
        return keys;
    }

    // -----------------------------------------------------------------------
    // Shared I/O helpers
    // -----------------------------------------------------------------------

    private static void exportCsvFile(JDialog dlg, JTable table, Consumer<PrintWriter> writeContent) {
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
        JFileChooser fc = FileChoosers.create();
        fc.setDialogTitle("Export User Dictionary");
        fc.setFileFilter(new FileNameExtensionFilter(CSV_FILE_FILTER, "csv"));
        fc.setSelectedFile(new File("user_dictionary.csv"));
        if (fc.showSaveDialog(dlg) != JFileChooser.APPROVE_OPTION) return;
        File file = ensureCsvExtension(fc.getSelectedFile());
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            pw.write(UTF8_BOM);
            writeContent.accept(pw);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(dlg, "Export failed:\n" + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // -----------------------------------------------------------------------
    // CSV helpers
    // -----------------------------------------------------------------------

    @FunctionalInterface
    private interface RowConsumer {
        void accept(String[] parts);
    }

    private static void readCsvLines(File file, String fieldSep, RowConsumer consumer)
            throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (!line.isEmpty() && line.charAt(0) == UTF8_BOM) line = line.substring(1);
                if (first && line.startsWith(COL_QUALIFIER)) { first = false; continue; }
                first = false;
                if (!line.isEmpty()) consumer.accept(line.split(java.util.regex.Pattern.quote(fieldSep), -1));
            }
        }
    }

    private static String csvField(String[] parts, int index) {
        if (index >= parts.length) return "";
        return csvUnquote(parts[index]);
    }

    private static String csvQuote(String value, String fieldSep) {
        if (value.contains(fieldSep) || value.contains("\"") || value.contains("\n"))
            return "\"" + value.replace("\"", "\"\"") + "\"";
        return value;
    }

    private static String csvUnquote(String s) {
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2)
            return s.substring(1, s.length() - 1).replace("\"\"", "\"");
        return s;
    }

    private static File ensureCsvExtension(File file) {
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".csv"))
            return new File(file.getAbsolutePath() + ".csv");
        return file;
    }

    private static String nvl(Object o) {
        return o != null ? o.toString().trim() : "";
    }

    // -----------------------------------------------------------------------
    // Form helpers
    // -----------------------------------------------------------------------

    private static void addSectionSeparator(JPanel form, int row, String title) {
        GridBagConstraints sc = new GridBagConstraints();
        sc.gridx = 0; sc.gridy = row; sc.gridwidth = 2;
        sc.fill = GridBagConstraints.HORIZONTAL;
        sc.insets = new Insets(10, 0, 2, 0);
        JLabel sep = new JLabel(title);
        sep.setFont(sep.getFont().deriveFont(Font.BOLD));
        sep.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0,
                UIManager.getColor("Separator.foreground")));
        form.add(sep, sc);
    }
}