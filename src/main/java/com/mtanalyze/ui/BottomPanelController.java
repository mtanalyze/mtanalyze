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

import com.mtanalyze.export.CsvExport;
import com.mtanalyze.ui.view.AccountMappingPanel;
import com.mtanalyze.ui.view.BookmarkPanel;
import com.mtanalyze.ui.view.CashPostingPanel;
import com.mtanalyze.ui.view.MessageSourcePanel;
import com.mtanalyze.ui.view.SecuritiesPostingPanel;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

class BottomPanelController {

    static final String BOOKMARKS       = "bookmarks_bottom";
    static final String SECURITIES      = "securities_bottom";
    static final String CASH            = "cash_bottom";
    static final String ACCOUNT_MAPPING = "acctmap_bottom";

    private static final String PREF_SPLIT   = "bookmark_split";
    private static final String TOOLTIP_AND  = "Quick Filter: AND – click to switch to OR";
    private static final String TOOLTIP_OR   = "Quick Filter: OR – click to switch to AND";

    private final BookmarkPanel           bookmarkPanel;
    private final AccountMappingPanel     accountMappingPanel;
    private final SecuritiesPostingPanel  securitiesPanel;
    private final CashPostingPanel        cashPanel;
    private final Map<String, EditMenuContributor> contributors;

    private final Preferences prefs;
    private final Runnable    onSync;

    private boolean    collapsed  = true;
    private String     activeCard = BOOKMARKS;

    private JPanel     wrapperPanel;
    private CardLayout cardLayout;
    private JPanel     cardPanel;
    private JLabel     titleLabel;
    private JButton    pasteBtn;
    private JButton    filterModeBtn;
    private JSplitPane split;

    BottomPanelController(BookmarkPanel bookmarkPanel,
                          Consumer<String> onApplySafeFilter,
                          CsvExport.Prefs csvPrefs,
                          Preferences prefs,
                          String accountMappingPrefKey,
                          Runnable onSync) {
        this.bookmarkPanel = bookmarkPanel;
        this.prefs         = prefs;
        this.onSync        = onSync;

        accountMappingPanel = new AccountMappingPanel(prefs, accountMappingPrefKey, csvPrefs);
        securitiesPanel = new SecuritiesPostingPanel(csvPrefs,
            cv -> { String safe = accountMappingPanel.lookupSafeBySecuritiesAccount(cv);
                    if (safe != null && !safe.isEmpty()) onApplySafeFilter.accept(safe); },
            cv -> { String safe = accountMappingPanel.lookupSafeBySecuritiesAccount(cv);
                    return safe != null && !safe.isEmpty(); });
        cashPanel = new CashPostingPanel(csvPrefs,
            cv -> { String safe = accountMappingPanel.lookupSafeByCashAccount(cv);
                    if (safe != null && !safe.isEmpty()) onApplySafeFilter.accept(safe); },
            cv -> { String safe = accountMappingPanel.lookupSafeByCashAccount(cv);
                    return safe != null && !safe.isEmpty(); });
        contributors = Map.of(
            BOOKMARKS,       bookmarkPanel,
            SECURITIES,      securitiesPanel,
            CASH,            cashPanel,
            ACCOUNT_MAPPING, accountMappingPanel
        );
    }

