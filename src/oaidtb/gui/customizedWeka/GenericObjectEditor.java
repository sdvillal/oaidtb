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
 *    GenericObjectEditor.java
 *    Copyright (C) 2001 Len Trigg, Xin Xu
 *    Modified (2002) by Santiago David Villalba
 *
 */

package oaidtb.gui.customizedWeka;

import weka.core.OptionHandler;
import weka.core.SelectedTag;
import weka.core.SerializedObject;
import weka.core.Utils;
import weka.gui.FileEditor;
import weka.gui.PropertyDialog;
import weka.gui.SelectedTagEditor;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.beans.PropertyEditor;
import java.io.*;
import java.util.Properties;

import oaidtb.gui.AllContainerPanel;

/**
 * A PropertyEditor for objects that themselves have been defined as
 * editable in the GenericObjectEditor configuration file, which lists
 * possible values that can be selected from, and themselves configured.
 * The configuration file is called "GenericObjectEditor.props" and
 * may live in either the location given by "user.home" or the current
 * directory (this last will take precedence), and a default properties
 * file is read from the weka distribution. For speed, the properties
 * file is read only once when the class is first loaded -- this may need
 * to be changed if we ever end up running in a Java OS ;-).
 *
 * TODO: PropertySheetPanel class calls the setClassType only if the class is a weka.gui.GenericObjectEditor
 *   ==> Change PropertySheetPanel
 *
 * @author Len Trigg (trigg@cs.waikato.ac.nz)
 * @author Xin Xu (xx5@cs.waikato.ac.nz)
 * @author Santiago David Villalba (sdvb@wanadoo.es)
 * @version $Revision: 1.34 $
 */
public class GenericObjectEditor implements PropertyEditor{

  /** The classifier being configured */
  private Object m_Object;

  /** Holds a copy of the current classifier that can be reverted to
   if the user decides to cancel */
  private Object m_Backup;

  /** Handles property change notification */
  private PropertyChangeSupport m_Support = new PropertyChangeSupport(this);

  /** The Class of objects being edited */
  private Class m_ClassType;

  /*---New feature: class customized properties.---*/
  /**
   * Allows to specialize the values that can be selected from a class of a certain type, by example,
   * Booster@AdaBoostMH
   */
  private String m_SpecializeFromClass = null;

  public String getSpecializeFromClass(){
    return m_SpecializeFromClass;
  }

  public void setSpecializeFromClass(String specializeFromClass){
    m_SpecializeFromClass = specializeFromClass;
  }
  /*---New feature: class customized properties.---*/

  /** The GUI component for editing values, created when needed */
  private GOEPanel m_EditorComponent = new GOEPanel();

  /** True if the GUI component is needed */
  private boolean m_Enabled = true;

  /** The name of the properties file */
  protected static String PROPERTY_FILE = "weka/gui/GenericObjectEditor.props";

  /** Contains the editor properties */
  private static Properties EDITOR_PROPERTIES;

  /** Loads the configuration property file && cargar la lista de editores para las propiedades */
  static{

    //Intentar cargar la lista de editores para cada objeto
    //Buscamos una clase RegisterEditors en el paquete al que pertenezca esta clase "GenericObjectEditor"
    //y, si no la encontramos, en el directorio desde donde ejecutamos.
    //Ver oaidtb.gui.customizedWeka.RegisterEditors.java como un ejemplo sobre el formato de dicha clase
    try{
      Class.forName(GenericObjectEditor.class.getPackage().getName() + ".RegisterEditors");
    }
    catch (Exception ex){
      try{
        Class.forName("RegisterEditors");
      }
      catch (Exception ex2){
        System.err.println("Warning: can't load property editors configutration class");
      }
    }

// Allow a properties file in the current directory to override
    try{
      EDITOR_PROPERTIES = Utils.readProperties(PROPERTY_FILE);
      java.util.Enumeration keys =
        (java.util.Enumeration) EDITOR_PROPERTIES.propertyNames();
      if (!keys.hasMoreElements()){
        throw new Exception("Failed to read a property file for the "
                            + "generic object editor");
      }
    }
    catch (Exception ex){
      JOptionPane.showMessageDialog(null,
                                    "Could not read a configuration file for the generic object\n"
                                    + "editor. An example file is included with the Weka distribution.\n"
                                    + "This file should be named \"" + PROPERTY_FILE + "\" and\n"
                                    + "should be placed either in your user home (which is set\n"
                                    + "to \"" + System.getProperties().getProperty("user.home") + "\")\n"
                                    + "or the directory that java was started from\n",
                                    "GenericObjectEditor",
                                    JOptionPane.ERROR_MESSAGE);
    }
  }

