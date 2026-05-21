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

import com.mtanalyze.model.SwiftMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ImportBatch {
    final List<SwiftMessage> messages   = new ArrayList<>();
    final List<ColumnDef>    columnDefs = new ArrayList<>();
    final Set<String>        knownKeys  = new HashSet<>();
    final List<String>       prowideLog = new ArrayList<>();
    int     entryCount;
    int     totalParsed;
    int     errors;
    boolean limitReached;
}