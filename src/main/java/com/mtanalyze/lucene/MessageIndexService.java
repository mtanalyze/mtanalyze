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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * Application-facing wrapper around {@link MtLucene}: turns the in-memory
 * {@link SwiftMessage} entries of a tab into Lucene documents ("Index Messages")
 * and runs a raw Lucene query string against the shared index ("Search
 * Messages").
 * <p>
 * The index is a single directory shared by every tab and every session.
 * Indexing is idempotent: each message carries a content-hash identity, so
 * re-indexing the same message replaces its document rather than adding a
 * duplicate. {@link #clearIndex()} empties the index. The index directory and
 * the search hit limit are configurable via {@link #configure(String, int)}.
 */
public final class MessageIndexService {

    /** Default hit limit for a "Search Messages" query. */
    public static final int DEFAULT_MAX_HITS = MtLucene.DEFAULT_MAX_HITS;

    /** {@code ~/.mtanalyze/swift-index} — the default index location. */
    public static Path defaultIndexDir() {
        return Path.of(System.getProperty("user.home"), ".mtanalyze", MtLucene.DEFAULT_INDEX_DIR);
    }

    /** Directory name of the dedicated MT 536 transaction index (see {@link #defaultMt536IndexDir()}). */
    public static final String MT536_INDEX_DIR = "swift-index-mt536";

    /**
     * {@code ~/.mtanalyze/swift-index-mt536} — default location of the MT 536 transaction
     * index populated by "Index MT 536 Entries". Kept separate from the general-purpose
     * index ({@link #defaultIndexDir()}) because each document there is a single isolated
     * transaction (one {@code TRAN}/{@code TRANSDET} sequence) rather than a whole message.
     */
    public static Path defaultMt536IndexDir() {
        return Path.of(System.getProperty("user.home"), ".mtanalyze", MT536_INDEX_DIR);
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
     * Indexes already-serialized FIN message texts directly, e.g. the single-transaction
     * messages produced by {@code EntryPanelModel.buildIsolatedMessageText} for "Index MT
     * 536 Entries" -- skipping the {@link SwiftMessage} round-trip {@link #indexMessages}
     * needs. Otherwise behaves exactly like {@link #indexMessages}: progress/cancel
     * callbacks, one commit at the end, and idempotent re-indexing by content hash.
     */
    public int indexRawTexts(List<String> finTexts, String sourceLabel,
                             IntConsumer onProgress, BooleanSupplier cancelled) throws IOException {
        String label = (sourceLabel == null || sourceLabel.isBlank()) ? "mt-entries" : sourceLabel;
        int total = finTexts.size();
        int processed = 0;
        int indexed = 0;
        try (MtLucene client = new MtLucene(indexDir, maxHits)) {
            client.ensureIndex();
            for (String fin : finTexts) {
                if (cancelled != null && cancelled.getAsBoolean()) {
                    break;
                }
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

    /**
     * Runs a classic Lucene query string; default field is {@code raw_message}.
     * Returns at most {@link #maxHits()} hits.
     */
    public List<MtLucene.SwiftHit> search(String queryString) throws IOException {
        try (MtLucene client = new MtLucene(indexDir, maxHits)) {
            return client.searchByQueryString(queryString);
        }
    }

    /**
     * Structured search for the "Search MT 536 Entries" mask: an optional Logical Terminal
     * (BIC, sender OR receiver, prefix match), any given tag values (e.g.
     * {@code {"20C":"SEME123", "35B":"US0378331005"}}) and an optional free-text Lucene
     * query (classic {@link org.apache.lucene.queryparser.classic.QueryParser} syntax, same
     * as the "Search Messages" query box) -- all ANDed together, restricted to MT 536. At
     * least one of {@code lt} / {@code tagValues} / {@code extraQuery} must carry a
     * non-blank criterion, otherwise the result is empty (see {@link MtLucene#search}).
     *
     * @throws IllegalArgumentException if {@code extraQuery} cannot be parsed
     */
    public List<MtLucene.SwiftHit> searchMt536(String lt, Map<String, String> tagValues, String extraQuery)
            throws IOException {
        try (MtLucene client = new MtLucene(indexDir, maxHits)) {
            return client.search(lt, List.of("536"), tagValues, extraQuery);
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

    /**
     * Of {@code messages}, how many already have a document in the index -- identified by
     * the same content hash used for indexing (see {@link MtLucene#contentId}), so a message
     * counts as "already indexed" no matter which tab or file it was originally loaded from.
     */
    public int countAlreadyIndexed(List<SwiftMessage> messages) throws IOException {
        Set<String> contentIds = new HashSet<>();
        for (SwiftMessage msg : messages) {
            String fin = toFin(msg);
            if (fin != null && !fin.isBlank()) {
                contentIds.add(MtLucene.contentId(fin.strip()));
            }
        }
        try (MtLucene client = new MtLucene(indexDir, maxHits)) {
            return client.existingContentIds(contentIds).size();
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
