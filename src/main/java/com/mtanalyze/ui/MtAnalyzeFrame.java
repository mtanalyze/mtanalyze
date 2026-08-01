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


import com.mtanalyze.model.SwiftMessage;
import com.mtanalyze.config.SystemConfig;
import com.mtanalyze.parser.MtFileIO;
import com.mtanalyze.parser.MtParser;
import com.mtanalyze.parser.HintDictionary;
import com.mtanalyze.export.CsvExport;
import com.mtanalyze.export.MtExport;
import com.mtanalyze.export.ProjectIO;
import com.mtanalyze.ui.view.NotificationPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

public class MtAnalyzeFrame extends JFrame {

    private static final String APP_NAME                 = "MT Analyze";
    private static final String GITHUB_URL               = "https://github.com/mtanalyze/mtanalyze";
    private static final String DEVELOPER_URL            = "https://www.linkedin.com/in/ralfschwarz/";
    private static final String LABEL_ACCOUNT_MAPPING   = "Account Mapping";
    private static final String LABEL_NOTIFICATIONS     = "Notifications";
    static final String PASTE_MT_SNIPPET                = "Paste MT Snippet";
    private static final String MENU_PASTE              = "Paste";

    // -----------------------------------------------------------------------
    private final transient SystemConfig        config    = new SystemConfig();
    private final transient CsvExport           csvExport   = new CsvExport();
    private final transient MtExport            mtExport    = new MtExport();
    private final transient ImportService       importService = new ImportService();
    private final transient HintDictionary      dict          = new HintDictionary();

    // UI fields
    // -----------------------------------------------------------------------
    private JTabbedPane        tabs;
    private JPanel             eastSlot;
    private JPanel             statusRight;
    private transient MtEntryPanel menuBarEntryPanel;
    private JLabel             statusLabel;
    private JMenuItem          reloadItem;
    private JMenuItem          openSessionItem;
    private JMenuItem          saveItem;
    private JMenuItem          saveAsMtItem;
    private JMenuItem          exportComponentsItem;
    private JMenuItem          validateFileItem;
    private JMenuItem          attachBlock5Item;
    private JMenu              importMenu;
    private JMenu              exportMenu;
    private JSeparator         importExportLeadingSeparator;
    private JSeparator         importExportMiddleSeparator;
    private JSeparator         importExportTrailingSeparator;
    private JSeparator         viewMenuSeparator;
    private JMenuItem          importMappingItem;
    private JSeparator         importMappingSeparator;
    private JMenuItem          exportMappingItem;
    private JSeparator         exportMappingSeparator;

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
    private static final String PREF_POWER_USER            = "power_user";
    private static final String PREF_ACCOUNT_MAPPING       = "account_mapping";
    private static final String THEME_LIGHT             = "Light";

    private JButton    menuSearchBtn;

    private transient BottomPanelController      bottomCtrl;
    private JCheckBoxMenuItem    menuNotifications;
    private JCheckBoxMenuItem    menuAccountMapping;
    private JRadioButtonMenuItem menuTags;
    private JRadioButtonMenuItem menuCompare;
    private JRadioButtonMenuItem menuSource;
    private JRadioButtonMenuItem menuComponents;

