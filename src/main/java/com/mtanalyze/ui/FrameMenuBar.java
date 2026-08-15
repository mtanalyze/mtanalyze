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
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Builds the application menu bar and returns the items the frame must reference at runtime.
 * All frame-specific logic stays in the {@link Callbacks} the caller provides;
 * this class only handles layout, wiring, and icon assignment.
 */
public final class FrameMenuBar {

    private FrameMenuBar() {}

    // -----------------------------------------------------------------------
    // Public output record – items the frame stores for runtime access
    // -----------------------------------------------------------------------

    public record Items(
        JMenuBar              menuBar,
        JMenuItem             openSessionItem,
        JMenuItem             saveItem,
        JMenuItem             saveAsMtItem,
        JMenuItem             saveExcelItem,
        JMenuItem             reloadItem,
        JMenuItem             exportComponentsItem,
        JMenuItem             validateFileItem,
        JMenuItem             attachBlock5Item,
        JMenu                 importMenu,
        JMenu                 exportMenu,
        JSeparator            importExportLeadingSeparator,
        JSeparator            importExportMiddleSeparator,
        JSeparator            viewMenuSeparator,
        JMenuItem             importSecuritiesItem,
        JMenuItem             importCashItem,
        JMenuItem             importMappingItem,
        JSeparator            importPostingsSeparator,
        JMenuItem             exportSecuritiesItem,
        JMenuItem             exportCashItem,
        JMenuItem             exportMappingItem,
        JSeparator            exportPostingsSeparator,
        JButton               searchButton,
        JCheckBoxMenuItem     menuExplorer,
        JCheckBoxMenuItem     menuBookmarks,
        JCheckBoxMenuItem     menuSecurities,
        JCheckBoxMenuItem     menuCash,
        JCheckBoxMenuItem     menuAccountMapping,
        JRadioButtonMenuItem  menuNotifications,
        JRadioButtonMenuItem  menuTags,
        JRadioButtonMenuItem  menuCompare,
        JRadioButtonMenuItem  menuSource,
        JRadioButtonMenuItem  menuComponents
    ) {}

    // -----------------------------------------------------------------------
    // Callbacks – everything the frame supplies
    // -----------------------------------------------------------------------

    public record Callbacks(
        // File menu
        Runnable          onNew,
        Runnable          onOpenSession,
        Runnable          onSaveSession,
        Runnable          onSaveAsMt,
        Runnable          onSaveExcel,
        Runnable          onOpenFile,
        Runnable          onAppendFile,
        Runnable          onImportDirectory,
        Runnable          onReloadFile,
        Runnable          onValidateFile,
        Runnable          onAttachBlock5,
        Runnable          onExportCsv,
        Runnable          onExportCsvComponents,
        Runnable          onExportMt,
        Runnable          onExportMtVisible,
        Runnable          onImportSecurities,
        Runnable          onImportCash,
        Runnable          onImportMapping,
        Runnable          onExportSecurities,
        Runnable          onExportCash,
        Runnable          onExportMapping,
        Runnable          onShowSettings,
        Runnable          onShowSearchPopup,
        Runnable          onHelp,
        Runnable          onAbout,
        // Edit menu (rebuilt on open)
        Consumer<JMenu>   populateEditMenu,
        // View menu – left sidebar / bottom
        Runnable          onToggleExplorer,
        Runnable          onToggleBookmarks,
        Runnable          onToggleSecurities,
        Runnable          onToggleCash,
        Runnable          onToggleAccountMapping,
        // View menu – right detail panel
        Runnable          onShowNotifications,
        Runnable          onShowTags,
        Runnable          onShowDiff,
        Runnable          onShowSource,
        Runnable          onShowComponents
    ) {}

    // -----------------------------------------------------------------------
    // Factory
    // -----------------------------------------------------------------------

