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


public class UpdateListPanel extends ListPanel {
  Contribution.Filter contribFilter;


  public UpdateListPanel(ContributionTab contributionTab,
                         Contribution.Filter contribFilter) {
    super(contributionTab, contribFilter, true,
          ContributionColumn.STATUS_NO_HEADER,
          ContributionColumn.NAME,
          ContributionColumn.AUTHOR,
          ContributionColumn.INSTALLED_VERSION,
          ContributionColumn.AVAILABLE_VERSION);

    this.contribFilter = contribFilter;
    table.getTableHeader().setEnabled(false);
  }


  // Thread: EDT
  @Override
  public void contributionAdded(final Contribution contribution) {
    // Ensures contributionAdded in ListPanel is only run on LocalContributions
    if (contribFilter.matches(contribution)) {
      super.contributionAdded(contribution);
      ((UpdateStatusPanel) contributionTab.statusPanel).update(); // Enables update button
    }
  }


  // Thread: EDT
  @Override
  public void contributionRemoved(final Contribution contribution) {
    super.contributionRemoved(contribution);
    ((UpdateStatusPanel) contributionTab.statusPanel).update(); // Disables update button on last contribution
  }


  // Thread: EDT
  @Override
  public void contributionChanged(final Contribution oldContrib,
                                  final Contribution newContrib) {
    StatusDetail detail = detailForContrib.get(oldContrib);
    if (detail == null) {
      contributionAdded(newContrib);
    } else if (newContrib.isInstalled()) {
      detailForContrib.remove(oldContrib);
    }
    model.fireTableDataChanged();
  }
}
