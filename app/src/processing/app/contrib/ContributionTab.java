/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-15 The Processing Foundation
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

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.*;
import java.util.*;

import javax.swing.*;
import javax.swing.event.*;

import processing.app.*;
import processing.app.ui.Editor;
import processing.app.ui.Toolkit;


public class ContributionTab extends JPanel {
  static final int FILTER_WIDTH = Toolkit.zoom(180);

  Base base;
  ContributionType contribType;
  ManagerFrame managerFrame;

  Contribution.Filter filter;
  JComboBox<String> categoryChooser;
  ListPanel listPanel;
  StatusPanel statusPanel;
  FilterField filterField;

  JLabel categoryLabel;
  JLabel loaderLabel;

  JPanel errorPanel;
  JTextPane errorMessage;
  JButton tryAgainButton;
  JButton closeButton;

  String category;
  ContributionListing contribListing;

  JProgressBar progressBar;


  public ContributionTab(ManagerFrame dialog) {
    this.managerFrame = dialog;
    this.base = dialog.base;
  }


  public ContributionTab(ManagerFrame frame, ContributionType type) {
    this(frame);
    this.contribType = type;

//    long t1 = System.currentTimeMillis();
    filter = contrib -> contrib.getType() == contribType;

//    long t2 = System.currentTimeMillis();
    contribListing = ContributionListing.getInstance();
//    long t3 = System.currentTimeMillis();
    statusPanel = new StatusPanel(this);
//    long t4 = System.currentTimeMillis();
    listPanel = new ListPanel(this, filter, false);
//    long t5 = System.currentTimeMillis();
    // TODO optimize: this line is taking all of the time
    //      (though not after removing the loop in addListener() in 4.0b4)
    //contribListing.addListener(contributionListPanel);
    contribListing.addListPanel(listPanel);
//    long t6 = System.currentTimeMillis();
//    System.out.println("ContributionTab.<init> " + (t4-t1) + " " + (t5-t4) + " " + (t6-t5));
  }


  public void showFrame(boolean error, boolean loading) {
    setLayout();

    listPanel.setVisible(!loading);
    loaderLabel.setVisible(loading);
    errorPanel.setVisible(error);

    listPanel.fireChange();

    validate();
    repaint();
  }


  protected void setLayout() {
    if (progressBar == null) {
      progressBar = new JProgressBar();
      progressBar.setVisible(false);

      createComponents();
      buildErrorPanel();

      loaderLabel = new JLabel(Toolkit.getLibIcon("manager/loader.gif"));
      loaderLabel.setOpaque(false);
      loaderLabel.setBackground(Color.WHITE);
    }

    int scrollBarWidth = listPanel.getScrollBarWidth();

    GroupLayout layout = new GroupLayout(this);
    setLayout(layout);
    layout.setHorizontalGroup(layout
      .createParallelGroup(GroupLayout.Alignment.CENTER)
      .addGroup(layout
                  .createSequentialGroup()
                  .addGap(ManagerFrame.STATUS_WIDTH)
                  .addComponent(filterField,
                                FILTER_WIDTH, FILTER_WIDTH, FILTER_WIDTH)
      .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED,
                       GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE)
                  .addComponent(categoryChooser,
                                ManagerFrame.AUTHOR_WIDTH,
                                ManagerFrame.AUTHOR_WIDTH,
                                ManagerFrame.AUTHOR_WIDTH)
                  .addGap(scrollBarWidth)).addComponent(loaderLabel)
      .addComponent(listPanel).addComponent(errorPanel)
      .addComponent(statusPanel));