  /**
   * Handles the GUI side of editing values.
   */
  public class GOEPanel extends JPanel{

    /** The chooser component */
    private FromHierarchySelectorMenu m_ObjectChooser;

    /** The component that performs classifier customization */
    private PropertySheetPanel m_ChildPropertySheet;

    /** Copy object textual representation to the system clipboard */
    private JButton m_ClipboardCopyBut;

    /** Open object from disk */
    private JButton m_OpenBut;

    /** Save object to disk */
    private JButton m_SaveBut;

    /** ok button */
    private JButton m_okBut;

    /** cancel button */
    private JButton m_cancelBut;

    /** The filechooser for opening and saving object files */
    private JFileChooser m_FileChooser;

    /** The Menubar to show the classes */
    private JMenuBar m_MenuBar;

    /** Creates the GUI editor component */
    public GOEPanel(){
      //System.err.println("GOE(): " + m_Object);
      m_Backup = copyObject(m_Object);

      m_MenuBar = new JMenuBar();
      m_MenuBar.setLayout(new BorderLayout());

      //Menu setup
      m_ObjectChooser = new FromHierarchySelectorMenu();
      m_ObjectChooser.setMnemonic('c');
      m_ObjectChooser.setSelectablePrefix("Class: ");
      m_ObjectChooser.setNotSelectablePrefix("Package: ");
      m_ObjectChooser.setSelectableFont(m_ObjectChooser.getFont().deriveFont(Font.ITALIC | Font.BOLD));
      m_ObjectChooser.setSelectableIcon(AllContainerPanel.PROFX_ICON);
      m_ObjectChooser.getObjectNames().setHierarchyLevelsSeparator(".");
      m_ObjectChooser.setHierarchyLevelsSeparator(".");
      m_ObjectChooser.setHierarchyMembersSeparator(", ");

      //What to do when we select a new class.
      m_ObjectChooser.addChangeListener(new ChangeListener(){

        public void stateChanged(ChangeEvent e){

          if (m_ObjectChooser.isSelected())
            return;

          //We assure that all of the pending events are processed before this one.
          //If it was an internal class, it could be easier and faster (added as an ActionListener to
          //all of the JMenuItem's), but less reusable.
          SwingUtilities.invokeLater(new Runnable(){
            public void run(){
              if (!m_ObjectChooser.hasChanged()){
                if (m_Object != null)
                  m_ObjectChooser.setSelectableMemberName(m_Object.getClass().getName());
              }
              else
                try{
                  setValue(Class.forName(m_ObjectChooser.getLastHierarchyMemberSelected()).newInstance());
                }
                catch (Exception ex){

                  JOptionPane.showMessageDialog(null,
                                                "Could not create an example of\n"
                                                + m_ObjectChooser.getLastHierarchyMemberSelected() + "\n"
                                                + "from the current classpath",
                                                "example loaded",
                                                JOptionPane.ERROR_MESSAGE);

                  try{
                    if (null == m_Backup){
                      setDefaultValue();
                    }
                    else{
                      setValue(m_Backup);
                    }
                  }
                  catch (Exception ex2){
                    System.err.println(ex.getMessage());
                    ex.printStackTrace();
                  }
                }
            }
          });
        }
      });

      //Construct the menu tree
      updateClassType();

      m_MenuBar.add(m_ObjectChooser, BorderLayout.CENTER);
      m_MenuBar.setSelected(m_ObjectChooser);

      m_ChildPropertySheet = new PropertySheetPanel();
      m_ChildPropertySheet.addPropertyChangeListener
        (new PropertyChangeListener(){
          public void propertyChange(PropertyChangeEvent evt){
            m_Support.firePropertyChange("", null, null);
          }
        });

      m_ClipboardCopyBut = new JButton("To Clipboard");
      m_ClipboardCopyBut.setToolTipText("Copy the object's textual representation to the clipboard");
      m_ClipboardCopyBut.setEnabled(true);
      m_ClipboardCopyBut.addActionListener(new ActionListener(){
        public void actionPerformed(ActionEvent e){
          String rep = m_Object.getClass().getName();
          if (m_Object instanceof OptionHandler){
            rep += " " + Utils.joinOptions(((OptionHandler) m_Object)
                                           .getOptions());
          }
          StringSelection ss = new StringSelection(rep);
          Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, null);
        }
      });

