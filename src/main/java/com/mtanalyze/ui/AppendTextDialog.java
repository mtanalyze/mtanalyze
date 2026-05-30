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

import com.mtanalyze.parser.MtFileIO;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Modal dialog for pasting and parsing raw SWIFT MT text.
 * Handles plain-text and name-value CSV content, and EBCDIC encoding correction.
 */
public final class AppendTextDialog {

    private AppendTextDialog() {}

    /**
     * Shows the dialog and blocks until the user closes it.
     *
     * @param owner        parent frame for the modal dialog
     * @param promptMtType called when the MT type cannot be auto-detected; returns the user choice or null
     * @param onParseText  called with (strippedText, mtTypeOverride) for plain-text content; returns count parsed
     * @param onParseCsv   called with CSV chunks for name-value content; returns count parsed
     */
    public static void show(
            Frame owner,
            Supplier<String> promptMtType,
            BiFunction<String, String, Integer> onParseText,
            ToIntFunction<List<String>> onParseCsv) {

        JDialog dialog = new JDialog(owner, "Paste MT Snippet", true);
        dialog.setLayout(new BorderLayout(8, 8));

        JTextArea textArea = new JTextArea(20, 60);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setLineWrap(false);
        JScrollPane scroll = new JScrollPane(textArea);

        JButton pasteBtn        = new JButton("Paste", ToolbarIcons.menuPaste());
        JButton fixMainframeBtn = new JButton("Fix Encoding");
        JButton parseBtn        = new JButton("Parse");
        JButton cancelBtn       = new JButton("Cancel");

        fixMainframeBtn.setEnabled(false);
        fixMainframeBtn.setToolTipText(
            "<html>Fixes EBCDIC CP273 mis-conversion:<br>" +
            "&auml; &rarr; {&nbsp;&nbsp;&nbsp;(SWIFT block opener)<br>" +
            "&uuml; &rarr; }&nbsp;&nbsp;&nbsp;(SWIFT block closer)</html>");

        Runnable updateFixBtn = () ->
            fixMainframeBtn.setEnabled(textArea.getText().indexOf('ä') >= 0
                                    || textArea.getText().indexOf('ü') >= 0);

        pasteBtn.addActionListener(e -> {
            try {
                Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
                if (t != null && t.isDataFlavorSupported(DataFlavor.stringFlavor))
                    textArea.setText((String) t.getTransferData(DataFlavor.stringFlavor));
                updateFixBtn.run();
            } catch (UnsupportedFlavorException | IOException ex) {
                JOptionPane.showMessageDialog(dialog, "Clipboard error:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        textArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate (javax.swing.event.DocumentEvent e) { updateFixBtn.run(); }
            @Override public void removeUpdate (javax.swing.event.DocumentEvent e) { updateFixBtn.run(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updateFixBtn.run(); }
        });

        fixMainframeBtn.addActionListener(e -> {
            textArea.setText(MtFileIO.fixMainframeEncoding(textArea.getText()));
            textArea.setCaretPosition(0);
        });

        Runnable doParse = () -> {
            String raw = textArea.getText();
            if (raw.trim().isEmpty()) return;
            int parsed = parseContent(raw, promptMtType, onParseText, onParseCsv);
            if (parsed != 0) dialog.dispose();  // >0 success, <0 error (notifications shown)
        };

        parseBtn.addActionListener(e -> doParse.run());
        cancelBtn.addActionListener(e -> dialog.dispose());

        // auto-paste clipboard on open
        pasteBtn.doClick();

        JPanel leftBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftBtns.add(pasteBtn);
        leftBtns.add(fixMainframeBtn);

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightBtns.add(parseBtn);
        rightBtns.add(cancelBtn);

        JPanel btnPanel = new JPanel(new BorderLayout());
        btnPanel.setBorder(new EmptyBorder(4, 8, 8, 8));
        btnPanel.add(leftBtns,  BorderLayout.WEST);
        btnPanel.add(rightBtns, BorderLayout.EAST);

        dialog.add(scroll,    BorderLayout.CENTER);
        dialog.add(btnPanel,  BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(parseBtn);
        dialog.getRootPane().registerKeyboardAction(
            e -> dialog.dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.getRootPane().registerKeyboardAction(
            e -> doParse.run(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK),
            JComponent.WHEN_IN_FOCUSED_WINDOW);

        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private static int parseContent(String raw, Supplier<String> promptMtType,
            BiFunction<String, String, Integer> onParseText,
            ToIntFunction<List<String>> onParseCsv) {
        if (MtFileIO.isCsvSwiftContent(raw))
            return onParseCsv.applyAsInt(MtFileIO.splitCsvIntoSwiftMessages(raw));
        String text = MtFileIO.stripIndentation(raw);
        String mtOverride = null;
        if (!MtFileIO.isNameValueContent(text)) {
            mtOverride = MtFileIO.tryDetectMtType(text);
            if (mtOverride == null && MtFileIO.needsMtTypeOverride(text))
                mtOverride = promptMtType.get();
        }
        return onParseText.apply(text, mtOverride);
    }
}