    public static Items build(MtEntryPanel entryPanel, Callbacks cb) {

        // ── File menu ─────────────────────────────────────────────────────
        JMenu fileMenu = new JMenu("File");

        JMenuItem newItem = item("New", ToolbarIcons.menuNew(), "ctrl N", cb.onNew());

        JMenuItem openMtItem = item("Open...", ToolbarIcons.menuImportFile(), "ctrl O", cb.onOpenFile());

        JMenuItem openSessionItem = item("Open Session...", ToolbarIcons.menuOpen(), "ctrl shift O", cb.onOpenSession());

        JMenuItem saveItem = new JMenuItem("Save Session", ToolbarIcons.menuSaveSession());
        saveItem.setAccelerator(KeyStroke.getKeyStroke("ctrl shift S"));
        saveItem.setEnabled(false);
        saveItem.addActionListener(e -> cb.onSaveSession().run());

        JMenuItem saveAsMtItem = item("Save...", ToolbarIcons.menuExport(), "ctrl S", cb.onSaveAsMt());
        saveAsMtItem.setEnabled(false);

        JMenuItem saveExcelItem = item("Save Excel...", ToolbarIcons.menuExport(), "ctrl E", cb.onSaveExcel());
        saveExcelItem.setEnabled(false);

        JMenuItem reloadItem = new JMenuItem("Reload", ToolbarIcons.menuReload());
        reloadItem.setAccelerator(KeyStroke.getKeyStroke("ctrl R"));
        reloadItem.setEnabled(false);
        reloadItem.addActionListener(e -> cb.onReloadFile().run());

        JMenuItem exportComponentsItem = item("Export CSV (Components)...", ToolbarIcons.menuExport(), null, cb.onExportCsvComponents());

        JMenuItem importSecuritiesItem = item("Import Securities Postings CSV…", ToolbarIcons.menuOpen(),       null, cb.onImportSecurities());
        JMenuItem importCashItem       = item("Import Cash Postings CSV…",       ToolbarIcons.menuOpen(),       null, cb.onImportCash());
        JMenuItem importMappingItem    = item("Import Account Mapping CSV…",     ToolbarIcons.menuImportFile(), null, cb.onImportMapping());
        JPopupMenu.Separator importPostingsSeparator = new JPopupMenu.Separator();

        JMenu importMenu = new JMenu("Import");
        importMenu.setIcon(ToolbarIcons.menuImportFile());
        importMenu.add(item("Import MT File...",           ToolbarIcons.menuImportFile(), null, cb.onOpenFile()));
        importMenu.add(item("Append MT File...",           ToolbarIcons.menuAppendFile(), null, cb.onAppendFile()));
        importMenu.add(item("Import Directory...",         ToolbarIcons.menuImportDir(),  null, cb.onImportDirectory()));
        importMenu.add(reloadItem);
        importMenu.add(importPostingsSeparator);
        importMenu.add(importSecuritiesItem);
        importMenu.add(importCashItem);
        importMenu.add(importMappingItem);

        JMenuItem exportSecuritiesItem = item("Export Securities Postings CSV…", ToolbarIcons.menuExport(), null, cb.onExportSecurities());
        JMenuItem exportCashItem       = item("Export Cash Postings CSV…",       ToolbarIcons.menuExport(), null, cb.onExportCash());
        JMenuItem exportMappingItem    = item("Export Account Mapping CSV…",     ToolbarIcons.menuExport(), null, cb.onExportMapping());
        JPopupMenu.Separator exportPostingsSeparator = new JPopupMenu.Separator();

        JMenu exportMenu = new JMenu("Export");
        exportMenu.setIcon(ToolbarIcons.menuExport());
        exportMenu.add(item("Export CSV...",               ToolbarIcons.menuExport(), null, cb.onExportCsv()));
        exportMenu.add(exportComponentsItem);
        exportMenu.add(item("Export MT Messages (All)...",       ToolbarIcons.menuExport(), null, cb.onExportMt()));
        exportMenu.add(item("Export MT Messages (Visible)...",   ToolbarIcons.menuExport(), null, cb.onExportMtVisible()));
        exportMenu.add(exportPostingsSeparator);
        exportMenu.add(exportSecuritiesItem);
        exportMenu.add(exportCashItem);
        exportMenu.add(exportMappingItem);

        fileMenu.add(newItem);
        fileMenu.addSeparator();
        fileMenu.add(openMtItem);
        fileMenu.add(openSessionItem);
        fileMenu.add(saveItem);
        fileMenu.add(saveAsMtItem);
        fileMenu.add(saveExcelItem);
        JPopupMenu.Separator importExportLeadingSeparator = new JPopupMenu.Separator();
        JPopupMenu.Separator importExportMiddleSeparator  = new JPopupMenu.Separator();
        fileMenu.add(importExportLeadingSeparator);
        fileMenu.add(importMenu);
        fileMenu.add(importExportMiddleSeparator);
        fileMenu.add(exportMenu);
        fileMenu.addSeparator();
        JMenuItem validateFileItem = item("Validate SWIFT File...", ToolbarIcons.menuSearch(), null, cb.onValidateFile());
        fileMenu.add(validateFileItem);
        JMenuItem attachBlock5Item = item("Attach Block 5...", ToolbarIcons.menuAppendFile(), null, cb.onAttachBlock5());
        fileMenu.add(attachBlock5Item);
        fileMenu.addSeparator();
        fileMenu.add(item("Settings...", ToolbarIcons.menuSettings(), null, cb.onShowSettings()));
        fileMenu.addSeparator();
        fileMenu.add(item("Exit",        ToolbarIcons.menuExit(),     "ctrl Q", () -> System.exit(0)));

        // ── View menu ─────────────────────────────────────────────────────
        JCheckBoxMenuItem menuExplorer = new JCheckBoxMenuItem("Explorer", true);
        menuExplorer.setIcon(ToolbarIcons.folderIcon());
        menuExplorer.setAccelerator(KeyStroke.getKeyStroke("ctrl 1"));
        menuExplorer.addActionListener(e -> cb.onToggleExplorer().run());

        JCheckBoxMenuItem menuBookmarks = new JCheckBoxMenuItem("Bookmarks", false);
        menuBookmarks.setIcon(ToolbarIcons.bookmarkRibbon());
        menuBookmarks.setAccelerator(KeyStroke.getKeyStroke("ctrl 2"));
        menuBookmarks.addActionListener(e -> cb.onToggleBookmarks().run());

        JCheckBoxMenuItem menuSecurities = new JCheckBoxMenuItem("Securities Posting", false);
        menuSecurities.setIcon(ToolbarIcons.securitiesIcon());
        menuSecurities.addActionListener(e -> cb.onToggleSecurities().run());
        menuSecurities.setVisible(false);

        JCheckBoxMenuItem menuCash = new JCheckBoxMenuItem("Cash Posting", false);
        menuCash.setIcon(ToolbarIcons.cashIcon());
        menuCash.addActionListener(e -> cb.onToggleCash().run());
        menuCash.setVisible(false);

        JCheckBoxMenuItem menuAccountMapping = new JCheckBoxMenuItem("Account Mapping", false);
        menuAccountMapping.setIcon(ToolbarIcons.accountMappingIcon());
        menuAccountMapping.addActionListener(e -> cb.onToggleAccountMapping().run());
        menuAccountMapping.setVisible(false);

        JRadioButtonMenuItem menuNotifications = new JRadioButtonMenuItem("Notifications");
        menuNotifications.setIcon(ToolbarIcons.notificationIcon());
        menuNotifications.setAccelerator(KeyStroke.getKeyStroke("ctrl 3"));
        menuNotifications.addActionListener(e -> cb.onShowNotifications().run());

        JRadioButtonMenuItem menuTags = new JRadioButtonMenuItem("Tags");
        menuTags.setIcon(ToolbarIcons.tagsIcon());
        menuTags.setAccelerator(KeyStroke.getKeyStroke("ctrl 4"));
        menuTags.addActionListener(e -> cb.onShowTags().run());

        JRadioButtonMenuItem menuCompare = new JRadioButtonMenuItem("Diff");
        menuCompare.setIcon(ToolbarIcons.diffIcon());
        menuCompare.setAccelerator(KeyStroke.getKeyStroke("ctrl 5"));
        menuCompare.addActionListener(e -> cb.onShowDiff().run());

        JRadioButtonMenuItem menuSource = new JRadioButtonMenuItem("Source");
        menuSource.setIcon(ToolbarIcons.sourceIcon());
        menuSource.setAccelerator(KeyStroke.getKeyStroke("ctrl 6"));
        menuSource.addActionListener(e -> cb.onShowSource().run());

        JRadioButtonMenuItem menuComponents = new JRadioButtonMenuItem("Components");
        menuComponents.setIcon(ToolbarIcons.splitValues());
        menuComponents.setAccelerator(KeyStroke.getKeyStroke("ctrl 7"));
        menuComponents.addActionListener(e -> cb.onShowComponents().run());

        ButtonGroup detailViewGroup = new ButtonGroup();
        detailViewGroup.add(menuNotifications);
        detailViewGroup.add(menuTags);
        detailViewGroup.add(menuCompare);
        detailViewGroup.add(menuSource);
        detailViewGroup.add(menuComponents);
        menuTags.setSelected(true);

        JPopupMenu.Separator viewMenuSeparator = new JPopupMenu.Separator();
        JMenu viewMenu = new JMenu("View");
        viewMenu.add(menuExplorer);
        viewMenu.add(menuBookmarks);
        viewMenu.add(menuSecurities);
        viewMenu.add(menuCash);
        viewMenu.add(menuAccountMapping);
        viewMenu.add(viewMenuSeparator);
        viewMenu.add(menuNotifications);
        viewMenu.add(menuTags);
        viewMenu.add(menuCompare);
        viewMenu.add(menuSource);
        viewMenu.add(menuComponents);

        // ── Help menu ─────────────────────────────────────────────────────
        JMenuItem helpItem = new JMenuItem("Help...", ToolbarIcons.menuHelp());
        helpItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F1, 0));
        helpItem.addActionListener(e -> cb.onHelp().run());

        JMenu helpMenu = new JMenu("Help");
        helpMenu.add(helpItem);
        helpMenu.addSeparator();
        helpMenu.add(item("About MT Analyze App...", ToolbarIcons.menuAbout(), null, cb.onAbout()));

        // ── Assemble bar ──────────────────────────────────────────────────
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(fileMenu);
        menuBar.add(buildEditMenu(cb.populateEditMenu()));
        menuBar.add(viewMenu);
        menuBar.add(helpMenu);

        menuBar.add(Box.createHorizontalGlue());
        menuBar.add(entryPanel.colLayoutIconLabel);
        menuBar.add(entryPanel.colLayoutGap1);
        menuBar.add(entryPanel.columnLayoutCombo);
        menuBar.add(entryPanel.colLayoutGapAfter);
        menuBar.add(entryPanel.quickFilterIconLabel);
        menuBar.add(entryPanel.quickFilterGap1);
        menuBar.add(entryPanel.quickFilterCombo);
        menuBar.add(entryPanel.filterModeBtn);
        menuBar.add(entryPanel.quickFilterGapAfter);
        menuBar.add(entryPanel.seqModeBtn);
        menuBar.add(Box.createRigidArea(new Dimension(4, 0)));

        JButton searchBtn = FrameLayout.makeNavButton(ToolbarIcons.search(), "Search (Ctrl+F)");
        searchBtn.addActionListener(e -> cb.onShowSearchPopup().run());
        JButton settingsBtn = FrameLayout.makeNavButton(ToolbarIcons.settings(), "Settings");
        settingsBtn.addActionListener(e -> cb.onShowSettings().run());
        menuBar.add(searchBtn);
        menuBar.add(settingsBtn);

        return new Items(menuBar, openSessionItem, saveItem, saveAsMtItem, saveExcelItem, reloadItem, exportComponentsItem, validateFileItem, attachBlock5Item,
            importMenu, exportMenu, importExportLeadingSeparator, importExportMiddleSeparator,
            viewMenuSeparator,
            importSecuritiesItem, importCashItem, importMappingItem, importPostingsSeparator,
            exportSecuritiesItem, exportCashItem, exportMappingItem, exportPostingsSeparator,
            searchBtn,
            menuExplorer, menuBookmarks, menuSecurities, menuCash, menuAccountMapping,
            menuNotifications, menuTags, menuCompare, menuSource, menuComponents);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static JMenu buildEditMenu(Consumer<JMenu> populate) {
        JMenu menu = new JMenu("Edit");
        menu.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent e) { menu.removeAll(); populate.accept(menu); }
            @Override public void menuDeselected(MenuEvent e) { /* nothing to do on deselect */ }
            @Override public void menuCanceled(MenuEvent e)   { /* nothing to do on cancel */ }
        });
        return menu;
    }

    private static JMenuItem item(String text, Icon icon, String accel, Runnable action) {
        JMenuItem i = new JMenuItem(text, icon);
        if (accel != null) i.setAccelerator(KeyStroke.getKeyStroke(accel));
        i.addActionListener(e -> action.run());
        return i;
    }
}