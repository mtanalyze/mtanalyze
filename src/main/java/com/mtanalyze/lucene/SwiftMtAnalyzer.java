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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.AnalyzerWrapper;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.pattern.PatternTokenizer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * The analyzer used for both indexing and querying the SWIFT MT index.
 * <p>
 * It behaves differently per field:
 * <ul>
 *   <li>{@code raw_message} is analyzed with the {@link StandardAnalyzer}
 *       (general full-text search over the complete message);</li>
 *   <li>the {@code keyword}-style fields ({@code file_name}, {@code mt},
 *       {@code sender}, ...) -- the names passed to the constructor -- use a
 *       {@link KeywordAnalyzer}: the value is kept verbatim. At index time these
 *       are {@code StringField}s and bypass the analyzer anyway; this matters for
 *       the raw query string, so that {@code sender:BANKUS33AXXX} is not
 *       lowercased or split before it is matched against the stored term;</li>
 *   <li>every other field ({@code tag_*} and the combined {@code tags_all}) is
 *       analyzed with the tag analyzer, which splits SWIFT tag values on the
 *       separators {@code :} {@code /} {@code ,} and whitespace and lowercases
 *       them, so a qualifier ({@code SEME}, {@code SAFE}) and the value behind
 *       {@code //} are both searchable.</li>
 * </ul>
 * <p>
 * Ported from the standalone {@code mtlucene} command-line tool.
 */
public final class SwiftMtAnalyzer extends AnalyzerWrapper {

    /** Field that holds the complete, unmodified message text. */
    static final String RAW_MESSAGE_FIELD = "raw_message";

    private final Analyzer rawMessageAnalyzer = new StandardAnalyzer();
    private final Analyzer keywordAnalyzer = new KeywordAnalyzer();
    private final Analyzer tagAnalyzer = new TagAnalyzer();
    private final Set<String> keywordFields;

    /**
     * @param keywordFields names of the fields that must be matched verbatim
     *                      (not tokenized, not lowercased)
     */
    public SwiftMtAnalyzer(Set<String> keywordFields) {
        super(PER_FIELD_REUSE_STRATEGY);
        this.keywordFields = Set.copyOf(keywordFields);
    }

    @Override
    protected Analyzer getWrappedAnalyzer(String fieldName) {
        if (RAW_MESSAGE_FIELD.equals(fieldName)) {
            return rawMessageAnalyzer;
        }
        if (keywordFields.contains(fieldName)) {
            return keywordAnalyzer;
        }
        return tagAnalyzer;
    }

    @Override
    public void close() {
        rawMessageAnalyzer.close();
        keywordAnalyzer.close();
        tagAnalyzer.close();
        super.close();
    }

    /**
     * Tokenizes SWIFT tag values: splits on whitespace and on the SWIFT
     * separators {@code :} {@code /} {@code ,} and lowercases. So
     * {@code :SEME//STMT20210915001} yields {@code seme} and
     * {@code stmt20210915001} -- the qualifier and the value are both
     * searchable.
     */
    private static final class TagAnalyzer extends Analyzer {

        private static final Pattern SEPARATORS = Pattern.compile("[\\s:/,]+");

        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer source = new PatternTokenizer(SEPARATORS, -1);
            TokenStream tokens = new LowerCaseFilter(source);
            return new TokenStreamComponents(source, tokens);
        }
    }
}
