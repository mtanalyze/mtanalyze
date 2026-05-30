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
package com.mtanalyze.ui.view;

import com.mtanalyze.export.CsvExport;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class SecuritiesPostingPanel extends PostingPanel {

    public SecuritiesPostingPanel(CsvExport.Prefs csvPrefs,
                                   Consumer<String> onAddSafeToQuickFilter,
                                   Predicate<String> hasSecuritiesAccountMapping) {
        super("to load securities postings",
              "Load Securities Postings",
              "Export Securities Postings",
              "securities_postings.csv",
              csvPrefs);
        installSafeFilter(onAddSafeToQuickFilter, hasSecuritiesAccountMapping);
    }
}