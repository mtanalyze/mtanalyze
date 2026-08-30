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
package com.mtanalyze.util;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileSystemView;
import java.io.File;

/**
 * Factory for {@link JFileChooser} instances anchored to a start directory that
 * always resolves on Windows.
 *
 * <p>{@code new JFileChooser()} asks the {@link FileSystemView} for its default
 * directory, which on Windows resolves the "Personal" (Documents) shell folder.
 * When that folder is redirected to an unavailable OneDrive/network location the
 * JDK logs a warning from {@code sun.awt.shell.Win32ShellFolderManager2}:
 * {@code Cannot access 'Personal' – java.io.IOException: Could not get shell
 * folder ID list}. Passing an explicit, existing start directory skips that
 * lookup.
 */
public final class FileChoosers {

    private FileChoosers() {
    }

    /** A chooser anchored at {@link #safeStartDirectory()}. */
    public static JFileChooser create() {
        return new JFileChooser(safeStartDirectory());
    }

    /**
     * A chooser anchored at {@code directory}, falling back to
     * {@link #safeStartDirectory()} when it is {@code null} or not an existing
     * directory.
     */
    public static JFileChooser create(File directory) {
        return new JFileChooser(
            directory != null && directory.isDirectory() ? directory : safeStartDirectory());
    }

    /**
     * The user's home directory when it exists, otherwise the first filesystem
     * root, otherwise the working directory. Resolved without any shell-folder
     * lookup.
     */
    public static File safeStartDirectory() {
        File home = new File(System.getProperty("user.home", "."));
        if (home.isDirectory()) {
            return home;
        }
        File[] roots = File.listRoots();
        return roots != null && roots.length > 0 ? roots[0] : new File(".");
    }
}
