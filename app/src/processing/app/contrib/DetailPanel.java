/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2013-16 The Processing Foundation
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
//import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import javax.swing.*;
//import javax.swing.border.EmptyBorder;
//import javax.swing.event.HyperlinkEvent;
//import javax.swing.text.html.HTMLDocument;
//import javax.swing.text.html.HTMLEditorKit;
//import javax.swing.text.html.StyleSheet;

import processing.app.*;
import processing.app.ui.Toolkit;


/**
 * Panel that expands and gives a brief overview of a library when clicked.
 */
class DetailPanel extends JPanel {
  static private final String PROGRESS_BAR_CONSTRAINT =
    "Install/Remove Progress Bar Panel";

  static private final String BUTTON_CONSTRAINT =
    "Install/Remove Button Panel";

  static private final String INCOMPATIBILITY_BLUR =
    "This contribution is not compatible with the current revision of Processing";

  private final ListPanel listPanel;
  private final ContributionListing contribListing = ContributionListing.getInstance();

  static private final int BUTTON_WIDTH = Toolkit.zoom(100);
//  static private Icon foundationIcon;

  /**
   * Should only be set through setContribution(),
   * otherwise UI components will not be updated.
   */
  private Contribution contrib;

//  private boolean alreadySelected;
  //  private JTextPane descriptionPane;
//  private JLabel notificationLabel;
//  private JButton updateButton;
//  private JButton installRemoveButton;

  JProgressBar installProgressBar;

//  final private JPopupMenu contextMenu;
//  final private JMenuItem openFolder;

  final private JPanel barButtonCardPane;
  private CardLayout barButtonCardLayout;

//  static private final String installText = Language.text("contrib.install");
//  static private final String removeText = Language.text("contrib.remove");
//  static private final String undoText = Language.text("contrib.undo");

  boolean updateInProgress;
  boolean installInProgress;
  boolean removeInProgress;

//  private String description;


  DetailPanel(ListPanel contributionListPanel) {
//    if (foundationIcon == null) {
//      foundationIcon = Toolkit.getLibIconX("icons/foundation", 32);
//    }

    listPanel = contributionListPanel;
    barButtonCardPane = new JPanel();

//    contextMenu = new JPopupMenu();
//    openFolder = new JMenuItem("Open Folder");
//    openFolder.addActionListener(e -> {
//      if (contrib instanceof LocalContribution) {
//        File folder = ((LocalContribution) contrib).getFolder();
//        Platform.openFolder(folder);
//      }
//    });

    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

    addPaneComponents();

    setBackground(listPanel.getBackground());
    setOpaque(true);
    setSelected(false);

//    setExpandListener(this, new MouseAdapter() {
//      public void mousePressed(MouseEvent e) {
//        if (contrib.isCompatible(Base.getRevision())) {
//          listPanel.setSelectedPanel(DetailPanel.this);
//        } else {
//          setErrorMessage(contrib.getName() +
//                          " cannot be used with this version of Processing");
//        }
//      }
//    });
  }