      m_OpenBut = new JButton("Open...");
      m_OpenBut.setToolTipText("Load a configured object");
      m_OpenBut.setEnabled(true);
      m_OpenBut.addActionListener(new ActionListener(){
        public void actionPerformed(ActionEvent e){
          Object object = openObject();
          if (object != null){
            // setValue takes care of: Making sure obj is of right type,
            // and firing property change.
            setValue(object);
            // Need a second setValue to get property values filled in OK.
            // Not sure why.
            setValue(object);
          }
        }
      });

      m_SaveBut = new JButton("Save...");
      m_SaveBut.setToolTipText("Save the current configured object");
      m_SaveBut.setEnabled(true);
      m_SaveBut.addActionListener(new ActionListener(){
        public void actionPerformed(ActionEvent e){
          saveObject(m_Object);
        }
      });

      m_okBut = new JButton("OK");
      m_okBut.setEnabled(true);
      m_okBut.addActionListener(new ActionListener(){
        public void actionPerformed(ActionEvent e){
          //System.err.println("\nOK--Backup: "+
          // m_Backup.getClass().getName()+
          //	   "\nOK--Object: "+
          //		   m_Object.getClass().getName());
          m_Backup = copyObject(m_Object);
          if ((getTopLevelAncestor() != null)
            && (getTopLevelAncestor() instanceof Window)){
            Window w = (Window) getTopLevelAncestor();
            w.dispose();
          }
        }
      });

      m_cancelBut = new JButton("Cancel");
      m_cancelBut.setEnabled(true);
      m_cancelBut.addActionListener(new ActionListener(){
        public void actionPerformed(ActionEvent e){
          if (m_Backup != null){
            //System.err.println("\nCncl--Backup: "+
            // m_Backup.getClass().getName()+
            //	       "\nCncl--Object: "+
            //       m_Object.getClass().getName());
            m_Object = copyObject(m_Backup);

            // To fire property change
            m_Support.firePropertyChange("", null, null);
            updateClassType();
            updateChooser();
            updateChildPropertySheet();
          }
          if ((getTopLevelAncestor() != null)
            && (getTopLevelAncestor() instanceof Window)){
            Window w = (Window) getTopLevelAncestor();
            w.dispose();
          }
        }
      });

      setLayout(new BorderLayout());

      add(m_MenuBar, BorderLayout.NORTH);
      add(m_ChildPropertySheet, BorderLayout.CENTER);
      // Since we resize to the size of the property sheet, a scrollpane isn't
      // typically needed
      // addMemberName(new JScrollPane(m_ChildPropertySheet), BorderLayout.CENTER);

      JPanel okcButs = new JPanel();
      okcButs.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
      okcButs.setLayout(new GridLayout(1, 5, 5, 5));
      okcButs.add(m_OpenBut);
      okcButs.add(m_SaveBut);
      okcButs.add(m_ClipboardCopyBut);
      okcButs.add(m_okBut);
      okcButs.add(m_cancelBut);
      add(okcButs, BorderLayout.SOUTH);

      if (m_ClassType != null){
        updateClassType();
        if (m_Object != null){
          updateChooser();
          updateChildPropertySheet();
        }
      }
    }

    /**
     * Enables/disables the cancel button.
     *
     * @param flag true to enable cancel button, false
     * to disable it
     */
    protected void setCancelButton(boolean flag){
      if (m_cancelBut != null)
        m_cancelBut.setEnabled(flag);
    }

    /**
     * Opens an object from a file selected by the user.
     *
     * @return the loaded object, or null if the operation was cancelled
     */
    protected Object openObject(){

      if (m_FileChooser == null){
        createFileChooser();
      }
      int returnVal = m_FileChooser.showOpenDialog(this);
      if (returnVal == JFileChooser.APPROVE_OPTION){
        File selected = m_FileChooser.getSelectedFile();
        try{
          ObjectInputStream oi = new ObjectInputStream(new BufferedInputStream(new FileInputStream(selected)));
          Object obj = oi.readObject();
          oi.close();
          if (!m_ClassType.isAssignableFrom(obj.getClass())){
            throw new Exception("Object not of type: " + m_ClassType.getName());
          }
          return obj;
        }
        catch (Exception ex){
          JOptionPane.showMessageDialog(this,
                                        "Couldn't read object: "
                                        + selected.getName()
                                        + "\n" + ex.getMessage(),
                                        "Open object file",
                                        JOptionPane.ERROR_MESSAGE);
        }
      }
      return null;
    }

