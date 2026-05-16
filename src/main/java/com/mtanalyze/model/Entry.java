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
package com.mtanalyze.model;

import com.prowidesoftware.swift.model.SwiftTagListBlock;
import com.prowidesoftware.swift.model.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One row in the entry table. Wraps the flat key/value data together with
 * the two SWIFT block contexts needed for display and deletion.
 * {@code data} is mutable so that synthetic columns (MT type, file, entry type)
 * can be added after parsing.
 */
public record Entry(
        Map<String, String> data,
        SwiftTagListBlock sequence,
        SwiftTagListBlock parentContext
) {
    public String getValue(String key) {
        return data.getOrDefault(key, "");
    }

    /** Combined block used for tag display: parentContext tags followed by sequence tags. */
    public SwiftTagListBlock fullDisplaySequence() {
        List<Tag> tags = new ArrayList<>(parentContext.getTags());
        tags.addAll(sequence.getTags());
        return new SwiftTagListBlock(tags);
    }
}