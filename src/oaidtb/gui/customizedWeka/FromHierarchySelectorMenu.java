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
 *    FromHierarchySelectorMenu.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.gui.customizedWeka;

import weka.gui.HierarchyPropertyParser;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Iterator;

/**
 * A class that implements a menu that can cascade according to a hierarchical structure.
 * Based on the internal class "CascadedComboBox" of the original GenericObjectEditor in the weka library.
 * It allows the user to select a member from a hierarchy and retrieve this selection as a
 * representative string. To ensure that this selection is really the last selected, the InvokeLater
 * method from the SwingUtilities class should be used (see addSecureChangeListener or
 * the ChangeListener added in the GenericObjectEditor class).
 *
 * <p><p>Also MenuSelectionManager.defaultManager().getSelectedPath() could be used
 *
 * Note: "this" expression in the documentation usually refers to the class instance.
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class FromHierarchySelectorMenu extends JMenu{

  //TODO: Create own HierarchyParser structure or implement it using the JMenu's hierarchy (mergeLevels etc.)
  //TODO: Disable the mnemonic key when selecting (not trivial, setMnemonic method fires
  //      a stateChange event and this also fires a menuEvent, so a lock variable
  //      would be neccesary to avoid infinite recursion). Cosmetic only.

  /** The underlying structure this menu represents. */
  private MultiRootHierarchyPropertyParser m_ObjectNames = new MultiRootHierarchyPropertyParser();

  /** Selection tracking. */
  private String m_LastHierarchyMemberSelected = "";

  /** Selection tracking. */
  private boolean m_HasChanged = false;

  /** Can be different from the one used in m_ObjectNames. */
  private String m_HierarchyLevelsSeparator = ".";

  /*--
    Cosmetic configuration fields.
    Allows the appearance of this menu label to change if a selectable item is being highlighted in the submenu hierarchy.
   --*/
  private int m_CurrentPrefixLength = 0;
  private String m_SelectablePrefix = "";
  private String m_NotSelectablePrefix = "";

  private Icon m_SelectableIcon = null;
  private Icon m_NotSelectableIcon = null;

  private Font m_SelectableFont = null;
  private Font m_NotSelectableFont = null;

  /*--
    Listeners.
   --*/

  /**
   * Updates the label string of the Jmenu.
   */
  private ChangeListener m_JMenuChangeListener = new ChangeListener(){
    public void stateChanged(ChangeEvent e){

      //If we click again in the label to cancel the action, the stateChanged will be fired
      //for some submenus, but in this case we don't want to change anything
      if (!isSelected())
        return;

      JMenu menuItem = (JMenu) e.getSource();

      if (menuItem.isSelected() || isTopLevelComponent(menuItem))
        setNotSelectableMemberName(getSelectionIdentifier(menuItem));
      else
        setNotSelectableMemberName(getSelectionIdentifier(((JMenuItem) menuItem.getAccessibleContext().getAccessibleParent())));
    }
  };

  /**
   * Updates the label string of the JMenuItem and allows some cosmetics enhancements
   */
  private ChangeListener m_JMenuItemChangeListener = new ChangeListener(){
    public void stateChanged(ChangeEvent e){

      //If we click again in the label to cancel the action, the stateChanged will be fired
      //for some submenus, but in this case we don't want to change anything
      if (!isSelected())
        return;

      JMenuItem menuItem = (JMenuItem) e.getSource();

      if (menuItem.isArmed()){
        setFont(m_SelectableFont);
        setIcon(m_SelectableIcon);
        setSelectableMemberName(getSelectionIdentifier(menuItem));
      }
      else{
        setFont(m_NotSelectableFont);
        setIcon(m_NotSelectableIcon);
        if (!isTopLevelComponent(menuItem))
          setNotSelectableMemberName(getSelectionIdentifier(
            ((JMenuItem) menuItem.getAccessibleContext().getAccessibleParent())));
      }
    }
  };

  /**
   * When the user confirms a selection it's fired
   */
  private ActionListener m_SelectionActionListener = new ActionListener(){
    public void actionPerformed(ActionEvent e){

      String tmp = getSelectionIdentifier((JMenuItem) e.getSource());

      //This check avoid to "reset" the selection (i.e. reload a class), so finally we won't make it
      //if (!tmp.equals(m_LastHierarchyMemberSelected))
      m_HasChanged = true;
      m_LastHierarchyMemberSelected = tmp;


      setSelectableMemberName(m_LastHierarchyMemberSelected);
    }
  };

  /**
   * Default constructor
   */
  public FromHierarchySelectorMenu(){

    m_SelectableFont = getFont();
    m_NotSelectableFont = m_SelectableFont;

    addChangeListener(new ChangeListener(){

      public void stateChanged(ChangeEvent e){
        if (isSelected())
          m_HasChanged = false;
      }
    });
  }

  /**
   * Rebuilds from scratch the menu.
   *
   * @param memberNames
   * @param currentName The name to be put as a selectable member in the label string of this menu.
   */
  public void buildMenu(String memberNames, String currentName){

    m_ObjectNames.build(memberNames);

    if (m_ObjectNames.size() > 0){
      try{
        build();
        if (currentName != null)
          setSelectableMemberName(currentName);
        else
          setSelectableMemberName(getFirstValue());
      }
      catch (Exception e){
        e.printStackTrace();
        System.err.println(e.getMessage());
      }
    }
  }

  /*--
    Getter & Setter methods.
   --*/

  public Font getNotSelectableFont(){
    return m_NotSelectableFont;
  }

  public void setNotSelectableFont(Font notSelectableFont){
    m_NotSelectableFont = notSelectableFont;
  }

  public Font getSelectableFont(){
    return m_SelectableFont;
  }

  public void setSelectableFont(Font selectableFont){
    m_SelectableFont = selectableFont;
  }

  public Icon getNotSelectableIcon(){
    return m_NotSelectableIcon;
  }

  public void setNotSelectableIcon(Icon notSelectableIcon){
    m_NotSelectableIcon = notSelectableIcon;
  }

  public Icon getSelectableIcon(){
    return m_SelectableIcon;
  }

  public void setSelectableIcon(Icon selectableIcon){
    m_SelectableIcon = selectableIcon;
  }

  public String getNotSelectablePrefix(){
    return m_NotSelectablePrefix;
  }

  public void setNotSelectablePrefix(String notSelectablePrefix){
    m_NotSelectablePrefix = notSelectablePrefix == null ? "" : notSelectablePrefix;
  }

  public String getSelectablePrefix(){
    return m_SelectablePrefix;
  }

  public void setSelectablePrefix(String selectablePrefix){
    m_SelectablePrefix = selectablePrefix == null ? "" : selectablePrefix;
  }

  /**
   * Return the name of the member currently shown in the menu
   *
   * @return The name of the selected member.
   */
  public String getMemberName(){
    return getText().substring(m_CurrentPrefixLength);
  }

  /**
   * Set this label text using the selectable prefix.
   *
   * @param text The text to be put in this label (usually a JMenu hierarchical identifier).
   */
  public void setSelectableMemberName(String text){
    setText(m_SelectablePrefix + text);
    m_CurrentPrefixLength = m_SelectablePrefix.length();
  }

  /**
   * Set this label text using the not selectable prefix.
   *
   * @param text The text to be put in this label (usually a leaf JMenuItem hierarchical identifier).
   */
  public void setNotSelectableMemberName(String text){
    setText(m_NotSelectablePrefix + text);
    m_CurrentPrefixLength = m_NotSelectablePrefix.length();
  }

  /** @return The underlying structure from where menu is constructed. */
  public MultiRootHierarchyPropertyParser getObjectNames(){
    return m_ObjectNames;
  }

  /**
   * The caracters that separates the levels of each hierarchy.
   *
   * Note: this could be different to the one used by the underlying structure from where menu is constructed;
   * to get that, use getObjectNames.getHierarchyLevelsSeparator()
   *
   * @return The caracters that separates the levels of each hierarchy.
   */
  public String getHierarchyLevelsSeparator(){
    return m_HierarchyLevelsSeparator;
  }


  /**
   * The caracters that separates the levels of each hierarchy.
   *
   * Note: this could be different to the one used by the underlying structure from where menu is constructed;
   * to set that, use getObjectNames.setHierarchyLevelsSeparator()
   *
   * @param hierarchyLevelsSeparator The caracters that separates the levels of each hierarchy.
   */
  public void setHierarchyLevelsSeparator(String hierarchyLevelsSeparator){
    m_HierarchyLevelsSeparator = hierarchyLevelsSeparator;
  }

  /** @return The caracters that separates the individual members of the hierarchies. */
  public String getHierarchyMembersSeparator(){
    return m_ObjectNames.getHierarchyMembersSeparator();
  }

  /** @param hierarchyMembersSeparator The caracters that separates the individual members of the hierarchies. */
  public void setHierarchyMembersSeparator(String hierarchyMembersSeparator){
    m_ObjectNames.setHierarchyMembersSeparator(hierarchyMembersSeparator);
  }

  /*--------------------- Menu builder methods ---------------------*/

  /** The model containing the list of names to select from.
   *  Necessary to minimize parameter pass until an iterative version of build is made, if ever.
   */
  private HierarchyPropertyParser m_CurrentRootObjectNames;

  /**
   * Rebuilds the submenu hierarchy based on the underlying structure.
   * Caution: It removes all the submenus before reconstructing the tree
   * TODO: Control the maximun number of levels in a single MenuItem / minimum depth etc.
   */
  private void build(){

    Iterator iterator = m_ObjectNames.iterator();

    //Be careful, if it fails it will destroy the tree
    removeAll();

    //We do it for all roots
    while (iterator.hasNext()){
      m_CurrentRootObjectNames = (HierarchyPropertyParser) iterator.next();
      m_CurrentRootObjectNames.goToRoot();
      JMenuItem singleMemberCase = recursiveMenuHierarchyBuild();
      if (null != singleMemberCase)
        add(singleMemberCase);
    }
  }

  /**
   * Private procedure to build the cascaded comboBox; slightly modified from the weka´s original.
   */
  private JMenuItem recursiveMenuHierarchyBuild(){
    JMenuItem menu;
    int singleItem = 0;
    boolean isFromRoot = m_CurrentRootObjectNames.isRootReached();
    //El texto para poner en este menu
    StringBuffer sb = new StringBuffer(m_CurrentRootObjectNames.getValue());

    //Siempre que se tenga sólo un hijo, lo ponemos todo junto.
    while (m_CurrentRootObjectNames.numChildren() == 1){
      try{
        m_CurrentRootObjectNames.goToChild(0);
      }
      catch (Exception e){
      }
      sb.append(m_HierarchyLevelsSeparator + m_CurrentRootObjectNames.getValue());
      singleItem++;
    }

    //MODICADO (...&& m_ObjectNames.size() == 1): Colocar objetos que tengan diferentes raíces
    //siempre en submenus distintos (y que no quepa la posibilidad de colocarlos como items seleccionables
    //directamente en el primer subnivel de la jerarquía de menús (peazo de lío)
    if (isFromRoot && (!m_CurrentRootObjectNames.isLeafReached()) && m_ObjectNames.size() == 1){
      String[] kids = m_CurrentRootObjectNames.childrenValues();
      for (int i = 0; i < kids.length; i++){
        String child = kids[i];
        m_CurrentRootObjectNames.goToChild(child);
        if (m_CurrentRootObjectNames.isLeafReached()){
          menu = new JMenuItem(m_CurrentRootObjectNames.fullValue());
          menu.addChangeListener(m_JMenuItemChangeListener);
          menu.addActionListener(m_SelectionActionListener);
        }
        else{
          menu = new JMenu(m_CurrentRootObjectNames.fullValue());
          menu.addChangeListener(m_JMenuChangeListener);

          String[] grandkids =
            m_CurrentRootObjectNames.childrenValues();
          for (int j = 0; j < grandkids.length; j++){
            m_CurrentRootObjectNames.goToChild(grandkids[j]);
            menu.add(recursiveMenuHierarchyBuild());
          }
        }

        m_CurrentRootObjectNames.goToParent();
        add(menu);
      }
      menu = null;
    }
    else if (m_CurrentRootObjectNames.isLeafReached()){
      menu = new JMenuItem(sb.toString());
      menu.addChangeListener(m_JMenuItemChangeListener);
      menu.addActionListener(m_SelectionActionListener);
    }
    else{
      menu = new JMenu(sb.toString());
      menu.addChangeListener(m_JMenuChangeListener);

      String[] kids = m_CurrentRootObjectNames.childrenValues();
      for (int i = 0; i < kids.length; i++){
        String child = kids[i];
        m_CurrentRootObjectNames.goToChild(child);
        menu.add(recursiveMenuHierarchyBuild());
      }
    }

    for (int i = 0; i <= singleItem; i++)
      m_CurrentRootObjectNames.goToParent(); // One more level up

    return menu;
  }

  /**
   * Search the HierarchyPropertyParser for the given member name,
   * if not found, add it into the appropriate position; rebuilds all the
   * menu hierarchy. Does nothing if already there
   *
   * TODO: Make build iterative & not reconstruct all when adding...
   *
   * @param memberName the given member name
   */
  public void addMemberName(String memberName){
    if (!m_ObjectNames.contains(memberName)){
      m_ObjectNames.add(memberName);
      try{
        //Build a more efficient function to...
        build();
      }
      catch (Exception e){
      }
    }
  }

  /**
   * Is the item a direct submenu of this menu?
   *
   * @param item A menu item.
   * @return True if it's a first level submenu
   */
  private boolean isTopLevelComponent(JMenuItem item){
    return item.getAccessibleContext().getAccessibleParent() == this;
  }

  /**
   * Get the string identifying a JMenuItem in the Hierarchy
   *
   * @param selected The JMenuItem
   * @return A string representing the Hierarchy context of the JMenuItem
   */
  private String getSelectionIdentifier(JMenuItem selected){
    String tmp = selected.getText();
    while (!isTopLevelComponent(selected)){
      selected = (JMenuItem) selected.getAccessibleContext().getAccessibleParent();
      tmp = selected.getText().concat(m_HierarchyLevelsSeparator + tmp);
    }
    return tmp;
  }

  /** @return If the last selected item has changed */
  public boolean hasChanged(){
    return m_HasChanged;
  }

  /** @return The string identifying the last selected hierarchy member. */
  public String getLastHierarchyMemberSelected(){
    return m_LastHierarchyMemberSelected;
  }

  /**
   * @return The first value introduced in the tree
   * @throws Exception If no value exists in the tree
   */
  public String getFirstValue() throws Exception{
    return m_ObjectNames.getFirstValue();
  }

  /**
   * Adds a <code>ChangeListener</code> to the button. The event will be dispatched
   * after all of the queued events in the event dispatch thread.
   * @param l the listener to be added
   */
  public void addSecureChangeListener(final ChangeListener l){
    super.addChangeListener(new ChangeListener(){
      public void stateChanged(final ChangeEvent e){
        SwingUtilities.invokeLater(new Runnable(){
          public void run(){
            l.stateChanged(e);
          }
        });
      }
    });
  }
}