package processing.app.tools;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

import static javafx.concurrent.Worker.State.FAILED;


public class WebFrame extends JFrame {

  private final JFXPanel jfxPanel = new JFXPanel();
  private WebEngine engine;

  private final JPanel panel = new JPanel(new BorderLayout());
  private final JLabel lblStatus = new JLabel();

  private final JButton btnGo = new JButton("Go");
  private final JTextField txtURL = new JTextField();
  private final JProgressBar progressBar = new JProgressBar();


  public WebFrame() {
    super();
    initComponents();
  }


  private void initComponents() {
    createScene();

    ActionListener al = e -> loadURL(txtURL.getText());

    btnGo.addActionListener(al);
    txtURL.addActionListener(al);

    progressBar.setPreferredSize(new Dimension(150, 18));
    progressBar.setStringPainted(true);

    JPanel topBar = new JPanel(new BorderLayout(5, 0));
    topBar.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
    topBar.add(txtURL, BorderLayout.CENTER);
    topBar.add(btnGo, BorderLayout.EAST);

    JPanel statusBar = new JPanel(new BorderLayout(5, 0));
    statusBar.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
    statusBar.add(lblStatus, BorderLayout.CENTER);
    statusBar.add(progressBar, BorderLayout.EAST);

    panel.add(topBar, BorderLayout.NORTH);
    panel.add(jfxPanel, BorderLayout.CENTER);
    panel.add(statusBar, BorderLayout.SOUTH);

    getContentPane().add(panel);

    setPreferredSize(new Dimension(1024, 600));
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    pack();
  }


  private void createScene() {
    Platform.runLater(() -> {
      WebView view = new WebView();
      engine = view.getEngine();

      engine.setOnStatusChanged(event -> SwingUtilities.invokeLater(() -> lblStatus.setText(event.getData())));

      engine.locationProperty().addListener((ov, oldValue, newValue) -> SwingUtilities.invokeLater(() -> txtURL.setText(newValue)));

      engine.getLoadWorker().workDoneProperty().addListener((observableValue, oldValue, newValue) -> SwingUtilities.invokeLater(() -> progressBar.setValue(newValue.intValue())));

      engine.getLoadWorker()
        .exceptionProperty()
        .addListener((o, old, value) -> {
          if (engine.getLoadWorker().getState() == FAILED) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
              panel,
              (value != null)
                ? engine.getLocation() + "\n" + value.getMessage()
                : engine.getLocation() + "\nUnexpected error.",
              "Loading error...",
              JOptionPane.ERROR_MESSAGE));
          }
        });

      jfxPanel.setScene(new Scene(view));
    });
  }


  public void loadURL(final String url) {
    Platform.runLater(() -> engine.load(url));
  }


  /*
  public void loadURL(final String url) {
    Platform.runLater(() -> {
      String tmp = toURL(url);

      if (url == null) {
        tmp = toURL("http://" + url);
      }

      System.out.println("loading " + url);
      engine.load(tmp);
    });
  }


  private static String toURL(String str) {
    try {
      return new URL(str).toExternalForm();
    } catch (MalformedURLException exception) {
      return null;
    }
  }
  */


  static public void main(String[] args) {
    WebFrame wf = new WebFrame();
    wf.setLocationRelativeTo(null);
    wf.setVisible(true);
//    wf.loadURL("http://download.processing.org/contribs");
    wf.loadURL("https://learn.fathom.info/palettemaker/");
  }
}