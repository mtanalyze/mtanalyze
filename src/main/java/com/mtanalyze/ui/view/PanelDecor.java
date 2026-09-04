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
package com.mtanalyze.ui.view;

import javax.swing.*;
import java.awt.*;

/** Shared painting helpers for the tool-window panels: section headers and empty-state hints. */
public final class PanelDecor {

    private PanelDecor() { }


    public static JPanel buildSectionHeader(JComponent title, JPanel btns) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new javax.swing.border.AbstractBorder() {
            @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
                Color col = UIManager.getColor("Separator.foreground");
                if (col != null) { g.setColor(col); g.drawLine(x, y + h - 1, x + w - 1, y + h - 1); }
            }
            @Override public Insets getBorderInsets(Component c)              { return new Insets(3, 6, 3, 4); }
            @Override public Insets getBorderInsets(Component c, Insets ins)  { ins.set(3, 6, 3, 4); return ins; }
        });
        header.add(title, BorderLayout.WEST);
        if (btns != null) header.add(btns, BorderLayout.EAST);
        return header;
    }

    public static void paintHintLines(Graphics g, Component c, String... lines) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color fg = UIManager.getColor("Label.disabledForeground");
            g2.setColor(fg != null ? fg : Color.GRAY);
            FontMetrics fm = g2.getFontMetrics();
            int lineH = fm.getHeight();
            int blockH = lines.length * lineH + (lines.length - 1) * 4;
            int y = hintTopY(c, blockH) + fm.getAscent();
            for (String line : lines) {
                g2.drawString(line, (c.getWidth() - fm.stringWidth(line)) / 2, y);
                y += lineH + 4;
            }
        } finally {
            g2.dispose();
        }
    }

    private static int hintTopY(Component c, int blockH) {
        Component root = SwingUtilities.getRoot(c);
        if (root != null && c.isShowing()) {
            Point p = SwingUtilities.convertPoint(c, 0, 0, root);
            int localY = root.getHeight() / 3 - p.y - blockH / 2;
            return Math.max(4, Math.min(localY, c.getHeight() - blockH - 4));
        }
        return (c.getHeight() - blockH) / 2;
    }
}