  /**
   * Create the widgets for the header panel that is visible
   * when the library panel is not clicked.
   */
  private void addPaneComponents() {
    System.out.println("DetailPanel.addPaneComponents()");
    setLayout(new BorderLayout());

//    descriptionPane = new JTextPane();
//    descriptionPane.setInheritsPopupMenu(true);
//    descriptionPane.setEditable(false);  // why would this ever be true?
//    Insets margin = descriptionPane.getMargin();
//    margin.bottom = 0;
//    descriptionPane.setMargin(margin);
//    descriptionPane.setContentType("text/html");

//    HTMLEditorKit kit = new HTMLEditorKit();
//    HTMLEditorKit kit = Toolkit.createHtmlEditorKit();
//    StyleSheet stylesheet = new StyleSheet();
//    stylesheet.addRule(StatusPanel.getBodyStyle());
////    stylesheet.addRule("a { color: #000000; text-decoration:underline; text-decoration-style: dotted; }");
//    kit.setStyleSheet(stylesheet);
//    HTMLDocument hd = (HTMLDocument) kit.createDefaultDocument();
//    descriptionPane.setEditorKit(kit);
//    descriptionPane.setDocument(hd);

//    descriptionPane.setOpaque(false);
//    if (UIManager.getLookAndFeel().getID().equals("Nimbus")) {
//      descriptionPane.setBackground(new Color(0, 0, 0, 0));
//    }

//    descriptionPane.setBorder(new EmptyBorder(4, 7, 7, 7));
//    descriptionPane.setHighlighter(null);
//    descriptionPane.addHyperlinkListener(e -> {
//      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
//        // for 3.2.3, added the isSelected() prompt here,
//        // rather than adding/removing the listener repeatedly
//        if (isSelected()) {
//          if (enableHyperlinks && e.getURL() != null) {
//            Platform.openURL(e.getURL().toString());
//          }
//        }
//      }
//    });
//
//    add(descriptionPane, BorderLayout.CENTER);

//    JPanel updateBox = new JPanel();
//    updateBox.setLayout(new BorderLayout());

//    notificationLabel = new JLabel();
//    notificationLabel.setInheritsPopupMenu(true);
//    notificationLabel.setVisible(false);
//    notificationLabel.setOpaque(false);
//    notificationLabel.setFont(ManagerFrame.SMALL_PLAIN);

//    {
//      updateButton = new JButton("Update");
//      updateButton.setInheritsPopupMenu(true);
//      Dimension dim =
//        new Dimension(BUTTON_WIDTH, updateButton.getPreferredSize().height);
//      updateButton.setMinimumSize(dim);
//      updateButton.setPreferredSize(dim);
//      updateButton.setOpaque(false);
//      updateButton.setVisible(false);
//
//      updateButton.addActionListener(e -> update());
//    }

//    updateBox.add(updateButton, BorderLayout.EAST);
//    updateBox.add(notificationLabel, BorderLayout.WEST);
//    updateBox.setBorder(new EmptyBorder(4, 7, 7, 7));
//    updateBox.setOpaque(false);
//    add(updateBox, BorderLayout.SOUTH);

    JPanel rightPane = new JPanel();
    rightPane.setInheritsPopupMenu(true);
    rightPane.setOpaque(false);
    rightPane.setLayout(new BoxLayout(rightPane, BoxLayout.Y_AXIS));
    rightPane.setMinimumSize(new Dimension(BUTTON_WIDTH, 1));
    add(rightPane, BorderLayout.EAST);

    barButtonCardLayout = new CardLayout();
    barButtonCardPane.setLayout(barButtonCardLayout);
    barButtonCardPane.setInheritsPopupMenu(true);
    barButtonCardPane.setOpaque(false);
    barButtonCardPane.setMinimumSize(new Dimension(BUTTON_WIDTH, 1));

    {
      installProgressBar = new JProgressBar();
      installProgressBar.setInheritsPopupMenu(true);
      installProgressBar.setStringPainted(true);
      resetInstallProgressBarState();
      Dimension dim =
        new Dimension(BUTTON_WIDTH,
          installProgressBar.getPreferredSize().height);
      installProgressBar.setPreferredSize(dim);
      installProgressBar.setMaximumSize(dim);
      installProgressBar.setMinimumSize(dim);
      installProgressBar.setOpaque(false);
      installProgressBar.setAlignmentX(CENTER_ALIGNMENT);
      installProgressBar.setFont(ManagerFrame.NORMAL_PLAIN);
    }

//    installRemoveButton = new JButton(" ");
//    installRemoveButton.setInheritsPopupMenu(true);
//    installRemoveButton.addActionListener(e -> {
//      String mode = installRemoveButton.getText();
//      if (mode.equals(installText)) {
//        install();
//      } else if (mode.equals(removeText)) {
//        remove();
//      } else if (mode.equals(undoText)) {
//        unflag();
//      }
//    });

//    Dimension installButtonDimensions = installRemoveButton.getPreferredSize();
//    installButtonDimensions.width = BUTTON_WIDTH;
//    installRemoveButton.setPreferredSize(installButtonDimensions);
//    installRemoveButton.setMaximumSize(installButtonDimensions);
//    installRemoveButton.setMinimumSize(installButtonDimensions);
//    installRemoveButton.setOpaque(false);
//    installRemoveButton.setAlignmentX(CENTER_ALIGNMENT);

//    JPanel barPane = new JPanel();
//    barPane.setOpaque(false);

//    JPanel buttonPane = new JPanel();
//    buttonPane.setOpaque(false);
//    buttonPane.add(installRemoveButton);

//    barButtonCardPane.add(buttonPane, BUTTON_CONSTRAINT);
//    barButtonCardPane.add(barPane, PROGRESS_BAR_CONSTRAINT);
    barButtonCardLayout.show(barButtonCardPane, BUTTON_CONSTRAINT);

    rightPane.add(barButtonCardPane);

    // Set the minimum size of this pane to be the sum
    // of the height of the progress bar and install button
    Dimension dim =
      new Dimension(BUTTON_WIDTH,
        new JButton().getPreferredSize().height);
//                    installRemoveButton.getPreferredSize().height);
    rightPane.setMinimumSize(dim);
    rightPane.setPreferredSize(dim);
  }


//  /**
//   * Clear the 'marked for deletion' flag. (Formerly 'undo')
//   */
//  private void unflag() {
//    clearStatusMessage();
//    if (contrib instanceof LocalContribution) {
//      LocalContribution installed = getLocalContrib();
//      installed.setDeletionFlag(false);
//      contribListing.replaceContribution(contrib, contrib);
//      for (Contribution contribElement : contribListing.allContributions) {
//        if (contrib.getType().equals(contribElement.getType())) {
//          if (contribElement.isDeletionFlagged() ||
//            contribElement.isUpdateFlagged()) {
//            break;
//          }
//        }
//      }
//    }
//  }


