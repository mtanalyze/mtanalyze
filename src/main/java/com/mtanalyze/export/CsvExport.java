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
package com.mtanalyze.export;

import com.prowidesoftware.swift.model.SwiftTagListBlock;
import com.mtanalyze.profile.DataHelper;
import com.mtanalyze.ui.ColumnDef;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Desktop;
import java.awt.Frame;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

public final class CsvExport {

    public static final String DEFAULT_DECIMAL_SEP = String.valueOf(
            DecimalFormatSymbols.getInstance(Locale.getDefault(Locale.Category.FORMAT)).getDecimalSeparator());
    public static final String DEFAULT_FIELD_SEP   = ",".equals(DEFAULT_DECIMAL_SEP) ? ";" : ",";

    private final DataHelper dataHelper = new DataHelper();

    public static final class Prefs {
        private final Preferences store;
        private final String fieldSepKey;
        private final String decimalSepKey;

        public Prefs(Preferences store, String fieldSepKey, String decimalSepKey) {
            this.store         = store;
            this.fieldSepKey   = fieldSepKey;
            this.decimalSepKey = decimalSepKey;
        }

        public String fieldSep()   { return store.get(fieldSepKey,   DEFAULT_FIELD_SEP); }
        public String decimalSep() { return store.get(decimalSepKey, DEFAULT_DECIMAL_SEP); }
    }

    @FunctionalInterface
    private interface FileWriter {
        void write(File file, String fieldSep, String decimalSep) throws IOException;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public void export(JFrame owner, List<ColumnDef> cols,
                       List<Map<String, String>> rows, Consumer<String> status, Prefs csvPrefs) {
        if (cols.isEmpty()) { showNoData(owner); return; }
        doExport(owner, "SWIFT_Export.csv", csvPrefs, status,
                 (f, fs, ds) -> writeFile(f, cols, rows, fs, ds));
    }

    public void exportComponents(JFrame owner, List<SwiftTagListBlock> seqs,
                                 List<Map<String, String>> rowData, String seqKey,
                                 Consumer<String> status, Prefs csvPrefs) {
        if (seqs.isEmpty()) { showNoData(owner); return; }
        List<String[]> compRows = dataHelper.collectAllComponentRows(seqs, rowData, seqKey);
        doExport(owner, "SWIFT_Components.csv", csvPrefs, status,
                 (f, fs, ds) -> writePivotedFile(f, compRows, fs, ds));
    }

    // -----------------------------------------------------------------------
    // Export orchestration
    // -----------------------------------------------------------------------

    private static void doExport(JFrame owner, String defaultName, Prefs csvPrefs,
                                  Consumer<String> status, FileWriter writer) {
        File file = pickCsvFile(owner, defaultName);
        if (file == null) return;
        try {
            writer.write(file, csvPrefs.fieldSep(), csvPrefs.decimalSep());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(owner, "Error during export:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        status.accept("Exported: " + file.getAbsolutePath());
        offerOpenFile(owner, file);
    }

    private static File pickCsvFile(JFrame owner, String defaultName) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save CSV File");
        fc.setFileFilter(new FileNameExtensionFilter("CSV Files (*.csv)", "csv"));
        fc.setSelectedFile(new File(defaultName));
        if (fc.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) return null;
        File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".csv"))
            file = new File(file.getAbsolutePath() + ".csv");
        return file;
    }

    private static void showNoData(JFrame owner) {
        JOptionPane.showMessageDialog(owner, "Please load a SWIFT file first.",
                "No Data", JOptionPane.INFORMATION_MESSAGE);
    }

    // -----------------------------------------------------------------------
    // File writing
    // -----------------------------------------------------------------------

    private static void writeFile(File file, List<ColumnDef> cols,
                                   List<Map<String, String>> rows,
                                   String fieldSep, String decimalSep) throws IOException {
        String[] headers = cols.stream().map(c -> c.label).toArray(String[]::new);
        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            bw.write(buildArrayRow(headers, fieldSep, decimalSep));
            for (Map<String, String> row : rows) {
                String[] cells = cols.stream()
                        .map(c -> row.getOrDefault(c.key, ""))
                        .toArray(String[]::new);
                bw.write(buildArrayRow(cells, fieldSep, decimalSep));
            }
        }
    }

    private static void writePivotedFile(File file, List<String[]> compRows,
                                          String fieldSep, String decimalSep) throws IOException {
        // comp rows: [entry, seqLabel, tag, qualifier, component, value]
        LinkedHashMap<String, String>  colHeaders = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> colIndex   = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> entryIndex = new LinkedHashMap<>();

        for (String[] r : compRows) {
            String ck = r[1] + "\t" + r[2] + "\t" + r[3] + "\t" + r[4];
            colIndex.computeIfAbsent(ck, key -> {
                colHeaders.put(key, compColHeader(r[1], r[2], r[3], r[4]));
                return colIndex.size();
            });
            entryIndex.putIfAbsent(r[0], entryIndex.size());
        }

        String[][] data = new String[entryIndex.size()][colIndex.size()];
        for (String[] row : data) Arrays.fill(row, "");
        for (String[] r : compRows) {
            String ck = r[1] + "\t" + r[2] + "\t" + r[3] + "\t" + r[4];
            data[entryIndex.get(r[0])][colIndex.get(ck)] = r[5];
        }

        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            bw.write(buildArrayRow(colHeaders.values().toArray(new String[0]), fieldSep, decimalSep));
            for (String[] row : data)
                bw.write(buildArrayRow(row, fieldSep, decimalSep));
        }
    }

    private static String compColHeader(String seq, String tag, String qualifier, String comp) {
        StringBuilder sb = new StringBuilder(seq).append(" / ").append(tag);
        if (!qualifier.isEmpty()) sb.append(" / ").append(qualifier);
        return sb.append(" / ").append(comp).toString();
    }

    private static String buildArrayRow(String[] fields, String fieldSep, String decimalSep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(fieldSep);
            sb.append(csvField(fields[i], fieldSep, decimalSep));
        }
        sb.append("\r\n");
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Field formatting
    // -----------------------------------------------------------------------

    public static String csvField(String value, String fieldSep, String decimalSep) {
        if (value == null) value = "";
        if (!".".equals(decimalSep)) value = value.replace(".", decimalSep);
        boolean needsQuote = value.contains(fieldSep)
                          || value.contains("\"")
                          || value.contains("\n")
                          || value.contains("\r");
        if (!needsQuote) return value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    // -----------------------------------------------------------------------
    // Post-export
    // -----------------------------------------------------------------------

    public static void offerOpenFile(Frame owner, File file) {
        if (!Desktop.isDesktopSupported()) return;
        int ans = JOptionPane.showConfirmDialog(owner,
                "File saved:\n" + file.getAbsolutePath() + "\n\nOpen now?",
                "Export Successful", JOptionPane.YES_NO_OPTION);
        if (ans == JOptionPane.YES_OPTION) {
            try { Desktop.getDesktop().open(file); } catch (Exception ignore) { /* best effort */ }
        }
    }
}