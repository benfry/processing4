package processing.mode.java;

import java.awt.EventQueue;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.WindowConstants;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;

import processing.app.Messages;
import processing.app.ui.ZoomTreeCellRenderer;


class ASTViewer {
  final JDialog window;
  final JTree tree;
  final Consumer<PreprocSketch> updateListener;


  ASTViewer(JavaEditor editor, PreprocService pps) {
    updateListener = this::buildAndUpdateTree;

    window = new JDialog(editor);

    tree = new JTree() {
      @Override
      public String convertValueToText(Object value, boolean selected,
                                       boolean expanded, boolean leaf,
                                       int row, boolean hasFocus) {
        if (value instanceof DefaultMutableTreeNode) {
          DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) value;
          Object o = treeNode.getUserObject();
          if (o instanceof ASTNode) {
            ASTNode node = (ASTNode) o;
            return CompletionGenerator.getNodeAsString(node);
          }
        }
        return super.convertValueToText(value, selected, expanded, leaf, row, hasFocus);
      }
    };
    tree.setCellRenderer(new ZoomTreeCellRenderer());
    window.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentHidden(ComponentEvent e) {
        pps.unregisterListener(updateListener);
        tree.setModel(null);
      }
    });
    window.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
    window.setBounds(new Rectangle(680, 100, 460, 620));
    window.setTitle("AST View - " + editor.getSketch().getName());
    JScrollPane sp = new JScrollPane();
    sp.setViewportView(tree);
    window.add(sp);
    pps.whenDone(updateListener);
    pps.registerListener(updateListener);

    tree.addTreeSelectionListener(e -> {
      if (tree.getLastSelectedPathComponent() != null) {
        DefaultMutableTreeNode treeNode =
          (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (treeNode.getUserObject() instanceof ASTNode) {
          ASTNode node = (ASTNode) treeNode.getUserObject();
          pps.whenDone(ps -> {
            SketchInterval si = ps.mapJavaToSketch(node);
            if (!ps.inRange(si)) return;
            EventQueue.invokeLater(() -> editor.highlight(si.tabIndex, si.startTabOffset, si.stopTabOffset));
          });
        }
      }
    });
  }


  void dispose() {
    if (window != null) {
      window.dispose();
    }
  }


  // Thread: worker
  void buildAndUpdateTree(PreprocSketch ps) {
    CompilationUnit cu = ps.compilationUnit;
    if (cu.types().isEmpty()){
      Messages.err("No Type found in CU");
      return;
    }

    Deque<DefaultMutableTreeNode> treeNodeStack = new ArrayDeque<>();

    ASTNode type0 = (ASTNode) cu.types().get(0);
    type0.accept(new ASTVisitor() {
      @Override
      public boolean preVisit2(ASTNode node) {
        treeNodeStack.push(new DefaultMutableTreeNode(node));
        return super.preVisit2(node);
      }

      @Override
      public void postVisit(ASTNode node) {
        if (treeNodeStack.size() > 1) {
          DefaultMutableTreeNode treeNode = treeNodeStack.pop();
          treeNodeStack.peek().add(treeNode);
        }
      }
    });

    DefaultMutableTreeNode codeTree = treeNodeStack.pop();

    EventQueue.invokeLater(() -> {
      if (tree.hasFocus() || window.hasFocus()) {
        return;
      }
      tree.setModel(new DefaultTreeModel(codeTree));
      ((DefaultTreeModel) tree.getModel()).reload();
      tree.validate();
      if (!window.isVisible()) {
        window.setVisible(true);
      }
    });
  }
}