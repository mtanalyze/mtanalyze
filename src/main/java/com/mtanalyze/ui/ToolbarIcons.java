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

import javax.swing.Icon;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.BiConsumer;

/**
 * Programmatically generated toolbar icons (20 × 20 px, Java2D).
 * All icons render in the component's foreground color at paint time,
 * adapting automatically to light and dark themes.
 */
public final class ToolbarIcons {

    public static final String SANS_SERIF = "SansSerif";

    private ToolbarIcons() {}

    private static final int S = 20;
    private static final int M = 16;

    // ------------------------------------------------------------------
    // Filter-mode icons – AND / OR toggle
    // ------------------------------------------------------------------
    private static final Color AND_COLOR = new Color(74, 144, 194);
    private static final Color OR_COLOR  = new Color(89, 155, 89);

    // Semantic palette – fixed colours that convey action meaning independent of theme
    private static final Color COLOR_DESTRUCTIVE = new Color(220,  53,  69);  // delete / remove
    private static final Color COLOR_FILTER      = new Color(251, 140,   0);  // filter actions
    private static final Color COLOR_EXPORT      = new Color(  0, 150, 136);  // export / import arrows
    public static final Color COLOR_BOOKMARK    = new Color(210, 158,  55);  // bookmarks
    private static final Color COLOR_RELOAD      = new Color( 21, 128, 196);  // reload / refresh
    private static final Color COLOR_PLUS        = new Color( 52, 168,  83);  // add / create actions
    private static final Color COLOR_COLUMN      = new Color(100,  96, 205);  // column / layout actions

    public static Icon filterAnd() { return filterLabel("AND",  AND_COLOR); }
    public static Icon filterOr()  { return filterLabel("OR",   OR_COLOR); }
    public static Icon seqOn()     { return filterLabel("SEQ",  COLOR_COLUMN); }
    public static Icon seqOff()    { return filterLabel("FLAT", new Color(120, 120, 120)); }

    private static Icon filterLabel(String label, Color accent) {
        return makeIcon((g, c) -> {
            g.setColor(accent);
            g.fill(new RoundRectangle2D.Float(1.5f, 3.5f, 17f, 13f, 6f, 6f));
            g.setFont(new Font(SANS_SERIF, Font.BOLD, 7));
            FontMetrics fm = g.getFontMetrics();
            int tx = (S - fm.stringWidth(label)) / 2;
            int ty = (S + fm.getAscent() - fm.getDescent()) / 2;
            g.setColor(Color.WHITE);
            g.drawString(label, tx, ty);
        });
    }

    // ------------------------------------------------------------------
    // Wrap-mode icons – multi-line / single-line toggle
    // ------------------------------------------------------------------
    public static Icon wrapMulti()  { return lineWrapIcon(true); }
    public static Icon wrapSingle() { return lineWrapIcon(false); }

    private static Icon lineWrapIcon(boolean multi) {
        return makeIcon((g, c) -> {
            g.setColor(c);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (multi) {
                g.drawLine(3, 5, 17, 5);
                g.drawLine(3, 10, 14, 10);
                g.drawLine(3, 15, 17, 15);
            } else {
                g.drawLine(3, 10, 13, 10);
                g.fillOval(14, 9, 2, 2);
                g.fillOval(17, 9, 2, 2);
            }
        });
    }

    // ------------------------------------------------------------------
    // Quick Filter – symmetric funnel
    // ------------------------------------------------------------------
    public static Icon quickFilter() {
        return makeIcon((g, c) -> {
            Path2D.Float funnel = new Path2D.Float();
            funnel.moveTo(2, 3);
            funnel.lineTo(18, 3);
            funnel.lineTo(13, 9);
            funnel.lineTo(13, 17);
            funnel.lineTo(7, 17);
            funnel.lineTo(7, 9);
            funnel.closePath();
            g.setColor(COLOR_FILTER);
            g.fill(funnel);
        });
    }

    // ------------------------------------------------------------------
    // Cols – column bars (two active, one muted)
    // ------------------------------------------------------------------
    public static Icon colFilter() {
        return makeIcon((g, c) -> {
            g.setColor(COLOR_COLUMN);
            g.fill(new RoundRectangle2D.Float(2f,    3f, 4.5f, 14f, 1.5f, 1.5f));
            g.fill(new RoundRectangle2D.Float(7.75f, 3f, 4.5f, 14f, 1.5f, 1.5f));
            g.setColor(muted());
            g.fill(new RoundRectangle2D.Float(13.5f, 3f, 4.5f, 14f, 1.5f, 1.5f));
        });
    }

