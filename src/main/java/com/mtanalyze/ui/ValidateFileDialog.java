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

import com.prowidesoftware.swift.io.parser.SwiftParser;
import com.prowidesoftware.swift.model.SwiftBlock;
import com.prowidesoftware.swift.model.SwiftBlock1;
import com.prowidesoftware.swift.model.SwiftBlock2;
import com.prowidesoftware.swift.model.SwiftBlock4;
import com.prowidesoftware.swift.model.SwiftMessage;
import com.prowidesoftware.swift.model.SwiftValueBlock;
import com.prowidesoftware.swift.model.mt.AbstractMT;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Parses and validates a complete SWIFT FIN file (with header blocks) using Prowide Core
 * and displays the result – JUL log messages, parser errors, block structure, message info,
 * and JSON – in a scrollable dialog window.
 */
public final class ValidateFileDialog {

    private ValidateFileDialog() {}

    // -----------------------------------------------------------------------

    public static void show(Frame parent, File file) {
        String report = buildReport(file);

        JTextArea area = new JTextArea(report);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setLineWrap(false);
        area.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(860, 660));

        JDialog dlg = new JDialog(parent, "Validate SWIFT File – " + file.getName(), false);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JButton copyBtn = new JButton("Copy to Clipboard");
        copyBtn.addActionListener(e -> {
            area.selectAll();
            area.copy();
            area.setCaretPosition(0);
            copyBtn.setText("Copied!");
            Timer t = new Timer(1500, ev -> copyBtn.setText("Copy to Clipboard"));
            t.setRepeats(false);
            t.start();
        });

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dlg.dispose());
        dlg.getRootPane().setDefaultButton(closeBtn);
        dlg.getRootPane().registerKeyboardAction(e -> dlg.dispose(),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        btnPanel.add(copyBtn);
        btnPanel.add(closeBtn);

        JPanel content = new JPanel(new BorderLayout(0, 4));
        content.setBorder(new EmptyBorder(8, 8, 4, 8));
        content.add(scroll,   BorderLayout.CENTER);
        content.add(btnPanel, BorderLayout.SOUTH);

        dlg.setContentPane(content);
        dlg.pack();
        dlg.setLocationRelativeTo(parent);
        dlg.setVisible(true);
    }

    // -----------------------------------------------------------------------

    private static String buildReport(File file) {
        StringBuilder sb = new StringBuilder();
        sb.append("File: ").append(file.getAbsolutePath()).append("\n");
        sb.append("=".repeat(80)).append("\n\n");

        // ── Capture JUL output from Prowide during all parsing ─────────────
        SwiftMessage swiftMsg  = null;
        List<String> parseErrors = List.of();
        AbstractMT   typedMt   = null;
        Exception    parseEx   = null;
        Exception    typedEx   = null;
        List<String> prowideLog;
        try (ProwideLogCapture cap = ProwideLogCapture.start()) {
            try {
                // ── 1a. Low-level parse ──────────────────────────────────
                SwiftParser parser = new SwiftParser(file);
                swiftMsg    = parser.message();
                parseErrors = parser.getErrors() != null ? parser.getErrors() : List.of();

                // ── 1b. Typed parse (AbstractMT) ─────────────────────────
                try {
                    typedMt = AbstractMT.parse(file);
                } catch (Exception ex) {
                    typedEx = ex;
                }
            } catch (IOException ex) {
                parseEx = ex;
            } catch (Exception ex) {
                parseEx = ex;
            }
            prowideLog = cap.stop();
        }

        // ── 2. JUL log messages (WARNUNG / SCHWERWIEGEND from Prowide) ─────
        sb.append("PROWIDE LOG\n");
        sb.append("-".repeat(40)).append("\n");
        if (prowideLog.isEmpty()) {
            sb.append("  none\n");
        } else {
            for (String line : prowideLog) sb.append("  ").append(line).append("\n");
        }
        sb.append("\n");

        // ── 3. Parser errors (SwiftParser.getErrors) ─────────────────────
        sb.append("PARSER ERRORS\n");
        sb.append("-".repeat(40)).append("\n");
        if (parseEx != null) {
            sb.append("  [EXCEPTION] ").append(parseEx.getClass().getSimpleName())
              .append(": ").append(parseEx.getMessage()).append("\n");
        } else if (parseErrors.isEmpty()) {
            sb.append("  none\n");
        } else {
            for (String e : parseErrors) sb.append("  [ERROR] ").append(e).append("\n");
        }
        sb.append("\n");

        if (swiftMsg == null) {
            sb.append("No message object returned by parser.\n");
            return sb.toString();
        }

        // ── 4. Block structure ────────────────────────────────────────────
        sb.append("BLOCK STRUCTURE\n");
        sb.append("-".repeat(40)).append("\n");
        try {
            appendValueBlock(sb, "Block 1 (Basic Header)   ", swiftMsg.getBlock1());
            appendValueBlock(sb, "Block 2 (App Header)     ", swiftMsg.getBlock2());
            appendTagBlock(sb,   "Block 3 (User Header)    ", swiftMsg.getBlock3());
            appendBlock4(sb, swiftMsg.getBlock4());
            appendTagBlock(sb,   "Block 5 (Trailer)        ", swiftMsg.getBlock5());
        } catch (Exception ex) {
            sb.append("  (error reading blocks: ").append(ex.getMessage()).append(")\n");
        }
        sb.append("\n");

        // ── 5. Message info ───────────────────────────────────────────────
        sb.append("MESSAGE INFO\n");
        sb.append("-".repeat(40)).append("\n");
        try {
            SwiftBlock2 b2 = swiftMsg.getBlock2();
            String msgType = b2 != null ? nvl(b2.getMessageType()) : "";
            sb.append("  Type:      ").append(msgType.isEmpty() ? "(unknown – block 2 absent)" : "MT" + msgType).append("\n");
            sb.append("  Sender:    ").append(nvl(swiftMsg.getSender())).append("\n");
            sb.append("  Receiver:  ").append(nvl(swiftMsg.getReceiver())).append("\n");
            SwiftBlock1 b1 = swiftMsg.getBlock1();
            if (b1 != null) {
                sb.append("  Service ID: ").append(nvl(b1.getServiceId())).append("\n");
                sb.append("  LT Address: ").append(nvl(b1.getLogicalTerminal())).append("\n");
                sb.append("  Session:    ").append(nvl(b1.getSessionNumber())).append("\n");
                sb.append("  Seq. No.:   ").append(nvl(b1.getSequenceNumber())).append("\n");
            }
        } catch (Exception ex) {
            sb.append("  (error reading message info: ").append(ex.getMessage()).append(")\n");
        }
        sb.append("\n");

        // ── 6. AbstractMT.toJson ──────────────────────────────────────────
        sb.append("JSON (AbstractMT.toJson)\n");
        sb.append("-".repeat(40)).append("\n");
        if (typedEx != null) {
            sb.append("  (").append(typedEx.getClass().getSimpleName()).append(": ")
              .append(typedEx.getMessage()).append(")\n");
        } else if (typedMt != null) {
            try {
                sb.append(typedMt.toJson()).append("\n");
            } catch (Exception ex) {
                sb.append("  (toJson failed: ").append(ex.getMessage()).append(")\n");
            }
        } else {
            sb.append("  (could not create typed MT object – block 2 may be missing)\n");
        }
        sb.append("\n");

        // ── 7. SwiftMessage.toJson ────────────────────────────────────────
        sb.append("JSON (SwiftMessage.toJson)\n");
        sb.append("-".repeat(40)).append("\n");
        try {
            sb.append(swiftMsg.toJson()).append("\n");
        } catch (Exception ex) {
            sb.append("  (").append(ex.getClass().getSimpleName()).append(": ")
              .append(ex.getMessage()).append(")\n");
        }

        return sb.toString();
    }

    // -----------------------------------------------------------------------

    private static void appendValueBlock(StringBuilder sb, String label, SwiftValueBlock block) {
        if (block == null) {
            sb.append("  ").append(label).append("ABSENT\n");
        } else {
            sb.append("  ").append(label).append("PRESENT");
            String val = block.getValue();
            if (val != null && !val.isBlank()) {
                String preview = val.replace("\n", " ").replace("\r", "");
                if (preview.length() > 60) preview = preview.substring(0, 57) + "...";
                sb.append(" [").append(preview).append("]");
            }
            sb.append("\n");
        }
    }

    private static void appendTagBlock(StringBuilder sb, String label, SwiftBlock block) {
        if (block == null) sb.append("  ").append(label).append("ABSENT\n");
        else               sb.append("  ").append(label).append("PRESENT\n");
    }

    private static void appendBlock4(StringBuilder sb, SwiftBlock4 b4) {
        if (b4 == null) {
            sb.append("  Block 4 (Text Body)      ABSENT\n");
        } else {
            int fields = b4.size();
            sb.append("  Block 4 (Text Body)      PRESENT – ").append(fields)
              .append(fields == 1 ? " field\n" : " fields\n");
        }
    }

    private static String nvl(String s) { return s != null ? s : ""; }
}