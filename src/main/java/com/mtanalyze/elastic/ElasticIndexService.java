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
package com.mtanalyze.elastic;

import com.mtanalyze.model.SwiftMessage;

import java.io.IOException;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * Application-facing wrapper around {@link MtElastic}: turns the in-memory
 * {@link SwiftMessage} entries of a tab into Elasticsearch documents ("Index Messages")
 * and runs a {@code query_string} search against the configured cluster ("Search
 * Messages"). Mirrors {@code com.mtanalyze.lucene.MessageIndexService}'s shape so the
 * Lucene and Elasticsearch menus behave the same way.
 * <p>
 * Unlike the embedded Lucene index, this talks to a real Elasticsearch cluster: host,
 * port, scheme, credentials and index name are configurable via
 * {@link #configure(String, int, String, String, String, String, int)}. Indexing is
 * idempotent: each message carries a content-hash identity, so re-indexing the same
 * message replaces its document rather than adding a duplicate.
 */
public final class ElasticIndexService {

    /** Default hit limit for a "Search Messages" query. */
    public static final int DEFAULT_MAX_HITS = MtElastic.DEFAULT_MAX_HITS;

    public static final String DEFAULT_HOST   = "localhost";
    public static final int    DEFAULT_PORT   = 9200;
    public static final String DEFAULT_SCHEME = "http";
    public static final String DEFAULT_INDEX  = MtElastic.DEFAULT_INDEX;

    private String host     = DEFAULT_HOST;
    private int    port     = DEFAULT_PORT;
    private String scheme   = DEFAULT_SCHEME;
    private String username = "";
    private String password = "";
    private String index    = DEFAULT_INDEX;
    private int    maxHits  = DEFAULT_MAX_HITS;

    public String host()  { return host; }
    public int    port()  { return port; }
    public String index() { return index; }
    public int    maxHits() { return maxHits; }

    /** A short {@code scheme://host:port} label for status messages and confirmations. */
    public String connectionLabel() {
        return scheme + "://" + host + ":" + port + " (index \"" + index + "\")";
    }

    /**
     * Applies the user-configured connection settings.
     *
     * @param host     cluster host; blank falls back to {@link #DEFAULT_HOST}
     * @param port     cluster port; {@code <= 0} falls back to {@link #DEFAULT_PORT}
     * @param scheme   {@code http} or {@code https}; blank falls back to {@link #DEFAULT_SCHEME}
     * @param username basic-auth user; blank disables basic auth
     * @param password basic-auth password
     * @param index    index name; blank falls back to {@link #DEFAULT_INDEX}
     * @param maxHits  search hit limit; {@code <= 0} falls back to {@link #DEFAULT_MAX_HITS}
     */
    public void configure(String host, int port, String scheme, String username, String password,
                          String index, int maxHits) {
        this.host     = (host == null || host.isBlank()) ? DEFAULT_HOST : host.trim();
        this.port     = port > 0 ? port : DEFAULT_PORT;
        this.scheme   = (scheme == null || scheme.isBlank()) ? DEFAULT_SCHEME : scheme.trim();
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        this.index    = (index == null || index.isBlank()) ? DEFAULT_INDEX : index.trim();
        this.maxHits  = maxHits > 0 ? maxHits : DEFAULT_MAX_HITS;
    }

    /**
     * Indexes every message in {@code messages} as its own Elasticsearch document, reporting
     * progress and honouring a cancel request so it can run behind a progress bar.
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
        try (MtElastic client = open()) {
            client.ensureIndex();
            for (SwiftMessage msg : messages) {
                if (cancelled != null && cancelled.getAsBoolean()) {
                    break;
                }
                String fin = toFin(msg);
                if (fin != null && !fin.isBlank()) {
                    client.uploadRaw(label, fin.strip());
                    indexed++;
                }
                processed++;
                if (onProgress != null && (processed % 25 == 0 || processed == total)) {
                    onProgress.accept(processed);
                }
            }
        }
        return indexed;
    }

    /**
     * Runs a {@code query_string} search; default field is {@code raw_message}.
     * Returns at most {@link #maxHits()} hits.
     */
    public List<MtElastic.SwiftHit> search(String queryString) throws IOException {
        try (MtElastic client = open()) {
            return client.searchByQueryString(queryString);
        }
    }

    /** Deletes every document in the index; returns how many were removed. */
    public long clearIndex() throws IOException {
        try (MtElastic client = open()) {
            return client.deleteAll();
        }
    }

    /** Number of documents currently in the index (0 if it does not exist yet). */
    public long documentCount() throws IOException {
        try (MtElastic client = open()) {
            return client.documentCount();
        }
    }

    private MtElastic open() {
        return new MtElastic(host, port, scheme, username, password, index, maxHits);
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
