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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Captures java.util.logging output from {@code com.prowidesoftware.*} loggers for the
 * duration of a code block.  The handler is attached to the JUL root logger so that
 * records are guaranteed to arrive regardless of whether Prowide loggers were initialised
 * before this capture started.  Only WARNING+ records whose logger name starts with
 * {@code com.prowidesoftware} are kept; all others pass through silently.
 * Duplicate messages (same text repeated per chunk) are deduplicated.
 *
 * <pre>
 *   try (ProwideLogCapture cap = ProwideLogCapture.start()) {
 *       // ... Prowide calls ...
 *       batch.prowideLog.addAll(cap.stop());
 *   }
 * </pre>
 */
final class ProwideLogCapture implements AutoCloseable {

    private static final Logger JUL_ROOT = Logger.getLogger("");

    private final CollectingHandler handler = new CollectingHandler();

    private ProwideLogCapture() {
        handler.setLevel(Level.WARNING);
        JUL_ROOT.addHandler(handler);
    }

    static ProwideLogCapture start() {
        return new ProwideLogCapture();
    }

    /**
     * Stops capturing and returns deduplicated formatted messages.
     * Each entry looks like {@code "[WARNING] Cannot determine the message type..."}.
     */
    List<String> stop() {
        JUL_ROOT.removeHandler(handler);
        return handler.formatted();
    }

    @Override
    public void close() {
        JUL_ROOT.removeHandler(handler);
    }

    // -----------------------------------------------------------------------

    private static final class CollectingHandler extends Handler {
        private final Set<String> seen = new LinkedHashSet<>();

        @Override
        public void publish(LogRecord r) {
            if (r == null) return;
            String loggerName = r.getLoggerName();
            if (loggerName == null || !loggerName.startsWith("com.prowidesoftware")) return;
            if (!isLoggable(r)) return;

            String label   = r.getLevel().intValue() >= Level.SEVERE.intValue() ? "SEVERE " : "WARNING";
            String message = r.getMessage() != null ? r.getMessage() : "";
            StringBuilder sb = new StringBuilder("[").append(label).append("] ").append(message);
            if (r.getThrown() != null)
                sb.append(" → ").append(r.getThrown().getClass().getSimpleName())
                  .append(": ").append(r.getThrown().getMessage());
            seen.add(sb.toString());
        }

        @Override public void flush() { /* records are kept in memory; nothing to flush */ }
        @Override public void close() { /* no I/O resources to release */ }

        List<String> formatted() { return new ArrayList<>(seen); }
    }
}