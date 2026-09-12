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
            + buildTabsSection()
            + buildFilterRowSection()
            + buildQuickFilterSection()
            + buildRepositorySection()
            + buildContextMenuSection()
            + "</body></html>";
    }

    private static String buildShortcutsSection() {
        return "<h2>Keyboard Shortcuts</h2>"
            + "<table>"
            + "<tr><th>Key</th><th>Action</th></tr>"
            + "<tr><td><code>Ctrl+N</code></td><td>New Tab — open an empty MT Entries tab</td></tr>"
            + "<tr><td><code>Ctrl+O</code></td><td>Open... — load a SWIFT MT file into a new tab</td></tr>"
            + "<tr><td><code>Ctrl+S</code></td><td>Save... — export the active tab's view as an MT file</td></tr>"
            + "<tr><td><code>Ctrl+E</code></td><td>Save Excel... — export the active tab's view as an Excel file</td></tr>"
            + "<tr><td><code>Ctrl+Q</code></td><td>Exit the application</td></tr>"
            + "<tr><td><code>Ctrl+F</code></td><td>Focus the MT Entries search field (search only applies to that view)</td></tr>"
            + "<tr><td><code>Ctrl+Shift+F</code></td><td>Search Messages — query the Lucene index; results open in a new tab</td></tr>"
            + "<tr><td><code>Ctrl+Shift+E</code></td><td>Search Messages — query the Elasticsearch index; results open in a new tab</td></tr>"
            + "<tr><td><code>Ctrl+D</code></td><td>Show / hide the active tab's Detail panel</td></tr>"
            + "<tr><td><code>Ctrl+3</code></td><td>Show Notifications in the Detail panel</td></tr>"
            + "<tr><td><code>Ctrl+4</code></td><td>Show Tags in the Detail panel</td></tr>"
            + "<tr><td><code>Ctrl+5</code></td><td>Show Diff in the Detail panel</td></tr>"
            + "<tr><td><code>Ctrl+6</code></td><td>Show Source in the Detail panel</td></tr>"
            + "<tr><td><code>Ctrl+7</code></td><td>Show Components in the Detail panel</td></tr>"
            + "<tr><td><code>Ctrl+C</code></td><td>Copy selected cell value to clipboard</td></tr>"
            + "<tr><td><code>Ctrl+X</code></td><td>Cut selected text (text fields only)</td></tr>"
            + "<tr><td><code>Ctrl+V</code></td><td>Paste (text fields), or open the paste dialog to append raw SWIFT text</td></tr>"
            + "<tr><td><code>Ctrl+T</code></td><td>Copy Table — copy headers and all visible rows as tab-separated values</td></tr>"
            + "<tr><td><code>Delete</code></td><td>Remove the selected row from the current view</td></tr>"
            + "<tr><td><code>F1</code></td><td>Open this help dialog</td></tr>"
            + "<tr><td><code>Esc</code></td><td>Close this dialog</td></tr>"
            + "</table>"
            + "<p><i>On macOS, <code>Cmd</code> is used in place of <code>Ctrl</code> for these shortcuts.</i></p>"
            + "<hr/>";
    }

    private static String buildTabsSection() {
        return "<h2>Tabs</h2>"
            + "<p>Each tab is an independent workspace with its own MT Entries table, filters, "
            + "column layout and Detail panel. Click <b>+</b> at the end of the tab strip or press "
            + "<code>Ctrl+N</code> for an empty tab; <b>File &gt; Open...</b> or <b>Import Directory...</b> "
            + "always loads into a new tab, while <b>Append</b>, <b>Save</b> and the "
            + "Export actions apply to the active tab. Close a tab with the <b>×</b> on the tab itself; "
            + "closing the last tab opens a fresh empty one.</p>"
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
            + "&nbsp;&nbsp;<code>^DE+^AT</code> &nbsp;— begins with DE or AT<br/>"
            + "Expressions in different columns are always combined with <b>AND</b> — "
            + "a row must satisfy every filtered column.</p>"
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

    private static String buildRepositorySection() {
        return "<h2>Message Repository (Index &amp; Search)</h2>"
            + "<p>The <b>Lucene</b> and <b>Elasticsearch</b> menus each keep a full-text index of "
            + "your messages &mdash; two independent flavours you can use side by side: an "
            + "embedded Lucene index (no server, just a directory on disk) and an Elasticsearch "
            + "index on a cluster you configure. Both menus have the same three items.</p>"
            + "<h3>Lucene</h3>"
            + "<table>"
            + "<tr><th>Item</th><th>Description</th></tr>"
            + "<tr><td><b>Index Messages</b></td>"
                + "<td>Adds every message in the active MT Entries tab to the index (progress bar, "
                + "cancellable). Indexing is idempotent &mdash; a message is identified by a hash "
                + "of its content, so re-indexing the same message replaces its entry instead of "
                + "creating a duplicate.</td></tr>"
            + "<tr><td><b>Search Messages...</b> <code>(Ctrl+Shift+F)</code></td>"
                + "<td>Enter a Lucene query; the matching messages open in a new tab.</td></tr>"
            + "<tr><td><b>Clear Index...</b></td><td>Removes all documents from the index.</td></tr>"
            + "</table>"
            + "<p>The index directory (default <code>~/.mtanalyze/swift-index</code>) and the maximum "
            + "number of hits (default 100) are set under <b>Settings &gt; Advanced &gt; Lucene Search</b>.</p>"
            + "<h3>Elasticsearch</h3>"
            + "<table>"
            + "<tr><th>Item</th><th>Description</th></tr>"
            + "<tr><td><b>Index Messages</b></td>"
                + "<td>Adds every message in the active MT Entries tab to the configured Elasticsearch "
                + "index (progress bar, cancellable). Indexing is idempotent, same as Lucene, via a "
                + "content-hash document id.</td></tr>"
            + "<tr><td><b>Search Messages...</b> <code>(Ctrl+Shift+E)</code></td>"
                + "<td>Enter a <code>query_string</code> query (Elasticsearch's Lucene-like syntax); "
                + "the matching messages open in a new tab.</td></tr>"
            + "<tr><td><b>Clear Index...</b></td><td>Removes all documents from the index.</td></tr>"
            + "</table>"
            + "<p>Host, port, scheme, credentials, index name and the maximum number of hits (default "
            + "100) are set under <b>Settings &gt; Advanced &gt; Elasticsearch</b>. Unlike Lucene, this "
            + "is a real server connection &mdash; indexing and searching fail with a network error "
            + "dialog if the cluster is unreachable.</p>"
            + "<h3>Lucene query syntax</h3>"
            + "<p>Classic Lucene <code>QueryParser</code> syntax: boolean operators "
            + "(<code>AND</code>, <code>OR</code>, <code>NOT</code>), grouping <code>( )</code>, "
            + "phrases <code>\"...\"</code>, ranges <code>[a TO b]</code>, wildcards <code>* ?</code>. "
            + "A bare term with no <code>field:</code> prefix searches <code>raw_message</code>.</p>"
            + "<table>"
            + "<tr><th>Field</th><th>Contents</th><th>Matching</th></tr>"
            + "<tr><td><code>raw_message</code></td><td>the complete message text</td><td>analyzed, lowercased</td></tr>"
            + "<tr><td><code>mt</code></td><td>e.g. <code>536</code></td><td>verbatim, case-sensitive</td></tr>"
            + "<tr><td><code>sender</code> / <code>receiver</code></td><td>LT address, e.g. <code>BANKUS33AXXX</code></td>"
                + "<td>verbatim, case-sensitive</td></tr>"
            + "<tr><td><code>file_name</code></td><td>source label (tab title)</td><td>verbatim</td></tr>"
            + "<tr><td><code>tag_&lt;name&gt;</code></td><td>one SWIFT tag value, e.g. <code>tag_20C</code>, <code>tag_35B</code></td>"
                + "<td>analyzed; split on <code>: / ,</code> and whitespace, lowercased</td></tr>"
            + "<tr><td><code>tags_all</code></td><td>every indexed tag value combined</td><td>analyzed</td></tr>"
            + "</table>"
            + "<p>Only content-bearing tags are indexed &mdash; those whose name starts with "
            + "<code>20</code>, <code>35</code>, <code>70</code>, <code>94</code>, <code>95</code>, "
            + "<code>97</code> or <code>98</code>. Structural tags (<code>16R</code>/<code>16S</code>, "
            + "<code>23G</code>, <code>22F</code>, ...) are not.</p>"
            + "<p><b>Examples:</b></p>"
            + "<table>"
            + "<tr><td><code>mt:536 AND tag_35B:US0378331005</code></td></tr>"
            + "<tr><td><code>raw_message:\"APPLE INC\"</code></td></tr>"
            + "<tr><td><code>sender:BANKUS33AXXX</code></td></tr>"
            + "<tr><td><code>tag_20C:seme AND mt:(536 OR 537)</code></td></tr>"
            + "<tr><td><code>tag_98A:[20210101 TO 20211231] NOT tag_23G:CANC</code></td></tr>"
            + "</table>"
            + "<h3>Elasticsearch query syntax</h3>"
            + "<p>Elasticsearch's <code>query_string</code> syntax &mdash; very close to Lucene's: "
            + "boolean operators (<code>AND</code>, <code>OR</code>, <code>NOT</code>), grouping "
            + "<code>( )</code>, phrases <code>\"...\"</code>, wildcards <code>* ?</code>. A bare term "
            + "searches <code>raw_message</code>; use <code>field:value</code> for a specific field, "
            + "e.g. <code>mt</code>, <code>sender</code>, <code>receiver</code> or "
            + "<code>tag.&lt;name&gt;</code> (e.g. <code>tag.35B:US0378331005</code>).</p>"
            + "<hr/>";
    }

    private static String buildContextMenuSection() {
        return "<h2>Context Menus (right-click)</h2>"
            + "<h3>Entries Table — cell</h3>"
            + "<table>"
            + "<tr><th>Item</th><th>Description</th></tr>"
            + "<tr><td><b>Copy</b></td><td>Copies the clicked cell's text to the clipboard</td></tr>"
            + "<tr><td><b>Copy Table</b></td>"
                + "<td>Copies headers and all visible rows as tab-separated values — paste directly into Excel</td></tr>"
            + "<tr><td><b>Copy Visible Messages to Tab</b></td>"
                + "<td>Copies the messages currently visible (after filtering) into another open tab or a new one</td></tr>"
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