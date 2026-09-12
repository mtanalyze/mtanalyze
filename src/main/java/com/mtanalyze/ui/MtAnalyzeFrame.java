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


import com.mtanalyze.model.Entry;
import com.mtanalyze.model.EntrySelectionListener;
import com.mtanalyze.model.MessageOrigin;
import com.mtanalyze.model.SwiftMessage;
import com.mtanalyze.config.ElasticSecretStore;
import com.mtanalyze.config.SystemConfig;
import com.mtanalyze.parser.MtFileIO;
import com.mtanalyze.parser.MtParser;
import com.mtanalyze.parser.HintDictionary;
import com.mtanalyze.export.CsvExport;
import com.mtanalyze.export.ExcelExport;
import com.mtanalyze.export.MtExport;
import com.mtanalyze.lucene.MessageIndexService;
import com.mtanalyze.lucene.MtLucene;
import com.mtanalyze.elastic.ElasticIndexService;
import com.mtanalyze.elastic.MtElastic;
import com.mtanalyze.ui.view.NotificationPanel;
import com.mtanalyze.ui.view.TagView;
import com.mtanalyze.util.FileChoosers;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.prefs.Preferences;

/**
 * Main application window. Hosts a tab strip of independent {@link EntryTab} workspaces —
 * each with its own MT Entries table, detail panel and file importer — plus the window-level
 * chrome shared by all of them (menu bar, status bar, settings).
 */
public class MtAnalyzeFrame extends JFrame {

    private static final String MSG_SINGULAR = " message";
    private static final String MSG_PLURAL   = " messages";
    private static final String APP_NAME                 = "MT Analyze";
    private static final String GITHUB_URL               = "https://github.com/mtanalyze/mtanalyze";
    private static final String DEVELOPER_URL            = "https://www.linkedin.com/in/ralfschwarz/";
    private static final String PASTE_MT_SNIPPET        = "Paste MT Snippet";
    private static final String MENU_PASTE              = "Paste";
    private static final String UNTITLED                = "Untitled";
    private static final String SEARCH_MESSAGES         = "Search Messages";
    private static final String UNKNOWN                 = "unknown";

    // -----------------------------------------------------------------------
    // Shared services (one instance for the whole window, used by every tab)
    // -----------------------------------------------------------------------
    private final transient SystemConfig        config    = new SystemConfig();
    private final transient CsvExport           csvExport   = new CsvExport();
    private final transient MtExport            mtExport    = new MtExport();
    private final transient ExcelExport         excelExport = new ExcelExport();
    private final transient ImportService       importService = new ImportService();
    private final transient MessageIndexService messageIndex  = new MessageIndexService();
    private final transient ElasticIndexService elasticIndex  = new ElasticIndexService();
    private final transient HintDictionary      dict          = new HintDictionary();
    private final transient MtEntryPanel.PrefKeys prefKeys;

    // -----------------------------------------------------------------------
    // Tabs
    // -----------------------------------------------------------------------
    private JTabbedPane           tabs;
    private JPanel                 contentHost;
    private CardLayout             contentCards;
    private final List<EntryTab>  openTabs = new ArrayList<>();
    private int                   untitledCounter = 0;
    private int                   tabIdSeq        = 0;

    // UI fields – shared window chrome
    // -----------------------------------------------------------------------
    private JLabel             statusLabel;
    private JMenuItem          saveAsMtItem;
    private JMenuItem          saveExcelItem;
    private JMenuItem          exportComponentsItem;
    private JMenuItem          validateFileItem;
    private JMenuItem          attachBlock5Item;
    private JMenuItem          removeDuplicatesItem;
    private JMenu              importMenu;
    private JMenu              exportMenu;
    private JSeparator         importExportLeadingSeparator;
    private JSeparator         importExportMiddleSeparator;

    private static final Preferences PREFS = Preferences.userNodeForPackage(MtAnalyzeFrame.class);
    private static final String PREF_COL_ORDER  = "col_order";
    private static final String PREF_COL_VIS    = "col_visibility";
    private static final String PREF_LAST_FILE         = "last_file";
    private static final String PREF_WIN_X      = "win_x";
    private static final String PREF_WIN_Y      = "win_y";
    private static final String PREF_WIN_W      = "win_w";
    private static final String PREF_WIN_H      = "win_h";

    private static final String PREF_DARK_MODE            = "dark_mode";  // legacy – used for migration only
    private static final String PREF_THEME                = "theme";
    private static final String PREF_CSV_FIELD_SEP        = "csv_field_sep";
    private static final String PREF_CSV_DECIMAL_SEP      = "csv_decimal_sep";
    private static final String PREF_USER_DICT             = "user_qualifier_values";
    private static final String PREF_POWER_USER            = "power_user";
    private static final String PREF_LUCENE_DIR            = "lucene_index_dir";
    private static final String PREF_LUCENE_MAX_HITS       = "lucene_max_hits";
    private static final String PREF_ELASTIC_HOST          = "elastic_host";
    private static final String PREF_ELASTIC_PORT          = "elastic_port";
    private static final String PREF_ELASTIC_SCHEME        = "elastic_scheme";
    private static final String PREF_ELASTIC_USERNAME      = "elastic_username";
    private static final String PREF_ELASTIC_INDEX         = "elastic_index";
    private static final String PREF_ELASTIC_MAX_HITS      = "elastic_max_hits";
    private static final String THEME_LIGHT             = "Light";

    private JButton    menuSearchBtn;

    /** Last Lucene / Elasticsearch query string, pre-filled into the "Search Messages" dialog. */
    private String lastLuceneQuery = "";
    private String lastElasticQuery = "";

    private JRadioButtonMenuItem menuNotifications;
    private JRadioButtonMenuItem menuTags;
    private JRadioButtonMenuItem menuCompare;
    private JRadioButtonMenuItem menuSource;
    private JRadioButtonMenuItem menuComponents;

    private static final String SEQ_KEY            = MtParser.SEQ_KEY;
    private static final String ERROR_TITLE         = "Error";
    private static final String IMPORT_DIR_TITLE    = "Import Directory";

    // -----------------------------------------------------------------------
    // Constructor / UI setup
    // -----------------------------------------------------------------------
    public MtAnalyzeFrame() {
        super(windowTitle());
        String legacyDict = PREFS.get(PREF_USER_DICT, "");
        if (!legacyDict.isEmpty()) {
            dict.loadUserEntriesFromCsv(legacyDict);
            if (dict.saveUserEntriesToFile(dict.getUserEntries()))
                PREFS.remove(PREF_USER_DICT);
        } else {
            dict.loadUserEntriesFromFile();
        }
        prefKeys = new MtEntryPanel.PrefKeys(PREF_COL_ORDER, PREF_COL_VIS);
        initUI();
    }

