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
 *    PropertyPanel.java
 *    Copyright (C) 1999 Len Trigg
 *    Modified 2002 by Santiago David Villaba
 *
 *  Customized Weka changes-->
 *    ** Given "focusability" and keyboard "selectability" (appears like a button).
 *    ** If the panel isn't enabled, it won't respond to the user clicks
 *    ** Also see removeNotify() notes.
 *
 */

package oaidtb.gui.customizedWeka;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.beans.PropertyEditor;

/**
 * Support for drawing a property value in a component.
 *
 * @author Len Trigg (trigg@cs.waikato.ac.nz)
 * @author Santiago David Villalba (sdvb@wanadoo.es)
 * @version $Revision: 1.7 $
 */
public class PropertyPanel extends JPanel{

  /** The property editor */
  private PropertyEditor m_Editor;

  /** The currently displayed property dialog, if any */
  private oaidtb.gui.customizedWeka.PropertyDialog m_PD;

  /** The appearance when this panel has the focus. */
  private Border m_WithFocusBorder;

  /** The appearance when this panel doesn't have the focus. */
  private Border m_WithoutFocusBorder;

  /**
   * Create the panel with the supplied property editor.
   *
   * @param pe the PropertyEditor
   */
  public PropertyPanel(PropertyEditor pe){

    m_Editor = pe;

    m_WithoutFocusBorder = BorderFactory.createEtchedBorder();
    m_WithFocusBorder = BorderFactory.createLineBorder(Color.black);

    setBorder(m_WithoutFocusBorder);

    setToolTipText("Click to edit properties for this object");

    setOpaque(true);

    //m_Editor.getAsText() != null
    if (m_Editor instanceof oaidtb.gui.customizedWeka.GenericObjectEditor)
      addMouseListener(new MouseAdapter(){
        public void mousePressed(MouseEvent evt){
          if (!isEnabled())
            return;
          if (SwingUtilities.isRightMouseButton(evt))
            showPopUp(evt);
          else
            showPropertyDialog(getLocationOnScreen().x, getLocationOnScreen().y);
        }
      });
    else
      addMouseListener(new MouseAdapter(){
        public void mousePressed(MouseEvent evt){
          if (!isEnabled())
            return;
          if (SwingUtilities.isLeftMouseButton(evt))
            showPropertyDialog(getLocationOnScreen().x, getLocationOnScreen().y);
        }
      });

    addKeyListener(new KeyAdapter(){
      public void keyPressed(KeyEvent e){
        if (!isEnabled())
          return;
        if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE)
          showPropertyDialog(getLocationOnScreen().x, getLocationOnScreen().y);
      }
    });

    addFocusListener(new FocusListener(){
      public void focusGained(FocusEvent e){
        setBorder(m_WithFocusBorder);
      }

      public void focusLost(FocusEvent e){
        setBorder(m_WithoutFocusBorder);
      }
    });

    Dimension newPref = getPreferredSize();
    newPref.height = getFontMetrics(getFont()).getHeight() * 5 / 4;
    newPref.width = newPref.height * 5;
    setPreferredSize(newPref);
  }

  public void showPropertyDialog(int x, int y){
    if(!isEnabled())
      return;
    if (m_Editor.getValue() != null){
      if (m_PD == null){
        //Tenemos que hacer que el diálogo interno pertenezca al Frame principal de la
        //aplicación o a un JDialog subordinado para que al
        //cambiar de aplicación y volver no se pierda el mismo, bloqueando la aplicación
        //si es modal (se podría recuperar en ese caso mediante el cambio de ventana con el teclado)
        Container container = getTopLevelAncestor();
        while(container instanceof JInternalFrame)
          container = ((JInternalFrame)container).getTopLevelAncestor();
        if(container instanceof JDialog)
          m_PD = new oaidtb.gui.customizedWeka.PropertyDialog((JDialog)container, m_Editor, x, y);
        else
          m_PD = new oaidtb.gui.customizedWeka.PropertyDialog((JFrame)container, m_Editor, x, y);
      }
      else{
        m_PD.setLocation(x, y);
        m_PD.setVisible(true);
      }
    }
  }

  /**
   * We allow this panel to receive the focus
   *
   * @return true if this component can receive the focus
   */
  public boolean isFocusTraversable(){
    return true;
  }

  /**
   * Paints the component, using the property editor's paint method.
   *
   * @param g the current graphics context
   */
  public void paintComponent(Graphics g){

    Insets i = getInsets();
    Rectangle box = new Rectangle(i.left, i.top,
                                  getSize().width - i.left - i.right - 1,
                                  getSize().height - i.top - i.bottom - 1);

    g.clearRect(i.left, i.top,
                getSize().width - i.right - i.left,
                getSize().height - i.bottom - i.top);
    m_Editor.paintValue(g, box);
  }

  /**
   * Shows a popup menu that allows the user to put a textual representation of the object
   * being represented in the system clipboard.
   *
   * @param e The mouse event that provocates the action
   */
  private void showPopUp(MouseEvent e){
    JPopupMenu popupMenu = new JPopupMenu();
    JMenuItem copyToClipBoard = new JMenuItem("Copy the text to the clipboard");
    copyToClipBoard.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e2){
        String obj = ((GenericObjectEditor) m_Editor).getObjectName();
        String ops = ((GenericObjectEditor) m_Editor).getObjectOptions();
        if (ops != "")
          obj += " " + ops;

        StringSelection ss = new StringSelection(obj);

        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, null);
      }
    });
    popupMenu.add(copyToClipBoard);
    popupMenu.show(this, e.getX(), e.getY());
  }

// I think this is redundant
//  public void removeNotify(){
//    //Avoid a Exception: if we close the m_Editor windows and this PropertyPanel has
//    //the focus, a "java.lang.IllegalStateException: Can't dispose InputContext while it's active"
//    //is thrown (see the removeNotify docs). Nevertheless, this must be done.
//    super.removeNotify();
//    if (m_PD != null){
//      m_PD.dispose();
//      m_PD = null;
//    }
//  }
}