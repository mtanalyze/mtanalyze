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
package com.mtanalyze.config;

import com.mtanalyze.parser.MtFileIO;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class SystemConfig {

    public static final String FILE_NAME = "mtanalyze.properties";

    private static final int DEFAULT_MAX_ENTRIES = 1000;

    private final Properties props;

    public SystemConfig() {
        this.props = load();
    }

    private static Properties load() {
        Properties p = new Properties();
        try {
            Path jar = Path.of(SystemConfig.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            Path cfg = jar.getParent().resolve(FILE_NAME);
            if (Files.exists(cfg)) {
                try (InputStream in = Files.newInputStream(cfg)) {
                    p.load(in);
                }
            }
        } catch (Exception ignored) {}
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
}