    private ToolWindowButton       notificationsTwBtn;
    private ToolWindowButton       accountMappingTwBtn;

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
        initUI();
    }

    private void initUI() {
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1280, 820);
        setLocationRelativeTo(null);
        setupIcons();
        setupStatusBar();
        bottomCtrl = buildBottomCtrl();
        tabs = new JTabbedPane();
        tabs.addChangeListener(this::onTabChanged);
        Document first = createDocument();
        addDocumentTab(first);
        menuBarEntryPanel = first.entryPanel();
        setupMenuBar();
        eastSlot = new JPanel(new BorderLayout());
        eastSlot.add(first.detailCtrl().buildDetailBar(), BorderLayout.CENTER);
        assembleMainLayout(tabs, eastSlot);
        applyPowerUserMode();
        applyExperimentalMode();
        bindGlobalShortcuts();
        SwingUtilities.invokeLater(first::collapseDetailPanel);
    }

    /** Resolves the document behind the currently selected tab. */
    private Document activeDocument() {
        Component sel = tabs.getSelectedComponent();
        return sel == null ? null : (Document) ((JComponent) sel).getClientProperty(Document.class);
    }

    private Document addDocumentTab(Document doc) {
        JComponent root = doc.rootComponent();
        root.putClientProperty(Document.class, doc);
        tabs.addTab(doc.title(), root);
        tabs.setTabComponentAt(tabs.indexOfComponent(root), buildTabHeader(doc));
        tabs.setSelectedComponent(root);
        return doc;
    }

    private static final Object TAB_LABEL_KEY = new Object();

    private JComponent buildTabHeader(Document doc) {
        JLabel label = new JLabel(doc.title());
        JButton close = FrameLayout.makeCloseButton(() -> closeTab(doc));
        JPanel header = new JPanel(new BorderLayout(4, 0));
        header.setOpaque(false);
        header.add(label,  BorderLayout.CENTER);
        header.add(close,  BorderLayout.EAST);
        doc.rootComponent().putClientProperty(TAB_LABEL_KEY, label);
        return header;
    }

    /** Closes a tab; a fresh empty document is opened if that was the last one. */
    private void closeTab(Document doc) {
        tabs.remove(doc.rootComponent());
        if (tabs.getTabCount() == 0) addDocumentTab(createDocument());
    }

    /** Runs on every tab switch: pushes the newly active document's state into the shared chrome. */
    private void onTabChanged(javax.swing.event.ChangeEvent e) {
        // Fires once synchronously from addDocumentTab() while initUI() is still assembling the
        // chrome for the very first tab (eastSlot/statusRight don't exist yet) — nothing to swap.
        if (eastSlot == null || statusRight == null) return;
        Document doc = activeDocument();
        if (doc == null) return;
        onDocumentStateChanged(doc);

        eastSlot.removeAll();
        eastSlot.add(doc.detailCtrl().buildDetailBar(), BorderLayout.CENTER);
        eastSlot.revalidate();
        eastSlot.repaint();

        FrameMenuBar.swapEntryPanelWidgets(getJMenuBar(), menuBarEntryPanel, doc.entryPanel());
        menuBarEntryPanel = doc.entryPanel();

        statusRight.removeAll();
        statusRight.add(doc.entryPanel().rowCountLabel);
        statusRight.revalidate();
        statusRight.repaint();

        String title = doc.title();
        setTitle("Untitled".equals(title) ? APP_NAME : APP_NAME + " — " + title);
    }

    private void setupIcons() {
        List<java.awt.Image> icons = new ArrayList<>();
        for (int s : new int[]{16, 24, 32, 48, 64, 128})
            icons.add(AppIcon.createAppIcon(s));
        setIconImages(icons);
    }

    private BottomPanelController buildBottomCtrl() {
        CsvExport.Prefs postingCsvPrefs = new CsvExport.Prefs(PREFS, PREF_CSV_FIELD_SEP, PREF_CSV_DECIMAL_SEP);
        BottomPanelController ctrl = new BottomPanelController(
            postingCsvPrefs, PREFS, PREF_ACCOUNT_MAPPING, this::syncBottomTwButtons);
        ctrl.notificationPanel().setOnAdded(() -> {
            if (notificationsTwBtn != null) notificationsTwBtn.setBadge(true);
        });
        return ctrl;
    }

    private Document createDocument() {
        return new Document(this, PREFS,
            new MtEntryPanel.PrefKeys(PREF_COL_ORDER, PREF_COL_VIS, PREF_QUICK_FILTER_PROFILES, PREF_COL_LAYOUT_PROFILES),
            dict, config, importService, mtExport, bottomCtrl,
            statusLabel::setText, this::isPowerUser, this::onDocumentStateChanged);
    }

    /** Pushes a document's enablement/detail-card state into the shared chrome, if it's the active one. */
    private void onDocumentStateChanged(Document d) {
        updateTabTitle(d);
        if (d != activeDocument()) return;
        if (reloadItem   != null) reloadItem  .setEnabled(d.isReloadable());
        if (saveItem     != null) saveItem    .setEnabled(d.hasContent());
        if (saveAsMtItem != null) saveAsMtItem.setEnabled(d.hasSelection());
        String card = d.detailActiveCard();
        if (menuTags       != null) menuTags      .setSelected(d.isTagsActive());
        if (menuCompare    != null) menuCompare   .setSelected(DetailPanelController.COMPARE.equals(card));
        if (menuSource     != null) menuSource    .setSelected(DetailPanelController.EDITOR.equals(card));
        if (menuComponents != null) menuComponents.setSelected(d.isComponentsActive());
    }

    /** Keeps a tab's header label in sync with its document's current title, active or not. */
    private void updateTabTitle(Document d) {
        Object label = d.rootComponent().getClientProperty(TAB_LABEL_KEY);
        if (label instanceof JLabel l) l.setText(d.title());
    }

    private void setupMenuBar() {
        FrameMenuBar.Items items = FrameMenuBar.build(menuBarEntryPanel, createMenuCallbacks());
        setJMenuBar(items.menuBar());
        openSessionItem      = items.openSessionItem();
        saveItem             = items.saveItem();
        saveAsMtItem         = items.saveAsMtItem();
        reloadItem           = items.reloadItem();
        exportComponentsItem    = items.exportComponentsItem();
        validateFileItem        = items.validateFileItem();
        attachBlock5Item        = items.attachBlock5Item();
        importMenu              = items.importMenu();
        exportMenu              = items.exportMenu();
        importExportLeadingSeparator = items.importExportLeadingSeparator();
        importExportMiddleSeparator  = items.importExportMiddleSeparator();
        importExportTrailingSeparator = items.importExportTrailingSeparator();
        viewMenuSeparator            = items.viewMenuSeparator();
        importMappingItem       = items.importMappingItem();
        importMappingSeparator  = items.importMappingSeparator();
        exportMappingItem       = items.exportMappingItem();
        exportMappingSeparator  = items.exportMappingSeparator();
        menuSearchBtn        = items.searchButton();
        menuNotifications    = items.menuNotifications();
        menuAccountMapping   = items.menuAccountMapping();
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
            this::onOpenFile,
            this::onAppendFile,
            this::onImportDirectory,
            this::onReloadFile,
            this::onValidateFile,
            this::onAttachBlock5,
            () -> csvExport.export(this, activeDocument().entryPanel().getColumnDefs(), activeDocument().entryPanel().getRowData(), statusLabel::setText, csvPrefs),
            () -> csvExport.exportComponents(this, activeDocument().entryPanel().getFullDisplaySequences(), activeDocument().entryPanel().getRowData(), SEQ_KEY, statusLabel::setText, csvPrefs),
            () -> mtExport.export(this, activeDocument().entryPanel().getLoadedMessages().stream().map(SwiftMessage::raw).toList(),
                                  config.getMtExportSender(), config.getMtExportReceiver(),
                                  statusLabel::setText),
            () -> mtExport.export(this, activeDocument().entryPanel().getVisibleMessages().stream().map(SwiftMessage::raw).toList(),
                                  config.getMtExportSender(), config.getMtExportReceiver(),
                                  statusLabel::setText),
            () -> bottomCtrl.accountMappingPanel().showImportDialog(),
            () -> bottomCtrl.accountMappingPanel().showExportDialog(),
            this::showSettings,
            this::showSearchPopup,
            () -> HelpDialog.show(this),
            this::showAboutDialog,
            this::populateEditMenu,
            () -> bottomCtrl.toggle(BottomPanelController.NOTIFICATIONS),
            () -> bottomCtrl.toggle(BottomPanelController.ACCOUNT_MAPPING),
            () -> activeDocument().switchDetailCard(DetailPanelController.INSPECTOR),
            () -> activeDocument().switchDetailCard(DetailPanelController.COMPARE),
            () -> activeDocument().switchDetailCard(DetailPanelController.EDITOR),
            () -> activeDocument().switchToComponents()
        );
    }

    private void showSettings() {
        SettingsDialog.show(this, PREFS, new SettingsDialog.Config(
            new SettingsDialog.Config.CsvKeys(PREF_CSV_FIELD_SEP, PREF_CSV_DECIMAL_SEP),
            new SettingsDialog.Config.ThemeConfig(PREF_THEME, this::applyTheme),
            new SettingsDialog.Config.MtKeys(config::getMtExportSender, config::getMtExportReceiver, config::saveMtExportBic),
            new SettingsDialog.Config.PowerUserConfig(PREF_POWER_USER, this::applyPowerUserMode),
            new SettingsDialog.Config.ExperimentalConfig(config::isExperimentalMode, config::setExperimentalMode, this::applyExperimentalMode)),
            dict);
    }

    private void syncBottomTwButtons() {
        boolean collapsed   = bottomCtrl.isCollapsed();
        String  card        = bottomCtrl.getActiveCard();
        boolean amActive    = !collapsed && BottomPanelController.ACCOUNT_MAPPING.equals(card);
        boolean notifActive = !collapsed && BottomPanelController.NOTIFICATIONS.equals(card);
        if (accountMappingTwBtn != null) accountMappingTwBtn.setSelected(amActive);
        if (notificationsTwBtn  != null) notificationsTwBtn .setSelected(notifActive);
        if (menuAccountMapping  != null) menuAccountMapping .setSelected(amActive);
        if (menuNotifications   != null) menuNotifications  .setSelected(notifActive);
        if (notifActive && notificationsTwBtn != null) notificationsTwBtn.setBadge(false);
    }

    private void populateEditMenu(JMenu menu) {
        java.awt.Component fo = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
        if (fo instanceof JTextField tf) {
            boolean hasSel = tf.getSelectedText() != null && !tf.getSelectedText().isEmpty();
            int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
            JMenuItem copyItem  = new JMenuItem("Copy",  ToolbarIcons.menuCopy());
            JMenuItem cutItem   = new JMenuItem("Cut");
            JMenuItem pasteItem = new JMenuItem(MENU_PASTE, ToolbarIcons.menuPaste());
            copyItem .setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, menuMask));
            cutItem  .setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, menuMask));
            pasteItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V, menuMask));
            FrameLayout.wireTextMenuItems(tf, hasSel, copyItem, cutItem, pasteItem, menu::add);
            return;
        }
        if (tryPopulateBottomPanel(menu)) return;
        Document doc = activeDocument();
        JTable tbl = doc.focusedTable();
        int viewRow = tbl.getSelectedRow();
        int viewCol = tbl.getSelectedColumn();

        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        if (tbl == doc.tagPanel().getTable()) {
            copyPopupItemsToMenu(doc.tagPanel().buildContextMenu(viewRow, viewCol), menu);
            menu.addSeparator();
            JMenuItem pasteItem = new JMenuItem(MENU_PASTE, ToolbarIcons.menuPaste());
            pasteItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V, menuMask));
            pasteItem.addActionListener(e -> doc.showAppendTextDialog());
            menu.add(pasteItem);
        } else if (tbl.getRowCount() == 0) {
            JMenuItem pasteItem = new JMenuItem(MENU_PASTE, ToolbarIcons.menuPaste());
            pasteItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V, menuMask));
            pasteItem.addActionListener(e -> doc.showAppendTextDialog());
            menu.add(pasteItem);
        } else {
            int modelRow = viewRow >= 0 ? doc.entryPanel().getTable().convertRowIndexToModel(viewRow) : -1;
            int safeViewRow = (viewRow >= 0 && viewRow < tbl.getRowCount()) ? viewRow : -1;
            int safeViewCol = Math.max(viewCol, 0);
            copyPopupItemsToMenu(doc.entryPanel().buildRowContextMenu(modelRow, safeViewRow, safeViewCol), menu);
        }
    }

    private boolean tryPopulateBottomPanel(JMenu menu) {
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

    /** Ctrl+F focuses the search field that belongs to the currently active table. */
    private void bindGlobalShortcuts() {
        InputMap  im = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getRootPane().getActionMap();

        im.put(KeyStroke.getKeyStroke("ctrl F"), "focusSearch");
        am.put("focusSearch", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { showSearchPopup(); }
        });

        im.put(KeyStroke.getKeyStroke("ctrl D"), "toggleDetail");
        am.put("toggleDetail", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { activeDocument().detailCtrl().toggle(); }
        });
    }

    private void showSearchPopup() {
        Document doc = activeDocument();
        if (doc.focusedTable() == doc.tagPanel().getTable()) {
            SearchPopup.show(menuSearchBtn, doc.tagPanel().getSearchField(),
                doc.tagPanel().getClearButton(), doc.tagPanel().getMatchLabel());
        } else {
            SearchPopup.show(menuSearchBtn, doc.entryPanel().getSearchField(),
                doc.entryPanel().finClearBtn, doc.entryPanel().finPrevBtn,
                doc.entryPanel().finNextBtn, doc.entryPanel().finMatchLabel);
        }
    }

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

    private void assembleMainLayout(JComponent mainContent, JPanel detailBar) {
        JSplitPane    bottomSplit;
        notificationsTwBtn  = new ToolWindowButton(LABEL_NOTIFICATIONS,   ToolbarIcons.notificationIcon());
        accountMappingTwBtn = new ToolWindowButton(LABEL_ACCOUNT_MAPPING, ToolbarIcons.accountMappingIcon());
        addToggleBottomListener(notificationsTwBtn,  BottomPanelController.NOTIFICATIONS);
        addToggleBottomListener(accountMappingTwBtn, BottomPanelController.ACCOUNT_MAPPING);

        JPanel twBar = buildToolWindowBar();

        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEtchedBorder());
        statusRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        statusRight.setOpaque(false);
        statusRight.add(menuBarEntryPanel.rowCountLabel);
        statusBar.add(statusLabel,  BorderLayout.WEST);
        statusBar.add(statusRight,  BorderLayout.EAST);

        JPanel bottomWrapper = bottomCtrl.buildPanel();
        bottomSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        bottomSplit.setTopComponent(mainContent);
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
            if (isOnScreen(saved)) setBounds(saved);
        }

        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { saveWindowPrefs(); }
            @Override public void componentMoved  (java.awt.event.ComponentEvent e) { saveWindowPrefs(); }
        });
    }

    private void saveWindowPrefs() {
        PREFS.putInt(PREF_WIN_X, getX()); PREFS.putInt(PREF_WIN_Y, getY());
        PREFS.putInt(PREF_WIN_W, getWidth()); PREFS.putInt(PREF_WIN_H, getHeight());
    }

    /**
     * True if enough of {@code bounds} falls within some currently connected screen
     * to be draggable back into view. Guards against a saved position from a monitor
     * that is no longer attached (or a resolution change) leaving the window off-screen.
     */
    private static boolean isOnScreen(Rectangle bounds) {
        final int minVisible = 80;
        for (GraphicsDevice gd : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            Rectangle visible = bounds.intersection(gd.getDefaultConfiguration().getBounds());
            if (visible.width >= minVisible && visible.height >= minVisible) return true;
        }
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
        addDocumentTab(createDocument()).openSession(fc.getSelectedFile());
    }

    private void onSaveSession() { activeDocument().saveSession(); }

    private void onSaveSelectedMtAs() { activeDocument().saveSelectedMtAs(); }

    File pickSessionSaveFile() {
        JFileChooser fc = createSessionFileChooser("Save Session");
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return null;
        File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase(java.util.Locale.ROOT).endsWith("." + SESSION_EXT))
            file = new File(file.getAbsolutePath() + "." + SESSION_EXT);
        return file;
    }

    void rememberLastSessionFile(File file) {
        PREFS.put(PREF_LAST_SESSION_FILE, file.getAbsolutePath());
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

    private SystemConfig  config()        { return config; }

    void rememberLastFile(File file) {
        PREFS.put(PREF_LAST_FILE, file.getAbsolutePath());
    }

    // -----------------------------------------------------------------------

    void onOpenFile() {
        JFileChooser fc = createSwiftFileChooser("Open SWIFT MT File");
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            addDocumentTab(createDocument()).openFile(fc.getSelectedFile());
    }

    void onAppendFile() {
        JFileChooser fc = createSwiftFileChooser("Append SWIFT MT File");
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            activeDocument().appendFile(fc.getSelectedFile());
    }

    private void onImportDirectory() {
        JFileChooser fc = createSwiftFileChooser(IMPORT_DIR_TITLE);
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        activeDocument().importDirectory(fc.getSelectedFile());
    }

    private void onNew() { addDocumentTab(createDocument()); }

    private void onReloadFile() { activeDocument().reload(); }

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

    /**
     * Shows a combo-box dialog asking the user to choose a message type.
     * Returns the type number (e.g. "536") or null for Auto-detect / cancelled.
     */
    String promptMtType(String message) {
        JComboBox<String> combo = new JComboBox<>(MtFileIO.getMtTypeItems());
        int result = JOptionPane.showConfirmDialog(this,
                new Object[]{message, combo},
                "Select Message Type", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return null;
        String selected = (String) combo.getSelectedItem();
        if (selected == null || selected.startsWith("Auto")) return null;
        return selected.replaceAll("\\D", "");
    }

    java.util.Optional<java.util.Set<String>> promptMtTypeFilter(String logFileName) {
        String input = JOptionPane.showInputDialog(this,
                "Filter MT types in " + logFileName + "\n(comma-separated, e.g. 536,548 – empty = all types):",
                "Import Log Filter", JOptionPane.QUESTION_MESSAGE);
        if (input == null) return java.util.Optional.empty();
        java.util.Set<String> types = new java.util.LinkedHashSet<>();
        for (String token : input.split("[,;\\s]+")) {
            String t = token.trim().toUpperCase().replaceFirst("^MT", "");
            if (!t.isEmpty()) types.add(t);
        }
        return java.util.Optional.of(types);
    }

    void fileError(String verb, Exception ex) {
        error("Error: " + ex.getMessage());
        JOptionPane.showMessageDialog(this, "Error " + verb + " file:\n" + ex.getMessage(),
            ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
    }

    void notifyBatchErrors(int errors) {
        if (errors <= 0) return;
        bottomCtrl.notificationPanel().addNotification(NotificationPanel.Type.WARNING, "Parse errors",
            errors + (errors == 1 ? " item" : " items") + " could not be parsed.");
    }

    void notifyProwideLog(ImportBatch batch) {
        if (batch.prowideLog.isEmpty()) return;
        String body = "<html>" + String.join("<br>", batch.prowideLog) + "</html>";
        NotificationPanel.Type type = batch.prowideLog.stream()
            .anyMatch(s -> s.startsWith("[SEVERE"))
            ? NotificationPanel.Type.ERROR : NotificationPanel.Type.WARNING;
        bottomCtrl.notificationPanel().addNotification(type, "Parser log", body);
        bottomCtrl.show(BottomPanelController.NOTIFICATIONS);
    }

    void warnLimitReached() {
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
        addRow(panel, dep2,    14);
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

    /** All documents currently open, one per tab. */
    private List<Document> allDocuments() {
        List<Document> docs = new ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            Object doc = ((JComponent) tabs.getComponentAt(i)).getClientProperty(Document.class);
            if (doc instanceof Document d) docs.add(d);
        }
        return docs;
    }

    private void applyPowerUserMode() {
        boolean on = PREFS.getBoolean(PREF_POWER_USER, false);
        for (Document d : allDocuments()) d.applyPowerUserMode(on);
        setVisible(on,
            exportComponentsItem, validateFileItem, attachBlock5Item, openSessionItem, saveItem,
            viewMenuSeparator,
            importMenu, exportMenu, importExportLeadingSeparator, importExportMiddleSeparator,
            importExportTrailingSeparator);
        JMenuBar bar = getJMenuBar();
        if (bar != null) { bar.revalidate(); bar.repaint(); }
    }

    private void applyExperimentalMode() {
        boolean on = config.isExperimentalMode();
        setVisible(on,
            accountMappingTwBtn, menuAccountMapping,
            importMappingItem, importMappingSeparator,
            exportMappingItem, exportMappingSeparator);
        if (!on && bottomCtrl != null && !bottomCtrl.isCollapsed()
                && BottomPanelController.ACCOUNT_MAPPING.equals(bottomCtrl.getActiveCard()))
            bottomCtrl.collapse();
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

    void error(String msg) {
        statusLabel.setText(msg);
        if (bottomCtrl != null)
            bottomCtrl.notificationPanel().addNotification(NotificationPanel.Type.ERROR, ERROR_TITLE, msg);
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
            accountMappingTwBtn, notificationsTwBtn);
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
