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

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/** Shared brand palette and constellation backdrop used by the About dialog. */
public final class BrandTheme {

    // Brand palette — keeps the About box visually consistent.
    static final Color BG     = new Color(0x1F, 0x33, 0x5D);
    static final Color FG     = new Color(0xEA, 0xEF, 0xF8);
    static final Color SUB    = new Color(0xB5, 0xC4, 0xE1);
    static final Color BORDER = new Color(0x10, 0x1B, 0x32);

    // Constellation backdrop, loaded lazily and cached.
    private static BufferedImage backdrop;
    private static boolean       backdropLoaded;

    private BrandTheme() {}

    /** The constellation graphic painted behind the About content, or {@code null} if absent. */
    static BufferedImage backdrop() {
        if (!backdropLoaded) {
            backdropLoaded = true;
            try (InputStream in = BrandTheme.class.getResourceAsStream("/constellation_bg.png")) {
                if (in != null) backdrop = ImageIO.read(in);
            } catch (IOException e) {
                backdrop = null;
            }
        }
        return backdrop;
    }

    /**
     * Fills {@code w}×{@code h} with the brand background: the constellation graphic scaled to cover
     * (centred, aspect preserved), over the navy {@link #BG}, with a translucent navy wash on top so
     * foreground text stays legible. Falls back to a plain {@link #BG} fill when the image is missing.
     */
    static void paintBackdrop(Graphics2D g, int w, int h) {
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        BufferedImage img = backdrop();
        if (img != null) {
            double scale = Math.max(w / (double) img.getWidth(), h / (double) img.getHeight());
            int dw = (int) Math.ceil(img.getWidth()  * scale);
            int dh = (int) Math.ceil(img.getHeight() * scale);
            Object oldInterp = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
            if (oldInterp != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);
            g.setColor(new Color(BG.getRed(), BG.getGreen(), BG.getBlue(), 120));
            g.fillRect(0, 0, w, h);
        }
    }
}