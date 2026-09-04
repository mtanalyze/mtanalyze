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
    private static final String LOAD_CSV_TITLE   = "Loading CSV File";
    private static final String LOADING          = "loading";

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
                ctx.config().getLogNewlineToken());
            return;
        }
        if (MtFileIO.isQuotedCsvSwiftFile(file)) {
            loadCsvFileWithProgress(file);
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
                if (!MtFileIO.isNameValueContent(content)) {
                    mtOverride = MtFileIO.tryDetectMtType(content);
                    if (mtOverride == null && MtFileIO.needsMtTypeOverride(content))
                        mtOverride = ctx.promptMtType("Select the message type for: " + file.getName());
                }
                chunks = MtFileIO.splitIntoMessages(content);
            }
            ImportBatch batch;
            try (ProwideLogCapture cap = ProwideLogCapture.start()) {
                batch = ctx.importService().parseChunks(
                    chunks, mtOverride, file.getAbsolutePath(), origin, ctx.config().getMaxEntries());
                batch.prowideLog.addAll(cap.stop());
            }
            ctx.onFileLoaded(batch, file);
        } catch (IOException ex) {
            ctx.fileError(LOADING, ex);
        }
    }

    void appendFile(File file) {
        if (!ImportService.isLogFile(file) && MtFileIO.isQuotedCsvSwiftFile(file)) {
            appendCsvFileStreaming(file);
            return;
        }
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
                String mtOverride = null;
                if (!MtFileIO.isNameValueContent(content)) {
                    mtOverride = MtFileIO.tryDetectMtType(content);
                    if (mtOverride == null && MtFileIO.needsMtTypeOverride(content))
                        mtOverride = ctx.promptMtType("Select the message type for: " + file.getName());
                }
                parsed = appendFromContent(
                    MtFileIO.splitIntoMessages(content),
                    mtOverride, file.getAbsolutePath(), MessageOrigin.SWIFT_FILE);
            }
            if (parsed > 0) ctx.onFileAppended(file);
        } catch (IOException ex) {
            ctx.fileError("appending", ex);
        }
    }

    /**
     * Streams a large single-column CSV file cell by cell instead of loading the whole
     * file into memory, so directories/files with tens of thousands of rows don't blow
     * up heap usage the way materialising every message into a {@code List<String>} first
     * would.
     */
    private void appendCsvFileStreaming(File file) {
        ImportBatch batch = new ImportBatch();
        int maxEntries = ctx.config().getMaxEntries();
        try (ProwideLogCapture cap = ProwideLogCapture.start();
             java.io.BufferedReader reader = Files.newBufferedReader(
                     file.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
            MtFileIO.streamCsvMessages(reader, chunk -> {
                if (batch.entryCount >= maxEntries) return;
                ctx.importService().parseChunkIntoBatch(
                    chunk, null, batch, file.getAbsolutePath(), MessageOrigin.NAME_VALUE, maxEntries);
            });
            batch.prowideLog.addAll(cap.stop());
        } catch (IOException ex) {
            ctx.fileError("appending", ex);
            return;
        }
        if (finalizeAppend(batch) > 0) ctx.onFileAppended(file);
    }

    void importDirectory(File dir) {
        // Directory import expects one message per file; a CSV export bundles many
        // messages in a single file (possibly tens of thousands) and is imported
        // separately via "Open File" / "Append File" instead, so it's excluded here
        // even when its extension (e.g. .txt) would otherwise match.
        File[] files = dir.listFiles(f ->
            f.isFile() && f.getName().matches("(?i).*\\.(txt|swift|mt5\\d{2}|mt9\\d{2}|ste|log)")
                && !MtFileIO.isCsvSwiftFile(f));
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
        return appendFromContent(chunks, mtTypeOverride, sourceFile, origin, Collections.emptySet());
    }

    int appendFromContent(List<String> chunks, String mtTypeOverride, String sourceFile,
                          MessageOrigin origin, Set<String> mtTypeFilter) {
        ImportBatch batch;
        try (ProwideLogCapture cap = ProwideLogCapture.start()) {
            batch = ctx.importService().parseChunks(
                chunks, mtTypeOverride, sourceFile, origin, ctx.config().getMaxEntries(), mtTypeFilter);
            batch.prowideLog.addAll(cap.stop());
        }
        return finalizeAppend(batch);
    }

    private int finalizeAppend(ImportBatch batch) {
        if (batch.totalParsed > 0) {
            ctx.onContentAppended(batch);
            return batch.totalParsed;
        }
        if (batch.errors > 0 || !batch.prowideLog.isEmpty()) {
            ctx.onContentAppended(batch);
            return -1;  // signals AppendTextDialog to close and show notifications
        }
        return 0;  // truly empty input – keep dialog open for retry
    }

    int appendFromContent(String content, String mtTypeOverride) {
        return appendFromContent(
            MtFileIO.splitIntoMessages(content), mtTypeOverride, null, MessageOrigin.CLIPBOARD);
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
                try (ProwideLogCapture cap = ProwideLogCapture.start()) {
                    for (int i = 0; i < files.length; i++) {
                        if (isCancelled()) break;
                        ctx.importService().parseFileIntoBatch(
                            files[i], dirMtOverride, batch, logSwiftStart, logNewlineToken, maxEntries);
                        publish(i + 1);
                    }
                    batch.prowideLog.addAll(cap.stop());
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

    private abstract class FileLoadWorker extends SwingWorker<ImportBatch, Integer> {
        protected final int maxEntries = ctx.config().getMaxEntries();
        private final File file;
        private final JProgressBar bar;
        private final FrameLayout.ProgressDialog pd;

        FileLoadWorker(File file, String title) {
            this.file = file;
            this.bar  = new JProgressBar();
            bar.setIndeterminate(true);
            bar.setStringPainted(true);
            bar.setString("Scanning…");
            this.pd = FrameLayout.buildProgressDialog(
                ctx.frame(), title, "Loading " + file.getName() + "…", bar);
        }

        @Override
        protected final void process(List<Integer> vals) {
            bar.setString("Parsed " + vals.get(vals.size() - 1) + " messages…");
        }

        @Override
        protected final void done() {
            pd.dialog().dispose();
            if (isCancelled()) { ctx.error("Load cancelled."); return; }
            try {
                ctx.onFileLoaded(get(), file);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                ctx.error("Load interrupted.");
            } catch (ExecutionException ex) {
                ctx.fileError(LOADING, ex);
            }
        }

        void start() { pd.runWorker(this); }
    }

    private void loadLogFileWithProgress(File file, String swiftStart, String newlineToken) {
        Optional<Set<String>> filterOpt = ctx.promptMtTypeFilter(file.getName());
        if (filterOpt.isEmpty()) return;
        Set<String> mtTypeFilter = filterOpt.get();

        new FileLoadWorker(file, LOAD_LOG_TITLE) {
            @Override
            protected ImportBatch doInBackground() throws Exception {
                ImportBatch batch = new ImportBatch();
                batch.mtTypeFilter.addAll(mtTypeFilter);
                try (ProwideLogCapture cap = ProwideLogCapture.start();
                     java.io.BufferedReader reader = Files.newBufferedReader(
                             file.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                    MtFileIO.streamLogMessages(reader, swiftStart, newlineToken, chunk -> {
                        if (isCancelled() || batch.entryCount >= maxEntries) return;
                        ctx.importService().parseChunkIntoBatch(
                            chunk, null, batch, file.getAbsolutePath(), MessageOrigin.LOG_FILE, maxEntries);
                        publish(batch.totalParsed);
                    });
                    batch.prowideLog.addAll(cap.stop());
                }
                return batch;
            }
        }.start();
    }

    private void loadCsvFileWithProgress(File file) {
        new FileLoadWorker(file, LOAD_CSV_TITLE) {
            @Override
            protected ImportBatch doInBackground() throws Exception {
                ImportBatch batch = new ImportBatch();
                try (ProwideLogCapture cap = ProwideLogCapture.start();
                     java.io.BufferedReader reader = Files.newBufferedReader(
                             file.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                    MtFileIO.streamCsvMessages(reader, chunk -> {
                        if (isCancelled() || batch.entryCount >= maxEntries) return;
                        ctx.importService().parseChunkIntoBatch(
                            chunk, null, batch, file.getAbsolutePath(),
                            MessageOrigin.NAME_VALUE, maxEntries);
                        publish(batch.totalParsed);
                    });
                    batch.prowideLog.addAll(cap.stop());
                }
                return batch;
            }
        }.start();
    }

    private String detectDirMtOverride(File[] files) {
        for (File f : files) {
            // Log files and quoted-CSV exports never need an MT type override (each row/message
            // already carries its own {1:...} header) — skip them so a large CSV export isn't
            // read fully into memory just to determine this.
            if (ImportService.isLogFile(f) || MtFileIO.isQuotedCsvSwiftFile(f)) continue;
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