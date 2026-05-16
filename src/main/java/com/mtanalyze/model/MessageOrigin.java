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

/** Describes how a SWIFT message entered the application. */
public enum MessageOrigin {
    /** Parsed from a standard SWIFT file. The source file is also the persisted file. */
    SWIFT_FILE,
    /** Extracted from a log file containing embedded SWIFT content. */
    LOG_FILE,
    /** Parsed from a CSV / name-value format (not a native SWIFT file). */
    NAME_VALUE,
    /** Pasted from clipboard or typed directly into the append dialog. */
    CLIPBOARD
}