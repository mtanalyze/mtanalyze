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
import com.mtanalyze.parser.MtFileIO;
import com.prowidesoftware.swift.model.mt.AbstractMT;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Handles all file-based import operations, delegating UI feedback to {@link ImportContext}.
 */
final class FileImporter {

    private static final String IMPORT_DIR_TITLE = "Import Directory";
    private static final String LOAD_LOG_TITLE   = "Loading Log File";

    private final ImportContext ctx;

    FileImporter(ImportContext ctx) {
        this.ctx = ctx;
    }

    // -----------------------------------------------------------------------
    // Public entry points
    // -----------------------------------------------------------------------

    void loadFile(File file) {
        if (ImportService.isLogFile(file)) {
            loadLogFileWithProgress(file,
                ctx.config().getLogSwiftStart(),
                ctx.config().getLogNewlineToken(),
                ctx.config().getMaxEntries());
            return;
        }
        try {
            String content = new String(Files.readAllBytes(file.toPath()));
            List<String> chunks;
            String mtOverride = null;
            MessageOrigin origin;
            if (MtFileIO.isCsvSwiftContent(content)) {
                origin = MessageOrigin.NAME_VALUE;
                chunks = MtFileIO.splitCsvIntoSwiftMessages(content);
            } else {
                origin = MessageOrigin.SWIFT_FILE;
                mtOverride = MtFileIO.tryDetectMtType(content);
                if (mtOverride == null && MtFileIO.needsMtTypeOverride(content))
                    mtOverride = ctx.promptMtType("Select the message type for: " + file.getName());
                chunks = MtFileIO.splitIntoMessages(content);
            }
            ImportBatch batch = ctx.importService().parseChunks(
                chunks, mtOverride, file.getAbsolutePath(), origin, ctx.config().getMaxEntries());
            ctx.onFileLoaded(batch, file);
        } catch (IOException ex) {
            ctx.fileError("loading", ex);
        }
    }

    void appendFile(File file) {
        try {
            String content = new String(Files.readAllBytes(file.toPath()));
            int parsed;
            if (ImportService.isLogFile(file)) {
                parsed = appendFromContent(
                    MtFileIO.splitLogIntoSwiftMessages(content,
                        ctx.config().getLogSwiftStart(), ctx.config().getLogNewlineToken()),
                    null, file.getAbsolutePath(), MessageOrigin.LOG_FILE);
            } else if (MtFileIO.isCsvSwiftContent(content)) {
                parsed = appendFromContent(
                    MtFileIO.splitCsvIntoSwiftMessages(content),
                    null, file.getAbsolutePath(), MessageOrigin.NAME_VALUE);
            } else {
                String mtOverride = MtFileIO.tryDetectMtType(content);
                if (mtOverride == null && MtFileIO.needsMtTypeOverride(content))
                    mtOverride = ctx.promptMtType("Select the message type for: " + file.getName());
                parsed = appendFromContent(
                    MtFileIO.splitIntoMessages(content),
                    mtOverride, file.getAbsolutePath(), MessageOrigin.SWIFT_FILE);
            }
            if (parsed > 0) ctx.onFileAppended(file);
        } catch (IOException ex) {
            ctx.fileError("appending", ex);
        }
    }

