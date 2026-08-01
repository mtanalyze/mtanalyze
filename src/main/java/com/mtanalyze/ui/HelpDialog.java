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

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * Modal help dialog — opened via Help &gt; Help... or by pressing F1.
 * Describes keyboard shortcuts, the Filter Row and the Quick Filter.
 */
public final class HelpDialog {

    private HelpDialog() {}

    public static void show(JFrame owner) {
        JDialog dlg = new JDialog(owner, "MT Analyze Help", true);
        dlg.setLayout(new BorderLayout());
        dlg.add(buildEditorPane(), BorderLayout.CENTER);
        dlg.add(buildCloseButton(dlg), BorderLayout.SOUTH);
        dlg.pack();
        dlg.setLocationRelativeTo(owner);
        registerEscapeKey(dlg);
        dlg.setVisible(true);
    }

    // -----------------------------------------------------------------------
    // Dialog assembly
    // -----------------------------------------------------------------------

    private static JScrollPane buildEditorPane() {
        JEditorPane ep = new JEditorPane("text/html", buildHtml());
        ep.setEditable(false);
        ep.setCaretPosition(0);
        Color panelBg = UIManager.getColor("Panel.background");
        if (panelBg != null) ep.setBackground(panelBg);
        JScrollPane scroll = new JScrollPane(ep);
        scroll.setPreferredSize(new Dimension(660, 560));
        scroll.setBorder(null);
        return scroll;
    }

    private static JPanel buildCloseButton(JDialog dlg) {
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dlg.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        south.add(closeBtn);
        return south;
    }

