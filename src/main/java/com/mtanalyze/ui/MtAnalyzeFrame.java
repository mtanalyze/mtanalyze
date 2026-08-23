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


import com.mtanalyze.model.Entry;
import com.mtanalyze.model.EntrySelectionListener;
import com.mtanalyze.model.MessageOrigin;
import com.mtanalyze.model.SwiftMessage;
import com.mtanalyze.bookmark.Bookmark;
import com.mtanalyze.config.SystemConfig;
import com.mtanalyze.parser.MtFileIO;
import com.mtanalyze.parser.MtParser;
import com.mtanalyze.parser.HintDictionary;
import com.mtanalyze.bookmark.BookmarkManager;
import com.mtanalyze.export.CsvExport;
import com.mtanalyze.export.ExcelExport;
import com.mtanalyze.export.MtExport;
import com.mtanalyze.export.ProjectIO;
import com.mtanalyze.ui.view.BookmarkPanel;
import com.mtanalyze.ui.view.MessageSourcePanel;
import com.mtanalyze.ui.view.NotificationPanel;
import com.mtanalyze.ui.view.SourcePanel;
import com.mtanalyze.ui.view.TagView;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

public class MtAnalyzeFrame extends JFrame {

    private static final String MSG_SINGULAR = " message";
    private static final String MSG_PLURAL   = " messages";
    public static final String BOOKMARKS = "Bookmarks";
    private static final String APP_NAME                 = "MT Analyze";
    private static final String GITHUB_URL               = "https://github.com/mtanalyze/mtanalyze";
    private static final String DEVELOPER_URL            = "https://www.linkedin.com/in/ralfschwarz/";
    private static final String LABEL_SECURITIES        = "Securities Posting";
    private static final String LABEL_CASH              = "Cash Posting";
    private static final String LABEL_ACCOUNT_MAPPING   = "Account Mapping";
    private static final String PASTE_MT_SNIPPET        = "Paste MT Snippet";
    private static final String MENU_PASTE              = "Paste";

    // -----------------------------------------------------------------------
    private final List<EntrySelectionListener> selectionListeners = new ArrayList<>();

    // -----------------------------------------------------------------------
    private final transient SystemConfig        config    = new SystemConfig();
    private final transient CsvExport           csvExport   = new CsvExport();
    private final transient MtExport            mtExport    = new MtExport();
    private final transient ExcelExport         excelExport = new ExcelExport();
    private final transient ImportService       importService = new ImportService();
    private final transient HintDictionary      dict          = new HintDictionary();
    private final transient FileImporter        importer;

    // UI fields
    // -----------------------------------------------------------------------
    private final MtEntryPanel       entryPanel;
    private TagView            tagPanel;
    private JLabel             statusLabel;
    private JMenuItem          reloadItem;
    private JMenuItem          openSessionItem;
    private JMenuItem          saveItem;
    private JMenuItem          saveAsMtItem;
    private JMenuItem          saveExcelItem;
    private JMenuItem          exportComponentsItem;
    private JMenuItem          validateFileItem;
    private JMenuItem          attachBlock5Item;
    private JMenu              importMenu;
    private JMenu              exportMenu;
    private JSeparator         importExportLeadingSeparator;
    private JSeparator         importExportMiddleSeparator;
    private JSeparator         viewMenuSeparator;
    private JMenuItem          importSecuritiesItem;
    private JMenuItem          importCashItem;
    private JMenuItem          importMappingItem;
    private JSeparator         importPostingsSeparator;
    private JMenuItem          exportSecuritiesItem;
    private JMenuItem          exportCashItem;
    private JMenuItem          exportMappingItem;
    private JSeparator         exportPostingsSeparator;
    private File               currentSessionFile;

    private static final Preferences PREFS = Preferences.userNodeForPackage(MtAnalyzeFrame.class);
    private static final String PREF_COL_ORDER  = "col_order";
    private static final String PREF_COL_VIS    = "col_visibility";
    private static final String PREF_LAST_FILE         = "last_file";
    private static final String PREF_LAST_SESSION_FILE = "last_session_file";
    private static final String SESSION_EXT            = ProjectIO.SESSION_EXTENSION;
    private static final String PREF_WIN_X      = "win_x";
    private static final String PREF_WIN_Y      = "win_y";
    private static final String PREF_WIN_W      = "win_w";
    private static final String PREF_WIN_H      = "win_h";

    private static final String PREF_DARK_MODE            = "dark_mode";  // legacy – used for migration only
    private static final String PREF_THEME                = "theme";
    private static final String PREF_CSV_FIELD_SEP        = "csv_field_sep";
    private static final String PREF_CSV_DECIMAL_SEP      = "csv_decimal_sep";
    private static final String PREF_USER_DICT             = "user_qualifier_values";
    private static final String PREF_QUICK_FILTER_PROFILES = "quick_filter_profiles";
    private static final String PREF_COL_LAYOUT_PROFILES   = "col_layout_profiles";
    private static final String PREF_EXPLORER_ROOTS        = "explorer_roots";
    private static final String PREF_EXPLORER_SPLIT        = "explorer_split";
    private static final String PREF_BOOKMARKS             = "bookmarks";
    private static final String PREF_POWER_USER            = "power_user";
    private static final String PREF_ACCOUNT_MAPPING       = "account_mapping";
    private static final String NAV_CARD_EXPLORER       = "explorer";
    private static final String THEME_LIGHT             = "Light";

    private JButton    menuSearchBtn;

    private MessageSourcePanel           messageSourcePanel;
    private BookmarkPanel              bookmarkPanel;
    private transient BottomPanelController      bottomCtrl;
    private JPanel         navPanel;
    private CardLayout     navCardLayout;
    private JCheckBoxMenuItem    menuExplorer;
    private JCheckBoxMenuItem    menuBookmarks;
    private JCheckBoxMenuItem    menuSecurities;
    private JCheckBoxMenuItem    menuCash;
    private JCheckBoxMenuItem    menuAccountMapping;
    private JRadioButtonMenuItem menuNotifications;
    private JRadioButtonMenuItem menuTags;
    private JRadioButtonMenuItem menuCompare;
    private JRadioButtonMenuItem menuSource;
    private JRadioButtonMenuItem menuComponents;
    private ToolWindowButton  explorerTwBtn;
    private ToolWindowButton  bookmarksTwBtn;
    private JSplitPane    explorerSplit;

    private ToolWindowButton       securitiesTwBtn;
    private ToolWindowButton       cashTwBtn;
    private ToolWindowButton       accountMappingTwBtn;
    private transient DetailPanelController  detailCtrl;
    private boolean       explorerCollapsed    = false;
    private final transient BookmarkManager bookmarkManager;

    /** The table that last received focus – drives the Edit menu content. */
    private JTable focusedTable;

    private static final String SEQ_KEY            = MtParser.SEQ_KEY;
    private static final String ERROR_TITLE         = "Error";
    private static final String IMPORT_DIR_TITLE    = "Import Directory";

    // -----------------------------------------------------------------------
    // Constructor / UI setup
    // -----------------------------------------------------------------------
    public MtAnalyzeFrame() {
        super(APP_NAME);
        String legacyDict = PREFS.get(PREF_USER_DICT, "");
        if (!legacyDict.isEmpty()) {
            dict.loadUserEntriesFromCsv(legacyDict);
            if (dict.saveUserEntriesToFile(dict.getUserEntries()))
                PREFS.remove(PREF_USER_DICT);
        } else {
            dict.loadUserEntriesFromFile();
        }
        bookmarkManager = new BookmarkManager(PREFS, PREF_BOOKMARKS);
        entryPanel = new MtEntryPanel(createEntryPanelHost(), PREFS,
            new MtEntryPanel.PrefKeys(PREF_COL_ORDER, PREF_COL_VIS,
                                      PREF_QUICK_FILTER_PROFILES, PREF_COL_LAYOUT_PROFILES), dict);
        importer = new FileImporter(createImportContext());
        initUI();
    }