  private void reorganizePaneComponents() {
    BorderLayout layout = (BorderLayout) this.getLayout();
//    remove(layout.getLayoutComponent(BorderLayout.SOUTH));
    remove(layout.getLayoutComponent(BorderLayout.EAST));

//    JPanel updateBox = new JPanel();
//    updateBox.setLayout(new BorderLayout());
//    updateBox.setInheritsPopupMenu(true);
//    updateBox.add(notificationLabel, BorderLayout.WEST);
//    updateBox.setBorder(new EmptyBorder(4, 7, 7, 7));
//    updateBox.setOpaque(false);
//    add(updateBox, BorderLayout.SOUTH);

    JPanel rightPane = new JPanel();
    rightPane.setInheritsPopupMenu(true);
    rightPane.setOpaque(false);
    rightPane.setLayout(new BoxLayout(rightPane, BoxLayout.Y_AXIS));
    rightPane.setMinimumSize(new Dimension(BUTTON_WIDTH, 1));
    add(rightPane, BorderLayout.EAST);

//    if (updateButton.isVisible() && !removeInProgress && !contrib.isDeletionFlagged()) {
    if (!removeInProgress && contrib != null && !contrib.isDeletionFlagged()) {
//      JPanel updateRemovePanel = new JPanel();
//      updateRemovePanel.setLayout(new FlowLayout());
//      updateRemovePanel.setOpaque(false);
//      updateRemovePanel.add(updateButton);
//      updateRemovePanel.setInheritsPopupMenu(true);
//      updateRemovePanel.add(installRemoveButton);
//      updateBox.add(updateRemovePanel, BorderLayout.EAST);

//      JPanel barPane = new JPanel();
//      barPane.setOpaque(false);
//      barPane.setInheritsPopupMenu(true);
//      rightPane.add(barPane);

      if (updateInProgress) {
        barButtonCardLayout.show(barButtonCardPane, PROGRESS_BAR_CONSTRAINT);
      }
    } else {
//      updateBox.add(updateButton, BorderLayout.EAST);
      barButtonCardPane.removeAll();

//      JPanel barPane = new JPanel();
//      barPane.setOpaque(false);
//      barPane.setInheritsPopupMenu(true);

//      JPanel buttonPane = new JPanel();
//      buttonPane.setOpaque(false);
//      buttonPane.setInheritsPopupMenu(true);
//      buttonPane.add(installRemoveButton);

//      barButtonCardPane.add(buttonPane, BUTTON_CONSTRAINT);
//      barButtonCardPane.add(barPane, PROGRESS_BAR_CONSTRAINT);
      if (installInProgress || removeInProgress || updateInProgress) {
        barButtonCardLayout.show(barButtonCardPane, PROGRESS_BAR_CONSTRAINT);
      } else {
        barButtonCardLayout.show(barButtonCardPane, BUTTON_CONSTRAINT);
      }
      rightPane.add(barButtonCardPane);
    }

    Dimension progressDim = installProgressBar.getPreferredSize();
//    Dimension installDim = installRemoveButton.getPreferredSize();
    progressDim.width = BUTTON_WIDTH;
//    progressDim.height = Math.max(progressDim.height, installDim.height);
    rightPane.setMinimumSize(progressDim);
    rightPane.setPreferredSize(progressDim);
  }


//  private void setExpandListener(Component component,
//                                 MouseListener expandListener) {
//    // If it's a JButton, adding the listener will make this stick on OS X
//    // https://github.com/processing/processing/issues/3172
//    if (!(component instanceof JButton)) {
//      component.addMouseListener(expandListener);
//      if (component instanceof Container) {
//        for (Component child : ((Container) component).getComponents()) {
//          setExpandListener(child, expandListener);
//        }
//      }
//    }
//  }