    JPanel buildPanel() {
        cardLayout = new CardLayout();
        cardPanel  = new JPanel(cardLayout);
        cardPanel.add(bookmarkPanel,       BOOKMARKS);
        cardPanel.add(securitiesPanel,     SECURITIES);
        cardPanel.add(cashPanel,           CASH);
        cardPanel.add(accountMappingPanel, ACCOUNT_MAPPING);

        titleLabel = new JLabel(MtAnalyzeFrame.BOOKMARKS);
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
        pasteBtn.addActionListener(e -> onPasteClick());

        filterModeBtn = new JButton(ToolbarIcons.filterAnd());
        filterModeBtn.setToolTipText(TOOLTIP_AND);
        filterModeBtn.setFocusPainted(false);
        filterModeBtn.setBorderPainted(false);
        filterModeBtn.setContentAreaFilled(false);
        filterModeBtn.setOpaque(false);
        filterModeBtn.setPreferredSize(new Dimension(20, 20));
        filterModeBtn.setVisible(false);
        filterModeBtn.addActionListener(e -> onFilterModeClick());

        JPanel closeBtnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 2));
        closeBtnPanel.setOpaque(false);
        closeBtnPanel.add(filterModeBtn);
        closeBtnPanel.add(pasteBtn);
        closeBtnPanel.add(closeBtn);

        JPanel titleBar = MessageSourcePanel.buildSectionHeader(titleLabel, closeBtnPanel);

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
        if (BOOKMARKS.equals(card)) bookmarkPanel.refresh();
        if (cardLayout != null && cardPanel != null) cardLayout.show(cardPanel, card);
        if (titleLabel != null) titleLabel.setText(getTitle(card));
        boolean hasFilter = SECURITIES.equals(card) || CASH.equals(card) || ACCOUNT_MAPPING.equals(card);
        if (pasteBtn != null) pasteBtn.setVisible(hasFilter);
        updateFilterModeBtn(card, hasFilter);
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

    AccountMappingPanel    accountMappingPanel()       { return accountMappingPanel; }
    SecuritiesPostingPanel securitiesPanel()           { return securitiesPanel; }
    CashPostingPanel       cashPanel()                 { return cashPanel; }
    JTable                 accountMappingTable()       { return accountMappingPanel.getTable(); }

    // -----------------------------------------------------------------------

    private static String getTitle(String card) {
        if (BOOKMARKS.equals(card))       return MtAnalyzeFrame.BOOKMARKS;
        if (CASH.equals(card))            return "Cash Posting";
        if (ACCOUNT_MAPPING.equals(card)) return "Account Mapping";
        return "Securities Posting";
    }

    private void updateFilterModeBtn(String card, boolean hasFilter) {
        if (filterModeBtn == null) return;
        filterModeBtn.setVisible(hasFilter);
        if (hasFilter) {
            boolean or = getOrMode(card);
            filterModeBtn.setIcon(or ? ToolbarIcons.filterOr() : ToolbarIcons.filterAnd());
            filterModeBtn.setToolTipText(or ? TOOLTIP_OR : TOOLTIP_AND);
        }
    }

    private boolean getOrMode(String card) {
        if (SECURITIES.equals(card)) return securitiesPanel.isFinFilterOrMode();
        if (CASH.equals(card))       return cashPanel.isFinFilterOrMode();
        return accountMappingPanel.isFinFilterOrMode();
    }

    private void onPasteClick() {
        if      (SECURITIES.equals(activeCard))      securitiesPanel.pasteFromClipboard();
        else if (CASH.equals(activeCard))            cashPanel.pasteFromClipboard();
        else if (ACCOUNT_MAPPING.equals(activeCard)) accountMappingPanel.pasteFromClipboard();
    }

    private void onFilterModeClick() {
        boolean or;
        switch (activeCard) {
            case SECURITIES -> {
                securitiesPanel.setFinFilterOrMode(!securitiesPanel.isFinFilterOrMode());
                or = securitiesPanel.isFinFilterOrMode();
            }
            case CASH -> {
                cashPanel.setFinFilterOrMode(!cashPanel.isFinFilterOrMode());
                or = cashPanel.isFinFilterOrMode();
            }
            case ACCOUNT_MAPPING -> {
                accountMappingPanel.setFinFilterOrMode(!accountMappingPanel.isFinFilterOrMode());
                or = accountMappingPanel.isFinFilterOrMode();
            }
            case null, default -> {
                return;
            }
        }
        filterModeBtn.setIcon(or ? ToolbarIcons.filterOr() : ToolbarIcons.filterAnd());
        filterModeBtn.setToolTipText(or ? TOOLTIP_OR : TOOLTIP_AND);
    }
}