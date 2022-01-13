/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-22 The Processing Foundation
  Copyright (c) 2004-12 Ben Fry and Casey Reas
  Copyright (c) 2001-04 Massachusetts Institute of Technology

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, version 2.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package processing.app.ui;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;

import processing.app.Base;
import processing.app.Language;
import processing.app.Mode;


/**
 * Run/Stop button plus Mode selection
 */
abstract public class EditorToolbar extends JPanel implements KeyListener {
  // haven't decided how to handle this/how to make public/consistency
  // for components/does it live in theme.txt
  static final int HIGH = Toolkit.zoom(53);
  // horizontal gap between buttons
  static final int GAP = Toolkit.zoom(9);

  protected Editor editor;
  protected Base base;
  protected Mode mode;

  protected EditorButton runButton;
  protected EditorButton stopButton;

  protected EditorButton rolloverButton;
  protected JLabel rolloverLabel;

  protected Box box;

  protected Image gradient;


  public EditorToolbar(Editor editor) {
    this.editor = editor;
    base = editor.getBase();
    mode = editor.getMode();

    rebuild();
  }


  public void rebuild() {
    removeAll();  // remove previous components, if any
    List<EditorButton> buttons = createButtons();

    box = Box.createHorizontalBox();
    box.add(Box.createHorizontalStrut(Editor.LEFT_GUTTER));

    rolloverLabel = new JLabel();

    for (EditorButton button : buttons) {
      box.add(button);
      box.add(Box.createHorizontalStrut(GAP));
//      registerButton(button);
    }
//    // remove the last gap
//    box.remove(box.getComponentCount() - 1);

//    box.add(Box.createHorizontalStrut(LABEL_GAP));
    box.add(rolloverLabel);
//    currentButton = runButton;

//    runButton.setRolloverLabel(label);
//    stopButton.setRolloverLabel(label);

    box.add(Box.createHorizontalGlue());
    addModeButtons(box, rolloverLabel);
//    Component items = createModeButtons();
//    if (items != null) {
//      box.add(items);
//    }
    ModeSelector ms = new ModeSelector(editor);
    box.add(ms);
    box.add(Box.createHorizontalStrut(Editor.RIGHT_GUTTER));

    setLayout(new BorderLayout());
    add(box, BorderLayout.CENTER);

    updateTheme();
  }


  public void updateTheme() {
    gradient = Theme.makeGradient("toolbar", Toolkit.zoom(400), HIGH);

    rolloverLabel.setFont(Theme.getFont("toolbar.rollover.font"));
    rolloverLabel.setForeground(Theme.getColor("toolbar.rollover.color"));

    for (Component c : box.getComponents()) {
      if (c instanceof EditorButton) {
        ((EditorButton) c).updateTheme();
      }
    }
  }


//  public void registerButton(EditorButton button) {
    //button.setRolloverLabel(rolloverLabel);
    //editor.getTextArea().addKeyListener(button);
//  }


//  public void setReverse(EditorButton button) {
//    button.setGradient(reverseGradient);
//  }


//  public void setText(String text) {
//    label.setText(text);
//  }


  public void paintComponent(Graphics g) {
    Dimension size = getSize();
    g.drawImage(gradient, 0, 0, size.width, size.height, this);
  }


  public List<EditorButton> createButtons() {
    runButton = new EditorButton(this,
                                 "/lib/toolbar/run",
                                 Language.text("toolbar.run"),
                                 Language.text("toolbar.present")) {
      @Override
      public void actionPerformed(ActionEvent e) {
        handleRun(e.getModifiers());
      }
    };

    stopButton = new EditorButton(this,
                                  "/lib/toolbar/stop",
                                  Language.text("toolbar.stop")) {
      @Override
      public void actionPerformed(ActionEvent e) {
        handleStop();
      }
    };
    return new ArrayList<>(Arrays.asList(runButton, stopButton));
  }


  public void addModeButtons(Box box, JLabel label) {
  }


  public void addGap(Box box) {
    box.add(Box.createHorizontalStrut(GAP));
  }


//  public Component createModeSelector() {
//    return new ModeSelector();
//  }


//  protected void swapButton(EditorButton replacement) {
//    if (currentButton != replacement) {
//      box.remove(currentButton);
//      box.add(replacement, 1);  // has to go after the strut
//      box.revalidate();
//      box.repaint();  // may be needed
//      currentButton = replacement;
//    }
//  }


  public void activateRun() {
    runButton.setSelected(true);
    repaint();
  }


  public void deactivateRun() {
    runButton.setSelected(false);
    repaint();
  }


  public void activateStop() {
    stopButton.setSelected(true);
    repaint();
  }


  public void deactivateStop() {
    stopButton.setSelected(false);
    repaint();
  }


  abstract public void handleRun(int modifiers);


  abstract public void handleStop();


  void setRollover(EditorButton button, InputEvent e) {
    rolloverButton = button;
    updateRollover(e);
  }


  void updateRollover(InputEvent e) {
    if (rolloverButton == null) {
      rolloverLabel.setText("");
    } else {
      rolloverLabel.setText(rolloverButton.getRolloverText(e));
    }
  }


  @Override
  public void keyTyped(KeyEvent e) { }


  @Override
  public void keyReleased(KeyEvent e) {
    updateRollover(e);
  }


  @Override
  public void keyPressed(KeyEvent e) {
    updateRollover(e);
  }


  public Dimension getPreferredSize() {
    return new Dimension(super.getPreferredSize().width, HIGH);
  }


  public Dimension getMinimumSize() {
    return new Dimension(super.getMinimumSize().width, HIGH);
  }


  public Dimension getMaximumSize() {
    return new Dimension(super.getMaximumSize().width, HIGH);
  }
}