  private void blurContributionPanel(Component component) {
    component.setFocusable(false);
    component.setEnabled(false);
    if (component instanceof JComponent) {
      ((JComponent) component).setToolTipText(INCOMPATIBILITY_BLUR);
    }
    if (component instanceof Container) {
      for (Component child : ((Container) component).getComponents()) {
        blurContributionPanel(child);
      }
    }
  }


  public Contribution getContrib() {
    return contrib;
  }


  private LocalContribution getLocalContrib() {
    return (LocalContribution) contrib;
  }


  public void setContrib(Contribution contrib) {
//    System.out.println("DetailPanel.setContribution " + contrib.name);
//    new Exception("DetailPanel.setContrib " + contrib.name).printStackTrace(System.out);
    this.contrib = contrib;

//    if (contrib.isSpecial()) {
//      JLabel iconLabel = new JLabel(foundationIcon);
//      iconLabel.setBorder(new EmptyBorder(4, 7, 7, 7));
//      iconLabel.setVerticalAlignment(SwingConstants.TOP);
//      add(iconLabel, BorderLayout.WEST);
//    }

//    description = StatusPanel.updateDescription(contrib);
//    descriptionPane.setText(description);

//    if (contribListing.hasUpdates(contrib) && contrib.isCompatible(Base.getRevision())) {
//      StringBuilder versionText = new StringBuilder();
//      versionText.append("<html><body><i>");
//      //noinspection StatementWithEmptyBody
//      if (contrib.isUpdateFlagged() || contrib.isDeletionFlagged()) {
//        // Already marked for deletion, see requiresRestart() notes below.
//        // versionText.append("To finish an update, reinstall this contribution after restarting.");
//
//      } else {
//        String latestVersion = contribListing.getLatestPrettyVersion(contrib);
//        if (latestVersion != null) {
//          versionText.append("New version (")
//            .append(latestVersion)
//            .append(") available.");
//        } else {
//          versionText.append("New version available.");
//        }
//      }
//      versionText.append("</i></body></html>");
//      notificationLabel.setText(versionText.toString());
//      notificationLabel.setVisible(true);
//    } else {
//      notificationLabel.setText("");
//      notificationLabel.setVisible(false);
//    }

//    updateButton.setEnabled(true);
//    updateButton.setVisible((contribListing.hasUpdates(contrib) && !contrib.isUpdateFlagged() && !contrib.isDeletionFlagged()) || updateInProgress);

    if (contrib.isDeletionFlagged()) {
//      installRemoveButton.setText(undoText);

    } else if (contrib.isInstalled()) {
//      installRemoveButton.setText(removeText);
//      installRemoveButton.setVisible(true);
//      installRemoveButton.setEnabled(!contrib.isUpdateFlagged());
      reorganizePaneComponents();
    } else {
//      installRemoveButton.setText(installText);
    }

//    contextMenu.removeAll();
//    if (contrib.isInstalled()) {
//      contextMenu.add(openFolder);
//      setComponentPopupMenu(contextMenu);
//    } else {
//      setComponentPopupMenu(null);
//    }

    if (!contrib.isCompatible(Base.getRevision())) {
      blurContributionPanel(this);
    }
  }


