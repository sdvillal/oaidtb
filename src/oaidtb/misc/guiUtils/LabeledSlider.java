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
 *    LabeledSlider.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.misc.guiUtils;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import java.awt.*;

/**
 * Clase que soluciona el bug de guiUtils 4220108 (un JSlider no se pinta cuando se inserta
 * en un JDesktopPane...) y que permite tener un JLabel que se actualiza según los valores
 * de un JSlider.
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class LabeledSlider{

  private JSlider slider = new JSlider(){
    boolean primeraVezQueSePinta = true;

    //Solución cutre para el bug 4220108
    //Ver http://developer.java.sun.com/developer/bugParade/bugs/4220108.html
    //A ver si reabren el tema...
    public void paint(Graphics g){
      super.paint(g);
      if(primeraVezQueSePinta){
        updateUI();
        primeraVezQueSePinta = false;
      }
    }
  };
  private JLabel label = new JLabel("");
  private String labelMessage = "";

  public JLabel getLabel(){
    return label;
  }

  public JSlider getSlider(){
    return slider;
  }

  public LabeledSlider(String messagePrefix){
    if(messagePrefix != null)
      labelMessage = messagePrefix;

    slider.addChangeListener(new ChangeListener(){
      public void stateChanged(ChangeEvent e){
        label.setText(labelMessage +String.valueOf(slider.getValue()));
      }
    });
  }
}