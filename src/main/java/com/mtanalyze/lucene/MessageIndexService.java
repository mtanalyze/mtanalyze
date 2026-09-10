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
package com.mtanalyze.lucene;

import com.mtanalyze.model.SwiftMessage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * Application-facing wrapper around {@link MtLucene}: turns the in-memory
 * {@link SwiftMessage} entries of a tab into Lucene documents ("Index Messages")
 * and runs a raw Lucene query string against the shared index ("Search
 * Messages").
 * <p>
 * The index is a single directory shared by every tab and every session.
 * Indexing is idempotent: each message carries a stable identity (message type +
 * sender + the sender's own reference), so re-indexing the same message replaces
 * its document rather than adding a duplicate. {@link #clearIndex()} empties the
 * index. The index directory and the search hit limit are configurable via
 * {@link #configure(String, int)}.
 */
public final class MessageIndexService {

    /** Default hit limit for a "Search Messages" query. */
    public static final int DEFAULT_MAX_HITS = MtLucene.DEFAULT_MAX_HITS;

    /** {@code ~/.mtanalyze/swift-index} — the default index location. */
    public static Path defaultIndexDir() {
        return Path.of(System.getProperty("user.home"), ".mtanalyze", MtLucene.DEFAULT_INDEX_DIR);
    }

    private Path indexDir = defaultIndexDir();
    private int maxHits = DEFAULT_MAX_HITS;

    public Path indexDir() {
        return indexDir;
    }

    public int maxHits() {
        return maxHits;
    }

    /**
     * Applies the user-configured settings.
     *
     * @param dir     index directory; blank falls back to {@link #defaultIndexDir()}
     * @param maxHits search hit limit; {@code <= 0} falls back to {@link #DEFAULT_MAX_HITS}
     */
    public void configure(String dir, int maxHits) {
        this.indexDir = (dir == null || dir.isBlank())
                ? defaultIndexDir() : Path.of(dir.trim());
        this.maxHits = maxHits > 0 ? maxHits : DEFAULT_MAX_HITS;
    }

    /**
     * Indexes every message in {@code messages} as its own Lucene document,
     * reporting progress and honouring a cancel request so it can run behind a
     * progress bar. Messages are added one by one and flushed in a single commit
     * at the end.
     *
     * @param messages    the tab's loaded messages
     * @param sourceLabel value stored in the {@code file_name} field (e.g. the tab title)
     * @param onProgress  called on the calling thread with the number of messages
     *                    processed so far (may be {@code null})
     * @param cancelled   polled between messages; indexing stops early when it
     *                    returns {@code true} (may be {@code null})
     * @return the number of messages actually indexed
     */
    public int indexMessages(List<SwiftMessage> messages, String sourceLabel,
                             IntConsumer onProgress, BooleanSupplier cancelled) throws IOException {
        String label = (sourceLabel == null || sourceLabel.isBlank()) ? "mt-entries" : sourceLabel;
        int total = messages.size();
        int processed = 0;
        int indexed = 0;
        try (MtLucene client = new MtLucene(indexDir, maxHits)) {
            client.ensureIndex();
            for (SwiftMessage msg : messages) {
                if (cancelled != null && cancelled.getAsBoolean()) {
                    break;
                }
                String fin = toFin(msg);
                if (fin != null && !fin.isBlank()) {
                    indexed += client.addRaw(label, fin.strip()).size();
                }
                processed++;
                if (onProgress != null && (processed % 25 == 0 || processed == total)) {
                    onProgress.accept(processed);
                }
            }
            client.commit();
        }
        return indexed;
    }

    /** Convenience overload without progress/cancel callbacks. */
    public int indexMessages(List<SwiftMessage> messages, String sourceLabel) throws IOException {
        return indexMessages(messages, sourceLabel, null, null);
    }

    /**
     * Runs a classic Lucene query string; default field is {@code raw_message}.
     * Returns at most {@link #maxHits()} hits.
     */
    public List<MtLucene.SwiftHit> search(String queryString) throws IOException {
        try (MtLucene client = new MtLucene(indexDir, maxHits)) {
            return client.searchByQueryString(queryString);
        }
    }

    /** Deletes every document in the index; returns how many were removed. */
    public long clearIndex() throws IOException {
        try (MtLucene client = new MtLucene(indexDir, maxHits)) {
            return client.deleteAll();
        }
    }

    /** Number of documents currently in the index (0 if it does not exist yet). */
    public long documentCount() throws IOException {
        try (MtLucene client = new MtLucene(indexDir, maxHits)) {
            return client.documentCount();
        }
    }

    /** Prowide's FIN serialization of the parsed message, or {@code null} if it fails. */
    private static String toFin(SwiftMessage msg) {
        try {
            return msg.raw().message();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
