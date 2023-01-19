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

import javax.swing.table.TableColumnModel;


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

    TableColumnModel tcm = table.getColumnModel();
    tcm.getColumn(3).setMaxWidth(ManagerFrame.VERSION_WIDTH);
    tcm.getColumn(4).setMaxWidth(ManagerFrame.VERSION_WIDTH);

    this.contribFilter = contribFilter;
    // This is apparently a hack to prevent rows from being sorted by
    // clicking on the column headers, which makes a mess because this
    // list has sub-headers for the categories mixed into the list.
    // However, unfortunately it also breaks column resizing. [fry 220726]
    table.getTableHeader().setEnabled(false);
  }


  /*
  // Thread: EDT
  @Override
  public void contributionAdded(final Contribution contribution) {
    // Ensures contributionAdded in ListPanel is only run on LocalContributions
    if (contribFilter.matches(contribution)) {
      super.contributionAdded(contribution);

      // Enable the update button if contributions are available
      ((UpdateStatusPanel) contributionTab.statusPanel).setUpdateEnabled(anyRows());
    }
  }


  // Thread: EDT
  @Override
  public void contributionRemoved(final Contribution contribution) {
    if (contribFilter.matches(contribution)) {
      super.contributionRemoved(contribution);

      // Disable the update button if no contributions in the list
      ((UpdateStatusPanel) contributionTab.statusPanel).setUpdateEnabled(anyRows());
    }
  }
  */


  // TODO This seems a little weird… Wasn't checking against the filter,
  //      and not entirely clear why it isn't just calling super().
  //      Also seems dangerous to do its own add/remove calls.
  //      However, if removed, the StatusDetail entries for the Updates
  //      panel are all f-ed up (NPE for progressBar). [fry 230119]
  // Thread: EDT
  @Override
  public void contributionChanged(final Contribution oldContrib,
                                  final Contribution newContrib) {
    // TODO Matching against oldContrib brings back NPEs,
    //      but using newContrib seems to work. [fry 230119]
    if (contribFilter.matches(newContrib)) {
      StatusDetail detail = detailForContrib.get(oldContrib);
      if (detail == null) {
        contributionAdded(newContrib);
      } else if (newContrib.isInstalled()) {
        detailForContrib.remove(oldContrib);
      }
//    model.fireTableDataChanged();
    }
  }


  @Override
  protected void updateModel() {
    super.updateModel();

    boolean anyRows = sorter.getViewRowCount() > 0;
    ((UpdateStatusPanel) contributionTab.statusPanel).setUpdateEnabled(anyRows);
  }
}
