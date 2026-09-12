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

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Wraps the connection, upload and search for SWIFT MT files in Elasticsearch.
 * <p>
 * Unlike the embedded Lucene index there is a real server: {@code host}/{@code port} point
 * at a running Elasticsearch cluster (local or remote), configured by the user.
 * <p>
 * Each document contains:
 * <ul>
 *  <li>{@code file_name}    : original file name / tab title</li>
 *  <li>{@code raw_message}  : the complete, unmodified message text</li>
 *  <li>{@code mt}           : e.g. "536" for MT 536 (if detectable)</li>
 *  <li>{@code sender}       : Logical Terminal (LT address) of the sender, e.g. "BANKUS33AXXX"</li>
 *  <li>{@code receiver}     : Logical Terminal (LT address) of the receiver, e.g. "BANKBEBBAXXX"</li>
 *  <li>{@code tag}          : map of all detected SWIFT tags (20C, 23G, 35B, 97A, ...),
 *      each holding the list of its occurrences in the message</li>
 *  <li>{@code indexed_at}   : upload timestamp</li>
 * </ul>
 * As a result every search -- whether by tag or full text -- returns the complete
 * original document (including {@code raw_message}) as a hit.
 * <p>
 * Ported from the standalone {@code mtelastic} command-line tool. Divergence from mtelastic:
 * the document id is a content hash (see {@link #contentId(String)}, same technique as
 * {@code com.mtanalyze.lucene.MtLucene}) so re-indexing the same message replaces its
 * document instead of adding a duplicate, and this class gained a {@code maxHits}
 * constructor parameter and {@link #documentCount()}.
 */
public class MtElastic implements AutoCloseable {

    public static final String DEFAULT_INDEX = "swift-messages";

    /** Default upper bound on the number of hits a search returns. */
    public static final int DEFAULT_MAX_HITS = 100;

    private static final String F_FILE_NAME = "file_name";
    private static final String F_RAW_MESSAGE = "raw_message";
    private static final String F_MESSAGE_TYPE = "mt";
    private static final String F_SENDER_LT = "sender";
    private static final String F_RECEIVER_LT = "receiver";
    private static final String F_INDEXED_AT = "indexed_at";
    private static final String F_TAGS = "tag";
    private static final String TAGS_PREFIX = F_TAGS + ".";

    private final RestClient restClient;
    private final ElasticsearchClient client;
    private final String index;
    private final int maxHits;

    public MtElastic(String host, int port, String scheme, String username, String password, String index) {
        this(host, port, scheme, username, password, index, DEFAULT_MAX_HITS);
    }

    public MtElastic(String host, int port, String scheme, String username, String password,
                      String index, int maxHits) {
        this.index = index;
        this.maxHits = maxHits > 0 ? maxHits : DEFAULT_MAX_HITS;

        HttpHost httpHost = new HttpHost(host, port, scheme);
        var builder = RestClient.builder(httpHost);

        if (username != null && !username.isBlank()) {
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(username, password));
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        }

        this.restClient = builder.build();
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        this.client = new ElasticsearchClient(transport);
    }

    /** Creates the index if it does not exist yet. */
    public void ensureIndex() throws IOException {
        boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(index))).value();
        if (exists) {
            return;
        }

        client.indices().create(CreateIndexRequest.of(c -> c
                .index(index)
                .mappings(m -> m
                        .properties(F_FILE_NAME, p -> p.keyword(k -> k))
                        .properties(F_RAW_MESSAGE, p -> p.text(t -> t))
                        .properties(F_MESSAGE_TYPE, p -> p.keyword(k -> k))
                        .properties(F_SENDER_LT, p -> p.keyword(k -> k))
                        .properties(F_RECEIVER_LT, p -> p.keyword(k -> k))
                        .properties(F_INDEXED_AT, p -> p.date(d -> d))
                        .properties(F_TAGS, p -> p.object(o -> o.enabled(true)))
                )
        ));
    }

    /**
     * Indexes raw text directly, e.g. when the message is already in memory. The document id
     * is a content hash (see {@link #contentId(String)}), so indexing the same message again
     * replaces its document instead of adding a copy.
     */
    public String uploadRaw(String fileName, String rawMessage) throws IOException {
        Map<String, List<String>> tags = MessageParser.extractTags(rawMessage);
        String messageType = MessageParser.extractMessageType(rawMessage);
        String senderLt = MessageParser.extractSender(rawMessage);
        String receiverLt = MessageParser.extractReceiver(rawMessage);
        String docId = contentId(rawMessage);

        Map<String, Object> doc = new HashMap<>();
        doc.put(F_FILE_NAME, fileName);
        doc.put(F_RAW_MESSAGE, rawMessage);
        doc.put(F_MESSAGE_TYPE, messageType);
        doc.put(F_SENDER_LT, senderLt);
        doc.put(F_RECEIVER_LT, receiverLt);
        doc.put(F_TAGS, tags);
        doc.put(F_INDEXED_AT, Instant.now().toString());

        IndexResponse response = client.index(i -> i
                .index(index)
                .id(docId)
                .document(doc)
        );
        return response.id();
    }

    /**
     * Stable identity of a message, so that re-indexing stays idempotent: a second pass over
     * the same message replaces its document instead of adding a copy. It is a SHA-256 over the
     * raw message text with runs of whitespace collapsed, so re-serialised copies that differ
     * only in line endings or padding still count as the same message.
     */
    static String contentId(String rawMessage) {
        return sha256Hex(rawMessage.replaceAll("\\s+", " ").strip());
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Deletes all documents in the index (mapping/settings are kept).
     * Returns the number of deleted documents.
     */
    public long deleteAll() throws IOException {
        DeleteByQueryResponse response = client.deleteByQuery(d -> d
                .index(index)
                .query(q -> q.matchAll(m -> m))
                .refresh(true)
        );
        return response.deleted() == null ? 0L : response.deleted();
    }

    /** Number of documents currently in the index (0 if the index does not exist yet). */
    public long documentCount() throws IOException {
        boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(index))).value();
        if (!exists) {
            return 0L;
        }
        return client.count(c -> c.index(index)).count();
    }

    /**
     * Searches for a value in a specific tag, e.g. field="tag.35B", value="US0378331005".
     * Returns the complete original documents (raw_message + metadata).
     */
    public List<SwiftHit> searchByTag(String tag, String value) throws IOException {
        return runSearch(tagMatchQuery(tag, value));
    }

    /**
     * Searches for several tag conditions at once (ANDed).
     * Expects a map of tag -> value, e.g. {"23G":"NEWM", "35B":"US0378331005"}.
     * A document is a hit when ALL conditions match.
     */
    public List<SwiftHit> searchByTags(Map<String, String> tagValues) throws IOException {
        Query query = Query.of(q -> q
                .bool(b -> {
                    tagValues.forEach((tag, value) -> b.must(tagMatchQuery(tag, value)));
                    return b;
                })
        );
        return runSearch(query);
    }

    /**
     * Searches all messages of a given message type, e.g. "536" for MT 536.
     * Exact match (term query) since mt is mapped as a keyword.
     */
    public List<SwiftHit> searchByMessageType(String messageType) throws IOException {
        Query query = Query.of(q -> q
                .term(t -> t
                        .field(F_MESSAGE_TYPE)
                        .value(messageType)
                )
        );
        return runSearch(query);
    }

    /**
     * Searches messages by Logical Terminal (LT address) -- as sender OR receiver.
     * Prefix search (case-insensitive) so that the BIC8/BIC11 works as the search
     * term instead of the full 12-character LT address, e.g. "BANKUS33".
     */
    public List<SwiftHit> searchByLogicalTerminal(String lt) throws IOException {
        return runSearch(logicalTerminalQuery(lt));
    }

    /**
     * Combined search: Logical Terminal AND (optionally) a list of message types
     * AND any number of tag conditions -- everything ANDed together.
     *
     *  - lt           : LT address (sender OR receiver, prefix, case-insensitive); null/blank = ignored
     *  - messageTypes : list of allowed message types (ORed among themselves); null/empty = ignored
     *  - tagValues    : map of tag -> value, every condition must match (AND); empty = ignored
     *
     * If all three are empty the query matches nothing (empty hit list).
     */
    public List<SwiftHit> search(String lt, List<String> messageTypes,
                                 Map<String, String> tagValues) throws IOException {
        Query query = Query.of(q -> q
                .bool(b -> {
                    if (lt != null && !lt.isBlank()) {
                        b.must(logicalTerminalQuery(lt));
                    }
                    if (messageTypes != null && !messageTypes.isEmpty()) {
                        b.must(messageTypesQuery(messageTypes));
                    }
                    if (tagValues != null) {
                        tagValues.forEach((tag, value) -> b.must(tagMatchQuery(tag, value)));
                    }
                    return b;
                })
        );
        return runSearch(query);
    }

    /** Full-text search over the complete raw message (all tags/content). */
    public List<SwiftHit> searchFullText(String value) throws IOException {
        Query query = Query.of(q -> q
                .match(m -> m
                        .field(F_RAW_MESSAGE)
                        .query(value)
                )
        );
        return runSearch(query);
    }

    /** Full-text search across all tag values at once (query_string on tag.*). */
    public List<SwiftHit> searchAnyTag(String value) throws IOException {
        Query query = Query.of(q -> q
                .queryString(qs -> qs
                        .query(value)
                        .fields(TAGS_PREFIX + "*")
                )
        );
        return runSearch(query);
    }

    /**
     * Runs a free-text {@code query_string} query (Elasticsearch's Lucene-like syntax: boolean
     * operators {@code AND}/{@code OR}/{@code NOT}, {@code field:value}, wildcards, ranges) over
     * {@code raw_message}, so it works the same way as the embedded Lucene search's classic
     * {@code QueryParser} string.
     *
     * @throws IllegalArgumentException if Elasticsearch cannot parse the query
     */
    public List<SwiftHit> searchByQueryString(String queryString) throws IOException {
        Query query = Query.of(q -> q
                .queryString(qs -> qs
                        .query(queryString)
                        .defaultField(F_RAW_MESSAGE)
                        .allowLeadingWildcard(true)
                )
        );
        try {
            return runSearch(query);
        } catch (co.elastic.clients.elasticsearch._types.ElasticsearchException e) {
            throw new IllegalArgumentException("Invalid query: " + e.getMessage(), e);
        }
    }

    /** {@code match} on a single tag field, e.g. tag.35B. */
    private static Query tagMatchQuery(String tag, String value) {
        return Query.of(q -> q.match(m -> m.field(TAGS_PREFIX + tag).query(value)));
    }

    /** {@code bool}/{@code should} prefix on sender OR receiver (case-insensitive). */
    private static Query logicalTerminalQuery(String lt) {
        return Query.of(q -> q.bool(b -> b
                .should(s -> s.prefix(p -> p.field(F_SENDER_LT).value(lt).caseInsensitive(true)))
                .should(s -> s.prefix(p -> p.field(F_RECEIVER_LT).value(lt).caseInsensitive(true)))
                .minimumShouldMatch("1")
        ));
    }

    /** {@code bool}/{@code should} term on mt for any of the given types. */
    private static Query messageTypesQuery(List<String> messageTypes) {
        return Query.of(q -> q.bool(b -> {
            for (String mt : messageTypes) {
                b.should(s -> s.term(t -> t.field(F_MESSAGE_TYPE).value(mt)));
            }
            return b.minimumShouldMatch("1");
        }));
    }

    @SuppressWarnings("unchecked")
    private List<SwiftHit> runSearch(Query query) throws IOException {
        SearchResponse<Map> response = client.search(s -> s
                        .index(index)
                        .query(query)
                        .size(maxHits),
                Map.class
        );

        List<SwiftHit> results = new ArrayList<>();
        for (Hit<Map> hit : response.hits().hits()) {
            Map<String, Object> source = hit.source();
            if (source == null) {
                continue;
            }
            results.add(new SwiftHit(
                    hit.id(),
                    (String) source.get(F_FILE_NAME),
                    (String) source.get(F_RAW_MESSAGE),
                    (String) source.get(F_MESSAGE_TYPE),
                    (String) source.get(F_SENDER_LT),
                    (String) source.get(F_RECEIVER_LT),
                    (Map<String, List<String>>) source.get(F_TAGS)
            ));
        }
        return results;
    }

    @Override
    public void close() throws IOException {
        restClient.close();
    }

    /** A search hit: contains the complete original document. */
    public record SwiftHit(String id, String fileName, String rawMessage,
                            String messageType, String senderLt, String receiverLt,
                            Map<String, List<String>> tags) {
    }
}
