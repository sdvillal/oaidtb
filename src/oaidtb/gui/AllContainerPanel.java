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
 *    AllContainerPanel.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.gui;

import com.jrefinery.ui.RefineryUtilities;
import com.jrefinery.ui.about.AboutFrame;
import oaidtb.gui.customizedWeka.LogPanel;
import oaidtb.misc.guiUtils.SystemOutputStreamRedirector;
import oaidtb.misc.mediator.Arbitrable;
import oaidtb.misc.mediator.Mediator;
import weka.classifiers.Classifier;
import weka.core.Utils;
import weka.gui.WekaTaskMonitor;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;
import java.io.File;
import java.util.ArrayList;

/**
 * Clase que hace de contenedor principal para todos los componentes que forman la aplicación.
 *
 * Encapsula y centraliza mediante el interface Mediator las relaciones y la lógica de interacción
 * entre dichos componentes.
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.2 $
 */
public class AllContainerPanel implements Mediator{

  /** La base de datos de puntos compartida entre los diversos componentes */
  private final Point2DInstances m_Points = new Point2DInstances("BIGUIApp", 0);

  /** El panel de configuración del clasificador */
  private final ClassifierPanel m_ClassifierPanel = new ClassifierPanel(m_Points);
  /** El panel de represantación de instancias e hipótesis */
  private final DrawingPanel m_DrawingPanel = new DrawingPanel(m_Points);
  /** El panel que controla la inserción y borrado de las instancias */
  private final InstancesEditingPanel m_InstancesEditingPanel = new InstancesEditingPanel(m_DrawingPanel);
  /** El panel que controla las opciones de visualización y la creación de las imágenes de hipótesis */
  private final VisualOptionsPanel m_VisualOptionsPanel = new VisualOptionsPanel(m_DrawingPanel);
  /** El panel de log */
  private final LogPanel m_Log = new LogPanel(new WekaTaskMonitor());

  //Opciones globales
  /** Hacer o no log de las acciones ejecutadas en el panel del clasificador */
  private final JCheckBoxMenuItem m_LogClassifierPanel = new JCheckBoxMenuItem("Log classifier panel messages", true);
  /** Hacer o no log de las acciones ejecutadas en el panel de edición de instancias */
  private final JCheckBoxMenuItem m_LogInstancesEditingPanel = new JCheckBoxMenuItem("Log instances editing panel messages", true);
  /** Hacer o no log de las acciones ejecutadas en el panel de dibujado */
  private final JCheckBoxMenuItem m_LogDrawingPanel = new JCheckBoxMenuItem("Log drawing panel messages", false);
  /** Hacer o no log de las acciones ejecutadas en el panel de opciones visuales */
  private final JCheckBoxMenuItem m_LogVisualOptionsPanel = new JCheckBoxMenuItem("Log visual options panel messages", true);
  /** Capturar o no los mensajes mandados a System.err para mostrarlos en el panel de log */
  private final JCheckBoxMenuItem m_LogSystemErr = new JCheckBoxMenuItem("Log system err messages", true);
  /** A la vez que se mandan los mensajes de System.err al panel de log, mandarlos también o no al System.err "estándar" */
  private final JCheckBoxMenuItem m_SystemErrFork = new JCheckBoxMenuItem("Create a fork with default err", false);
  /** Crear de manera automática la imagen de hipótesis tras la construcción de un clasificador */
  private final JCheckBoxMenuItem m_AutoUpdateOnClassifierBuilt = new JCheckBoxMenuItem("Auto update when a classifier is built", true);
  /** Crear de manera automática la imagen de hipótesis tras la iteración de un booster */
  private final JCheckBoxMenuItem m_AutoUpdateOnBoosterIt = new JCheckBoxMenuItem("Auto update when a booster finish to iterate", true);

  /** La barra de herramientas */
  private final JToolBar m_ToolBar = new JToolBar();

  /** El thread que se encarga de redirigir los mensajes enviados a System.err al panel de log */
  private SystemOutputStreamRedirector m_ErrRedirector;

  /**
   *  Etiqueta en que se muestra las coordenadas en el dominio del problema del punto del panel de dibujado
   * sobre el que se encuentra el puntero del ratón; coordenada X
   */
  private final JTextArea m_MouseLocationLabelX = new JTextArea(1, 8);
  /**
   *  Etiqueta en que se muestra las coordenadas en el dominio del problema del punto del panel de dibujado
   * sobre el que se encuentra el puntero del ratón; coordenada Y
   */
  private final JTextArea m_MouseLocationLabelY = new JTextArea(1, 8);

  /** Frame interno en que se muestra el panel de configuración del clasificador */
  private final JInternalFrame m_ClassifierFrame = new JInternalFrame("Classifier", true, false, true, true);
  /** Frame interno en que se muestra el panel de opciones visuales */
  private final JInternalFrame m_VisualOptionsFrame = new JInternalFrame("Visual options", true, false, true, true);
  /** Frame interno en que se muestra el panel de edición de instancias */
  private final JInternalFrame m_InstanceEditingFrame = new JInternalFrame("Instance options", true, false, true, true);

  /** El frame "acerca de", con información relativa al proyecto, las licencias e información del sistema */
  private static AboutFrame m_AboutFrame;

  //Componentes de la toolbar
  /** Botón de acceso rápido para hacer zoom out */
  private final JButton m_ZoomOutButton = new JButton(new ImageIcon(getClass().getResource("_images_/expandall.gif")));
  /** Botón de acceso rápido para entrar en modo zoom */
  private final JCheckBox m_ZoomMode = new JCheckBox("Zoom mode");

  /** Icono del Profesor X, seña de identidad de la aplicación */
  //TODO: Una clase Resources para todas estas cosas + clase Locale para configurar el lenguaje etc.
  public final static ImageIcon PROFX_ICON = new ImageIcon(AllContainerPanel.class.getResource("_images_/profx.gif"));

