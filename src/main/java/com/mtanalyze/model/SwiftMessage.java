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
package com.mtanalyze.model;

import com.prowidesoftware.swift.model.mt.AbstractMT;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A parsed SWIFT message with its provenance metadata.
 * {@code persistedFile} starts as {@code sourceFile} for {@link MessageOrigin#SWIFT_FILE}
 * origins and {@code null} otherwise.  It can be set later when the user exports the message
 * to a SWIFT file, which is a prerequisite for bookmarking.
 */
public class SwiftMessage {

    private final AbstractMT    raw;
    private final File          sourceFile;
    private final List<Entry>   entries = new ArrayList<>();

    public SwiftMessage(AbstractMT raw, File sourceFile) {
        this.raw          = raw;
        this.sourceFile   = sourceFile;
    }

    public AbstractMT    raw()          { return raw; }
    public File          sourceFile()   { return sourceFile; }

    public List<Entry> entries()         { return Collections.unmodifiableList(entries); }
    public void addEntry(Entry e)        { entries.add(e); }
    public void removeEntry(Entry e)     { entries.remove(e); }

    /** MT type string, e.g. {@code "MT536"}, or empty if the block 2 is absent. */
    public String mtType() {
        com.prowidesoftware.swift.model.SwiftBlock2 b2 = raw.getSwiftMessage().getBlock2();
        if (b2 == null) return "";
        String t = b2.getMessageType();
        return t != null ? "MT" + t : "";
    }
}