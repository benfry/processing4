/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle or the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package processing.app.tools;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.MalformedURLException;
import java.net.URL;

import static javafx.concurrent.Worker.State.FAILED;


public class SimpleSwingBrowser extends JFrame {

  private final JFXPanel jfxPanel = new JFXPanel();
  private WebEngine engine;

  private final JPanel panel = new JPanel(new BorderLayout());
  private final JLabel lblStatus = new JLabel();

  private final JButton btnGo = new JButton("Go");
  private final JTextField txtURL = new JTextField();
  private final JProgressBar progressBar = new JProgressBar();


  public SimpleSwingBrowser() {
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

			engine.titleProperty().addListener((observable, oldValue, newValue) -> SwingUtilities.invokeLater(() -> SimpleSwingBrowser.this.setTitle(newValue)));

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
    Platform.runLater(() -> {
			String tmp = toURL(url);

			if (tmp == null) {
				tmp = toURL("http://" + url);
			}

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


  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
			SimpleSwingBrowser browser = new SimpleSwingBrowser();
			browser.setVisible(true);
			browser.loadURL("http://oracle.com");
		});
  }
}
