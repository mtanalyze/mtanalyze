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
package com.mtanalyze.export;

import com.mtanalyze.model.Entry;
import com.mtanalyze.model.Project;
import com.mtanalyze.model.SwiftMessage;
import com.prowidesoftware.swift.model.SwiftBlock2;
import com.prowidesoftware.swift.model.SwiftTagListBlock;
import com.prowidesoftware.swift.model.Tag;
import com.prowidesoftware.swift.model.mt.AbstractMT;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializes a {@link Project} to the {@code .mtd} session file format and back.
 *
 * <p>The .mtd format is a concatenation of SWIFT messages with synthetic block-1/2
 * headers so the file can be re-loaded by the standard parser pipeline.
 * Reading a .mtd file needs no special handling — the existing multi-message
 * splitter in {@code MtFileIO} covers it.
 *
 * <p>User notes ({@link Entry#NOTE_COL_KEY}) are persisted as a real SWIFT field
 * {@code :70E::NOTE//text} inside each entry's sequence block. On load, these tags
 * are stripped before the standard parser runs and the note text is restored into
 * {@code Entry.data()}.
 */
public final class ProjectIO {

    public static final String SESSION_EXTENSION = "mtd";

    /** SWIFT tag used to persist the user note: field 70E with qualifier NOTE. */
    public static final String NOTE_TAG_NAME         = "70E";
    /** Value prefix that identifies a persisted note: qualifier + subfield delimiter. */
    public static final String NOTE_TAG_VALUE_PREFIX = ":NOTE//";

    private ProjectIO() {}

    /**
     * Writes all messages in {@code project} to {@code file} in .mtd format.
     *
     * @throws IllegalArgumentException if the project has no messages
     * @throws IOException              if the file cannot be written
     */
    public static void save(Project project, File file) throws IOException {
        if (project.isEmpty()) throw new IllegalArgumentException("Project has no messages to save.");
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (SwiftMessage msg : project.messages()) {
            if (!first) sb.append("\r\n");
            sb.append(toSessionText(msg));
            first = false;
        }
        Files.writeString(file.toPath(), sb.toString());
    }

    private static String toSessionText(SwiftMessage msg) {
        String mtType = resolveMtType(msg.raw());
        StringBuilder sb = new StringBuilder();
        sb.append("{1:F01AAAAAAAA0000000000}\r\n");
        sb.append("{2:I").append(mtType).append("AAAAAAAA0000U3003}\r\n");
        sb.append("{4:\r\n");
        writeBlock4WithNotes(sb, msg);
        sb.append("-}");
        return sb.toString();
    }

    /**
     * Writes Block 4 tags, injecting a {@code :70E::NOTE//text} field for each entry
     * that has a non-empty note.
     *
     * <p>For entries bounded by a {@code 16S} tag the note is written immediately
     * before the {@code 16S} (inside the block). For single-entry messages without
     * {@code 16R}/{@code 16S} the note is appended after the last tag.
     */
    private static void writeBlock4WithNotes(StringBuilder sb, SwiftMessage msg) {
        Map<Tag, String> noteBefore = new IdentityHashMap<>();
        Map<Tag, String> noteAfter  = new IdentityHashMap<>();

        for (Entry entry : msg.entries())
            collectNoteMarkers(entry, noteBefore, noteAfter);

        SwiftTagListBlock b4 = msg.raw().getSwiftMessage().getBlock4();
        if (b4 == null) return;
        for (Tag tag : b4.getTags()) {
            if (isNoteTag(tag)) continue; // skip stale note tags from prior load
            String before = noteBefore.get(tag);
            if (before != null) writeTag(sb, NOTE_TAG_NAME, NOTE_TAG_VALUE_PREFIX + escapeNote(before));
            writeTag(sb, tag.getName(), tag.getValue());
            String after = noteAfter.get(tag);
            if (after != null) writeTag(sb, NOTE_TAG_NAME, NOTE_TAG_VALUE_PREFIX + escapeNote(after));
        }
    }

    private static void collectNoteMarkers(Entry entry, Map<Tag, String> noteBefore, Map<Tag, String> noteAfter) {
        String note = entry.getValue(Entry.NOTE_COL_KEY);
        if (note.isEmpty()) return;
        List<Tag> seqTags = entry.sequence().getTags();
        if (seqTags.isEmpty()) return;
        Tag lastTag = seqTags.get(seqTags.size() - 1);
        if ("16S".equals(lastTag.getName()))
            noteBefore.put(lastTag, note);
        else
            noteAfter.put(lastTag, note);
    }

    /** True when {@code tag} is one of our persisted note markers (not a real narrative field). */
    public static boolean isNoteTag(Tag tag) {
        return NOTE_TAG_NAME.equals(tag.getName())
                && tag.getValue() != null
                && tag.getValue().startsWith(NOTE_TAG_VALUE_PREFIX);
    }

    private static void writeTag(StringBuilder sb, String name, String value) {
        sb.append(":").append(name).append(":");
        if (value != null) sb.append(value);
        sb.append("\r\n");
    }

    /** Collapses embedded newlines so the note stays on one SWIFT field line. */
    private static String escapeNote(String note) {
        return note.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
    }

    private static String resolveMtType(AbstractMT mt) {
        try {
            SwiftBlock2 b2 = mt.getSwiftMessage().getBlock2();
            if (b2 != null && b2.getMessageType() != null && !b2.getMessageType().isEmpty())
                return b2.getMessageType();
        } catch (Exception ignored) { /* fall through */ }
        return "536";
    }
}