    private static void registerEscapeKey(JDialog dlg) {
        dlg.getRootPane().registerKeyboardAction(
            e -> dlg.dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    // -----------------------------------------------------------------------
    // HTML assembly
    // -----------------------------------------------------------------------

    private static String buildHtml() {


        return "<html><head>" /* + buildCss(fgHex, bgHex, codeBg, divider, ff, fs) */ + "</head><body>"
            + buildShortcutsSection()
            + buildSequenceModeSection()
            + buildFilterRowSection()
            + buildQuickFilterSection()
            + buildContextMenuSection()
            + "</body></html>";
    }

    private static String buildShortcutsSection() {
        return "<h2>Keyboard Shortcuts</h2>"
            + "<table>"
            + "<tr><th>Key</th><th>Action</th></tr>"
            + "<tr><td><code>Ctrl+N</code></td><td>New — clear all entries and start a fresh session</td></tr>"
            + "<tr><td><code>Ctrl+O</code></td><td>Open session file (.mtd)</td></tr>"
            + "<tr><td><code>Ctrl+S</code></td><td>Save session — write all loaded messages to a .mtd file</td></tr>"
            + "<tr><td><code>Ctrl+Shift+O</code></td><td>Append MT file — add to the current view</td></tr>"
            + "<tr><td><code>Ctrl+R</code></td><td>Reload current file from disk</td></tr>"
            + "<tr><td><code>Ctrl+F</code></td><td>Focus the search field of the active panel</td></tr>"
            + "<tr><td><code>Ctrl+D</code></td><td>Show / hide the Detail panel</td></tr>"
            + "<tr><td><code>Ctrl+C</code></td><td>Copy selected cell value to clipboard</td></tr>"
            + "<tr><td><code>Space</code></td><td>Move focus to detail panel and select first row</td></tr>"
            + "<tr><td><code>←</code></td><td>Move focus back to entries table</td></tr>"
            + "<tr><td><code>F1</code></td><td>Open this help dialog</td></tr>"
            + "<tr><td><code>Esc</code></td><td>Close this dialog</td></tr>"
            + "</table>"
            + "<hr/>";
    }

    private static String buildSequenceModeSection() {
        return "<h2>Sequence Mode (SEQ / FLAT)</h2>"
            + "<p>The <b>SEQ / FLAT</b> button in the toolbar controls how tag columns are named "
            + "when MT messages contain sequences.</p>"
            + "<table>"
            + "<tr><th>Mode</th><th>Column naming</th><th>When to use</th></tr>"
            + "<tr><td><b>SEQ</b> (default)</td>"
                + "<td>Columns include the sequence prefix — e.g. <code>GENL/95P</code> "
                + "or <code>SUBSAFE/TRAD/36B</code>. Each sequence occurrence gets its own column.</td>"
                + "<td>When you need to distinguish the same tag in different sequences "
                + "(e.g. 95P in GENL vs. 95P in TRAD)</td></tr>"
            + "<tr><td><b>FLAT</b></td>"
                + "<td>Sequence prefix is removed — columns show only the tag name and qualifier, "
                + "e.g. <code>95P</code>. When the same tag appears in multiple sequences, "
                + "the values are merged into one column by occurrence order.</td>"
                + "<td>When you want a compact view without sequence structure, "
                + "or when comparing messages across different MT subtypes</td></tr>"
            + "</table>"
            + "<p>Column layouts (saved profiles) are maintained separately for SEQ and FLAT mode. "
            + "Switching mode rebuilds the table; filters and sort order are reset.</p>"
            + "<hr/>";
    }

    private static String buildFilterRowSection() {
        return "<h2>Filter Row</h2>"
            + "<p>A dropdown button row sits directly below the table header, one button per column. "
            + "Click a button to open an Excel-style checkbox list of all distinct values in that column. "
            + "Tick one or more values to restrict the table to matching rows. "
            + "Select <b>(All)</b> to clear the filter for that column. "
            + "Active columns show a count badge (e.g. <code>2 ✓</code>) on their button.</p>"
            + "<p><b>Right-click</b> any Filter Row button to open a context menu:</p>"
            + "<table>"
            + "<tr><th>Item</th><th>Description</th></tr>"
            + "<tr><td><b>Convert to Quick Filter</b></td>"
                + "<td>Converts all active dropdown selections into Quick Filter expressions "
                + "(e.g. EUR and USD become <code>=EUR+=USD</code>), writes them into the Quick Filter "
                + "fields, and clears the dropdown filter. Disabled when no dropdown filter is active.</td></tr>"
            + "</table>"
            + "<hr/>";
    }

    private static String buildQuickFilterSection() {
        return "<h2>Quick Filter</h2>"
            + "<p>A row of yellow text fields below the Filter Row, one per column. "
            + "Type an expression and press <b>Tab</b>, <b>Enter</b>, or click elsewhere to apply. "
            + "Clear a field to remove that column's filter. "
            + "Matching is case-insensitive.</p>"
            + buildQuickFilterTable()
            + "<p>Combine multiple values with <code>+</code> for <b>OR</b> logic within one field:<br/>"
            + "&nbsp;&nbsp;<code>=EUR+GBP</code> &nbsp;— cell is EUR or GBP<br/>"
            + "&nbsp;&nbsp;<code>^DE+^AT</code> &nbsp;— begins with DE or AT</p>"
            + "<h3>AND / OR Column Toggle</h3>"
            + "<p>The <b>AND/OR button</b> in the toolbar controls how expressions in different columns are combined:</p>"
            + "<table>"
            + "<tr><th>Mode</th><th>Behaviour</th></tr>"
            + "<tr><td><b>AND</b> (default)</td><td>A row is shown only if it satisfies the expressions in <i>all</i> filtered columns</td></tr>"
            + "<tr><td><b>OR</b></td><td>A row is shown if it satisfies the expression in <i>any one</i> filtered column</td></tr>"
            + "</table>"
            + "<p>Click the button to toggle between modes. The icon changes from a blue AND badge to an orange OR badge. "
            + "The dropdown Filter Row is always applied on top regardless of this setting.</p>"
            + "<h3>Profiles</h3>"
            + "<p>Right-click any Quick Filter field and choose <b>Save Quick Filter…</b> to save the current "
            + "expressions under a name. Load saved profiles from the <b>Quick Filter</b> combobox in the toolbar. "
            + "Right-click a profile name in the combobox to delete it. Profiles are stored in OS user preferences "
            + "and survive restarts.</p>"
            + "<hr/>";
    }

    private static String buildQuickFilterTable() {
        return "<table>"
            + "<tr><th>Expression</th><th>Meaning</th></tr>"
            + "<tr><td><code>value</code></td><td>Contains (default)</td></tr>"
            + "<tr><td><code>=value</code></td><td>Equal</td></tr>"
            + "<tr><td><code>=</code></td><td>Empty (field has no value)</td></tr>"
            + "<tr><td><code>!=value</code> &nbsp;or&nbsp; <code>!value</code> &nbsp;or&nbsp; <code>&lt;&gt;value</code></td>"
                + "<td>Not equal</td></tr>"
            + "<tr><td><code>!=</code></td><td>Not empty (field has any value)</td></tr>"
            + "<tr><td><code>^value</code></td><td>Begins with</td></tr>"
            + "<tr><td><code>$value</code></td><td>Ends with</td></tr>"
            + "<tr><td><code>%value</code></td><td>Contains</td></tr>"
            + "<tr><td><code>!^value</code></td><td>Does not begin with</td></tr>"
            + "<tr><td><code>!%value</code></td><td>Does not contain</td></tr>"
            + "<tr><td><code>&lt;value</code> &nbsp;/&nbsp; <code>&gt;value</code></td>"
                + "<td>Less / greater than</td></tr>"
            + "<tr><td><code>&lt;=value</code> &nbsp;/&nbsp; <code>&gt;=value</code></td>"
                + "<td>Less / greater or equal</td></tr>"
            + "<tr><td><code>lo-hi</code></td><td>Between lo and hi (inclusive)</td></tr>"
            + "</table>";
    }

    private static String buildContextMenuSection() {
        return "<h2>Context Menus (right-click)</h2>"
            + "<h3>Entries Table — cell</h3>"
            + "<table>"
            + "<tr><th>Item</th><th>Description</th></tr>"
            + "<tr><td><b>Copy</b></td><td>Copies the clicked cell's text to the clipboard</td></tr>"
            + "<tr><td><b>Copy Table</b></td>"
                + "<td>Copies headers and all visible rows as tab-separated values — paste directly into Excel</td></tr>"
            + "<tr><td><b>Paste</b></td><td>Open the paste dialog to append raw SWIFT text to the current view</td></tr>"
            + "<tr><td><b>Goto Tag</b></td>"
                + "<td>Selects and scrolls to the corresponding tag row in the detail panel. "
                + "Also triggered by <b>double-click</b> on a cell. "
                + "The correct occurrence is resolved automatically when the same tag appears multiple times.</td></tr>"
            + "<tr><td><b>Export Message</b></td><td>Export the raw SWIFT MT message for this row</td></tr>"
            + "<tr><td><b>Show in Editor</b></td><td>Open the source file in the system default text editor</td></tr>"
            + "<tr><td><b>Delete Row</b></td><td>Remove the selected row from the current view</td></tr>"
            + "</table>"
            + "<h3>Entries Table — column header</h3>"
            + "<table>"
            + "<tr><th>Item</th><th>Description</th></tr>"
            + "<tr><td><b>↑ Sort ascending / ↓ Sort descending</b></td><td>Sort the table by this column</td></tr>"
            + "<tr><td><b>✕ Clear sort</b></td><td>Remove all sort keys</td></tr>"
            + "<tr><td><b>⇤ Move to Start</b></td><td>Move this column to the first position</td></tr>"
            + "<tr><td><b>Hide</b></td><td>Hide this column</td></tr>"
            + "<tr><td><b>Save Column Layout…</b></td><td>Save the current column order and visibility as a named profile</td></tr>"
            + "</table>"
            + "<h3>Filter Row button</h3>"
            + "<table>"
            + "<tr><th>Item</th><th>Description</th></tr>"
            + "<tr><td><b>Convert to Quick Filter</b></td>"
                + "<td>Convert all active dropdown selections to Quick Filter expressions and clear the dropdown filter</td></tr>"
            + "</table>"
            + "<h3>Detail Panel — cell</h3>"
            + "<table>"
            + "<tr><th>Item</th><th>Description</th></tr>"
            + "<tr><td><b>Copy</b></td><td>Copies the clicked cell's text to the clipboard</td></tr>"
            + "<tr><td><b>Copy Table</b></td><td>Copies the full detail view as tab-separated values</td></tr>"
            + "<tr><td><b>Add to Dictionary</b></td><td>Add the selected value to the qualifier/value dictionary for hover tooltips</td></tr>"
            + "</table>";
    }
}