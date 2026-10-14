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
package com.mtanalyze.search;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Read-only search in an index written by the standalone <i>mtsearch</i> command-line tool
 * (<a href="https://github.com/rmschwarz/mtsearch">github.com/rmschwarz/mtsearch</a>).
 * <p>
 * mtanalyze never writes to this index and never creates its directory. The field layout
 * (keyword fields lower-cased, {@code tag_<name>} fields in lower case, default field
 * {@code raw_message}) is that of mtsearch, which differs from the index written by
 * {@link com.mtanalyze.lucene.MtLucene}.
 */
public final class MtSearchReader {

    /** Default hit limit for a query. */
    public static final int DEFAULT_MAX_HITS = 100;

    /** Name of the environment variable mtsearch itself reads for the index folder. */
    public static final String INDEX_DIR_ENV = "MT_SEARCH_INDEX";

    private static final String DEFAULT_INDEX_DIR = "swift-index";
    private static final String F_RAW_MESSAGE = SwiftMtAnalyzer.RAW_MESSAGE_FIELD;

    /** Fields mtsearch indexes as a single lower-cased term. */
    private static final Set<String> KEYWORD_FIELDS = Set.of(
            "doc_id", "file_name", "msg_index", "mt", "sender", "receiver", "sha256");

    /**
     * The index folder mtsearch uses when none is given: {@code MT_SEARCH_INDEX}, else
     * {@code ./swift-index} (resolved against the working directory).
     */
    public static Path defaultIndexDir() {
        String env = System.getenv(INDEX_DIR_ENV);
        return Path.of(env != null && !env.isBlank() ? env.trim() : DEFAULT_INDEX_DIR).toAbsolutePath();
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
     * @param dir     index directory; blank falls back to {@link #defaultIndexDir()}
     * @param maxHits search hit limit; {@code <= 0} falls back to {@link #DEFAULT_MAX_HITS}
     */
    public void configure(String dir, int maxHits) {
        this.indexDir = dir == null || dir.isBlank() ? defaultIndexDir() : Path.of(dir.trim());
        this.maxHits = maxHits > 0 ? maxHits : DEFAULT_MAX_HITS;
    }

    /**
     * Runs a classic Lucene query string (default field {@code raw_message}) and returns the
     * complete original message of each hit, at most {@link #maxHits()}. Returns an empty list
     * if the index does not exist.
     *
     * @throws IllegalArgumentException if the query cannot be parsed
     */
    public List<String> search(String queryString) throws IOException {
        try (Analyzer analyzer = new SwiftMtAnalyzer(KEYWORD_FIELDS)) {
            Query query = parse(queryString, analyzer);
            if (!Files.isDirectory(indexDir)) {
                return List.of();
            }
            try (Directory dir = FSDirectory.open(indexDir)) {
                if (!DirectoryReader.indexExists(dir)) {
                    return List.of();
                }
                try (DirectoryReader reader = DirectoryReader.open(dir)) {
                    StoredFields stored = reader.storedFields();
                    List<String> messages = new ArrayList<>();
                    for (ScoreDoc hit : new IndexSearcher(reader).search(query, maxHits).scoreDocs) {
                        Document doc = stored.document(hit.doc);
                        String raw = doc.get(F_RAW_MESSAGE);
                        if (raw != null) {
                            messages.add(raw);
                        }
                    }
                    return messages;
                }
            }
        }
    }

    /** Number of messages in the index; 0 if it does not exist. */
    public long documentCount() throws IOException {
        if (!Files.isDirectory(indexDir)) {
            return 0L;
        }
        try (Directory dir = FSDirectory.open(indexDir)) {
            if (!DirectoryReader.indexExists(dir)) {
                return 0L;
            }
            try (DirectoryReader reader = DirectoryReader.open(dir)) {
                return reader.numDocs();
            }
        }
    }

    private static Query parse(String queryString, Analyzer analyzer) {
        QueryParser parser = new LowerCaseFieldQueryParser(F_RAW_MESSAGE, analyzer);
        parser.setAllowLeadingWildcard(true);
        try {
            return parser.parse(queryString);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Invalid Lucene query: " + e.getMessage(), e);
        }
    }

    /** Query parser that treats field names case-insensitively (all index field names are lowercase). */
    private static final class LowerCaseFieldQueryParser extends QueryParser {

        LowerCaseFieldQueryParser(String defaultField, Analyzer analyzer) {
            super(defaultField, analyzer);
        }

        private static String lower(String field) {
            return field == null ? null : field.toLowerCase(Locale.ROOT);
        }

        @Override
        protected Query getFieldQuery(String field, String queryText, boolean quoted) throws ParseException {
            return super.getFieldQuery(lower(field), queryText, quoted);
        }

        @Override
        protected Query getFieldQuery(String field, String queryText, int slop) throws ParseException {
            return super.getFieldQuery(lower(field), queryText, slop);
        }

        @Override
        protected Query getRangeQuery(String field, String part1, String part2,
                                      boolean startInclusive, boolean endInclusive) throws ParseException {
            return super.getRangeQuery(lower(field), part1, part2, startInclusive, endInclusive);
        }

        @Override
        protected Query getPrefixQuery(String field, String termStr) throws ParseException {
            return super.getPrefixQuery(lower(field), termStr);
        }

        @Override
        protected Query getWildcardQuery(String field, String termStr) throws ParseException {
            return super.getWildcardQuery(lower(field), termStr);
        }

        @Override
        protected Query getRegexpQuery(String field, String termStr) throws ParseException {
            return super.getRegexpQuery(lower(field), termStr);
        }

        @Override
        protected Query getFuzzyQuery(String field, String termStr, float minSimilarity) throws ParseException {
            return super.getFuzzyQuery(lower(field), termStr, minSimilarity);
        }
    }
}
