/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-22 The Processing Foundation
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
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.*;
import java.util.List;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import processing.app.Base;
import processing.app.laf.PdeComboBoxUI;
import processing.app.ui.Theme;
import processing.app.ui.Toolkit;


public class ContributionTab extends JPanel {
  Base base;
  ContributionType contribType;
  ManagerFrame managerFrame;

  Contribution.Filter filter;
  JComboBox<String> categoryChooser;
  ListPanel listPanel;
  StatusPanel statusPanel;
  FilterField filterField;

  /*
  JLabel loaderLabel;

  JPanel errorPanel;
  JTextPane errorMessage;
  JButton tryAgainButton;
  JButton closeButton;
  */

  String category;

  //protected JProgressBar progressBar;  // TODO this is not actually used?


  public ContributionTab(ManagerFrame dialog) {
    this.managerFrame = dialog;
    this.base = dialog.base;

    /*
    buildErrorPanel();

    loaderLabel = new JLabel(Toolkit.getLibIcon("manager/loader.gif"));
    loaderLabel.setOpaque(false);
    */
  }


  public ContributionTab(ManagerFrame frame, ContributionType type) {
    this(frame);

    contribType = type;
    filter = contrib -> contrib.getType() == contribType;

    listPanel = new ListPanel(this, filter, false);
    // TODO StatusPanel init is after listPanel is created because it calls
    //      updateTheme() which needs it, but yuck, too messy [fry 220504]
    statusPanel = new StatusPanel(this);
    initLayout();

    ContributionListing.getInstance().addListPanel(listPanel);
  }


  /** Only used for debugging. */
  @Override
  public String getName() {
    return (contribType == null) ? "updates" : contribType.toString();
  }


  protected void updateTheme() {
    setBackground(Theme.getColor("manager.list.background.color"));

    if (filterField != null) {
      filterField.updateTheme();
    }

    listPanel.updateTheme();
    statusPanel.updateTheme();

    //closeButton.setIcon(Toolkit.renderIcon("manager/close", Theme.get("manager.error.close.icon.color"), 16));
  }


  /*
  protected void activate() {
    System.out.println("activating " + contribType);
    //updateContributionListing();
    ContributionListing.getInstance().updateInstalledList(base.getInstalledContribs());
    updateCategoryChooser();
    //rebuildLayout(false, false);
    rebuildLayout();
  }
  */


  /*
//  public void rebuildLayout(boolean error, boolean loading) {
  public void rebuildLayout() {
    setLayout();

//    listPanel.setVisible(!loading);
//    loaderLabel.setVisible(loading);
//    errorPanel.setVisible(error);

    listPanel.fireChange();  // wtf, really? every time? [fry 230111]

    validate();
    repaint();
  }
  */


  protected void initLayout() {
    if (categoryChooser == null) {
      createComponents();
    }
    /*
    if (loaderLabel == null) {
      createComponents();
      buildErrorPanel();

      loaderLabel = new JLabel(Toolkit.getLibIcon("manager/loader.gif"));
      loaderLabel.setOpaque(false);
      //loaderLabel.setBackground(Color.WHITE);
    }
    */

    final int scrollBarWidth = listPanel.getScrollBarWidth();
    final int filterWidth = Toolkit.zoom(180);

    GroupLayout layout = new GroupLayout(this);
    setLayout(layout);
    layout.setHorizontalGroup(layout
      .createParallelGroup(GroupLayout.Alignment.CENTER)
      .addGroup(layout
                  .createSequentialGroup()
                  .addGap(ManagerFrame.STATUS_WIDTH)
                  .addComponent(filterField,
                                filterWidth, filterWidth, filterWidth)
      .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED,
                       GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE)
      .addComponent(categoryChooser,
                    ManagerFrame.AUTHOR_WIDTH,
                    ManagerFrame.AUTHOR_WIDTH,
                    ManagerFrame.AUTHOR_WIDTH)
      .addGap(scrollBarWidth))
      //.addComponent(loaderLabel)
      .addComponent(listPanel)
      //.addComponent(errorPanel)
      .addComponent(statusPanel));

