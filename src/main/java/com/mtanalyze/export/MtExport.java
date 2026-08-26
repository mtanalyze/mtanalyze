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
package com.mtanalyze.export;

import com.mtanalyze.util.FileChoosers;
import com.prowidesoftware.swift.model.SwiftTagListBlock;
import com.prowidesoftware.swift.model.Tag;
import com.prowidesoftware.swift.model.mt.AbstractMT;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class MtExport {

    private static final String FALLBACK_BIC  = "BANKBEBBAXXX";
    private static final String BLOCK_CLOSE   = "}\r\n";

    /** Block1 value produced by MtFileIO.buildSwiftWrapper for content without an original header. */
    private static final String SYNTHETIC_BLOCK1_VALUE = "F01" + FALLBACK_BIC + "0000000000";

    /** Returns {sender, receiver} or an empty array if the user cancelled. */
    private static String[] showBicDialog(Frame owner, String sender, String receiver) {
        JTextField senderField   = new JTextField(sender,   20);
        JTextField receiverField = new JTextField(receiver, 20);

        JPanel panel = new JPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.insets  = new java.awt.Insets(4, 4, 4, 4);
        c.anchor  = java.awt.GridBagConstraints.WEST;
        c.fill    = java.awt.GridBagConstraints.HORIZONTAL;

        c.gridx = 0; c.gridy = 0; c.weightx = 0; panel.add(new JLabel("Sender BIC:"),   c);
        c.gridx = 1; c.gridy = 0; c.weightx = 1; panel.add(senderField,                 c);
        c.gridx = 0; c.gridy = 1; c.weightx = 0; panel.add(new JLabel("Receiver BIC:"), c);
        c.gridx = 1; c.gridy = 1; c.weightx = 1; panel.add(receiverField,               c);

        int result = JOptionPane.showConfirmDialog(owner, panel, "Export MT Messages",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return new String[0];
        return new String[]{ senderField.getText().trim(), receiverField.getText().trim() };
    }

    public void exportSingle(Frame owner, AbstractMT message, String sender, String receiver,
                             String sourcePath, Consumer<String> status) {
        String[] bic = showBicDialog(owner, sender, receiver);
        if (bic.length == 0) return;
        String mtType = getMtType(message);
        String snd = padBic(bic[0]);
        String rcv = padBic(bic[1]);
        String msgText = buildMessage(message, snd, rcv);

        JFileChooser fc = getJFileChooser(sourcePath, mtType);
        if (fc.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) return;

        File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".txt"))
            file = new File(file.getAbsolutePath() + ".txt");
        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            bw.write(msgText);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(owner, "Error during export:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        status.accept("Exported to: " + file.getAbsolutePath());
        CsvExport.offerOpenFile(owner, file);
    }

    private static JFileChooser getJFileChooser(String sourcePath, String mtType) {
        File sourceFile = sourcePath != null && !sourcePath.isEmpty() ? new File(sourcePath) : null;
        File initialDir = sourceFile != null && sourceFile.getParentFile() != null
                ? sourceFile.getParentFile() : null;
        JFileChooser fc = FileChoosers.create(initialDir);
        fc.setDialogTitle("Export MT Message");
        fc.setFileFilter(new FileNameExtensionFilter("SWIFT Message Files (*.txt)", "txt"));
        fc.setSelectedFile(new File(initialDir != null ? initialDir : new File("."),
                "MT" + mtType + ".txt"));
        return fc;
    }

    public void export(Frame owner, List<AbstractMT> messages, String sender, String receiver,
                       Consumer<String> status) {
        if (messages.isEmpty()) {
            JOptionPane.showMessageDialog(owner, "Please load a SWIFT file first.",
                    "No Data", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String[] bic = showBicDialog(owner, sender, receiver);
        if (bic.length == 0) return;

        JFileChooser fc = FileChoosers.create();
        fc.setDialogTitle("Export MT Messages");
        fc.setFileFilter(new FileNameExtensionFilter(
                "MT Message Files (*.txt, *.fin)", "txt", "fin"));
        fc.setSelectedFile(new File("SWIFT_Export.txt"));
        if (fc.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) return;

        File file = ensureExtension(fc.getSelectedFile());
        String snd = padBic(bic[0]);
        String rcv = padBic(bic[1]);

        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            for (AbstractMT mt : messages) {
                bw.write(buildMessage(mt, snd, rcv));
                bw.write("\r\n");
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(owner, "Error during export:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int n = messages.size();
        status.accept("Exported " + n + (n == 1 ? " message" : " messages")
                + " to: " + file.getAbsolutePath());
        CsvExport.offerOpenFile(owner, file);
    }

    private static String buildMessage(AbstractMT mt, String sender, String receiver) {
        com.prowidesoftware.swift.model.SwiftMessage sm = mt.getSwiftMessage();
        com.prowidesoftware.swift.model.SwiftBlock1 b1 = sm.getBlock1();
        String b1Value = b1 != null ? b1.getValue() : null;
        boolean hasOriginalHeader = b1Value != null && !b1Value.isEmpty()
                                    && !b1Value.equals(SYNTHETIC_BLOCK1_VALUE);

        StringBuilder sb = new StringBuilder();
        if (hasOriginalHeader) {
            appendOriginalHeaders(sb, sm, b1Value);
        } else {
            sb.append("{1:F01").append(sender).append("0000000000}\r\n");
            sb.append("{2:I").append(getMtType(mt)).append(receiver).append("U3003}\r\n");
        }
        appendBlock4(sb, sm.getBlock4());
        return sb.toString();
    }

    private static void appendOriginalHeaders(StringBuilder sb,
            com.prowidesoftware.swift.model.SwiftMessage sm, String b1Value) {
        sb.append("{1:").append(b1Value).append(BLOCK_CLOSE);
        com.prowidesoftware.swift.model.SwiftBlock2 b2 = sm.getBlock2();
        if (b2 != null && b2.getValue() != null && !b2.getValue().isEmpty())
            sb.append("{2:").append(b2.getValue()).append(BLOCK_CLOSE);
        com.prowidesoftware.swift.model.SwiftBlock3 b3 = sm.getBlock3();
        if (b3 != null && !b3.isEmpty()) {
            sb.append("{3:");
            for (Tag t : b3.getTags())
                sb.append("{").append(t.getName()).append(":")
                  .append(t.getValue() != null ? t.getValue() : "").append("}");
            sb.append(BLOCK_CLOSE);
        }
    }

    private static void appendBlock4(StringBuilder sb, SwiftTagListBlock b4) {
        sb.append("{4:\r\n");
        if (b4 != null) {
            for (Tag t : b4.getTags()) {
                sb.append(":").append(t.getName()).append(":");
                if (t.getValue() != null) sb.append(t.getValue());
                sb.append("\r\n");
            }
        }
        sb.append("-}");
    }

    private static String getMtType(AbstractMT mt) {
        try {
            com.prowidesoftware.swift.model.SwiftBlock2 b2 = mt.getSwiftMessage().getBlock2();
            if (b2 != null) {
                String type = b2.getMessageType();
                if (type != null && !type.isEmpty()) return type;
            }
        } catch (Exception ignored) {
            // NOP
        }
        return "536";
    }

    /**
     * Pads/trims a user-supplied BIC to the 12-character SWIFT LT address format:
     * BIC8(8) + LT-code(1) + branch(3).
     * BIC8 → append "AXXX"; BIC11 → insert "A" between BIC8 and branch.
     */
    private static String padBic(String bic) {
        if (bic == null || bic.trim().isEmpty()) return FALLBACK_BIC;
        StringBuilder b = new StringBuilder(bic.trim().toUpperCase(Locale.ROOT));
        if (b.length() == 8)  return b + "AXXX";
        if (b.length() == 11) return b.substring(0, 8) + "A" + b.substring(8);
        if (b.length() >= 12) return b.substring(0, 12);
        while (b.length() < 12) b.append("X");
        return b.toString();
    }

    private static File ensureExtension(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".txt") || name.endsWith(".fin") || name.endsWith(".mt")) return file;
        return new File(file.getAbsolutePath() + ".txt");
    }

}