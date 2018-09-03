/*
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program; if not, write to the Free Software
 *    Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

/*
 *    ColorHistoryPanel.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.gui;

import weka.core.FastVector;

import javax.swing.*;
import javax.swing.colorchooser.AbstractColorChooserPanel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Observable;

/**
 * Un panel que se añade al JColorChooser para permitir al usuario
 * reelegir entre los colores de los que ya existen instancias en un
 * conjunto de datos "Point2dInstances".
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class ColorHistoryPanel extends AbstractColorChooserPanel{

  private Point2DInstances m_Colors;

  /**
   * This method must be call every time before the panel is shown to ensure
   * that all the information it contains is correct.
   */
  public void remake(){
    removeAll();
    JPanel panel = new JPanel(new GridLayout(0, 8));
    FastVector colors = m_Colors.getColors();
    for (int i = 0; i < colors.size(); i++){
      Color c = (Color) colors.elementAt(i);
      JButton button = new JButton(String.valueOf(m_Colors.getNumPointsOfColor(c)));
      button.setBackground(c);
      button.addActionListener(setColorAction);
      panel.add(button);
    }
    setLayout(new BorderLayout());
    JLabel label = new JLabel("There are " + colors.size() + " colors");
    add(label, BorderLayout.NORTH);
    JScrollPane jScrollPane = new JScrollPane(panel);
//    jScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
//    jScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    add(jScrollPane, BorderLayout.CENTER);
  }

  public Point2DInstances getColors(){
    return m_Colors;
  }

  public void setColors(Point2DInstances colors){
    m_Colors = colors;
  }

  public ColorHistoryPanel(Point2DInstances colors){
    super();
    m_Colors = colors;
  }

  /**
   * Builds a new panel.
   */
  protected void buildChooser(){
  }

  /**
   * Returns a string containing the display name of the panel.
   * @return the name of the display panel
   */
  public String getDisplayName(){
    return "Colors history";
  }

  /**
   * Returns the small display icon for the panel.
   * @return the small display icon
   */
  public Icon getLargeDisplayIcon(){
    return null;
  }

  /**
   * Returns the large display icon for the panel.
   * @return the large display icon
   */
  public Icon getSmallDisplayIcon(){
    return null;
  }

  /**
   * Invoked automatically when the model's state changes.
   * It is also called by <code>installChooserPanel</code> to allow
   * you to set up the initial state of your m_ColorChooser.
   * Override this method to update your <code>ChooserPanel</code>.
   */
  public void updateChooser(){
  }

  // This action takes the background color of the button
  // and uses it to set the selected color.
  Action setColorAction = new AbstractAction(){
    public void actionPerformed(ActionEvent evt){
      JButton button = (JButton) evt.getSource();
      getColorSelectionModel().setSelectedColor(button.getBackground());
    }
  };
}