/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - https://processing.org

  Copyright (c) 2013-23 The Processing Foundation

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

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.LayoutStyle;
import javax.swing.SwingConstants;

import processing.app.ui.Theme;


public class UpdateStatusPanel extends StatusPanel {

  public UpdateStatusPanel(UpdateContributionTab tab) {
    super(tab);
  }


  @Override
  protected void buildLayout() {
    updateButton = new JButton("Update All");
    updateButton.setHorizontalAlignment(SwingConstants.LEFT);
    updateButton.setVisible(true);
    updateButton.setEnabled(false);

    updateButton.addActionListener(e -> updateAll());
    layout = new GroupLayout(this);
    setLayout(layout);

    layout.setAutoCreateContainerGaps(true);
    layout.setAutoCreateGaps(true);

    layout.setHorizontalGroup(layout.createSequentialGroup()
      .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED,
                       GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE)
      .addComponent(updateButton, BUTTON_WIDTH, BUTTON_WIDTH, BUTTON_WIDTH)
      .addGap(12));  // make button line up relative to the scrollbar

    layout.setVerticalGroup(layout.createParallelGroup()
      .addComponent(updateButton));

    // Dummy progress bar that's never shown. Yuck! [fry 230114]
    buildProgressBar();
  }


  @Override
  protected void updateTheme() {
    setBackground(Theme.getColor("manager.panel.background.color"));
    updateButtonTheme(updateButton, "update");
  }


  private void updateAll() {
    ListPanel listPanel = contributionTab.listPanel;
    for (StatusDetail detail : listPanel.detailForContrib.values()) {
      // Set the fake detail object so progressBar is set.
      applyDetail(detail);
      detail.updateContrib(listPanel);
    }
    listPanel.model.fireTableDataChanged();
  }


  protected void setUpdateEnabled(boolean updateEnabled) {
    updateButton.setEnabled(updateEnabled);
  }


  @Override
  protected void applyDetail(StatusDetail detail) {
    detail.setProgressBar(progressBar);
  }
}