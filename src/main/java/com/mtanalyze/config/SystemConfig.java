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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class SystemConfig {

    public static final String FILE_NAME = "mtanalyze.properties";

    private static final String KEY_MT_EXPORT_SENDER   = "mt.export.sender";
    private static final String KEY_MT_EXPORT_RECEIVER = "mt.export.receiver";

    private static final int DEFAULT_MAX_ENTRIES = 1000;

    private final Properties props;
    private final Path       configFile;

    public SystemConfig() {
        this.configFile = resolveConfigFile();
        this.props      = load(configFile);
    }

    private static Path resolveConfigFile() {
        try {
            Path jar = Path.of(SystemConfig.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return jar.getParent().resolve(FILE_NAME);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Properties load(Path cfg) {
        Properties p = new Properties();
        if (cfg == null) return p;
        try {
            if (Files.exists(cfg)) {
                try (InputStream in = Files.newInputStream(cfg)) {
                    p.load(in);
                }
            }
        } catch (Exception ignored) {
            // Config file is optional; proceed with empty/default properties
        }
        return p;
    }

    public String getLogSwiftStart() {
        return props.getProperty("log.swift.start", MtFileIO.DEFAULT_LOG_SWIFT_START);
    }

    public String getLogNewlineToken() {
        return props.getProperty("log.newline.token", MtFileIO.DEFAULT_LOG_NEWLINE_TOKEN);
    }

    public boolean isExperimentalMode() {
        return Boolean.parseBoolean(props.getProperty("experimental.mode", "false"));
    }

    public int getMaxEntries() {
        try {
            int v = Integer.parseInt(props.getProperty("max.entries",
                    String.valueOf(DEFAULT_MAX_ENTRIES)));
            return v > 0 ? v : DEFAULT_MAX_ENTRIES;
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_ENTRIES;
        }
    }

    public String getMtExportSender() {
        return props.getProperty(KEY_MT_EXPORT_SENDER, "");
    }

    public String getMtExportReceiver() {
        return props.getProperty(KEY_MT_EXPORT_RECEIVER, "");
    }

    public void saveMtExportBic(String sender, String receiver) throws IOException {
        if (configFile == null)
            throw new IOException("Cannot resolve location for " + FILE_NAME + ".");
        setOrRemove(KEY_MT_EXPORT_SENDER,   sender);
        setOrRemove(KEY_MT_EXPORT_RECEIVER, receiver);
        Files.createDirectories(configFile.getParent());
        try (OutputStream out = Files.newOutputStream(configFile)) {
            props.store(out, "MT Analyze configuration file");
        }
    }

    private void setOrRemove(String key, String value) {
        if (value == null || value.isEmpty()) props.remove(key);
        else                                  props.setProperty(key, value);
    }
}