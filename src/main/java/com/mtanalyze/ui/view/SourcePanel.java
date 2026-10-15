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
package com.mtanalyze.ui.view;

import com.mtanalyze.model.Entry;
import com.mtanalyze.model.EntrySelectionListener;
import com.mtanalyze.model.SwiftMessage;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SourcePanel extends RoundedPanel implements EntrySelectionListener {

    private static final Pattern HL_BLOCK = Pattern.compile(
        "\\{[0-9A-Z]+:[^}\n]*}|-}");
    private static final Pattern HL_TAG = Pattern.compile(
        "^:[^:\n]+:", Pattern.MULTILINE);
    private static final Pattern HL_QUALIFIER = Pattern.compile(
        ":[A-Z0-9]{4}//");

    private static final Color HL_COLOR_BLOCK = new Color(0x9B59B6);
    private static final Color HL_COLOR_TAG   = new Color(0x2585D4);
    private static final Color HL_COLOR_QUAL  = new Color(0x1A9E8E);

    private static final Highlighter.HighlightPainter PAINTER_MATCH =
            new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 220, 0));
    private static final Highlighter.HighlightPainter PAINTER_CURRENT =
            new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 140, 0));

    private final JTextPane      textPane;
    private final JTextField     searchField;
    private final JLabel         matchLabel;
    private final JButton        prevBtn;
    private final JButton        nextBtn;
    private final List<Object>   matchTags   = new ArrayList<>();
    private final List<Integer>  matchStarts = new ArrayList<>();
    private int currentMatch = -1;

    public SourcePanel() {
        super(new BorderLayout(4, 4));
        setBorder(new EmptyBorder(6, 6, 6, 6));

        textPane = new JTextPane() {
            @Override public boolean getScrollableTracksViewportWidth() { return false; }
            @Override public void copy() {
                if (!copyFormatted(getSelectionStart(), getSelectionEnd())) super.copy();
            }
        };
        textPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textPane.setEditable(false);
        textPane.setBackground(Color.WHITE);
        textPane.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed (MouseEvent e) { if (e.isPopupTrigger()) showPopup(e); }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) showPopup(e); }
            private void showPopup(MouseEvent e) {
                int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
                boolean hasSel = textPane.getSelectedText() != null && !textPane.getSelectedText().isEmpty();
                JMenuItem copyItem      = new JMenuItem("Copy");
                JMenuItem selectAllItem = new JMenuItem("Select All");
                copyItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask));
                selectAllItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, menuMask));
                copyItem.setEnabled(hasSel);
                copyItem.addActionListener(ae -> textPane.copy());
                selectAllItem.addActionListener(ae -> textPane.selectAll());
                JPopupMenu popup = new JPopupMenu();
                popup.add(copyItem);
                popup.add(selectAllItem);
                popup.show(e.getComponent(), e.getX(), e.getY());
            }
        });
        add(new JScrollPane(textPane), BorderLayout.CENTER);

        // ── Search bar ────────────────────────────────────────────────────
        searchField = new JTextField(18);
        searchField.putClientProperty("JTextField.placeholderText", "Search…");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { SwingUtilities.invokeLater(() -> runSearch(true)); }
            @Override public void removeUpdate(DocumentEvent e)  { SwingUtilities.invokeLater(() -> runSearch(true)); }
            @Override public void changedUpdate(DocumentEvent e) { /* style changes don't affect search */ }
        });
        searchField.addActionListener(e -> navigateNext());

        prevBtn = makeNavBtn("▲", "Previous match (Shift+Enter)");
        nextBtn = makeNavBtn("▼", "Next match (Enter)");
        prevBtn.addActionListener(e -> navigatePrev());
        nextBtn.addActionListener(e -> navigateNext());
        prevBtn.setEnabled(false);
        nextBtn.setEnabled(false);

        matchLabel = new JLabel("  ");
        matchLabel.setFont(matchLabel.getFont().deriveFont(11f));
        matchLabel.setBorder(new EmptyBorder(0, 4, 0, 0));

        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 2));
        searchBar.add(searchField);
        searchBar.add(prevBtn);
        searchBar.add(nextBtn);
        searchBar.add(matchLabel);
        add(searchBar, BorderLayout.SOUTH);

        // Shift+Enter in search field → prev
        searchField.getInputMap().put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, java.awt.event.InputEvent.SHIFT_DOWN_MASK),
                "prevMatch");
        searchField.getActionMap().put("prevMatch", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { navigatePrev(); }
        });

        // Ctrl/Cmd+F anywhere in the panel → focus search field
        KeyStroke ctrlF = KeyStroke.getKeyStroke(KeyEvent.VK_F,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        textPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ctrlF, "focusSearch");
        textPane.getActionMap().put("focusSearch", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                searchField.requestFocusInWindow();
                searchField.selectAll();
            }
        });
    }

    public void showMessage(String rawText) {
        textPane.setText(rawText);
        textPane.setCaretPosition(0);
        SwingUtilities.invokeLater(() -> {
            applyHighlighting();
            runSearch(false);
        });
    }

    public void clear() {
        textPane.setText("");
        clearSearchHighlights();
        matchStarts.clear();
        matchLabel.setText("  ");
        prevBtn.setEnabled(false);
        nextBtn.setEnabled(false);
    }

    // -----------------------------------------------------------------------
    // EntrySelectionListener
    // -----------------------------------------------------------------------

    @Override public void onSingleEntry(Entry entry, SwiftMessage message) { showMessage(message.raw().message()); }
    @Override public void onMultipleEntries(List<Entry> entries)           { clear(); }
    @Override public void onDeselect()                                     { clear(); }

    // -----------------------------------------------------------------------
    // Rich-text copy (HTML for Outlook / Word)
    // -----------------------------------------------------------------------

    private boolean copyFormatted(int start, int end) {
        if (end <= start) return false;
        StyledDocument doc = textPane.getStyledDocument();
        try {
            String plain = doc.getText(start, end - start);
            String html = buildHtml(doc, start, end);
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new RichSelection(html, plain), null);
            return true;
        } catch (BadLocationException | RuntimeException e) {
            return false;
        }
    }

    private static String buildHtml(StyledDocument doc, int start, int end) throws BadLocationException {
        StringBuilder sb = new StringBuilder("<div style=\"font-family:Consolas,'Courier New',monospace;font-size:10pt\">");
        int pos = start;
        while (pos < end) {
            Element para = doc.getParagraphElement(pos);
            int pEnd = Math.min(para.getEndOffset(), end);
            float indent = StyleConstants.getLeftIndent(para.getAttributes());
            sb.append("<p style=\"margin:0 0 0 ").append(Math.round(indent))
              .append("pt;font-family:Consolas,'Courier New',monospace;font-size:10pt\">");
            int runPos = pos;
            while (runPos < pEnd) {
                Element leaf = doc.getCharacterElement(runPos);
                int runEnd = Math.min(leaf.getEndOffset(), pEnd);
                String text = doc.getText(runPos, runEnd - runPos).replace("\n", "");
                if (!text.isEmpty()) {
                    AttributeSet a = leaf.getAttributes();
                    Color fg = StyleConstants.getForeground(a);
                    boolean bold = StyleConstants.isBold(a);
                    boolean styled = bold || (fg != null && !fg.equals(Color.BLACK));
                    if (styled) {
                        sb.append("<span style=\"");
                        if (fg != null) sb.append(String.format("color:#%06X;", fg.getRGB() & 0xFFFFFF));
                        if (bold) sb.append("font-weight:bold;");
                        sb.append("\">");
                    }
                    sb.append(escapeHtml(text));
                    if (styled) sb.append("</span>");
                }
                runPos = runEnd;
            }
            sb.append("</p>");
            pos = pEnd;
            if (para.getEndOffset() >= doc.getLength()) break;
        }
        return sb.append("</div>").toString();
    }

    private static String escapeHtml(String t) {
        StringBuilder sb = new StringBuilder(t.length() + 16);
        for (char c : t.toCharArray()) {
            switch (c) {
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '&' -> sb.append("&amp;");
                case ' ' -> sb.append("&nbsp;");
                default  -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static final class RichSelection implements java.awt.datatransfer.Transferable {
        private static final java.awt.datatransfer.DataFlavor HTML;
        static {
            java.awt.datatransfer.DataFlavor f;
            try { f = new java.awt.datatransfer.DataFlavor("text/html;class=java.lang.String"); }
            catch (ClassNotFoundException e) { f = java.awt.datatransfer.DataFlavor.stringFlavor; }
            HTML = f;
        }
        private final String html;
        private final String plain;
        RichSelection(String html, String plain) { this.html = html; this.plain = plain; }
        @Override public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
            return new java.awt.datatransfer.DataFlavor[] { HTML, java.awt.datatransfer.DataFlavor.stringFlavor };
        }
        @Override public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor f) {
            return HTML.equals(f) || java.awt.datatransfer.DataFlavor.stringFlavor.equals(f);
        }
        @Override public Object getTransferData(java.awt.datatransfer.DataFlavor f)
                throws java.awt.datatransfer.UnsupportedFlavorException {
            if (HTML.equals(f)) return html;
            if (java.awt.datatransfer.DataFlavor.stringFlavor.equals(f)) return plain;
            throw new java.awt.datatransfer.UnsupportedFlavorException(f);
        }
    }

    // -----------------------------------------------------------------------
    // Search
    // -----------------------------------------------------------------------

    private void runSearch(boolean resetIndex) {
        clearSearchHighlights();
        matchStarts.clear();
        String query = searchField.getText();
        if (query.isEmpty()) {
            matchLabel.setText("  ");
            prevBtn.setEnabled(false);
            nextBtn.setEnabled(false);
            return;
        }
        String text;
        try {
            int len = textPane.getDocument().getLength();
            text = textPane.getDocument().getText(0, len);
        } catch (BadLocationException e) { return; }

        String lq = query.toLowerCase(Locale.ROOT);
        String lt = text.toLowerCase(Locale.ROOT);
        int idx = 0;
        while ((idx = lt.indexOf(lq, idx)) >= 0) {
            matchStarts.add(idx);
            idx += lq.length();
        }
        if (matchStarts.isEmpty()) {
            currentMatch = -1;
            matchLabel.setText("0 / 0");
            prevBtn.setEnabled(false);
            nextBtn.setEnabled(false);
            return;
        }
        if (resetIndex || currentMatch < 0) currentMatch = 0;
        else currentMatch = Math.min(currentMatch, matchStarts.size() - 1);
        applySearchHighlights(query.length());
    }

    private void navigateNext() {
        if (matchStarts.isEmpty()) return;
        currentMatch = (currentMatch + 1) % matchStarts.size();
        applySearchHighlights(searchField.getText().length());
    }

    private void navigatePrev() {
        if (matchStarts.isEmpty()) return;
        currentMatch = (currentMatch - 1 + matchStarts.size()) % matchStarts.size();
        applySearchHighlights(searchField.getText().length());
    }

    private void applySearchHighlights(int qlen) {
        clearSearchHighlights();
        Highlighter hl = textPane.getHighlighter();
        for (int i = 0; i < matchStarts.size(); i++) {
            int start = matchStarts.get(i);
            try {
                matchTags.add(hl.addHighlight(start, start + qlen,
                        i == currentMatch ? PAINTER_CURRENT : PAINTER_MATCH));
            } catch (BadLocationException ignored) { /* positions come from document text */ }
        }
        if (currentMatch >= 0 && currentMatch < matchStarts.size()) {
            try {
                java.awt.geom.Rectangle2D r = textPane.modelToView2D(matchStarts.get(currentMatch));
                if (r != null) textPane.scrollRectToVisible(r.getBounds());
            } catch (BadLocationException ignored) { /* positions come from document text */ }
        }
        matchLabel.setText((currentMatch + 1) + " / " + matchStarts.size());
        prevBtn.setEnabled(true);
        nextBtn.setEnabled(true);
    }

    private void clearSearchHighlights() {
        Highlighter hl = textPane.getHighlighter();
        for (Object tag : matchTags) hl.removeHighlight(tag);
        matchTags.clear();
    }

    private static JButton makeNavBtn(String text, String tooltip) {
        JButton btn = new JButton(text);
        btn.setToolTipText(tooltip);
        btn.setMargin(new Insets(1, 5, 1, 5));
        btn.setFocusable(false);
        return btn;
    }

    // -----------------------------------------------------------------------
    // SWIFT syntax highlighting
    // -----------------------------------------------------------------------

    private void applyHighlighting() {
        StyledDocument doc = textPane.getStyledDocument();
        int len = doc.getLength();
        if (len == 0) return;
        try {
            String text = doc.getText(0, len);

            SimpleAttributeSet base = new SimpleAttributeSet();
            StyleConstants.setFontFamily(base, Font.MONOSPACED);
            StyleConstants.setFontSize(base, 12);
            StyleConstants.setBold(base, false);
            doc.setCharacterAttributes(0, len, base, true);

            applyIndentation(doc, text, len);

            SimpleAttributeSet bold = new SimpleAttributeSet();
            StyleConstants.setBold(bold, true);
            Matcher mt = HL_TAG.matcher(text);
            while (mt.find()) {
                int eol = text.indexOf('\n', mt.end());
                if (eol < 0) eol = len;
                if (eol > mt.end())
                    doc.setCharacterAttributes(mt.end(), eol - mt.end(), bold, false);
            }

            applyHl(doc, text, HL_BLOCK,     hlColor(HL_COLOR_BLOCK));
            applyHl(doc, text, HL_TAG,       hlColor(HL_COLOR_TAG));
            applyHl(doc, text, HL_QUALIFIER, hlColor(HL_COLOR_QUAL));
        } catch (BadLocationException ignored) { /* positions derived from document length */ }
    }

    private static void applyIndentation(StyledDocument doc, String text, int docLen) {
        int indent = 0;
        int pos = 0;
        for (String line : text.split("\n", -1)) {
            if (pos >= docLen) break;
            if (line.startsWith(":16S:") && indent > 0) indent--;
            SimpleAttributeSet para = new SimpleAttributeSet();
            StyleConstants.setLeftIndent(para, indent * 16f);
            StyleConstants.setFirstLineIndent(para, 0f);
            doc.setParagraphAttributes(pos, Math.max(1, line.length()), para, false);
            if (line.startsWith(":16R:")) indent++;
            pos += line.length() + 1;
        }
    }

    private static SimpleAttributeSet hlColor(Color c) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, c);
        return a;
    }

    private static void applyHl(StyledDocument doc, String text, Pattern p, SimpleAttributeSet attrs) {
        Matcher m = p.matcher(text);
        while (m.find())
            doc.setCharacterAttributes(m.start(), m.end() - m.start(), attrs, false);
    }
}