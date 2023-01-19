/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2013-23 The Processing Foundation
  Copyright (c) 2011-12 Ben Fry and Casey Reas

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License version 2
  as published by the Free Software Foundation.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License along
  with this program; if not, write to the Free Software Foundation, Inc.
  59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
package processing.app.contrib;

import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.*;
import javax.swing.RowSorter.SortKey;
import javax.swing.table.*;

import processing.app.Util;
import processing.app.ui.Theme;
import processing.app.laf.PdeScrollBarUI;
import processing.app.ui.Toolkit;


// The "Scrollable" implementation and its methods here take care of preventing
// the scrolling area from running exceptionally slowly. Not sure why they're
// necessary in the first place, however. Is that hiding a bigger problem?
// It also allows the description text in the panels to wrap properly.

public class ListPanel extends JPanel implements Scrollable {
  ContributionTab contributionTab;
  Map<Contribution, StatusDetail> detailForContrib =
    new ConcurrentHashMap<>();

  private final Contribution.Filter filter;

  private StatusDetail selectedDetail;
  protected ContributionRowFilter rowFilter;
  protected JTable table;
  protected TableRowSorter<ContributionTableModel> sorter;
  protected ContributionTableModel model;

  // state icons appearing to the left side of the list
  static final int ICON_SIZE = 16;
  Icon upToDateIcon;
  Icon updateAvailableIcon;
  Icon incompatibleIcon;
  Icon downloadingIcon;

  // used in the list next to the creator name
  Icon foundationIcon;

  Color headerFgColor;
  Color headerBgColor;
  Color sectionColor;
  Color rowColor;

  Color textColor;
  Color selectionColor;
  Color textColorIncompatible;
  Color selectionColorIncompatible;

  JScrollPane scrollPane;

  static final SectionHeaderContribution[] sectionHeaders = {
    new SectionHeaderContribution(ContributionType.LIBRARY),
    new SectionHeaderContribution(ContributionType.MODE),
    new SectionHeaderContribution(ContributionType.TOOL),
    new SectionHeaderContribution(ContributionType.EXAMPLES)
  };


