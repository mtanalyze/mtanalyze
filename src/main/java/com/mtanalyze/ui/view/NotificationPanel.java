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
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class NotificationPanel extends RoundedPanel {

    public enum Type { INFO, WARNING, ERROR }

    private static final class Entry {
        final Type   type;
        final String title;
        final String message;
        final String time;

        Entry(Type type, String title, String message) {
            this.type    = type;
            this.title   = title;
            this.message = message;
            this.time    = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm"));
        }
    }

    private static final Color  ACCENT_INFO    = new Color(0x3592C4);
    private static final Color  ACCENT_WARNING = new Color(0xD4A500);
    private static final Color  ACCENT_ERROR   = new Color(0xC94040);
    private static final String DISABLED_FG    = "Label.disabledForeground";

    private final List<Entry> entries     = new ArrayList<>();
    private final JPanel      listPanel;
    private final JLabel      emptyLabel;
    private final JButton     clearAllBtn;
    private transient Runnable onAdded;

    public NotificationPanel() {
        super(new BorderLayout(0, 0));
        setBorder(new EmptyBorder(8, 8, 8, 8));

        // ── Clear-all button (used by the outer wrapper's title bar) ────────
        clearAllBtn = new JButton("Clear all");
        clearAllBtn.setFocusPainted(false);
        clearAllBtn.setBorderPainted(false);
        clearAllBtn.setContentAreaFilled(false);
        clearAllBtn.setForeground(UIManager.getColor(DISABLED_FG));
        clearAllBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearAllBtn.setFont(clearAllBtn.getFont().deriveFont(Font.PLAIN, 11f));
        clearAllBtn.addActionListener(e -> clearAll());

        // ── Empty state ──────────────────────────────────────────────────────
        emptyLabel = new JLabel("No notifications", SwingConstants.CENTER);
        emptyLabel.setForeground(UIManager.getColor(DISABLED_FG));
        emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.PLAIN, 12f));

        // ── Notification list ────────────────────────────────────────────────
        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setOpaque(false);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(listPanel,  BorderLayout.NORTH);
        wrapper.add(emptyLabel, BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(wrapper);
        scroll.setBorder(null);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);

        add(scroll, BorderLayout.CENTER);

        refresh();
    }

    public JButton getClearAllButton() { return clearAllBtn; }

    public void setOnAdded(Runnable callback) {
        this.onAdded = callback;
    }

    public void addNotification(Type type, String title, String message) {
        entries.addFirst(new Entry(type, title, message));
        refresh();
        if (onAdded != null) onAdded.run();
    }

    private void clearAll() {
        entries.clear();
        refresh();
    }

    private void refresh() {
        listPanel.removeAll();
        boolean empty = entries.isEmpty();
        emptyLabel.setVisible(empty);
        clearAllBtn.setVisible(!empty);

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            listPanel.add(buildItem(entry, () -> {
                entries.remove(entry);
                refresh();
            }));
            listPanel.add(Box.createVerticalStrut(4));
        }

        listPanel.revalidate();
        listPanel.repaint();
    }

    private static JPanel buildItem(Entry e, Runnable onClose) {
        JPanel item = getJPanel(e);

        JPanel content = buildContent(e);

        // ── Close button ──────────────────────────────────────────────────────
        JButton closeBtn = new JButton("×");
        closeBtn.setFont(closeBtn.getFont().deriveFont(Font.PLAIN, 16f));
        closeBtn.setFocusPainted(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setForeground(UIManager.getColor(DISABLED_FG));
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.setPreferredSize(new Dimension(22, 22));
        closeBtn.setVerticalAlignment(SwingConstants.TOP);
        closeBtn.addActionListener(ev -> onClose.run());

        item.add(content, BorderLayout.CENTER);
        item.add(closeBtn, BorderLayout.EAST);

        return item;
    }

    private static JPanel getJPanel(Entry e) {
        Color accent = switch (e.type) {
            case WARNING -> ACCENT_WARNING;
            case ERROR -> ACCENT_ERROR;
            default -> ACCENT_INFO;
        };

        JPanel item = new JPanel(new BorderLayout(6, 0));
        item.setOpaque(false);
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        item.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
            new EmptyBorder(8, 8, 8, 4)
        ));
        return item;
    }

    private static JPanel buildContent(Entry e) {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        JLabel titleLbl = new JLabel(e.title);
        titleLbl.setFont(titleLbl.getFont().deriveFont(Font.BOLD, 13f));
        titleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel msgLbl = new JLabel("<html>" + e.message + "</html>");
        msgLbl.setFont(msgLbl.getFont().deriveFont(Font.PLAIN, 12f));
        msgLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel timeLbl = new JLabel(e.time);
        timeLbl.setFont(timeLbl.getFont().deriveFont(Font.PLAIN, 11f));
        timeLbl.setForeground(UIManager.getColor(DISABLED_FG));
        timeLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(titleLbl);
        content.add(Box.createVerticalStrut(2));
        content.add(msgLbl);
        content.add(Box.createVerticalStrut(4));
        content.add(timeLbl);
        return content;
    }
}