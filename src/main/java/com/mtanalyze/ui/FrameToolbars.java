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
import javax.swing.border.Border;
import java.awt.*;

/** Static factory helpers for building the left and right tool-window side bars. */
public final class FrameToolbars {

    private FrameToolbars() {}

    /** Dynamic 1-px separator border that reads its colour from UIManager at paint time. */
    public static Border separatorBorder(boolean left) {
        return new javax.swing.border.AbstractBorder() {
            @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
                Color col = UIManager.getColor("Separator.foreground");
                if (col == null) return;
                g.setColor(col);
                if (left) g.drawLine(x, y, x, y + h - 1);
                else      g.drawLine(x + w - 1, y, x + w - 1, y + h - 1);
            }
            @Override public Insets getBorderInsets(Component c) {
                return left ? new Insets(0, 1, 0, 0) : new Insets(0, 0, 0, 1);
            }
            @Override public Insets getBorderInsets(Component c, Insets i) {
                if (left) i.set(0, 1, 0, 0); else i.set(0, 0, 0, 1);
                return i;
            }
        };
    }

    /** Removes the border / fill decoration from a toolbar toggle/push button. */
    public static void styleDetailButton(AbstractButton btn) {
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setMaximumSize(new Dimension(28, 28));
        btn.setPreferredSize(new Dimension(28, 28));
    }

    /**
     * Right detail sidebar: all {@code buttons} group at the top; vertical glue fills the rest.
     */
    public static JPanel buildDetailRight(Border border, AbstractButton... buttons) {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setBorder(border);
        for (AbstractButton b : buttons) bar.add(b);
        bar.add(Box.createVerticalGlue());
        return bar;
    }
}