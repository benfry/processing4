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
import java.text.DateFormat;
import java.util.Date;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLDocument;

import processing.app.Language;
import processing.app.Util;
import processing.app.ui.Toolkit;
import processing.app.Base;
import processing.app.Platform;


class StatusPanel extends JPanel {
  static final int BUTTON_WIDTH = Toolkit.zoom(150);

  static Icon foundationIcon;
  static Icon installIcon;
  static Icon updateIcon;
  static Icon removeIcon;
  static Font buttonFont;

  JTextPane label;
  JButton installButton;
  JPanel progressPanel;
  JLabel updateLabel;
  JButton updateButton;
  JButton removeButton;
  GroupLayout layout;
  JLabel iconLabel;
  ContributionListing contributionListing = ContributionListing.getInstance();
  ContributionTab contributionTab;


  public StatusPanel(final ContributionTab contributionTab) {
    this.contributionTab = contributionTab;

    if (foundationIcon == null) {
      foundationIcon = Toolkit.getLibIconX("icons/foundation", 32);
      installIcon = Toolkit.getLibIconX("manager/install");
      updateIcon = Toolkit.getLibIconX("manager/update");
      removeIcon = Toolkit.getLibIconX("manager/remove");
      buttonFont = ManagerFrame.NORMAL_PLAIN;
    }
  }


  public StatusPanel(final ContributionTab contributionTab, int width) {
    this(contributionTab);

    setBackground(new Color(0xebebeb));

    iconLabel = new JLabel();
    iconLabel.setHorizontalAlignment(SwingConstants.CENTER);

    label = new JTextPane();
    label.setEditable(false);
    label.setOpaque(false);
    label.setContentType("text/html");
//    bodyRule = "a, body { font-family: " + buttonFont.getFamily() + "; " +
//            "font-size: " + buttonFont.getSize() + "pt; color: black; text-decoration: none;}";
//    bodyRule = "";
//    bodyRule = DetailPanel.getBodyStyle();
    label.addHyperlinkListener(e -> {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        if (e.getURL() != null) {
          Platform.openURL(e.getURL().toString());
        }
      }
    });
    installButton = Toolkit.createIconButton("Install", installIcon);
    //installButton.setDisabledIcon(installIcon);
    installButton.setFont(buttonFont);
    installButton.setHorizontalAlignment(SwingConstants.LEFT);
    installButton.addActionListener(e -> {
      installButton.setEnabled(false);
      DetailPanel currentPanel =
        contributionTab.contributionListPanel.getSelectedPanel();
      currentPanel.install();
      StatusPanel.this.update(currentPanel);
    });
    progressPanel = new JPanel();
    progressPanel.setLayout(new BorderLayout());
    progressPanel.setOpaque(false);

    updateLabel = new JLabel(" ");
    updateLabel.setFont(buttonFont);
    updateLabel.setHorizontalAlignment(SwingConstants.CENTER);

    updateButton = Toolkit.createIconButton("Update", updateIcon);
    updateButton.setFont(buttonFont);
    updateButton.setHorizontalAlignment(SwingConstants.LEFT);
    updateButton.addActionListener(e -> {
      updateButton.setEnabled(false);
      DetailPanel currentPanel =
        contributionTab.contributionListPanel.getSelectedPanel();
      currentPanel.update();
      StatusPanel.this.update(currentPanel);
    });

    removeButton = Toolkit.createIconButton("Remove", removeIcon);
    removeButton.setFont(buttonFont);
    removeButton.setHorizontalAlignment(SwingConstants.LEFT);
    removeButton.addActionListener(e -> {
      removeButton.setEnabled(false);
      DetailPanel currentPanel =
        contributionTab.contributionListPanel.getSelectedPanel();
      currentPanel.remove();
      StatusPanel.this.update(currentPanel);
    });

