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
package com.mtanalyze.ui.view;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * A JPanel that paints itself with rounded corners and a subtle border,
 * similar to IntelliJ IDEA tool-window cards. All child components are
 * clipped to the rounded shape so nothing bleeds into the corners.
 */
public class RoundedPanel extends JPanel {

    public static final int ARC = 14;

    public RoundedPanel(LayoutManager layout) {
        super(layout);
        setOpaque(false);
    }

    @Override
    public void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();

        // 1 – fill background (rounded), then clip children to same shape
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, w, h, ARC, ARC);
            g2.clip(new RoundRectangle2D.Float(0, 0, w, h, ARC, ARC));
            super.paint(g2);
        } finally {
            g2.dispose();
        }

        // 2 – draw border outline on top, unclipped so the stroke is crisp
        Graphics2D gb = (Graphics2D) g.create();
        try {
            gb.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color c = UIManager.getColor("Component.borderColor");
            gb.setColor(c != null ? c : UIManager.getColor("Separator.foreground"));
            gb.drawRoundRect(0, 0, w - 1, h - 1, ARC, ARC);
        } finally {
            gb.dispose();
        }
    }
}