    // ------------------------------------------------------------------
    // Component View – split tag values into individual components
    // ------------------------------------------------------------------
    public static Icon splitValues() {
        return makeIcon((g, c) -> {
            g.setColor(c);
            g.fill(new RoundRectangle2D.Float(3f, 1.5f, 14f, 5f, 1.8f, 1.8f));
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(10, 7, 10, 10);
            g.drawLine(10, 10, 5, 12);
            g.drawLine(10, 10, 15, 12);
            g.fill(new RoundRectangle2D.Float(1.5f, 13f, 7f, 5f, 1.8f, 1.8f));
            g.fill(new RoundRectangle2D.Float(11.5f, 13f, 7f, 5f, 1.8f, 1.8f));
        });
    }

    // ------------------------------------------------------------------
    // Magnifying glass – "Search"
    // ------------------------------------------------------------------
    public static Icon search() {
        return makeIcon((g, c) -> {
            g.setColor(c);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawOval(2, 2, 11, 11);
            g.drawLine(12, 12, 17, 17);
        });
    }

    // ------------------------------------------------------------------
    // Gear – "Settings" (FA-cog style: solid body, hollow centre)
    // ------------------------------------------------------------------
    public static Icon settings() {
        return makeIcon((g, c) -> {
            g.setColor(c);
            drawGear(g, 10, 10, 8, new double[]{8.5, 5.8, 3.0, Math.PI / 8 * 0.45});
        });
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Icon makeIconOfSize(int size, BiConsumer<Graphics2D, Color> painter) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Color fg = (c != null) ? c.getForeground() : new Color(0x455A64);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.translate(x, y);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,    RenderingHints.VALUE_STROKE_PURE);
                g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                painter.accept(g2, fg);
                g2.dispose();
            }
            @Override public int getIconWidth()  { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    private static Icon makeIcon(BiConsumer<Graphics2D, Color> painter)     { return makeIconOfSize(S, painter); }
    private static Icon makeMenuIcon(BiConsumer<Graphics2D, Color> painter) { return makeIconOfSize(M, painter); }

    private static void setupDocIcon(Graphics2D g, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        drawDocOutline(g);
    }

    /** Dog-eared document outline at (2,1)→(13,15), with fold lines at (10,4). */
    private static void drawDocOutline(Graphics2D g) {
        Path2D.Float doc = new Path2D.Float();
        doc.moveTo(2, 1); doc.lineTo(10, 1); doc.lineTo(13, 4);
        doc.lineTo(13, 15); doc.lineTo(2, 15); doc.closePath();
        g.draw(doc);
        g.drawLine(10, 1, 10, 4);
        g.drawLine(10, 4, 13, 4);
    }

    /** Small filter funnel filled with the current colour. */
    private static void fillFunnel(Graphics2D g) {
        Path2D.Float f = new Path2D.Float();
        f.moveTo(1, 2); f.lineTo(9, 2); f.lineTo(7, 6);
        f.lineTo(7, 13); f.lineTo(4, 13); f.lineTo(4, 6);
        f.closePath();
        g.fill(f);
    }

    /** Gear icon centred at (cx,cy) with tooth geometry [ro, ri, rc, tw]. Color must be set on g before calling. */
    private static void drawGear(Graphics2D g, int cx, int cy, int teeth, double[] r) {
        double ro = r[0];
        double ri = r[1];
        double rc = r[2];
        double tw = r[3];
        double ah = Math.PI / teeth;
        Path2D.Double gear = new Path2D.Double();
        for (int i = 0; i < teeth; i++) {
            double a = i * 2.0 * Math.PI / teeth - Math.PI / 2;
            double xIn = cx + ri * Math.cos(a - ah);
            double yIn = cy + ri * Math.sin(a - ah);
            if (i == 0) gear.moveTo(xIn, yIn); else gear.lineTo(xIn, yIn);
            gear.lineTo(cx + ro * Math.cos(a - tw), cy + ro * Math.sin(a - tw));
            gear.lineTo(cx + ro * Math.cos(a + tw), cy + ro * Math.sin(a + tw));
            gear.lineTo(cx + ri * Math.cos(a + ah), cy + ri * Math.sin(a + ah));
        }
        gear.closePath();
        Area area = new Area(gear);
        area.subtract(new Area(new Ellipse2D.Double(cx - rc, cy - rc, rc * 2, rc * 2)));
        g.fill(area);
    }

    // ------------------------------------------------------------------
    // Menu icons (16 × 16 px) — use with JMenuItem.setIcon()
    // ------------------------------------------------------------------

    // ── Clipboard / Edit ──────────────────────────────────────────────

    public static Icon menuCopy() {
        return makeMenuIcon((g, c) -> {
            drawClipboardBody(g, c);
            g.drawLine(4, 8, 9, 8);
            g.drawLine(4, 10, 9, 10);
            g.drawLine(4, 12, 7, 12);
        });
    }

    public static Icon menuCopyTable() {
        return makeMenuIcon((g, c) -> {
            drawClipboardBody(g, c);
            g.drawLine(3, 9, 10, 9);
            g.drawLine(3, 12, 10, 12);
            g.drawLine(6, 6, 6, 14);
        });
    }

    private static void drawClipboardBody(Graphics2D g, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawRoundRect(2, 4, 9, 11, 2, 2);
        g.fillRoundRect(4, 2, 5, 4, 2, 2);
    }

    public static Icon menuPaste() {
        return makeMenuIcon((g, c) -> {
            g.setColor(c);
            g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawRoundRect(1, 5, 9, 10, 2, 2);
            g.fillRoundRect(3, 3, 5, 4, 2, 2);
            g.drawLine(13, 1, 13, 8);
            g.fillPolygon(new int[]{10, 13, 15}, new int[]{6, 10, 6}, 3);
        });
    }

    // ── Navigation / View ─────────────────────────────────────────────

    public static Icon menuSelectDetail() {
        return makeMenuIcon((g, c) -> {
            g.setColor(c);
            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawRoundRect(4, 1, 11, 14, 1, 1);
            g.drawLine(6, 5, 13, 5);
            g.drawLine(6, 8, 13, 8);
            g.drawLine(6, 11, 11, 11);
            g.fillPolygon(new int[]{1, 4, 1}, new int[]{6, 8, 10}, 3);
        });
    }

    public static Icon menuViewSource() {
        return makeMenuIcon((g, c) -> {
            setupDocIcon(g, c);
            g.drawLine(4, 7, 11, 7);
            g.drawLine(4, 9, 9, 9);
            g.drawLine(4, 11, 11, 11);
        });
    }

    // ── Sort ──────────────────────────────────────────────────────────

    public static Icon menuSortAsc()  { return makeMenuIcon((g, c) -> drawSortIcon(g, c, true)); }
    public static Icon menuSortDesc() { return makeMenuIcon((g, c) -> drawSortIcon(g, c, false)); }

    private static void drawSortIcon(Graphics2D g, Color c, boolean ascending) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int[] ys  = ascending ? new int[]{13, 10, 7, 4} : new int[]{3, 6, 9, 12};
        int[] xes = {9, 7, 5, 3};
        for (int i = 0; i < ys.length; i++) g.drawLine(1, ys[i], xes[i], ys[i]);
        g.drawLine(13, ys[0], 13, ys[3]);
        g.fillPolygon(new int[]{10, 13, 15}, new int[]{8, ys[3], 8}, 3);
    }

    public static Icon menuSortClear() {
        return makeMenuIcon((g, c) -> {
            g.setColor(c);
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(1, 4,  8, 4);
            g.drawLine(1, 8,  8, 8);
            g.drawLine(1, 12, 8, 12);
            g.drawLine(11, 5, 15, 9);
            g.drawLine(15, 5, 11, 9);
        });
    }

    public static Icon menuMoveFirst() {
        return makeMenuIcon((g, c) -> {
            g.setColor(COLOR_COLUMN);
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(1, 2, 1, 14);
            g.drawLine(3, 8, 14, 8);
            g.fillPolygon(new int[]{6, 3, 6}, new int[]{5, 8, 11}, 3);
        });
    }

    // ── Column / Layout ───────────────────────────────────────────────

    public static Icon menuHideColumn() {
        return makeMenuIcon((g, c) -> {
            g.setColor(COLOR_COLUMN);
            g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D.Float upper = new Path2D.Float();
            upper.moveTo(1, 8); upper.quadTo(8, 2, 15, 8);
            g.draw(upper);
            Path2D.Float lower = new Path2D.Float();
            lower.moveTo(1, 8); lower.quadTo(8, 14, 15, 8);
            g.draw(lower);
            g.fillOval(6, 6, 4, 4);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(COLOR_COLUMN.getRed(), COLOR_COLUMN.getGreen(), COLOR_COLUMN.getBlue(), 180));
            g.drawLine(3, 13, 13, 3);
        });
    }

    public static Icon menuShowColumns() {
        return makeMenuIcon((g, c) -> {
            g.setColor(COLOR_COLUMN);
            g.fill(new RoundRectangle2D.Float(1f,  2f, 3.5f, 12f, 1f, 1f));
            g.fill(new RoundRectangle2D.Float(6f,  2f, 3.5f, 12f, 1f, 1f));
            g.fill(new RoundRectangle2D.Float(11f, 2f, 3.5f, 12f, 1f, 1f));
        });
    }

    public static Icon menuSaveLayout() {
        return makeMenuIcon((g, c) -> drawSaveIcon(g, COLOR_COLUMN));
    }

    public static Icon menuSaveSession() {
        return makeMenuIcon(ToolbarIcons::drawSaveIcon);
    }

    private static void drawSaveIcon(Graphics2D g, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawRoundRect(1, 1, 14, 14, 1, 1);
        g.drawRoundRect(3, 8, 10, 6, 1, 1);
        g.fillRoundRect(4, 1, 7, 5, 1, 1);
    }

    // ── Search & Filter ───────────────────────────────────────────────

    public static Icon menuSearch() {
        return makeMenuIcon((g, c) -> {
            g.setColor(c);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawOval(1, 1, 9, 9);
            g.drawLine(9, 9, 14, 14);
        });
    }

    public static Icon menuSoftMatch() {
        return makeMenuIcon((g, c) -> {
            g.setColor(c);
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawOval(1, 1, 9, 9);
            g.drawLine(9, 9, 14, 14);
            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D.Float wave = new Path2D.Float();
            wave.moveTo(2.5f, 6f);
            wave.curveTo(3f, 4f, 4.5f, 8f, 6f, 6f);
            wave.curveTo(7.5f, 4f, 8f, 7f, 8f, 6f);
            g.draw(wave);
        });
    }

    public static Icon menuFilterAdd() {
        return makeMenuIcon((g, c) -> {
            g.setColor(COLOR_FILTER);
            fillFunnel(g);
            g.setColor(COLOR_PLUS);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(11, 8, 15, 8);
            g.drawLine(13, 6, 13, 10);
        });
    }

    public static Icon menuFilterClear() {
        return makeMenuIcon((g, c) -> {
            g.setColor(COLOR_FILTER);
            fillFunnel(g);
            g.setColor(COLOR_DESTRUCTIVE);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(11, 5, 15, 9);
            g.drawLine(15, 5, 11, 9);
        });
    }

    // ── Bookmark / Export / Delete ────────────────────────────────────

    public static Icon menuNote() {
        return makeMenuIcon((g, c) -> {
            g.setColor(c);
            g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            // Page outline with folded top-right corner
            g.drawLine(3, 1, 11, 1);
            g.drawLine(11, 1, 14, 4);
            g.drawLine(14, 4, 14, 15);
            g.drawLine(3, 15, 14, 15);
            g.drawLine(3, 1, 3, 15);
            g.drawLine(11, 1, 11, 4);
            g.drawLine(11, 4, 14, 4);
            // Text lines
            g.drawLine(5, 6, 12, 6);
            g.drawLine(5, 9, 12, 9);
            g.drawLine(5, 12, 9, 12);
        });
    }

    public static Icon menuBookmark() {
        return makeMenuIcon((g, c) -> {
            g.setColor(COLOR_BOOKMARK);
            Path2D.Float ribbon = new Path2D.Float();
            ribbon.moveTo(3, 1);  ribbon.lineTo(13, 1);
            ribbon.lineTo(13, 15); ribbon.lineTo(8, 11);
            ribbon.lineTo(3, 15); ribbon.closePath();
            g.fill(ribbon);
        });
    }

    public static Icon menuExport() {
        return makeMenuIcon((g, c) -> {
            g.setColor(c);
            g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawRoundRect(2, 8, 12, 7, 2, 2);
            g.setColor(COLOR_EXPORT);
            g.drawLine(8, 1, 8, 9);
            g.fillPolygon(new int[]{5, 8, 11}, new int[]{4, 1, 4}, 3);
        });
    }

    public static Icon menuDelete() {
        return makeMenuIcon((g, c) -> {
            g.setColor(COLOR_DESTRUCTIVE);
            g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(2, 4, 14, 4);
            g.drawRoundRect(5, 2, 6, 3, 1, 1);
            g.drawRoundRect(3, 4, 10, 11, 1, 1);
            g.drawLine(6, 6,  6, 13);
            g.drawLine(8, 6,  8, 13);
            g.drawLine(10, 6, 10, 13);
        });
    }

    // ── Explorer ──────────────────────────────────────────────────────

    public static Icon menuOpen() {
        return makeMenuIcon((g, c) -> {
            g.setColor(c);
            g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawRoundRect(1, 6, 14, 9, 1, 1);
            Path2D.Float tab = new Path2D.Float();
            tab.moveTo(1, 6); tab.lineTo(1, 4); tab.lineTo(5, 4); tab.lineTo(7, 6);
            g.draw(tab);
        });
    }

    public static Icon menuViewInEditor() {
        return makeMenuIcon((g, c) -> {
            setupDocIcon(g, c);
            g.drawLine(4, 7, 8, 7);
            g.drawLine(4, 9, 11, 9);
            g.drawLine(4, 11, 9, 11);
        });
    }

    public static Icon menuImportDir() {
        return makeMenuIcon((g, c) -> {
            g.setColor(c);
            g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawRoundRect(1, 7, 9, 8, 1, 1);
            Path2D.Float tab = new Path2D.Float();
            tab.moveTo(1, 7); tab.lineTo(1, 5); tab.lineTo(4, 5); tab.lineTo(6, 7);
            g.draw(tab);
            g.setColor(COLOR_EXPORT);
            g.drawLine(13, 2, 13, 11);
            g.fillPolygon(new int[]{10, 13, 15}, new int[]{8, 12, 8}, 3);
        });
    }

    public static Icon menuRemove() {
        return makeMenuIcon((g, c) -> {
            g.setColor(COLOR_DESTRUCTIVE);
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawOval(1, 1, 14, 14);
            g.drawLine(4, 8, 12, 8);
        });
    }

    public static Icon menuAddDict() {
        return makeMenuIcon((g, c) -> {
            g.setColor(c);
            g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawRoundRect(2, 1, 12, 14, 1, 1);
            g.drawLine(5, 1, 5, 15);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(9, 6, 9, 11);
            g.drawLine(6, 8, 12, 8);
        });
    }

    // ── File actions ──────────────────────────────────────────────────

    public static Icon menuNew() {
        return makeMenuIcon(ToolbarIcons::setupDocIcon);
    }

    public static Icon menuImportFile() {
        return makeMenuIcon((g, c) -> {
            g.setColor(c);
            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D.Float doc = new Path2D.Float();
            doc.moveTo(2, 4); doc.lineTo(8, 4); doc.lineTo(11, 7);
            doc.lineTo(11, 15); doc.lineTo(2, 15); doc.closePath();
            g.draw(doc);
            g.drawLine(8, 4, 8, 7);
            g.drawLine(8, 7, 11, 7);
            g.setColor(COLOR_EXPORT);
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(13, 1, 13, 8);
            g.fillPolygon(new int[]{10, 13, 15}, new int[]{6, 9, 6}, 3);
        });
    }

    public static Icon menuAppendFile() {
        return makeMenuIcon((g, c) -> {
            setupDocIcon(g, c);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(4, 9, 10, 9);
            g.drawLine(7, 6, 7, 12);
        });
    }

    public static Icon menuReload() {
        return makeMenuIcon((g, c) -> {
            g.setColor(COLOR_RELOAD);
            g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawArc(2, 2, 12, 12, 30, -280);
            g.fillPolygon(new int[]{14, 14, 10}, new int[]{3, 7, 4}, 3);
        });
    }

    public static Icon menuSettings() {
        return makeMenuIcon((g, c) -> {
            g.setColor(c);
            drawGear(g, 8, 8, 6, new double[]{6.5, 4.5, 2.3, Math.PI / 6 * 0.4});
        });
    }

    public static Icon menuExit() {
        return makeMenuIcon((g, c) -> {
            g.setColor(c);
            g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(2, 1, 2, 15);
            g.drawLine(2, 1, 9, 1);
            g.drawLine(2, 15, 9, 15);
            g.drawLine(5, 8, 14, 8);
            g.fillPolygon(new int[]{11, 14, 11}, new int[]{5, 8, 11}, 3);
        });
    }

    // ── View / Navigation ─────────────────────────────────────────────

    public static Icon menuDiff() {
        return makeMenuIcon((g, c) -> {
            g.setColor(c);
            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawRoundRect(1, 2, 5, 12, 1, 1);
            g.drawLine(2, 6, 5, 6); g.drawLine(2, 9, 5, 9);
            g.drawRoundRect(10, 2, 5, 12, 1, 1);
            g.drawLine(11, 6, 14, 6); g.drawLine(11, 9, 14, 9); g.drawLine(11, 11, 13, 11);
            g.drawLine(7, 6, 9, 6);
            g.fillPolygon(new int[]{7, 9, 7}, new int[]{4, 6, 8}, 3);
            g.drawLine(7, 10, 9, 10);
            g.fillPolygon(new int[]{9, 7, 9}, new int[]{8, 10, 12}, 3);
        });
    }

    // ── Help ──────────────────────────────────────────────────────────

    private static final Color HELP_COLOR  = new Color(53, 116, 240);
    private static final Color ABOUT_COLOR = new Color(0, 150, 136);

    public static Icon menuHelp()   { return menuFilledCircleLetter("?", HELP_COLOR); }
    public static Icon menuAbout()  { return menuFilledCircleLetter("i", ABOUT_COLOR); }

    public static Icon menuIsoDoc() {
        return makeMenuIcon((g, c) -> {
            g.setColor(new Color(0, 130, 180));
            g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawOval(1, 2, 13, 13);
            g.drawLine(1, 8, 14, 8);
            g.drawOval(4, 2, 6, 13);
        });
    }

    private static Icon menuFilledCircleLetter(String letter, Color fill) {
        return makeMenuIcon((g, c) -> {
            g.setColor(fill);
            g.fillOval(1, 1, 14, 14);
            g.setFont(new Font(SANS_SERIF, Font.BOLD, 9));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(Color.WHITE);
            g.drawString(letter, (M - fm.stringWidth(letter)) / 2, (M + fm.getAscent() - fm.getDescent()) / 2);
        });
    }

    /** Muted colour for de-emphasised elements – ~40 % opacity. */
    private static Color muted() {
        return new Color(COLOR_COLUMN.getRed(), COLOR_COLUMN.getGreen(), COLOR_COLUMN.getBlue(), 100);
    }

    // ------------------------------------------------------------------
    // Tool-window / view-switcher icons (variable size, x/y-offset aware)
    // ------------------------------------------------------------------

    @FunctionalInterface
    private interface IconPainter { void paint(Graphics2D g, Component c, int x, int y); }

    private static Icon makeThemedIcon(int w, int h, IconPainter painter) {
        return new Icon() {
            @Override public int getIconWidth()  { return w; }
            @Override public int getIconHeight() { return h; }
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c.getForeground());
                try { painter.paint(g2, c, x, y); } finally { g2.dispose(); }
            }
        };
    }

    public static Icon folderIcon() {
        return makeThemedIcon(20, 18, (g2, c, x, y) -> {
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(x + 1,  y + 6, x + 1,  y + 4);
            g2.drawLine(x + 1,  y + 4, x + 8,  y + 4);
            g2.drawLine(x + 8,  y + 4, x + 10, y + 6);
            g2.drawRoundRect(x + 1, y + 6, 18, 11, 2, 2);
        });
    }

    public static Icon bookmarkRibbon() {
        return makeThemedIcon(14, 20, (g2, c, x, y) -> {
            g2.setColor(COLOR_BOOKMARK);
            int[] px = {x + 1, x + 13, x + 13, x + 7, x + 1};
            int[] py = {y,     y,       y + 20,  y + 14, y + 20};
            g2.fillPolygon(px, py, 5);
        });
    }

    public static Icon securitiesIcon() {
        return makeThemedIcon(20, 20, (g2, c, x, y) -> {
            g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawRect(x + 2, y + 1, 14, 18);
            g2.drawLine(x + 5, y + 6,  x + 13, y + 6);
            g2.drawLine(x + 5, y + 10, x + 13, y + 10);
            g2.drawLine(x + 5, y + 14, x + 13, y + 14);
        });
    }

    public static Icon cashIcon() {
        return makeThemedIcon(20, 20, (g2, c, x, y) -> {
            g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawRect(x + 2, y + 1, 14, 18);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, 12f));
            FontMetrics fm = g2.getFontMetrics();
            String euro = "€";
            int tx = x + 2 + (14 - fm.stringWidth(euro)) / 2;
            int ty = y + 1 + (18 + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(euro, tx, ty);
        });
    }

    public static Icon clipboardIcon() {
        return makeThemedIcon(16, 18, (g2, c, x, y) -> {
            g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawRect(x + 1, y + 3, 13, 14);
            g2.drawRoundRect(x + 4, y + 1, 7, 4, 2, 2);
            g2.drawLine(x + 4, y + 8,  x + 10, y + 8);
            g2.drawLine(x + 4, y + 12, x + 10, y + 12);
        });
    }

    public static Icon accountMappingIcon() {
        return makeThemedIcon(20, 20, (g2, c, x, y) -> {
            g2.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawRect(x + 1, y + 4,  7, 4);
            g2.drawRect(x + 1, y + 12, 7, 4);
            g2.drawRect(x + 11, y + 4,  7, 4);
            g2.drawRect(x + 11, y + 12, 7, 4);
            g2.drawLine(x + 8, y + 6,  x + 11, y + 6);
            g2.drawLine(x + 8, y + 14, x + 11, y + 14);
            g2.drawLine(x + 9, y + 5,  x + 11, y + 6);
            g2.drawLine(x + 9, y + 7,  x + 11, y + 6);
            g2.drawLine(x + 9, y + 13, x + 11, y + 14);
            g2.drawLine(x + 9, y + 15, x + 11, y + 14);
        });
    }

    public static Icon diffIcon() {
        return makeThemedIcon(20, 20, (g2, c, x, y) -> {
            g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawRect(x + 1, y + 3, 7, 14);
            g2.drawRect(x + 12, y + 3, 7, 14);
            g2.setColor(new Color(200, 60, 60));
            g2.fillRect(x + 13, y + 8, 6, 3);
            g2.setColor(c.getForeground());
            g2.drawLine(x + 9, y + 10, x + 11, y + 10);
        });
    }

    public static Icon sourceIcon() {
        return makeThemedIcon(20, 20, (g2, c, x, y) -> {
            g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int[] px = { x+2, x+13, x+18, x+18, x+2 };
            int[] py = { y+1, y+1,  y+6,  y+19, y+19 };
            g2.drawPolygon(px, py, 5);
            g2.drawLine(x+13, y+1, x+13, y+6);
            g2.drawLine(x+13, y+6, x+18, y+6);
            g2.drawLine(x+4,  y+9,  x+15, y+9);
            g2.drawLine(x+4,  y+12, x+15, y+12);
            g2.drawLine(x+4,  y+15, x+11, y+15);
        });
    }

    public static Icon tagsIcon() {
        return makeThemedIcon(20, 20, (g2, c, x, y) -> {
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int icx = x + 10;
            int icy = y + 10;
            g2.drawArc(icx - 9, icy - 5, 18, 10,   0, 180);
            g2.drawArc(icx - 9, icy - 5, 18, 10, 180, 180);
            g2.fillOval(icx - 2, icy - 2, 5, 5);
        });
    }

    public static Icon notificationIcon() {
        return makeThemedIcon(20, 20, (g2, c, x, y) -> {
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawArc(x + 8,  y + 1,  4,  3,  0, 180);
            g2.drawArc(x + 3,  y + 4,  14, 11, 0, 180);
            g2.drawLine(x + 3,  y + 9,  x + 3,  y + 13);
            g2.drawLine(x + 3,  y + 13, x + 1,  y + 15);
            g2.drawLine(x + 17, y + 9,  x + 17, y + 13);
            g2.drawLine(x + 17, y + 13, x + 19, y + 15);
            g2.drawLine(x + 1,  y + 15, x + 19, y + 15);
            g2.fillOval(x + 8,  y + 16, 4, 3);
        });
    }
}