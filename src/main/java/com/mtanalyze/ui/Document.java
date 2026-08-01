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

import com.mtanalyze.config.SystemConfig;
import com.mtanalyze.export.MtExport;
import com.mtanalyze.export.ProjectIO;
import com.mtanalyze.model.Entry;
import com.mtanalyze.model.EntrySelectionListener;
import com.mtanalyze.model.MessageOrigin;
import com.mtanalyze.model.SwiftMessage;
import com.mtanalyze.parser.HintDictionary;
import com.mtanalyze.ui.view.NotificationPanel;
import com.mtanalyze.ui.view.TagView;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * One open document: an entries table + its detail panel + the importer/session state
 * that goes with them. {@link MtAnalyzeFrame} hosts one or more Documents, one per tab.
 */
final class Document {

    private static final String MSG_SINGULAR = " message";
    private static final String MSG_PLURAL   = " messages";
    private static final String ERROR_TITLE  = "Error";

    private final MtAnalyzeFrame        owner;
    private final HintDictionary        dict;
    private final SystemConfig          config;
    private final ImportService         importService;
    private final MtExport              mtExport;
    private final BottomPanelController bottomCtrl;
    private final Consumer<String>      setStatus;
    private final BooleanSupplier       isPowerUser;
    private final Consumer<Document>    onStateChanged;

    private final MtEntryPanel          entryPanel;
    private final TagView               tagPanel;
    private final DetailPanelController detailCtrl;
    private final FileImporter          importer;
    private final List<EntrySelectionListener> selectionListeners = new ArrayList<>();
    private final JSplitPane            rootSplit;

    private File   currentSessionFile;
    private File   lastLoadedFile;
    private JTable focusedTable;

    private boolean reloadable;
    private boolean hasContent;
    private boolean hasSelection;
    private boolean tagsActive = true;
    private boolean componentsActive;

