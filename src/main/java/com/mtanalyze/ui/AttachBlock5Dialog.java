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
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Self-contained dialog for attaching a Block 5 MAC trailer to a SWIFT FIN file.
 * Flow: source file chooser → MAC value configuration → output file chooser → write.
 */
public final class AttachBlock5Dialog {

    private static final String ERROR_TITLE = "Error";

    private AttachBlock5Dialog() {}

    /**
     * Runs the full "Attach Block 5" workflow.
     *
     * @param parent     parent frame for all modal sub-dialogs
     * @param initialDir starting directory for the source file chooser (may be {@code null})
     * @param onSuccess  called with the absolute output file path on successful write
     */
    public static void show(Frame parent, File initialDir, Consumer<String> onSuccess) {

        // ── 1. Source file chooser ─────────────────────────────────────────
        JFileChooser openFc = new JFileChooser();
        openFc.setDialogTitle("Attach Block 5 – Select SWIFT FIN File");
        openFc.setFileFilter(new FileNameExtensionFilter(
            "SWIFT Files (*.txt, *.swift, *.fin, *.ste)", "txt", "swift", "fin", "ste"));
        openFc.setAcceptAllFileFilterUsed(true);
        if (initialDir != null && initialDir.isDirectory()) openFc.setCurrentDirectory(initialDir);
        if (openFc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        File sourceFile = openFc.getSelectedFile();

        // ── 2. Read content ────────────────────────────────────────────────
        String content;
        try {
            content = new String(Files.readAllBytes(sourceFile.toPath()));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(parent, "Cannot read file:\n" + ex.getMessage(),
                ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
            return;
        }

        // ── 3. Inspect existing Block 5 ────────────────────────────────────
        boolean hasBlock5   = Block5Service.hasBlock5(content);
        String  existingMac = Block5Service.findExistingMac(content);

        String statusText;
        if (!hasBlock5) {
            statusText = "Block 5 absent – will be appended.";
        } else if (existingMac != null) {
            statusText = "Block 5 present – existing MAC (" + existingMac + ") will be replaced.";
        } else {
            statusText = "Block 5 present (no MAC tag) – MAC tag will be inserted.";
        }

        // ── 4. MAC configuration dialog ────────────────────────────────────
        String     defaultMac = existingMac != null ? existingMac : "00000000";
        JTextField macField   = new JTextField(defaultMac, 12);
        macField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(4, 4, 4, 4));
        panel.add(new JLabel("File: " + sourceFile.getName()));
        panel.add(Box.createVerticalStrut(4));
        panel.add(new JLabel(statusText));
        panel.add(Box.createVerticalStrut(8));
        panel.add(new JLabel("MAC value (8 hex characters):"));
        panel.add(Box.createVerticalStrut(2));
        panel.add(macField);

        int dlgResult = JOptionPane.showConfirmDialog(parent, panel,
            "Attach Block 5", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (dlgResult != JOptionPane.OK_OPTION) return;

        String macValue = macField.getText().trim().toUpperCase(Locale.ROOT);
        if (!macValue.matches("[0-9A-F]{8}")) {
            JOptionPane.showMessageDialog(parent,
                "MAC value must be exactly 8 hexadecimal characters (0–9, A–F).",
                "Invalid MAC", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // ── 5. Process ─────────────────────────────────────────────────────
        String modified;
        try {
            modified = Block5Service.attachMac(content, macValue);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(parent, "Processing error:\n" + ex.getMessage(),
                ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
            return;
        }

        // ── 6. Output file chooser ─────────────────────────────────────────
        JFileChooser saveFc = new JFileChooser();
        saveFc.setDialogTitle("Save Modified SWIFT File");
        saveFc.setCurrentDirectory(sourceFile.getParentFile());
        saveFc.setSelectedFile(new File(sourceFile.getParentFile(),
            addSuffix(sourceFile.getName())));
        if (saveFc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        File outFile = saveFc.getSelectedFile();

        // ── 7. Write ───────────────────────────────────────────────────────
        try {
            Files.writeString(outFile.toPath(), modified);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(parent, "Cannot write file:\n" + ex.getMessage(),
                ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
            return;
        }

        onSuccess.accept(outFile.getAbsolutePath());
    }

    // -----------------------------------------------------------------------

    private static String addSuffix(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0
            ? filename.substring(0, dot) + "_mac" + filename.substring(dot)
            : filename + "_mac";
    }
}
