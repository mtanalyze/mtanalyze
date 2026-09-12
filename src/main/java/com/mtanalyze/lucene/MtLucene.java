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

import com.mtanalyze.parser.SwiftMessageParser;
import com.prowidesoftware.swift.io.RJEReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Wraps the local Apache Lucene index for indexing and searching SWIFT MT messages.
 * <p>
 * There is no server: the index is a directory on disk.
 * <p>
 * A file may hold several SWIFT messages (RJE format: messages separated by a
 * line containing only {@code $}). Each message becomes its own document.
 * <p>
 * Each document contains:
 * <ul>
 *  <li>{@code doc_id}       : a generated identifier for the indexed document</li>
 *  <li>{@code content_id}   : content hash identifying the message; re-indexing the
 *      same message replaces its document instead of adding a copy</li>
 *  <li>{@code file_name}    : original file name</li>
 *  <li>{@code msg_index}    : 1-based position of the message within its file</li>
 *  <li>{@code raw_message}  : the complete, unmodified text of this one message</li>
 *  <li>{@code mt} : e.g. "536" for MT 536 (if detectable)</li>
 *  <li>{@code sender}    : Logical Terminal (LT address) of the sender, e.g. "BANKUS33AXXX"</li>
 *  <li>{@code receiver}  : Logical Terminal (LT address) of the receiver, e.g. "BANKBEBBAXXX"</li>
 *  <li>{@code tag_<name>}   : one field per SWIFT tag (20C, 23G, 35B, 97A, ...),
 *      repeated once per occurrence so every value stays individually searchable</li>
 *  <li>{@code tags_all}     : all tag values combined, for the "search across all tags" query</li>
 *  <li>{@code indexed_at}   : timestamp when the message was indexed</li>
 * </ul>
 * As a result every search -- whether by tag or full text -- returns the complete
 * original message (including {@code raw_message}) as a hit.
 * <p>
 * Ported from the standalone {@code mtlucene} command-line tool.
 */
public class MtLucene implements AutoCloseable {

    /** Default index directory, relative to the working directory. */
    public static final String DEFAULT_INDEX_DIR = "swift-index";

    /** Default upper bound on the number of hits a search returns. */
    public static final int DEFAULT_MAX_HITS = 100;

    private static final String F_DOC_ID = "doc_id";
    private static final String F_CONTENT_ID = "content_id";
    private static final String F_FILE_NAME = "file_name";
    private static final String F_MSG_INDEX = "msg_index";
    private static final String F_RAW_MESSAGE = SwiftMtAnalyzer.RAW_MESSAGE_FIELD;
    private static final String F_MT = "mt";
    private static final String F_SENDER = "sender";
    private static final String F_SENDER_LC = "sender_lc";
    private static final String F_RECEIVER = "receiver";
    private static final String F_RECEIVER_LC = "receiver_lc";
    private static final String F_INDEXED_AT = "indexed_at";
    private static final String F_TAGS_ALL = "tags_all";
    private static final String TAG_FIELD_PREFIX = "tag_";

    /**
     * Only tags whose name starts with one of these prefixes are indexed. These
     * are the fields that actually carry searchable business content (references,
     * identifiers, narratives, parties, amounts, dates); structural tags such as
     * {@code 16R}/{@code 16S} or {@code 23G} are skipped.
     */
    private static final List<String> INDEXABLE_TAG_PREFIXES =
            List.of("20", "35", "70", "94", "95", "97", "98");

    /** Fields matched verbatim (see {@link SwiftMtAnalyzer}). */
    private static final Set<String> KEYWORD_FIELDS = Set.of(
            F_DOC_ID, F_CONTENT_ID, F_FILE_NAME, F_MSG_INDEX, F_MT,
            F_SENDER, F_SENDER_LC, F_RECEIVER, F_RECEIVER_LC);

    private final Directory directory;
    private final Analyzer analyzer = new SwiftMtAnalyzer(KEYWORD_FIELDS);
    private final int maxHits;
    private IndexWriter writer;

    public MtLucene(Path indexDir) throws IOException {
        this(indexDir, DEFAULT_MAX_HITS);
    }

    public MtLucene(Path indexDir, int maxHits) throws IOException {
        Files.createDirectories(indexDir);
        this.directory = FSDirectory.open(indexDir);
        this.maxHits = maxHits > 0 ? maxHits : DEFAULT_MAX_HITS;
    }

