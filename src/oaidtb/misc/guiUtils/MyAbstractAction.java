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
 *    MyAbstractAction.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.misc.guiUtils;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Clase que encapsula una {@link javax.swing.AbstractAction}, añadiéndola funcionalidades tales
 * como facilidad de acceso a la tecla de acceso rápido, al tooltip,
 * al icono y al nombre, y la posibilidad de especificar componentes
 * que serán habilitados o deshabilitados según la acción sea
 * habilitada o deshabilitada
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public abstract class MyAbstractAction extends AbstractAction{

  /** El listener que centralizará la habilitación de los componentes asociados */
  private ActionComponentsAdapter m_EnabeDisableList = null;

  public void setAcceleratorKey(KeyStroke keyStroke){
    putValue(Action.ACCELERATOR_KEY, keyStroke);
  }

  public KeyStroke getAcceleratorKey(){
    return (KeyStroke) getValue(Action.ACCELERATOR_KEY);
  }

  public void setToolTipText(String toolTip){
    KeyStroke keyStroke = getAcceleratorKey();
    putValue(Action.SHORT_DESCRIPTION, new String(toolTip +(keyStroke != null ? keyStroke.toString() : "")));
  }

  public String getTip(){
    return (String) getValue(Action.SHORT_DESCRIPTION);
  }

  public void setIcon(Icon icon){
    putValue(Action.SMALL_ICON, icon);
  }

  public Icon getIcon(){
    return (Icon) getValue(Action.SMALL_ICON);
  }

  public void setName(String name){
    putValue(Action.NAME, name);
  }

  public String getName(){
    return (String) getValue(Action.NAME);
  }

  public MyAbstractAction(){}

  public MyAbstractAction(String name){
    super(name);
  }

  public MyAbstractAction(String name, Icon icon){
    super(name, icon);
  }

  /**
   * Añadir un nuevo componente a la lista de componentes cuyo estado de habilitación
   * depende de que esta acción esté o no habilitada
   *
   * @param component Un componente cuyo estado de (des)habilitación será el de la acción
   */
  public void addStateDependantComponent(JComponent component){
    if(m_EnabeDisableList == null){
      m_EnabeDisableList = new ActionComponentsAdapter(component);
      addPropertyChangeListener(m_EnabeDisableList);
    }
    else
      m_EnabeDisableList.addComponent(component);
  }

  /**
   * Clase que sirve para actualizar los componentes ligados a una acción;
   * se puede extender para manejar otras propiedades no estándar de
   * todos los componentes (por ejemplo, el texto o el icono de un JButton).
   */
  public static class ActionComponentsAdapter implements PropertyChangeListener{

    /** El array de componentes */
    private JComponent[] components;

    /**
     * Constructor
     *
     * @param firstComponent El primer componente en la lista
     */
    public ActionComponentsAdapter(JComponent firstComponent){
      components = new JComponent[1];
      components[0] = firstComponent;
    }

    /**
     * Añade un nuevo componente a la lista
     *
     * @param component El componente
     */
    public void addComponent(JComponent component){
      JComponent[] tmp = new JComponent[components.length + 1];
      System.arraycopy(components, 0, tmp, 0, components.length);
      tmp[components.length] = component;
      components = tmp;
    }

    /**
     * Cada vez que la {@link javax.swing.Action} cambie su estado, se cambiará
     * también el de los componentes asociados
     *
     * @param e El evento
     */
    public void propertyChange(PropertyChangeEvent e){
      String propertyName = e.getPropertyName();

      if (propertyName.equals("enabled")){
        Boolean enabledState = (Boolean) e.getNewValue();
        for(int i=0; i<components.length;i++)
          components[i].setEnabled(enabledState.booleanValue());
      }
//      else{
//        if (propertyName.equals(Action.SHORT_DESCRIPTION)){
//          String tip = (String) e.getNewValue();
//          for(int i=0; i<components.length;i++)
//            components[i].setToolTipText(tip);
//        }
//      }
    }
  }
}