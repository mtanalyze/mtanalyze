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
package com.mtanalyze.export;

import com.mtanalyze.model.Project;
import com.mtanalyze.model.SwiftMessage;
import com.prowidesoftware.swift.model.SwiftBlock2;
import com.prowidesoftware.swift.model.SwiftTagListBlock;
import com.prowidesoftware.swift.model.mt.AbstractMT;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Serializes a {@link Project} to the {@code .mtd} session file format and back.
 *
 * <p>The .mtd format is a concatenation of SWIFT messages with synthetic block-1/2
 * headers so the file can be re-loaded by the standard parser pipeline.
 * Reading a .mtd file needs no special handling — the existing multi-message
 * splitter in {@code MtFileIO} covers it.
 */
public final class ProjectIO {

    public static final String SESSION_EXTENSION = "mtd";

    private ProjectIO() {}

    /**
     * Writes all messages in {@code project} to {@code file} in .mtd format.
     *
     * @throws IllegalArgumentException if the project has no messages
     * @throws IOException              if the file cannot be written
     */
    public static void save(Project project, File file) throws IOException {
        if (project.isEmpty()) throw new IllegalArgumentException("Project has no messages to save.");
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (SwiftMessage msg : project.messages()) {
            if (!first) sb.append("\r\n");
            sb.append(toSessionText(msg.raw()));
            first = false;
        }
        Files.writeString(file.toPath(), sb.toString());
    }

    private static String toSessionText(AbstractMT mt) {
        String mtType = resolveMtType(mt);
        StringBuilder sb = new StringBuilder();
        sb.append("{1:F01AAAAAAAA0000000000}\r\n");
        sb.append("{2:I").append(mtType).append("AAAAAAAA0000U3003}\r\n");
        sb.append("{4:\r\n");
        SwiftTagListBlock b4 = mt.getSwiftMessage().getBlock4();
        if (b4 != null) {
            for (var tag : b4.getTags()) {
                sb.append(":").append(tag.getName()).append(":");
                if (tag.getValue() != null) sb.append(tag.getValue());
                sb.append("\r\n");
            }
        }
        sb.append("-}");
        return sb.toString();
    }

    private static String resolveMtType(AbstractMT mt) {
        try {
            SwiftBlock2 b2 = mt.getSwiftMessage().getBlock2();
            if (b2 != null && b2.getMessageType() != null && !b2.getMessageType().isEmpty())
                return b2.getMessageType();
        } catch (Exception ignored) { /* fall through */ }
        return "536";
    }
}