    private void initUI() {
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1280, 820);
        setLocationRelativeTo(null);
        setupIcons();
        setupMenuBar();
        setupStatusBar();
        applyLuceneConfig();
        applyElasticConfig();
        assembleMainLayout();
        openNewTab();
        applyPowerUserMode();
        bindGlobalShortcuts();
    }

    private void setupIcons() {
        List<java.awt.Image> icons = new ArrayList<>();
        for (int s : new int[]{16, 24, 32, 48, 64, 128})
            icons.add(AppIcon.createAppIcon(s));
        setIconImages(icons);
    }

    private void setupMenuBar() {
        FrameMenuBar.Items items = FrameMenuBar.build(createMenuCallbacks());
        setJMenuBar(items.menuBar());
        saveAsMtItem         = items.saveAsMtItem();
        saveExcelItem        = items.saveExcelItem();
        exportComponentsItem    = items.exportComponentsItem();
        validateFileItem        = items.validateFileItem();
        attachBlock5Item        = items.attachBlock5Item();
        removeDuplicatesItem    = items.removeDuplicatesItem();
        importMenu              = items.importMenu();
        exportMenu               = items.exportMenu();
        importExportLeadingSeparator = items.importExportLeadingSeparator();
        importExportMiddleSeparator  = items.importExportMiddleSeparator();
        menuSearchBtn        = items.searchButton();
        menuNotifications    = items.menuNotifications();
        menuTags             = items.menuTags();
        menuCompare          = items.menuCompare();
        menuSource           = items.menuSource();
        menuComponents       = items.menuComponents();
    }

    private FrameMenuBar.Callbacks createMenuCallbacks() {
        CsvExport.Prefs csvPrefs = new CsvExport.Prefs(PREFS, PREF_CSV_FIELD_SEP, PREF_CSV_DECIMAL_SEP);
        return new FrameMenuBar.Callbacks(
            this::openNewTab,
            () -> withActiveTab(EntryTab::onSaveMtAs),
            () -> withActiveTab(EntryTab::onSaveExcel),
            this::onOpenFile,
            this::onAppendFile,
            this::onImportDirectory,
            this::onValidateFile,
            this::onAttachBlock5,
            () -> withActiveTab(EntryTab::onRemoveDuplicates),
            () -> withActiveTab(t -> csvExport.export(this, t.entryPanel.getColumnDefs(), t.entryPanel.getRowData(), statusLabel::setText, csvPrefs)),
            () -> withActiveTab(t -> csvExport.exportComponents(this, t.entryPanel.getFullDisplaySequences(), t.entryPanel.getRowData(), SEQ_KEY, statusLabel::setText, csvPrefs)),
            () -> withActiveTab(t -> mtExport.export(this, t.entryPanel.getLoadedMessages().stream().map(SwiftMessage::raw).toList(),
                                  config.getMtExportSender(), config.getMtExportReceiver(),
                                  statusLabel::setText)),
            () -> withActiveTab(t -> mtExport.export(this, t.entryPanel.getVisibleMessages().stream().map(SwiftMessage::raw).toList(),
                                  config.getMtExportSender(), config.getMtExportReceiver(),
                                  statusLabel::setText)),
            this::showSettings,
            this::showSearchPopup,
            () -> HelpDialog.show(this),
            this::showAboutDialog,
            this::populateEditMenu,
            () -> withActiveTab(t -> t.switchDetailCard(DetailPanelController.NOTIFICATIONS)),
            () -> withActiveTab(t -> t.switchDetailCard(DetailPanelController.INSPECTOR)),
            () -> withActiveTab(t -> t.switchDetailCard(DetailPanelController.COMPARE)),
            () -> withActiveTab(t -> t.switchDetailCard(DetailPanelController.EDITOR)),
            () -> withActiveTab(EntryTab::switchToComponents),
            this::onIndexMessages,
            this::onSearchMessages,
            this::onClearLuceneIndex,
            this::onIndexMessagesElastic,
            this::onSearchMessagesElastic,
            this::onClearElasticIndex,
            this::onShowStatistics
        );
    }

    // -----------------------------------------------------------------------
    // Lucene: Index Messages / Search Messages
    // -----------------------------------------------------------------------

    @FunctionalInterface
    private interface Indexer {
        int indexMessages(List<SwiftMessage> messages, String sourceLabel,
                IntConsumer onProgress, BooleanSupplier cancelled) throws IOException;
    }

    /** Indexes every message of the active tab's MT Entries view into the shared Lucene index. */
    private void onIndexMessages() {
        runIndexing(messageIndex::indexMessages, messageIndex::documentCount, "into the Lucene index…",
            "the Lucene index", "the Lucene index at " + messageIndex.indexDir());
    }

    /** Indexes every message of the active tab's MT Entries view into the configured Elasticsearch index. */
    private void onIndexMessagesElastic() {
        runIndexing(elasticIndex::indexMessages, elasticIndex::documentCount, "into Elasticsearch…",
            "the Elasticsearch index", elasticIndex.connectionLabel());
    }

    /** Shared background-indexing flow for the Lucene / Elasticsearch "Index Messages" items. */
    private void runIndexing(Indexer indexer, CountSupplier documentCount, String progressVerb,
            String indexDescription, String indexedIntoDescription) {
        EntryTab t = activeTab();
        if (t == null) return;
        List<SwiftMessage> messages = t.entryPanel.getLoadedMessages();
        if (messages.isEmpty()) {
            statusLabel.setText("Nothing to index – this tab has no MT entries.");
            return;
        }
        final int count = messages.size();

        JProgressBar bar = new JProgressBar(0, count);
        bar.setStringPainted(true);
        bar.setString("0 / " + count);
        FrameLayout.ProgressDialog pd = FrameLayout.buildProgressDialog(this, "Index Messages",
            "Indexing " + count + (count == 1 ? MSG_SINGULAR : MSG_PLURAL) + " " + progressVerb, bar);

        SwingWorker<Integer, Integer> worker = new SwingWorker<>() {
            @Override protected Integer doInBackground() throws IOException {
                return indexer.indexMessages(messages, t.title, n -> publish(n), this::isCancelled);
            }
            @Override protected void process(List<Integer> progress) {
                int n = progress.get(progress.size() - 1);
                bar.setValue(n);
                bar.setString(n + " / " + count);
            }
            @Override protected void done() {
                finishIndexing(this, pd, t, documentCount, indexDescription, indexedIntoDescription);
            }
        };
        pd.runWorker(worker);
    }

    @FunctionalInterface
    private interface CountSupplier {
        long count() throws IOException;
    }

    /** Shared completion handler for the Lucene / Elasticsearch indexing background workers. */
    private void finishIndexing(SwingWorker<Integer, Integer> worker, FrameLayout.ProgressDialog pd, EntryTab t,
            CountSupplier documentCount, String indexDescription, String indexedIntoDescription) {
        pd.dialog().dispose();
        if (worker.isCancelled()) { statusLabel.setText("Indexing cancelled."); return; }
        try {
            int n = worker.get();
            long total = documentCount.count();
            statusLabel.setText(n + (n == 1 ? MSG_SINGULAR : MSG_PLURAL)
                + " indexed (" + total + " in " + indexDescription + ").");
            t.detailCtrl.notificationPanel().addNotification(
                NotificationPanel.Type.INFO, "Messages indexed",
                n + (n == 1 ? MSG_SINGULAR : MSG_PLURAL) + " indexed into "
                    + indexedIntoDescription + " (" + total + " total; a message already in the index is replaced).");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | IOException ex) {
            Throwable cause = ex instanceof ExecutionException && ex.getCause() != null ? ex.getCause() : ex;
            JOptionPane.showMessageDialog(this, "Indexing failed:\n" + cause.getMessage(),
                ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
        }
    }

    @FunctionalInterface
    private interface QueryRunner<H> {
        List<H> search(String query) throws IOException;
    }

    /** Prompts for a Lucene query string and shows the matching messages in a new tab. */
    private void onSearchMessages() {
        runSearch("Lucene", lastLuceneQuery, q -> lastLuceneQuery = q,
            messageIndex::search, messageIndex::maxHits, MtLucene.SwiftHit::rawMessage);
    }

    /** Prompts for a query_string search and shows the matching messages in a new tab. */
    private void onSearchMessagesElastic() {
        runSearch("Elasticsearch", lastElasticQuery, q -> lastElasticQuery = q,
            elasticIndex::search, elasticIndex::maxHits, MtElastic.SwiftHit::rawMessage);
    }

    /** Shared query-and-load flow for the Lucene / Elasticsearch "Search Messages" items. */
    private <H> void runSearch(String indexLabel, String lastQuery, Consumer<String> saveQuery,
            QueryRunner<H> runner, IntSupplier maxHits, Function<H, String> rawMessage) {
        String query = promptQuery(SEARCH_MESSAGES, lastQuery);
        if (query == null || query.isBlank()) return;
        saveQuery.accept(query);
        String trimmed = query.trim();

        List<H> hits;
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            hits = runner.search(trimmed);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                "Invalid Query", JOptionPane.WARNING_MESSAGE);
            return;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Search failed:\n" + ex.getMessage(),
                ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
            return;
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }

        if (hits.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No indexed messages match:\n" + trimmed
                    + "\n\n(Use " + indexLabel + " ▸ Index Messages first if the index is empty.)",
                SEARCH_MESSAGES, JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        List<String> chunks = hits.stream().map(rawMessage).toList();
        EntryTab tab = openNewTab();
        int parsed = tab.importer.appendFromContent(chunks, null, null, MessageOrigin.CLIPBOARD);
        tab.updateTitle("Search: " + trimmed);
        boolean capped = hits.size() >= maxHits.getAsInt();
        tab.setStatus(parsed + " of " + hits.size() + " search hit"
            + (hits.size() == 1 ? "" : "s") + (capped ? " (hit limit reached)" : "")
            + " loaded for query: " + trimmed);
    }

    /**
     * Multi-line query dialog shared by the Lucene and Elasticsearch "Search Messages" items.
     * Returns the entered query, or {@code null} if cancelled. Ctrl+Enter confirms.
     */
    private String promptQuery(String dialogTitle, String initialText) {
        JTextArea area = new JTextArea(8, 48);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setText(initialText);
        area.setCaretPosition(area.getText().length());
        area.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JScrollPane scroll = new JScrollPane(area);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scroll, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(560, 220));

        JOptionPane optionPane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE,
            JOptionPane.OK_CANCEL_OPTION);
        JDialog dialog = optionPane.createDialog(this, dialogTitle);
        // Ctrl+Enter = OK
        area.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "submit");
        area.getActionMap().put("submit", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                optionPane.setValue(JOptionPane.OK_OPTION);
                dialog.dispose();
            }
        });
        SwingUtilities.invokeLater(area::requestFocusInWindow);
        dialog.setVisible(true);
        dialog.dispose();

        Object value = optionPane.getValue();
        if (value == null || !value.equals(JOptionPane.OK_OPTION)) return null;
        return area.getText().trim();
    }

    private void onClearLuceneIndex() {
        onClearIndex(messageIndex::documentCount, messageIndex::clearIndex,
            "Lucene", "the Lucene index?\n" + messageIndex.indexDir());
    }

    // -----------------------------------------------------------------------
    // Elasticsearch: Index Messages / Search Messages
    // -----------------------------------------------------------------------

    private void onClearElasticIndex() {
        onClearIndex(elasticIndex::documentCount, elasticIndex::clearIndex,
            "Elasticsearch", "the Elasticsearch index?\n" + elasticIndex.connectionLabel());
    }

    /** Shared confirm-and-delete flow for the Lucene / Elasticsearch "Clear Index" items. */
    private void onClearIndex(CountSupplier documentCount, CountSupplier clearIndex,
            String indexLabel, String targetDescription) {
        long total;
        try {
            total = documentCount.count();
        } catch (IOException ex) {
            total = -1;
        }
        int choice = JOptionPane.showConfirmDialog(this,
            "Delete all " + (total >= 0 ? total + " " : "") + "documents from " + targetDescription,
            "Clear Index", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) return;
        try {
            long removed = clearIndex.count();
            statusLabel.setText(removed + " document" + (removed == 1 ? "" : "s")
                + " removed from the " + indexLabel + " index.");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not clear the index:\n" + ex.getMessage(),
                ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onShowStatistics() {
        StatisticsDialog.show(this, messageIndex, elasticIndex);
    }

    private void showSettings() {
        SettingsDialog.show(this, PREFS, new SettingsDialog.Config(
            new SettingsDialog.Config.CsvKeys(PREF_CSV_FIELD_SEP, PREF_CSV_DECIMAL_SEP),
            new SettingsDialog.Config.ThemeConfig(PREF_THEME, this::applyTheme),
            new SettingsDialog.Config.SystemKeys(
                config::getMtExportSender, config::getMtExportReceiver,
                config::getMaxEntries, config::getLogSwiftStart, config::getLogNewlineToken,
                config::saveSettings),
            new SettingsDialog.Config.PowerUserConfig(PREF_POWER_USER, this::applyPowerUserMode),
            new SettingsDialog.Config.LuceneConfig(PREF_LUCENE_DIR, PREF_LUCENE_MAX_HITS,
                MessageIndexService.defaultIndexDir().toString(),
                MessageIndexService.DEFAULT_MAX_HITS, this::applyLuceneConfig),
            new SettingsDialog.Config.ElasticConfig(
                PREF_ELASTIC_HOST, PREF_ELASTIC_PORT, PREF_ELASTIC_SCHEME,
                PREF_ELASTIC_USERNAME,
                () -> ElasticSecretStore.get(PREFS), password -> ElasticSecretStore.save(PREFS, password),
                PREF_ELASTIC_INDEX, PREF_ELASTIC_MAX_HITS,
                ElasticIndexService.DEFAULT_HOST, ElasticIndexService.DEFAULT_PORT,
                ElasticIndexService.DEFAULT_SCHEME, ElasticIndexService.DEFAULT_INDEX,
                ElasticIndexService.DEFAULT_MAX_HITS, this::applyElasticConfig)),
            dict);
    }

    /** Re-applies the persisted Lucene index directory and hit limit to the running service. */
    private void applyLuceneConfig() {
        messageIndex.configure(
            PREFS.get(PREF_LUCENE_DIR, ""),
            PREFS.getInt(PREF_LUCENE_MAX_HITS, MessageIndexService.DEFAULT_MAX_HITS));
    }

    /** Re-applies the persisted Elasticsearch connection settings to the running service. */
    private void applyElasticConfig() {
        elasticIndex.configure(
            PREFS.get(PREF_ELASTIC_HOST, ElasticIndexService.DEFAULT_HOST),
            PREFS.getInt(PREF_ELASTIC_PORT, ElasticIndexService.DEFAULT_PORT),
            PREFS.get(PREF_ELASTIC_SCHEME, ElasticIndexService.DEFAULT_SCHEME),
            PREFS.get(PREF_ELASTIC_USERNAME, ""),
            ElasticSecretStore.get(PREFS),
            PREFS.get(PREF_ELASTIC_INDEX, ElasticIndexService.DEFAULT_INDEX),
            PREFS.getInt(PREF_ELASTIC_MAX_HITS, ElasticIndexService.DEFAULT_MAX_HITS));
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
        EntryTab t = activeTab();
        if (t == null) return;
        JTable tbl = t.focusedTable != null ? t.focusedTable : t.entryPanel.getTable();
        int viewRow = tbl.getSelectedRow();
        int viewCol = tbl.getSelectedColumn();

        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        if (tbl == t.tagPanel.getTable()) {
            copyPopupItemsToMenu(t.tagPanel.buildContextMenu(viewRow, viewCol), menu);
            menu.addSeparator();
            JMenuItem pasteItem = new JMenuItem(MENU_PASTE, ToolbarIcons.menuPaste());
            pasteItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, menuMask));
            pasteItem.addActionListener(e -> t.showAppendTextDialog());
            menu.add(pasteItem);
        } else if (tbl.getRowCount() == 0) {
            JMenuItem pasteItem = new JMenuItem(MENU_PASTE, ToolbarIcons.menuPaste());
            pasteItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, menuMask));
            pasteItem.addActionListener(e -> t.showAppendTextDialog());
            menu.add(pasteItem);
        } else {
            int modelRow = viewRow >= 0 ? t.entryPanel.getTable().convertRowIndexToModel(viewRow) : -1;
            int safeViewRow = (viewRow >= 0 && viewRow < tbl.getRowCount()) ? viewRow : -1;
            int safeViewCol = Math.max(viewCol, 0);
            copyPopupItemsToMenu(t.entryPanel.buildRowContextMenu(modelRow, safeViewRow, safeViewCol), menu);
        }
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

    /** Ctrl+F focuses the active tab's MT Entries search field (search only applies to that
     *  view, not the Detail panel); Ctrl+D toggles the active tab's detail panel; Ctrl+N opens
     *  a new tab. */
    private void bindGlobalShortcuts() {
        InputMap  im = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getRootPane().getActionMap();

        im.put(KeyStroke.getKeyStroke("ctrl F"), "focusSearch");
        am.put("focusSearch", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { showSearchPopup(); }
        });

        im.put(KeyStroke.getKeyStroke("ctrl D"), "toggleDetail");
        am.put("toggleDetail", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { withActiveTab(t -> t.detailCtrl.toggle()); }
        });
    }

    private void showSearchPopup() {
        EntryTab t = activeTab();
        if (t == null) return;
        SearchPopup.show(menuSearchBtn, t.entryPanel.getSearchField(),
            t.entryPanel.finClearBtn, t.entryPanel.finPrevBtn,
            t.entryPanel.finNextBtn, t.entryPanel.finMatchLabel);
    }

    private void setupStatusBar() {
        statusLabel = new JLabel("Ready. Please open a SWIFT MT file (Ctrl+O).");
        statusLabel.setBorder(new EmptyBorder(2, 8, 2, 8));
    }

    // -----------------------------------------------------------------------
    // Tab management
    // -----------------------------------------------------------------------

    private EntryTab activeTab() {
        if (tabs == null) return null;
        int idx = tabs.getSelectedIndex();
        return (idx >= 0 && idx < openTabs.size()) ? openTabs.get(idx) : null;
    }

    /** Runs {@code action} against the active tab, if any. */
    private void withActiveTab(java.util.function.Consumer<EntryTab> action) {
        EntryTab t = activeTab();
        if (t != null) action.accept(t);
    }

    private EntryTab openNewTab() {
        EntryTab tab = new EntryTab();
        openTabs.add(tab);
        // The JTabbedPane only ever shows the tab strip (see assembleMainLayout) — each tab's
        // real content lives in contentHost's CardLayout instead, switched in onActiveTabChanged().
        JPanel dummyPage = new JPanel();
        dummyPage.setPreferredSize(new Dimension(0, 0));
        tabs.addTab(tab.title, dummyPage);
        contentHost.add(tab.content, tab.cardId);
        tabs.setSelectedIndex(openTabs.size() - 1);
        onActiveTabChanged();
        return tab;
    }

    private void closeTab(int idx) {
        if (idx < 0 || idx >= openTabs.size()) return;
        EntryTab closed = openTabs.remove(idx);
        tabs.removeTabAt(idx);
        contentHost.remove(closed.content);
        if (openTabs.isEmpty()) openNewTab();
    }

    /** Prompts for a new name for the tab at {@code idx} and applies it, unless cancelled or blank. */
    private void renameTab(int idx) {
        if (idx < 0 || idx >= openTabs.size()) return;
        EntryTab t = openTabs.get(idx);
        String newTitle = (String) JOptionPane.showInputDialog(this,
            "Tab name:", "Rename Tab", JOptionPane.PLAIN_MESSAGE, null, null, t.title);
        if (newTitle == null) return;
        newTitle = newTitle.trim();
        if (!newTitle.isEmpty() && !newTitle.equals(t.title)) t.updateTitle(newTitle);
    }

    private void showTabPopup(int idx, java.awt.event.MouseEvent e) {
        if (idx < 0 || idx >= openTabs.size()) return;
        JPopupMenu popup = new JPopupMenu();
        JMenuItem renameItem = new JMenuItem("Rename Tab...");
        renameItem.addActionListener(a -> renameTab(idx));
        popup.add(renameItem);
        popup.show(tabs, e.getX(), e.getY());
    }

    private void onActiveTabChanged() {
        EntryTab t = activeTab();
        if (t == null) return;
        contentCards.show(contentHost, t.cardId);
        statusLabel.setText(t.statusText);
        refreshFileMenuState();
        t.detailCtrl.syncButtons();
    }

    /** Refreshes the shared File-menu item enablement to reflect the active tab's state. */
    private void refreshFileMenuState() {
        EntryTab t = activeTab();
        if (t == null) return;
        saveExcelItem.setEnabled(!t.entryPanel.getLoadedMessages().isEmpty());
        saveAsMtItem.setEnabled(!t.entryPanel.getLoadedMessages().isEmpty());
    }

    /**
     * Context-menu action: copies the messages currently visible in {@code source}'s
     * entries table (after filtering) into another tab chosen by the user, or a new one.
     * The messages are re-parsed from their FIN text so the target tab gets independent
     * copies.
     */
    private void copyVisibleMessagesToTab(EntryTab source) {
        List<SwiftMessage> visible = source.entryPanel.getVisibleMessages();
        if (visible.isEmpty()) {
            source.setStatus("No visible messages to copy.");
            return;
        }

        final String newTabOption = "＋  New Tab";
        List<EntryTab> targets = new ArrayList<>();
        List<String> options = new ArrayList<>();
        options.add(newTabOption);
        for (int i = 0; i < openTabs.size(); i++) {
            EntryTab t = openTabs.get(i);
            if (t == source) continue;
            targets.add(t);
            options.add((i + 1) + ":  " + t.title);
        }

        String choice = (String) JOptionPane.showInputDialog(this,
            "Copy " + visible.size() + (visible.size() == 1 ? MSG_SINGULAR : MSG_PLURAL) + " to:",
            "Copy Visible Messages to Tab", JOptionPane.QUESTION_MESSAGE, null,
            options.toArray(), options.get(0));
        if (choice == null) return;

        EntryTab target = choice.equals(newTabOption)
            ? openNewTab()
            : targets.get(options.indexOf(choice) - 1);

        List<String> chunks = new ArrayList<>(visible.size());
        for (SwiftMessage m : visible) {
            try {
                String fin = m.raw().message();
                if (fin != null && !fin.isBlank()) chunks.add(fin);
            } catch (RuntimeException ignored) {
                // skip a message that cannot be serialized back to FIN
            }
        }
        int parsed = target.importer.appendFromContent(chunks, null, null, MessageOrigin.CLIPBOARD);
        int idx = openTabs.indexOf(target);
        if (idx >= 0) tabs.setSelectedIndex(idx);
        target.setStatus(parsed + (parsed == 1 ? MSG_SINGULAR : MSG_PLURAL)
            + " copied from " + source.title + ".");
    }

    // -----------------------------------------------------------------------

    private void assembleMainLayout() {
        tabs = new JTabbedPane();
        tabs.putClientProperty("JTabbedPane.tabClosable", true);
        tabs.putClientProperty("JTabbedPane.tabCloseToolTipText", "Close tab");
        tabs.putClientProperty("JTabbedPane.tabCloseCallback", (IntConsumer) this::closeTab);
        JButton newTabBtn = new JButton("+");
        newTabBtn.setMargin(new Insets(1, 6, 1, 6));
        newTabBtn.setToolTipText("New Tab (Ctrl+N)");
        newTabBtn.addActionListener(e -> openNewTab());
        tabs.putClientProperty("JTabbedPane.trailingComponent", newTabBtn);
        tabs.addChangeListener(e -> onActiveTabChanged());
        tabs.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    renameTab(tabs.indexAtLocation(e.getX(), e.getY()));
                }
            }
            @Override public void mousePressed(java.awt.event.MouseEvent e)  { maybeShowPopup(e); }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) { maybeShowPopup(e); }
            private void maybeShowPopup(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) showTabPopup(tabs.indexAtLocation(e.getX(), e.getY()), e);
            }
        });

        contentCards = new CardLayout();
        contentHost  = new JPanel(contentCards);

        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEtchedBorder());
        statusBar.add(statusLabel, BorderLayout.WEST);

        // Tab strip at the bottom, Excel-sheet-style; the tab pane itself never shows
        // real content (see openNewTab), so it collapses to just the strip's height.
        JPanel bottomArea = new JPanel(new BorderLayout());
        bottomArea.add(tabs,      BorderLayout.NORTH);
        bottomArea.add(statusBar, BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout(4, 4));
        root.setBorder(new EmptyBorder(4, 4, 4, 4));
        root.add(contentHost, BorderLayout.CENTER);
        root.add(bottomArea,  BorderLayout.SOUTH);
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
        JFileChooser fc = FileChoosers.create();
        fc.setDialogTitle(title);
        fc.setFileFilter(new FileNameExtensionFilter(
            "SWIFT Files (*.txt, *.swift, *.mt5xx, *.mt9xx, *.ste, *.log, *.csv)",
            "txt", "swift", "mt527", "mt536", "mt558", "mt940", "mt950", "ste", "log", "csv"));
        fc.setAcceptAllFileFilterUsed(true);
        return restoreLastDir(fc);
    }

    private JFileChooser restoreLastDir(JFileChooser fc) {
        String lastFile = PREFS.get(PREF_LAST_FILE, "");
        if (!lastFile.isEmpty()) {
            File lastDir = new File(lastFile).getParentFile();
            if (lastDir != null && lastDir.exists()) fc.setCurrentDirectory(lastDir);
        }
        return fc;
    }

    // -----------------------------------------------------------------------

    private void onOpenFile() {
        JFileChooser fc = createSwiftFileChooser("Open SWIFT MT File");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();
        openNewTab().importer.loadFile(file);
    }

    private void onAppendFile() {
        EntryTab t = activeTab();
        if (t == null) return;
        JFileChooser fc = createSwiftFileChooser("Append SWIFT MT File");
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            t.importer.appendFile(fc.getSelectedFile());
    }

    private void onImportDirectory() {
        JFileChooser fc = createSwiftFileChooser(IMPORT_DIR_TITLE);
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File dir = fc.getSelectedFile();
        openNewTab().importer.importDirectory(dir);
    }

    private void onValidateFile() {
        JFileChooser fc = FileChoosers.create();
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

    // -----------------------------------------------------------------------
    // About dialog / utilities
    // -----------------------------------------------------------------------
    /** Main-window title: the app name, plus the release version when it is known. */
    private static String windowTitle() {
        String v = loadVersion();
        return (v == null || v.isBlank() || UNKNOWN.equals(v) || v.contains("${"))
                ? APP_NAME
                : APP_NAME + " " + v;
    }

    static String loadVersion() {
        try (InputStream in = MtAnalyzeFrame.class.getResourceAsStream("/version.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                return p.getProperty("version", UNKNOWN);
            }
        } catch (IOException e) {
            // fall through
        }
        return UNKNOWN;
    }

    private void showAboutDialog() {
        JDialog dlg = new JDialog(this, "About MT Analyze App", true);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dlg.setContentPane(buildAboutPanel());
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private static JPanel buildAboutPanel() {
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
        JLabel copy    = aboutLabel("© 2026 Centerscout GmbH",           12f, Font.PLAIN,  BrandTheme.FG);
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
        return panel;
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

    private void applyPowerUserMode() {
        boolean on = isPowerUser();
        setVisible(on,
            exportComponentsItem, validateFileItem, attachBlock5Item, removeDuplicatesItem,
            importMenu, exportMenu, importExportLeadingSeparator, importExportMiddleSeparator);
        JMenuBar bar = getJMenuBar();
        if (bar != null) { bar.revalidate(); bar.repaint(); }
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

    private static void setDockIcon() {
        try {
            java.awt.Taskbar taskbar = java.awt.Taskbar.getTaskbar();
            if (taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE))
                taskbar.setIconImage(AppIcon.createAppIcon(128));
        } catch (UnsupportedOperationException | SecurityException ignored) {
            // not supported on this platform
        }
    }

    // =========================================================================================
    // EntryTab – one self-contained workspace (entries table + detail panel + importer).
    // Inner (non-static) so it can reach the window's shared services and dialog helpers
    // the same way the old single-workspace code did.
    // =========================================================================================
    private final class EntryTab {

        final MtEntryPanel          entryPanel;
        final TagView                tagPanel;
        final DetailPanelController  detailCtrl;
        final FileImporter           importer;
        final JComponent             content;
        final String                 cardId;
        final List<EntrySelectionListener> selectionListeners = new ArrayList<>();

        JTable focusedTable;
        String statusText = "Ready. Please open a SWIFT MT file (Ctrl+O).";
        String title;

        EntryTab() {
            title  = UNTITLED + " " + (++untitledCounter);
            cardId = "tab" + (++tabIdSeq);

            entryPanel = new MtEntryPanel(createEntryPanelHost(), PREFS, prefKeys, dict);
            entryPanel.init();

            tagPanel = new TagView(createTagPanelHost(), PREFS, dict);
            selectionListeners.add(tagPanel);
            tagPanel.setOnComponentsToggled(active -> {
                if (Boolean.TRUE.equals(active)) switchToComponents();
                else                             deactivateComponents(true);
            });
            trackFocus(tagPanel.getTable());
            trackFocus(entryPanel.getTable());

            JPanel tranListPanel = entryPanel.buildContentPanel();
            JLabel entriesTitle  = new JLabel("MT Entries");
            entriesTitle.setFont(entriesTitle.getFont().deriveFont(Font.BOLD));
            JButton appendBtn = new JButton("+");
            appendBtn.setMargin(new Insets(1, 5, 1, 5));
            appendBtn.setToolTipText("Append file");
            appendBtn.addActionListener(e -> onAppendFile());
            JButton pasteBtn = new JButton(ToolbarIcons.clipboardIcon());
            pasteBtn.setMargin(new Insets(1, 4, 1, 4));
            pasteBtn.setToolTipText(PASTE_MT_SNIPPET);
            pasteBtn.addActionListener(e -> showAppendTextDialog());
            JPanel entriesWrapper = FrameLayout.wrapDetailCard(tranListPanel, entriesTitle, this::clearEntries, appendBtn, pasteBtn);

            detailCtrl = new DetailPanelController(tagPanel, dict, this::syncDetailMenuItems);
            JPanel detailCardPanel = detailCtrl.buildCardPanel();
            selectionListeners.add(detailCtrl.sourcePanel());
            selectionListeners.add(detailCtrl.diffPanel());

            JSplitPane innerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, entriesWrapper, detailCardPanel);
            innerSplit.setResizeWeight(0.65);
            detailCtrl.setSplit(innerSplit);
            JPanel detailBar = detailCtrl.buildDetailBar();

            JPanel center = new JPanel(new BorderLayout(0, 0));
            center.add(innerSplit, BorderLayout.CENTER);
            center.add(detailBar,  BorderLayout.EAST);
            content = center;

            importer = new FileImporter(createImportContext());

            SwingUtilities.invokeLater(detailCtrl::collapse);
        }

        // -------------------------------------------------------------------
        // Host implementations
        // -------------------------------------------------------------------

        private MtEntryPanel.Host createEntryPanelHost() {
            return new MtEntryPanel.Host() {
                @Override public void onRowSelected(int modelRow) {
                    detailCtrl.expandIfNeeded();
                    dispatchSingleEntry(modelRow);
                    refreshFileMenuState();
                }
                @Override public void onMultipleRowsSelected(List<Entry> entries) {
                    switchDetailCard(DetailPanelController.COMPARE);
                    detailCtrl.expandIfNeeded();
                    for (EntrySelectionListener l : selectionListeners) l.onMultipleEntries(entries);
                    refreshFileMenuState();
                }
                @Override public void onRowDeselected() {
                    collapseDetailPanel();
                    dispatchDeselect();
                    refreshFileMenuState();
                }
                @Override public void onFilesDropped(List<File> files) { appendDroppedFiles(files); }
                @Override public boolean isPowerUser() { return MtAnalyzeFrame.this.isPowerUser(); }
                @Override public void focusDetailTag(ColumnDef cd) { tagPanel.focusTag(cd); }
                @Override public void switchDetailCard(String card) { EntryTab.this.switchDetailCard(card); }
                @Override public void exportMessageForRow(int modelRow) {
                    mtExport.exportSingle(MtAnalyzeFrame.this,
                        entryPanel.getMessageForRow(modelRow).raw(),
                        config.getMtExportSender(),
                        config.getMtExportReceiver(),
                        entryPanel.getRowValue(modelRow, EntryPanelModel.FILE_COL_KEY),
                        statusLabel::setText);
                }
                @Override public void showAppendTextDialog() { EntryTab.this.showAppendTextDialog(); }
                @Override public void setStatus(String message) { setStatusText(message); }
                @Override public void copyVisibleMessagesToTab() {
                    MtAnalyzeFrame.this.copyVisibleMessagesToTab(EntryTab.this);
                }
            };
        }

        private TagView.Host createTagPanelHost() {
            return new TagView.Host() {
                @Override public boolean isPowerUser() { return MtAnalyzeFrame.this.isPowerUser(); }
                @Override public JMenuItem makeReferenceSearchItem(String value) { return EntryTab.this.makeReferenceSearchItem(value); }
                @Override public JMenuItem makeCopyCellItem(JTable t, int vr, int vc) { return EntryTab.this.makeCopyCellItem(t, vr, vc); }
                @Override public JMenuItem makeCopyTableItem(JTable t) { return EntryTab.this.makeCopyTableItem(t); }
                @Override public void showAddToDictionaryDialog(String q, String v) { HintDictionaryDialog.showAddEntry(MtAnalyzeFrame.this, q, v, dict); }
                @Override public void appendToEntryFilterByQualifier(String q, String v) { entryPanel.appendToEntryFilterByQualifier(q, v); }
            };
        }

        private ImportContext createImportContext() {
            return new ImportContext() {
                @Override public Frame         frame()                          { return MtAnalyzeFrame.this; }
                @Override public SystemConfig  config()                         { return MtAnalyzeFrame.this.config; }
                @Override public ImportService importService()                  { return MtAnalyzeFrame.this.importService; }
                @Override public String        promptMtType(String m)           { return EntryTab.this.promptMtType(m); }
                @Override public java.util.Optional<java.util.Set<String>> promptMtTypeFilter(String f) { return EntryTab.this.promptMtTypeFilter(f); }
                @Override public void          onNew()                          { clearEntries(); }
                @Override public void          onFileLoaded(ImportBatch b, File f)              { EntryTab.this.onFileLoaded(b, f); }
                @Override public void          onDirectoryLoaded(ImportBatch b, File d, int n)  { EntryTab.this.onDirectoryLoaded(b, d, n); }
                @Override public void          onContentAppended(ImportBatch b)                  { EntryTab.this.onContentAppended(b); }
                @Override public void          onFileAppended(File f)           { EntryTab.this.onFileAppended(f); }
                @Override public void          error(String m)                  { EntryTab.this.error(m); }
                @Override public void          fileError(String v, Exception e) { EntryTab.this.fileError(v, e); }
            };
        }

        // -------------------------------------------------------------------
        // Per-tab behaviour (mirrors what used to be single frame-level methods)
        // -------------------------------------------------------------------

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

        private JMenuItem makeReferenceSearchItem(String value) {
            String label = value.length() > 30 ? value.substring(0, 27) + "..." : value;
            JMenuItem item = new JMenuItem("Reference Search: " + label, ToolbarIcons.menuSearch());
            item.addActionListener(ae -> applyReferenceSearch(value));
            return item;
        }

        private void applyReferenceSearch(String value) {
            entryPanel.applyReferenceSearch(value);
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

        /**
         * Shows a combo-box dialog asking the user to choose a message type.
         * Returns the type number (e.g. "536") or null for Auto-detect / cancelled.
         */
        private String promptMtType(String message) {
            JComboBox<String> combo = new JComboBox<>(MtFileIO.getMtTypeItems());
            int result = JOptionPane.showConfirmDialog(MtAnalyzeFrame.this,
                    new Object[]{message, combo},
                    "Select Message Type", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (result != JOptionPane.OK_OPTION) return null;
            String selected = (String) combo.getSelectedItem();
            if (selected == null || selected.startsWith("Auto")) return null;
            return selected.replaceAll("\\D", "");
        }

        private java.util.Optional<java.util.Set<String>> promptMtTypeFilter(String logFileName) {
            String input = JOptionPane.showInputDialog(MtAnalyzeFrame.this,
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

        private void warnLimitReached() {
            int max = config.getMaxEntries();
            JOptionPane.showMessageDialog(MtAnalyzeFrame.this,
                    "Entry limit of " + max + " reached. Some entries were not loaded.\n"
                    + "Increase 'Max. entries' in Settings to load more.",
                    "Entry Limit Reached", JOptionPane.WARNING_MESSAGE);
        }

        void onSaveMtAs() {
            File saved = mtExport.save(MtAnalyzeFrame.this,
                entryPanel.getLoadedMessages().stream().map(SwiftMessage::raw).toList(),
                config.getMtExportSender(),
                config.getMtExportReceiver(),
                statusLabel::setText);
            if (saved != null) updateTitle(saved.getName());
        }

        void onSaveExcel() {
            excelExport.exportComponents(MtAnalyzeFrame.this,
                entryPanel.getFullDisplaySequences(),
                entryPanel.getRowData(),
                SEQ_KEY,
                EntryPanelModel.MT_COL_KEY,
                statusLabel::setText);
        }

        /**
         * Removes exact duplicate messages (compared with Prowide's {@code SwiftMessageComparator})
         * from this tab, keeping the first occurrence of each. The SEME reference of every removed
         * message is reported in the notification panel.
         */
        void onRemoveDuplicates() {
            if (entryPanel.getLoadedMessages().isEmpty()) {
                statusLabel.setText("Nothing to check – this tab has no MT entries.");
                return;
            }
            int choice = JOptionPane.showConfirmDialog(MtAnalyzeFrame.this,
                "Remove duplicate messages from this tab?\n"
                    + "Messages are compared with Prowide's SwiftMessageComparator; exact duplicates "
                    + "are deleted, keeping the first occurrence of each.",
                "Remove Duplicates", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) return;

            MtEntryPanel.DuplicateRemovalResult result = entryPanel.removeDuplicateMessages();
            if (result.removedCount() == 0) {
                statusLabel.setText("No duplicate messages found.");
                return;
            }
            refreshFileMenuState();

            String semeList = result.removedSeme().stream()
                .filter(s -> !s.isEmpty())
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
            String body = "Removed " + result.removedCount()
                + (result.removedCount() == 1 ? " duplicate message" : " duplicate messages")
                + (semeList.isEmpty() ? "" : ("<br>SEME: " + semeList));
            detailCtrl.notificationPanel().addNotification(NotificationPanel.Type.WARNING, "Duplicates removed", body);
            switchDetailCard(DetailPanelController.NOTIFICATIONS);
            detailCtrl.expandIfNeeded();
        }

        void onFileLoaded(ImportBatch batch, File file) {
            notifyProwideLog(batch);
            if (batch.totalParsed == 0) { error("No valid SWIFT messages found."); return; }
            tagPanel.clear();
            entryPanel.clearSearch();
            entryPanel.loadBatch(batch.messages, batch.columnDefs);
            entryPanel.applyColumnPrefs();
            entryPanel.rebuildPositionTable();
            selectFirstRow();
            int n = batch.totalParsed;
            setStatus("Loaded: " + file.getAbsolutePath());
            detailCtrl.notificationPanel().addNotification(NotificationPanel.Type.INFO, "File loaded",
                file.getName() + " (" + n + (n == 1 ? MSG_SINGULAR : MSG_PLURAL) + ")");
            PREFS.put(PREF_LAST_FILE, file.getAbsolutePath());
            updateTitle(file.getName());
            notifyBatchErrors(batch.errors);
            if (batch.limitReached) warnLimitReached();
            refreshFileMenuState();
        }

        void onDirectoryLoaded(ImportBatch batch, File dir, int fileCount) {
            entryPanel.clearSearch();
            entryPanel.mergeBatch(batch.messages, batch.columnDefs);
            entryPanel.applyColumnPrefs();
            entryPanel.rebuildPositionTable();
            selectFirstRow();
            int n = batch.totalParsed;
            String msg = "Imported " + n + (n == 1 ? MSG_SINGULAR : MSG_PLURAL)
                + " from " + fileCount + (fileCount == 1 ? " file" : " files")
                + " in " + dir.getName();
            setStatus(msg);
            detailCtrl.notificationPanel().addNotification(NotificationPanel.Type.INFO, "Import complete",
                msg + " (" + entryPanel.getLoadedMessages().size() + " messages total)");
            notifyBatchErrors(batch.errors);
            notifyProwideLog(batch);
            updateTitle(dir.getName());
            if (batch.limitReached) warnLimitReached();
            refreshFileMenuState();
        }

        void onContentAppended(ImportBatch batch) {
            entryPanel.mergeBatch(batch.messages, batch.columnDefs);
            entryPanel.applyColumnPrefs();
            entryPanel.rebuildPositionTable();
            setStatus("Appended: " + batch.totalParsed + (batch.totalParsed == 1 ? MSG_SINGULAR : MSG_PLURAL));
            notifyBatchErrors(batch.errors);
            notifyProwideLog(batch);
            if (batch.limitReached) warnLimitReached();
            refreshFileMenuState();
        }

        void onFileAppended(File file) {
            int total = entryPanel.getLoadedMessages().size();
            setStatus("Appended: " + file.getAbsolutePath());
            detailCtrl.notificationPanel().addNotification(NotificationPanel.Type.INFO, "File appended",
                file.getName() + " (" + total + " messages total)");
            refreshFileMenuState();
        }

        void fileError(String verb, Exception ex) {
            error("Error: " + ex.getMessage());
            JOptionPane.showMessageDialog(MtAnalyzeFrame.this, "Error " + verb + " file:\n" + ex.getMessage(),
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

        private void selectFirstRow() {
            if (entryPanel.getTable().getRowCount() > 0) {
                entryPanel.getTable().setRowSelectionInterval(0, 0);
                entryPanel.getTable().scrollRectToVisible(entryPanel.getTable().getCellRect(0, 0, true));
            }
        }

        void showAppendTextDialog() {
            AppendTextDialog.show(
                MtAnalyzeFrame.this,
                () -> promptMtType("Select the message type for this content."),
                    importer::appendFromContent,
                chunks -> importer.appendFromContent(chunks, null, null, MessageOrigin.NAME_VALUE)
            );
        }

        void switchDetailCard(String card) {
            deactivateComponents(DetailPanelController.INSPECTOR.equals(card));
            detailCtrl.showCard(card);
        }

        private void collapseDetailPanel() { detailCtrl.collapse(); }

        private void syncDetailMenuItems(boolean tagsActive, boolean compActive) {
            if (this != activeTab()) return;
            String card = detailCtrl.getActiveCard();
            if (menuNotifications != null) menuNotifications.setSelected(DetailPanelController.NOTIFICATIONS.equals(card));
            if (menuTags          != null) menuTags         .setSelected(tagsActive);
            if (menuCompare       != null) menuCompare      .setSelected(DetailPanelController.COMPARE.equals(card));
            if (menuSource        != null) menuSource       .setSelected(DetailPanelController.EDITOR.equals(card));
            if (menuComponents    != null) menuComponents   .setSelected(compActive);
        }

        private void deactivateComponents(boolean rebuildModel) {
            tagPanel.setComponentsButtonSelected(false);
            if (this == activeTab() && menuComponents != null) menuComponents.setSelected(false);
            detailCtrl.resetTitleToTags();
            if (!tagPanel.isComponentsMode()) return;
            if (rebuildModel) tagPanel.rebuildModel(false);
            detailCtrl.syncButtons();
        }

        void switchToComponents() {
            detailCtrl.showComponentsMode();
            if (this == activeTab()) {
                if (menuNotifications != null) menuNotifications.setSelected(false);
                if (menuTags          != null) menuTags         .setSelected(false);
                if (menuCompare       != null) menuCompare      .setSelected(false);
                if (menuSource        != null) menuSource       .setSelected(false);
                if (menuComponents    != null) menuComponents   .setSelected(true);
            }
            tagPanel.setComponentsButtonSelected(true);
            if (!tagPanel.isComponentsMode()) tagPanel.rebuildModel(true);
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

        /** Drops onto the entry table append to the current data instead of replacing it. */
        private void appendDroppedFiles(List<File> files) {
            for (File f : files) importer.appendFile(f);
        }

        void clearEntries() {
            detailCtrl.showCard(DetailPanelController.INSPECTOR);
            entryPanel.clearSearch();
            entryPanel.clear();
            entryPanel.rebuildPositionTable();
            tagPanel.clear();
            setStatus("New model created.");
            updateTitle(UNTITLED + " " + untitledCounter);
            refreshFileMenuState();
        }

        void error(String msg) {
            setStatusText(msg);
            detailCtrl.notificationPanel().addNotification(NotificationPanel.Type.ERROR, ERROR_TITLE, msg);
        }

        void setStatus(String msg) { setStatusText(msg); }

        private void setStatusText(String msg) {
            statusText = msg;
            if (this == activeTab() && statusLabel != null) statusLabel.setText(msg);
        }

        void updateTitle(String newTitle) {
            title = newTitle;
            int idx = openTabs.indexOf(this);
            if (idx >= 0 && tabs != null) tabs.setTitleAt(idx, newTitle);
        }
    }
}