    /**
     * Opens an object from a file selected by the user.
     */
    protected void saveObject(Object object){

      if (m_FileChooser == null){
        createFileChooser();
      }
      int returnVal = m_FileChooser.showSaveDialog(this);
      if (returnVal == JFileChooser.APPROVE_OPTION){
        File sFile = m_FileChooser.getSelectedFile();
        try{
          ObjectOutputStream oo = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(sFile)));
          oo.writeObject(object);
          oo.close();
        }
        catch (Exception ex){
          JOptionPane.showMessageDialog(this,
                                        "Couldn't write to file: "
                                        + sFile.getName()
                                        + "\n" + ex.getMessage(),
                                        "Save object",
                                        JOptionPane.ERROR_MESSAGE);
        }
      }
    }

    protected void createFileChooser(){

      m_FileChooser = new JFileChooser(new File(System.getProperty("user.dir")));
      m_FileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    }

    /**
     * Makes a copy of an object using serialization
     * @param source the object to copy
     * @return a copy of the source object
     */
    protected Object copyObject(Object source){
      Object result = null;
      try{
        SerializedObject so = new SerializedObject(source);
        result = so.getObject();
        setCancelButton(true);

      }
      catch (Exception ex){
        setCancelButton(false);
        System.err.println("GenericObjectEditor: Problem making backup object");
        System.err.println(ex);
      }
      return result;
    }

    /**
     * This is used to hook an action listener to the ok button
     * @param a The action listener.
     */
    public void addOkListener(ActionListener a){
      m_okBut.addActionListener(a);
    }

    /**
     * This is used to hook an action listener to the cancel button
     * @param a The action listener.
     */
    public void addCancelListener(ActionListener a){
      m_cancelBut.addActionListener(a);
    }

    /**
     * This is used to remove an action listener from the ok button
     * @param a The action listener
     */
    public void removeOkListener(ActionListener a){
      m_okBut.removeActionListener(a);
    }

    /**
     * This is used to remove an action listener from the cancel button
     * @param a The action listener
     */
    public void removeCancelListener(ActionListener a){
      m_cancelBut.removeActionListener(a);
    }

    /** Called when the class of object being edited changes. */
    protected void updateClassType(){

      if (m_ClassType == null)
        return;

      //if(m_ObjectChooser == null)
      if (m_Object != null)
        m_ObjectChooser.buildMenu(getClassesFromProperties(), m_Object.getClass().getName());
      else
        m_ObjectChooser.buildMenu(getClassesFromProperties(), null);
    }

    /** Called to update the cascaded combo box of the values to
     * to be selected from */
    protected void updateChooser(){

      String objectName = m_Object.getClass().getName();

      m_ObjectChooser.addMemberName(objectName);
      m_ObjectChooser.setSelectableMemberName(objectName);

      repaint();
    }

    /** Updates the child property sheet, and creates if needed */
    public void updateChildPropertySheet(){

      //System.err.println("GOE::updateChildPropertySheet()");
      // Set the object as the target of the propertysheet
      m_ChildPropertySheet.setTarget(m_Object);

      // Adjust size of containing window if possible
      if ((getTopLevelAncestor() != null)
        && (getTopLevelAncestor() instanceof Window)){
        ((Window) getTopLevelAncestor()).pack();
      }
    }

    public String getDefaultValue() throws Exception{
      return m_ObjectChooser.getFirstValue();
    }

    /**
     * Para poder configurar el menú (fuentes, iconos etc.) desde fuera
     *
     * @return El menú que permite seleccionar entre los objetos
     */
    public FromHierarchySelectorMenu getObjectChooser(){
      return m_ObjectChooser;
    }
  }

  /** Called when the class of object being edited changes. */
  protected String getClassesFromProperties(){

    String className = m_ClassType.getName();
    String typeOptions = null;

    if (m_SpecializeFromClass != null)
      typeOptions = EDITOR_PROPERTIES.getProperty(className + "@" + m_SpecializeFromClass);

    if (typeOptions == null)
      typeOptions = EDITOR_PROPERTIES.getProperty(className);

    if (typeOptions == null){
      System.err.println("Warning: No configuration property found in\n"
                         + PROPERTY_FILE + "\n"
                         + "for " + className);
    }
    return typeOptions;
  }

  /**
   * Sets whether the editor is "enabled", meaning that the current
   * values will be painted.
   *
   * @param newVal a value of type 'boolean'
   */
  public void setEnabled(boolean newVal){

    if (newVal != m_Enabled){
      m_Enabled = newVal;
      /*
        if (m_EditorComponent != null) {
        m_EditorComponent.setEnabled(m_Enabled);
        }
      */
    }
  }

  /**
   * Sets the class of values that can be edited.
   *
   * @param type a value of type 'Class'
   */
  public void setClassType(Class type){

//System.err.println("setClassType("
//		   + (type == null? "<null>" : type.getName()) + ")");
    m_ClassType = type;

    if (m_EditorComponent != null){
      m_EditorComponent.updateClassType();
    }
  }

  /**
   * Sets the current object to be the default, taken as the first item in
   * the chooser
   */
  public void setDefaultValue(){

//System.err.println("GOE::setDefaultValue()");
    if (m_ClassType == null){
      System.err.println("No ClassType set up for GenericObjectEditor!!");
      return;
    }

    String defaultValue = null;
    try{
      defaultValue = m_EditorComponent.getDefaultValue();
      setValue(Class.forName(defaultValue).newInstance());
    }
    catch (Exception ex){
      if (defaultValue != null)
        System.err.println("Problem loading the first class: " + defaultValue);
      else
        System.err.println("Can't find the first class name.");

      ex.printStackTrace();
    }
  }

  /**
   * Sets the current Object. If the Object is in the
   * Object chooser, this becomes the selected item (and added
   * to the chooser if necessary).
   *
   * @param o an object that must be a Object.
   */
  public void setValue(Object o){

    if (m_ClassType == null){
      System.err.println("No ClassType set up for GenericObjectEditor!!");
      return;
    }

    if (!m_ClassType.isAssignableFrom(o.getClass())){
      System.err.println("setValue object not of correct type!");
      return;
    }

    setObject(o);

    if (m_EditorComponent != null)
      m_EditorComponent.updateChooser();

    //If Class.forName(selected).newInstance() fails in the next call, m_Backup will
    //not be updated to the last succesfully open object, it will turn back two calls
    //unless we do this.
    m_Backup = m_Object;
  }

  /**
   * Sets the current Object, but doesn't worry about updating
   * the state of the object chooser.
   *
   * @param c a value of type 'Object'
   */
  private void setObject(Object c){

// This should really call equals() for comparison.
    boolean trueChange;
    if (getValue() != null){
      trueChange = (!c.equals(getValue()));
      //System.err.println("GEO::setObject(): Changed? " + trueChange+ getValue().getClass().getName());
    }
    else
      trueChange = true;

    m_Object = c;

    if (m_EditorComponent != null){
      m_EditorComponent.updateChildPropertySheet();
      if (trueChange){
        m_Support.firePropertyChange("", null, null);
      }
    }
  }


  /**
   * Gets the current Object.
   *
   * @return the current Object
   */
  public Object getValue(){

    //System.err.println("getValue()");
    return m_Object;
  }

  /**
   * Supposedly returns an initialization string to create a Object
   * identical to the current one, including it's state, but this doesn't
   * appear possible given that the initialization string isn't supposed to
   * contain multiple statements.
   *
   * @return the java source code initialisation string
   */
  public String getJavaInitializationString(){
    return "new " + m_Object.getClass().getName() + "()";
  }

  /**
   * Returns true to indicate that we can paint a representation of the
   * Object.
   *
   * @return true
   */
  public boolean isPaintable(){
    return true;
  }

  /**
   * Paints a representation of the current Object.
   *
   * @param gfx the graphics context to use
   * @param box the area we are allowed to paint into
   */
  public void paintValue(java.awt.Graphics gfx, java.awt.Rectangle box){

    if (m_Enabled && m_Object != null){
      FontMetrics fm = gfx.getFontMetrics();
      int vpad = (box.height - fm.getHeight()) / 2;
      gfx.drawString(getObjectConfigurationText(), 2, fm.getHeight() + vpad);
    }
  }

  /**
   * TODO: Replace the getAsText method with this and, to conform the PropertyEditor API, enable the setAsText()
   *
   * @return The representation of the current object and its configuration as a String
   */
  public String getObjectConfigurationText(){
    if (!(m_Enabled && m_Object != null))
      return "";

    String rep = m_Object.getClass().getName();
    int dotPos = rep.lastIndexOf('.');
    if (dotPos != -1){
      rep = rep.substring(dotPos + 1);
    }
    if (m_Object instanceof OptionHandler)
      rep += " " + Utils.joinOptions(((OptionHandler) m_Object).getOptions());
    return rep;
  }

  /** @return The object name. If none, "". */
  public String getObjectName(){
    if (!(m_Enabled && m_Object != null))
      return "";

    return m_Object.getClass().getName();
  }

  /** @return The object options. If none, "" */
  public String getObjectOptions(){

    if (m_Object instanceof OptionHandler)
      return Utils.joinOptions(((OptionHandler) m_Object).getOptions());

    return "";
  }

  /**
   * Returns null as we don't support getting/setting values as text.
   *
   * @return null
   */
  public String getAsText(){
    return null;
  }

  /**
   * Returns null as we don't support getting/setting values as text.
   *
   * @param text the text value
   * @exception IllegalArgumentException as we don't support
   * getting/setting values as text.
   */
  public void setAsText(String text) throws IllegalArgumentException{
    throw new IllegalArgumentException(text);
  }

  /**
   * Returns null as we don't support getting values as tags.
   *
   * @return null
   */
  public String[] getTags(){
    return null;
  }

  /**
   * Returns true because we do support a custom editor.
   *
   * @return true
   */
  public boolean supportsCustomEditor(){
    return true;
  }

  /**
   * Returns the array editing component.
   *
   * @return a value of type 'java.awt.Component'
   */
  public java.awt.Component getCustomEditor(){

    //System.err.println("getCustomEditor()");
    if (m_EditorComponent == null){
      //System.err.println("creating new editing component");
      m_EditorComponent = new GOEPanel();
    }
    return m_EditorComponent;
  }

  /**
   * Adds a PropertyChangeListener who will be notified of value changes.
   *
   * @param l a value of type 'PropertyChangeListener'
   */
  public void addPropertyChangeListener(PropertyChangeListener l){
    m_Support.addPropertyChangeListener(l);
  }

  /**
   * Removes a PropertyChangeListener.
   *
   * @param l a value of type 'PropertyChangeListener'
   */
  public void removePropertyChangeListener(PropertyChangeListener l){
    m_Support.removePropertyChangeListener(l);
  }

  /**
   * Tests out the Object editor from the command line.
   *
   * @param args may contain the class name of a Object to edit
   */
  public static void main(String[] args){

    try{
      System.err.println("---Registering Weka Editors---");
      java.beans.PropertyEditorManager
        .registerEditor(weka.experiment.ResultProducer.class,
                        GenericObjectEditor.class);
      java.beans.PropertyEditorManager
        .registerEditor(weka.experiment.SplitEvaluator.class,
                        GenericObjectEditor.class);
      java.beans.PropertyEditorManager
        .registerEditor(weka.classifiers.Classifier.class,
                        GenericObjectEditor.class);
      java.beans.PropertyEditorManager
        .registerEditor(weka.attributeSelection.ASEvaluation.class,
                        GenericObjectEditor.class);
      java.beans.PropertyEditorManager
        .registerEditor(weka.attributeSelection.ASSearch.class,
                        GenericObjectEditor.class);
      java.beans.PropertyEditorManager
        .registerEditor(SelectedTag.class,
                        SelectedTagEditor.class);
      java.beans.PropertyEditorManager
        .registerEditor(java.io.File.class,
                        FileEditor.class);
      GenericObjectEditor ce = new GenericObjectEditor();
      ce.setClassType(weka.classifiers.Classifier.class);
      Object initial = new weka.classifiers.rules.ZeroR();
//      ce.setClassType(oaidtb.misc.pointDistributions.NormalDistribution.class);
//      Object initial = new oaidtb.misc.pointDistributions.NormalDistribution();
      if (args.length > 0){
        ce.setClassType(Class.forName(args[0]));
        if (args.length > 1){
          initial = Class.forName(args[1]).newInstance();
          ce.setValue(initial);
        }
        else
          ce.setDefaultValue();
      }
      else
        ce.setValue(initial);

      PropertyDialog pd = new PropertyDialog(ce, 100, 100);
      pd.addWindowListener(new WindowAdapter(){

        /** Invoked when a window has been closed. Needed fro properly work of the cancel button */
        public void windowClosed(WindowEvent e){
          windowClosing(e);
        }

        public void windowClosing(WindowEvent e){
          PropertyEditor pe = ((PropertyDialog) e.getSource()).getEditor();
          Object c = pe.getValue();
          String options = "";
          if (c instanceof OptionHandler){
            options = Utils.joinOptions(((OptionHandler) c).getOptions());
          }
          System.out.println(c.getClass().getName() + " " + options);
          System.exit(0);
        }
      });
    }
    catch (Exception ex){
      ex.printStackTrace();
      System.err.println(ex.getMessage());
    }
  }
}