  /** Constructor */
  public AllContainerPanel(){

    //Añadimos a todos los paneles el mediador
    m_ClassifierPanel.addMediator(this);
    m_InstancesEditingPanel.addMediator(this);
    m_DrawingPanel.addMediator(this);
    m_VisualOptionsPanel.addMediator(this);

    //Creamos los bordes
    m_VisualOptionsPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Visual options"),
                                                                      BorderFactory.createEmptyBorder(0, 5, 5, 5)));
    m_InstancesEditingPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Instance editing options"),
                                                                         BorderFactory.createEmptyBorder(0, 5, 5, 5)));
    m_ClassifierPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Classifier options"),
                                                                   BorderFactory.createEmptyBorder(0, 5, 5, 5)));
    m_DrawingPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED, Color.cyan, Color.green));

    //Configuramos el aspecto de los mensajes redirigidos desde System.err
    SimpleAttributeSet sas = new SimpleAttributeSet();
    StyleConstants.setForeground(sas, Color.blue);
    StyleConstants.setItalic(sas, true);
    StyleConstants.setBold(sas, true);
    //Configurar el thread que se encargará de la redirección */
    m_ErrRedirector = new SystemOutputStreamRedirector(m_Log.getLogText(), sas, true, m_SystemErrFork.isSelected());

    // Iniciar/parar la redirección de mensajes
    m_LogSystemErr.addItemListener(new ItemListener(){
      private Thread t;

      public void itemStateChanged(ItemEvent e){
        if (m_LogSystemErr.isSelected()){
          t = new Thread(m_ErrRedirector);
          t.setPriority(m_ErrRedirector.getPriority());
          t.setDaemon(m_ErrRedirector.isDaemon());
          t.start();
          m_SystemErrFork.setEnabled(true);
        }
        else{
          t.interrupt();
          m_SystemErrFork.setEnabled(false);
        }
      }
    });
    //Configuración inicial de la redirección
    if (m_LogSystemErr.isSelected())
      m_LogSystemErr.setSelected(true);

    //Configurar el fork entre System.err y el panel de log
    m_SystemErrFork.addItemListener(new ItemListener(){
      public void itemStateChanged(ItemEvent e){
        m_ErrRedirector.setFork(m_SystemErrFork.isSelected());
      }
    });
    //Configuración inicial de la redirección
    m_ErrRedirector.setFork(m_SystemErrFork.isSelected());

    //Configuración de las etiquetas que muestran las coordenadas reales
    //que representa el punto del panel de bibujado sobre el que se encuentra
    //el puntero del ratón
    m_MouseLocationLabelX.setEditable(false);
    m_MouseLocationLabelX.setForeground(Color.blue);
    m_MouseLocationLabelX.setBackground(Color.orange);
    m_MouseLocationLabelY.setEditable(false);
    m_MouseLocationLabelY.setBackground(Color.orange);
    m_MouseLocationLabelY.setForeground(Color.blue);
    m_DrawingPanel.addMouseMotionListener(new MouseMotionAdapter(){
      public void mouseMoved(MouseEvent e){
        if (!m_DrawingPanel.isZoomedIn()){
          m_MouseLocationLabelX.setText("x= " + e.getX());
          m_MouseLocationLabelY.setText("y= " + e.getY());
        }
        else{
          //Obtenemos las coordenadas "reales" del pubto en el dominio del problema
          m_MouseLocationLabelX.setText("x= " + Utils.doubleToString(m_DrawingPanel.getXFromPanel(e.getX()), 4, 4));
          m_MouseLocationLabelY.setText("y= " + Utils.doubleToString(m_DrawingPanel.getYFromPanel(e.getY()), 4, 4));
        }
      }

      public void mouseDragged(MouseEvent e){
        mouseMoved(e);
      }
    });

    //Añadimos las etiquetas de coordenadas a la parte izquerda de la barra de estado en el panel de log
    m_MouseLocationLabelX.setText("x=");
    m_MouseLocationLabelY.setText("y=");
    JPanel locPanel = new JPanel(new GridLayout(2, 1));
    locPanel.add(m_MouseLocationLabelX);
    locPanel.add(m_MouseLocationLabelY);
    locPanel.validate();
    locPanel.setBorder(BorderFactory.createLineBorder(Color.green));
    m_Log.addCustomPanel(locPanel);
  }

  /**
   * Crea un JMenu con todos los items que sirven para configurar las opciones globales
   *
   * @return Un JMenu con todos los items que sirven para configurar las opciones globales
   */
  public JMenu createGeneralOptionsMenu(){
    //El menú
    JMenu tmp = new JMenu("Global Options");

    //Añadimos las opciones de log
    tmp.add(m_LogInstancesEditingPanel);
    tmp.add(m_LogClassifierPanel);
    tmp.add(m_LogVisualOptionsPanel);
    tmp.add(m_LogDrawingPanel);
    m_LogInstancesEditingPanel.setToolTipText("Hacer log de los mensajes del panel de edición de instancias");
    m_LogClassifierPanel.setToolTipText("Hacer log de los mensajes del panel de configuración del clasificador");
    m_LogVisualOptionsPanel.setToolTipText("Hacer log de los mensajes del panel de opciones visuales");
    m_LogDrawingPanel.setToolTipText("Hacer log de los mensajes del panel de dibujado");

    //Añadimos un separador
    tmp.addSeparator();

    //Añadimos las opciones de redirección de System.err
    tmp.add(m_LogSystemErr);
    m_LogSystemErr.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.ALT_MASK));
    tmp.add(m_SystemErrFork);
    m_LogSystemErr.setToolTipText("Mandar los mensajes de System.err al panel de log");
    m_SystemErrFork.setToolTipText("Mandar también los mensajes al stream al que apunta System.err por defecto");

    //Situación inicial de la chackbox, coherente con la configuración
    m_SystemErrFork.setEnabled(m_LogSystemErr.isSelected());

    //Añadimos un separador
    tmp.addSeparator();

    //Añadimos las opciones de creación autmática de las imágenes de hipótesis
    tmp.add(m_AutoUpdateOnClassifierBuilt);
    m_AutoUpdateOnClassifierBuilt.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, KeyEvent.ALT_MASK));
    tmp.add(m_AutoUpdateOnBoosterIt);
    m_AutoUpdateOnBoosterIt.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, KeyEvent.CTRL_MASK));
    tmp.setMnemonic('o');
    m_AutoUpdateOnClassifierBuilt.setToolTipText("Crear automáticamente la imagen de hipótesis tras construir un clasificador");
    m_AutoUpdateOnBoosterIt.setToolTipText("Crear automáticamente la imagen de hipótesis tras iterar un booster");

    return tmp;
  }

  /**
   * Crea la barra de herramientas que contendrá componentes de acceso rápido a las acciones
   * más comunes para no tener que estar habriendo y cerrando los paneles
   * de configuración
   *
   *TODO: Completarla con otras acciones importantes (crear imagen, resetear el booster, partir las instancias, undo etc.
   */
  private void createToolBar(){

    //Configuración de la barra de herramientas
    m_ToolBar.setFloatable(false); //Tiene muy mal sopote por parte de sun
    m_ToolBar.setBorderPainted(true);
    m_ToolBar.setBackground(Color.black);

    //Tal y como está ahora no queda mal, pero en el momento en que se añadan más opciones
    //se deberá usar GridBagLayout
    m_ToolBar.setLayout(new GridLayout());

    //Acceso rápido a la acción de entrar o salir del modo zoom
    //TODO: No acceder directamente al componente del panel, sino hacerlo (centralizando la habilitación / deshabilitación)
    //      con una Action
    m_ZoomMode.addItemListener(new ItemListener(){
      public void itemStateChanged(ItemEvent e){
        m_InstancesEditingPanel.m_ZoomModeCB.setSelected(m_ZoomMode.isSelected());
      }
    });
    m_InstancesEditingPanel.m_ZoomModeCB.addItemListener(new ItemListener(){
      public void itemStateChanged(ItemEvent e){
        if (m_InstancesEditingPanel.m_ZoomModeCB.isSelected() != m_ZoomMode.isSelected())
          m_ZoomMode.setSelected(m_InstancesEditingPanel.m_ZoomModeCB.isSelected());
      }
    });
    m_ZoomMode.setMnemonic('z');
    m_ZoomMode.setToolTipText("Entrar / Salir del modo zoom" + " (Alt-Z)");
    m_ToolBar.add(m_ZoomMode);

    //Acceso rápido a la acción de hacer zoom out
    //TODO: No acceder directamente al componente del panel, sino hacerlo (centralizando la habilitación / deshabilitación)
    //      con una Action
    m_ZoomOutButton.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e){
        m_InstancesEditingPanel.m_ZoomOutButton.doClick();
      }
    });
    m_ZoomOutButton.setEnabled(false);
    m_ZoomOutButton.setMnemonic('x');
    m_ZoomOutButton.setToolTipText("Volver a ver todo el dominio del problema" + " (Alt-X)");
    m_ToolBar.add(m_ZoomOutButton);

    //Acceso rápido a la acción de configurar el color asignado al botón izquierdo del ratón
    //TODO: No acceder directamente al componente del panel, sino hacerlo (centralizando la habilitación / deshabilitación)
    //      con una Action
    final JButton color1Button = new JButton();
    color1Button.setText(m_InstancesEditingPanel.m_Color1Button.getText());
    color1Button.setBackground(m_InstancesEditingPanel.m_Color1Button.getBackground());
    color1Button.setMnemonic(KeyEvent.VK_1);
    color1Button.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e){
        m_InstancesEditingPanel.m_Color1Button.doClick();
      }
    });
    //Sincronizar las etiquetas que indican el número de instancias de ese color
    m_InstancesEditingPanel.m_Color1Button.addPropertyChangeListener(new PropertyChangeListener(){
      public void propertyChange(PropertyChangeEvent evt){
        color1Button.setText(m_InstancesEditingPanel.m_Color1Button.getText());
        color1Button.setBackground(m_InstancesEditingPanel.m_Color1Button.getBackground());
      }
    });
    color1Button.setToolTipText("Seleccionar el color asociado a los clicks del botón izquierdo del ratón" + " (Alt-1)");
    m_ToolBar.add(color1Button);

    //Acceso rápido a la acción de configurar el color asignado al botón derecho del ratón
    //TODO: No acceder directamente al componente del panel, sino hacerlo (centralizando la habilitación / deshabilitación)
    //      con una Action
    final JButton color2Button = new JButton();
    color2Button.setText(m_InstancesEditingPanel.m_Color1Button.getText());
    color2Button.setBackground(m_InstancesEditingPanel.m_Color1Button.getBackground());
    color2Button.setMnemonic(KeyEvent.VK_2);
    color2Button.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e){
        m_InstancesEditingPanel.m_Color2Button.doClick();
      }
    });
    //Sincronizar las etiquetas que indican el número de instancias de ese color
    m_InstancesEditingPanel.m_Color2Button.addPropertyChangeListener(new PropertyChangeListener(){
      public void propertyChange(PropertyChangeEvent evt){
        color2Button.setText(m_InstancesEditingPanel.m_Color2Button.getText());
        color2Button.setBackground(m_InstancesEditingPanel.m_Color2Button.getBackground());
      }
    });
    color2Button.setToolTipText("Seleccionar el color asociado a los clicks del botón derecho del ratón" + " (Alt-2)");
    m_ToolBar.add(color2Button);

    //Botón para seleccionar un clasificador
    JButton selectClassifierButton = new JButton(PROFX_ICON);
    selectClassifierButton.setMnemonic(KeyEvent.VK_SPACE);
    selectClassifierButton.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e){
        Point p = m_ToolBar.getLocationOnScreen();
        m_ClassifierPanel.getClassifierSelector().showPropertyDialog(p.x, p.y);
      }
    });
    selectClassifierButton.setToolTipText("Seleccionar el clasificador" + " (Alt-Espacio)");
    m_ToolBar.add(selectClassifierButton);

    //Textfield para editar el número de iteraciones a realizar
    JTextField boosterIterations = new JTextField(6);
    boosterIterations.setHorizontalAlignment(JTextField.RIGHT);
    //Sincronizamos con el del panel
    boosterIterations.setDocument(m_ClassifierPanel.getBoosterIterationsDocument());
    m_ClassifierPanel.getBoosterIterateAction().addStateDependantComponent(boosterIterations);
    boosterIterations.setEnabled(m_ClassifierPanel.getBoosterIterateAction().isEnabled());
    boosterIterations.setToolTipText("Número de iteraciones a realizar");
    m_ToolBar.add(boosterIterations);

    //Botón de construir un clasificador / iterar un booster
    JButton classifyButton = new JButton(new ImageIcon(AllContainerPanel.class.getResource("_images_/resume.gif")));
    classifyButton.setMnemonic(KeyEvent.VK_ENTER);
    classifyButton.addActionListener(m_ClassifierPanel.getBuildOrIterateAction());
    classifyButton.setToolTipText("Clasificar / Iterar" + " (Alt-Intro)");
    m_ToolBar.add(classifyButton);

    ////////
    /// Mostrar o no los paneles de la aplicación
    ////////

    /**
     * Listener que minimiza / restaura alternativamente un {@link JInternalFrame}
     */
    class ShowAndHideFrame implements ActionListener{

      /** El frame a minimizar / restaurar cuando la acción es invocada */
      final JInternalFrame frame;

      /**
       * Constructor
       *
       * @param frame El frame a minimizar / restaurar cuando la acción es invocada
       */
      public ShowAndHideFrame(JInternalFrame frame){
        this.frame = frame;
      }

      /**
       * Minimiza o restaura el frame
       *
       * @param e El evento que ha provocado esta llamada a la acción
       */
      public void actionPerformed(ActionEvent e){
        try{
          frame.setIcon(!frame.isIcon());
        }
        catch (PropertyVetoException ex){
          ex.printStackTrace();
        }
      }
    }

    //Botón de mostrar / ocultar el frame de edición de instancias
    JButton showAndHideIEFrame = new JButton("I.E.");
    showAndHideIEFrame.addActionListener(new ShowAndHideFrame(m_InstanceEditingFrame));
    showAndHideIEFrame.setMnemonic(KeyEvent.VK_LEFT);
    showAndHideIEFrame.setToolTipText("Mostrar / Ocultar la ventana de edición de instancias" + " (Alt-Izquierda)");
    m_ToolBar.add(showAndHideIEFrame);

    //Botón de mostrar / ocultar el frame de configuración del clasificador
    JButton showAndHideClassifierFrame = new JButton("C.P.");
    showAndHideClassifierFrame.addActionListener(new ShowAndHideFrame(m_ClassifierFrame));
    showAndHideClassifierFrame.setMnemonic(KeyEvent.VK_DOWN);
    showAndHideClassifierFrame.setToolTipText("Mostrar / Ocultar la ventana de utilidades del clasificador" + " (Alt-Abajo)");
    m_ToolBar.add(showAndHideClassifierFrame);

    //Botón de mostrar / ocultar el frame de opciones visuales
    JButton showAndHideVOFrame = new JButton("V.O.");
    showAndHideVOFrame.addActionListener(new ShowAndHideFrame(m_VisualOptionsFrame));
    showAndHideVOFrame.setMnemonic(KeyEvent.VK_RIGHT);
    showAndHideVOFrame.setToolTipText("Mostrar / Ocultar la ventana de opciones visuales" + " (Alt-Derecha)");
    m_ToolBar.add(showAndHideVOFrame);
  }//FIN de createToolBar()

  /**
   * Crea el panel en el que incluiremos todos los componentes que conforman la aplicación
   *
   * @return El panel que aglutina toda la aplicación, listo para ser añadido a un contenedor de nivel superior
   */
  public JDesktopPane createDesktopPane(){

    //Creamos la barra de herramientas
    createToolBar();

    //El panel con capas al que vamos a añadir todos los componentes
    final JDesktopPane desktopPane = new JDesktopPane();

    ///////
    /// Creamos los diversos frames y los colocamos en la capa de paleta del desktopPane
    ///////

    //El frame de edición de instancias
    m_InstanceEditingFrame.getContentPane().add(m_InstancesEditingPanel, BorderLayout.CENTER);
    m_InstanceEditingFrame.setLocation(0, 30);
    m_InstancesEditingPanel.validate();
    m_InstanceEditingFrame.setSize((int) m_InstancesEditingPanel.getPreferredSize().getWidth(),
                                   (int) m_InstancesEditingPanel.getPreferredSize().getHeight() + 20);
    m_InstanceEditingFrame.setVisible(true);
    desktopPane.add(m_InstanceEditingFrame, JDesktopPane.PALETTE_LAYER);

    //El frame de configuración del clasificador
    m_ClassifierFrame.getContentPane().add(m_ClassifierPanel, BorderLayout.CENTER);
    m_ClassifierPanel.validate();
    Dimension dim = m_ClassifierPanel.getPreferredSize();
    m_ClassifierFrame.setSize((int) dim.getWidth() + 20, (int) dim.getHeight() + 20);
    m_ClassifierFrame.setLocation(200, 30);
    m_ClassifierFrame.setVisible(true);
    desktopPane.add(m_ClassifierFrame, JDesktopPane.PALETTE_LAYER);
//    BasicInternalFrameUI ui = (BasicInternalFrameUI) classifierFrame.getUI();
//    ui.getNorthPane().setPreferredSize(new Dimension(0,0));

    //El frame de edición de instancias
    m_VisualOptionsFrame.getContentPane().add(m_VisualOptionsPanel, BorderLayout.CENTER);
    m_VisualOptionsPanel.validate();
    m_VisualOptionsFrame.setSize((int) m_VisualOptionsPanel.getPreferredSize().getWidth() + 20,
                                 (int) m_VisualOptionsPanel.getPreferredSize().getHeight() + 20);
    m_VisualOptionsFrame.setLocation(380, 30);
    m_VisualOptionsFrame.setVisible(true);
    desktopPane.add(m_VisualOptionsFrame, JDesktopPane.PALETTE_LAYER);

    /**
     * Listener que oculta un JInternalFrame cada vez que éste es minimizado
     * TODO: Crear un listener que produzca un comportamiento similar a las paletas
     *       de herramientas del PaintShopPro (mostrar cuando se pasa el ratón por
     *       encima del icono etc.)
     */
    InternalFrameListener whenIconizeHideListener = new InternalFrameAdapter(){
      public void internalFrameIconified(InternalFrameEvent e){
        e.getInternalFrame().getDesktopIcon().setVisible(false);
      }
    };

    //Añadimos el listener a los frames
    m_InstanceEditingFrame.addInternalFrameListener(whenIconizeHideListener);
    m_ClassifierFrame.addInternalFrameListener(whenIconizeHideListener);
    m_VisualOptionsFrame.addInternalFrameListener(whenIconizeHideListener);

    //Configuramos el "panel base" que se muestra por debajo de los frames, y lo añadimos a la capa por defecto
    final JPanel basePanel = new JPanel(new BorderLayout());
    //La barra de herramientas al note
    basePanel.add(m_ToolBar, BorderLayout.NORTH);
    //El m_BasePanel de dibujado en el centro
    basePanel.add(m_DrawingPanel, BorderLayout.CENTER);
    //El m_BasePanel de log al sur
    basePanel.add(m_Log, BorderLayout.SOUTH);
    //Lo añadimos a la capa por defecto del desktopPane
    desktopPane.add(basePanel, JDesktopPane.DEFAULT_LAYER);

    //Como los JDektopPane carecen de gestor de distribución, hacemos
    //que el panel base siempre ocupe todo el tamaño disponible
    //en la capa DefaultLayer del desktopPane
    desktopPane.addComponentListener(new ComponentAdapter(){
      public void componentResized(ComponentEvent e){
        basePanel.setBounds(0, 0, desktopPane.getWidth(), desktopPane.getHeight());
        desktopPane.validate();
      }
    });

    ////////
    /// Configuramos el mapa del teclado con las teclas-->acciones definidas por cada panel para que sean
    ///  accesibles desde toda la aplicación
    ///  ¡OJO con los duplicados!
    ///////

    //Mapa de nombreAcción-->acción
    oaidtb.misc.Utils.mergeActionMaps(desktopPane.getActionMap(), m_InstancesEditingPanel.getActionMap());
    oaidtb.misc.Utils.mergeActionMaps(desktopPane.getActionMap(), m_VisualOptionsPanel.getActionMap());
    oaidtb.misc.Utils.mergeActionMaps(desktopPane.getActionMap(), m_ClassifierPanel.getActionMap());

    //Mapa de tecla-->nombreAcción
    oaidtb.misc.Utils.mergeInputMaps(desktopPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW),
                                     m_InstancesEditingPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW));
    oaidtb.misc.Utils.mergeInputMaps(desktopPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW),
                                     m_VisualOptionsPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW));
    oaidtb.misc.Utils.mergeInputMaps(desktopPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW),
                                     m_ClassifierPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW));

    return desktopPane;
  }

  /**
   * Implementación del interfaz mediator, este método controla la acción
   * a realizar para cada uno de los mensajes definidos.
   *
   * @param colleague La identidad del panel que ha generado el mensaje
   *        (de dibujado, de opciones visuales, de configuración del clasificador o de edición de instancias)
   * @param changeIndicator Constante que indica el tipo de cambio
   * @param object Objeto que puede ser utilizado para realizar las acciones pertinentes
   */
  public void colleagueChanged(Arbitrable colleague, int changeIndicator, Object object){

    if (colleague == m_ClassifierPanel)
      classifierPanelChanged(changeIndicator, object);

    else if (colleague == m_DrawingPanel)
      drawingPanelChanged(changeIndicator, object);

    else if (colleague == m_InstancesEditingPanel)
      instancesEditingPanelChanged(changeIndicator, object);

    else if (colleague == m_VisualOptionsPanel)
      visualOptionsPanelChanged(changeIndicator, object);
  }

  /**
   * Control de los mensajes recibidos desde un {@link VisualOptionsPanel}
   *
   * @param changeIndicator El indicador de cambio
   * @param object El objeto definido para dicho indicador de cambio
   */
  private void visualOptionsPanelChanged(int changeIndicator, Object object){
    switch (changeIndicator){
      case VisualOptionsPanel.HYPOTHESIS_IMAGE_START:
        if (m_LogVisualOptionsPanel.isSelected()){
          m_Log.logMessage("Creando la imagen de hipótesis de un " + object.getClass().getName());
          m_Log.statusMessage("Clasificando");
        }
        m_Log.taskStarted();
        break;
      case VisualOptionsPanel.HYPOTHESIS_IMAGE_FINISH:
        if (m_LogVisualOptionsPanel.isSelected()){
          m_Log.logMessage("Creada la imagen de hipótesis de un " + object.getClass().getName());
          m_Log.statusMessage("OK");
        }
        m_Log.taskFinished();
        break;
      case VisualOptionsPanel.HYPOTHESIS_IMAGE_CANCELED:
        if (m_LogVisualOptionsPanel.isSelected()){
          m_Log.logMessage("Cancelada la creación de la imagen de hipótesis de un " + object.getClass().getName());
          m_Log.statusMessage("OK");
        }
        m_Log.taskFinished();
        break;
      default :
    }
  }

  /**
   * Control de los mensajes recibidos desde un {@link InstancesEditingPanel}
   *
   * @param changeIndicator El indicador de cambio
   * @param object El objeto definido para dicho indicador de cambio
   */
  private void instancesEditingPanelChanged(int changeIndicator, Object object){
    switch (changeIndicator){
      case InstancesEditingPanel.INSTANCES_DELETED:
        //Redibujamos todo
        m_DrawingPanel.setUpdateInstances(true);
        m_DrawingPanel.setUpdateGrid(true);
        m_DrawingPanel.repaint();
        //Reseteamos el clasificador
        m_ClassifierPanel.getResetAction().actionPerformed(null);
        //Sincronizamos el clasificador del panel de opciones visuales con este
        m_VisualOptionsPanel.setClassifier(m_ClassifierPanel.getClassifier(), m_ClassifierPanel.getTrainData(), m_ClassifierPanel.getColors(), true);
        //Ya no es válida la información de zoom out
        if (m_DrawingPanel.isZoomedIn())
          resetNaturalScale();
        if (m_LogInstancesEditingPanel.isSelected())
          m_Log.logMessage("Todas las instancias han sido purgadas");
        break;
      case InstancesEditingPanel.INSTANCES_LOAD_START:
        //Deshabilitar las acciones pertinentes
        m_ZoomMode.setEnabled(false);
        m_ZoomOutButton.setEnabled(false);
        m_ClassifierPanel.setEnableClassifyActions(false);
        //Siempre notificamos la acción
        if (object instanceof File)
          m_Log.logMessage("Cargando las instancias de: " + ((File) object).getName());
        else
          m_Log.logMessage("Cargando las instancias de: " + (String) object);
        m_Log.statusMessage("Cargando");
        m_Log.taskStarted();
        break;
      case InstancesEditingPanel.INSTANCES_LOAD_FINISH:
        //Habilitamos las acciones pertinentes
        m_ZoomMode.setEnabled(true);
        m_ZoomOutButton.setEnabled(m_DrawingPanel.isZoomedIn());
        m_ClassifierPanel.setEnableClassifyActions(true);
        //Reseteamos el clasificador
        m_ClassifierPanel.getResetAction().actionPerformed(null);
        //Sincronizamos el clasificador del panel de opciones visuales con este
        m_VisualOptionsPanel.setClassifier(m_ClassifierPanel.getClassifier(),
                                           m_ClassifierPanel.getTrainData(),
                                           m_ClassifierPanel.getColors(),
                                           true);
        //Redibujamos todo
        m_DrawingPanel.setUpdateInstances(true);
        m_DrawingPanel.setUpdateGrid(true);
        m_DrawingPanel.repaint();
        //Ya no es válida la información de zoom out
        if (m_DrawingPanel.isZoomedIn())
          resetNaturalScale();
        //Siempre notificamos la acción
        if (object instanceof File)
          m_Log.logMessage("Finalizada la carga de: " + ((File) object).getName());
        else
          m_Log.logMessage("Finalizada la carga de: " + ((String) object));
        m_Log.taskFinished();
        m_Log.statusMessage("OK");
        break;
      case InstancesEditingPanel.INSTANCES_LOAD_FAIL:
        //Habilitamos las acciones pertinentes
        m_ZoomMode.setEnabled(true);
        m_ZoomOutButton.setEnabled(m_DrawingPanel.isZoomedIn());
        m_ClassifierPanel.setEnableClassifyActions(true);
        //Siempre notificamos la acción
        if (object instanceof File)
          m_Log.logMessage("Fallo en la carga de: " + ((File) object).getName());
        else
          m_Log.logMessage("Fallo en la carga de: " + ((String) object));
        m_Log.taskFinished();
        m_Log.statusMessage("OK");
        break;
      case InstancesEditingPanel.INSTANCES_SAVE_START:
        //Deshabilitamos las acciones pertinentes
        m_ZoomMode.setEnabled(false);
        m_ZoomOutButton.setEnabled(false);
        //Siempre notificamos la acción
        m_Log.logMessage("Salvando las instancias en: " + ((File) object).getName());
        m_Log.statusMessage("Salvando");
        m_Log.taskStarted();
        break;
      case InstancesEditingPanel.INSTANCES_SAVE_FINISH:
        //Habilitamos las acciones pertinentes
        m_ZoomMode.setEnabled(true);
        m_ZoomOutButton.setEnabled(m_DrawingPanel.isZoomedIn());
        //Siempre notificamos la acción
        m_Log.logMessage("Instancias salvadas en: " + ((File) object).getName());
        m_Log.taskFinished();
        m_Log.statusMessage("OK");
        break;
      default :
    }
  }

  /**
   * Control de los mensajes recibidos desde un {@link ClassifierPanel}
   *
   * @param changeIndicator El indicador de cambio
   * @param object El objeto definido para dicho indicador de cambio
   */
  private void classifierPanelChanged(int changeIndicator, Object object){
    switch (changeIndicator){
      case ClassifierPanel.CLASSIFIER_CHANGED:
        if (m_LogClassifierPanel.isSelected())
          m_Log.logMessage("Seleccionado un classificador " + object.getClass().getName());
        //El clasificador no está construido, no lo pasamos aún
        //m_VisualOptionsPanel.setClassifier((Classifier) object, null, null, false);
        break;
      case ClassifierPanel.CLASSIFIER_BUILD_START:
        if (m_LogClassifierPanel.isSelected()){
          m_Log.logMessage("Inializada la construcción de un " + object.getClass().getName());
          m_Log.statusMessage("Construyendo un clasificador");
        }
        m_Log.taskStarted();
        break;
      case ClassifierPanel.CLASSIFIER_BUILD_FINISH:
        if (m_LogClassifierPanel.isSelected()){
          m_Log.logMessage("Finalizada la construcción de un " + object.getClass().getName());
          m_Log.statusMessage("OK");
        }
        m_Log.taskFinished();
        //Sincronizamos el clasificador del panel de opciones visuales con este
        m_VisualOptionsPanel.setClassifier((Classifier) object,
                                           m_ClassifierPanel.getTrainData(),
                                           m_ClassifierPanel.getColors(),
                                           true);
        //Ya no es válida la información de zoom out
        if (m_DrawingPanel.isZoomedIn())
          resetNaturalScale();
        //Reconstruir automáticamente, si procede, la imagen de hipótesis
        if (m_AutoUpdateOnClassifierBuilt.isSelected() && m_VisualOptionsPanel.getRefreshAction().isEnabled())
          m_VisualOptionsPanel.getRefreshAction().actionPerformed(null);
        break;
      case ClassifierPanel.CLASSIFIER_BUILD_CANCELED:
        if (m_LogClassifierPanel.isSelected()){
          m_Log.logMessage("Cancelada la construcción de un " + object.getClass().getName());
          m_Log.statusMessage("OK");
        }
        m_Log.taskFinished();
        break;
      case ClassifierPanel.BOOSTER_FIRST_ITERATION:
        //Resetear el panel de opciones visuales
        m_VisualOptionsPanel.reset();
        //Sincronizamos el clasificador del panel de opciones visuales con este
        m_VisualOptionsPanel.setClassifier((Classifier) object,
                                           m_ClassifierPanel.getTrainData(),
                                           m_ClassifierPanel.getColors(),
                                           false);
        break;
      case ClassifierPanel.BOOSTER_ITERATE_START:
        if (m_LogClassifierPanel.isSelected()){
          m_Log.logMessage("Iniciadas las siguientes iteraciones de un " + object.getClass().getName());
          m_Log.statusMessage("Un booster está iterando");
        }
        m_Log.taskStarted();
        break;
      case ClassifierPanel.BOOSTER_ITERATE_FINISHED:
        if (m_LogClassifierPanel.isSelected()){
          m_Log.logMessage("Terminadas las siguientes iteraciones de un " + object.getClass().getName());
          m_Log.statusMessage("OK");
        }
        m_Log.taskFinished();
        //Hemos realizado más iteraciones
        m_VisualOptionsPanel.newIterationsMade();
        //Ya no es válida la información de zoom out
        if (m_DrawingPanel.isZoomedIn())
          resetNaturalScale();
        //Reconstruir automáticamente, si procede, la imagen de hipótesis
        if (m_AutoUpdateOnBoosterIt.isSelected())
          m_VisualOptionsPanel.getRefreshAction().actionPerformed(null);
        break;
      case ClassifierPanel.BOOSTER_ITERATE_CANCELED:
        if (m_LogClassifierPanel.isSelected()){
          m_Log.logMessage("Canceladas las siguientes iteraciones de un " + object.getClass().getName());
          m_Log.statusMessage("OK");
        }
        m_Log.taskFinished();
        break;
      default :
    }
  }

  /**
   * Control de los mensajes recibidos desde un {@link DrawingPanel}
   *
   * @param changeIndicator El indicador de cambio
   * @param object El objeto definido para dicho indicador de cambio
   */
  private void drawingPanelChanged(int changeIndicator, Object object){
    switch (changeIndicator){
      case DrawingPanel.INSTANCES_REPAINT_BEGIN:
        if (m_LogDrawingPanel.isSelected()){
          m_Log.logMessage("Repintando las instancias");
          m_Log.statusMessage("Repintando las instancias");
          m_Log.taskStarted();
        }
        break;
      case DrawingPanel.INSTANCES_REPAINT_FINISH:
        if (m_LogDrawingPanel.isSelected()){
          m_Log.logMessage("Finalizado el repintado de las instancias");
          m_Log.statusMessage("OK");
          m_Log.taskFinished();
        }
        break;
      case DrawingPanel.REGION_SELECTED:
        //Hacemos zoom de manera automática
        Rectangle2D.Double r = (Rectangle2D.Double) object;
        doZoomIn(r);
        break;
      case DrawingPanel.ZOOM_IN:
        //Habilitamos las acciones pertinentes (usar un Action en vez de esto)
        m_ZoomOutButton.setEnabled(true);
        m_InstancesEditingPanel.m_ZoomOutButton.setEnabled(true);
        //Siempre informamos sobre la operación
        r = (Rectangle2D.Double) object;
        m_Log.logMessage("Zoom hecho sobre: x=" + r.x + " y=" + r.y + " width=" + r.width + " height=" + r.height);
        break;
      case DrawingPanel.ZOOM_OUT:
        //Deshabilitamos las acciones pertinentes
        m_ZoomOutButton.setEnabled(false);
        m_InstancesEditingPanel.m_ZoomOutButton.setEnabled(false);
        //Volvemos a ver todo a escala natural
        doZoomOut();
        m_Log.logMessage("Zoom out, vuelta al principio.");
        break;
      default :
    }
  }

  //////////////////////
  //// Métodos propios de las operaciones de zoom
  /////////////////////

  /**
   * Clase que sirve para llevar el historial de zoom; como consume
   * mucha memoria debido a que, en el peor de los casos,
   * tiene que almacenar cada vez 2 imágenes, he decidido limitar el historial de zoom a la
   * posibilidad de volver sólo a volver a ver todo a escala natural. Otra opción
   * más rápida y menos costosa en términos de memoria es hacer zoom símplemente
   * sobre lo que se ve en ese momento.
   */
  private static class ZoomHistory{

    BufferedImage m_HypothesisSumImage;
    BufferedImage m_HypothesisImage;
    Rectangle m_GridDimensions;
    Rectangle2D.Double m_Region;
  }

  /** Para almacenar el historial de zoom (por ahora, sólo la información de la vista a escala natural
   *  ZoomHistory
   */
  private ArrayList m_ZoomHistory = new ArrayList();

  /**
   * Hace zoom sobre la región especificada. Coordina todos los paneles para que la información sea
   * coherente y hace una copia de seguridad en caso de que sea el primer zoom que hacemos desde
   * la escala natural.
   *
   * @param r La región sobre la que hacer zoom, expresada en coordenadas reales del dominio del problema
   */
  private void doZoomIn(final Rectangle2D.Double r){

    // Para guardar las coordenadas naturales (panatalla)  que corresponden a las coordenadas
    // reales en el dominio del problema pasadas por parámetro
    final Rectangle2D.Double r2;

    if (!m_DrawingPanel.isZoomedIn()){
      //Guardamos la información para poder volver a restaurar
      ZoomHistory naturalScale = new ZoomHistory();
      naturalScale.m_HypothesisSumImage = m_VisualOptionsPanel.getHypothesisSumImage();
      naturalScale.m_HypothesisImage = m_VisualOptionsPanel.getHypothesisImage();
      naturalScale.m_GridDimensions = new Rectangle(m_DrawingPanel.getGridColumnWidth(),
                                                    m_DrawingPanel.getGridRowHeight(),
                                                    m_DrawingPanel.getGridWidth(),
                                                    m_DrawingPanel.getGridHeight());
      naturalScale.m_Region = new Rectangle2D.Double(0, 0, m_DrawingPanel.getWidth(), m_DrawingPanel.getHeight());
      m_ZoomHistory.add(naturalScale);
      //Las coordenadas reales coinciden con las naturales
      r2 = r;
    }
    else
    //Calculamos las coordenadas naturales (de pantalla) que se corresponden con estas coordenadas
    //reales en el dominio del problema pasadas por parámetro
      r2 = new Rectangle2D.Double(m_DrawingPanel.getXInPanel(r.x),
                                  m_DrawingPanel.getYInPanel(r.y),
                                  r.width * m_DrawingPanel.getXScaleFactor(),
                                  r.height * m_DrawingPanel.getYScaleFactor());

    //Le decimos al panel ahora tiene que representar la nueva región
    m_DrawingPanel.zoomIn(r.x, r.y, r.width, r.height);

    //Recuperamos la imagen de hipótesis que contiene el panel de dibujado
    BufferedImage hi = m_VisualOptionsPanel.getHypothesisImage();
    //Si existe dicha imagen
    if (hi != null){
      // Hay que evitar que cuando se maximiza pete (porque el tamaño de la imagen en el
      // panel de opciones visuales es menor que el de la imagen del panel de dibujo
      int finalImageWidth = Math.max(m_DrawingPanel.getWidth(),
                                     hi.getWidth());
      int finalImageHeight = Math.max(m_DrawingPanel.getHeight(),
                                      hi.getHeight());


      //Hacemos corresponder la imagen de hipótesis con lo que vemos en la pantalla
      //Demasie de cutre, pensar en algo mejor (+ rápido)
      if (hi.getWidth() < finalImageWidth || hi.getHeight() < finalImageHeight){
        m_VisualOptionsPanel.setHypothesisImage(resizeImage(m_VisualOptionsPanel.getHypothesisImage(),
                                                            finalImageWidth,
                                                            finalImageHeight));

        m_VisualOptionsPanel.setHypothesisSumImage(resizeImage(m_VisualOptionsPanel.getHypothesisSumImage(),
                                                               finalImageWidth,
                                                               finalImageHeight));
      }

      //Hacemos zoom de la imagen de hipótesis del panel de opciones visuales
      m_VisualOptionsPanel.setHypothesisImage(zoomInImage(m_VisualOptionsPanel.getHypothesisImage(),
                                                          r2, finalImageWidth, finalImageHeight));

      //Hacemos zoom de la imagen de hipótesis usando grados de confianza del panel de opciones visuales
      m_VisualOptionsPanel.setHypothesisSumImage(zoomInImage(m_VisualOptionsPanel.getHypothesisSumImage(),
                                                             r2, finalImageWidth, finalImageHeight));

      //También hacemos zoom, si procede, sobre la imagen de la rejilla
      BufferedImage gi = m_DrawingPanel.getGridImage();
      if (gi != null){
        //Hacemos corresponder la imagen de hipótesis con lo que vemos en la pantalla
        //Demasie de cutre, pensar en algo mejor (+ rápido)
        if (gi.getWidth() < finalImageWidth || gi.getHeight() < finalImageHeight)
          gi = resizeImage(gi, finalImageWidth, finalImageHeight);

        m_DrawingPanel.setGridImage(zoomInImage(gi, r2, finalImageWidth, finalImageHeight));
      }
    }

    //Repintamos todo
    m_DrawingPanel.clearSelection();
    m_VisualOptionsPanel.getRepaintAction().actionPerformed(null);
  }

  /**
   * Hace zoom out y retorna a la vista del problema a escala natural
   */
  private void doZoomOut(){

    ZoomHistory naturalScale = (ZoomHistory) m_ZoomHistory.get(0);

    //Recuperamos los datos que tengamos de cómo estaban las cosas antes de hacer zoom in
    m_VisualOptionsPanel.setHypothesisImage(naturalScale.m_HypothesisImage);
    m_VisualOptionsPanel.setHypothesisSumImage(naturalScale.m_HypothesisSumImage);
    m_DrawingPanel.setGridColumnWidth(naturalScale.m_GridDimensions.x);
    m_DrawingPanel.setGridRowHeight(naturalScale.m_GridDimensions.y);
    m_DrawingPanel.setGridWidth(naturalScale.m_GridDimensions.width);
    m_DrawingPanel.setGridHeight(naturalScale.m_GridDimensions.height);
    m_DrawingPanel.setUpdateGrid(true);

    //Repintamos todo
    m_DrawingPanel.setUpdateGrid(true);
    m_VisualOptionsPanel.getRepaintAction().actionPerformed(null);

    //Liberamos la memoria reservada para el historial de zoom
    m_ZoomHistory = new ArrayList();
  }

  /**
   * Boora la información guardada acerca del estado de las cosas en la vista a escala natural
   */
  private void resetNaturalScale(){
    ZoomHistory naturalScale = (ZoomHistory) m_ZoomHistory.get(0);

    naturalScale.m_HypothesisImage = null;
    naturalScale.m_HypothesisSumImage = null;

    naturalScale.m_GridDimensions.x = -1;
    naturalScale.m_GridDimensions.y = -1;
    naturalScale.m_GridDimensions.width = -1;
    m_DrawingPanel.setGridHeight(naturalScale.m_GridDimensions.height);
  }

  /**
   * Escala una región dentro de una imagen para que aparezca en otra imagen
   * de tamaño finalImageWidth x finalImageHeight
   *
   * @param image La imagen en que se encuentra la región a escalar
   * @param region la región dentro de la imagen a escalar
   * @param finalImageWidth El ancho de la imagen que se retornará
   * @param finalImageHeight El alto de la imagen que se retornará
   *
   * @return La imagen con la región escalada o null si image es null
   */
  private BufferedImage zoomInImage(final BufferedImage image, final Rectangle2D.Double region,
                                    final int finalImageWidth, final int finalImageHeight){

    if (image != null){
      //Comprobar que la región no exceda los límites de la imagen
      int maxWidth = image.getWidth() - (int) Math.round(region.x);
      int maxHeight = image.getHeight() - (int) Math.round(region.y);

      //El mayor tamaño a escojer de la imagen de zoom
      int width = (int) Math.round(region.width) < maxWidth ? (int) Math.round(region.width) : maxWidth;
      int height = (int) Math.round(region.height) < maxHeight ? (int) Math.round(region.height) : maxHeight;

      //La imagen que retornaremos
      BufferedImage bi = new BufferedImage(finalImageWidth, finalImageHeight, BufferedImage.TYPE_INT_ARGB);

      Graphics2D g2 = bi.createGraphics();
      //TODO: Controlada la calidad de dibujado
      g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      //Dibujamos la región de la imagen original para que ocupe toda la imagen que retornaremos
      g2.drawImage(image.getSubimage((int) Math.round(region.x),
                                     (int) Math.round(region.y),
                                     width,
                                     height),
                   0, 0, finalImageWidth, finalImageHeight, null);
      return bi;
    }
    return null;
  }

  /**
   * Retorna una nueva imagen de tamaño newWidth y newHeight y con la imagen
   * image pasada como parámetro dibujada en su interior; si
   * newWidth > image.getWidth() y/o newHeight > image.getHeight()
   * se rellena el área restante con un color transparente (0,0,0,0)
   *
   * @param image La imagen original
   * @param newWidth La nueva anchura
   * @param newHeight La nueva altura
   *
   * @return La imagen con el nuevo tamaño o null si image es null
   */
  private BufferedImage resizeImage(BufferedImage image, final int newWidth, final int newHeight){
    if (image != null){
      BufferedImage bi = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);

      //Rellenamos la imagen con un color transparente
      Graphics2D g2 = bi.createGraphics();
      g2.setColor(new Color(0, 0, 0, 0));
      g2.fillRect(0, 0, newWidth, newHeight);

      //Comprobar si estamos aumentando o disminuyendo el tamaño de la imagen
      final int minWidth = Math.min(image.getWidth(), newWidth);
      final int minHeight = Math.min(image.getHeight(), newHeight);

      //Pintar la imagen original en la nueva imagen
      g2.drawImage(image.getSubimage(0, 0, minWidth, minHeight), 0, 0, minWidth, minHeight, null);
      return bi;
    }
    return null;
  }

  /**
   * El frame principal de la aplicación
   */
  public static class AppMainFrame{

    /**
     * El método inicial de la aplicación
     *
     * @param args los parámetros para la aplicación (ignorados)
     */
    public static void main(String[] args){

      try{
        //La ventana principal de la aplicación
        JFrame app = new JFrame("Bidimensional generalization");

        //El icono para la ventana (y subventanas)
        app.setIconImage(AllContainerPanel.PROFX_ICON.getImage());

        //Configuración del Look & Feel; cuando haya tiempo, ponerlo "bonito" (Themes etc.)
        Font f = UIManager.getFont("Button.font");
        UIManager.put("Button.font", new FontUIResource(f.deriveFont(10F)));

        //Existe un bug en la clase javax.guiUtils.plaf.metal.MetalToolTipUI que hace que
        //las teclas de acceso rápido que no se corresponden con caracteres convencionales
        //se muestren de forma incorrecta, por lo que he preferido colocar esas teclas "a pelo".
        //Ver en el Java Bug Parade:
        //Bug ID 4375928 RFE Mnemonics for non-character keys are displayed incorrectly in the tooltip
        // if(!oaidtb.misc.Utils.isJVMVersionGreaterOrEqualThan("1.4.0"))
        // UIManager.getDefaults().put("ToolTipUI", "javax.guiUtils.plaf.basic.BasicToolTipUI");

        //El panel con los componentes de la aplicación
        final AllContainerPanel acp = new AllContainerPanel();

        //Salir de la aplicación cuando cerremos la ventana
        app.addWindowListener(
          new WindowAdapter(){
            public void windowClosing(WindowEvent e){
              System.exit(0);
            }
          }
        );

        //Añadir un menú de ayuda
        JMenu menuAyuda = new JMenu("Help");
        //El item de menu para abrir un frame con información sobre la aplicación
        JMenuItem aboutItem = new JMenuItem("About");
        //Listener para abrir el frame de about
        aboutItem.addActionListener(new ActionListener(){

          public void actionPerformed(ActionEvent e){

            if (m_AboutFrame == null){
              m_AboutFrame = new AboutFrame("OAIDTB Acerca de", new OaidtbInfo());
              m_AboutFrame.pack();
              m_AboutFrame.setSize(700, 350);
              RefineryUtilities.centerFrameOnScreen(m_AboutFrame);
            }
            m_AboutFrame.show();
            m_AboutFrame.requestFocus();
          }
        });
        menuAyuda.add(aboutItem);

        //Añadimos la barra de menú de la aplicación
        JMenuBar mb = new JMenuBar();
        mb.add(acp.createGeneralOptionsMenu());
        //Colocamos el menú de ayuda a la derecha
        mb.add(Box.createHorizontalGlue());
        mb.add(menuAyuda);
        app.setJMenuBar(mb);

        //Añadimos el panel que contiene los componentes de la aplicación a la ventana
        app.getContentPane().add(acp.createDesktopPane());

        //Lo mostramos en pantalla
        app.pack();
        app.setSize(800, 600);
        RefineryUtilities.centerFrameOnScreen(app);
        app.show();
      }
      catch (Exception e){
        System.err.println(e.toString());
        e.printStackTrace();
        System.exit(-1);
      }
    }
  }
}