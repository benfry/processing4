/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - https://processing.org

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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.text.DateFormat;
import java.util.Date;

import javax.swing.GroupLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextPane;
import javax.swing.LayoutStyle;
import javax.swing.SwingConstants;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLDocument;

import processing.app.Language;
import processing.app.Util;
import processing.app.laf.PdeButtonUI;
import processing.app.laf.PdeProgressBarUI;
import processing.app.ui.Theme;
import processing.app.ui.Toolkit;
import processing.app.Platform;


/**
 * The panel that explains a particular Contribution in the Manager.
 * One for each tab (Libraries, Modes, etc) in the Manager.
 */
class StatusPanel extends JPanel {
  static final int LABEL_WIDTH = Toolkit.zoom(480);
  static final int BUTTON_WIDTH = Toolkit.zoom(150);

  Icon foundationIcon;
  /*
  static Icon foundationIcon;
  static Icon installIcon;
  static Icon updateIcon;
  static Icon removeIcon;
  */

  String detailStyle;

  JTextPane label;
  JButton installButton;
  JProgressBar progressBar;
  JLabel updateLabel;
  JButton updateButton;
  JButton removeButton;
  GroupLayout layout;
  JLabel iconLabel;  // Foundation Icon to the left of the detail
  ContributionTab contributionTab;


  public StatusPanel(final ContributionTab contributionTab) {
    this.contributionTab = contributionTab;
    buildLayout();
  }


  protected void buildLayout() {
    iconLabel = new JLabel();
    iconLabel.setHorizontalAlignment(SwingConstants.CENTER);

    label = new JTextPane();
    label.setEditable(false);
    label.setOpaque(false);
    label.setContentType("text/html");
    label.addHyperlinkListener(e -> {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        if (e.getURL() != null) {
          Platform.openURL(e.getURL().toString());
        }
      }
    });
    //installButton = Toolkit.createIconButton("Install", installIcon);
    installButton = new JButton("Install");
    //installButton.setDisabledIcon(installIcon);
//    installButton.setFont(buttonFont);
    installButton.setHorizontalAlignment(SwingConstants.LEFT);
    installButton.addActionListener(e -> {
      installButton.setEnabled(false);
      StatusDetail currentDetail =
        contributionTab.listPanel.getSelectedDetail();
      currentDetail.installContrib(contributionTab.listPanel);
      applyDetail(currentDetail);
    });

    buildProgressBar();

    updateLabel = new JLabel(" ");
//    updateLabel.setFont(buttonFont);
    updateLabel.setHorizontalAlignment(SwingConstants.CENTER);

    //updateButton = Toolkit.createIconButton("Update", updateIcon);
    updateButton = new JButton("Update");
//    updateButton.setFont(buttonFont);
    updateButton.setHorizontalAlignment(SwingConstants.LEFT);
    updateButton.addActionListener(e -> {
      updateButton.setEnabled(false);
      StatusDetail currentDetail =
        contributionTab.listPanel.getSelectedDetail();
      currentDetail.updateContrib(contributionTab.listPanel);
      applyDetail(currentDetail);
    });

    //removeButton = Toolkit.createIconButton("Remove", removeIcon);
    removeButton = new JButton("Remove");
//    removeButton.setFont(buttonFont);
    removeButton.setHorizontalAlignment(SwingConstants.LEFT);
    removeButton.addActionListener(e -> {
      removeButton.setEnabled(false);
      StatusDetail currentPanel =
        contributionTab.listPanel.getSelectedDetail();
      currentPanel.removeContrib();
      applyDetail(currentPanel);
    });

    layout = new GroupLayout(this);
    this.setLayout(layout);

    layout.setAutoCreateContainerGaps(true);
    layout.setAutoCreateGaps(true);

