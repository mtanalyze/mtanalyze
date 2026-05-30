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

import com.mtanalyze.parser.Lookups;

public class ColumnDef {
    /** Unique key: seqLabel \t tagName \t qualifier \t occurrence */
    public final String key;
    public final String seqLabel;  // SWIFT sequence label, e.g. "B1a", "B1a2a"
    public final String tagName;   // SWIFT tag, e.g. "35B", "20C"
    public final String qualifier; // Qualifier value, or "" if none
    public final String label;     // Column header
    private boolean visible;

    public ColumnDef(String seqLabel, String tagName, String qualifier, int occurrence,
              String label) {
        this.seqLabel  = seqLabel;
        this.tagName   = tagName;
        this.qualifier = qualifier;
        this.label     = label;
        this.key       = seqLabel + "\t" + tagName + "\t" + qualifier + "\t" + occurrence;
        this.visible   = Lookups.DEFAULT_VISIBLE;
    }

    public boolean isVisible() { return visible; }

    public void setVisible(boolean visible) { this.visible = visible; }
}