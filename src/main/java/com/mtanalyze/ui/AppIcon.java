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

import java.awt.*;
import java.awt.image.BufferedImage;

public final class AppIcon {

    private AppIcon() {}

    public static BufferedImage createAppIcon(int size) {
        BufferedImage img = new BufferedImage(
            size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

        // Background: dark blue gradient
        int arc = Math.max(4, size / 6);
        GradientPaint bg = new GradientPaint(
            0, 0,    new Color(0x1B3F72),
            0, size, new Color(0x0A1E3F));
        g.setPaint(bg);
        g.fillRoundRect(0, 0, size - 1, size - 1, arc, arc);

        // Inner highlight (subtle top sheen)
        GradientPaint shine = new GradientPaint(
            0, 0,        new Color(255, 255, 255, 22),
            0, (float)size / 3, new Color(255, 255, 255, 0));
        g.setPaint(shine);
        g.fillRoundRect(0, 0, size - 1, size / 3, arc, arc);

        // --- Document ---
        int dLeft  = (int)(size * 0.13f);
        int dTop   = (int)(size * 0.07f);
        int dW     = (int)(size * 0.58f);
        int dH     = (int)(size * 0.76f);
        int fold   = (int)(size * 0.16f);  // folded corner size

        // Document body (white, slightly transparent)
        g.setColor(new Color(220, 232, 248));
        int[] docX = { dLeft, dLeft + dW - fold, dLeft + dW, dLeft + dW, dLeft };
        int[] docY = { dTop,  dTop,               dTop + fold, dTop + dH, dTop + dH };
        g.fillPolygon(docX, docY, 5);

        // Folded corner triangle (darker shade)
        g.setColor(new Color(160, 185, 215));
        int[] foldX = { dLeft + dW - fold, dLeft + dW - fold, dLeft + dW };
        int[] foldY = { dTop,               dTop + fold,        dTop + fold };
        g.fillPolygon(foldX, foldY, 3);

        // Document outline
        g.setColor(new Color(0x4A72AA));
        g.setStroke(new BasicStroke(Math.max(0.8f, size / 40f)));
        g.drawPolygon(docX, docY, 5);
        g.drawLine(dLeft + dW - fold, dTop, dLeft + dW - fold, dTop + fold);
        g.drawLine(dLeft + dW - fold, dTop + fold, dLeft + dW, dTop + fold);

        // Text lines inside document
        g.setColor(new Color(0x3A5A8A));
        float lineStroke = Math.max(0.8f, size / 36f);
        g.setStroke(new BasicStroke(lineStroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int lx1 = dLeft + (int)(size * 0.07f);
        int lx2 = dLeft + dW  - (int)(size * 0.06f);
        int lx2short = dLeft + dW - (int)(size * 0.22f);
        int lineGap = (int)(size * 0.10f);
        int lineStart = dTop + (int)(size * 0.22f);
        g.drawLine(lx1, lineStart,            lx2,      lineStart);
        g.drawLine(lx1, lineStart + lineGap,  lx2,      lineStart + lineGap);
        g.drawLine(lx1, lineStart + lineGap*2, lx2short, lineStart + lineGap*2);

        // --- Magnifying glass (gold, bottom-right, overlapping doc) ---
        float glassStroke = Math.max(1.5f, size / 12f);
        int gcx = (int)(size * 0.68f);
        int gcy = (int)(size * 0.68f);
        int gr  = (int)(size * 0.19f);

        // Lens fill (semi-transparent light)
        g.setColor(new Color(255, 255, 220, 60));
        g.fillOval(gcx - gr, gcy - gr, gr * 2, gr * 2);

        // Lens ring
        g.setColor(new Color(0xF2C84B));
        g.setStroke(new BasicStroke(glassStroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawOval(gcx - gr, gcy - gr, gr * 2, gr * 2);

        // Handle (bottom-right, 45°)
        int hx1 = (int)(gcx + gr * 0.70f);
        int hy1 = (int)(gcy + gr * 0.70f);
        int hx2 = (int)(gcx + gr * 1.65f);
        int hy2 = (int)(gcy + gr * 1.65f);
        g.drawLine(hx1, hy1, hx2, hy2);

        // Gold border
        g.setColor(new Color(0xC8A415));
        g.setStroke(new BasicStroke(Math.max(1f, size / 32f)));
        g.drawRoundRect(0, 0, size - 1, size - 1, arc, arc);

        g.dispose();
        return img;
    }
}
