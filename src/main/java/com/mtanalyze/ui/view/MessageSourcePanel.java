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
package com.mtanalyze.ui.view;

import com.mtanalyze.ui.EditMenuContributor;
import com.mtanalyze.ui.FileListTransferHandler;
import com.mtanalyze.ui.ToolbarIcons;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Left-side file-explorer panel that displays a lazily-loaded directory tree
 * of SWIFT message files. Selected files can be opened together via
 * double-click, Enter, or the context menu.
 */
public class MessageSourcePanel extends RoundedPanel implements EditMenuContributor {

    private static final Pattern SWIFT_FILE_PATTERN =
        Pattern.compile("(?i).*\\.(txt|swift|mt5\\d{2}|mt9\\d{2}|ste|log)");
    private static final String PLACEHOLDER = "loading…";

    private final JTree tree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode treeRoot;
    private final transient Consumer<List<File>> onOpen;
    private final transient Consumer<File> onImportDir;
    private final transient Consumer<File> onViewInEditor;
    private final transient Runnable onRootsChanged;
    private final transient Runnable onCollapse;
    private final List<File> rootDirs = new ArrayList<>();
    private final Map<File, String> fileMtTypes = new HashMap<>();
    private final Map<File, String> rootDescriptions = new LinkedHashMap<>();

    public MessageSourcePanel(Map<File, String> savedRoots, Consumer<List<File>> onOpen,
                  Consumer<File> onImportDir, Consumer<File> onViewInEditor,
                  Runnable onRootsChanged, Runnable onCollapse) {
        super(new BorderLayout(0, 0));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        this.onOpen = onOpen;
        this.onImportDir = onImportDir;
        this.onViewInEditor = onViewInEditor;
        this.onRootsChanged = onRootsChanged;
        this.onCollapse = onCollapse;
        treeRoot  = new DefaultMutableTreeNode("root");
        treeModel = new DefaultTreeModel(treeRoot);
        tree      = buildTree();
        add(buildHeader(), BorderLayout.NORTH);
        JScrollPane scrollPane = new JScrollPane(tree);
        add(scrollPane, BorderLayout.CENTER);
        installDropTarget(scrollPane);
        savedRoots.forEach(this::addRoot);
    }

    public List<File> getRootDirs() {
        return Collections.unmodifiableList(rootDirs);
    }

    public Map<File, String> getRootDescriptions() {
        return Collections.unmodifiableMap(rootDescriptions);
    }

    public void markFileMtType(File f, String mtType) {
        fileMtTypes.put(f, mtType);
        tree.repaint();
    }

    public void clearFileMtTypes() {
        fileMtTypes.clear();
        tree.repaint();
    }

    public void addRoot(File dir) {
        addRoot(dir, "");
    }

    public void addRoot(File dir, String description) {
        if (!dir.isDirectory() || rootDirs.contains(dir)) return;
        rootDirs.add(dir);
        if (description != null && !description.isBlank())
            rootDescriptions.put(dir, description.trim());
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(dir);
        node.add(new DefaultMutableTreeNode(PLACEHOLDER));
        treeModel.insertNodeInto(node, treeRoot, treeRoot.getChildCount());
        tree.expandPath(new TreePath(treeRoot.getPath()));
        onRootsChanged.run();
    }

