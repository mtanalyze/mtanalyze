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
package com.mtanalyze.config;

import com.mtanalyze.parser.MtFileIO;

import java.util.prefs.Preferences;

public final class SystemConfig {

    private static final String KEY_MT_EXPORT_SENDER   = "mt_export_sender";
    private static final String KEY_MT_EXPORT_RECEIVER = "mt_export_receiver";
    private static final String KEY_MAX_ENTRIES        = "max_entries";
    private static final String KEY_LOG_SWIFT_START    = "log_swift_start";
    private static final String KEY_LOG_NEWLINE_TOKEN  = "log_newline_token";
    private static final String KEY_EXPERIMENTAL_MODE  = "experimental_mode";

    public static final int DEFAULT_MAX_ENTRIES = 1000;

    private final Preferences prefs;

    public SystemConfig() {
        this.prefs = Preferences.userNodeForPackage(SystemConfig.class);
    }

    public String getLogSwiftStart() {
        return prefs.get(KEY_LOG_SWIFT_START, MtFileIO.DEFAULT_LOG_SWIFT_START);
    }

    public String getLogNewlineToken() {
        return prefs.get(KEY_LOG_NEWLINE_TOKEN, MtFileIO.DEFAULT_LOG_NEWLINE_TOKEN);
    }

    public boolean isExperimentalMode() {
        return prefs.getBoolean(KEY_EXPERIMENTAL_MODE, false);
    }

    public int getMaxEntries() {
        int v = prefs.getInt(KEY_MAX_ENTRIES, DEFAULT_MAX_ENTRIES);
        return v > 0 ? v : DEFAULT_MAX_ENTRIES;
    }

    public String getMtExportSender() {
        return prefs.get(KEY_MT_EXPORT_SENDER, "");
    }

    public String getMtExportReceiver() {
        return prefs.get(KEY_MT_EXPORT_RECEIVER, "");
    }

    /** Persists all Settings-dialog-editable values. */
    public void saveSettings(String sender, String receiver, int maxEntries,
                              String logSwiftStart, String logNewlineToken,
                              boolean experimentalMode) {
        prefs.put(KEY_MT_EXPORT_SENDER,   sender   == null ? "" : sender);
        prefs.put(KEY_MT_EXPORT_RECEIVER, receiver == null ? "" : receiver);
        prefs.putInt(KEY_MAX_ENTRIES, maxEntries);
        prefs.put(KEY_LOG_SWIFT_START,   logSwiftStart);
        prefs.put(KEY_LOG_NEWLINE_TOKEN, logNewlineToken);
        prefs.putBoolean(KEY_EXPERIMENTAL_MODE, experimentalMode);
    }
}