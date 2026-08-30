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

import com.mtanalyze.model.MessageOrigin;
import com.mtanalyze.model.SwiftMessage;
import com.mtanalyze.parser.MtFileIO;
import com.mtanalyze.parser.NameValueConverter;
import com.prowidesoftware.swift.model.mt.AbstractMT;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

final class ImportService {

    ImportService() {}

    static boolean isLogFile(File file) {
        return file.getName().toLowerCase(Locale.ROOT).endsWith(".log");
    }

    void parseFileIntoBatch(File file, String mtOverride, ImportBatch batch,
                            String logSwiftStart, String logNewlineToken, int maxEntries) {
        if (!isLogFile(file) && MtFileIO.isQuotedCsvSwiftFile(file)) {
            parseCsvFileStreaming(file, mtOverride, batch, maxEntries);
            return;
        }
        try {
            String content = new String(Files.readAllBytes(file.toPath()));
            List<String> chunks;
            MessageOrigin origin;
            if (isLogFile(file)) {
                chunks = MtFileIO.splitLogIntoSwiftMessages(content, logSwiftStart, logNewlineToken);
                origin = MessageOrigin.LOG_FILE;
            } else if (MtFileIO.isCsvSwiftContent(content)) {
                chunks = MtFileIO.splitCsvIntoSwiftMessages(content);
                origin = MessageOrigin.NAME_VALUE;
            } else {
                chunks = MtFileIO.splitIntoMessages(content);
                origin = MessageOrigin.SWIFT_FILE;
            }
            for (String chunk : chunks) {
                if (batch.entryCount >= maxEntries) break;
                parseChunkIntoBatch(chunk, mtOverride, batch, file.getAbsolutePath(), origin, maxEntries);
            }
        } catch (IOException ex) {
            batch.errors++;
        }
    }

    /**
     * Streams a single-column CSV file cell by cell instead of loading the whole file
     * into memory and materialising every message into a {@code List<String>} first —
     * needed for directory imports containing CSV exports with tens of thousands of rows.
     */
    private void parseCsvFileStreaming(File file, String mtOverride, ImportBatch batch, int maxEntries) {
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            MtFileIO.streamCsvMessages(reader, chunk -> {
                if (batch.entryCount >= maxEntries) return;
                parseChunkIntoBatch(chunk, mtOverride, batch, file.getAbsolutePath(), MessageOrigin.NAME_VALUE, maxEntries);
            });
        } catch (IOException ex) {
            batch.errors++;
        }
    }

    ImportBatch parseChunks(List<String> chunks, String mtTypeOverride, String sourceFile,
                            MessageOrigin origin, int maxEntries) {
        return parseChunks(chunks, mtTypeOverride, sourceFile, origin, maxEntries, java.util.Collections.emptySet());
    }

    ImportBatch parseChunks(List<String> chunks, String mtTypeOverride, String sourceFile,
                            MessageOrigin origin, int maxEntries, java.util.Set<String> mtTypeFilter) {
        ImportBatch batch = new ImportBatch();
        batch.mtTypeFilter.addAll(mtTypeFilter);
        for (String chunk : chunks) {
            parseChunkIntoBatch(chunk, mtTypeOverride, batch, sourceFile, origin, maxEntries);
        }
        return batch;
    }

    void parseChunkIntoBatch(String chunk, String mtOverride, ImportBatch batch,
                             String sourceFile, MessageOrigin origin, int maxEntries) {
        if (batch.entryCount >= maxEntries) { batch.limitReached = true; return; }
        try {
            AbstractMT mt = parseWithTruncationRecovery(chunk, mtOverride);
            if (mt == null) return;
            if (!batch.mtTypeFilter.isEmpty()) {
                com.prowidesoftware.swift.model.SwiftBlock2 b2 = mt.getSwiftMessage().getBlock2();
                String type = b2 != null ? b2.getMessageType() : null;
                if (type == null || !batch.mtTypeFilter.contains(type)) return;
            }
            SwiftMessage msg = new SwiftMessage(mt, sourceFile != null ? new File(sourceFile) : null, origin);
            batch.messages.add(msg);
            batch.entryCount += EntryPanelModel.parseAndDecorate(msg, batch.knownKeys, batch.columnDefs).size();
            batch.totalParsed++;
        } catch (Exception ex) {
            batch.errors++;
            String msg = ex.getMessage();
            if (msg != null && !msg.isBlank())
                batch.prowideLog.add("[SEVERE ] " + msg);
        }
    }

    /**
     * Parses a chunk, retrying with the last (incomplete) block4 line dropped each
     * time Prowide rejects it. A message cut off mid-tag would otherwise fail to
     * parse even after {@link MtFileIO#repairTruncated}, discarding the entire
     * chunk instead of the handful of well-formed tags before the broken one.
     *
     * <p>A Name-Value line using bare sequence codes (no explicit {@code :16R:}/
     * {@code :16S:} markers) is instead built directly via {@link NameValueConverter},
     * which already produces a typed {@link AbstractMT} - serializing it to text here
     * only to have Prowide re-parse it back into the same object would be redundant
     * and risks losing structure that {@link AbstractMT#parse} cannot always recover.
     */
    private static AbstractMT parseWithTruncationRecovery(String chunk, String mtOverride) throws Exception {
        if (NameValueConverter.isSequenceCodeFormat(chunk)) {
            return new NameValueConverter().convert(chunk);
        }
        String candidate = MtFileIO.wrapBlock4IfNeeded(chunk, mtOverride);
        Exception firstFailure = null;
        while (true) {
            try {
                return AbstractMT.parse(candidate);
            } catch (Exception ex) {
                if (firstFailure == null) firstFailure = ex;
                String shorter = MtFileIO.dropLastBlock4Line(candidate);
                if (shorter == null) throw firstFailure;
                candidate = shorter;
            }
        }
    }
}