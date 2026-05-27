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

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Renders the MT Analyze brand mark: a bold "M" in accent blue
 * on a dark-blue rounded square, matching the landing page favicon.
 */
public final class AppIcon {

    private static final Color BG_COLOR     = new Color(0x0B1220);
    private static final Color LETTER_COLOR = new Color(0x78B6FF);
    private static final String[] FONT_CANDIDATES = {
        "Inter", "Segoe UI", "Helvetica Neue", "Arial"
    };

    private AppIcon() {}

    public static BufferedImage createAppIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,        RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,   RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,   RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,           RenderingHints.VALUE_RENDER_QUALITY);

        // Rounded dark background (radius ~18.75% of size, matching SVG rx=12 on 64px)
        int arc = Math.max(4, size * 12 / 64);
        g.setColor(BG_COLOR);
        g.fillRoundRect(0, 0, size, size, arc, arc);

        // Centred bold "M" in accent colour, ~53% of icon size (SVG font-size 34 on 64px)
        Font font = pickFont(size * 34f / 64f);
        g.setFont(font);
        g.setColor(LETTER_COLOR);

        FontMetrics fm = g.getFontMetrics();
        String text = "M";
        int textW   = fm.stringWidth(text);
        int ascent  = fm.getAscent();
        int descent = fm.getDescent();
        int x = (size - textW) / 2;
        int y = (size - (ascent + descent)) / 2 + ascent;
        g.drawString(text, x, y);

        g.dispose();
        return img;
    }

    private static Font pickFont(float size) {
        java.util.Set<String> available = new java.util.HashSet<>(java.util.Arrays.asList(
            GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String name : FONT_CANDIDATES) {
            if (available.contains(name)) {
                return new Font(name, Font.BOLD, 1).deriveFont(size);
            }
        }
        return new Font(Font.SANS_SERIF, Font.BOLD, 1).deriveFont(size);
    }
}