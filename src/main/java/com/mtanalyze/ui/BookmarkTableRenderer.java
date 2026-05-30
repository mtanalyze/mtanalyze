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

import com.mtanalyze.bookmark.BookmarkManager;
import com.mtanalyze.bookmark.Bookmark;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.List;

public final class BookmarkTableRenderer extends DefaultTableCellRenderer {

    private final transient BookmarkManager manager;

    public BookmarkTableRenderer(BookmarkManager manager) {
        this.manager = manager;
    }

    @Override
    public Component getTableCellRendererComponent(JTable t, Object val,
            boolean sel, boolean focus, int row, int col) {
        super.getTableCellRendererComponent(t, val, sel, focus, row, col);
        List<Bookmark> bm = manager.items();
        if (col == 0 && row < bm.size())
            setToolTipText(bm.get(row).filePath());
        else if (col == 2 && row < bm.size())
            setToolTipText(bm.get(row).note().isEmpty() ? null : bm.get(row).note());
        else
            setToolTipText(null);
        return this;
    }
}