    /** Creates the index if it does not exist yet. */
    public void ensureIndex() throws IOException {
        writer().commit();
    }

    /**
     * Indexes a single file. The file may contain several SWIFT messages in RJE
     * format (separated by a line containing only {@code $}); each message is
     * indexed as its own document. Returns the generated document ids.
     */
    public List<String> indexFile(Path filePath) throws IOException {
        List<String> ids = addFile(filePath.getFileName().toString(),
                Files.readString(filePath, StandardCharsets.UTF_8));
        writer().commit();
        return ids;
    }

    /** Indexes every file in a directory (not recursive). Returns all message ids. */
    public List<String> indexDirectory(Path dirPath) throws IOException {
        List<String> ids = new ArrayList<>();
        try (Stream<Path> files = Files.list(dirPath)) {
            for (Path p : files.filter(Files::isRegularFile).sorted().toList()) {
                ids.addAll(addFile(p.getFileName().toString(),
                        Files.readString(p, StandardCharsets.UTF_8)));
            }
        }
        writer().commit();
        return ids;
    }

    /**
     * Indexes raw text directly (e.g. when the content is already in memory).
     * Splits multiple RJE messages just like {@link #indexFile(Path)}.
     */
    public List<String> indexRaw(String fileName, String rawText) throws IOException {
        List<String> ids = addFile(fileName, rawText);
        writer().commit();
        return ids;
    }

    /**
     * Adds the messages in {@code rawText} to the index <b>without committing</b>.
     * Use this to index many messages one by one (e.g. behind a progress bar) and
     * call {@link #commit()} once at the end. Splits multiple RJE messages just
     * like {@link #indexRaw(String, String)}.
     */
    public List<String> addRaw(String fileName, String rawText) throws IOException {
        return addFile(fileName, rawText);
    }

    /** Flushes everything added via {@link #addRaw} to disk. */
    public void commit() throws IOException {
        writer().commit();
    }

    /** Splits {@code fileContent} into individual SWIFT messages and indexes each. */
    private List<String> addFile(String fileName, String fileContent) throws IOException {
        List<String> ids = new ArrayList<>();
        RJEReader reader = new RJEReader(fileContent);
        int msgIndex = 0;
        while (reader.hasNext()) {
            String message = reader.next();
            if (message == null || message.isBlank()) {
                continue;
            }
            ids.add(addMessage(fileName, ++msgIndex, message));
        }
        return ids;
    }

    private String addMessage(String fileName, int msgIndex, String rawMessage) throws IOException {
        Map<String, List<String>> tags = SwiftMessageParser.extractTags(rawMessage);
        String messageType = SwiftMessageParser.extractMessageType(rawMessage);
        String senderLt = SwiftMessageParser.extractSender(rawMessage);
        String receiverLt = SwiftMessageParser.extractReceiver(rawMessage);
        String docId = UUID.randomUUID().toString();
        String contentId = contentId(rawMessage);

        Document doc = new Document();
        doc.add(new StringField(F_DOC_ID, docId, Field.Store.YES));
        doc.add(new StringField(F_CONTENT_ID, contentId, Field.Store.YES));
        doc.add(new StringField(F_FILE_NAME, fileName, Field.Store.YES));
        doc.add(new StringField(F_MSG_INDEX, Integer.toString(msgIndex), Field.Store.YES));
        doc.add(new TextField(F_RAW_MESSAGE, rawMessage, Field.Store.YES));
        if (messageType != null) {
            doc.add(new StringField(F_MT, messageType, Field.Store.YES));
        }
        if (senderLt != null) {
            doc.add(new StringField(F_SENDER, senderLt, Field.Store.YES));
            doc.add(new StringField(F_SENDER_LC, senderLt.toLowerCase(Locale.ROOT), Field.Store.NO));
        }
        if (receiverLt != null) {
            doc.add(new StringField(F_RECEIVER, receiverLt, Field.Store.YES));
            doc.add(new StringField(F_RECEIVER_LC, receiverLt.toLowerCase(Locale.ROOT), Field.Store.NO));
        }
        doc.add(new StoredField(F_INDEXED_AT, Instant.now().toString()));

        tags.forEach((name, values) -> {
            if (!isIndexableTag(name)) {
                return;
            }
            for (String value : values) {
                doc.add(new TextField(TAG_FIELD_PREFIX + name, value, Field.Store.YES));
                doc.add(new TextField(F_TAGS_ALL, value, Field.Store.NO));
            }
        });

        writer().updateDocument(new Term(F_CONTENT_ID, contentId), doc);
        return docId;
    }

