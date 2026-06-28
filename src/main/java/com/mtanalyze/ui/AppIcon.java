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
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders the application icon from {@code rs_logo.svg} on the classpath.
 * Only the path commands used by that file are supported (M/L/H/V/C/Z and lowercase).
 */
public final class AppIcon {

    private static final String LOGO_RESOURCE = "/rs_logo.svg";
    private static final Color  FILL_COLOR    = new Color(0x1F, 0x33, 0x5D, 238); // #1f335d @ 0.9333
    private static final Color  FILL_DARK_BG  = new Color(0xC8, 0xD6, 0xF0, 238); // lightened tint for dark UIs

    // viewBox from rs_logo.svg (kept in sync with the file).
    private static final double VIEW_W = 60.933334;
    private static final double VIEW_H = 46.626667;

    // Combined nested <g> transforms in rs_logo.svg: matrix(1.333,0,0,-1.333,0,46.627) · scale(0.1)
    private static final AffineTransform PATH_TO_VIEWBOX =
        new AffineTransform(0.13333333, 0, 0, -0.13333333, 0, 46.626667);

    private static final Shape LOGO_SHAPE = loadShape();

    private AppIcon() {}

    /**
     * Default badge form: rounded brand-blue square with the white RS logo inside.
     * Self-contained so it stays legible on both light and dark window chrome.
     */
    public static BufferedImage createAppIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,   RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,      RenderingHints.VALUE_RENDER_QUALITY);

            int arc = Math.max(4, size / 5);
            g.setColor(FILL_COLOR);
            g.fillRoundRect(0, 0, size, size, arc, arc);

            if (LOGO_SHAPE == null) return img;
            double padding = size * 0.18;
            double inner   = size - padding * 2;
            double s       = Math.min(inner / VIEW_W, inner / VIEW_H);
            double tx      = padding + (inner - VIEW_W * s) / 2.0;
            double ty      = padding + (inner - VIEW_H * s) / 2.0;
            g.translate(tx, ty);
            g.scale(s, s);
            g.setColor(FILL_DARK_BG);
            g.fill(LOGO_SHAPE);
        } finally {
            g.dispose();
        }
        return img;
    }

    private static Shape loadShape() {
        try (InputStream in = AppIcon.class.getResourceAsStream(LOGO_RESOURCE)) {
            if (in == null) return null;
            String svg = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("\\bd\\s*=\\s*\"([^\"]+)\"").matcher(svg);
            if (!m.find()) return null;
            Path2D path = parsePath(m.group(1));
            return PATH_TO_VIEWBOX.createTransformedShape(path);
        } catch (IOException ex) {
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // Minimal SVG path parser (M/m, L/l, H/h, V/v, C/c, Z/z)
    // -----------------------------------------------------------------------

    private static Path2D parsePath(String d) {
        List<String> tokens = tokenize(d);
        Path2D path = new Path2D.Double();
        char cmd = 0;
        double cx = 0;
        double cy = 0;
        double subX = 0;
        double subY = 0;
        int i = 0;
        while (i < tokens.size()) {
            String t = tokens.get(i);
            if (t.length() == 1 && isCommand(t.charAt(0))) { cmd = t.charAt(0); i++; }
            switch (cmd) {
                case 'M': {
                    double x = num(tokens.get(i++));
                    double y = num(tokens.get(i++));
                    path.moveTo(x, y);
                    cx = subX = x; cy = subY = y;
                    cmd = 'L';
                    break;
                }
                case 'm': {
                    cx += num(tokens.get(i++));
                    cy += num(tokens.get(i++));
                    path.moveTo(cx, cy);
                    subX = cx; subY = cy;
                    cmd = 'l';
                    break;
                }
                case 'L':
                    cx = num(tokens.get(i++));
                    cy = num(tokens.get(i++));
                    path.lineTo(cx, cy);
                    break;
                case 'l':
                    cx += num(tokens.get(i++));
                    cy += num(tokens.get(i++));
                    path.lineTo(cx, cy);
                    break;
                case 'H': cx  = num(tokens.get(i++)); path.lineTo(cx, cy); break;
                case 'h': cx += num(tokens.get(i++)); path.lineTo(cx, cy); break;
                case 'V': cy  = num(tokens.get(i++)); path.lineTo(cx, cy); break;
                case 'v': cy += num(tokens.get(i++)); path.lineTo(cx, cy); break;
                case 'C': {
                    double x1 = num(tokens.get(i++));
                    double y1 = num(tokens.get(i++));
                    double x2 = num(tokens.get(i++));
                    double y2 = num(tokens.get(i++));
                    double x  = num(tokens.get(i++));
                    double y  = num(tokens.get(i++));
                    path.curveTo(x1, y1, x2, y2, x, y);
                    cx = x; cy = y;
                    break;
                }
                case 'c': {
                    double x1 = cx + num(tokens.get(i++));
                    double y1 = cy + num(tokens.get(i++));
                    double x2 = cx + num(tokens.get(i++));
                    double y2 = cy + num(tokens.get(i++));
                    double x  = cx + num(tokens.get(i++));
                    double y  = cy + num(tokens.get(i++));
                    path.curveTo(x1, y1, x2, y2, x, y);
                    cx = x; cy = y;
                    break;
                }
                case 'Z', 'z':
                    path.closePath();
                    cx = subX; cy = subY;
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported SVG path command: " + cmd);
            }
        }
        return path;
    }

    private static boolean isCommand(char c) {
        return "MmLlHhVvCcZz".indexOf(c) >= 0;
    }

    private static double num(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid SVG number: " + s, e);
        }
    }

    private static List<String> tokenize(String d) {
        List<String> tokens = new ArrayList<>();
        int n = d.length();
        int i = 0;
        while (i < n) {
            char c = d.charAt(i);
            if (Character.isWhitespace(c) || c == ',') {
                i++;
            } else if (isCommand(c)) {
                tokens.add(String.valueOf(c));
                i++;
            } else {
                int end = scanNumber(d, i, n);
                if (end == i) {
                    i++;
                } else {
                    tokens.add(d.substring(i, end));
                    i = end;
                }
            }
        }
        return tokens;
    }

    private static int scanNumber(String d, int start, int n) {
        int j = start;
        if (j < n && (d.charAt(j) == '+' || d.charAt(j) == '-')) j++;
        boolean hasDot = false;
        while (j < n) {
            char ch = d.charAt(j);
            if (ch >= '0' && ch <= '9') {
                j++;
            } else if (ch == '.' && !hasDot) {
                hasDot = true;
                j++;
            } else {
                break;
            }
        }
        if (j < n && (d.charAt(j) == 'e' || d.charAt(j) == 'E')) {
            j++;
            if (j < n && (d.charAt(j) == '+' || d.charAt(j) == '-')) j++;
            while (j < n && d.charAt(j) >= '0' && d.charAt(j) <= '9') j++;
        }
        return j;
    }
}