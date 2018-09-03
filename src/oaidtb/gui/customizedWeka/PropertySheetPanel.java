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
 *    PropertySheet.java
 *    Copyright (C) 1999 Len Trigg
 *    Modified (2002) by Santiago David Villalba
 *
 * Customized Weka changes-->
 *   ** The global info text area won't receive the focus (and so it won't swallow it).
 *   ** Reference to the oaidtb's GenericObjectEditor instead of weka's one
 *   ** It pass to every GenericObjectEditor referenced in the sheet the class name of the object being edited,
 *      so they can retrieve special names from the properties configuration file (i.e. Booster@MH).
 *   ** Possibility of customize the order in which the properties are displayed by the edited object's
 *      "sortProperties" method.
 *   ** "More information window" converted to a JDialog (see openHelpFrame)
 *   ** Verbose mode added, and by default disbled (I don't like so many messages to the
 *      System.err stream in an already validated class)
 *
 */


package oaidtb.gui.customizedWeka;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


/**
 * Displays a property sheet where (supported) properties of the target
 * object may be edited.
 *
 * @author Len Trigg (trigg@cs.waikato.ac.nz)
 * @author Santiago David Villalba (sdvb@wanadoo.es)
 * @version $Revision: 1.10 $
 */
public class PropertySheetPanel extends JPanel

  implements PropertyChangeListener{

  /** The target object being edited */
  private Object m_Target;

  /** Holds properties of the target */
  private PropertyDescriptor m_Properties[];

  /** Holds the methods of the target */
  private MethodDescriptor m_Methods[];

  /** Holds property editors of the object */
  private PropertyEditor m_Editors[];

  /** Holds current object values for each property */
  private Object m_Values[];

  /** Stores GUI components containing each editing component */
  private JComponent m_Views[];

  /** The labels for each property */
  private JLabel m_Labels[];

  /** The tool tip text for each property */
  private String m_TipTexts[];

  /** StringBuffer containing help text for the object being edited */
  private StringBuffer m_HelpText;

  /** Help frame */
  private JDialog m_HelpFrame;

  /** Button to pop up the full help text in a separate frame */
  private JButton m_HelpBut;

  /** A count of the number of properties we have an editor for */
  private int m_NumEditable = 0;

  /** Send or not warning messages to the System.err when introspecting */
  private boolean m_VerboseMode = false;

  public boolean isVerboseMode(){
    return m_VerboseMode;
  }

  public void setVerboseMode(boolean verboseMode){
    m_VerboseMode = verboseMode;
  }

  /**
   * Creates the property sheet panel.
   */
  public PropertySheetPanel(){

    //    setBorder(BorderFactory.createLineBorder(Color.red));
    setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
  }

  /** A support object for handling property change listeners */
  private PropertyChangeSupport support = new PropertyChangeSupport(this);

  /**
   * Updates the property sheet panel with a changed property and also passed
   * the event along.
   *
   * @param evt a value of type 'PropertyChangeEvent'
   */
  public void propertyChange(PropertyChangeEvent evt){
    wasModified(evt); // Let our panel update before guys downstream
    support.firePropertyChange("", null, null);
  }

  /**
   * Adds a PropertyChangeListener.
   *
   * @param l a value of type 'PropertyChangeListener'
   */
  public void addPropertyChangeListener(PropertyChangeListener l){
    support.addPropertyChangeListener(l);
  }

  /**
   * Removes a PropertyChangeListener.
   *
   * @param l a value of type 'PropertyChangeListener'
   */
  public void removePropertyChangeListener(PropertyChangeListener l){
    support.removePropertyChangeListener(l);
  }

  /**
   * Sets a new target object for customisation.
   *
   * @param targ a value of type 'Object'
   */
  public synchronized void setTarget(Object targ){

    // used to offset the components for the properties of targ
    // if there happens to be globalInfo available in targ
    int componentOffset = 0;

    // Close any child windows at this point
    removeAll();

    GridBagLayout gbLayout = new GridBagLayout();

    setLayout(gbLayout);
    setVisible(false);
    m_NumEditable = 0;
    m_Target = targ;
    try{
      BeanInfo bi = Introspector.getBeanInfo(m_Target.getClass());
      m_Properties = bi.getPropertyDescriptors();
      m_Methods = bi.getMethodDescriptors();
    }
    catch (IntrospectionException ex){
      System.err.println("PropertySheet: Couldn't introspect");
      return;
    }

    JTextArea jt = new JTextArea(){
      /**
       * Return false, because we don't want this text area to receive the focus, and if this happens
       * it will "swallow" it (JDK bug?).
       *
       * The new JDK 1.4.x gives the setFocusable() method to do this; isFocusTraversable is deprecated there.
       *
       * The same effect (but with undesired visual collaterals) can be obtained by: jt.setEnabled(false);
       *
       * @return true if the focus is traversable
       */
      public boolean isFocusTraversable(){
        return false;
      }
    };

    m_HelpText = null;

// En el JDK 1.4, el Introspector no encuentra los métodos
// que existen en Booster (por ejemplo, sortProperties)
// desde AdaBoostECC (2 niveles por debajo en la jerarquía de clases),
// pero class.getMethod sí que lo encuentra
//    Class c = m_Target.getClass();
//    while(Object.class != c){
//      Method mola = null;
//        try{
//          mola = c.getMethod("sortProperties", new Class[]{PropertyDescriptor[].class});
//        } catch(NoSuchMethodException ex){
//          System.err.println("No encontrado en " +c.getName());
//        }
//      if(mola != null)
//        System.err.println(mola.getName() +" encontrado en " +c.getName());
//      c = c.getSuperclass();
//    }

    //Look for globalInfo & sortProperties methods
    for (int i = 0; i < m_Methods.length; i++){

      String name = m_Methods[i].getName();
      Method meth = m_Methods[i].getMethod();

      //If this method exists, it will define in which order and/or which
      //properties will be shown for editing. The order can't be defined by
      //a BeanInfo class, since no special order is defined
      //by the Java Beans specs; in fact, overriding the method
      //getPropertyDescriptors() in the target BeanInfo class
      //will only be used by the JDK classes when calling to the
      //Introspector.getBeanInfo() method to construct the index
      //(currently a HashMap)of the properties names over the properties descriptors,
      //and once this index is built, the array returned every time by
      //Introspector.getBeanInfo().getPropertyDescriptors() is constructed
      //using an Iterator over the index, whose order is undefined.
      if (name.equals("sortProperties") &&
        PropertyDescriptor[].class.equals(meth.getReturnType())
        && meth.getParameterTypes()[0] == PropertyDescriptor[].class){
        try{
          m_Properties = (PropertyDescriptor[]) meth.invoke(m_Target, new Object[]{m_Properties});
        }
        catch (Exception e){
          System.err.println(e.toString());
        }
      }

      // Look for a globalInfo method that returns a string
      // describing the target
      if (name.equals("globalInfo")){
        if (meth.getReturnType().equals(String.class)){
          try{
            Object args[] = {};
            String globalInfo = (String) (meth.invoke(m_Target, args));
            String summary = globalInfo;
            int ci = globalInfo.indexOf('.');
            if (ci != -1){
              summary = globalInfo.substring(0, ci + 1);
            }
            final String className = targ.getClass().getName();
            m_HelpText = new StringBuffer("NAME\n");
            m_HelpText.append(className).append("\n\n");
            m_HelpText.append("SYNOPSIS\n").append(globalInfo).append("\n\n");
            m_HelpBut = new JButton("More");
            m_HelpBut.setMnemonic('m');
            m_HelpBut.setToolTipText("More information about "
                                     + className);

            m_HelpBut.addActionListener(new ActionListener(){
              public void actionPerformed(ActionEvent a){
                openHelpFrame();
                m_HelpBut.setEnabled(false);
              }
            });

            jt.setFont(new Font("SansSerif", Font.PLAIN, 12));
            jt.setEditable(false);
            jt.setLineWrap(true);
            jt.setWrapStyleWord(true);
            jt.setText(summary);
            jt.setBackground(getBackground());
            JPanel jp = new JPanel();
            jp.setBorder(BorderFactory.createCompoundBorder(
              BorderFactory.createTitledBorder("About"),
              BorderFactory.createEmptyBorder(0, 5, 5, 5)
            ));
            jp.setLayout(new BorderLayout());
            jp.add(jt, BorderLayout.CENTER);
            JPanel p2 = new JPanel();
            p2.setLayout(new BorderLayout());
            p2.add(m_HelpBut, BorderLayout.NORTH);
            jp.add(p2, BorderLayout.EAST);
            GridBagConstraints gbConstraints = new GridBagConstraints();
            //	    gbConstraints.anchor = GridBagConstraints.EAST;
            gbConstraints.fill = GridBagConstraints.BOTH;
            //	    gbConstraints.gridy = 0;     gbConstraints.gridx = 0;
            gbConstraints.gridwidth = 2;
            gbConstraints.insets = new Insets(0, 5, 0, 5);
            gbLayout.setConstraints(jp, gbConstraints);
            add(jp);
            componentOffset = 1;
            break;
          }
          catch (Exception ex){
          }
        }
      }
    }

    m_Editors = new PropertyEditor[m_Properties.length];
    m_Values = new Object[m_Properties.length];
    m_Views = new JComponent[m_Properties.length];
    m_Labels = new JLabel[m_Properties.length];
    m_TipTexts = new String[m_Properties.length];
    boolean firstTip = true;
    for (int i = 0; i < m_Properties.length; i++){

      // Don't display hidden or expert properties.
      if (m_Properties[i].isHidden() || m_Properties[i].isExpert()){
        continue;
      }

      String name = m_Properties[i].getDisplayName();
      Class type = m_Properties[i].getPropertyType();
      Method getter = m_Properties[i].getReadMethod();
      Method setter = m_Properties[i].getWriteMethod();

      // Only display read/write properties.
      if (getter == null || setter == null){
        continue;
      }

      JComponent view;

      try{
        Object args[] = {};
        Object value = getter.invoke(m_Target, args);
        m_Values[i] = value;

        PropertyEditor editor = null;
        Class pec = m_Properties[i].getPropertyEditorClass();
        if (pec != null){
          try{
            editor = (PropertyEditor) pec.newInstance();
          }
          catch (Exception ex){
            System.err.println(ex.toString());
            // Drop through.
          }
        }
        if (editor == null){
          editor = PropertyEditorManager.findEditor(type);
        }
        m_Editors[i] = editor;

        // If we can't edit this component, skip it.
        if (editor == null){
          // If it's a user-defined property we give a warning.
          String getterClass = m_Properties[i].getReadMethod()
            .getDeclaringClass().getName();
          if (m_VerboseMode && getterClass.indexOf("java.") != 0){
            System.err.println("Warning: Can't find public property editor"
                               + " for property \"" + name + "\" (class \""
                               + type.getName() + "\").  Skipping.");
          }
          continue;
        }
        if (editor instanceof GenericObjectEditor){
          ((GenericObjectEditor) editor).setSpecializeFromClass(m_Target.getClass().getName());
          ((GenericObjectEditor) editor).setClassType(type);
        }
        else if (editor instanceof weka.gui.GenericObjectEditor){
          ((weka.gui.GenericObjectEditor) editor).setClassType(type);
        }

        // Don't try to set null values:
        if (value == null){
          // If it's a user-defined property we give a warning.
          String getterClass = m_Properties[i].getReadMethod()
            .getDeclaringClass().getName();
          if (m_VerboseMode && getterClass.indexOf("java.") != 0){
            System.err.println("Warning: Property \"" + name
                               + "\" has null initial value.  Skipping.");
          }
          continue;
        }

        editor.setValue(value);

        // now look for a TipText method for this property
        String tipName = name + "TipText";
        for (int j = 0; j < m_Methods.length; j++){
          String mname = m_Methods[j].getDisplayName();
          Method meth = m_Methods[j].getMethod();
          if (mname.equals(tipName)){
            if (meth.getReturnType().equals(String.class)){
              try{
                String tempTip = (String) (meth.invoke(m_Target, args));
                int ci = tempTip.indexOf('.');
                if (ci < 0){
                  m_TipTexts[i] = tempTip;
                }
                else{
                  m_TipTexts[i] = tempTip.substring(0, ci);
                }
                if (m_HelpText != null){
                  if (firstTip){
                    m_HelpText.append("OPTIONS\n");
                    firstTip = false;
                  }
                  m_HelpText.append(name).append(" -- ");
                  m_HelpText.append(tempTip).append("\n\n");
                  //jt.setText(m_HelpText.toString());
                }
              }
              catch (Exception ex){
              }
              break;
            }
          }
        }

        // Now figure out how to display it...
        if (editor.isPaintable() && editor.supportsCustomEditor()){
          view = new PropertyPanel(editor);
        }
        else if (editor.getTags() != null){
          view = new PropertyValueSelector(editor);
        }
        else if (editor.getAsText() != null){
          //String init = editor.getAsText();
          view = new PropertyText(editor);
        }
        else{
          if(m_VerboseMode)
            System.err.println("Warning: Property \"" + name
                               + "\" has non-displayabale editor.  Skipping.");
          continue;
        }

        editor.addPropertyChangeListener(this);

      }
      catch (InvocationTargetException ex){
        System.err.println("Skipping property " + name
                           + " ; exception on target: "
                           + ex.getTargetException());
        ex.getTargetException().printStackTrace();
        continue;
      }
      catch (Exception ex){
        System.err.println("Skipping property " + name
                           + " ; exception: " + ex);
        ex.printStackTrace();
        continue;
      }

      m_Labels[i] = new JLabel(name, SwingConstants.RIGHT);
      m_Labels[i].setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 5));
      m_Views[i] = view;
      GridBagConstraints gbConstraints = new GridBagConstraints();
      gbConstraints.anchor = GridBagConstraints.EAST;
      gbConstraints.fill = GridBagConstraints.HORIZONTAL;
      gbConstraints.gridy = i + componentOffset;
      gbConstraints.gridx = 0;
      gbLayout.setConstraints(m_Labels[i], gbConstraints);
      add(m_Labels[i]);
      JPanel newPanel = new JPanel();
      if (m_TipTexts[i] != null){
        m_Views[i].setToolTipText(m_TipTexts[i]);
      }
      newPanel.setBorder(BorderFactory.createEmptyBorder(10, 5, 0, 10));
      newPanel.setLayout(new BorderLayout());
      newPanel.add(m_Views[i], BorderLayout.CENTER);
      gbConstraints = new GridBagConstraints();
      gbConstraints.anchor = GridBagConstraints.WEST;
      gbConstraints.fill = GridBagConstraints.BOTH;
      gbConstraints.gridy = i + componentOffset;
      gbConstraints.gridx = 1;
      gbConstraints.weightx = 100;
      gbLayout.setConstraints(newPanel, gbConstraints);
      add(newPanel);
      m_NumEditable++;
    }
    if (m_NumEditable == 0){
      JLabel empty = new JLabel("No editable properties",
                                SwingConstants.CENTER);
      Dimension d = empty.getPreferredSize();
      empty.setPreferredSize(new Dimension(d.width * 2, d.height * 2));
      empty.setBorder(BorderFactory.createEmptyBorder(10, 5, 0, 10));
      GridBagConstraints gbConstraints = new GridBagConstraints();
      gbConstraints.anchor = GridBagConstraints.CENTER;
      gbConstraints.fill = GridBagConstraints.HORIZONTAL;
      gbConstraints.gridy = componentOffset;
      gbConstraints.gridx = 0;
      gbLayout.setConstraints(empty, gbConstraints);
      add(empty);
    }

    validate();
    setVisible(true);
  }

  protected void openHelpFrame(){

    JTextArea ta = new JTextArea();
    ta.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    ta.setLineWrap(true);
    ta.setWrapStyleWord(true);
    //ta.setBackground(getBackground());
    ta.setEditable(false);
    ta.setText(m_HelpText.toString());
    ta.setCaretPosition(0);
    final JDialog jf;
    if(getTopLevelAncestor() instanceof JDialog)
      jf = new JDialog((JDialog)getTopLevelAncestor());
    else
      jf = new JDialog();
    jf.setTitle("Information");
    jf.addWindowListener(new WindowAdapter(){
      public void windowClosing(WindowEvent e){
        jf.dispose();
        if (m_HelpFrame == jf){
          m_HelpBut.setEnabled(true);
        }
      }
    });
    jf.getContentPane().setLayout(new BorderLayout());
    jf.getContentPane().add(new JScrollPane(ta), BorderLayout.CENTER);
    jf.pack();
    jf.setSize(400, 350);
    jf.setLocation(getTopLevelAncestor().getLocationOnScreen().x
                   + getTopLevelAncestor().getSize().width,
                   getTopLevelAncestor().getLocationOnScreen().y);
    jf.setVisible(true);
    jf.setModal(false);
    m_HelpFrame = jf;
  }


  /**
   * Gets the number of editable properties for the current target.
   *
   * @return the number of editable properties.
   */
  public int editableProperties(){

    return m_NumEditable;
  }

  /**
   * Updates the propertysheet when a value has been changed (from outside
   * the propertysheet?).
   *
   * @param evt a value of type 'PropertyChangeEvent'
   */
  synchronized void wasModified(PropertyChangeEvent evt){

    //    System.err.println("wasModified");
    if (evt.getSource() instanceof PropertyEditor){
      PropertyEditor editor = (PropertyEditor) evt.getSource();
      for (int i = 0; i < m_Editors.length; i++){
        if (m_Editors[i] == editor){
          PropertyDescriptor property = m_Properties[i];
          Object value = editor.getValue();
          m_Values[i] = value;
          Method setter = property.getWriteMethod();
          try{
            Object args[] = {value};
            args[0] = value;
            setter.invoke(m_Target, args);
          }
          catch (InvocationTargetException ex){
            if (ex.getTargetException()
              instanceof PropertyVetoException){
              if(m_VerboseMode)
                System.err.println("WARNING: Vetoed; reason is: "
                                   + ex.getTargetException().getMessage());
            }
            else{
              System.err.println("InvocationTargetException while updating "
                                 + property.getName());
            }
          }
          catch (Exception ex){
            System.err.println("Unexpected exception while updating "
                               + property.getName());
          }
          if (m_Views[i] != null && m_Views[i] instanceof PropertyPanel){
            //System.err.println("Trying to repaint the property canvas");
            m_Views[i].repaint();
            revalidate();
          }
          break;
        }
      }
    }

    // Now re-read all the properties and update the editors
    // for any other properties that have changed.
    for (int i = 0; i < m_Properties.length; i++){
      Object o;
      try{
        Method getter = m_Properties[i].getReadMethod();
        Method setter = m_Properties[i].getWriteMethod();

        if (getter == null || setter == null){
          // ignore set/get only properties
          continue;
        }

        Object args[] = {};
        o = getter.invoke(m_Target, args);
      }
      catch (Exception ex){
        o = null;
      }
      if (o == m_Values[i] || (o != null && o.equals(m_Values[i]))){
        // The property is equal to its old value.
        continue;
      }
      m_Values[i] = o;
      // Make sure we have an editor for this property...
      if (m_Editors[i] == null){
        continue;
      }
      // The property has changed!  Update the editor.
      m_Editors[i].removePropertyChangeListener(this);
      m_Editors[i].setValue(o);
      m_Editors[i].addPropertyChangeListener(this);
      if (m_Views[i] != null){
        //System.err.println("Trying to repaint " + (i + 1));
        m_Views[i].repaint();
      }
    }

    // Make sure the target bean gets repainted.
    if (Beans.isInstanceOf(m_Target, Component.class)){
      ((Component) (Beans.getInstanceOf(m_Target, Component.class))).repaint();
    }
  }
}


