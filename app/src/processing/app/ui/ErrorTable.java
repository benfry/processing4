/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-19 The Processing Foundation

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License version 2
  as published by the Free Software Foundation.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation, Inc.
  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
*/

package processing.app.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.ToolTipManager;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import processing.app.Language;
import processing.app.Problem;


public class ErrorTable extends JTable {
  Editor editor;

  static final String[] columnNames = {
    "",  // the blank column used for spacing
    Language.text("editor.footer.errors.problem"),
    Language.text("editor.footer.errors.tab"),
    Language.text("editor.footer.errors.line")
  };

  static final int DATA_COLUMN = 0;
  static final int PROBLEM_COLUMN = 1;
  static final int TAB_COLUMN = 2;
  static final int LINE_COLUMN = 3;


  public ErrorTable(final Editor editor) {
    super(new DefaultTableModel(columnNames, 0));

    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    this.editor = editor;
    updateTheme();

    //setShowGrid(false);
    setIntercellSpacing(new Dimension(0, 0));

    // be specific about the width of the first column
    TableColumn emptyColumn = columnModel.getColumn(0);
    emptyColumn.setMaxWidth(Editor.LEFT_GUTTER);
    emptyColumn.setMinWidth(Editor.LEFT_GUTTER);

    columnModel.getColumn(PROBLEM_COLUMN).setPreferredWidth(400);
    columnModel.getColumn(TAB_COLUMN).setPreferredWidth(100);
    columnModel.getColumn(LINE_COLUMN).setPreferredWidth(50);

    addMouseListener(new MouseAdapter() {
      @Override
      synchronized public void mouseClicked(MouseEvent e) {
        try {
          int row = ((ErrorTable) e.getSource()).getSelectedRow();
          if (row >= 0 && row < getRowCount()) {
            Object data = getModel().getValueAt(row, DATA_COLUMN);
            int clickCount = e.getClickCount();
            if (clickCount == 1) {
              editor.errorTableClick(data);
            } else if (clickCount > 1) {
              editor.errorTableDoubleClick(data);
            }
            editor.getTextArea().requestFocusInWindow();
//          editor.getErrorChecker().scrollToErrorLine(row);
          }
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      }
    });

    getTableHeader().setReorderingAllowed(false);
    setFillsViewportHeight(true);
    ToolTipManager.sharedInstance().registerComponent(this);
  }


  protected void updateTheme() {
    setBackground(Theme.getColor("errors.bgcolor"));
    getTableHeader().setDefaultRenderer(new GradyHeaderRenderer());
    setDefaultRenderer(Object.class, new GradyRowRenderer());
  }


  public void clearRows() {
    DefaultTableModel dtm = (DefaultTableModel) getModel();
    dtm.setRowCount(0);
  }


  public void addRow(Problem data, String msg, String filename, String line) {
    DefaultTableModel dtm = (DefaultTableModel) getModel();
    dtm.addRow(new Object[] { data, msg, filename, line });
  }


  @Override
  public boolean isCellEditable(int rowIndex, int colIndex) {
    return false;  // Disallow the editing of any cell
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  static class GradyHeaderRenderer extends JLabel implements TableCellRenderer {

    public GradyHeaderRenderer() {
      setFont(Theme.getFont("errors.header.font"));
      setAlignmentX(LEFT_ALIGNMENT);

      setForeground(Theme.getColor("errors.header.fgcolor"));
      setBackground(Theme.getColor("errors.header.bgcolor"));
      setOpaque(true);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean selected,
                                                   boolean focused,
                                                   int row, int column) {

      // Adjust height for magnified displays. The font is scaled properly,
      // but the rows don't automatically use the scaled preferred size.
      // https://github.com/processing/processing/issues/4936
      int high = getPreferredSize().height;
      if (high != 0) {
        JTableHeader header = table.getTableHeader();
        int current = header.getSize().height;
        if (current != high) {
          table.setPreferredSize(new Dimension(table.getWidth(), high));
        }
      }
      setText(value == null ? "" : value.toString());
      return this;
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  static class GradyRowRenderer extends JLabel implements TableCellRenderer {
    Color textColor;
    Color bgColor;
    Color textColorSelected;
    Color bgColorSelected;
    Color bgColorError;
    Color bgColorWarning;

    Color errorIndicatorColor;
    Color warningIndicatorColor;

    public GradyRowRenderer() {
      setFont(Theme.getFont("errors.row.font"));
      setAlignmentX(LEFT_ALIGNMENT);

      textColor = Theme.getColor("errors.row.fgcolor");
      bgColor = Theme.getColor("errors.row.bgcolor");
      textColorSelected = Theme.getColor("errors.selection.fgcolor");
      bgColorSelected = Theme.getColor("errors.selection.bgcolor");
      bgColorError = Theme.getColor("errors.selection.error.bgcolor");
      bgColorWarning = Theme.getColor("errors.selection.warning.bgcolor");

      errorIndicatorColor = Theme.getColor("errors.indicator.error.color");
      warningIndicatorColor = Theme.getColor("errors.indicator.warning.color");

      setOpaque(true);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean selected,
                                                   boolean focused,
                                                   int row, int column) {
      Problem entry = (Problem) table.getValueAt(row, DATA_COLUMN);

      // Adjust row height for magnified displays. The font is scaled properly,
      // but the rows don't automatically use the scaled preferred size.
      // https://github.com/processing/processing/issues/4936
      int high = getPreferredSize().height;
      if (high != 0) {
        int current = table.getRowHeight();
        if (current != high) {
          table.setRowHeight(high);
        }
      }

      if (selected) {
        setForeground(textColorSelected);
        if (entry.isError()) {
          setBackground(bgColorError);
        } else if (entry.isWarning()) {
          setBackground(bgColorWarning);
        } else {
          setBackground(bgColorSelected);
        }
      } else {
        setForeground(textColor);
        setBackground(bgColor);
      }
      if (column == DATA_COLUMN) {
        setText("\u2022");
        setHorizontalAlignment(SwingConstants.CENTER);
        if (entry.isError()) {
          setForeground(errorIndicatorColor);
        } else if (entry.isWarning()) {
          setForeground(warningIndicatorColor);
        } else {
          setText("");  // no dot
        }
      } else if (value == null) {
        setText("");
      } else {
        setHorizontalAlignment(SwingConstants.LEFT);
        setText(value.toString());
      }
      return this;
    }
  }
}
