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
package com.mtanalyze.ui.filter;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;

/**
 * Shared base for {@link ColumnFilterRow} and {@link FinFilterRow}.
 * Owns the column-model reference, layout utilities, and the column-model
 * listener that keeps both filter rows in sync with column resize / reorder.
 */
public abstract class AbstractFilterRow extends JPanel {

    public static final int ROW_H = 22;

    protected transient TableColumnModel columnModel;

    protected final transient TableColumnModelListener colModelListener =
            new TableColumnModelListener() {
                @Override public void columnMarginChanged(ChangeEvent e)           { refreshLayout(); repaint(); }
                @Override public void columnMoved(TableColumnModelEvent e)         { refreshLayout(); repaint(); }
                @Override public void columnAdded(TableColumnModelEvent e)         { /* NOP */ }
                @Override public void columnRemoved(TableColumnModelEvent e)       { /* NOP */ }
                @Override public void columnSelectionChanged(ListSelectionEvent e) { /* NOP */ }
            };

    /** Reposition child components to match the current column widths/order. */
    public abstract void refreshLayout();

    @Override
    public Dimension getPreferredSize() {
        if (columnModel == null) return new Dimension(0, ROW_H);
        int w = 0;
        for (int i = 0; i < columnModel.getColumnCount(); i++)
            w += effectiveWidth(columnModel.getColumn(i));
        return new Dimension(w, ROW_H);
    }

    protected static int effectiveWidth(TableColumn tc) {
        int w = tc.getWidth();
        return w > 0 ? w : tc.getPreferredWidth();
    }
}