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
 *    PropertyDialog.java
 *    Copyright (C) 1999 Len Trigg
 *    Modified 2002 by Santiago David Villaba
 *
 *  Customized Weka changes-->
 *    ** Converted in  JOptionPane, so that no other window appear in the OS DeskTop
 *       and it can be made modal.
 *
 */


package oaidtb.gui.customizedWeka;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.DropTarget;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyEditor;

/**
 * Support for PropertyEditors with custom editors: puts the editor into
 * a separate frame.
 *
 * @author Len Trigg (trigg@cs.waikato.ac.nz)
 * @author Santiago David Villalna (sdvb@wanadoo.es)
 * @version $Revision: 1.5 $
 */
public class PropertyDialog extends JDialog{

  /** The property editor */
  private PropertyEditor m_Editor;

  /** The custom editor component */
  private Component m_EditorComponent;

  /**
   * Creates the editor frame.
   *
   * @param owner The owner of the dialog
   * @param pe the PropertyEditor
   * @param x initial x coord for the frame
   * @param y initial y coord for the frame
   */
  public PropertyDialog(Frame owner, PropertyEditor pe, int x, int y){

    super(owner);
    initialize(pe, x, y);
  }

  /**
   * Creates the editor frame.
   *
   * @param owner The owner of the dialog
   * @param pe the PropertyEditor
   * @param x initial x coord for the frame
   * @param y initial y coord for the frame
   */
  public PropertyDialog(JDialog owner, PropertyEditor pe, int x, int y){

    super(owner);
    initialize(pe, x, y);
  }

  private void initialize(PropertyEditor pe, int x, int y){
    addWindowListener(new WindowAdapter(){
      public void windowClosing(WindowEvent e){
        e.getWindow().dispose();
      }
    });
    getContentPane().setLayout(new BorderLayout());

    m_Editor = pe;
    m_EditorComponent = pe.getCustomEditor();
    getContentPane().add(m_EditorComponent, BorderLayout.CENTER);

    pack();
    setTitle(pe.getClass().getName());
    setModal(true);
    setLocation(x, y);
    setVisible(true);
  }

  /**
   * Gets the current property editor.
   *
   * @return a value of type 'PropertyEditor'
   */
  public PropertyEditor getEditor(){

    return m_Editor;
  }
}

