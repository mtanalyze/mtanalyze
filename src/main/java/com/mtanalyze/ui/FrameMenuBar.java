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
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
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
        JMenuItem             saveAsMtItem,
        JMenuItem             saveExcelItem,
        JMenuItem             exportComponentsItem,
        JMenuItem             validateFileItem,
        JMenuItem             attachBlock5Item,
        JMenu                 importMenu,
        JMenu                 exportMenu,
        JSeparator            importExportLeadingSeparator,
        JSeparator            importExportMiddleSeparator,
        JButton               searchButton,
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
        Runnable          onNewTab,
        Runnable          onSaveAsMt,
        Runnable          onSaveExcel,
        Runnable          onOpenFile,
        Runnable          onAppendFile,
        Runnable          onImportDirectory,
        Runnable          onValidateFile,
        Runnable          onAttachBlock5,
        Runnable          onExportCsv,
        Runnable          onExportCsvComponents,
        Runnable          onExportMt,
        Runnable          onExportMtVisible,
        Runnable          onShowSettings,
        Runnable          onShowSearchPopup,
        Runnable          onHelp,
        Runnable          onAbout,
        // Edit menu (rebuilt on open)
        Consumer<JMenu>   populateEditMenu,
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

    public static Items build(Callbacks cb) {

        // ── File menu ─────────────────────────────────────────────────────
        JMenu fileMenu = new JMenu("File");

        JMenuItem newTabItem = item("New Tab", ToolbarIcons.menuNewTab(), "ctrl N", cb.onNewTab());

        JMenuItem openMtItem = item("Open...", ToolbarIcons.menuImportFile(), "ctrl O", cb.onOpenFile());

        JMenuItem saveAsMtItem = item("Save...", ToolbarIcons.menuExport(), "ctrl S", cb.onSaveAsMt());
        saveAsMtItem.setEnabled(false);

        JMenuItem saveExcelItem = item("Save Excel...", ToolbarIcons.menuExport(), "ctrl E", cb.onSaveExcel());
        saveExcelItem.setEnabled(false);

        JMenuItem exportComponentsItem = item("Export CSV (Components)...", ToolbarIcons.menuExport(), null, cb.onExportCsvComponents());

        JMenu importMenu = new JMenu("Import");
        importMenu.setIcon(ToolbarIcons.menuImportFile());
        importMenu.add(item("Import MT File...",           ToolbarIcons.menuImportFile(), null, cb.onOpenFile()));
        importMenu.add(item("Append MT File...",           ToolbarIcons.menuAppendFile(), null, cb.onAppendFile()));
        importMenu.add(item("Import Directory...",         ToolbarIcons.menuImportDir(),  null, cb.onImportDirectory()));

        JMenu exportMenu = new JMenu("Export");
        exportMenu.setIcon(ToolbarIcons.menuExport());
        exportMenu.add(item("Export CSV...",               ToolbarIcons.menuExport(), null, cb.onExportCsv()));
        exportMenu.add(exportComponentsItem);
        exportMenu.add(item("Export MT Messages (All)...",       ToolbarIcons.menuExport(), null, cb.onExportMt()));
        exportMenu.add(item("Export MT Messages (Visible)...",   ToolbarIcons.menuExport(), null, cb.onExportMtVisible()));

        fileMenu.add(newTabItem);
        fileMenu.addSeparator();
        fileMenu.add(openMtItem);
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

        JMenu viewMenu = new JMenu("View");
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

        JButton searchBtn = FrameLayout.makeNavButton(ToolbarIcons.search(), "Search (Ctrl+F)");
        searchBtn.addActionListener(e -> cb.onShowSearchPopup().run());
        JButton settingsBtn = FrameLayout.makeNavButton(ToolbarIcons.settings(), "Settings");
        settingsBtn.addActionListener(e -> cb.onShowSettings().run());
        menuBar.add(searchBtn);
        menuBar.add(settingsBtn);

        return new Items(menuBar, saveAsMtItem, saveExcelItem, exportComponentsItem, validateFileItem, attachBlock5Item,
            importMenu, exportMenu, importExportLeadingSeparator, importExportMiddleSeparator,
            searchBtn,
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