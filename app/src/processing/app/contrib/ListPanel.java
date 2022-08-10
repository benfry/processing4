/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2013-22 The Processing Foundation
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
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.*;
import javax.swing.RowSorter.SortKey;
import javax.swing.table.*;

import processing.app.Base;
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
//  static public Comparator<Contribution> COMPARATOR =
//    Comparator.comparing(o -> o.getName().toLowerCase());
//  TreeMap<Contribution, StatusPanelDetail> detailForContrib =
//    new TreeMap<>(ContributionListing.COMPARATOR);
//  TreeMap<Contribution, StatusPanelDetail> detailForContrib =
//    new TreeMap<>(Comparator.comparing(o -> o.getName().toLowerCase()));
  Map<Contribution, StatusDetail> detailForContrib =
    new ConcurrentHashMap<>();

  private final Contribution.Filter filter;

  private StatusDetail selectedDetail;
  protected ContributionRowFilter rowFilter;
  protected JTable table;
  protected TableRowSorter<ContributionTableModel> sorter;
  protected ContributionTableModel model;

  // state icons appearing to the left side of the list
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

  static final SectionHeaderContribution[] sections = {
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

    this.rowFilter = new ContributionRowFilter(filter);

//    if (upToDateIcon == null) {
//      upToDateIcon = Toolkit.getLibIconX("manager/up-to-date");
//      updateAvailableIcon = Toolkit.getLibIconX("manager/update-available");
//      incompatibleIcon = Toolkit.getLibIconX("manager/incompatible");
//      foundationIcon = Toolkit.getLibIconX("icons/foundation", 16);
//      downloadingIcon = Toolkit.getLibIconX("manager/downloading");
//    }

    setOpaque(true);
    model = new ContributionTableModel(columns);
    model.enableSections(enableSections);
    table = new JTable(model) {
      @Override
      public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Component c = super.prepareRenderer(renderer, row, column);
        Object rowValue = getValueAt(row, column);
        if (rowValue instanceof SectionHeaderContribution) {
          c.setBackground(sectionColor);
        } else if (isRowSelected(row)) {
          if (((Contribution) rowValue).isCompatible(Base.getRevision())) {
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
    table.setDefaultRenderer(Contribution.class, new ContribStatusRenderer());
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
    sorter.setRowFilter(this.rowFilter);
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

    foundationIcon = Toolkit.renderIcon("manager/foundation", Theme.get("manager.list.foundation.color"), 16);

    upToDateIcon = Toolkit.renderIcon("manager/list-up-to-date", Theme.get("manager.list.icon.color"), 16);
    updateAvailableIcon = Toolkit.renderIcon("manager/list-update-available", Theme.get("manager.list.icon.color"), 16);
    incompatibleIcon = Toolkit.renderIcon("manager/list-incompatible", Theme.get("manager.list.icon.color"), 16);
    downloadingIcon = Toolkit.renderIcon("manager/list-downloading", Theme.get("manager.list.icon.color"), 16);

    ((PdeScrollBarUI) scrollPane.getVerticalScrollBar().getUI()).updateTheme();
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
      if (!c.isCompatible(Base.getRevision())) {
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

//      JTableHeader tableHeader = table.getTableHeader();
//      if (tableHeader != null) {
//        setForeground(tableHeader.getForeground());
//      }
      setForeground(headerFgColor);
      //setText(getText() + "\u2191\u2193");
      setText(getText() + getSortText(table, column));
      putClientProperty("FlatLaf.styleClass", "small");
//      setFont(ManagerFrame.SMALL_PLAIN);
      //setIcon(getSortIcon(table, column));
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
          case ASCENDING:
            return "  \u2193";
          case DESCENDING:
            return "  \u2191";
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


  private class ContribStatusRenderer extends DefaultTableCellRenderer {

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
      if (value == null) {
        // Working on https://github.com/processing/processing/issues/3667
        //System.err.println("null value seen in getTableCellRendererComponent()");
        // TODO this is now working, but the underlying issue is not fixed
        return label;
      }

      label.setOpaque(true);

      if (value instanceof SectionHeaderContribution && col != ContributionColumn.NAME) {
        return label;
      }
      switch (col) {
        case STATUS:
        case STATUS_NO_HEADER:
          configureStatusColumnLabel(label, contribution);
          break;
        case NAME:
          configureNameColumnLabel(table, label, contribution);
          break;
        case AUTHOR:
          configureAuthorsColumnLabel(label, contribution);
          break;
        case INSTALLED_VERSION:
          label.setText(contribution.getBenignVersion());
          break;
        case AVAILABLE_VERSION:
          label.setText(ContributionListing.getInstance().getLatestPrettyVersion(contribution));
          break;
      }

      if (contribution.isCompatible(Base.getRevision())) {
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
        icon = downloadingIcon;
      } else if (contribution.isInstalled()) {
        if (!contribution.isCompatible(Base.getRevision())) {
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
//      final Font boldFont = ManagerFrame.NORMAL_BOLD;
      final Font boldFont = Theme.getFont("manager.list.heavy.font");
      FontMetrics fontMetrics = table.getFontMetrics(boldFont);
      int colSize = table.getColumnModel().getColumn(1).getWidth();
      int currentWidth = fontMetrics.stringWidth(contribution.getName() + " | ...");
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
          text.append("...");
        }
      }
      text.append("</body></html>");
      label.setText(text.toString());
//      label.setFont(ManagerFrame.NORMAL_PLAIN);
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
      //label.setFont(ManagerFrame.NORMAL_BOLD);
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
      return ContributionListing.getInstance().allContributions.size() + (sectionsEnabled ? 4 : 0);
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
      final Set<Contribution> allContribs =
        ContributionListing.getInstance().allContributions;
      if (rowIndex >= allContribs.size()) {
        return sections[rowIndex - allContribs.size()];
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
      return ContributionListing.getInstance().allContributions.stream()
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
        case LIBRARY: this.name = "Libraries"; break;
        case MODE: this.name = "Modes"; break;
        case TOOL: this.name = "Tools"; break;
        case EXAMPLES: this.name = "Examples"; break;
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
//    if (true || filter.matches(contribution)) {
    if (filter.matches(contribution)) {
//      System.out.println(contributionTab.contribType + " tab: " +
//        "added " + contribution.name);
      //new Exception().printStackTrace(System.out);

      if (!detailForContrib.containsKey(contribution)) {
//        System.out.println(contributionTab.contribType + " tab: " +
//          "actually adding " + contribution.name);
//      new Exception().printStackTrace(System.out);
//        long t1 = System.currentTimeMillis();
        //StatusPanelDetail newPanel = new StatusPanelDetail(this);
        StatusDetail newPanel =
          new StatusDetail(contributionTab.base, contributionTab.statusPanel);
        detailForContrib.put(contribution, newPanel);
        newPanel.setContrib(contribution);
//      add(newPanel);
        model.fireTableDataChanged();
//        long t2 = System.currentTimeMillis();
//        System.out.println("ListPanel.contributionAdded() " + (t2 - t1) + " " + contribution.getTypeName() + " " + contribution.getName());
      }
//    } else {
//      System.out.println("ignoring contrib " + contribution.getName());
    }
  }


  // Thread: EDT
  protected void contributionRemoved(final Contribution contribution) {
    if (filter.matches(contribution)) {
//      System.out.println(contributionTab.contribType + " tab: " +
//        "removed " + contribution.name);
//    if (true || filter.matches(contribution)) {
      StatusDetail panel = detailForContrib.get(contribution);
      if (panel != null) {
        detailForContrib.remove(contribution);
      }
      model.fireTableDataChanged();
      updateUI();
    }
  }


  // Thread: EDT
  protected void contributionChanged(final Contribution oldContrib,
                                  final Contribution newContrib) {
    if (filter.matches(oldContrib)) {
//    if (true || filter.matches(oldContrib)) {
//      System.out.println(contributionTab.contribType + " tab: " +
//        "changed " + oldContrib + " -> " + newContrib);
//      new Exception().printStackTrace(System.out);
      StatusDetail detail = detailForContrib.get(oldContrib);
//      if (panel == null) {
////        System.out.println("panel null for " + newContrib);
//        contributionAdded(newContrib);
//      } else {
        detailForContrib.remove(oldContrib);
        detail.setContrib(newContrib);
        detailForContrib.put(newContrib, detail);
        model.fireTableDataChanged();
//      }
    }
  }


  // Thread: EDT
  protected void filterLibraries(String category, List<String> filters) {
    rowFilter.setCategoryFilter(category);
    rowFilter.setStringFilters(filters);
    model.fireTableDataChanged();
  }


  protected void fireChange() {
    model.fireTableDataChanged();
  }
//  protected void filterDummy(String category) {
//    System.out.println("LAST CHANCE DUMMY");
////    rowFilter.setCategoryFilter(category);
////    rowFilter.setStringFilters(new ArrayList<>());
//    model.fireTableDataChanged();
//  }


  // Thread: EDT
  private void setSelectedDetail(StatusDetail contribDetail) {
    contributionTab.updateStatusDetail(contribDetail);

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


  public int getRowCount() {
    // This will count section headers, but it is only used to check if any rows are shown
    return sorter.getViewRowCount();
  }
}
