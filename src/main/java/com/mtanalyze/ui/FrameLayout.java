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

import com.mtanalyze.ui.view.MessageSourcePanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.function.Consumer;

/** Static factory helpers for common frame layout patterns. */
public final class FrameLayout {

    private FrameLayout() {}

    /** Flat ✕ close button (no border/fill, 20×20). Calls {@code onClose} on click. */
    public static JButton makeCloseButton(Runnable onClose) {
        JButton btn = new JButton("✕");
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setFont(btn.getFont().deriveFont(10f));
        btn.setPreferredSize(new Dimension(20, 20));
        btn.addActionListener(e -> onClose.run());
        return btn;
    }

    /**
     * Wires copy/cut/paste actions from {@code src} onto pre-created menu items,
     * then passes each item to {@code addItem} (e.g. {@code menu::add}).
     */
    public static void wireTextMenuItems(JTextComponent src, boolean hasSel,
                                         JMenuItem copy, JMenuItem cut, JMenuItem paste,
                                         Consumer<JMenuItem> addItem) {
        copy.setEnabled(hasSel);
        cut.setEnabled(hasSel);
        copy.addActionListener(e -> src.copy());
        cut.addActionListener(e -> src.cut());
        paste.addActionListener(e -> src.paste());
        addItem.accept(copy);
        addItem.accept(cut);
        addItem.accept(paste);
    }

    /** Icon-only toolbar button that looks flat (no border / fill). */
    public static JButton makeNavButton(Icon icon, String tooltip) {
        JButton btn = new JButton(icon);
        btn.setToolTipText(tooltip);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setPreferredSize(new Dimension(28, 28));
        return btn;
    }

    /**
     * Wraps {@code content} in a titled card with a section header and close button.
     * {@code onClose} is invoked when the close button is clicked.
     * {@code extraBtns} appear left of the close button.
     */
    public static JPanel wrapDetailCard(JComponent content, String title,
                                        Runnable onClose, JButton... extraBtns) {
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return wrapDetailCard(content, label, onClose, extraBtns);
    }

    // -----------------------------------------------------------------------
    // Progress dialog
    // -----------------------------------------------------------------------

    /** Returned by {@link #buildProgressDialog}; use {@link #runWorker} to launch. */
    public record ProgressDialog(JDialog dialog, JButton cancelBtn) {
        public void runWorker(SwingWorker<?, ?> worker) {
            cancelBtn.addActionListener(e -> worker.cancel(true));
            worker.execute();
            dialog.setVisible(true);
        }
    }

    /**
     * Builds a modal progress dialog containing {@code bar} and a Cancel button.
     * Configure the bar before passing it in; call {@link ProgressDialog#runWorker} to show.
     */
    public static ProgressDialog buildProgressDialog(Frame owner, String title, String label, JProgressBar bar) {
        JButton cancelBtn = new JButton("Cancel");
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnPanel.add(cancelBtn);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));
        panel.add(new JLabel(label), BorderLayout.NORTH);
        panel.add(bar,              BorderLayout.CENTER);
        panel.add(btnPanel,         BorderLayout.SOUTH);
        panel.setPreferredSize(new Dimension(360, 110));

        JDialog dialog = new JDialog(owner, title, true);
        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(owner);
        return new ProgressDialog(dialog, cancelBtn);
    }

    // -----------------------------------------------------------------------
    // Detail card wrappers
    // -----------------------------------------------------------------------

    /** Overload accepting a pre-built {@code JLabel} as the title. */
    public static JPanel wrapDetailCard(JComponent content, JLabel titleLabel,
                                        Runnable onClose, JButton... extraBtns) {
        JButton closeBtn = makeCloseButton(onClose);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 2));
        btns.setOpaque(false);
        for (JButton b : extraBtns) btns.add(b);
        btns.add(closeBtn);

        JPanel wrapper = new JPanel(new BorderLayout(0, 0));
        wrapper.add(MessageSourcePanel.buildSectionHeader(titleLabel, btns), BorderLayout.NORTH);
        wrapper.add(content, BorderLayout.CENTER);
        return wrapper;
    }
}