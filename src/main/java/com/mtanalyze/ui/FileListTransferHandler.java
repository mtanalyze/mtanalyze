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

import javax.swing.TransferHandler;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Base TransferHandler that accepts OS file-list drops.
 * Subclasses implement {@link #handleFiles} for drop-specific logic.
 */
public abstract class FileListTransferHandler extends TransferHandler {

    @Override
    public final boolean canImport(TransferSupport s) {
        return s.isDrop() && s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
    }

    @Override
    public final boolean importData(TransferSupport s) {
        if (!canImport(s)) return false;
        try {
            @SuppressWarnings("unchecked")
            List<File> dropped = (List<File>) s.getTransferable()
                    .getTransferData(DataFlavor.javaFileListFlavor);
            return handleFiles(dropped);
        } catch (UnsupportedFlavorException | IOException ex) {
            return false;
        }
    }

    protected abstract boolean handleFiles(List<File> files);
}