    int labelWidth = (width != 0) ?
      (3 * width / 4) : GroupLayout.PREFERRED_SIZE;
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
      .addComponent(label, labelWidth, labelWidth, labelWidth)
      .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED,
                       GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE)
      .addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                  .addComponent(installButton,
                                BUTTON_WIDTH, BUTTON_WIDTH, BUTTON_WIDTH)
                  .addComponent(progressPanel)
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
                              .addComponent(progressPanel)
                              .addComponent(updateLabel))
                  .addComponent(updateButton).addComponent(removeButton)));

    layout.linkSize(SwingConstants.HORIZONTAL,
                    installButton, progressPanel, updateButton, removeButton);

    progressPanel.setVisible(false);
    updateLabel.setVisible(false);

    installButton.setEnabled(false);
    updateButton.setEnabled(false);
    removeButton.setEnabled(false);
    updateLabel.setVisible(true);

    // Makes the label take up space even though not visible
    layout.setHonorsVisibility(updateLabel, false);

    validate();
  }


  /*
  void setMessage(String message) {
    if (label != null) {
      label.setText(message);
      label.repaint();
    }
  }
  */


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


  static String getBodyStyle() {
    return "body { " +
      "  margin: 0; " +
      "  padding: 0;" +
      "  font-family: " + Toolkit.getSansFontName() + ", Helvetica, Arial, sans-serif;" +
      "  font-size: 11px;" +
//      "  font-size: 100%;" +
//      "  font-size: 0.95em; " +
//      "}";
      "}" +
      "a { color: #444; text-decoration: none; }";
  }


  static private final String REMOVE_RESTART_MESSAGE =
    String.format("<i>%s</i>", Language.text("contrib.messages.remove_restart"));

  static private final String INSTALL_RESTART_MESSAGE =
    String.format("<i>%s</i>", Language.text("contrib.messages.install_restart"));

  static private final String UPDATE_RESTART_MESSAGE =
    String.format("<i>%s</i>", Language.text("contrib.messages.update_restart"));

  static String updateDescription(Contribution contrib) {
    // Avoid ugly synthesized bold
    Font boldFont = ManagerFrame.SMALL_BOLD;
    String fontFace = "<font face=\"" + boldFont.getName() + "\">";

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
        sentence = String.format("<i>%s</i>", Language.text("contrib.errors.description_unavailable"));
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


  public void update(DetailPanel panel) {
    System.out.println("rebuilding status panel for " + panel.getContrib().name);
    progressPanel.removeAll();

    iconLabel.setIcon(panel.getContrib().isSpecial() ? foundationIcon : null);
//    label.setText(panel.description);
    label.setText(updateDescription(panel.getContrib()));
    ((HTMLDocument)label.getDocument()).getStyleSheet().addRule(getBodyStyle());

    updateButton.setEnabled(contributionListing.hasDownloadedLatestList() &&
                            (contributionListing.hasUpdates(panel.getContrib()) &&
                             !panel.getContrib().isUpdateFlagged()) &&
                            !panel.updateInProgress);

    String latestVersion =
      contributionListing.getLatestPrettyVersion(panel.getContrib());
    String currentVersion = panel.getContrib().getPrettyVersion();

    installButton.setEnabled(!panel.getContrib().isInstalled() &&
                             contributionListing.hasDownloadedLatestList() &&
                             panel.getContrib().isCompatible(Base.getRevision()) &&
                             !panel.installInProgress);

    if (panel.getContrib().isCompatible(Base.getRevision())) {
      if (installButton.isEnabled()) {
        if (latestVersion != null) {
          updateLabel.setText(latestVersion + " available");
        } else {
          updateLabel.setText("Available");
        }
      } else {
        if (currentVersion != null) {
          updateLabel.setText(currentVersion + " installed");
        } else {
          updateLabel.setText("Installed");
        }
      }
    } else {
      if (currentVersion != null) {
        updateLabel.setText(currentVersion + " not compatible");
      } else {
        updateLabel.setText("Not compatible");
      }
    }

    if (latestVersion != null) {
      latestVersion = "Update to " + latestVersion;
    } else {
      latestVersion = "Update";
    }

    if (updateButton.isEnabled()) {
      updateButton.setText(latestVersion);
    } else {
      updateButton.setText("Update");
    }

    removeButton.setEnabled(panel.getContrib().isInstalled() && !panel.removeInProgress);
    progressPanel.add(panel.installProgressBar);
    progressPanel.setVisible(false);
    updateLabel.setVisible(true);
    if (panel.updateInProgress || panel.installInProgress || panel.removeInProgress) {
      progressPanel.setVisible(true);
      updateLabel.setVisible(false);
      progressPanel.repaint();
    }
  }
}