    Document(MtAnalyzeFrame owner,
             Preferences prefs, MtEntryPanel.PrefKeys entryPrefKeys,
             HintDictionary dict, SystemConfig config, ImportService importService,
             MtExport mtExport, BottomPanelController bottomCtrl,
             Consumer<String> setStatus, BooleanSupplier isPowerUser,
             Consumer<Document> onStateChanged) {
        this.owner          = owner;
        this.dict           = dict;
        this.config         = config;
        this.importService  = importService;
        this.mtExport       = mtExport;
        this.bottomCtrl     = bottomCtrl;
        this.setStatus      = setStatus;
        this.isPowerUser    = isPowerUser;
        this.onStateChanged = onStateChanged;

        entryPanel = new MtEntryPanel(createEntryPanelHost(), prefs, entryPrefKeys, dict);
        entryPanel.init();
        importer = new FileImporter(createImportContext());

        tagPanel = new TagView(createTagPanelHost(), prefs, dict);
        selectionListeners.add(tagPanel);
        tagPanel.setOnComponentsToggled(active -> {
            if (Boolean.TRUE.equals(active)) switchToComponents();
            else                             deactivateComponents(true);
        });
        trackFocus(tagPanel.getTable());
        bindLeftToPositionTable();
        trackFocus(entryPanel.getTable());

        JPanel tranListPanel = entryPanel.buildContentPanel();
        JLabel entriesTitle  = new JLabel("MT Entries");
        entriesTitle.setFont(entriesTitle.getFont().deriveFont(Font.BOLD));
        JButton appendBtn = new JButton("+");
        appendBtn.setMargin(new Insets(1, 5, 1, 5));
        appendBtn.setToolTipText("Append file (Ctrl+Shift+O)");
        appendBtn.addActionListener(e -> owner.onAppendFile());
        JButton pasteBtn = new JButton(ToolbarIcons.clipboardIcon());
        pasteBtn.setMargin(new Insets(1, 4, 1, 4));
        pasteBtn.setToolTipText(MtAnalyzeFrame.PASTE_MT_SNIPPET);
        pasteBtn.addActionListener(e -> showAppendTextDialog());
        JPanel entriesWrapper = FrameLayout.wrapDetailCard(tranListPanel, entriesTitle, this::newFile, appendBtn, pasteBtn);

        detailCtrl = new DetailPanelController(tagPanel, this::onDetailMenuSyncNeeded);
        JPanel detailCardPanel = detailCtrl.buildCardPanel();
        selectionListeners.add(detailCtrl.sourcePanel());
        selectionListeners.add(detailCtrl.diffPanel());

        rootSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, entriesWrapper, detailCardPanel);
        rootSplit.setResizeWeight(0.65);
        detailCtrl.setSplit(rootSplit);
    }

    // -----------------------------------------------------------------------
    // Frame-facing API
    // -----------------------------------------------------------------------

    JComponent rootComponent() { return rootSplit; }

    String title() {
        File f = lastLoadedFile != null ? lastLoadedFile : currentSessionFile;
        return f != null ? f.getName() : "Untitled";
    }

    MtEntryPanel          entryPanel()   { return entryPanel; }
    TagView               tagPanel()     { return tagPanel; }
    DetailPanelController detailCtrl()   { return detailCtrl; }
    JTable                focusedTable() { return focusedTable != null ? focusedTable : entryPanel.getTable(); }

    boolean isReloadable()      { return reloadable; }
    boolean hasContent()        { return hasContent; }
    boolean hasSelection()      { return hasSelection; }
    boolean isTagsActive()      { return tagsActive; }
    boolean isComponentsActive(){ return componentsActive; }
    String  detailActiveCard()  { return detailCtrl.getActiveCard(); }

    void collapseDetailPanel() { detailCtrl.collapse(); }

    void newFile() {
        detailCtrl.showCard(DetailPanelController.INSPECTOR);
        entryPanel.clearSearch();
        tagPanel.clearSearch();
        entryPanel.clear();
        entryPanel.rebuildPositionTable();
        tagPanel.clear();
        setStatus.accept("New model created.");
        reloadable         = false;
        hasContent         = false;
        currentSessionFile = null;
        lastLoadedFile      = null;
        notifyStateChanged();
    }

    void openFile(File file)    { importer.loadFile(file); }

    void openSession(File file) {
        currentSessionFile = file;
        importer.loadFile(file);
    }

    void appendFile(File file)        { importer.appendFile(file); }
    void importDirectory(File dir)    { importer.importDirectory(dir); }

    void reload() {
        if (lastLoadedFile == null) return;
        if (!lastLoadedFile.exists()) {
            JOptionPane.showMessageDialog(owner,
                "File no longer exists:\n" + lastLoadedFile.getAbsolutePath(), ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
            return;
        }
        importer.loadFile(lastLoadedFile);
    }

    void saveSession() {
        if (currentSessionFile != null) {
            doSaveSession(currentSessionFile);
        } else {
            File file = owner.pickSessionSaveFile();
            if (file != null) doSaveSession(file);
        }
    }

    private void doSaveSession(File file) {
        try {
            ProjectIO.save(entryPanel.getProject(), file);
            currentSessionFile = file;
            owner.rememberLastSessionFile(file);
            setStatus.accept("Session saved: " + file.getAbsolutePath());
            notifyStateChanged();
        } catch (IllegalArgumentException ex) {
            owner.error(ex.getMessage());
        } catch (IOException ex) {
            owner.fileError("saving", ex);
        }
    }

    void saveSelectedMtAs() {
        JTable table = entryPanel.getTable();
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) { setStatus.accept("Select an entry in MT Entries first."); return; }
        int modelRow = table.convertRowIndexToModel(viewRow);
        SwiftMessage msg = entryPanel.getMessageForRow(modelRow);
        if (msg == null) { setStatus.accept("No SWIFT message for selected entry."); return; }
        mtExport.exportSingle(owner,
            msg.raw(),
            config.getMtExportSender(),
            config.getMtExportReceiver(),
            entryPanel.getRowValue(modelRow, EntryPanelModel.FILE_COL_KEY),
            setStatus);
    }

    void applyPowerUserMode(boolean on) { entryPanel.applyPowerUserMode(on); }

    // -----------------------------------------------------------------------
    // Hosts
    // -----------------------------------------------------------------------

    private MtEntryPanel.Host createEntryPanelHost() {
        return new MtEntryPanel.Host() {
            @Override public void onRowSelected(int modelRow) {
                detailCtrl.expandIfNeeded();
                dispatchSingleEntry(modelRow);
                setSaveAsMtEnabled(true);
            }
            @Override public void onMultipleRowsSelected(List<Entry> entries) {
                switchDetailCard(DetailPanelController.COMPARE);
                detailCtrl.expandIfNeeded();
                for (EntrySelectionListener l : selectionListeners) l.onMultipleEntries(entries);
                setSaveAsMtEnabled(true);
            }
            @Override public void onRowDeselected() {
                collapseDetailPanel();
                dispatchDeselect();
                setSaveAsMtEnabled(false);
            }
            @Override public void onFilesDropped(List<File> files) { appendDroppedFiles(files); }
            @Override public boolean isPowerUser() { return isPowerUser.getAsBoolean(); }
            @Override public boolean isExperimentalMode() { return config.isExperimentalMode(); }
            @Override public JTable getDetailTable() { return tagPanel.getTable(); }
            @Override public void focusDetailTag(ColumnDef cd) { tagPanel.focusTag(cd); }
            @Override public void switchDetailCard(String card) { Document.this.switchDetailCard(card); }
            @Override public void onAddNote(int modelRow) {
                Entry entry = entryPanel.getEntryForRow(modelRow);
                if (entry == null) return;
                entry.data().putIfAbsent(Entry.NOTE_COL_KEY, "");
                detailCtrl.expandIfNeeded();
                switchDetailCard(DetailPanelController.INSPECTOR);
                dispatchSingleEntry(modelRow);
                tagPanel.activateNoteEditing();
            }
            @Override public void onSetNote(int modelRow, String note) {
                Entry entry = entryPanel.getEntryForRow(modelRow);
                if (entry == null) return;
                entry.data().put(Entry.NOTE_COL_KEY, note);
                int colIdx = findPositionColumnIndex(Entry.NOTE_COL_KEY);
                if (colIdx >= 0)
                    entryPanel.getTable().getModel().setValueAt(note, modelRow, colIdx);
                detailCtrl.expandIfNeeded();
                switchDetailCard(DetailPanelController.INSPECTOR);
                dispatchSingleEntry(modelRow);
                tagPanel.activateNoteEditing();
            }
            @Override public void exportMessageForRow(int modelRow) {
                mtExport.exportSingle(owner,
                    entryPanel.getMessageForRow(modelRow).raw(),
                    config.getMtExportSender(),
                    config.getMtExportReceiver(),
                    entryPanel.getRowValue(modelRow, EntryPanelModel.FILE_COL_KEY),
                    setStatus);
            }
            @Override public void showAppendTextDialog() { Document.this.showAppendTextDialog(); }
            @Override public void setStatus(String message) { setStatus.accept(message); }
        };
    }

    private TagView.Host createTagPanelHost() {
        return new TagView.Host() {
            @Override public boolean isPowerUser() { return Document.this.isPowerUser.getAsBoolean(); }
            @Override public JMenuItem makeReferenceSearchItem(String value) { return Document.this.makeReferenceSearchItem(value); }
            @Override public JMenuItem makeCopyCellItem(JTable t, int vr, int vc) { return Document.this.makeCopyCellItem(t, vr, vc); }
            @Override public JMenuItem makeCopyTableItem(JTable t) { return Document.this.makeCopyTableItem(t); }
            @Override public void showAddToDictionaryDialog(String q, String v) { HintDictionaryDialog.showAddEntry(owner, q, v, dict); }
            @Override public void appendToEntryFilterByQualifier(String q, String v) { entryPanel.appendToEntryFilterByQualifier(q, v); }
            @Override public void onDetailValueEdited(DefaultTableModel m, int r, String v) { Document.this.onDetailValueEdited(m, r, v); }
        };
    }

    private ImportContext createImportContext() {
        return new ImportContext() {
            @Override public Frame         frame()                          { return owner; }
            @Override public SystemConfig  config()                         { return config; }
            @Override public ImportService importService()                  { return importService; }
            @Override public String        promptMtType(String m)           { return owner.promptMtType(m); }
            @Override public java.util.Optional<java.util.Set<String>> promptMtTypeFilter(String f) { return owner.promptMtTypeFilter(f); }
            @Override public void          onNew()                          { Document.this.newFile(); }
            @Override public void          onFileLoaded(ImportBatch b, File f)              { Document.this.onFileLoaded(b, f); }
            @Override public void          onDirectoryLoaded(ImportBatch b, File d, int n)  { Document.this.onDirectoryLoaded(b, d, n); }
            @Override public void          onContentAppended(ImportBatch b)                  { Document.this.onContentAppended(b); }
            @Override public void          onFileAppended(File f)           { Document.this.onFileAppended(f); }
            @Override public void          error(String m)                  { owner.error(m); }
            @Override public void          fileError(String v, Exception e) { owner.fileError(v, e); }
        };
    }

    // -----------------------------------------------------------------------
    // Import callbacks
    // -----------------------------------------------------------------------

    private void onFileLoaded(ImportBatch batch, File file) {
        owner.notifyProwideLog(batch);
        if (batch.totalParsed == 0) { owner.error("No valid SWIFT messages found."); return; }
        tagPanel.clear();
        entryPanel.clearSearch();
        tagPanel.clearSearch();
        entryPanel.loadBatch(batch.messages, batch.columnDefs);
        entryPanel.applyColumnPrefs();
        entryPanel.rebuildPositionTable();
        selectFirstRow();
        int n = batch.totalParsed;
        setStatus.accept("Loaded: " + file.getAbsolutePath());
        bottomCtrl.notificationPanel().addNotification(NotificationPanel.Type.INFO, "File loaded",
            file.getName() + " (" + n + (n == 1 ? MSG_SINGULAR : MSG_PLURAL) + ")");
        owner.rememberLastFile(file);
        lastLoadedFile = file;
        reloadable     = true;
        hasContent     = true;
        notifyStateChanged();
        owner.notifyBatchErrors(batch.errors);
        if (batch.limitReached) owner.warnLimitReached();
    }

    private void onDirectoryLoaded(ImportBatch batch, File dir, int fileCount) {
        entryPanel.clearSearch();
        tagPanel.clearSearch();
        entryPanel.mergeBatch(batch.messages, batch.columnDefs);
        entryPanel.applyColumnPrefs();
        entryPanel.rebuildPositionTable();
        selectFirstRow();
        int n = batch.totalParsed;
        String msg = "Imported " + n + (n == 1 ? MSG_SINGULAR : MSG_PLURAL)
            + " from " + fileCount + (fileCount == 1 ? " file" : " files")
            + " in " + dir.getName();
        setStatus.accept(msg);
        bottomCtrl.notificationPanel().addNotification(NotificationPanel.Type.INFO, "Import complete",
            msg + " (" + entryPanel.getLoadedMessages().size() + " messages total)");
        owner.notifyBatchErrors(batch.errors);
        owner.notifyProwideLog(batch);
        reloadable = false;
        hasContent = true;
        notifyStateChanged();
        if (batch.limitReached) owner.warnLimitReached();
    }

    private void onContentAppended(ImportBatch batch) {
        entryPanel.mergeBatch(batch.messages, batch.columnDefs);
        entryPanel.applyColumnPrefs();
        entryPanel.rebuildPositionTable();
        setStatus.accept("Appended: " + batch.totalParsed + (batch.totalParsed == 1 ? MSG_SINGULAR : MSG_PLURAL));
        owner.notifyBatchErrors(batch.errors);
        owner.notifyProwideLog(batch);
        if (batch.limitReached) owner.warnLimitReached();
    }

    private void onFileAppended(File file) {
        int total = entryPanel.getLoadedMessages().size();
        setStatus.accept("Appended: " + file.getAbsolutePath());
        bottomCtrl.notificationPanel().addNotification(NotificationPanel.Type.INFO, "File appended",
            file.getName() + " (" + total + " messages total)");
        reloadable = true;
        hasContent = true;
        notifyStateChanged();
    }

    // -----------------------------------------------------------------------
    // Detail-panel state machine
    // -----------------------------------------------------------------------

    void switchDetailCard(String card) {
        deactivateComponents(DetailPanelController.INSPECTOR.equals(card));
        detailCtrl.showCard(card);
    }

    private void deactivateComponents(boolean rebuildModel) {
        tagPanel.setComponentsButtonSelected(false);
        componentsActive = false;
        notifyStateChanged();
        detailCtrl.resetTitleToTags();
        if (!tagPanel.isComponentsMode()) return;
        if (rebuildModel) tagPanel.rebuildModel(false);
        detailCtrl.syncButtons();
    }

    void switchToComponents() {
        detailCtrl.showComponentsMode();
        tagPanel.setComponentsButtonSelected(true);
        if (!tagPanel.isComponentsMode()) tagPanel.rebuildModel(true);
        detailCtrl.expandIfNeeded();
        tagsActive       = false;
        componentsActive = true;
        notifyStateChanged();
    }

    private void onDetailMenuSyncNeeded(boolean tagsActive, boolean compActive) {
        this.tagsActive       = tagsActive;
        this.componentsActive = compActive;
        notifyStateChanged();
    }

    private void setSaveAsMtEnabled(boolean enabled) {
        hasSelection = enabled;
        notifyStateChanged();
    }

    private void notifyStateChanged() { onStateChanged.accept(this); }

    // -----------------------------------------------------------------------
    // Selection dispatch
    // -----------------------------------------------------------------------

    private void dispatchSingleEntry(int modelRow) {
        Entry entry = entryPanel.getEntryForRow(modelRow);
        SwiftMessage msg = entryPanel.getMessageForRow(modelRow);
        if (entry == null || msg == null) { dispatchDeselect(); return; }
        for (EntrySelectionListener l : selectionListeners) l.onSingleEntry(entry, msg);
    }

    private void dispatchDeselect() {
        for (EntrySelectionListener l : selectionListeners) l.onDeselect();
    }

    private void selectFirstRow() {
        if (entryPanel.getTable().getRowCount() > 0) {
            entryPanel.getTable().setRowSelectionInterval(0, 0);
            entryPanel.getTable().scrollRectToVisible(entryPanel.getTable().getCellRect(0, 0, true));
        }
    }

    /** Drops onto the entry table append to the current data instead of replacing it. */
    private void appendDroppedFiles(List<File> files) {
        for (File f : files) importer.appendFile(f);
    }

    void showAppendTextDialog() {
        AppendTextDialog.show(
            owner,
            () -> owner.promptMtType("Select the message type for this content."),
                importer::appendFromContent,
            chunks -> importer.appendFromContent(chunks, null, null, MessageOrigin.NAME_VALUE)
        );
    }

    private void onDetailValueEdited(DefaultTableModel model, int detailModelRow, String newValue) {
        int viewRow  = entryPanel.getTable().getSelectedRow();
        int modelRow = viewRow >= 0 ? entryPanel.getTable().convertRowIndexToModel(viewRow) : -1;
        if (modelRow < 0 || modelRow >= entryPanel.getRowData().size()) return;
        String seqLabel  = nvl(model.getValueAt(detailModelRow, 0));
        String tagName   = nvl(model.getValueAt(detailModelRow, 1));
        String qualifier = nvl(model.getValueAt(detailModelRow, 2));
        int occ = 1;
        for (int r = 0; r < detailModelRow; r++) {
            if (seqLabel.equals(nvl(model.getValueAt(r, 0)))
                    && tagName.equals(nvl(model.getValueAt(r, 1)))
                    && qualifier.equals(nvl(model.getValueAt(r, 2)))) {
                occ++;
            }
        }
        String key = seqLabel + "\t" + tagName + "\t" + qualifier + "\t" + occ;
        Map<String, String> rowData = entryPanel.getRowData().get(modelRow);
        if (!rowData.containsKey(key)) return;
        rowData.put(key, newValue);
        int posColIdx = findPositionColumnIndex(key);
        if (posColIdx >= 0)
            entryPanel.getTable().getModel().setValueAt(newValue, modelRow, posColIdx);
    }

    private int findPositionColumnIndex(String key) {
        int idx = 0;
        for (ColumnDef cd : entryPanel.getColumnDefs()) {
            if (!cd.isVisible()) continue;
            if (cd.key.equals(key)) return idx;
            idx++;
        }
        return -1;
    }

    private static String nvl(Object o) { return o != null ? o.toString() : ""; }

    // -----------------------------------------------------------------------
    // Copy-to-clipboard / reference-search helpers
    // -----------------------------------------------------------------------

    private JMenuItem makeReferenceSearchItem(String value) {
        String label = value.length() > 30 ? value.substring(0, 27) + "..." : value;
        JMenuItem item = new JMenuItem("Reference Search: " + label, ToolbarIcons.menuSearch());
        item.addActionListener(ae -> entryPanel.applyReferenceSearch(value));
        return item;
    }

    private JMenuItem makeCopyCellItem(JTable table, int viewRow, int viewCol) {
        JMenuItem item = new JMenuItem("Copy", ToolbarIcons.menuCopy());
        item.addActionListener(ae -> copyCellToClipboard(table, viewRow, viewCol));
        return item;
    }

    private void copyCellToClipboard(JTable table, int viewRow, int viewCol) {
        Object value = table.getValueAt(viewRow, viewCol);
        String text  = value != null ? value.toString() : "";
        Toolkit.getDefaultToolkit()
               .getSystemClipboard()
               .setContents(new StringSelection(text), null);
        setStatus.accept("Cell value copied to clipboard.");
    }

    private JMenuItem makeCopyTableItem(JTable table) {
        JMenuItem item = new JMenuItem("Copy Table", ToolbarIcons.menuCopyTable());
        item.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T,
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        item.addActionListener(ae -> copyTableToClipboard(table));
        return item;
    }

    private void copyTableToClipboard(JTable table) {
        String tsv = FilterSupport.buildTableTsv(table);
        Toolkit.getDefaultToolkit()
               .getSystemClipboard()
               .setContents(new StringSelection(tsv), null);
        setStatus.accept("Table copied to clipboard.");
    }

    // -----------------------------------------------------------------------
    // Focus tracking
    // -----------------------------------------------------------------------

    private void trackFocus(JTable table) {
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) { focusedTable = table; }
        });
        table.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) { focusedTable = table; }
        });
    }

    /** Left arrow in the detail table returns keyboard focus to the TRAN position table. */
    private void bindLeftToPositionTable() {
        String actionKey = "focusPositionTable";
        tagPanel.getTable().getInputMap(JComponent.WHEN_FOCUSED)
                           .put(KeyStroke.getKeyStroke("LEFT"), actionKey);
        tagPanel.getTable().getActionMap().put(actionKey, new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                entryPanel.getTable().requestFocusInWindow();
            }
        });
    }
}
