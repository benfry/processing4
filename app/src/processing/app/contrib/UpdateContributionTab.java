/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - https://processing.org

  Copyright (c) 2015-22 The Processing Foundation

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


public class UpdateContributionTab extends ContributionTab {

  public UpdateContributionTab(ManagerFrame dialog) {
    super(dialog);

    // Filter to show only the contributions with updates available,
    // or are section headers (which are fake contributions).
    filter = contrib -> {
      if (contrib instanceof ListPanel.SectionHeaderContribution) {
        return true;
      }
      if (contrib instanceof LocalContribution) {
        return ContributionListing.getInstance().hasUpdates(contrib);
      }
      return false;
    };

    listPanel = new UpdateListPanel(this, filter);
    statusPanel = new UpdateStatusPanel(this);
    initLayout();

    ContributionListing.getInstance().addListPanel(listPanel);
  }


  @Override
  protected void initLayout() {
    /*
    if (loaderLabel == null) {
//    if (progressBar == null) {
//      progressBar = new JProgressBar();
//      progressBar.setVisible(false);

      buildErrorPanel();

      loaderLabel = new JLabel(Toolkit.getLibIcon("manager/loader.gif"));
      loaderLabel.setOpaque(false);
    }
    */

    GroupLayout layout = new GroupLayout(this);
    setLayout(layout);
    layout.setHorizontalGroup(layout
      .createParallelGroup(GroupLayout.Alignment.CENTER)
      //.addComponent(loaderLabel)
      .addComponent(listPanel)
      //.addComponent(errorPanel)
      .addComponent(statusPanel));

    layout.setVerticalGroup(layout
      .createSequentialGroup()
      .addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                  //.addComponent(loaderLabel)
                  .addComponent(listPanel))
                  //.addComponent(errorPanel)
                  .addComponent(statusPanel,
                                GroupLayout.PREFERRED_SIZE,
                                GroupLayout.DEFAULT_SIZE,
                                GroupLayout.PREFERRED_SIZE));
    layout.setHonorsVisibility(listPanel, false);

    //setBackground(Color.WHITE);
  }
}