  private void installContribution(AvailableContribution info) {
    if (info.link == null) {
      setErrorMessage(Language.interpolate("contrib.unsupported_operating_system", info.getType()));
    } else {
      installContribution(info, info.link);
    }
  }


  private void finishInstall(boolean error) {
    resetInstallProgressBarState();
//    installRemoveButton.setEnabled(!contrib.isUpdateFlagged());

    if (error) {
      setErrorMessage(Language.text("contrib.download_error"));
    }
    barButtonCardLayout.show(barButtonCardPane, BUTTON_CONSTRAINT);
    installInProgress = false;
    if (updateInProgress) {
      updateInProgress = false;
    }
//    updateButton.setVisible(contribListing.hasUpdates(contrib) && !contrib.isUpdateFlagged());
    setSelected(true);
  }


  private void installContribution(AvailableContribution ad, String url) {
//    installRemoveButton.setEnabled(false);

    try {
      URL downloadUrl = new URL(url);
      installProgressBar.setVisible(true);

      ContribProgressBar downloadProgress = new ContribProgressBar(installProgressBar) {
        public void finishedAction() { }

        public void cancelAction() {
          finishInstall(false);
        }
      };

      ContribProgressBar installProgress = new ContribProgressBar(installProgressBar) {
        public void finishedAction() {
          finishInstall(isError());
        }

        public void cancelAction() {
          finishedAction();
        }
      };

      ContributionManager.downloadAndInstall(getBase(), downloadUrl, ad,
                                             downloadProgress, installProgress,
                                             getStatusPanel());

    } catch (MalformedURLException e) {
      Messages.showWarning(Language.text("contrib.errors.install_failed"),
                           Language.text("contrib.errors.malformed_url"), e);
      // not sure why we'd re-enable the button if it had an error...
      //installRemoveButton.setEnabled(true);
    }
  }


  protected void resetInstallProgressBarState() {
    installProgressBar.setString(Language.text("contrib.progress.starting"));
    installProgressBar.setIndeterminate(false);
    installProgressBar.setValue(0);
    installProgressBar.setVisible(false);
  }


  /**
   * Should be called whenever this component is selected (clicked on)
   * or unselected, even if it is already selected.
   */
  void setSelected(boolean selected) {
//    new Exception("DetailPanel.setSelected()").printStackTrace(System.out);

    // Only enable hyperlinks if this component is already selected.
    // Why? Because otherwise if the user happened to click on what is
    // now a hyperlink, it will be opened as the mouse is released.
//    boolean enableHyperlinks = alreadySelected;

//    if (contrib != null) {
//      updateButton.setVisible((contribListing.hasUpdates(contrib) && !contrib.isUpdateFlagged() && !contrib.isDeletionFlagged()) || updateInProgress);
//      updateButton.setEnabled(contribListing.listDownloadSuccessful());
//    }
//    installRemoveButton.setVisible(isSelected() || installRemoveButton.getText().equals(Language.text("contrib.remove")) || updateInProgress);
//    installRemoveButton.setEnabled(installRemoveButton.getText().equals(Language.text("contrib.remove")) || contribListing.listDownloadSuccessful());
    reorganizePaneComponents();

    // Update style of hyperlinks
    //setSelectionStyle(descriptionPane, selected);

//    alreadySelected = selected;
  }


  boolean isSelected() {
    return listPanel.getSelectedPanel() == this;
  }


  public void install() {
    clearStatusMessage();
    installInProgress = true;
    barButtonCardLayout.show(barButtonCardPane, PROGRESS_BAR_CONSTRAINT);
    if (contrib instanceof AvailableContribution) {
      installContribution((AvailableContribution) contrib);
      contribListing.replaceContribution(contrib, contrib);
    }
  }


