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

import com.mtanalyze.export.CsvExport;
import com.mtanalyze.ui.view.AccountMappingPanel;
import com.mtanalyze.ui.view.NotificationPanel;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.prefs.Preferences;

/** Collapsible bottom panel hosting Notifications and the Account Mapping table. */
class BottomPanelController {

    static final String NOTIFICATIONS   = "notifications_bottom";
    static final String ACCOUNT_MAPPING = "acctmap_bottom";

    private static final String PREF_SPLIT   = "bottom_panel_split";
    private static final String TOOLTIP_AND  = "Quick Filter: AND – click to switch to OR";
    private static final String TOOLTIP_OR   = "Quick Filter: OR – click to switch to AND";

    private final NotificationPanel       notificationPanel;
    private final AccountMappingPanel     accountMappingPanel;
    private final Map<String, EditMenuContributor> contributors;

    private final Preferences prefs;
    private final Runnable    onSync;

    private boolean    collapsed  = true;
    private String     activeCard = ACCOUNT_MAPPING;

    private JPanel     wrapperPanel;
    private CardLayout cardLayout;
    private JPanel     cardPanel;
    private JLabel     titleLabel;
    private JButton    pasteBtn;
    private JButton    filterModeBtn;
    private JPanel     clearAllWrap;
    private JSplitPane split;

    BottomPanelController(CsvExport.Prefs csvPrefs,
                          Preferences prefs,
                          String accountMappingPrefKey,
                          Runnable onSync) {
        this.prefs = prefs;
        this.onSync = onSync;
        notificationPanel   = new NotificationPanel();
        accountMappingPanel = new AccountMappingPanel(prefs, accountMappingPrefKey, csvPrefs);
        contributors = Map.of(
            ACCOUNT_MAPPING, accountMappingPanel
        );
    }

    JPanel buildPanel() {
        cardLayout = new CardLayout();
        cardPanel  = new JPanel(cardLayout);
        cardPanel.add(notificationPanel,   NOTIFICATIONS);
        cardPanel.add(accountMappingPanel, ACCOUNT_MAPPING);

        titleLabel = new JLabel(getTitle(activeCard));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));

        JButton closeBtn = FrameLayout.makeCloseButton(this::collapse);

        pasteBtn = new JButton(ToolbarIcons.menuPaste());
        pasteBtn.setToolTipText("Paste Table");
        pasteBtn.setFocusPainted(false);
        pasteBtn.setBorderPainted(false);
        pasteBtn.setContentAreaFilled(false);
        pasteBtn.setOpaque(false);
        pasteBtn.setPreferredSize(new Dimension(20, 20));
        pasteBtn.setVisible(false);
        pasteBtn.addActionListener(e -> accountMappingPanel.pasteFromClipboard());

        filterModeBtn = new JButton(ToolbarIcons.filterAnd());
        filterModeBtn.setToolTipText(TOOLTIP_AND);
        filterModeBtn.setFocusPainted(false);
        filterModeBtn.setBorderPainted(false);
        filterModeBtn.setContentAreaFilled(false);
        filterModeBtn.setOpaque(false);
        filterModeBtn.setPreferredSize(new Dimension(20, 20));
        filterModeBtn.setVisible(false);
        filterModeBtn.addActionListener(e -> onFilterModeClick());

        clearAllWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        clearAllWrap.setOpaque(false);
        clearAllWrap.add(notificationPanel.getClearAllButton());
        clearAllWrap.setVisible(false);

        JPanel closeBtnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 2));
        closeBtnPanel.setOpaque(false);
        closeBtnPanel.add(clearAllWrap);
        closeBtnPanel.add(filterModeBtn);
        closeBtnPanel.add(pasteBtn);
        closeBtnPanel.add(closeBtn);

        JPanel titleBar = FrameLayout.buildSectionHeader(titleLabel, closeBtnPanel);

        wrapperPanel = new JPanel(new BorderLayout(0, 0));
        wrapperPanel.add(titleBar,  BorderLayout.NORTH);
        wrapperPanel.add(cardPanel, BorderLayout.CENTER);
        return wrapperPanel;
    }

    void setSplit(JSplitPane split) { this.split = split; }

    void toggle(String card) {
        if (collapsed || !card.equals(activeCard)) show(card);
        else collapse();
    }

    void show(String card) {
        activeCard = card;
        if (cardLayout != null && cardPanel != null) cardLayout.show(cardPanel, card);
        if (titleLabel != null) titleLabel.setText(getTitle(card));
        boolean isAcctMap = ACCOUNT_MAPPING.equals(card);
        if (pasteBtn != null) pasteBtn.setVisible(isAcctMap);
        updateFilterModeBtn(isAcctMap);
        if (clearAllWrap != null) clearAllWrap.setVisible(NOTIFICATIONS.equals(card));
        if (wrapperPanel != null) wrapperPanel.setMinimumSize(new Dimension(0, 150));
        int saved = prefs.getInt(PREF_SPLIT, 200);
        SwingUtilities.invokeLater(() -> {
            int total = split.getHeight();
            split.setDividerLocation(Math.max(total - saved, 50));
        });
        collapsed = false;
        onSync.run();
    }

    void collapse() {
        if (split != null) {
            int loc        = split.getDividerLocation();
            int total      = split.getHeight();
            int panelHeight = total - loc - split.getDividerSize();
            if (panelHeight > 0) prefs.putInt(PREF_SPLIT, panelHeight);
            if (wrapperPanel != null) wrapperPanel.setMinimumSize(new Dimension(0, 0));
            SwingUtilities.invokeLater(() ->
                split.setDividerLocation(split.getHeight() - split.getDividerSize()));
        }
        collapsed = true;
        onSync.run();
    }

    boolean isCollapsed()                              { return collapsed; }
    String  getActiveCard()                            { return activeCard; }
    EditMenuContributor getContributor(String card)    { return contributors.get(card); }

    NotificationPanel      notificationPanel()         { return notificationPanel; }
    AccountMappingPanel    accountMappingPanel()       { return accountMappingPanel; }
    JTable                 accountMappingTable()       { return accountMappingPanel.getTable(); }

    // -----------------------------------------------------------------------

    private static String getTitle(String card) {
        if (NOTIFICATIONS.equals(card)) return "Notifications";
        return "Account Mapping";
    }

    private void updateFilterModeBtn(boolean isAcctMap) {
        if (filterModeBtn == null) return;
        filterModeBtn.setVisible(isAcctMap);
        if (isAcctMap) {
            boolean or = accountMappingPanel.isFinFilterOrMode();
            filterModeBtn.setIcon(or ? ToolbarIcons.filterOr() : ToolbarIcons.filterAnd());
            filterModeBtn.setToolTipText(or ? TOOLTIP_OR : TOOLTIP_AND);
        }
    }

    private void onFilterModeClick() {
        accountMappingPanel.setFinFilterOrMode(!accountMappingPanel.isFinFilterOrMode());
        updateFilterModeBtn(true);
    }
}