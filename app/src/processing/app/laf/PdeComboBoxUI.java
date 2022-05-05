package processing.app.laf;

import processing.app.ui.Theme;
import processing.app.ui.Toolkit;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.*;


// https://github.com/AdoptOpenJDK/openjdk-jdk8u/blob/master/jdk/src/share/classes/javax/swing/plaf/basic/BasicComboBoxUI.java
public class PdeComboBoxUI extends BasicComboBoxUI {
  final String prefix;

  Color enabledFgColor;
  Color enabledBgColor;
  Color disabledFgColor;
  Color disabledBgColor;
  Color selectedFgColor;
  Color selectedBgColor;


  public PdeComboBoxUI(String prefix) {
    this.prefix = prefix;
  }


  @Override
  protected void installDefaults() {
    super.installDefaults();
    comboBox.setBorder(null);
  }


  @Override
  protected JButton createArrowButton() {
    /*
    JButton button = new BasicArrowButton(BasicArrowButton.SOUTH,
      UIManager.getColor("ComboBox.buttonBackground"),
      UIManager.getColor("ComboBox.buttonShadow"),
      UIManager.getColor("ComboBox.buttonDarkShadow"),
      UIManager.getColor("ComboBox.buttonHighlight"));
    button.setName("ComboBox.arrowButton");
    return button;
    */
    JButton button = new JButton();
    //JButton button = new JButton("\u2193");  // arrow down
//    JButton button = new JButton("\u25BC");  // filled triangle
//    JButton button = new JButton("\u25BD");  // outlined triangle
    //JButton button = new JButton("\u02C5 . \u02EC . \u142f");
//    JButton button = new JButton("\u02EC . \u142f");
//    button.setFont(new Font("Dialog", Font.PLAIN, 8));
//    button.putClientProperty("FlatLaf.styleClass", "mini");
    //button.putClientProperty();
    button.setBorder(null);
    return button;
  }


  @Override
  protected ListCellRenderer<Object> createRenderer() {
    return new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        // TODO There must be a less convoluted way to do this [fry 220505]
        Container parent = getParent();
        if (parent instanceof CellRendererPane) {
          Container crp = parent.getParent();
          // anonymous class javax.swing.plaf.basic.BasicComboPopup$1
          if (crp instanceof JComponent) {
            Container viewport = crp.getParent();
            if (viewport instanceof JViewport) {
              Container scrollPane = viewport.getParent();
              if (scrollPane instanceof JScrollPane) {
                Container popup = scrollPane.getParent();
                if (popup instanceof JComponent) {
                  // com.formdev.flatlaf.ui.FlatComboBoxUI$FlatComboPopup
                  JComponent c = (JComponent) popup;
                  if (!(c.getBorder() instanceof EmptyBorder)) {  // just once
                    // remove the black outline from the popup
                    c.setBorder(new EmptyBorder(0, 0, 0, 0));
                  }
                }
              }
            }
          }
        }

        // Can't use instanceof because FlatLaf Border is EmptyBorder subclass.
        // If this is the currently selected item (index == -1), do not add
        // extra left indent, so the list items left-align with the selection.
        setBorder(new EmptyBorder(2, index == -1 ? 0 : 6, 2, 2));

        if (isSelected) {
          setForeground(selectedFgColor);
          setBackground(selectedBgColor);
        } else {
          setForeground(enabledFgColor);
          setBackground(enabledBgColor);
        }
        setText(String.valueOf(value));
        return this;
      }
    };
  }


  @Override
  public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
    ListCellRenderer<Object> renderer = comboBox.getRenderer();
    Component c;

    if (hasFocus && !isPopupVisible(comboBox) ) {
      c = renderer.getListCellRendererComponent(listBox, comboBox.getSelectedItem(), -1, true, false );
    } else {
      c = renderer.getListCellRendererComponent(listBox, comboBox.getSelectedItem(), -1, false, false );
      //c.setBackground(UIManager.getColor("ComboBox.background"));
      c.setBackground(enabledBgColor);
    }
    c.setFont(comboBox.getFont());
//    if (hasFocus && !isPopupVisible(comboBox)) {
//      //c.setForeground(listBox.getSelectionForeground());
//      //c.setBackground(listBox.getSelectionBackground());
//      c.setForeground(selectedFgColor);
//      c.setBackground(selectedBgColor);
//
//    } else {
    if (comboBox.isEnabled()) {
//        c.setForeground(comboBox.getForeground());
//        c.setBackground(comboBox.getBackground());
      c.setForeground(enabledFgColor);
      c.setBackground(enabledBgColor);
    } else {
      c.setForeground(disabledFgColor);
      c.setBackground(disabledBgColor);
    }
//    }

    // Fix for 4238829: should lay out the JPanel.
    boolean shouldValidate = c instanceof JPanel;

    int x = bounds.x, y = bounds.y, w = bounds.width, h = bounds.height;
    if (padding != null) {
      x = bounds.x + padding.left;
      y = bounds.y + padding.top;
      w = bounds.width - (padding.left + padding.right);
      h = bounds.height - (padding.top + padding.bottom);
    }

    currentValuePane.paintComponent(g, c, comboBox, x, y, w, h, shouldValidate);
  }


  @Override
  public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
    Color t = g.getColor();
    if (comboBox.isEnabled()) {
      //g.setColor(DefaultLookup.getColor(comboBox, this, "ComboBox.background", null));
      g.setColor(enabledBgColor);
    } else {
      //g.setColor(DefaultLookup.getColor(comboBox, this, "ComboBox.disabledBackground", null));
      g.setColor(disabledBgColor);
    }
    g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    g.setColor(t);
  }


  public void updateTheme() {
    enabledFgColor = Theme.getColor(prefix + ".enabled.fgcolor");
    enabledBgColor = Theme.getColor(prefix + ".enabled.bgcolor");
    disabledFgColor = Theme.getColor(prefix + ".disabled.fgcolor");
    disabledBgColor = Theme.getColor(prefix + ".disabled.bgcolor");
    selectedFgColor = Theme.getColor(prefix + ".selected.fgcolor");
    selectedBgColor = Theme.getColor(prefix + ".selected.bgcolor");

    if (arrowButton.isEnabled()) {
      arrowButton.setBackground(enabledBgColor);
      arrowButton.setForeground(enabledFgColor);
      arrowButton.setIcon(Toolkit.renderIcon("manager/chevron", Theme.get(prefix + ".enabled.fgcolor"), 16));
    } else {
      arrowButton.setBackground(disabledBgColor);
      arrowButton.setForeground(disabledFgColor);
      arrowButton.setIcon(Toolkit.renderIcon("manager/chevron", Theme.get(prefix + ".disabled.fgcolor"), 16));
    }
  }
}