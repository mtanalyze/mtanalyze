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
package com.mtanalyze.ui;

import com.mtanalyze.config.SystemConfig;

import java.awt.*;
import java.io.File;
import java.util.Set;

/**
 * Implemented by the host frame so {@link FileImporter} can trigger UI reactions
 * without depending directly on frame internals.
 */
interface ImportContext {

    Frame         frame();
    SystemConfig  config();
    ImportService importService();

    String     promptMtType(String msg);
    /** Prompt for an MT type filter before importing a log file.
     *  Returns an empty set if no filter (all types), or {@code null} if the user cancelled. */
    Set<String> promptMtTypeFilter(String logFileName);
    void   onNew();

    void onFileLoaded(ImportBatch batch, File file);
    void onDirectoryLoaded(ImportBatch batch, File dir, int fileCount);
    void onContentAppended(ImportBatch batch);
    void onFileAppended(File file);

    void error(String msg);
    void fileError(String verb, Exception ex);
}