    layout.setHorizontalGroup(layout
      .createSequentialGroup()
      .addGap(0)
      .addComponent(iconLabel,
                    ManagerFrame.STATUS_WIDTH,
                    ManagerFrame.STATUS_WIDTH,
                    ManagerFrame.STATUS_WIDTH)
      .addGap(0)
      .addComponent(label, LABEL_WIDTH, LABEL_WIDTH, LABEL_WIDTH)
      .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED,
                       GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE)
      .addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                  .addComponent(installButton,
                                BUTTON_WIDTH, BUTTON_WIDTH, BUTTON_WIDTH)
                  .addComponent(progressBar)
                  .addComponent(updateLabel,
                                BUTTON_WIDTH, BUTTON_WIDTH, BUTTON_WIDTH)
                  .addComponent(updateButton)
                  .addComponent(removeButton))
      .addGap(12));  // make buttons line up relative to the scrollbar

    layout.setVerticalGroup(layout
      .createParallelGroup(GroupLayout.Alignment.LEADING)
      .addComponent(iconLabel)
      .addComponent(label)
      .addGroup(layout.createSequentialGroup()
                  .addComponent(installButton)
                  .addGroup(layout.createParallelGroup()
                              .addComponent(progressBar)
                              .addComponent(updateLabel))
                  .addComponent(updateButton).addComponent(removeButton)));

    layout.linkSize(SwingConstants.HORIZONTAL,
                    installButton, progressBar, updateButton, removeButton);

    installButton.setEnabled(false);
    updateButton.setEnabled(false);
    removeButton.setEnabled(false);
    updateLabel.setVisible(true);

    // Makes the label take up space even though not visible
    layout.setHonorsVisibility(updateLabel, false);

    //validate();  // necessary? [fry 230114]
  }


  protected void buildProgressBar() {
    progressBar = new JProgressBar();
    progressBar.setStringPainted(true);
    progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);
    //progressBar.setOpaque(true);

    resetProgressBar();

    final int high = progressBar.getPreferredSize().height;
    Dimension dim = new Dimension(BUTTON_WIDTH, high);
    progressBar.setPreferredSize(dim);
    progressBar.setMaximumSize(dim);
    progressBar.setMinimumSize(dim);
  }


  protected void resetProgressBar() {
    // TODO is this overkill for a reset? is this really only being used
    //      when we mean to call setVisible(false)? [fry 220311]
    progressBar.setString(Language.text("contrib.progress.starting"));
    progressBar.setIndeterminate(false);
    progressBar.setValue(0);
    progressBar.setVisible(false);
  }


  protected void updateTheme() {
    setBackground(Theme.getColor("manager.panel.background.color"));

    Font detailFont = Theme.getFont("manager.panel.font");
    detailStyle =
      "body { " +
      "  margin: 0; " +
      "  padding: 0;" +
      "  font-family: " + detailFont.getName() + ", sans-serif;" +
      "  font-size: " + detailFont.getSize() + "px;" +
      "  color: " + Theme.get("manager.panel.text.color") + ";" +
      "}" +
      "a { " +
      "  color: " + Theme.get("manager.panel.link.color") + ";" +
      "  text-decoration: none;" +
      "}";

    updateLabel.setForeground(Theme.getColor("manager.panel.text.color"));
    foundationIcon = Toolkit.renderIcon("manager/foundation", Theme.get("manager.panel.foundation.color"), 32);

    updateButtonTheme(installButton, "install");
    updateButtonTheme(updateButton, "update");
    updateButtonTheme(removeButton, "remove");

    StatusDetail currentDetail =
      // I'm not circuitous, *you're* circuitous.
      contributionTab.listPanel.getSelectedDetail();
    if (currentDetail != null) {
      applyDetail(currentDetail);
      // TODO This only updates the scroll bar, and only the current selection.
      //      So if the theme is updated with the manager opened, selecting
      //      another contrib and installing/updating/removing it will result
      //      in a progress bar being used that's not the correct theme.
      //      But the progress bars need to be removed anyway. [fry 230114]
      currentDetail.updateTheme();
    }

    if (progressBar.getUI() instanceof PdeProgressBarUI) {
      ((PdeProgressBarUI) progressBar.getUI()).updateTheme();
    } else {
      progressBar.setUI(new PdeProgressBarUI("manager.progress"));
    }

    /*
    if (installButton.getUI() instanceof PdeButtonUI) {
      ((PdeButtonUI) installButton.getUI()).updateTheme();
    } else {
      installButton.setUI(new PdeButtonUI("manager.button"));
    }
    */

    /*
    installButton.setForeground(Theme.getColor("manager.button.text.color"));
    installButton.setBackground(Theme.getColor("manager.button.background.color"));
    //installButton.setBorder(new EmptyBorder(2, 14, 2, 14));
    //installButton.setBorder(new LineBorder(Color.ORANGE, 1, true));

    // draws correctly, but specifying rounded doesn't help
    // still doesn't work for disabled state, so what's the point
    installButton.setBorder(new CompoundBorder(
      new LineBorder(Color.ORANGE, 1), //, true),
      new EmptyBorder(2, 14, 2, 14)
    ));
    */

//    installButton.setBorder(new EmptyBorder(0, 0, 0, 0));
//    installButton.setMargin(new Insets(2, 14, 2, 14));
//    installButton.putClientProperty(FlatClientProperties.OUTLINE, Color.GREEN);
//    installButton.putClientProperty("Component.borderWidth", "10");  // does not work
  }


  static protected void updateButtonTheme(JButton button, String name) {
    if (button.getUI() instanceof PdeButtonUI) {
      ((PdeButtonUI) button.getUI()).updateTheme();
    } else {
      button.setUI(new PdeButtonUI("manager.button"));
    }

    button.setIcon(Toolkit.renderIcon("manager/panel-" + name, Theme.get("manager.button.enabled.icon.color"), 16));
    button.setPressedIcon(Toolkit.renderIcon("manager/panel-" + name, Theme.get("manager.button.pressed.icon.color"), 16));
    button.setDisabledIcon(Toolkit.renderIcon("manager/panel-" + name, Theme.get("manager.button.disabled.icon.color"), 16));
  }


  void setErrorMessage(String message) {
    if (label != null) {
      label.setText(message);
      label.repaint();
    }
  }


  void clearMessage() {
    if (label != null) {
      label.setText(null);
      label.repaint();
    }
  }


  static private final String REMOVE_RESTART_MESSAGE =
    String.format("<i>%s</i>", Language.text("contrib.messages.remove_restart"));

  static private final String INSTALL_RESTART_MESSAGE =
    String.format("<i>%s</i>", Language.text("contrib.messages.install_restart"));

  static private final String UPDATE_RESTART_MESSAGE =
    String.format("<i>%s</i>", Language.text("contrib.messages.update_restart"));

  static String updateDescription(Contribution contrib) {
    String fontFace = "<font face=\"" + Toolkit.getSansFontName() + "\">";

    StringBuilder desc = new StringBuilder();
    desc.append("<html><body>");
    desc.append(fontFace);
    if (contrib.getUrl() == null) {
      desc.append(contrib.getName());
    } else {
      desc.append("<a href=\"");
      desc.append(contrib.getUrl());
      desc.append("\">");
      desc.append(contrib.getName());
      desc.append("</a>");
    }
    desc.append("</font> ");

    String prettyVersion = contrib.getPrettyVersion();
    if (prettyVersion != null) {
      desc.append(prettyVersion);
    }
    desc.append(" <br/>");

    String authorList = contrib.getAuthorList();
    if (authorList != null && !authorList.isEmpty()) {
      desc.append(Util.markDownLinksToHtml(contrib.getAuthorList()));
    }
    desc.append("<br/><br/>");

    if (contrib.isDeletionFlagged()) {
      desc.append(REMOVE_RESTART_MESSAGE);
    } else if (contrib.isRestartFlagged()) {
      desc.append(INSTALL_RESTART_MESSAGE);
    } else if (contrib.isUpdateFlagged()) {
      desc.append(UPDATE_RESTART_MESSAGE);
    } else {
      String sentence = contrib.getSentence();
      if (sentence == null || sentence.isEmpty()) {
        sentence = "<i>" + Language.text("contrib.errors.description_unavailable") + "</i>";
      } else {
        sentence = Util.sanitizeHtmlTags(sentence);
        sentence = Util.markDownLinksToHtml(sentence);
      }
      desc.append(sentence);
    }

    long lastUpdatedUTC = contrib.getLastUpdated();
    if (lastUpdatedUTC != 0) {
      DateFormat dateFormatter = DateFormat.getDateInstance(DateFormat.MEDIUM);
      Date lastUpdatedDate = new Date(lastUpdatedUTC);
      if (prettyVersion != null) {
        desc.append(", ");
      }
      desc.append("Last Updated on ");
      desc.append(dateFormatter.format(lastUpdatedDate));
    }

    desc.append("</body></html>");
    return desc.toString();
  }


  protected void applyDetail(StatusDetail detail) {
//    System.out.println("rebuilding status detail for " + detail.getContrib().name);
//    new Exception("rebuilding status detail for " + detail.getContrib().name).printStackTrace(System.out);
    Contribution contrib = detail.getContrib();

    iconLabel.setIcon(contrib.isFoundation() ? foundationIcon : null);
    label.setText(updateDescription(contrib));
    ((HTMLDocument) label.getDocument()).getStyleSheet().addRule(detailStyle);

    ContributionListing listing = ContributionListing.getInstance();

    updateButton.setEnabled((listing.hasUpdates(contrib) &&
                             !contrib.isUpdateFlagged()) &&
                            !detail.updateInProgress);

    String latestVersion = listing.getLatestPrettyVersion(contrib);
    String currentVersion = contrib.getPrettyVersion();

    installButton.setEnabled(!contrib.isInstalled() &&
                             contrib.isCompatible() &&
                             !detail.installInProgress);

    if (contrib.isCompatible()) {
      if (installButton.isEnabled()) {
        if (latestVersion != null) {
          updateLabel.setText(latestVersion + " available");
        } else {
          updateLabel.setText("Available");
        }
      } else {  // install disabled
        if (currentVersion != null) {
          updateLabel.setText(currentVersion + " installed");
        } else {
          updateLabel.setText("Installed");
        }
      }
    } else {  // contrib not compatible
      if (currentVersion != null) {
        updateLabel.setText(currentVersion + " not compatible");
      } else {
        updateLabel.setText("Not compatible");
      }
    }

    if (updateButton.isEnabled() && latestVersion != null) {
      updateButton.setText("Update to " + latestVersion);
    } else {
      updateButton.setText("Update");
    }

    removeButton.setEnabled(contrib.isInstalled() && !detail.removeInProgress);

    if (detail.updateInProgress || detail.installInProgress || detail.removeInProgress) {
//      progressBar.setUI(new PdeProgressBarUI("manager.progress"));
//      System.out.println(progressBar.getUI());
//      ((PdeProgressBarUI) progressBar.getUI()).updateTheme();
      progressBar.setVisible(true);
//      System.out.println(progressBar.getUI());
//      System.out.println("progress bar bg: " + progressBar.getBackground());
      updateLabel.setVisible(false);
    } else {
      progressBar.setVisible(false);
      updateLabel.setVisible(true);
    }
    detail.setProgressBar(progressBar);
//    progressPanel.repaint();  // needed? [fry 220504]
  }
}
