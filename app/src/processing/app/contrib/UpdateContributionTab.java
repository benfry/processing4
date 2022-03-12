package processing.app.contrib;

import java.awt.Color;

import javax.swing.GroupLayout;
import javax.swing.JLabel;
import javax.swing.JProgressBar;

import processing.app.ui.Toolkit;


public class UpdateContributionTab extends ContributionTab {

  public UpdateContributionTab(ManagerFrame dialog) {
    super(dialog);

    filter = contrib -> {
      if (contrib instanceof ListPanel.SectionHeaderContribution) {
        return true;
      }
      if (contrib instanceof LocalContribution) {
        return ContributionListing.getInstance().hasUpdates(contrib);
      }
      return false;
    };
    contributionListPanel = new UpdateListPanel(this, filter);

    statusPanel = new UpdateStatusPanel(this);
    contribListing = ContributionListing.getInstance();
    contribListing.addListener(contributionListPanel);
  }


  @Override
  protected void setLayout(boolean error, boolean loading) {
    if (progressBar == null) {
      progressBar = new JProgressBar();
      progressBar.setVisible(false);

      buildErrorPanel();

      loaderLabel = new JLabel(Toolkit.getLibIcon("manager/loader.gif"));
      loaderLabel.setOpaque(false);
    }

    GroupLayout layout = new GroupLayout(this);
    setLayout(layout);
    layout.setHorizontalGroup(layout
      .createParallelGroup(GroupLayout.Alignment.CENTER)
      .addComponent(loaderLabel)
      .addComponent(contributionListPanel)
      .addComponent(errorPanel)
      .addComponent(statusPanel));

    layout.setVerticalGroup(layout
      .createSequentialGroup()
      .addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                  .addComponent(loaderLabel)
                  .addComponent(contributionListPanel))
      .addComponent(errorPanel)
      .addComponent(statusPanel, GroupLayout.PREFERRED_SIZE,
                    GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE));
    layout.setHonorsVisibility(contributionListPanel, false);

    setBackground(Color.WHITE);
  }


  @Override
  public void updateStatusDetail(StatusPanelDetail detail) {
    // Do nothing
  }
}
