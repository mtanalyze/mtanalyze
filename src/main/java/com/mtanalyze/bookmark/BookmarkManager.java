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
package com.mtanalyze.bookmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;

public final class BookmarkManager {

    // Tab separates fields; newline separates records.
    // Neither appears in ISINs, SWIFT refs, or OS file paths.
    private static final String FS = "\t";
    private static final String RS = "\n";

    private final Preferences prefs;
    private final String prefKey;
    private final List<Bookmark> items = new ArrayList<>();

    public BookmarkManager(Preferences prefs, String prefKey) {
        this.prefs   = prefs;
        this.prefKey = prefKey;
        load();
    }

    public List<Bookmark> items() { return Collections.unmodifiableList(items); }

    public void add(Bookmark b) {
        items.add(b);
        save();
    }

    public void update(int index, Bookmark b) {
        if (index >= 0 && index < items.size()) {
            items.set(index, b);
            save();
        }
    }

    public void remove(int index) {
        if (index >= 0 && index < items.size()) {
            items.remove(index);
            save();
        }
    }

    private void load() {
        items.clear();
        String raw = prefs.get(prefKey, "");
        if (raw.isEmpty()) return;
        for (String rec : raw.split(RS, -1)) {
            String[] f = rec.split(FS, -1);
            if (f.length >= 4) {
                String note = f.length >= 5 ? f[4] : "";
                String ts   = f.length >= 6 ? f[5] : "";
                items.add(new Bookmark(f[0], f[1], f[2], f[3], note, ts));
            }
        }
    }

    private void save() {
        StringBuilder sb = new StringBuilder();
        for (Bookmark b : items) {
            if (!sb.isEmpty()) sb.append(RS);
            sb.append(b.isin()).append(FS)
              .append(b.seme()).append(FS)
              .append(b.rela()).append(FS)
              .append(b.filePath()).append(FS)
              .append(b.note()).append(FS)
              .append(b.timestamp());
        }
        prefs.put(prefKey, sb.toString());
    }
}