  public ListPanel(final ContributionTab contributionTab,
                   final Contribution.Filter filter,
                   final boolean enableSections,
                   final ContributionColumn... columns) {
    this.contributionTab = contributionTab;
    this.filter = filter;

    model = new ContributionTableModel(columns); /* {
      @Override
      public void fireTableDataChanged() {
        new Exception().printStackTrace(System.out);
        super.fireTableDataChanged();
      }
    };*/
    model.enableSections(enableSections);
    table = new JTable(model) {
      @Override
      public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Component c = super.prepareRenderer(renderer, row, column);
        Object rowValue = getValueAt(row, column);
        if (rowValue instanceof SectionHeaderContribution) {
          c.setBackground(sectionColor);
        } else if (isRowSelected(row)) {
          if (((Contribution) rowValue).isCompatible()) {
            c.setBackground(selectionColor);
          } else {
            c.setBackground(selectionColorIncompatible);
          }
        } else {
          c.setBackground(rowColor);
        }
        return c;
      }

      @Override
      public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
        // disallow selection of the header lines
        if (!(getValueAt(rowIndex, columnIndex) instanceof SectionHeaderContribution)) {
          super.changeSelection(rowIndex, columnIndex, toggle, extend);
        }
      }
    };

    scrollPane = new JScrollPane(table);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    scrollPane.getVerticalScrollBar().setUI(new PdeScrollBarUI("manager.scrollbar"));
    scrollPane.setBorder(BorderFactory.createEmptyBorder());
    table.setFillsViewportHeight(true);
    table.setDefaultRenderer(Contribution.class, new ContribCellRenderer());
    table.setRowHeight(Toolkit.zoom(28));
    table.setRowMargin(Toolkit.zoom(6));

    TableColumnModel tcm = table.getColumnModel();
    tcm.setColumnMargin(0);
    tcm.getColumn(0).setMaxWidth(ManagerFrame.STATUS_WIDTH);
    tcm.getColumn(2).setMinWidth(ManagerFrame.AUTHOR_WIDTH);
    tcm.getColumn(2).setMaxWidth(ManagerFrame.AUTHOR_WIDTH);

    table.setShowGrid(false);
    table.setColumnSelectionAllowed(false);
    table.setCellSelectionEnabled(false);
    table.setAutoCreateColumnsFromModel(true);
    table.setAutoCreateRowSorter(false);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    table.getSelectionModel().addListSelectionListener(event -> {
      // This is called twice for each mouse click, once on mouse press with
      // event.getValueIsAdjusting()) set true, and again when the mouse is
      // released where adjusting will be set false. But instead of only
      // responding to one or the other, need to fire on both so that the
      // selection updates while the user drags mouse across the list (and
      // not just when released). Using the arrow keys will only fire once
      // because adjusting will be false (no ongoing drag with keys).
      int row = table.getSelectedRow();
      if (row != -1) {
        Contribution contrib = (Contribution) table.getValueAt(row, 0);
        setSelectedDetail(detailForContrib.get(contrib));
        // Preventing the focus to move out of filterField after typing every character
        if (!contributionTab.filterHasFocus()) {
          table.requestFocusInWindow();
        }
      }
    });

    sorter = new TableRowSorter<>(model);
    table.setRowSorter(sorter);
    rowFilter = new ContributionRowFilter(filter);
    sorter.setRowFilter(rowFilter);
    for (int i = 0; i < model.getColumnCount(); i++) {
      if (model.columns[i] == ContributionColumn.NAME) {
        sorter.setSortKeys(Collections.singletonList(new SortKey(i, SortOrder.ASCENDING)));
      }
      sorter.setComparator(i, model.columns[i].getComparator());
    }
    table.getTableHeader().setDefaultRenderer(new ContribHeaderRenderer());
    table.getTableHeader().setReorderingAllowed(false);
    table.getTableHeader().setResizingAllowed(true);
    table.setVisible(true);

    setOpaque(true);
    setLayout(new BorderLayout());
    add(scrollPane, BorderLayout.CENTER);
  }


  protected void updateTheme() {
    headerFgColor = Theme.getColor("manager.list.header.fgcolor");
    headerBgColor = Theme.getColor("manager.list.header.bgcolor");
    sectionColor = Theme.getColor("manager.list.section.color");

    textColor = Theme.getColor("manager.list.text.color");
    selectionColor = Theme.getColor("manager.list.selection.color");

    textColorIncompatible = Theme.getColor("manager.list.incompatible.text.color");
    selectionColorIncompatible = Theme.getColor("manager.list.incompatible.selection.color");

    rowColor = Theme.getColor("manager.list.background.color");
    table.setBackground(rowColor);

    foundationIcon = Toolkit.renderIcon("manager/foundation", Theme.get("manager.list.foundation.color"), ICON_SIZE);

    upToDateIcon = Toolkit.renderIcon("manager/list-up-to-date", Theme.get("manager.list.icon.color"), ICON_SIZE);
    updateAvailableIcon = Toolkit.renderIcon("manager/list-update-available", Theme.get("manager.list.icon.color"), ICON_SIZE);
    incompatibleIcon = Toolkit.renderIcon("manager/list-incompatible", Theme.get("manager.list.icon.color"), ICON_SIZE);
    downloadingIcon = Toolkit.renderIcon("manager/list-downloading", Theme.get("manager.list.icon.color"), ICON_SIZE);

    ((PdeScrollBarUI) scrollPane.getVerticalScrollBar().getUI()).updateTheme();
  }


  /**
   * Render the pie chart or indeterminate spinner for table rows.
   * @param amount 0..1 for a pie, -1 for indeterminate
   * @param hash unique offset to prevent indeterminate from being in the same position
   * @return properly scalable ImageIcon for rendering in the Table at 1x or 2x
   */
  Icon renderProgressIcon(float amount, int hash) {
//    final int FFS_JAVA2D = ICON_SIZE - 2;
    final float FFS_JAVA2D = ICON_SIZE - 1.5f;
//    final int scale = Toolkit.highResImages() ? 2 : 1;
//    final int dim = ICON_SIZE * scale;
    final int dim = ICON_SIZE * (Toolkit.highResImages() ? 2 : 1);
//    Image image = Toolkit.offscreenGraphics(this, ICON_SIZE, ICON_SIZE);
    Image image = new BufferedImage(dim, dim, BufferedImage.TYPE_INT_ARGB);
//    Graphics2D g2 = (Graphics2D) image.getGraphics();
//    Toolkit.prepareGraphics(g2);
    Graphics2D g2 = Toolkit.prepareGraphics(image);

//    g2.setColor(rowColor);
//    g2.fillRect(0, 0, ICON_SIZE, ICON_SIZE);
    g2.translate(0.5, 0.5);

    g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

    Color iconColor = Theme.getColor("manager.list.icon.color");
    g2.setColor(iconColor);

//    g2.drawOval(0, 0, FFS_JAVA2D, FFS_JAVA2D);
    Ellipse2D circle = new Ellipse2D.Float(0, 0, FFS_JAVA2D, FFS_JAVA2D);

    if (amount != -1) {
      // draw ever-growing pie wedge
      g2.draw(circle);

      int theta = (int) (360 * amount);
//      g2.fillArc(0, 0, ICON_SIZE-1, ICON_SIZE-1, 90, -theta);
      Arc2D wedge = new Arc2D.Float(0, 0, FFS_JAVA2D, FFS_JAVA2D, 90, -theta, Arc2D.PIE);
      g2.fill(wedge);

    } else {
      // draw indeterminate state
      g2.fill(circle);

      g2.translate(FFS_JAVA2D/2, FFS_JAVA2D/2);
      // offset by epoch to avoid integer out of bounds (the date is in 2001)
      final long EPOCH = 1500000000000L + Math.abs((long) hash);
      int angle = (int) ((System.currentTimeMillis() - EPOCH) / 20) % 360;
      g2.rotate(angle);

      g2.setColor(rowColor);
      float lineRadius = FFS_JAVA2D * 0.3f;
      g2.draw(new Line2D.Float(-lineRadius, 0, lineRadius, 0));
    }

    g2.dispose();
    return Toolkit.wrapIcon(image);
  }


  // TODO remove this, yuck [fry 220313]
  protected int getScrollBarWidth() {
    return scrollPane.getVerticalScrollBar().getPreferredSize().width;
  }


  private static int getContributionStatusRank(Contribution c) {
    // Uninstalled items are at the bottom of the sort order
    int pos = 4;

    if (c.isInstalled()) {
      pos = 1;
      if (ContributionListing.getInstance().hasUpdates(c)) {
        pos = 2;
      }
      if (!c.isCompatible()) {
        // This is weird because it means some grayed-out items will
        // show up before non-gray items. We probably need another
        // state icon for 'installed but incompatible' [fry 220116]
        pos = 3;
      }
    }
    return pos;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  class ContribHeaderRenderer extends DefaultTableCellRenderer {

    public ContribHeaderRenderer() {
      setHorizontalTextPosition(LEFT);
      setOpaque(true);
    }

    /**
     * Returns the default table header cell renderer.
     * <P>
     * If the column is sorted, the appropriate icon is retrieved from the
     * current Look and Feel, and a border appropriate to a table header cell
     * is applied.
     * <P>
     * Subclasses may override this method to provide custom content or
     * formatting.
     *
     * @param table the <code>JTable</code>.
     * @param value the value to assign to the header cell
     * @param isSelected This parameter is ignored.
     * @param hasFocus This parameter is ignored.
     * @param row This parameter is ignored.
     * @param column the column of the header cell to render
     * @return the default table header cell renderer
     */
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {
      super.getTableCellRendererComponent(table, value,
              isSelected, hasFocus, row, column);

      setForeground(headerFgColor);
      setText(getText() + getSortText(table, column));
      putClientProperty("FlatLaf.styleClass", "small");
      setBackground(headerBgColor);
      setBorder(null);
      return this;
    }


//    /**
//     * Return an icon suitable to the primary sorted column,
//     * or null if the column is not the primary sort key.
//     *
//     * @param table the <code>JTable</code>.
//     * @param column the column index.
//     * @return the sort icon, or null if the column is unsorted.
//     */
//    private Icon getSortIcon(JTable table, int column) {
//      SortKey sortKey = getSortKey(table);
//      if (sortKey != null && table.convertColumnIndexToView(sortKey.getColumn()) == column) {
//        switch (sortKey.getSortOrder()) {
//          case ASCENDING:
//            return UIManager.getIcon("Table.ascendingSortIcon");
//          case DESCENDING:
//            return UIManager.getIcon("Table.descendingSortIcon");
//        }
//      }
//      return null;
//    }


    private String getSortText(JTable table, int column) {
      SortKey sortKey = getSortKey(table);
      if (sortKey != null && table.convertColumnIndexToView(sortKey.getColumn()) == column) {
        switch (sortKey.getSortOrder()) {
          case ASCENDING -> { return "  ↓"; }
          case DESCENDING -> { return "  ↑"; }
        }
      }
      // if not sorting on this column
      return "";
    }


    /**
     * Returns the current sort key, or null if the column is unsorted.
     *
     * @param table the table
     * @return the SortKey, or null if the column is unsorted
     */
    protected SortKey getSortKey(JTable table) {
      return Optional.ofNullable(table.getRowSorter())
        .map(RowSorter::getSortKeys)
        .map(columns -> columns.isEmpty() ? null : columns.get(0))
        .orElse(null);
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  private class ContribCellRenderer extends DefaultTableCellRenderer {

    @Override
    public void setVerticalAlignment(int alignment) {
      super.setVerticalAlignment(SwingConstants.CENTER);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected,
                                                   boolean hasFocus, int row,
                                                   int column) {
      Contribution contribution = (Contribution) value;
      JLabel label = new JLabel();
      ContributionColumn col = model.columns[column];
      /*
      // Removing this workaround for 4.1.2; likely fixed by change
      // for the concurrent Set used with allContributions list.
      if (value == null) {
        // Working on https://github.com/processing/processing/issues/3667
        //System.err.println("null value seen in getTableCellRendererComponent()");
        // TODO this is now working, but the underlying issue is not fixed
        return label;
      }
      */

      label.setOpaque(true);

      if (value instanceof SectionHeaderContribution && col != ContributionColumn.NAME) {
        return label;
      }
      switch (col) {
        case STATUS, STATUS_NO_HEADER -> configureStatusColumnLabel(label, contribution);
        case NAME -> configureNameColumnLabel(table, label, contribution);
        case AUTHOR -> configureAuthorsColumnLabel(label, contribution);
        case INSTALLED_VERSION -> label.setText(contribution.getBenignVersion());
        case AVAILABLE_VERSION -> label.setText(ContributionListing.getInstance().getLatestPrettyVersion(contribution));
      }

      if (contribution instanceof SectionHeaderContribution) {
        // grouping color for libraries, modes, tools headers in updates panel
        label.setForeground(textColorIncompatible);
      } else if (contribution.isCompatible()) {
        label.setForeground(textColor);
      } else {
        label.setForeground(textColorIncompatible);
      }
      return label;
    }

    private void configureStatusColumnLabel(JLabel label, Contribution contribution) {
      Icon icon = null;
      StatusDetail detail = detailForContrib.get(contribution);

      if (detail != null && (detail.updateInProgress || detail.installInProgress)) {
        // Display "loading" icon if download/install in progress
//        icon = downloadingIcon;
//        float amount = detail.getProgressAmount();
//        icon = (amount == -1) ? downloadingIcon : renderProgressIcon(amount);
        icon = renderProgressIcon(detail.getProgressAmount(), contribution.hashCode());
      } else if (contribution.isInstalled()) {
        if (!contribution.isCompatible()) {
          icon = incompatibleIcon;
        } else if (ContributionListing.getInstance().hasUpdates(contribution)) {
          icon = updateAvailableIcon;
        } else if (detail != null && (detail.installInProgress || detail.updateInProgress)) {
          icon = downloadingIcon;
        } else {
          icon = upToDateIcon;
        }
      }
      label.setIcon(icon);
      label.setHorizontalAlignment(SwingConstants.CENTER);
    }

    private void configureNameColumnLabel(JTable table, JLabel label, Contribution contribution) {
      // Generating ellipses based on fontMetrics
      final Font boldFont = Theme.getFont("manager.list.heavy.font");
      FontMetrics fontMetrics = table.getFontMetrics(boldFont);
      int colSize = table.getColumnModel().getColumn(1).getWidth();
      int currentWidth = fontMetrics.stringWidth(contribution.getName() + " | …");
      String sentence = Util.removeMarkDownLinks(contribution.getSentence());
      StringBuilder text =
        new StringBuilder("<html><body><font face=\"")
          .append(boldFont.getName())
          .append("\">")
          .append(contribution.getName());

      if (sentence.length() == 0) {
        text.append("</font>");
      } else {
        int index;
        for (index = 0; index < sentence.length(); index++) {
          currentWidth += fontMetrics.charWidth(sentence.charAt(index));
          if (currentWidth >= colSize) {
            break;
          }
        }
        text.append(" | </font>").append(sentence, 0, index);
        // Adding ellipses only if text doesn't fit into the column
        if (index != sentence.length()) {
          text.append("…");
        }
      }
      text.append("</body></html>");
      label.setText(text.toString());
    }

    private void configureAuthorsColumnLabel(JLabel label, Contribution contribution) {
      if (contribution.isFoundation()) {
        label.setIcon(foundationIcon);
      }
      String authorList = contribution.getAuthorList();
      String name = Util.removeMarkDownLinks(authorList);
      label.setText(name);
      label.setHorizontalAlignment(SwingConstants.LEFT);
      label.setForeground(Color.BLACK);
      label.setFont(Theme.getFont("manager.list.heavy.font"));
    }
  }

  protected enum ContributionColumn {
    STATUS(" Status"),
    NAME("Name"),
    AUTHOR("Author"),
    INSTALLED_VERSION("Installed"),
    AVAILABLE_VERSION("Available"),
    STATUS_NO_HEADER("");

    final String name;

    ContributionColumn(String name) {
      this.name = name;
    }

    Comparator<Contribution> getComparator() {
      Comparator<Contribution> comparator = Comparator.comparing(Contribution::getType)
        .thenComparingInt(contribution -> contribution instanceof SectionHeaderContribution ? 0 : 1);

      if (this == STATUS || this == STATUS_NO_HEADER) {
        return comparator.thenComparingInt(ListPanel::getContributionStatusRank);
      } else if (this == AUTHOR) {
        return comparator.thenComparing(contribution -> Util.removeMarkDownLinks(contribution.getAuthorList()));
      } else {  // default case, or this == NAME
        return comparator.thenComparing(Contribution::getName, String.CASE_INSENSITIVE_ORDER);
      }
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  static class ContributionTableModel extends AbstractTableModel {
    ContributionColumn[] columns = {
      ContributionColumn.STATUS,
      ContributionColumn.NAME,
      ContributionColumn.AUTHOR
    };
    boolean sectionsEnabled;

    ContributionTableModel(ContributionColumn... columns) {
      if (columns.length > 0) {
        this.columns = columns;
      }
    }

    @Override
    public int getRowCount() {
      return ContributionListing.getAllContribs().size() + (sectionsEnabled ? 4 : 0);
    }

    @Override
    public int getColumnCount() {
      return columns.length;
    }

    @Override
    public String getColumnName(int column) {
      if (column < 0 || column > columns.length) {
        return "";
      }
      return columns[column].name;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return Contribution.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      Set<Contribution> allContribs = ContributionListing.getAllContribs();
      if (rowIndex >= allContribs.size()) {
        return sectionHeaders[rowIndex - allContribs.size()];
      }
      return allContribs.stream().skip(rowIndex).findFirst().orElse(null);
    }

    public void enableSections(boolean enable) {
      this.sectionsEnabled = enable;
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  static protected boolean matches(Contribution contrib, String typed) {
    String search = ".*" + typed.toLowerCase() + ".*";

    return (matchField(contrib.getName(), search) ||
      matchField(contrib.getSentence(), search) ||
      matchField(contrib.getAuthorList(), search) ||
      matchField(contrib.getParagraph(), search));
  }


  static private boolean matchField(String field, String regex) {
    return (field != null) && field.toLowerCase().matches(regex);
  }


  static class ContributionRowFilter extends RowFilter<ContributionTableModel, Integer> {
    Contribution.Filter contributionFilter;
    String categoryFilter;
    List<String> stringFilters = Collections.emptyList();

    ContributionRowFilter(Contribution.Filter contributionFilter) {
      this.contributionFilter = contributionFilter;
    }

    public void setCategoryFilter(String categoryFilter) {
      this.categoryFilter = categoryFilter;
    }

    public void setStringFilters(List<String> filters) {
      this.stringFilters = filters;
    }

    @Override
    public boolean include(Entry<? extends ContributionTableModel, ? extends Integer> entry) {
      Contribution contribution = (Contribution) entry.getValue(0);
//      if (contributionTab.getName().equals("updates")) {
//        System.out.println(contributionTab.getName() + " checking " + contribution.getName() + " for inclusion " + includeContribution(contribution));
//      }
      if (contribution instanceof SectionHeaderContribution) {
        return includeSection((SectionHeaderContribution) contribution);
      }
      return includeContribution(contribution);
    }

    private boolean includeContribution(Contribution contribution) {
      return contributionFilter.matches(contribution) &&
        Optional.ofNullable(categoryFilter).map(contribution::hasCategory).orElse(true) &&
        stringFilters.stream().allMatch(pattern -> matches(contribution, pattern));
    }

    private boolean includeSection(SectionHeaderContribution section) {
      return ContributionListing.getAllContribs().stream()
        .filter(contribution -> contribution.getType() == section.getType())
        .anyMatch(this::includeContribution);
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /**
   * Fake contribution that's just a section header for the updates panel.
   */
  static class SectionHeaderContribution extends Contribution {
    ContributionType type;

    SectionHeaderContribution(ContributionType type) {
      this.type = type;

      switch (type) {
        case LIBRARY -> this.name = "Libraries";
        case MODE -> this.name = "Modes";
        case TOOL -> this.name = "Tools";
        case EXAMPLES -> this.name = "Examples";
      }
    }

    @Override
    public ContributionType getType() {
      return type;
    }

    @Override
    public boolean isInstalled() {
      return false;
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  // Thread: EDT
  protected void contributionAdded(final Contribution contribution) {
    if (filter.matches(contribution)) {
      if (!detailForContrib.containsKey(contribution)) {
        StatusDetail newPanel = contributionTab.createStatusDetail();
        detailForContrib.put(contribution, newPanel);
        newPanel.setContrib(contribution);
//        model.fireTableDataChanged();
      }
    }
  }


  // Thread: EDT
  protected void contributionRemoved(final Contribution contribution) {
    if (filter.matches(contribution)) {
      StatusDetail panel = detailForContrib.get(contribution);
      if (panel != null) {
        detailForContrib.remove(contribution);
      }
//      model.fireTableDataChanged();
    }
  }


  // Thread: EDT
  protected void contributionChanged(final Contribution oldContrib,
                                     final Contribution newContrib) {
    if (filter.matches(oldContrib)) {
      StatusDetail detail = detailForContrib.get(oldContrib);
      detailForContrib.remove(oldContrib);
      detail.setContrib(newContrib);
      detailForContrib.put(newContrib, detail);
//      model.fireTableDataChanged();
    }
  }


  // Thread: EDT
  protected void updateFilter(String category, List<String> filters) {
    rowFilter.setCategoryFilter(category);
    rowFilter.setStringFilters(filters);
//    model.fireTableDataChanged();
    updateModel();
  }


  protected void updateModel() {
    model.fireTableDataChanged();
  }


  // Thread: EDT
  private void setSelectedDetail(StatusDetail contribDetail) {
    contributionTab.applyDetail(contribDetail);

    if (selectedDetail != contribDetail) {
      selectedDetail = contribDetail;
      requestFocusInWindow();
    }
  }


  protected StatusDetail getSelectedDetail() {
    return selectedDetail;
  }


  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredSize();
  }


  /**
   * Amount to scroll to reveal a new page of items
   */
  @Override
  public int getScrollableBlockIncrement(Rectangle visibleRect,
                                         int orientation, int direction) {
    if (orientation == SwingConstants.VERTICAL) {
      int blockAmount = visibleRect.height;
      if (direction > 0) {
        visibleRect.y += blockAmount;
      } else {
        visibleRect.y -= blockAmount;
      }

      blockAmount +=
        getScrollableUnitIncrement(visibleRect, orientation, direction);
      return blockAmount;
    }
    return 0;
  }


  /**
   * Amount to scroll to reveal the rest of something we are on or a new item
   */
  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    if (orientation != SwingConstants.VERTICAL) {
      return 0;
    }
    int lastHeight = 0;
    int height = 0;
    int bottomOfScrollArea = visibleRect.y + visibleRect.height;

    for (Component c : getComponents()) {
      Dimension d = c.getPreferredSize();

      int nextHeight = height + d.height;

      if (direction > 0) {
        // scrolling down
        if (nextHeight > bottomOfScrollArea) {
          return nextHeight - bottomOfScrollArea;
        }
      } else if (nextHeight > visibleRect.y) {
        if (visibleRect.y != height) {
          return visibleRect.y - height;
        } else {
          return visibleRect.y - lastHeight;
        }
      }

      lastHeight = height;
      height = nextHeight;
    }
    return 0;
  }


  @Override
  public boolean getScrollableTracksViewportHeight() {
    return false;
  }


  @Override
  public boolean getScrollableTracksViewportWidth() {
    return true;
  }
}
