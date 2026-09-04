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

import com.mtanalyze.model.Entry;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/**
 * Live-view {@link AbstractTableModel} backed by the panel's entry list.
 * Reads directly from the list — no data copy.
 */
final class EntryTableModel extends AbstractTableModel {

    private final transient List<Entry> allEntries;
    private transient List<ColumnDef>   visibleCols = List.of();

    EntryTableModel(List<Entry> allEntries) {
        this.allEntries = allEntries;
    }

    /** Replaces the visible column set, then fires fireTableStructureChanged(). */
    void update(List<ColumnDef> visible) {
        this.visibleCols = List.copyOf(visible);
        fireTableStructureChanged();
    }

    /** Must be called after the row has already been removed from the backing list. */
    void rowDeleted(int modelRow) {
        fireTableRowsDeleted(modelRow, modelRow);
    }

    @Override public int     getRowCount()               { return allEntries.size(); }
    @Override public int     getColumnCount()            { return visibleCols.size(); }
    @Override public String  getColumnName(int c)        { return visibleCols.get(c).label; }
    @Override
    public Object getValueAt(int r, int c) {
        if (r < 0 || c < 0 || c >= visibleCols.size() || r >= allEntries.size()) return "";
        return allEntries.get(r).getValue(visibleCols.get(c).key);
    }
}