    layout.setVerticalGroup(layout
      .createSequentialGroup()
      .addContainerGap()
      .addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                  .addComponent(categoryChooser)
                  .addComponent(filterField))
      .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
      .addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                  .addComponent(loaderLabel)
                  .addComponent(listPanel))
      .addComponent(errorPanel)
      .addComponent(statusPanel, GroupLayout.PREFERRED_SIZE,
                    GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE));
    layout.linkSize(SwingConstants.VERTICAL, categoryChooser, filterField);

    // these will occupy space even if not visible
    layout.setHonorsVisibility(listPanel, false);
    layout.setHonorsVisibility(categoryChooser, false);

    setBackground(Color.WHITE);
    setBorder(null);
  }


  private void createComponents() {
    categoryLabel = new JLabel(Language.text("contrib.category"));

    categoryChooser = new JComboBox<>();
    categoryChooser.setMaximumRowCount(20);
    categoryChooser.setFont(ManagerFrame.NORMAL_PLAIN);

    updateCategoryChooser();

    categoryChooser.addItemListener(e -> {
      category = (String) categoryChooser.getSelectedItem();
      if (ManagerFrame.ANY_CATEGORY.equals(category)) {
        category = null;
      }
      filterLibraries(category, filterField.filters);
    });

    filterField = new FilterField();
  }


  protected void buildErrorPanel() {
    errorPanel = new JPanel();
    GroupLayout layout = new GroupLayout(errorPanel);
    layout.setAutoCreateGaps(true);
    layout.setAutoCreateContainerGaps(true);
    errorPanel.setLayout(layout);
    errorMessage = new JTextPane();
    errorMessage.setEditable(false);
    errorMessage.setContentType("text/html");
    errorMessage.setText("<html><body><center>" +
      "Could not connect to the Processing server.<br>" +
      "Contributions cannot be installed or updated without an Internet connection.<br>" +
      "Please verify your network connection again, then try connecting again." +
      "</center></body></html>");
    Dimension dim = new Dimension(550, 60);
    errorMessage.setMaximumSize(dim);
    errorMessage.setMinimumSize(dim);
    errorMessage.setOpaque(false);

    closeButton = Toolkit.createIconButton("manager/close");
    closeButton.setContentAreaFilled(false);
    closeButton.addActionListener(e -> managerFrame.makeAndShowTab(false, false));
    tryAgainButton = new JButton("Try Again");
    tryAgainButton.setFont(ManagerFrame.NORMAL_PLAIN);
    tryAgainButton.addActionListener(e -> {
      managerFrame.makeAndShowTab(false, true);
      managerFrame.downloadAndUpdateContributionListing();
      //managerFrame.downloadAndUpdateContributionListing(base);
    });
    layout.setHorizontalGroup(layout.createSequentialGroup()
      .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED,
                       GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE)
      .addGroup(layout
                  .createParallelGroup(GroupLayout.Alignment.CENTER)
                  .addComponent(errorMessage)
                  .addComponent(tryAgainButton, StatusPanel.BUTTON_WIDTH,
                                StatusPanel.BUTTON_WIDTH,
                                StatusPanel.BUTTON_WIDTH))
      .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED,
                       GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE)
      .addComponent(closeButton));
    layout.setVerticalGroup(layout.createSequentialGroup()
      .addGroup(layout.createParallelGroup().addComponent(errorMessage)
                  .addComponent(closeButton)).addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED).addComponent(tryAgainButton));
    errorPanel.setBackground(Color.PINK);
    errorPanel.validate();
  }


  private Set<String> listCategories() {
    Set<String> categories = new HashSet<>();

    for (Contribution c : contribListing.allContributions) {
      if (filter.matches(c)) {
        for (String category : c.getCategories()) {
          categories.add(category);
        }
      }
    }
    return categories;
  }


  protected void updateCategoryChooser() {
    if (categoryChooser != null) {
      categoryChooser.removeAllItems();

      Set<String> categories = listCategories();
      if (categories.size() == 1 &&
          categories.contains(Contribution.UNKNOWN_CATEGORY)) {
        // no unique categories
        categoryChooser.setVisible(false);

      } else {
        categoryChooser.addItem(ManagerFrame.ANY_CATEGORY);

        String[] list = categories.toArray(String[]::new);
        Arrays.sort(list);
        for (String category : list) {
          categoryChooser.addItem(category);
        }
        categoryChooser.setVisible(true);
      }
    }
  }


  protected void filterLibraries(String category, List<String> filters) {
    listPanel.filterLibraries(category, filters);
  }


  // TODO Why is this entire set of code only running when Editor
  //      is not null... And what's it doing anyway? Shouldn't it run
  //      on all editors? (The change to getActiveEditor() was made
  //      for 4.0b8 because the Editor may have been closed after the
  //      Contrib Manager was opened.) [fry 220311]
  protected void updateContributionListing() {
    Editor editor = base.getActiveEditor();
    if (editor != null) {
      List<Library> libraries =
        new ArrayList<>(editor.getMode().contribLibraries);

      // Only add Foundation libraries that are installed in the sketchbook
      // https://github.com/processing/processing/issues/3688
      final String sketchbookPath =
        Base.getSketchbookLibrariesFolder().getAbsolutePath();
      for (Library lib : editor.getMode().coreLibraries) {
        if (lib.getLibraryPath().startsWith(sketchbookPath)) {
          libraries.add(lib);
        }
      }

      List<Contribution> contributions = new ArrayList<>(libraries);

//      List<ToolContribution> tools = base.getToolContribs();
//      contributions.addAll(tools);
      contributions.addAll(base.getToolContribs());

//      List<ModeContribution> modes = base.getModeContribs();
//      contributions.addAll(modes);
      contributions.addAll(base.getModeContribs());

//      List<ExamplesContribution> examples = base.getExampleContribs();
//      contributions.addAll(examples);
      contributions.addAll(base.getContribExamples());

      contribListing.updateInstalledList(contributions);

      //listPanel.filterLibraries(category, new ArrayList<>());
      //listPanel.filterDummy(category);
    }
  }


  /*
  protected void setFilterText(String filter) {
    if (filter == null || filter.isEmpty()) {
      filterField.setText("");
    } else {
      filterField.setText(filter);
    }
    filterField.applyFilter();
  }
  */


  class FilterField extends JTextField {
    List<String> filters;

    public FilterField () {
      super("");

      JLabel filterLabel = new JLabel("Filter");
      filterLabel.setFont(ManagerFrame.NORMAL_PLAIN);
      filterLabel.setOpaque(false);

      setFont(ManagerFrame.NORMAL_PLAIN);
      filterLabel.setIcon(Toolkit.getLibIconX("manager/search"));
      JButton removeFilter = Toolkit.createIconButton("manager/remove");
      removeFilter.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 2));
      removeFilter.setBorderPainted(false);
      removeFilter.setContentAreaFilled(false);
      removeFilter.setCursor(Cursor.getDefaultCursor());
      removeFilter.addActionListener(e -> {
        setText("");
        filterField.requestFocusInWindow();
      });
      //searchIcon = new ImageIcon(java.awt.Toolkit.getDefaultToolkit().getImage("NSImage://NSComputerTemplate"));
      setOpaque(false);

      GroupLayout fl = new GroupLayout(this);
      setLayout(fl);
      fl.setHorizontalGroup(fl
        .createSequentialGroup()
        .addComponent(filterLabel)
        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED,
                         GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE)
        .addComponent(removeFilter));

      fl.setVerticalGroup(fl.createSequentialGroup()
                          .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED,
                                           GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE)
                          .addGroup(fl.createParallelGroup()
                          .addComponent(filterLabel)
                          .addComponent(removeFilter))
                          .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED,
                                           GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE));
      removeFilter.setVisible(false);

      filters = new ArrayList<>();

      addFocusListener(new FocusListener() {
        public void focusLost(FocusEvent focusEvent) {
          if (getText().isEmpty()) {
            filterLabel.setVisible(true);
          }
        }

        public void focusGained(FocusEvent focusEvent) {
          filterLabel.setVisible(false);
        }
      });

      getDocument().addDocumentListener(new DocumentListener() {
        public void removeUpdate(DocumentEvent e) {
          removeFilter.setVisible(!getText().isEmpty());
          applyFilter();
        }

        public void insertUpdate(DocumentEvent e) {
          removeFilter.setVisible(!getText().isEmpty());
          applyFilter();
        }

        public void changedUpdate(DocumentEvent e) {
          removeFilter.setVisible(!getText().isEmpty());
          applyFilter();
        }
      });
    }

    public void applyFilter() {
      String filter = getText().toLowerCase();

      // Replace anything but 0-9, a-z, or : with a space
      //filter = filter.replaceAll("[^\\x30-\\x39^\\x61-\\x7a\\x3a]", " ");

      filters = Arrays.asList(filter.split(" "));
      filterLibraries(category, filters);
    }
  }


  public void updateStatusDetail(StatusPanelDetail detail) {
    statusPanel.updateDetail(detail);
  }


  protected void updateAll() {
    Collection<StatusPanelDetail> collection =
      listPanel.detailForContrib.values();
    for (StatusPanelDetail detail : collection) {
      detail.update();
    }
    listPanel.model.fireTableDataChanged();
  }


  protected boolean hasUpdates() {
    return listPanel.getRowCount() > 0;
  }


  public boolean filterHasFocus() {
      return filterField != null && filterField.hasFocus();
  }
}
