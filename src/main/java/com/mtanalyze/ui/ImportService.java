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

import com.mtanalyze.model.MessageOrigin;
import com.mtanalyze.model.SwiftMessage;
import com.mtanalyze.parser.MtFileIO;
import com.prowidesoftware.swift.model.mt.AbstractMT;

import java.io.File;
import java.io.IOException;
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
            AbstractMT mt = AbstractMT.parse(MtFileIO.wrapBlock4IfNeeded(chunk, mtOverride));
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
}