    layout.setVerticalGroup(layout
      .createSequentialGroup()
      .addContainerGap()
      .addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                  .addComponent(categoryChooser)
                  .addComponent(filterField))
      // https://github.com/processing/processing4/issues/520
      .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
      .addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                  //.addComponent(loaderLabel)
                  .addComponent(listPanel))
                  //.addComponent(errorPanel)
                  .addComponent(statusPanel,
                                GroupLayout.PREFERRED_SIZE,
                                GroupLayout.DEFAULT_SIZE,
                                GroupLayout.PREFERRED_SIZE));
    layout.linkSize(SwingConstants.VERTICAL, categoryChooser, filterField);

    // these will occupy space even if not visible
    layout.setHonorsVisibility(listPanel, false);
    layout.setHonorsVisibility(categoryChooser, false);

    setBorder(null);
  }


  private void createComponents() {
//    categoryLabel = new JLabel(Language.text("contrib.category"));

    categoryChooser = new JComboBox<>();
    categoryChooser.setMaximumRowCount(20);
//    categoryChooser.setFont(ManagerFrame.NORMAL_PLAIN);

    updateCategoryChooser();

    categoryChooser.addItemListener(e -> {
      category = (String) categoryChooser.getSelectedItem();
      if (ManagerFrame.ANY_CATEGORY.equals(category)) {
        category = null;
      }
      //filterLibraries(category, filterField.filterWords);
      updateFilter();
    });

    filterField = new FilterField();
  }


  /*
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

//    closeButton = Toolkit.createIconButton("manager/close");
    closeButton = new JButton();
    closeButton.setContentAreaFilled(false);
    closeButton.addActionListener(e -> managerFrame.rebuildTabLayouts(false, false));
    tryAgainButton = new JButton("Try Again");
//    tryAgainButton.setFont(ManagerFrame.NORMAL_PLAIN);
    tryAgainButton.addActionListener(e -> {
      managerFrame.rebuildTabLayouts(false, true);
      managerFrame.downloadAndUpdateContributionListing();
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
  */


  private Set<String> listCategories() {
    Set<String> categories = new HashSet<>();

    for (Contribution c : ContributionListing.getAllContribs()) {
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
      // this is a complicated way of saying "if this is the libraries tab"
      //if (categories.size() == 1 &&
      //    categories.contains(Contribution.UNKNOWN_CATEGORY)) {
      if (categories.isEmpty() || // listing not loaded yet
          contribType != ContributionType.LIBRARY) {
        // Add dummy item for sizing purposes
        // https://github.com/processing/processing4/issues/520
        categoryChooser.addItem("NULL");
        // If no unique categories, hide the category chooser
        categoryChooser.setVisible(false);

      } else {
        // Build the category chooser dropdown from the list
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


  /**
   * Filter the libraries based on category and filter words.
   */
  protected void updateFilter() {
    listPanel.updateFilter(category, filterField.filterWords);
  }


  protected boolean filterHasFocus() {
    return filterField != null && filterField.hasFocus();
  }


  // TODO Why is this entire set of code only running when Editor
  //      is not null... And what's it doing anyway? Shouldn't it run
  //      on all editors? (The change to getActiveEditor() was made
  //      for 4.0b8 because the Editor may have been closed after the
  //      Contrib Manager was opened.) [fry 220311]
  /*
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
      contributions.addAll(base.getContribTools());

//      List<ModeContribution> modes = base.getModeContribs();
//      contributions.addAll(modes);
      contributions.addAll(base.getContribModes());

//      List<ExamplesContribution> examples = base.getExampleContribs();
//      contributions.addAll(examples);
      contributions.addAll(base.getContribExamples());

      ContributionListing.getInstance().updateInstalledList(contributions);

      //listPanel.filterLibraries(category, new ArrayList<>());
      //listPanel.filterDummy(category);
    }
  }
  */


  protected StatusDetail createStatusDetail() {
    return new StatusDetail(base, statusPanel);
  }


  protected void applyDetail(StatusDetail detail) {
    statusPanel.applyDetail(detail);
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  class FilterField extends JTextField {
    List<String> filterWords = new ArrayList<>();

    JLabel placeholderLabel;
    JButton resetButton;

//    Color textColor;
//    Color placeholderColor;
//    Color backgroundColor;

    FilterField() {
//      super("");  // necessary?

      // a label that appears above the component when empty and not focused
      placeholderLabel = new JLabel("Filter");
//      filterLabel.setFont(ManagerFrame.NORMAL_PLAIN);
      placeholderLabel.setOpaque(false);
//      filterLabel.setOpaque(true);
//      setFont(ManagerFrame.NORMAL_PLAIN);
//      placeholderLabel.setIcon(Toolkit.getLibIconX("manager/search"));

//      JButton removeFilter = Toolkit.createIconButton("manager/remove");
      resetButton = new JButton();
      resetButton.setBorder(new EmptyBorder(0, 0, 0, 2));
      resetButton.setBorderPainted(false);
      resetButton.setContentAreaFilled(false);
      resetButton.setCursor(Cursor.getDefaultCursor());
      resetButton.addActionListener(e -> {
        setText("");
        FilterField.this.requestFocusInWindow();
      });

      //setOpaque(false);
      setOpaque(true);
      setBorder(new EmptyBorder(3, 7, 3, 7));

      GroupLayout fl = new GroupLayout(this);
      setLayout(fl);
      fl.setHorizontalGroup(fl
        .createSequentialGroup()
        .addComponent(placeholderLabel)
        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED,
                         GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE)
        .addComponent(resetButton));

      fl.setVerticalGroup(fl.createSequentialGroup()
                          .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED,
                                           GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE)
                          .addGroup(fl.createParallelGroup()
                          .addComponent(placeholderLabel)
                          .addComponent(resetButton))
                          .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED,
                                           GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE));
      resetButton.setVisible(false);

      addFocusListener(new FocusListener() {
        public void focusLost(FocusEvent focusEvent) {
          if (getText().isEmpty()) {
            placeholderLabel.setVisible(true);
          }
        }

        public void focusGained(FocusEvent focusEvent) {
          placeholderLabel.setVisible(false);
        }
      });

      getDocument().addDocumentListener(new DocumentListener() {
        public void removeUpdate(DocumentEvent e) {
          resetButton.setVisible(!getText().isEmpty());
          applyFilter();
        }

        public void insertUpdate(DocumentEvent e) {
          resetButton.setVisible(!getText().isEmpty());
          applyFilter();
        }

        public void changedUpdate(DocumentEvent e) {
          resetButton.setVisible(!getText().isEmpty());
          applyFilter();
        }
      });

      updateTheme();
    }

    private void applyFilter() {
      String filter = getText().toLowerCase();

      // Replace anything but 0-9, a-z, or : with a space
      //filter = filter.replaceAll("[^\\x30-\\x39^\\x61-\\x7a\\x3a]", " ");

      filterWords = Arrays.asList(filter.split(" "));
      //filterLibraries(category, filterWords);
      updateFilter();
    }

    protected void updateTheme() {
      placeholderLabel.setForeground(Theme.getColor("manager.search.placeholder.color"));
      placeholderLabel.setIcon(Toolkit.renderIcon("manager/search", Theme.get("manager.search.icon.color"), 16));
      resetButton.setIcon(Toolkit.renderIcon("manager/search-reset", Theme.get("manager.search.icon.color"), 16));

      /*
      if (getUI() instanceof PdeTextFieldUI) {
        ((PdeTextFieldUI) getUI()).updateTheme();
      } else {
        setUI(new PdeTextFieldUI("manager.search"));
      }
      */

//        System.out.println(getBorder().getBorderInsets(FilterField.this));
      //setBorder(new EmptyBorder(0, 5, 0, 5));
      //setBorder(null);
//        setBorder(new EmptyBorder(3, 7, 3, 7));

      setBackground(Theme.getColor("manager.search.background.color"));
      setForeground(Theme.getColor("manager.search.text.color"));

      // not yet in use, so leaving out for now
      //filterField.setDisabledTextColor(Theme.getColor("manager.search.disabled.text.color"));

      setSelectionColor(Theme.getColor("manager.search.selection.background.color"));
      setSelectedTextColor(Theme.getColor("manager.search.selection.text.color"));

      setCaretColor(Theme.getColor("manager.search.caret.color"));

      //SwingUtilities.updateComponentTreeUI(this);

      if (categoryChooser.getUI() instanceof PdeComboBoxUI) {
        ((PdeComboBoxUI) categoryChooser.getUI()).updateTheme();
      } else {
        categoryChooser.setUI(new PdeComboBoxUI("manager.categories"));
      }

      /*
      textColor = Theme.getColor("manager.list.search.text.color");
      placeholderColor = Theme.getColor("manager.list.search.placeholder.color");
      backgroundColor = Theme.getColor("manager.list.search.background.color");

      setBackground(backgroundColor);
      */
    }
  }
}
