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

public final class ToolWindowButton extends JToggleButton {

    private static final int BTN_W = 28;
    private static final int BTN_H = 34;
    private static final Color BADGE_COLOR = new Color(0x3592C4);

    private boolean badge = false;

    public void setBadge(boolean show) {
        if (badge != show) {
            badge = show;
            repaint();
        }
    }

    public ToolWindowButton(String text, Icon icon) {
        super(text, icon);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setMaximumSize(new Dimension(BTN_W, BTN_H));
        setPreferredSize(new Dimension(BTN_W, BTN_H));
        setToolTipText(text);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            if (isSelected() || getModel().isRollover()) {
                Color bg = resolveButtonBackground(isSelected());
                if (bg != null) {
                    g2.setColor(bg);
                    g2.fillRoundRect(1, 1, w - 2, h - 2, 6, 6);
                }
            }

            Icon ic = getIcon();
            if (ic != null) {
                int ix = (w - ic.getIconWidth()) / 2;
                int iy = (h - ic.getIconHeight()) / 2;
                ic.paintIcon(this, g2, ix, iy);
            }

            if (badge) {
                g2.setColor(BADGE_COLOR);
                g2.fillOval(w - 9, 4, 6, 6);
            }
        } finally {
            g2.dispose();
        }
    }

    private static Color resolveButtonBackground(boolean selected) {
        Color bg;
        if (selected) {
            bg = UIManager.getColor("ToggleButton.selectedBackground");
            if (bg == null) bg = UIManager.getColor("List.selectionBackground");
        } else {
            bg = UIManager.getColor("Button.hoverBackground");
        }
        if (bg == null) bg = UIManager.getColor("Table.selectionBackground");
        return bg;
    }
}