    private void setSaveAsMtEnabled(boolean enabled) {
        if (saveAsMtItem != null) saveAsMtItem.setEnabled(enabled);
    }

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
            @Override public boolean isPowerUser() { return PREFS.getBoolean(PREF_POWER_USER, false); }
            @Override public boolean isExperimentalMode() { return config.isExperimentalMode(); }
            @Override public JTable getDetailTable() { return tagPanel.getTable(); }
            @Override public void focusDetailTag(ColumnDef cd) { tagPanel.focusTag(cd); }
            @Override public void switchDetailCard(String card) { MtAnalyzeFrame.this.switchDetailCard(card); }
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
            @Override public void addBookmarkForRow(int modelRow) { addBookmarkFromRow(modelRow); }
            @Override public void exportMessageForRow(int modelRow) {
                mtExport.exportSingle(MtAnalyzeFrame.this,
                    entryPanel.getMessageForRow(modelRow).raw(),
                    config.getMtExportSender(),
                    config.getMtExportReceiver(),
                    entryPanel.getRowValue(modelRow, EntryPanelModel.FILE_COL_KEY),
                    statusLabel::setText);
            }
            @Override public void showAppendTextDialog() { MtAnalyzeFrame.this.showAppendTextDialog(); }
            @Override public void setStatus(String message) { if (statusLabel != null) statusLabel.setText(message); }
        };
    }

    private TagView.Host createTagPanelHost() {
        return new TagView.Host() {
            @Override public boolean isPowerUser() { return MtAnalyzeFrame.this.isPowerUser(); }
            @Override public JMenuItem makeReferenceSearchItem(String value) { return MtAnalyzeFrame.this.makeReferenceSearchItem(value); }
            @Override public JMenuItem makeCopyCellItem(JTable t, int vr, int vc) { return MtAnalyzeFrame.this.makeCopyCellItem(t, vr, vc); }
            @Override public JMenuItem makeCopyTableItem(JTable t) { return MtAnalyzeFrame.this.makeCopyTableItem(t); }
            @Override public void showAddToDictionaryDialog(String q, String v) { HintDictionaryDialog.showAddEntry(MtAnalyzeFrame.this, q, v, dict); }
            @Override public void appendToEntryFilterByQualifier(String q, String v) { entryPanel.appendToEntryFilterByQualifier(q, v); }
            @Override public void onDetailValueEdited(DefaultTableModel m, int r, String v) { MtAnalyzeFrame.this.onDetailValueEdited(m, r, v); }
        };
    }

    private void tagPanelRebuild(boolean withComponents) {
        tagPanel.rebuildModel(withComponents);
    }

    private void initUI() {
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1280, 820);
        setLocationRelativeTo(null);
        setupIcons();
        entryPanel.init();
        setupMenuBar();
        tagPanel = new TagView(createTagPanelHost(), PREFS, dict);
        selectionListeners.add(tagPanel);
        tagPanel.setOnComponentsToggled(active -> {
            if (Boolean.TRUE.equals(active)) switchToComponents();
            else                             deactivateComponents(true);
        });
        trackFocus(tagPanel.getTable());
        bindLeftToPositionTable();
        trackFocus(entryPanel.getTable());
        JPanel tranListPanel  = entryPanel.buildContentPanel();
        JLabel entriesTitle   = new JLabel("MT Entries");
        entriesTitle.setFont(entriesTitle.getFont().deriveFont(Font.BOLD));
        JButton appendBtn = new JButton("+");
        appendBtn.setMargin(new Insets(1, 5, 1, 5));
        appendBtn.setToolTipText("Append file (Ctrl+Shift+O)");
        appendBtn.addActionListener(e -> onAppendFile());
        JButton pasteBtn = new JButton(ToolbarIcons.clipboardIcon());
        pasteBtn.setMargin(new Insets(1, 4, 1, 4));
        pasteBtn.setToolTipText(PASTE_MT_SNIPPET);
        pasteBtn.addActionListener(e -> showAppendTextDialog());
        JPanel entriesWrapper = FrameLayout.wrapDetailCard(tranListPanel, entriesTitle, this::onNew, appendBtn, pasteBtn);
        detailCtrl = new DetailPanelController(tagPanel, this::syncDetailMenuItems);
        JPanel detailCardPanel = detailCtrl.buildCardPanel();
        selectionListeners.add(detailCtrl.sourcePanel());
        selectionListeners.add(detailCtrl.diffPanel());
        JSplitPane innerSplit = buildInnerSplit(entriesWrapper, detailCardPanel);
        JSplitPane outerSplit = buildOuterSplit(innerSplit);
        setupStatusBar();
        assembleMainLayout(outerSplit);
        applyPowerUserMode();
        applyExperimentalMode();
        bindGlobalShortcuts();
        SwingUtilities.invokeLater(this::collapseExplorer);
        SwingUtilities.invokeLater(this::collapseDetailPanel);
    }

    private void setupIcons() {
        List<java.awt.Image> icons = new ArrayList<>();
        for (int s : new int[]{16, 24, 32, 48, 64, 128})
            icons.add(AppIcon.createAppIcon(s));
        setIconImages(icons);
    }

    private void setupMenuBar() {
        FrameMenuBar.Items items = FrameMenuBar.build(entryPanel, createMenuCallbacks());
        setJMenuBar(items.menuBar());
        openSessionItem      = items.openSessionItem();
        saveItem             = items.saveItem();
        saveAsMtItem         = items.saveAsMtItem();
        saveExcelItem        = items.saveExcelItem();
        reloadItem           = items.reloadItem();
        exportComponentsItem    = items.exportComponentsItem();
        validateFileItem        = items.validateFileItem();
        attachBlock5Item        = items.attachBlock5Item();
        importMenu              = items.importMenu();
        exportMenu              = items.exportMenu();
        importExportLeadingSeparator = items.importExportLeadingSeparator();
        importExportMiddleSeparator  = items.importExportMiddleSeparator();
        viewMenuSeparator            = items.viewMenuSeparator();
        importSecuritiesItem    = items.importSecuritiesItem();
        importCashItem          = items.importCashItem();
        importMappingItem       = items.importMappingItem();
        importPostingsSeparator = items.importPostingsSeparator();
        exportSecuritiesItem    = items.exportSecuritiesItem();
        exportCashItem          = items.exportCashItem();
        exportMappingItem       = items.exportMappingItem();
        exportPostingsSeparator = items.exportPostingsSeparator();
        menuSearchBtn        = items.searchButton();
        menuExplorer         = items.menuExplorer();
        menuBookmarks        = items.menuBookmarks();
        menuSecurities       = items.menuSecurities();
        menuCash             = items.menuCash();
        menuAccountMapping   = items.menuAccountMapping();
        menuNotifications    = items.menuNotifications();
        menuTags             = items.menuTags();
        menuCompare          = items.menuCompare();
        menuSource           = items.menuSource();
        menuComponents       = items.menuComponents();
    }

    private FrameMenuBar.Callbacks createMenuCallbacks() {
        CsvExport.Prefs csvPrefs = new CsvExport.Prefs(PREFS, PREF_CSV_FIELD_SEP, PREF_CSV_DECIMAL_SEP);
        return new FrameMenuBar.Callbacks(
            this::onNew,
            this::onOpenSession,
            this::onSaveSession,
            this::onSaveSelectedMtAs,
            this::onSaveExcel,
            this::onOpenFile,
            this::onAppendFile,
            this::onImportDirectory,
            this::onReloadFile,
            this::onValidateFile,
            this::onAttachBlock5,
            () -> csvExport.export(this, entryPanel.getColumnDefs(), entryPanel.getRowData(), statusLabel::setText, csvPrefs),
            () -> csvExport.exportComponents(this, entryPanel.getFullDisplaySequences(), entryPanel.getRowData(), SEQ_KEY, statusLabel::setText, csvPrefs),
            () -> mtExport.export(this, entryPanel.getLoadedMessages().stream().map(SwiftMessage::raw).toList(),
                                  config.getMtExportSender(), config.getMtExportReceiver(),
                                  statusLabel::setText),
            () -> mtExport.export(this, entryPanel.getVisibleMessages().stream().map(SwiftMessage::raw).toList(),
                                  config.getMtExportSender(), config.getMtExportReceiver(),
                                  statusLabel::setText),
            () -> bottomCtrl.securitiesPanel().showLoadDialog(),
            () -> bottomCtrl.cashPanel().showLoadDialog(),
            () -> bottomCtrl.accountMappingPanel().showImportDialog(),
            () -> bottomCtrl.securitiesPanel().showExportDialog(),
            () -> bottomCtrl.cashPanel().showExportDialog(),
            () -> bottomCtrl.accountMappingPanel().showExportDialog(),
            this::showSettings,
            this::showSearchPopup,
            () -> HelpDialog.show(this),
            this::showAboutDialog,
            this::populateEditMenu,
            this::switchNavPanel,
            () -> bottomCtrl.toggle(BottomPanelController.BOOKMARKS),
            () -> bottomCtrl.toggle(BottomPanelController.SECURITIES),
            () -> bottomCtrl.toggle(BottomPanelController.CASH),
            () -> bottomCtrl.toggle(BottomPanelController.ACCOUNT_MAPPING),
            () -> switchDetailCard(DetailPanelController.NOTIFICATIONS),
            () -> switchDetailCard(DetailPanelController.INSPECTOR),
            () -> switchDetailCard(DetailPanelController.COMPARE),
            () -> switchDetailCard(DetailPanelController.EDITOR),
            this::switchToComponents
        );
    }

    private void showSettings() {
        SettingsDialog.show(this, PREFS, new SettingsDialog.Config(
            new SettingsDialog.Config.CsvKeys(PREF_CSV_FIELD_SEP, PREF_CSV_DECIMAL_SEP),
            new SettingsDialog.Config.ThemeConfig(PREF_THEME, this::applyTheme),
            new SettingsDialog.Config.SystemKeys(
                config::getMtExportSender, config::getMtExportReceiver,
                config::getMaxEntries, config::getLogSwiftStart, config::getLogNewlineToken,
                config::isExperimentalMode,
                config::saveSettings,
                this::applyExperimentalMode),
            new SettingsDialog.Config.PowerUserConfig(PREF_POWER_USER, this::applyPowerUserMode)),
            dict);
    }

    private void switchNavPanel() {
        boolean show = menuExplorer.isSelected();
        if (explorerTwBtn != null) explorerTwBtn.setSelected(show);
        navCardLayout.show(navPanel, MtAnalyzeFrame.NAV_CARD_EXPLORER);
        if (show == explorerCollapsed) toggleMessageSourcePanel();
    }



    private void collapseBottomPanel() { bottomCtrl.collapse(); }

    private void syncBottomTwButtons() {
        boolean collapsed = bottomCtrl.isCollapsed();
        String  card      = bottomCtrl.getActiveCard();
        boolean bmActive  = !collapsed && BottomPanelController.BOOKMARKS.equals(card);
        boolean seActive  = !collapsed && BottomPanelController.SECURITIES.equals(card);
        boolean caActive  = !collapsed && BottomPanelController.CASH.equals(card);
        boolean amActive  = !collapsed && BottomPanelController.ACCOUNT_MAPPING.equals(card);
        if (bookmarksTwBtn      != null) bookmarksTwBtn     .setSelected(bmActive);
        if (securitiesTwBtn     != null) securitiesTwBtn    .setSelected(seActive);
        if (cashTwBtn           != null) cashTwBtn          .setSelected(caActive);
        if (accountMappingTwBtn != null) accountMappingTwBtn.setSelected(amActive);
        if (menuBookmarks       != null) menuBookmarks      .setSelected(bmActive);
        if (menuSecurities      != null) menuSecurities     .setSelected(seActive);
        if (menuCash            != null) menuCash           .setSelected(caActive);
        if (menuAccountMapping  != null) menuAccountMapping .setSelected(amActive);
    }

    private void populateEditMenu(JMenu menu) {
        java.awt.Component fo = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
        if (fo instanceof JTextField tf) {
            boolean hasSel = tf.getSelectedText() != null && !tf.getSelectedText().isEmpty();
            int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
            JMenuItem copyItem  = new JMenuItem("Copy",  ToolbarIcons.menuCopy());
            JMenuItem cutItem   = new JMenuItem("Cut");
            JMenuItem pasteItem = new JMenuItem(MENU_PASTE, ToolbarIcons.menuPaste());
            copyItem .setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask));
            cutItem  .setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, menuMask));
            pasteItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, menuMask));
            FrameLayout.wireTextMenuItems(tf, hasSel, copyItem, cutItem, pasteItem, menu::add);
            return;
        }
        if (tryPopulatePostingPanel(menu)) return;
        JTable tbl = focusedTable != null ? focusedTable : entryPanel.getTable();
        int viewRow = tbl.getSelectedRow();
        int viewCol = tbl.getSelectedColumn();

        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        if (tbl == tagPanel.getTable()) {
            copyPopupItemsToMenu(tagPanel.buildContextMenu(viewRow, viewCol), menu);
            menu.addSeparator();
            JMenuItem pasteItem = new JMenuItem(MENU_PASTE, ToolbarIcons.menuPaste());
            pasteItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, menuMask));
            pasteItem.addActionListener(e -> showAppendTextDialog());
            menu.add(pasteItem);
        } else if (tbl.getRowCount() == 0) {
            JMenuItem pasteItem = new JMenuItem(MENU_PASTE, ToolbarIcons.menuPaste());
            pasteItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, menuMask));
            pasteItem.addActionListener(e -> showAppendTextDialog());
            menu.add(pasteItem);
        } else {
            int modelRow = viewRow >= 0 ? entryPanel.getTable().convertRowIndexToModel(viewRow) : -1;
            int safeViewRow = (viewRow >= 0 && viewRow < tbl.getRowCount()) ? viewRow : -1;
            int safeViewCol = Math.max(viewCol, 0);
            copyPopupItemsToMenu(entryPanel.buildRowContextMenu(modelRow, safeViewRow, safeViewCol), menu);
        }
    }

    private boolean tryPopulatePostingPanel(JMenu menu) {
        if (!explorerCollapsed && messageSourcePanel != null) {
            JPopupMenu p = messageSourcePanel.getPopupMenu();
            if (p.getComponentCount() > 0) {
                copyPopupItemsToMenu(p, menu);
                return true;
            }
        }
        if (bottomCtrl == null || bottomCtrl.isCollapsed()) return false;
        EditMenuContributor c = bottomCtrl.getContributor(bottomCtrl.getActiveCard());
        if (c != null) { copyPopupItemsToMenu(c.getPopupMenu(), menu); return true; }
        return false;
    }

    private static void copyPopupItemsToMenu(JPopupMenu popup, JMenu menu) {
        for (java.awt.Component c : popup.getComponents()) {
            if (c instanceof JSeparator) {
                menu.addSeparator();
            } else if (c instanceof JMenuItem src) {
                JMenuItem copy = new JMenuItem(src.getText(), src.getIcon());
                copy.setEnabled(src.isEnabled());
                copy.setAccelerator(src.getAccelerator());
                for (java.awt.event.ActionListener l : src.getActionListeners()) copy.addActionListener(l);
                menu.add(copy);
            }
        }
    }

    private void showAppendTextDialog() {
        AppendTextDialog.show(
            this,
            () -> promptMtType("Select the message type for this content."),
                importer::appendFromContent,
            chunks -> importer.appendFromContent(chunks, null, null, MessageOrigin.NAME_VALUE)
        );
    }

    /** Registers a focus-tracking listener so the Edit menu knows which table is active. */
    private void trackFocus(JTable table) {
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                focusedTable = table;
            }
        });
        table.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                focusedTable = table;
            }
        });
    }

    /** Left arrow in the detail table returns keyboard focus to the TRAN position table. */
    private void bindLeftToPositionTable() {
        String actionKey = "focusPositionTable";
        tagPanel.getTable().getInputMap(JComponent.WHEN_FOCUSED)
                           .put(KeyStroke.getKeyStroke("LEFT"), actionKey);
        tagPanel.getTable().getActionMap().put(actionKey, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                entryPanel.getTable().requestFocusInWindow();
            }
        });
    }

    /** Ctrl+F focuses the search field that belongs to the currently active table. */
    private void bindGlobalShortcuts() {
        InputMap  im = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getRootPane().getActionMap();

        im.put(KeyStroke.getKeyStroke("ctrl F"), "focusSearch");
        am.put("focusSearch", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { showSearchPopup(); }
        });

        im.put(KeyStroke.getKeyStroke("ctrl E"), "toggleExplorer");
        am.put("toggleExplorer", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { toggleMessageSourcePanel(); }
        });

        im.put(KeyStroke.getKeyStroke("ctrl D"), "toggleDetail");
        am.put("toggleDetail", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { detailCtrl.toggle(); }
        });
    }

    private void showSearchPopup() {
        if (focusedTable == tagPanel.getTable()) {
            SearchPopup.show(menuSearchBtn, tagPanel.getSearchField(),
                tagPanel.getClearButton(), tagPanel.getMatchLabel());
        } else {
            SearchPopup.show(menuSearchBtn, entryPanel.getSearchField(),
                entryPanel.finClearBtn, entryPanel.finPrevBtn,
                entryPanel.finNextBtn, entryPanel.finMatchLabel);
        }
    }

    private void toggleMessageSourcePanel() {
        if (explorerCollapsed) {
            int saved = PREFS.getInt(PREF_EXPLORER_SPLIT, 200);
            SwingUtilities.invokeLater(() -> explorerSplit.setDividerLocation(Math.max(saved, 150)));
            explorerCollapsed = false;
            syncTwButtons();
        } else {
            SwingUtilities.invokeLater(() -> explorerSplit.setDividerLocation(0));
            explorerCollapsed = true;
            deselectTwButtons();
        }
    }

    private void collapseExplorer() {
        if (!explorerCollapsed) {
            SwingUtilities.invokeLater(() -> explorerSplit.setDividerLocation(0));
            explorerCollapsed = true;
            deselectTwButtons();
        }
    }

    private void deselectTwButtons() {
        if (explorerTwBtn != null) explorerTwBtn.setSelected(false);
        if (menuExplorer  != null) menuExplorer .setSelected(false);
    }

    private void syncTwButtons() {
        if (explorerTwBtn == null) return;
        explorerTwBtn.setSelected(!explorerCollapsed);
        if (menuExplorer != null) menuExplorer.setSelected(!explorerCollapsed);
    }

    private void switchDetailCard(String card) {
        deactivateComponents(DetailPanelController.INSPECTOR.equals(card));
        detailCtrl.showCard(card);
    }

    private void collapseDetailPanel() { detailCtrl.collapse(); }

    private void syncDetailMenuItems(boolean tagsActive, boolean compActive) {
        String card = detailCtrl.getActiveCard();
        if (menuNotifications != null) menuNotifications.setSelected(DetailPanelController.NOTIFICATIONS.equals(card));
        if (menuTags          != null) menuTags         .setSelected(tagsActive);
        if (menuCompare       != null) menuCompare      .setSelected(DetailPanelController.COMPARE.equals(card));
        if (menuSource        != null) menuSource       .setSelected(DetailPanelController.EDITOR.equals(card));
        if (menuComponents    != null) menuComponents   .setSelected(compActive);
    }

    private void selectFirstRow() {
        if (entryPanel.getTable().getRowCount() > 0) {
            entryPanel.getTable().setRowSelectionInterval(0, 0);
            entryPanel.getTable().scrollRectToVisible(entryPanel.getTable().getCellRect(0, 0, true));
        }
    }

    private void deactivateComponents(boolean rebuildModel) {
        tagPanel.setComponentsButtonSelected(false);
        if (menuComponents != null) menuComponents.setSelected(false);
        detailCtrl.resetTitleToTags();
        if (!tagPanel.isComponentsMode()) return;
        if (rebuildModel) tagPanelRebuild(false);
        detailCtrl.syncButtons();
    }

    private void switchToComponents() {
        detailCtrl.showComponentsMode();
        if (menuNotifications != null) menuNotifications.setSelected(false);
        if (menuTags          != null) menuTags         .setSelected(false);
        if (menuCompare       != null) menuCompare      .setSelected(false);
        if (menuSource        != null) menuSource       .setSelected(false);
        if (menuComponents    != null) menuComponents   .setSelected(true);
        tagPanel.setComponentsButtonSelected(true);
        if (!tagPanel.isComponentsMode()) tagPanelRebuild(true);
        detailCtrl.expandIfNeeded();
    }

    private void dispatchSingleEntry(int modelRow) {
        Entry entry = entryPanel.getEntryForRow(modelRow);
        SwiftMessage msg = entryPanel.getMessageForRow(modelRow);
        if (entry == null || msg == null) { dispatchDeselect(); return; }
        for (EntrySelectionListener l : selectionListeners) l.onSingleEntry(entry, msg);
    }

    private void dispatchDeselect() {
        for (EntrySelectionListener l : selectionListeners) l.onDeselect();
    }

    private void addBookmarkFromRow(int modelRow) {
        String isin = entryPanel.findValueByTagQualifier(modelRow, "35B", "ISIN");
        String seme = entryPanel.findValueByTagQualifier(modelRow, "20C", "SEME");
        String rela = entryPanel.findValueByTagQualifier(modelRow, "20C", "RELA");
        String rawPath = entryPanel.getRowValue(modelRow, EntryPanelModel.FILE_COL_KEY);
        String file = rawPath.isEmpty() ? "" : new File(rawPath).getAbsolutePath();
        JTextField noteField = new JTextField(30);
        int result = JOptionPane.showConfirmDialog(this,
            new Object[]{"Note (optional):", noteField},
            "Add Bookmark", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        bookmarkManager.add(new Bookmark(isin, seme, rela, file, noteField.getText().trim()));
        if (bookmarkPanel != null) bookmarkPanel.refresh();
        statusLabel.setText("Bookmark added.");
    }

    private void navigateToBookmark(Bookmark b) {
        if (!b.filePath().isEmpty() && !entryPanel.isFileLoaded(b.filePath())) {
            int choice = JOptionPane.showConfirmDialog(this,
                "File not loaded:\n" + b.filePath() + "\n\nLoad it now?",
                "File Not Loaded", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) return;
            importer.loadFile(new File(b.filePath()));
        }
        int modelRow = findBookmarkRow(b);
        if (modelRow < 0) {
            statusLabel.setText("Bookmark entry not found in loaded data.");
            return;
        }
        entryPanel.selectAndScrollToModelRow(modelRow);
        toFront();
    }

    private int findBookmarkRow(Bookmark b) {
        // ISIN + file is the most specific match: one entry per ISIN per file
        if (!b.isin().isEmpty() && !b.filePath().isEmpty()) {
            int row = entryPanel.findRowByFileAndIsin(b.filePath(), b.isin());
            if (row >= 0) return row;
        }
        // RELA is entry-level but may be "NONREF" (non-unique); try only if it looks specific
        if (!b.rela().isEmpty() && !b.rela().equalsIgnoreCase("NONREF")) {
            int row = entryPanel.findRowByTagValue("RELA", b.rela());
            if (row >= 0) return row;
        }
        // SEME is message-level and shared across all entries of the same message; use as last resort
        if (!b.seme().isEmpty()) {
            int row = entryPanel.findRowByTagValue("SEME", b.seme());
            if (row >= 0) return row;
        }
        if (!b.rela().isEmpty()) {
            int row = entryPanel.findRowByTagValue("RELA", b.rela());
            if (row >= 0) return row;
        }
        return -1;
    }

    private JMenuItem makeReferenceSearchItem(String value) {
        String label = value.length() > 30 ? value.substring(0, 27) + "..." : value;
        JMenuItem item = new JMenuItem("Reference Search: " + label, ToolbarIcons.menuSearch());
        item.addActionListener(ae -> applyReferenceSearch(value));
        return item;
    }

    // -----------------------------------------------------------------------
    // Copy to clipboard helpers
    // -----------------------------------------------------------------------

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
        if (statusLabel != null) statusLabel.setText("Cell value copied to clipboard.");
    }

    private JMenuItem makeCopyTableItem(JTable table) {
        JMenuItem item = new JMenuItem("Copy Table", ToolbarIcons.menuCopyTable());
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T,
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        item.addActionListener(ae -> copyTableToClipboard(table));
        return item;
    }

    private void copyTableToClipboard(JTable table) {
        String tsv = FilterSupport.buildTableTsv(table);
        Toolkit.getDefaultToolkit()
               .getSystemClipboard()
               .setContents(new StringSelection(tsv), null);
        if (statusLabel != null) statusLabel.setText("Table copied to clipboard.");
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

    private void setupStatusBar() {
        statusLabel = new JLabel("Ready. Please open a SWIFT MT file (Ctrl+O).");
        statusLabel.setBorder(new EmptyBorder(2, 8, 2, 8));
    }


    private void addToggleBottomListener(ToolWindowButton btn, String card) {
        btn.addActionListener(e -> {
            if (btn.isSelected()) bottomCtrl.show(card);
            else bottomCtrl.collapse();
        });
    }

    private void assembleMainLayout(JSplitPane outerSplit) {
        JSplitPane    bottomSplit;
        explorerTwBtn   = new ToolWindowButton("Explorer",           ToolbarIcons.folderIcon());
        bookmarksTwBtn  = new ToolWindowButton(BOOKMARKS,            ToolbarIcons.bookmarkRibbon());
        securitiesTwBtn    = new ToolWindowButton(LABEL_SECURITIES,    ToolbarIcons.securitiesIcon());
        cashTwBtn          = new ToolWindowButton(LABEL_CASH,          ToolbarIcons.cashIcon());
        accountMappingTwBtn = new ToolWindowButton(LABEL_ACCOUNT_MAPPING, ToolbarIcons.accountMappingIcon());
        explorerTwBtn.setSelected(true);
        explorerTwBtn.addActionListener(e -> {
            if (explorerTwBtn.isSelected()) toggleMessageSourcePanel();
            else collapseExplorer();
        });
        addToggleBottomListener(bookmarksTwBtn,      BottomPanelController.BOOKMARKS);
        addToggleBottomListener(securitiesTwBtn,     BottomPanelController.SECURITIES);
        addToggleBottomListener(cashTwBtn,           BottomPanelController.CASH);
        addToggleBottomListener(accountMappingTwBtn, BottomPanelController.ACCOUNT_MAPPING);

        JPanel twBar      = buildToolWindowBar();
        JPanel detailBar  = detailCtrl.buildDetailBar();

        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEtchedBorder());
        JPanel statusRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        statusRight.setOpaque(false);
        statusRight.add(entryPanel.rowCountLabel);
        statusBar.add(statusLabel,  BorderLayout.WEST);
        statusBar.add(statusRight,  BorderLayout.EAST);

        CsvExport.Prefs postingCsvPrefs = new CsvExport.Prefs(PREFS, PREF_CSV_FIELD_SEP, PREF_CSV_DECIMAL_SEP);
        bottomCtrl = new BottomPanelController(bookmarkPanel, entryPanel::applyFilterForSafe,
            postingCsvPrefs, PREFS, PREF_ACCOUNT_MAPPING, this::syncBottomTwButtons);
        JPanel bottomWrapper = bottomCtrl.buildPanel();
        trackFocus(bottomCtrl.accountMappingTable());
        bottomSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        bottomSplit.setTopComponent(outerSplit);
        bottomSplit.setBottomComponent(bottomWrapper);
        bottomSplit.setResizeWeight(1.0);
        bottomSplit.setDividerSize(5);
        bottomWrapper.setMinimumSize(new Dimension(0, 0));
        SwingUtilities.invokeLater(() ->
            bottomSplit.setDividerLocation(bottomSplit.getHeight() - bottomSplit.getDividerSize()));
        bottomCtrl.setSplit(bottomSplit);

        JPanel center = new JPanel(new BorderLayout(0, 0));
        center.add(twBar,       BorderLayout.WEST);
        center.add(bottomSplit, BorderLayout.CENTER);
        center.add(detailBar,   BorderLayout.EAST);

        JPanel root = new JPanel(new BorderLayout(4, 4));
        root.setBorder(new EmptyBorder(4, 4, 4, 4));
        root.add(center,    BorderLayout.CENTER);
        root.add(statusBar, BorderLayout.SOUTH);
        setContentPane(root);

        int wx = PREFS.getInt(PREF_WIN_X, Integer.MIN_VALUE);
        if (wx != Integer.MIN_VALUE) {
            Rectangle saved = new Rectangle(wx, PREFS.getInt(PREF_WIN_Y, 0),
                    PREFS.getInt(PREF_WIN_W, 1280), PREFS.getInt(PREF_WIN_H, 820));
            // Ignore a saved position left over from a monitor that is no longer connected
            // (or a resolution change) — otherwise the window would restore off-screen and
            // appear not to open at all. The default centered bounds set above stay in effect.
            if (isOnScreen(saved)) setBounds(saved);
        }

        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { saveWindowPrefs(); }
            @Override public void componentMoved  (java.awt.event.ComponentEvent e) { saveWindowPrefs(); }
        });
    }

    private JSplitPane buildInnerSplit(JPanel left, JPanel right) {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.65);
        detailCtrl.setSplit(split);
        return split;
    }

    private JSplitPane buildOuterSplit(JSplitPane innerSplit) {
        messageSourcePanel = new MessageSourcePanel(
            loadExplorerRoots(), this::loadExplorerFiles, importer::importDirectory,
            this::showFileInEditor, this::saveExplorerRoots, this::collapseExplorer);
        bookmarkPanel = new BookmarkPanel(bookmarkManager, this::navigateToBookmark, this::collapseBottomPanel);

        navCardLayout = new CardLayout();
        navPanel = new JPanel(navCardLayout);
        navPanel.setOpaque(false);
        navPanel.setMinimumSize(new Dimension(0, 0));
        navPanel.add(messageSourcePanel, NAV_CARD_EXPLORER);

        explorerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navPanel, innerSplit);
        explorerSplit.setResizeWeight(0.0);
        int saved = PREFS.getInt(PREF_EXPLORER_SPLIT, 200);
        SwingUtilities.invokeLater(() -> explorerSplit.setDividerLocation(saved));
        explorerSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
            int loc = explorerSplit.getDividerLocation();
            if (loc > 0) PREFS.putInt(PREF_EXPLORER_SPLIT, loc);
        });
        return explorerSplit;
    }

    private Map<File, String> loadExplorerRoots() {
        String pref = PREFS.get(PREF_EXPLORER_ROOTS, "");
        Map<File, String> roots = new LinkedHashMap<>();
        if (pref.isBlank()) return roots;
        for (String line : pref.split("\n", -1)) {
            if (line.isBlank()) continue;
            int tab = line.indexOf('\t');
            String path = (tab < 0 ? line : line.substring(0, tab)).trim();
            String desc = tab < 0 ? "" : line.substring(tab + 1).trim();
            File f = new File(path);
            if (f.isDirectory()) roots.put(f, desc);
        }
        return roots;
    }

    private void saveExplorerRoots() {
        if (messageSourcePanel == null) return;
        Map<File, String> descs = messageSourcePanel.getRootDescriptions();
        StringBuilder sb = new StringBuilder();
        for (File f : messageSourcePanel.getRootDirs()) {
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(f.getAbsolutePath());
            String desc = descs.get(f);
            if (desc != null && !desc.isBlank()) sb.append('\t').append(desc.trim());
        }
        PREFS.put(PREF_EXPLORER_ROOTS, sb.toString());
    }

    private void loadExplorerFiles(List<File> files) {
        if (files.isEmpty()) return;
        if (files.size() == 1) { importer.loadFile(files.get(0)); return; }
        File[] arr = files.toArray(new File[0]);
        File dir = files.get(0).getParentFile();
        importer.importFileBatch(arr, dir != null ? dir : files.get(0));
    }

    /** Drops onto the entry table append to the current data instead of replacing it. */
    private void appendDroppedFiles(List<File> files) {
        for (File f : files) importer.appendFile(f);
    }

    private void saveWindowPrefs() {
        PREFS.putInt(PREF_WIN_X, getX()); PREFS.putInt(PREF_WIN_Y, getY());
        PREFS.putInt(PREF_WIN_W, getWidth()); PREFS.putInt(PREF_WIN_H, getHeight());
    }

    /** True when {@code bounds} overlaps at least one currently connected screen. */
    private static boolean isOnScreen(Rectangle bounds) {
        for (GraphicsDevice gd : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())
            if (gd.getDefaultConfiguration().getBounds().intersects(bounds)) return true;
        return false;
    }

    // -----------------------------------------------------------------------
    // Load file
    // -----------------------------------------------------------------------
    private JFileChooser createSwiftFileChooser(String title) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(title);
        fc.setFileFilter(new FileNameExtensionFilter(
            "SWIFT Files (*.txt, *.swift, *.mt5xx, *.mt9xx, *.ste, *.log, *.csv)",
            "txt", "swift", "mt527", "mt536", "mt558", "mt940", "mt950", "ste", "log", "csv"));
        fc.setAcceptAllFileFilterUsed(true);
        return restoreLastDir(fc, PREF_LAST_FILE);
    }

    private void onOpenSession() {
        JFileChooser fc = createSessionFileChooser("Open Session");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        currentSessionFile = fc.getSelectedFile();
        importer.loadFile(currentSessionFile);
    }

    private void onSaveSession() {
        if (currentSessionFile != null) {
            doSaveSession(currentSessionFile);
        } else {
            File file = pickSessionSaveFile();
            if (file != null) doSaveSession(file);
        }
    }

    private void onSaveSelectedMtAs() {
        JTable table = entryPanel.getTable();
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            statusLabel.setText("Select an entry in MT Entries first.");
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        SwiftMessage msg = entryPanel.getMessageForRow(modelRow);
        if (msg == null) {
            statusLabel.setText("No SWIFT message for selected entry.");
            return;
        }
        mtExport.exportSingle(this,
            msg.raw(),
            config.getMtExportSender(),
            config.getMtExportReceiver(),
            entryPanel.getRowValue(modelRow, EntryPanelModel.FILE_COL_KEY),
            statusLabel::setText);
    }

    private void onSaveExcel() {
        excelExport.exportComponents(this,
            entryPanel.getFullDisplaySequences(),
            entryPanel.getRowData(),
            SEQ_KEY,
            statusLabel::setText);
    }

    private File pickSessionSaveFile() {
        JFileChooser fc = createSessionFileChooser("Save Session");
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return null;
        File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase(java.util.Locale.ROOT).endsWith("." + SESSION_EXT))
            file = new File(file.getAbsolutePath() + "." + SESSION_EXT);
        return file;
    }

    private void doSaveSession(File file) {
        try {
            ProjectIO.save(entryPanel.getProject(), file);
            currentSessionFile = file;
            PREFS.put(PREF_LAST_SESSION_FILE, file.getAbsolutePath());
            statusLabel.setText("Session saved: " + file.getAbsolutePath());
        } catch (IllegalArgumentException ex) {
            error(ex.getMessage());
        } catch (IOException ex) {
            fileError("saving", ex);
        }
    }

    private JFileChooser createSessionFileChooser(String title) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(title);
        fc.setFileFilter(new FileNameExtensionFilter(
            "MT Session Files (*." + SESSION_EXT + ")", SESSION_EXT));
        fc.setAcceptAllFileFilterUsed(true);
        return restoreLastDir(fc, PREF_LAST_SESSION_FILE);
    }

    private JFileChooser restoreLastDir(JFileChooser fc, String prefKey) {
        String lastFile = PREFS.get(prefKey, "");
        if (!lastFile.isEmpty()) {
            File lastDir = new File(lastFile).getParentFile();
            if (lastDir != null && lastDir.exists()) fc.setCurrentDirectory(lastDir);
        }
        return fc;
    }

    // -----------------------------------------------------------------------
    // ImportContext – kept private so package-private types stay unexposed
    // -----------------------------------------------------------------------

    private ImportContext createImportContext() {
        return new ImportContext() {
            @Override public Frame         frame()                          { return MtAnalyzeFrame.this; }
            @Override public SystemConfig  config()                         { return MtAnalyzeFrame.this.config(); }
            @Override public ImportService importService()                  { return MtAnalyzeFrame.this.importService(); }
            @Override public String        promptMtType(String m)           { return MtAnalyzeFrame.this.promptMtType(m); }
            @Override public java.util.Optional<java.util.Set<String>> promptMtTypeFilter(String f) { return MtAnalyzeFrame.this.promptMtTypeFilter(f); }
            @Override public void          onNew()                          { MtAnalyzeFrame.this.onNew(); }
            @Override public void          onFileLoaded(ImportBatch b, File f)              { MtAnalyzeFrame.this.onFileLoaded(b, f); }
            @Override public void          onDirectoryLoaded(ImportBatch b, File d, int n)  { MtAnalyzeFrame.this.onDirectoryLoaded(b, d, n); }
            @Override public void          onContentAppended(ImportBatch b)                  { MtAnalyzeFrame.this.onContentAppended(b); }
            @Override public void          onFileAppended(File f)           { MtAnalyzeFrame.this.onFileAppended(f); }
            @Override public void          error(String m)                  { MtAnalyzeFrame.this.error(m); }
            @Override public void          fileError(String v, Exception e) { MtAnalyzeFrame.this.fileError(v, e); }
        };
    }

    private SystemConfig  config()        { return config; }
    private ImportService importService() { return importService; }

    private void onFileLoaded(ImportBatch batch, File file) {
        notifyProwideLog(batch);
        if (batch.totalParsed == 0) { error("No valid SWIFT messages found."); return; }
        tagPanel.clear();
        entryPanel.clearSearch();
        tagPanel.clearSearch();
        entryPanel.loadBatch(batch.messages, batch.columnDefs);
        entryPanel.applyColumnPrefs();
        entryPanel.rebuildPositionTable();
        selectFirstRow();
        int n = batch.totalParsed;
        statusLabel.setText("Loaded: " + file.getAbsolutePath());
        detailCtrl.notificationPanel().addNotification(NotificationPanel.Type.INFO, "File loaded",
            file.getName() + " (" + n + (n == 1 ? MSG_SINGULAR : MSG_PLURAL) + ")");
        PREFS.put(PREF_LAST_FILE, file.getAbsolutePath());
        reloadItem.setEnabled(true);
        saveItem.setEnabled(true);
        saveExcelItem.setEnabled(true);
        markExplorerFileMtType(file);
        notifyBatchErrors(batch.errors);
        if (batch.limitReached) warnLimitReached();
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
        statusLabel.setText(msg);
        detailCtrl.notificationPanel().addNotification(NotificationPanel.Type.INFO, "Import complete",
            msg + " (" + entryPanel.getLoadedMessages().size() + " messages total)");
        notifyBatchErrors(batch.errors);
        notifyProwideLog(batch);
        reloadItem.setEnabled(false);
        saveItem.setEnabled(true);
        saveExcelItem.setEnabled(true);
        if (batch.limitReached) warnLimitReached();
    }

    private void onContentAppended(ImportBatch batch) {
        entryPanel.mergeBatch(batch.messages, batch.columnDefs);
        entryPanel.applyColumnPrefs();
        entryPanel.rebuildPositionTable();
        statusLabel.setText("Appended: " + batch.totalParsed + (batch.totalParsed == 1 ? MSG_SINGULAR : MSG_PLURAL));
        notifyBatchErrors(batch.errors);
        notifyProwideLog(batch);
        if (batch.limitReached) warnLimitReached();
    }

    private void onFileAppended(File file) {
        int total = entryPanel.getLoadedMessages().size();
        statusLabel.setText("Appended: " + file.getAbsolutePath());
        detailCtrl.notificationPanel().addNotification(NotificationPanel.Type.INFO, "File appended",
            file.getName() + " (" + total + " messages total)");
        reloadItem.setEnabled(true);
        saveItem.setEnabled(true);
        saveExcelItem.setEnabled(true);
    }

    // -----------------------------------------------------------------------

    private void onOpenFile() {
        JFileChooser fc = createSwiftFileChooser("Open SWIFT MT File");
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            importer.loadFile(fc.getSelectedFile());
    }

    private void onAppendFile() {
        JFileChooser fc = createSwiftFileChooser("Append SWIFT MT File");
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            importer.appendFile(fc.getSelectedFile());
    }

    private void onImportDirectory() {
        JFileChooser fc = createSwiftFileChooser(IMPORT_DIR_TITLE);
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        importer.importDirectory(fc.getSelectedFile());
    }

    private void onNew() {
        detailCtrl.showCard(DetailPanelController.INSPECTOR);
        entryPanel.clearSearch();
        tagPanel.clearSearch();
        entryPanel.clear();
        entryPanel.rebuildPositionTable();
        tagPanel.clear();
        statusLabel.setText("New model created.");
        reloadItem.setEnabled(false);
        saveItem.setEnabled(false);
        saveExcelItem.setEnabled(false);
        currentSessionFile = null;
    }

    private void onReloadFile() {
        String path = PREFS.get(PREF_LAST_FILE, "");
        if (path.isEmpty()) return;
        File file = new File(path);
        if (!file.exists()) {
            JOptionPane.showMessageDialog(this,
                "File no longer exists:\n" + path, ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
            return;
        }
        importer.loadFile(file);
    }

    private void onValidateFile() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Validate SWIFT FIN File");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "SWIFT Files (*.txt, *.swift, *.fin, *.ste, *.log)",
            "txt", "swift", "fin", "ste", "log"));
        fc.setAcceptAllFileFilterUsed(true);
        String lastFile = PREFS.get(PREF_LAST_FILE, "");
        if (!lastFile.isEmpty()) {
            File lastDir = new File(lastFile).getParentFile();
            if (lastDir != null && lastDir.exists()) fc.setCurrentDirectory(lastDir);
        }
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        ValidateFileDialog.show(this, fc.getSelectedFile());
    }

    private void onAttachBlock5() {
        File initialDir = null;
        String lastFile = PREFS.get(PREF_LAST_FILE, "");
        if (!lastFile.isEmpty()) {
            File d = new File(lastFile).getParentFile();
            if (d != null && d.exists()) initialDir = d;
        }
        AttachBlock5Dialog.show(this, initialDir,
            path -> statusLabel.setText("Block 5 attached: " + path));
    }

    private void markExplorerFileMtType(File file) {
        if (messageSourcePanel == null) return;
        messageSourcePanel.clearFileMtTypes();
        String label = FileImporter.detectMtTypesLabel(
            entryPanel.getLoadedMessages().stream().map(SwiftMessage::raw).toList());
        if (!label.isEmpty()) messageSourcePanel.markFileMtType(file, label);
    }

    /**
     * Shows a combo-box dialog asking the user to choose a message type.
     * Returns the type number (e.g. "536") or null for Auto-detect / cancelled.
     */
    private String promptMtType(String message) {
        JComboBox<String> combo = new JComboBox<>(MtFileIO.getMtTypeItems());
        int result = JOptionPane.showConfirmDialog(this,
                new Object[]{message, combo},
                "Select Message Type", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return null;
        String selected = (String) combo.getSelectedItem();
        if (selected == null || selected.startsWith("Auto")) return null;
        return selected.replaceAll("\\D", "");
    }

    private java.util.Optional<java.util.Set<String>> promptMtTypeFilter(String logFileName) {
        String input = JOptionPane.showInputDialog(this,
                "Filter MT types in " + logFileName
                    + "\n(comma-separated, ranges allowed, e.g. 527,540-548,558 – empty = all types):",
                "Import Log Filter", JOptionPane.QUESTION_MESSAGE);
        if (input == null) return java.util.Optional.empty();
        java.util.Set<String> types = new java.util.LinkedHashSet<>();
        for (String token : input.split("[,;]+")) {
            addMtTypeFilterToken(token, types);
        }
        return java.util.Optional.of(types);
    }

    /**
     * Adds one filter token to {@code types}: a plain MT number (e.g. "548") or an inclusive
     * numeric range (e.g. "540-548"); an optional "MT" prefix is stripped from each side.
     * A malformed range (non-numeric or start &gt; end) is kept as a single literal token.
     */
    private static void addMtTypeFilterToken(String token, java.util.Set<String> types) {
        String t = token.trim();
        if (t.isEmpty()) return;
        int dash = t.indexOf('-');
        if (dash > 0) {
            String start = normalizeMtToken(t.substring(0, dash));
            String end   = normalizeMtToken(t.substring(dash + 1));
            if (start.matches("\\d+") && end.matches("\\d+") && Integer.parseInt(start) <= Integer.parseInt(end)) {
                for (int n = Integer.parseInt(start); n <= Integer.parseInt(end); n++) types.add(String.valueOf(n));
                return;
            }
        }
        types.add(normalizeMtToken(t));
    }

    private static String normalizeMtToken(String s) {
        return s.trim().toUpperCase().replaceFirst("^MT\\s*", "");
    }

    private void showFileInEditor(File file) {
        String content;
        try {
            content = new String(Files.readAllBytes(file.toPath()));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Cannot read file:\n" + ex.getMessage(),
                ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
            return;
        }
        JDialog dlg = new JDialog(this, file.getAbsolutePath(), false);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        SourcePanel sp = new SourcePanel();
        sp.showMessage(content);
        sp.setPreferredSize(new Dimension(800, 600));

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dlg.dispose());
        dlg.getRootPane().registerKeyboardAction(e -> dlg.dispose(),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        btnPanel.add(closeBtn);

        dlg.add(sp, BorderLayout.CENTER);
        dlg.add(btnPanel, BorderLayout.SOUTH);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private void fileError(String verb, Exception ex) {
        error("Error: " + ex.getMessage());
        JOptionPane.showMessageDialog(this, "Error " + verb + " file:\n" + ex.getMessage(),
            ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
    }

    private void notifyBatchErrors(int errors) {
        if (errors <= 0) return;
        detailCtrl.notificationPanel().addNotification(NotificationPanel.Type.WARNING, "Parse errors",
            errors + (errors == 1 ? " item" : " items") + " could not be parsed.");
    }

    private void notifyProwideLog(ImportBatch batch) {
        if (batch.prowideLog.isEmpty()) return;
        String body = "<html>" + String.join("<br>", batch.prowideLog) + "</html>";
        NotificationPanel.Type type = batch.prowideLog.stream()
            .anyMatch(s -> s.startsWith("[SEVERE"))
            ? NotificationPanel.Type.ERROR : NotificationPanel.Type.WARNING;
        detailCtrl.notificationPanel().addNotification(type, "Parser log", body);
        switchDetailCard(DetailPanelController.NOTIFICATIONS);
        detailCtrl.expandIfNeeded();
    }

    private void warnLimitReached() {
        int max = config.getMaxEntries();
        JOptionPane.showMessageDialog(this,
                "Entry limit of " + max + " reached. Some entries were not loaded.\n"
                + "Increase 'Max. entries' in Settings to load more.",
                "Entry Limit Reached", JOptionPane.WARNING_MESSAGE);
    }

    // -----------------------------------------------------------------------
    // About dialog / utilities
    // -----------------------------------------------------------------------
    static String loadVersion() {
        try (InputStream in = MtAnalyzeFrame.class.getResourceAsStream("/version.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                return p.getProperty("version", "unknown");
            }
        } catch (IOException e) {
            // fall through
        }
        return "unknown";
    }

    private void showAboutDialog() {
        JPanel panel = new JPanel() {
            @Override protected void paintComponent(Graphics g0) {
                super.paintComponent(g0);
                Graphics2D g = (Graphics2D) g0.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                       RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    BrandTheme.paintBackdrop(g, getWidth(), getHeight());
                } finally {
                    g.dispose();
                }
            }
        };
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BrandTheme.BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BrandTheme.BORDER, 1),
            new EmptyBorder(20, 36, 18, 36)));

        JLabel title   = aboutLabel(APP_NAME,                            22f, Font.BOLD,   BrandTheme.FG);
        JLabel version = aboutLabel("Version " + loadVersion(),          12f, Font.PLAIN,  BrandTheme.SUB);
        JLabel copy    = aboutLabel("© 2026 Ralf Schwarz",               12f, Font.PLAIN,  BrandTheme.FG);
        JLabel devLink = linkLabel("<html><a href=''>linkedin.com/in/ralfschwarz</a></html>", DEVELOPER_URL);
        JLabel license = aboutLabel("Licensed under the Apache License, Version 2.0",
                                                                         11f, Font.PLAIN,  BrandTheme.SUB);
        JLabel depsHdr = aboutLabel("Uses open source components:",      11f, Font.PLAIN,  BrandTheme.SUB);
        JLabel dep1    = aboutLabel("Prowide Core — SWIFT parsing (Apache License 2.0)",
                                                                         11f, Font.PLAIN,  BrandTheme.FG);
        JLabel dep2    = aboutLabel("FlatLaf — Swing look and feel (Apache License 2.0)",
                                                                         11f, Font.PLAIN,  BrandTheme.FG);
        JLabel dep3    = aboutLabel("Apache POI — Excel export (Apache License 2.0)",
                                                                         11f, Font.PLAIN,  BrandTheme.FG);
        JLabel swift   = aboutLabel("SWIFT is a trademark of S.W.I.F.T. SCRL. (www.swift.com)",
                                                                         10f, Font.ITALIC, BrandTheme.SUB);

        JLabel github = linkLabel("<html><a href=''>github.com/mtanalyze/mtanalyze</a></html>", GITHUB_URL);

        addRow(panel, title,    6);
        addRow(panel, version,  4);
        addRow(panel, github,  14);
        addAboutDivider(panel);
        addRow(panel, copy,     4);
        addRow(panel, devLink,  4);
        addRow(panel, license, 14);
        addAboutDivider(panel);
        addRow(panel, depsHdr,  4);
        addRow(panel, dep1,     2);
        addRow(panel, dep2,     2);
        addRow(panel, dep3,    14);
        addAboutDivider(panel);
        panel.add(swift);

        JDialog dlg = new JDialog(this, "About MT Analyze App", true);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dlg.setContentPane(panel);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    @SuppressWarnings("MagicConstant") // callers pass Font.PLAIN/BOLD/ITALIC constants
    private static JLabel aboutLabel(String text, float size, int style, Color fg) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(style, size));
        l.setForeground(fg);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    /** A centred, clickable label that opens {@code url} in the system browser. */
    private static JLabel linkLabel(String html, String url) {
        JLabel l = aboutLabel(html, 11f, Font.PLAIN, BrandTheme.SUB);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, l.getPreferredSize().height));
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                try { Desktop.getDesktop().browse(new java.net.URI(url)); }
                catch (Exception ex) { JOptionPane.showMessageDialog(null, ex.getMessage()); }
            }
        });
        return l;
    }

    private static void addRow(JPanel p, Component c, int vgap) {
        p.add(c);
        p.add(Box.createVerticalStrut(vgap));
    }

    private static void addAboutDivider(JPanel p) {
        JComponent div = new JComponent() {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 1); }
            @Override public Dimension getPreferredSize() { return new Dimension(1, 1); }
            @Override protected void paintComponent(Graphics g) {
                g.setColor(BrandTheme.BORDER);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        div.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(div);
        p.add(Box.createVerticalStrut(8));
    }

    private void applyReferenceSearch(String value) {
        entryPanel.applyReferenceSearch(value);
    }

    // -----------------------------------------------------------------------
    private void applyTheme(String theme) {
        try {
            setupThemeLookAndFeel(theme);
            installAlternateRowColor(theme);
            com.formdev.flatlaf.FlatLaf.updateUI();
        } catch (UnsupportedLookAndFeelException | ReflectiveOperationException ex) { /* L&F not available */ }
    }

    private boolean isPowerUser() {
        return PREFS.getBoolean(PREF_POWER_USER, false);
    }

    /** Sets visibility on every non-null component. */
    private static void setVisible(boolean visible, Component... components) {
        for (Component c : components) if (c != null) c.setVisible(visible);
    }

    private void applyPowerUserMode() {
        boolean on = PREFS.getBoolean(PREF_POWER_USER, false);
        entryPanel.applyPowerUserMode(on);
        setVisible(on,
            exportComponentsItem, validateFileItem, attachBlock5Item, openSessionItem, saveItem,
            menuExplorer, menuBookmarks, explorerTwBtn, bookmarksTwBtn, viewMenuSeparator,
            importMenu, exportMenu, importExportLeadingSeparator, importExportMiddleSeparator);
        JMenuBar bar = getJMenuBar();
        if (bar != null) { bar.revalidate(); bar.repaint(); }
    }

    private void applyExperimentalMode() {
        boolean on = config.isExperimentalMode();
        setVisible(on,
            cashTwBtn, securitiesTwBtn, accountMappingTwBtn,
            menuSecurities, menuCash, menuAccountMapping,
            importSecuritiesItem, importCashItem, importMappingItem, importPostingsSeparator,
            exportSecuritiesItem, exportCashItem, exportMappingItem, exportPostingsSeparator);
        if (!on && bottomCtrl != null && !bottomCtrl.isCollapsed()) {
            String card = bottomCtrl.getActiveCard();
            if (BottomPanelController.SECURITIES.equals(card)
                    || BottomPanelController.CASH.equals(card)
                    || BottomPanelController.ACCOUNT_MAPPING.equals(card))
                bottomCtrl.collapse();
        }
    }

    private static void setupThemeLookAndFeel(String theme) throws UnsupportedLookAndFeelException, ReflectiveOperationException {
        switch (theme) {
            case THEME_LIGHT: UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatLightLaf());   break;
            case "IntelliJ": UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatIntelliJLaf()); break;
            case "Dark":     UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatDarkLaf());     break;
            case "Darcula":  UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatDarculaLaf());  break;
            default:
                LookAndFeel laf = (LookAndFeel) Class.forName(theme)
                    .getDeclaredConstructor().newInstance();
                UIManager.setLookAndFeel(laf);
        }
    }

    static void installAlternateRowColor(String theme) {
        // Subtle stripe visible in both themes; FlatLaf's prepareRenderer() applies
        // this automatically to DefaultTableCellRenderer-based columns.
        UIManager.put("Table.alternateRowColor",
            isDarkTheme(theme) ? new Color(52, 56, 65) : new Color(237, 242, 252));
    }

    static boolean isDarkTheme(String theme) {
        return switch (theme) {
            case "Dark", "Darcula"      -> true;
            default                      -> false;
        };
    }

    private void error(String msg) {
        statusLabel.setText(msg);
        if (detailCtrl != null)
            detailCtrl.notificationPanel().addNotification(NotificationPanel.Type.ERROR, ERROR_TITLE, msg);
    }

    public static void launch() {
        System.setProperty("apple.awt.application.name", APP_NAME);
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", APP_NAME);
        setDockIcon();
        Preferences prefs = Preferences.userNodeForPackage(MtAnalyzeFrame.class);
        String theme = prefs.get(PREF_THEME, null);
        if (theme == null) {
            // migrate from old boolean pref
            theme = prefs.getBoolean(PREF_DARK_MODE, true) ? "Dark" : THEME_LIGHT;
        }
        final String finalTheme = theme;
        SwingUtilities.invokeLater(() -> {
            try {
                setupThemeLookAndFeel(finalTheme);
            } catch (UnsupportedLookAndFeelException | ReflectiveOperationException ex) {
                com.formdev.flatlaf.FlatDarkLaf.setup();
            }
            installAlternateRowColor(finalTheme);
            MtAnalyzeFrame frame = new MtAnalyzeFrame();
            frame.setVisible(true);
        });
    }

    private JPanel buildToolWindowBar() {
        return FrameToolbars.buildLeft(FrameToolbars.separatorBorder(false),
            explorerTwBtn, accountMappingTwBtn, cashTwBtn, securitiesTwBtn, bookmarksTwBtn);
    }


    private static void setDockIcon() {
        try {
            java.awt.Taskbar taskbar = java.awt.Taskbar.getTaskbar();
            if (taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE))
                taskbar.setIconImage(AppIcon.createAppIcon(128));
        } catch (UnsupportedOperationException | SecurityException ignored) {
            // not supported on this platform
        }
    }
}
