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

import com.mtanalyze.ui.view.DiffPanel;
import com.mtanalyze.ui.view.SourcePanel;
import com.mtanalyze.ui.view.TagView;

import javax.swing.*;
import java.awt.*;
import java.util.function.BiConsumer;

class DetailPanelController {

    static final String INSPECTOR     = "inspector";
    static final String COMPARE       = "compare";
    static final String EDITOR        = "editor";

    private static final int    MIN_WIDTH           = 380;
    private static final String LABEL_SOURCE        = "Source";
    private static final String LABEL_COMPONENTS    = "Components";

    private final TagView                    tagPanel;
    private final BiConsumer<Boolean,Boolean> onMenuSync;

    private boolean collapsed  = false;
    private String  activeCard = INSPECTOR;

    private CardLayout         cardLayout;
    private JPanel             cardPanel;
    private JLabel             titleLabel;
    private DiffPanel          diffPanel;
    private SourcePanel        sourcePanel;

    private ToolWindowButton tagBtn;
    private ToolWindowButton compareBtn;
    private ToolWindowButton editorBtn;

    private JSplitPane split;

    DetailPanelController(TagView tagPanel, BiConsumer<Boolean, Boolean> onMenuSync) {
        this.tagPanel    = tagPanel;
        this.onMenuSync  = onMenuSync;
    }

    // -----------------------------------------------------------------------
    // Build
    // -----------------------------------------------------------------------

    JPanel buildCardPanel() {
        cardLayout = new CardLayout();
        cardPanel  = new JPanel(cardLayout);
        cardPanel.setMinimumSize(new Dimension(MIN_WIDTH, 0));

        diffPanel   = new DiffPanel();

        sourcePanel = new SourcePanel();

        titleLabel = new JLabel("Tags");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));

        cardPanel.add(FrameLayout.wrapDetailCard(tagPanel,    titleLabel,   this::collapse), INSPECTOR);
        cardPanel.add(FrameLayout.wrapDetailCard(diffPanel,   "Diff",       this::collapse), COMPARE);
        cardPanel.add(FrameLayout.wrapDetailCard(sourcePanel, LABEL_SOURCE, this::collapse), EDITOR);
        return cardPanel;
    }

    JPanel buildDetailBar() {
        tagBtn     = new ToolWindowButton("Tags",              ToolbarIcons.tagsIcon());
        compareBtn = new ToolWindowButton("Diff",              ToolbarIcons.diffIcon());
        editorBtn  = new ToolWindowButton(LABEL_SOURCE,        ToolbarIcons.sourceIcon());
        tagBtn    .setSelected(false);
        compareBtn.setSelected(false);
        editorBtn .setSelected(false);

        tagBtn.addActionListener(e -> {
            if (tagBtn.isSelected()) showCard(INSPECTOR);
            else { compareBtn.setSelected(false); editorBtn.setSelected(false); collapse(); }
        });
        compareBtn.addActionListener(e -> {
            if (compareBtn.isSelected()) showCard(COMPARE);
            else { tagBtn.setSelected(false); editorBtn.setSelected(false); collapse(); }
        });
        editorBtn.addActionListener(e -> {
            if (editorBtn.isSelected()) showCard(EDITOR);
            else { tagBtn.setSelected(false); compareBtn.setSelected(false); collapse(); }
        });

        FrameToolbars.styleDetailButton(compareBtn);
        FrameToolbars.styleDetailButton(editorBtn);
        return FrameToolbars.buildDetailRight(FrameToolbars.separatorBorder(true),
                tagBtn, tagPanel.componentsToggle(), compareBtn, editorBtn);
    }

    void setSplit(JSplitPane split) { this.split = split; }

    // -----------------------------------------------------------------------
    // State transitions
    // -----------------------------------------------------------------------

    void showCard(String card) {
        if (INSPECTOR.equals(card) && tagPanel.isComponentsMode()) {
            tagPanel.rebuildModel(false);
        }
        activeCard = card;
        if (INSPECTOR.equals(card) && titleLabel != null) titleLabel.setText("Tags");
        updateTwButtons(card);
        if (cardLayout != null && cardPanel != null) cardLayout.show(cardPanel, card);
        syncTwButtons();
        if (collapsed) toggle();
    }

    void syncButtons() { syncTwButtons(); }

    void showComponentsMode() {
        activeCard = INSPECTOR;
        if (titleLabel != null) titleLabel.setText(LABEL_COMPONENTS);
        if (tagBtn     != null) tagBtn    .setSelected(false);
        if (compareBtn != null) compareBtn.setSelected(false);
        if (editorBtn  != null) editorBtn .setSelected(false);
        if (cardLayout != null && cardPanel != null) cardLayout.show(cardPanel, INSPECTOR);
    }

    void resetTitleToTags() {
        if (titleLabel != null) titleLabel.setText("Tags");
    }

    void toggle() {
        if (collapsed) {
            split.getRightComponent().setMinimumSize(new Dimension(MIN_WIDTH, 0));
            int target = (int) (split.getWidth() * 0.65);
            SwingUtilities.invokeLater(() -> split.setDividerLocation(target));
            collapsed = false;
        } else {
            split.getRightComponent().setMinimumSize(new Dimension(0, 0));
            SwingUtilities.invokeLater(() ->
                split.setDividerLocation(split.getWidth() - split.getDividerSize()));
            collapsed = true;
        }
        syncTwButtons();
    }

    void collapse()       { if (!collapsed) toggle(); }
    void expandIfNeeded() { if ( collapsed) toggle(); }

    String  getActiveCard() { return activeCard; }

    DiffPanel         diffPanel()         { return diffPanel; }
    SourcePanel       sourcePanel()       { return sourcePanel; }

    // -----------------------------------------------------------------------

    private void updateTwButtons(String card) {
        if (tagBtn     != null) tagBtn    .setSelected(INSPECTOR.equals(card));
        if (compareBtn != null) compareBtn.setSelected(COMPARE.equals(card));
        if (editorBtn  != null) editorBtn .setSelected(EDITOR.equals(card));
    }

    private void syncTwButtons() {
        if (tagBtn == null) return;
        boolean expanded    = !collapsed;
        boolean isInspector = INSPECTOR.equals(activeCard);
        boolean tagsActive  = isInspector && !tagPanel.isComponentsMode();
        boolean compActive  = isInspector && tagPanel.isComponentsMode();
        tagBtn.setSelected(expanded && tagsActive);
        if (compareBtn != null) compareBtn.setSelected(expanded && COMPARE.equals(activeCard));
        if (editorBtn  != null) editorBtn .setSelected(expanded && EDITOR.equals(activeCard));
        tagPanel.setComponentsButtonSelected(expanded && compActive);
        onMenuSync.accept(tagsActive, compActive);
    }
}