    void importDirectory(File dir) {
        File[] files = dir.listFiles(f ->
            f.isFile() && f.getName().matches("(?i).*\\.(txt|swift|mt5\\d{2}|mt9\\d{2}|ste|log)"));
        if (files == null || files.length == 0) {
            JOptionPane.showMessageDialog(ctx.frame(),
                "No SWIFT files found in:\n" + dir.getAbsolutePath(),
                IMPORT_DIR_TITLE, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        importFileBatch(files, dir);
    }

    /** Resets the model, sorts {@code files}, and imports them with a progress dialog. */
    void importFileBatch(File[] files, File sourceDir) {
        Arrays.sort(files, Comparator.comparing(File::getName));
        ctx.onNew();
        importDirectoryWithProgress(sourceDir, files, detectDirMtOverride(files));
    }

    int appendFromContent(List<String> chunks, String mtTypeOverride, String sourceFile,
                          MessageOrigin origin) {
        ImportBatch batch = ctx.importService().parseChunks(
            chunks, mtTypeOverride, sourceFile, origin, ctx.config().getMaxEntries());
        if (batch.totalParsed == 0) return 0;
        ctx.onContentAppended(batch);
        return batch.totalParsed;
    }

    int appendFromContent(String content, String mtTypeOverride) {
        return appendFromContent(
            MtFileIO.splitIntoMessages(content), mtTypeOverride, null, MessageOrigin.CLIPBOARD);
    }

    // -----------------------------------------------------------------------
    // Package-private static (used by frame's onFileLoaded)
    // -----------------------------------------------------------------------

    static String detectMtTypesLabel(List<AbstractMT> messages) {
        Set<String> types = new LinkedHashSet<>();
        for (AbstractMT mt : messages) {
            com.prowidesoftware.swift.model.SwiftBlock2 b2 = mt.getSwiftMessage().getBlock2();
            if (b2 != null) {
                String t = b2.getMessageType();
                if (t != null) types.add("MT" + t);
            }
        }
        return String.join("/", types);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void importDirectoryWithProgress(File dir, File[] files, String dirMtOverride) {
        String logSwiftStart   = ctx.config().getLogSwiftStart();
        String logNewlineToken = ctx.config().getLogNewlineToken();
        int    maxEntries      = ctx.config().getMaxEntries();

        JProgressBar bar = new JProgressBar(0, files.length);
        bar.setStringPainted(true);
        bar.setString("0 / " + files.length);

        FrameLayout.ProgressDialog pd = FrameLayout.buildProgressDialog(
            ctx.frame(), IMPORT_DIR_TITLE,
            "Importing " + files.length + " files from " + dir.getName() + "…", bar);

        SwingWorker<ImportBatch, Integer> worker = new SwingWorker<>() {
            @Override
            protected ImportBatch doInBackground() {
                ImportBatch batch = new ImportBatch();
                for (int i = 0; i < files.length; i++) {
                    if (isCancelled()) break;
                    ctx.importService().parseFileIntoBatch(
                        files[i], dirMtOverride, batch, logSwiftStart, logNewlineToken, maxEntries);
                    publish(i + 1);
                }
                return batch;
            }

            @Override
            protected void process(List<Integer> chunks) {
                int n = chunks.get(chunks.size() - 1);
                bar.setValue(n);
                bar.setString(n + " / " + files.length);
            }

            @Override
            protected void done() {
                pd.dialog().dispose();
                if (isCancelled()) { ctx.error("Import cancelled."); return; }
                try {
                    ctx.onDirectoryLoaded(get(), dir, files.length);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    ctx.error("Import interrupted.");
                } catch (ExecutionException ex) {
                    ctx.error("Import failed: " + ex.getMessage());
                }
            }
        };

        pd.runWorker(worker);
    }

    private void loadLogFileWithProgress(File file, String swiftStart, String newlineToken, int maxEntries) {
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setStringPainted(true);
        bar.setString("Scanning…");

        FrameLayout.ProgressDialog pd = FrameLayout.buildProgressDialog(
            ctx.frame(), LOAD_LOG_TITLE, "Loading " + file.getName() + "…", bar);

        SwingWorker<ImportBatch, Integer> worker = new SwingWorker<>() {
            @Override
            protected ImportBatch doInBackground() throws Exception {
                String content = new String(Files.readAllBytes(file.toPath()));
                List<String> chunks = MtFileIO.splitLogIntoSwiftMessages(content, swiftStart, newlineToken);
                publish(-chunks.size()); // negative = scanning done, total known
                ImportBatch batch = new ImportBatch();
                for (int i = 0; i < chunks.size(); i++) {
                    if (isCancelled() || batch.entryCount >= maxEntries) break;
                    ctx.importService().parseChunkIntoBatch(
                        chunks.get(i), null, batch, file.getAbsolutePath(), MessageOrigin.LOG_FILE, maxEntries);
                    publish(i + 1);
                }
                return batch;
            }

            @Override
            protected void process(List<Integer> vals) {
                int v = vals.get(vals.size() - 1);
                if (v <= 0) {
                    bar.setIndeterminate(false);
                    bar.setMaximum(Math.max(1, -v));
                    bar.setString("Parsing…");
                } else {
                    bar.setValue(v);
                    bar.setString("Parsing " + v + " of " + bar.getMaximum() + "…");
                }
            }

            @Override
            protected void done() {
                pd.dialog().dispose();
                if (isCancelled()) { ctx.error("Load cancelled."); return; }
                try {
                    ctx.onFileLoaded(get(), file);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    ctx.error("Load interrupted.");
                } catch (ExecutionException ex) {
                    ctx.fileError("loading", ex);
                }
            }
        };

        pd.runWorker(worker);
    }

    private String detectDirMtOverride(File[] files) {
        for (File f : files) {
            if (ImportService.isLogFile(f)) continue;
            try {
                String sample = new String(Files.readAllBytes(f.toPath()));
                if (MtFileIO.needsMtTypeOverride(sample)) {
                    String detected = MtFileIO.tryDetectMtType(sample);
                    return detected != null ? detected
                        : ctx.promptMtType("Select the message type for all files in this directory.");
                }
                return null;
            } catch (IOException ignored) {
                // skip unreadable file, try next
            }
        }
        return null;
    }
}