    /**
     * Stable identity of a message, so that re-indexing stays idempotent: a
     * second pass over the same message replaces its document instead of adding
     * a copy. It is a SHA-256 over the raw message text with runs of whitespace
     * collapsed, so re-serialised copies that differ only in line endings or
     * padding still count as the same message. Two genuinely different messages
     * -- including the individual pages of one paginated statement, which may
     * share a {@code :20C::SEME//} reference -- always get distinct identities.
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

    /** Whether a SWIFT tag is kept in the index (see {@link #INDEXABLE_TAG_PREFIXES}). */
    private static boolean isIndexableTag(String name) {
        for (String prefix : INDEXABLE_TAG_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Deletes all documents in the index (the directory is kept).
     * Returns the number of documents that were present before the deletion.
     */
    public long deleteAll() throws IOException {
        long before = countDocs();
        writer().deleteAll();
        writer().commit();
        return before;
    }

    /** Number of documents currently in the index. */
    public long documentCount() throws IOException {
        return countDocs();
    }

    /**
     * Searches for a value in a specific tag, e.g. tag="35B", value="US0378331005".
     * Returns the complete original documents (raw_message + metadata).
     */
    public List<SwiftHit> searchByTag(String tag, String value) throws IOException {
        return runSearch(analyzedQuery(TAG_FIELD_PREFIX + tag, value));
    }

    /**
     * Searches for several tag conditions at once (ANDed).
     * Expects a map of tag -> value, e.g. {"23G":"NEWM", "35B":"US0378331005"}.
     * A document is a hit when ALL conditions match.
     */
    public List<SwiftHit> searchByTags(Map<String, String> tagValues) throws IOException {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        tagValues.forEach((tag, value) ->
                builder.add(analyzedQuery(TAG_FIELD_PREFIX + tag, value), BooleanClause.Occur.MUST));
        return runSearch(builder.build());
    }

    /**
     * Searches all messages of a given message type, e.g. "536" for MT 536.
     * Exact match, since mt is indexed as a keyword.
     */
    public List<SwiftHit> searchByMessageType(String messageType) throws IOException {
        return runSearch(new TermQuery(new Term(F_MT, messageType)));
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
     * <p>
     *  - lt           : LT address (sender OR receiver, prefix, case-insensitive); null/blank = ignored
     *  - messageTypes : list of allowed message types (ORed among themselves); null/empty = ignored
     *  - tagValues    : map of tag -> value, every condition must match (AND); empty = ignored
     * <p>
     * If all three are empty the result is an empty hit list.
     */
    public List<SwiftHit> search(String lt, List<String> messageTypes,
                                 Map<String, String> tagValues) throws IOException {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        boolean anyCondition = false;

        if (lt != null && !lt.isBlank()) {
            builder.add(logicalTerminalQuery(lt), BooleanClause.Occur.MUST);
            anyCondition = true;
        }
        if (messageTypes != null && !messageTypes.isEmpty()) {
            builder.add(messageTypesQuery(messageTypes), BooleanClause.Occur.MUST);
            anyCondition = true;
        }
        if (tagValues != null) {
            for (Map.Entry<String, String> entry : tagValues.entrySet()) {
                builder.add(analyzedQuery(TAG_FIELD_PREFIX + entry.getKey(), entry.getValue()),
                        BooleanClause.Occur.MUST);
                anyCondition = true;
            }
        }
        if (!anyCondition) {
            return List.of();
        }
        return runSearch(builder.build());
    }

    /** Full-text search over the complete raw message (all tags/content). */
    public List<SwiftHit> searchFullText(String value) throws IOException {
        return runSearch(analyzedQuery(F_RAW_MESSAGE, value));
    }

    /** Full-text search across all tag values at once. */
    public List<SwiftHit> searchAnyTag(String value) throws IOException {
        return runSearch(analyzedQuery(F_TAGS_ALL, value));
    }

    /**
     * Runs a raw Lucene query string (classic {@link QueryParser} syntax), e.g.
     * {@code tag_35B:US0378331005 AND mt:536} or
     * {@code raw_message:"APPLE INC" NOT tag_98A:20210914}.
     * <p>
     * The default field (a bare term without {@code field:}) is
     * {@code raw_message}. Field names: {@code raw_message}, {@code mt},
     * {@code sender}, {@code receiver}, {@code file_name}, {@code tags_all}
     * and {@code tag_<name>} (for example {@code tag_20C}). Leading wildcards are
     * allowed.
     *
     * @throws IllegalArgumentException if the query string cannot be parsed
     */
    public List<SwiftHit> searchByQueryString(String queryString) throws IOException {
        QueryParser parser = new QueryParser(F_RAW_MESSAGE, analyzer);
        parser.setAllowLeadingWildcard(true);
        Query query;
        try {
            query = parser.parse(queryString);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Invalid Lucene query: " + e.getMessage(), e);
        }
        return runSearch(query);
    }

    /**
     * Builds an OR query over every token the analyzer produces for {@code value}
     * on {@code field} -- the equivalent of an Elasticsearch {@code match} query.
     */
    private Query analyzedQuery(String field, String value) {
        List<String> terms = analyze(field, value);
        if (terms.isEmpty()) {
            return new MatchNoDocsQuery();
        }
        if (terms.size() == 1) {
            return new TermQuery(new Term(field, terms.get(0)));
        }
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (String term : terms) {
            builder.add(new TermQuery(new Term(field, term)), BooleanClause.Occur.SHOULD);
        }
        return builder.build();
    }

    private List<String> analyze(String field, String value) {
        List<String> terms = new ArrayList<>();
        if (value == null || value.isEmpty()) {
            return terms;
        }
        try (TokenStream stream = analyzer.tokenStream(field, value)) {
            CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                terms.add(term.toString());
            }
            stream.end();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return terms;
    }

    /** {@code should} prefix on sender OR receiver (case-insensitive). */
    private static Query logicalTerminalQuery(String lt) {
        String lowerCased = lt.toLowerCase(Locale.ROOT);
        return new BooleanQuery.Builder()
                .add(new PrefixQuery(new Term(F_SENDER_LC, lowerCased)), BooleanClause.Occur.SHOULD)
                .add(new PrefixQuery(new Term(F_RECEIVER_LC, lowerCased)), BooleanClause.Occur.SHOULD)
                .setMinimumNumberShouldMatch(1)
                .build();
    }

    /** {@code should} term on mt for any of the given types. */
    private static Query messageTypesQuery(List<String> messageTypes) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (String messageType : messageTypes) {
            builder.add(new TermQuery(new Term(F_MT, messageType)), BooleanClause.Occur.SHOULD);
        }
        return builder.setMinimumNumberShouldMatch(1).build();
    }

    private IndexWriter writer() throws IOException {
        if (writer == null) {
            IndexWriterConfig config = new IndexWriterConfig(analyzer)
                    .setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            writer = new IndexWriter(directory, config);
        }
        return writer;
    }

    private long countDocs() throws IOException {
        if (!DirectoryReader.indexExists(directory)) {
            return 0L;
        }
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            return reader.numDocs();
        }
    }

    private List<SwiftHit> runSearch(Query query) throws IOException {
        if (!DirectoryReader.indexExists(directory)) {
            return List.of();
        }
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            StoredFields storedFields = reader.storedFields();
            List<SwiftHit> results = new ArrayList<>();
            for (ScoreDoc scoreDoc : searcher.search(query, maxHits).scoreDocs) {
                results.add(toHit(storedFields.document(scoreDoc.doc)));
            }
            return results;
        }
    }

    private static SwiftHit toHit(Document doc) {
        Map<String, List<String>> tags = new LinkedHashMap<>();
        for (IndexableField field : doc.getFields()) {
            String name = field.name();
            if (name.startsWith(TAG_FIELD_PREFIX)) {
                tags.computeIfAbsent(name.substring(TAG_FIELD_PREFIX.length()), k -> new ArrayList<>())
                        .add(field.stringValue());
            }
        }
        return new SwiftHit(
                doc.get(F_DOC_ID),
                doc.get(F_FILE_NAME),
                doc.get(F_RAW_MESSAGE),
                doc.get(F_MT),
                doc.get(F_SENDER),
                doc.get(F_RECEIVER),
                tags);
    }

    @Override
    public void close() throws IOException {
        if (writer != null) {
            writer.close();
        }
        analyzer.close();
        directory.close();
    }

    /** A search hit: contains the complete original document. */
    public record SwiftHit(String id, String fileName, String rawMessage,
                            String messageType, String senderLt, String receiverLt,
                            Map<String, List<String>> tags) {
    }
}