    // -----------------------------------------------------------------------
    // Tree construction
    // -----------------------------------------------------------------------
    private JTree buildTree() {
        JTree t = new JTree(treeModel) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (treeRoot.getChildCount() > 0) return;
                paintHintLines(g, this, "Drop a folder here", "to add it to the Explorer");
            }
        };
        t.setRootVisible(false);
        t.setShowsRootHandles(true);
        t.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        t.setCellRenderer(new FileNodeRenderer());
        t.addTreeWillExpandListener(buildExpansionListener());
        t.addMouseListener(buildMouseListener());
        bindEnterKey(t);
        return t;
    }

    private TreeWillExpandListener buildExpansionListener() {
        return new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent e) {
                DefaultMutableTreeNode node =
                    (DefaultMutableTreeNode) e.getPath().getLastPathComponent();
                loadChildrenIfNeeded(node);
            }
            @Override public void treeWillCollapse(TreeExpansionEvent e) { /* NOP */ }
        };
    }

    private MouseAdapter buildMouseListener() {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && !e.isPopupTrigger()) openSelected();
            }
            @Override public void mousePressed (MouseEvent e) { maybeShowPopup(e); }
            @Override public void mouseReleased(MouseEvent e) { maybeShowPopup(e); }
        };
    }

    private void bindEnterKey(JTree t) {
        t.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "open");
        t.getActionMap().put("open", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { openSelected(); }
        });
    }

    // -----------------------------------------------------------------------
    // Header panel
    // -----------------------------------------------------------------------
    private JPanel buildHeader() {
        JButton addBtn = new JButton("+");
        addBtn.setMargin(new Insets(1, 5, 1, 5));
        addBtn.setToolTipText("Add folder");
        addBtn.addActionListener(e -> addRootInteractive());

        JButton refreshBtn = new JButton("↺");
        refreshBtn.setMargin(new Insets(1, 5, 1, 5));
        refreshBtn.setToolTipText("Refresh");
        refreshBtn.addActionListener(e -> refreshAll());

        JButton closeBtn = new JButton("✕");
        closeBtn.setMargin(new Insets(1, 5, 1, 5));
        closeBtn.setToolTipText("Close");
        closeBtn.addActionListener(e -> onCollapse.run());

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 2));
        btns.add(addBtn);
        btns.add(refreshBtn);
        btns.add(closeBtn);

        return buildSectionHeader("Explorer", btns);
    }

    public static JPanel buildSectionHeader(String titleText, JPanel btns) {
        JLabel label = new JLabel(titleText);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return buildSectionHeader(label, btns);
    }

    public static JPanel buildSectionHeader(JComponent title, JPanel btns) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new javax.swing.border.AbstractBorder() {
            @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
                Color col = UIManager.getColor("Separator.foreground");
                if (col != null) { g.setColor(col); g.drawLine(x, y + h - 1, x + w - 1, y + h - 1); }
            }
            @Override public Insets getBorderInsets(Component c)              { return new Insets(3, 6, 3, 4); }
            @Override public Insets getBorderInsets(Component c, Insets ins)  { ins.set(3, 6, 3, 4); return ins; }
        });
        header.add(title, BorderLayout.WEST);
        if (btns != null) header.add(btns, BorderLayout.EAST);
        return header;
    }

    private void addRootInteractive() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Add Folder to Explorer");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setAcceptAllFileFilterUsed(false);
        if (fc.showOpenDialog(SwingUtilities.getWindowAncestor(this)) == JFileChooser.APPROVE_OPTION)
            addRoot(fc.getSelectedFile());
    }

    private void refreshAll() {
        for (int i = 0; i < treeRoot.getChildCount(); i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) treeRoot.getChildAt(i);
            resetToPlaceholder(node);
        }
        treeModel.nodeStructureChanged(treeRoot);
    }

    private void resetToPlaceholder(DefaultMutableTreeNode node) {
        node.removeAllChildren();
        node.add(new DefaultMutableTreeNode(PLACEHOLDER));
    }

    // -----------------------------------------------------------------------
    // Lazy child loading
    // -----------------------------------------------------------------------
    private void loadChildrenIfNeeded(DefaultMutableTreeNode node) {
        if (!isPlaceholderNode(node)) return;
        Object userObj = node.getUserObject();
        if (!(userObj instanceof File)) return;
        node.removeAllChildren();
        populateDirectory(node, (File) userObj);
        treeModel.nodeStructureChanged(node);
    }

    private boolean isPlaceholderNode(DefaultMutableTreeNode node) {
        if (node.getChildCount() != 1) return false;
        Object child = ((DefaultMutableTreeNode) node.getChildAt(0)).getUserObject();
        return PLACEHOLDER.equals(child);
    }

    private void populateDirectory(DefaultMutableTreeNode node, File dir) {
        File[] children = dir.listFiles();
        if (children == null) return;
        Arrays.sort(children, Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)));
        addSubDirs(node, children);
        addSwiftFiles(node, children);
    }

    private void addSubDirs(DefaultMutableTreeNode parent, File[] children) {
        for (File f : children) {
            if (!f.isDirectory()) continue;
            DefaultMutableTreeNode child = new DefaultMutableTreeNode(f);
            child.add(new DefaultMutableTreeNode(PLACEHOLDER));
            parent.add(child);
        }
    }

    private void addSwiftFiles(DefaultMutableTreeNode parent, File[] children) {
        for (File f : children) {
            if (f.isFile() && isSwiftFile(f))
                parent.add(new DefaultMutableTreeNode(f));
        }
    }

    private static boolean isSwiftFile(File f) {
        return SWIFT_FILE_PATTERN.matcher(f.getName()).matches();
    }

    // -----------------------------------------------------------------------
    // Open / selection
    // -----------------------------------------------------------------------
    private void openSelected() {
        List<File> files = getSelectedFiles();
        if (!files.isEmpty()) {
            onOpen.accept(files);
            onCollapse.run();
        }
    }

    public List<File> getSelectedFiles() {
        TreePath[] paths = tree.getSelectionPaths();
        if (paths == null) return Collections.emptyList();
        List<File> files = new ArrayList<>();
        for (TreePath path : paths) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            Object userObj = node.getUserObject();
            if (userObj instanceof File f && f.isFile())
                files.add(f);
        }
        return files;
    }

    // -----------------------------------------------------------------------
    // Context menu
    // -----------------------------------------------------------------------
    private void maybeShowPopup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        selectNodeAt(e);
        JPopupMenu popup = buildContextMenu();
        if (popup.getComponentCount() > 0) popup.show(e.getComponent(), e.getX(), e.getY());
    }

    private void selectNodeAt(MouseEvent e) {
        int row = tree.getRowForLocation(e.getX(), e.getY());
        if (row >= 0 && !tree.isRowSelected(row)) tree.setSelectionRow(row);
    }

    @Override
    public JPopupMenu getPopupMenu() { return buildContextMenu(); }

    private JPopupMenu buildContextMenu() {
        JPopupMenu popup = new JPopupMenu();
        addOpenItem(popup);
        addViewInEditorItem(popup);
        TreePath path = tree.getSelectionPath();
        if (path != null) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            addImportDirItem(popup, node);
            addSetDescriptionItem(popup, node);
            addRemoveRootItem(popup, node);
        }
        return popup;
    }

    private void addImportDirItem(JPopupMenu popup, DefaultMutableTreeNode node) {
        Object userObj = node.getUserObject();
        if (!(userObj instanceof File f) || !f.isDirectory()) return;
        if (popup.getComponentCount() > 0) popup.addSeparator();
        JMenuItem item = new JMenuItem("Import Directory", ToolbarIcons.menuImportDir());
        item.addActionListener(ae -> onImportDir.accept(f));
        popup.add(item);
    }

    private void addOpenItem(JPopupMenu popup) {
        List<File> selected = getSelectedFiles();
        if (selected.isEmpty()) return;
        String label = selected.size() == 1 ? "Use in MT Entries" : "Use " + selected.size() + " Files in MT Entries";
        JMenuItem item = new JMenuItem(label, ToolbarIcons.menuOpen());
        item.addActionListener(ae -> openSelected());
        popup.add(item);
    }

    private void addViewInEditorItem(JPopupMenu popup) {
        List<File> selected = getSelectedFiles();
        if (selected.size() != 1) return;
        if (popup.getComponentCount() > 0) popup.addSeparator();
        JMenuItem item = new JMenuItem("View Source", ToolbarIcons.menuViewInEditor());
        item.addActionListener(ae -> onViewInEditor.accept(selected.get(0)));
        popup.add(item);
    }

    private void addSetDescriptionItem(JPopupMenu popup, DefaultMutableTreeNode node) {
        if (node.getParent() != treeRoot || !(node.getUserObject() instanceof File dir)) return;
        if (popup.getComponentCount() > 0) popup.addSeparator();
        JMenuItem item = new JMenuItem("Set Description…");
        item.addActionListener(ae -> {
            String current = rootDescriptions.getOrDefault(dir, "");
            String input = (String) JOptionPane.showInputDialog(
                    SwingUtilities.getWindowAncestor(MessageSourcePanel.this),
                    "Description for \"" + dir.getName() + "\":",
                    "Set Description", JOptionPane.PLAIN_MESSAGE, null, null, current);
            if (input == null) return;
            input = input.trim();
            if (input.isEmpty()) rootDescriptions.remove(dir);
            else rootDescriptions.put(dir, input);
            tree.repaint();
            onRootsChanged.run();
        });
        popup.add(item);
    }

    private void addRemoveRootItem(JPopupMenu popup, DefaultMutableTreeNode node) {
        if (node.getParent() != treeRoot || !(node.getUserObject() instanceof File)) return;
        if (popup.getComponentCount() > 0) popup.addSeparator();
        JMenuItem item = new JMenuItem("Remove from Explorer", ToolbarIcons.menuRemove());
        item.addActionListener(ae -> removeRoot(node));
        popup.add(item);
    }

    private void removeRoot(DefaultMutableTreeNode node) {
        Object userObj = node.getUserObject();
        if (userObj instanceof File) {
            rootDirs.remove(userObj);
            rootDescriptions.remove(userObj);
        }
        treeModel.removeNodeFromParent(node);
        onRootsChanged.run();
    }

    public static void paintHintLines(Graphics g, Component c, String... lines) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color fg = UIManager.getColor("Label.disabledForeground");
            g2.setColor(fg != null ? fg : Color.GRAY);
            FontMetrics fm = g2.getFontMetrics();
            int lineH = fm.getHeight();
            int blockH = lines.length * lineH + (lines.length - 1) * 4;
            int y = hintTopY(c, blockH) + fm.getAscent();
            for (String line : lines) {
                g2.drawString(line, (c.getWidth() - fm.stringWidth(line)) / 2, y);
                y += lineH + 4;
            }
        } finally {
            g2.dispose();
        }
    }

    private static int hintTopY(Component c, int blockH) {
        Component root = SwingUtilities.getRoot(c);
        if (root != null && c.isShowing()) {
            Point p = SwingUtilities.convertPoint(c, 0, 0, root);
            int localY = root.getHeight() / 3 - p.y - blockH / 2;
            return Math.max(4, Math.min(localY, c.getHeight() - blockH - 4));
        }
        return (c.getHeight() - blockH) / 2;
    }

    // -----------------------------------------------------------------------
    // Drag and drop — accept directories dropped from OS file manager
    // -----------------------------------------------------------------------
    private void installDropTarget(JScrollPane scrollPane) {
        TransferHandler handler = new FileListTransferHandler() {
            @Override
            protected boolean handleFiles(List<File> files) {
                boolean added = false;
                for (File f : files) {
                    if (f.isDirectory()) { addRoot(f); added = true; }
                }
                return added;
            }
        };
        tree.setTransferHandler(handler);
        scrollPane.setTransferHandler(handler);
        setTransferHandler(handler);
    }

    // -----------------------------------------------------------------------
    // Renderer
    // -----------------------------------------------------------------------
    private class FileNodeRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if (!(value instanceof DefaultMutableTreeNode node)) return this;
            Object userObj = node.getUserObject();
            if (userObj instanceof File f) applyFileLabel(f, node.getParent() == treeRoot);
            return this;
        }

        private void applyFileLabel(File f, boolean isRoot) {
            String mt  = fileMtTypes.get(f);
            String desc = isRoot ? rootDescriptions.get(f) : null;
            if (desc != null && !desc.isEmpty()) {
                setText("<html>" + esc(f.getName()) + " <font color='gray'><i>— " + esc(desc) + "</i></font></html>");
                setToolTipText("<html><b>" + esc(f.getAbsolutePath()) + "</b><br>" + esc(desc) + "</html>");
            } else if (mt != null) {
                setText("<html>" + esc(f.getName()) + " <font color='gray'>" + esc(mt) + "</font></html>");
                setToolTipText(f.getAbsolutePath());
            } else {
                setText(f.getName());
                setToolTipText(f.getAbsolutePath());
            }
        }

        private String esc(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}