  public void update() {
    clearStatusMessage();
    updateInProgress = true;
    if (contrib.getType().requiresRestart()) {
//      installRemoveButton.setEnabled(false);
      installProgressBar.setVisible(true);
      installProgressBar.setIndeterminate(true);

      ContribProgressBar progress = new UpdateProgressBar(installProgressBar);
      getLocalContrib().removeContribution(getBase(), progress, getStatusPanel());
    } else {
//      updateButton.setEnabled(false);
//      installRemoveButton.setEnabled(false);
      AvailableContribution ad =
        contribListing.getAvailableContribution(contrib);
      installContribution(ad, ad.link);
    }
  }


  class UpdateProgressBar extends ContribProgressBar {
    public UpdateProgressBar(JProgressBar progressBar) {
      super(progressBar);
    }

    public void finishedAction() {
      resetInstallProgressBarState();
//      updateButton.setEnabled(false);
      AvailableContribution ad =
        contribListing.getAvailableContribution(contrib);
      String url = ad.link;
      installContribution(ad, url);
    }

    @Override
    public void cancelAction() {
      resetInstallProgressBarState();
      //listPanel.contributionTab.statusPanel.setMessage("");  // same as clear?
      clearStatusMessage();
      updateInProgress = false;
//      installRemoveButton.setEnabled(true);
      if (contrib.isDeletionFlagged()) {
        getLocalContrib().setUpdateFlag(true);
        getLocalContrib().setDeletionFlag(false);
        contribListing.replaceContribution(contrib, contrib);
      }

//      if (isModeActive(contrib)) {
//        updateButton.setEnabled(true);
//      //} else {
//        // TODO: remove or uncomment if the button was added
//        //listPanel.contributionTab.restartButton.setVisible(true);
//      }
    }
  }


  public void remove() {
    clearStatusMessage();
    if (contrib.isInstalled() && contrib instanceof LocalContribution) {
      removeInProgress = true;
      barButtonCardLayout.show(barButtonCardPane, PROGRESS_BAR_CONSTRAINT);
//      updateButton.setEnabled(false);
//      installRemoveButton.setEnabled(false);
      installProgressBar.setVisible(true);
      installProgressBar.setIndeterminate(true);

      ContribProgressBar monitor = new RemoveProgressBar(installProgressBar);
      getLocalContrib().removeContribution(getBase(), monitor, getStatusPanel());
    }
  }


  class RemoveProgressBar extends ContribProgressBar {
    public RemoveProgressBar(JProgressBar progressBar) {
      super(progressBar);
    }

    private void preAction() {
      resetInstallProgressBarState();
      removeInProgress = false;
//      installRemoveButton.setEnabled(true);
      reorganizePaneComponents();
      setSelected(true); // Needed for smooth working. Dunno why, though...
    }

    public void finishedAction() {
      // Finished uninstalling the library
      preAction();
    }

    public void cancelAction() {
      preAction();

//      if (isModeActive(contrib)) {
//        updateButton.setEnabled(true);
//      //} else {
//        // TODO: remove or uncomment if the button was added
//        //contributionTab.restartButton.setVisible(true);
//      }
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  // Can't be called from the constructor because the path isn't set all the
  // way down. However, Base does not change over time. More importantly,
  // though, is that the functions being called in Base are somewhat suspect
  // since they're contribution-related, and should perhaps live closer.
  private Base getBase() {
    return listPanel.contributionTab.editor.getBase();
  }


//  private boolean isModeActive(Contribution contrib) {
//    if (contrib.getType() == ContributionType.MODE) {
//      ModeContribution m = (ModeContribution) contrib;
//      for (Editor e : getBase().getEditors()) {
//        if (e.getMode().equals(m.getMode())) {
//          return true;
//        }
//      }
//    }
//    return false;
//  }


  private StatusPanel getStatusPanel() {
    return listPanel.contributionTab.statusPanel;  // TODO this is gross [fry]
  }


  private void clearStatusMessage() {
    getStatusPanel().clearMessage();
  }


  private void setErrorMessage(String message) {
    getStatusPanel().setErrorMessage(message);
  }
}
