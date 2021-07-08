package processing.app.tools;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;


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
      //Scene scene = new Scene();

      WebView view = new WebView();
      engine = view.getEngine();

      engine.titleProperty().addListener((observable, oldValue, newValue) -> SwingUtilities.invokeLater(() -> WebFrame.this.setTitle(newValue)));
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