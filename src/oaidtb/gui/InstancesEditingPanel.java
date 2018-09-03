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
 *    InstancesEditingPanel.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.gui;

import com.sun.image.codec.jpeg.JPEGCodec;
import com.sun.image.codec.jpeg.JPEGEncodeParam;
import com.sun.image.codec.jpeg.JPEGImageEncoder;
import oaidtb.gui.customizedWeka.GenericObjectEditor;
import oaidtb.gui.customizedWeka.PropertyPanel;
import oaidtb.misc.guiUtils.IntegerDocument;
import oaidtb.misc.guiUtils.LabeledSlider;
import oaidtb.misc.SimpleInteger;
import oaidtb.misc.javaTutorial.ImageFileView;
import oaidtb.misc.javaTutorial.ImageFilter;
import oaidtb.misc.javaTutorial.ImagePreview;
import oaidtb.misc.mediator.Arbitrable;
import oaidtb.misc.mediator.Colleague;
import oaidtb.misc.mediator.Mediator;
import oaidtb.misc.pointDistributions.PointDistribution;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import weka.gui.ExtensionFileFilter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.awt.image.PixelGrabber;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.util.ArrayList;

/**
 * Panel que permite seleccionar los modos en que introducimos nuevas instancias
 * en un {@link Point2DInstances}, posiblemente interactivamente a trav�s de un
 * {@link DrawingPanel}. Permite rehacer/deshacer las inserciones, cargar y salvar
 * a ficheros y entrar en modo zoom.
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class InstancesEditingPanel extends JPanel implements Arbitrable{

  //El panel de dibujado
  private final DrawingPanel m_DrawingPanel;

  //Las instancias que representan a los puntos (y viceversa)
  private final Point2DInstances m_Points;

  //Undo & Redo points insertion features
  /** Log del n�mero de instancias a�adidas con cada click del rat�n
   *
   */
  private ArrayList m_NumberOfInserted = new ArrayList();
  /** Log de las instancias eliminadas en cada operaci�n de undo */
  private ArrayList m_RedoBuffer = new ArrayList();

  /** Bot�n de undo */
  private JButton m_UndoButton = new JButton("Undo");
  /** Bot�n de redo */
  private JButton m_RedoButton = new JButton("Redo");
  /** Bot�n para vaciar la memoria reservada para realizar las operaciones de undo & redo */
  private JButton m_FlushButton = new JButton("Flush buffer");
  /** Simplemente borrar las instancias al hacer undo o repintar todo el panel */
  private JCheckBox m_FastEraseMode = new JCheckBox("Fast erase mode", false);

  //Buttons to assign colors to left and right mouse buttons
  /** El color asignado a las pulsaciones del bot�n izquierdo del rat�n */
  protected JButton m_Color1Button = new JButton();
  /** El color asignado a las pulsaciones del bot�n derecho del rat�n */
  protected JButton m_Color2Button = new JButton();

  //Selectores de color a asignar a cada bot�n del rat�n
  /** El selector de color */
  protected JColorChooser m_ColorChooser = new JColorChooser();
  /** El panel que contiene el historial de colores existentes en la base de datos */
  protected ColorHistoryPanel m_ColorHistory;

  /** Borrar las enstancias de la base de datos */
  private JButton m_ResetButton = new JButton("Reset");

  /** Bot�n para partir las instancias en conjuntos de entrenamiento y de test */
  private JButton m_SplitButton = new JButton("Split");
  /** Bot�n para unir las instancias de test que existan con las de test */
  private JButton m_JoinButton = new JButton("Join");
  /** Configuara el porcentaje de instancias en cada conjunto en la siguiente partici�n */
  private LabeledSlider m_TrainPercentageLS = new LabeledSlider("Train percentage: ");
  /** A la hora de crear los conjuntos de netrenamiento de y de test, usar s�lo las instancias visibles */
  private JCheckBox m_UseOnlyInstancesInRegionCB = new JCheckBox("Use only visible instances", true);

  //Load & Save components
  /** El selector de ficheros para seleccionar archivos con extensi�n ARFF */
  private JFileChooser m_FileChooser = new JFileChooser(new File(System.getProperty("user.dir")));
  /** El selector de ficheros para seleccionar archivos de imagen */
  private JFileChooser m_ImageFileChooser = new JFileChooser(new File(System.getProperty("user.dir")));
  /** Bot�n para cargar instancias desde un archivo ARFF */
  private JButton m_LoadARFFButton = new JButton("Load ARFF");
  /** Bot�n para salvar las instancias a un archivo ARFF */
  private JButton m_SaveARFFButton = new JButton("Save ARFF");
  /** Bot�n para cargar instancias desde una imagen */
  private JButton m_LoadImageButton = new JButton("Load Image");
  /** Bot�n para salvar el panel de dibujado en un archivo JPEG */
  private JButton m_SaveImageButton = new JButton("Save Image");
  /** Escalar las instancias tras cargarlas para que ocupen toda la extensi�n del panel a escala natural? */
  private JCheckBox m_ScaleAfterLoadCB = new JCheckBox("Auto scale", false);

  /** Un hilo para salvar/cargar instancias desde ficheros */
  protected Thread m_IOThread;

  /** Seleccionar qu� modo de inserci�n vamos a utilizar */
  private JComboBox m_InsertModeCoB = new JComboBox(new String[]{"Allow all points",
                                                                 "One point per color and coords",
                                                                 "One point per coords"});
  /** A�adir un solo punto con cada click del rat�n */
  private JCheckBox m_SinglePointAddCB = new JCheckBox("Add single points", true);
  /** A�adir m�ltiples puntos con cada click del rat�n */
  private JCheckBox m_MultiplePointAddCB = new JCheckBox("Add", false);

  //Cuando estamos en modo de inserci�n m�ltiple, qu� funci�n vamos a utilizar
  /** N�mero de puntos a a�adir con cada click del rat�n en el modo de adici�n m�ltiple */
  private JTextField m_NumPointsToAddPerClickTF = new JTextField("10");
  /** Para elegir el {@link PointDistribution} que se usar� para generar los puntos */
  private GenericObjectEditor m_DistributionEditor = new GenericObjectEditor();
  /** Mostrar la selecci�n actual de un {@link PointDistribution} para generar los puntos */
  private PropertyPanel m_DistributionSelector = new PropertyPanel(m_DistributionEditor);
  /** La funci�n que se utilizar� para generar los puntos */
  private PointDistribution m_Distribution = new oaidtb.misc.pointDistributions.UniformDistribution();

  //Entrar y salir del modo zoom y hacer zoom out
  /** Entrar o salir del modo zoom */
  protected JCheckBox m_ZoomModeCB = new JCheckBox("Zoom mode");
  /** Hacer zoom out */
  protected JButton m_ZoomOutButton = new JButton("Zoom out");

//  TODO: Centralizar las acciones todo con el interfaz Action, colocar todos los componentes como private
//  private MyAbstractAction m_ZoomModeAction;
//  private MyAbstractAction m_ZoomOutAction;
//  private MyAbstractAction m_ChangeColor1Action;
//  private MyAbstractAction m_ChangeColor2Action;
//  private MyAbstractAction m_SplitAction;
//  private MyAbstractAction m_JoinAction;
//  private MyAbstractAction m_ResetAction;
//  private MyAbstractAction m_LoadARFFAction;
//  private MyAbstractAction m_SaveARFFAction;

  /**
   * Constructor.
   *
   * @param drawingPanel El panel de dibujado en que se mostrar�n las instancias
   */
  public InstancesEditingPanel(DrawingPanel drawingPanel){
    m_DrawingPanel = drawingPanel;
    m_Points = m_DrawingPanel.getPoints();
    initGUI();
    layoutComponents();
  }

  /**
   * Listener que escucha las pulsaciones de bot�n del rat�n sobre el panel de
   * dibujo para insertar instancias en el problema
   */
  private MouseListener m_AddInstancesListener = new MouseAdapter(){
    public synchronized void mousePressed(MouseEvent e){

      //Las coordenadas de la pulsaci�n
      final int x = e.getX(), y = e.getY();
      //Dependiendo del bot�n pulsado...
      final JButton button = e.isMetaDown() ? m_Color2Button : m_Color1Button;
      //La instancia ser� del color del bot�n 1 o del 2
      final Color color = button.getBackground();

      //Modo de inserci�n m�ltiple
      if (m_MultiplePointAddCB.isSelected()){

        int numInserted = 0;

        //Los n�meros generados por la funci�n s�lo ser�n a�adidos si caen dentro del �rea de la pantalla
        final int maxX = m_DrawingPanel.getWidth() - 1,
          maxY = m_DrawingPanel.getHeight() - 1;

        //A�adimos tantos puntos como haya especificado el usuario
        for (int i = 0; i < Integer.parseInt(m_NumPointsToAddPerClickTF.getText()); i++){

          //Generamos un nuevo punto utilizando la funci�n seleccionada
          m_Distribution.setCenter(x);
          int newX = (int) Math.round(m_Distribution.nextDouble());
          m_Distribution.setCenter(y);
          int newY = (int) Math.round(m_Distribution.nextDouble());

          //Si el punto generado no est� en el �rea vista en la pantalla, no lo a�adimos
          if (newX < 0 || newX > maxX || newY < 0 || newY > maxY)
            continue;

          //Intentamos a�adirlo a la base de datos
          if (m_Points.addPoint(newX, newY, color))
            numInserted++;
        }
        //Dibujamos las instancias en la imagen de instancias
        m_DrawingPanel.drawLastInstances(numInserted, color);
        //Para poder hacer undo
        newInsertionOp(numInserted);
        //Repintamos el panel
        m_DrawingPanel.repaint();
      }
      else{//A�adimos un punto en el lugar donde hemos hecho el click
        //Intentamos a�adirlo a la base de datos
        if (m_Points.addPoint(x, y, color)){
          //Para poder hacer undo
          newInsertionOp(1);
          //Dibujamos el punto
          m_DrawingPanel.drawInstance(x, y, color);
        }
      }
      updateColorSelectionButtonsText();
    }
  };

  /**
   * Listener que escucha las pulsaciones de bot�n del rat�n sobre el panel de
   * dibujo para insertar instancias en el problema. Esta es la versi�n para a�adir
   * puntos cuando nos encontramos en modo zoom, de manera que mapeamos
   * las coordenadas de pantalla a las correspondientes del �rea que esta representa.
   */
  private MouseListener m_AddInstancesListenerWithZoom = new MouseAdapter(){
    public synchronized void mousePressed(MouseEvent e){

      //Las coordenadas de la pulsaci�n
      final int x = e.getX(), y = e.getY();
      //Dependiendo del bot�n pulsado...
      final JButton button = e.isMetaDown() ? m_Color2Button : m_Color1Button;
      //La instancia ser� del color del bot�n 1 o del 2
      final Color color = button.getBackground();

      if (m_MultiplePointAddCB.isSelected()){

        //Un vector en el que guardar las instancias que a�adimos para poder dibujarlas despu�s r�pidamente
        FastVector vector = new FastVector();

        int numInserted = 0;

        //Calculamos los l�mites de la regi�n representada en el panel
        final double origX, origY, finalX, finalY;
        origX = m_DrawingPanel.getRegionX();
        origY = m_DrawingPanel.getRegionY();
        finalX = origX + m_DrawingPanel.getRegionWidth();
        finalY = origY + m_DrawingPanel.getRegionHeight();

        //Calculamos las coordenadas en la regi�n representada del punto del panel sobre el que hemos hecho click
        final double x2 = m_DrawingPanel.getXFromPanel(x);
        final double y2 = m_DrawingPanel.getYFromPanel(y);

        //A�adimos tantos puntos como marque el marco de configuraci�n
        for (int i = 0; i < Integer.parseInt(m_NumPointsToAddPerClickTF.getText()); i++){

          //Generamos un nuevo punto utilizando la funci�n seleccionada
          m_Distribution.setCenter(x2);
          double newX = m_Distribution.nextDouble();
          m_Distribution.setCenter(y2);
          double newY = m_Distribution.nextDouble();

          //Si el punto generado no est� en la regi�n representada en la pantalla, no lo a�adimos
          if (newX < origX || newX > finalX || newY < origY || newY > finalY)
            continue;

          //Intentamos a�adirlo a la base de datos
          if (m_Points.addPoint(newX, newY, color)){
            numInserted++;
            //Guardamos las coordenadas del punto generado trasladadas y escaladas en la pantalla
            vector.addElement(new Instance(1, new double[]{m_DrawingPanel.getXInPanel(newX),
                                                           m_DrawingPanel.getYInPanel(newY),
                                                           0}));
          }
        }
        //Dibujamos los puntos a�adidos en la imagen de instancias
        m_DrawingPanel.drawInstances(vector, color);
        //Para poder hacer undo
        newInsertionOp(numInserted);
        //Repuntamos el panel
        m_DrawingPanel.repaint();
      }
      else{//A�adimos un solo punto
        //Intentamos a�adir el punto de la regi�n que representa el punto sobre el que hemos
        //pulsado en la pantalla
        if (m_Points.addPoint(m_DrawingPanel.getXFromPanel(x), m_DrawingPanel.getYFromPanel(y), color)){
          newInsertionOp(1);
          //Dibujamos el punto
          m_DrawingPanel.drawInstance(x, y, color);
        }
      }
      updateColorSelectionButtonsText();
    }
  };

  /**
   * Actualizamos el texto de los botones de selecci�n de color
   * para que muestren el n�mero de instancias del color que representa cada bot�n
   */
  public void updateColorSelectionButtonsText(){
    m_Color1Button.setText(String.valueOf(m_Points.getNumPointsOfColor(m_Color1Button.getBackground())));
    m_Color2Button.setText(String.valueOf(m_Points.getNumPointsOfColor(m_Color2Button.getBackground())));
  }

  /**
   * Definimos que la clase est� ocupada si est� haciendo operaciones de entrada/salida a ficheros
   *
   * @return true Si la clase est� ocupada
   */
  public boolean isBusy(){
    return m_IOThread == null;
  }

  /**
   * En el modo edici�n, las instancias que existen en la base de datos
   * pueden ser cambiadas en cualquier momento; este m�todo habilita las acciones
   * que pueden provocar cambios en la base de datos y, si es pertinente, a�ade los
   * listeners necesarios al panel de dibujado para insertar instancias manualmente
   *
   * <p><p> Este m�todo es ejecutado de principio a fin sin poder ser interrumpido por ning�n otro thread
   *
   */
  private void enterEditMode(){

    //El thread en que estamos ejecutando esta operaci�n
    final Thread thread = Thread.currentThread();

    //Esta operaci�n debe ser ejecutada inmediatamente y sin interrupci�n
    final int priority = thread.getPriority();
    thread.setPriority(Thread.MAX_PRIORITY);

    //A�adimos los listeners que permiten meter instancias con clicks de rat�n
    if (!m_ZoomModeCB.isSelected() && !isBusy()){
      if (m_DrawingPanel.isZoomedIn()){
        m_DrawingPanel.removeMouseListener(m_AddInstancesListenerWithZoom);
        m_DrawingPanel.addMouseListener(m_AddInstancesListenerWithZoom);
      }
      else{
        m_DrawingPanel.removeMouseListener(m_AddInstancesListener);
        m_DrawingPanel.addMouseListener(m_AddInstancesListener);
      }
    }

    //Habilitar el bot�n de eliminar las instancias
    m_ResetButton.setEnabled(true);

    //Habilitar los botones de partici�n de instancias en test & train
    m_SplitButton.setEnabled(true);
    m_JoinButton.setEnabled(true);

    //Habilitar los botones de modo zoom
    m_ZoomModeCB.setEnabled(true);
    m_ZoomOutButton.setEnabled(m_DrawingPanel.isZoomedIn());

    //Habilitar las caracter�sticas de undo/redo
    m_UndoButton.setEnabled(m_NumberOfInserted.size() != 0);
    m_RedoButton.setEnabled(m_RedoBuffer.size() !=0);
    m_FlushButton.setEnabled(m_RedoBuffer.size() != 0);

    //Habilitamos los botones de cargar/salvar a fichero
    m_LoadARFFButton.setEnabled(true);
    m_SaveARFFButton.setEnabled(true);
    m_LoadImageButton.setEnabled(true);
    m_SaveImageButton.setEnabled(true);

    //Volvemos a la prioridad anterior
    thread.setPriority(priority);
    Thread.yield();
  }

  /**
   * En el modo edici�n, las instancias que existen en la base de datos
   * pueden ser cambiadas en cualquier momento; este m�todo deshabilita las acciones
   * que pueden provocar cambios en la base de datos y, si es pertinente, quita los
   * listeners necesarios al panel de dibujado para evitar insertar instancias manualmente
   *
   * <p><p> Este m�todo es ejecutado de principio a fin sin poder ser interrumpido por ning�n otro thread
   */
  public void exitEditMode(){

    //El thread en que estamos ejecutando esta operaci�n
    final Thread thread = Thread.currentThread();

    //Esta es una operaci�n que debe ser ejecutada inmediatamente y sin interrupci�n
    final int priority = thread.getPriority();
    thread.setPriority(Thread.MAX_PRIORITY);

    //Quitamos los listeners que permiten meter instancias con clicks de rat�n
    m_DrawingPanel.removeMouseListener(m_AddInstancesListener);
    m_DrawingPanel.removeMouseListener(m_AddInstancesListenerWithZoom);

    //Deshabilitar el bot�n de eliminar las instancias
    m_ResetButton.setEnabled(false);

    //Deshabilitar los botones de partici�n de instancias en test & train
    m_SplitButton.setEnabled(false);
    m_JoinButton.setEnabled(false);

    //Deshabilitar los botones de modo zoom
    m_ZoomModeCB.setEnabled(false);
    m_ZoomOutButton.setEnabled(false);

    //Habilitar las caracter�sticas de undo/redo
    m_UndoButton.setEnabled(m_NumberOfInserted.size() != 0);
    m_RedoButton.setEnabled(m_RedoBuffer.size() !=0);
    m_FlushButton.setEnabled(m_RedoBuffer.size() != 0);

    //Deshabilitamos los botones de cargar/salvar a fichero
    m_LoadARFFButton.setEnabled(false);
    m_SaveARFFButton.setEnabled(false);
    m_LoadImageButton.setEnabled(false);
    m_SaveImageButton.setEnabled(false);

    //Volvemos a la prioridad anterior
    thread.setPriority(priority);
    Thread.yield();
  }

  /** Inicializaci�n de los componentes y de las acciones */
  private void initGUI(){

    //Bot�n de undo
    m_UndoButton.setEnabled(false);
    m_UndoButton.addActionListener(new ActionListener(){
      public synchronized void actionPerformed(ActionEvent e){
        try{
          undoInsertion();
        }
        catch (Exception ex){
          System.err.println(ex.toString());
        }
      }
    });
    m_UndoButton.setToolTipText("Deshacer la inserci�n de instancias provocada por el �ltimo click del rat�n");

    //Bot�n de redo
    m_RedoButton.setEnabled(false);
    m_RedoButton.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e){
        try{
          redoInsertion();
        }
        catch (Exception ex){
          System.err.println(ex.toString());
        }
      }
    });
    m_RedoButton.setToolTipText("Rehacer la �ltima inserci�n de instancias deshecha");

    //Bot�n para liberer la memoria reservada por las operaciones de undo & redo
    m_FlushButton.setEnabled(false);
    m_FlushButton.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e){
        flushRedoAndUndoBuffers();
        m_FlushButton.setEnabled(false);
      }
    });
    m_FlushButton.setToolTipText("Liberar la memoria reservada por las operaciones de undo & redo, deshabilit�ndolas");

    //Checkbox para elegir entre repintar todas las instancias o borrar s�lo las que se han eliminado con un undo
    m_FastEraseMode.setToolTipText("Repintar todas las instancias o s�lo borrar las quitadas con cada operaci�n de undo");

    ////////////
    /// Elecci�n del color que representa un bot�n del rat�n
    ////////////

    //A�adimos el panel que muestra los colores existentes en la base de datos de instancias
    //dentro del selector de color
    m_ColorChooser.addChooserPanel(m_ColorHistory = new ColorHistoryPanel(m_Points));

    //Elecci�n del color
    ActionListener m = new ActionListener(){
      public void actionPerformed(ActionEvent e){
        final JButton button = (JButton) e.getSource();
        //Listener que comprueba si se ha aceptado una nueva selecci�n
        ActionListener OKListener = new ActionListener(){
          public void actionPerformed(ActionEvent e2){
            Color c = m_ColorChooser.getColor();
            if (c != null){  //Nunca puede pasar, pero por si las moscas...
              button.setBackground(c);
              button.setText(String.valueOf(m_Points.getNumPointsOfColor(c)));
            }
          }
        };
        //Recreamos el panel de historial de colores
        m_ColorHistory.remake();
        //Mostramos el di�logo de selecci�n de color
        m_ColorChooser.createDialog(button, "Elige un color:", true, m_ColorChooser, OKListener, null).show();
      }
    };

    //Inicializamos los botones de selecci�n de color
    m_Color1Button.addActionListener(m);
    m_Color2Button.addActionListener(m);
    m_Color1Button.setBackground(Color.blue);
    m_Color2Button.setBackground(Color.red);
    updateColorSelectionButtonsText();
    m_Color1Button.setToolTipText("Elegir el color asignado a los clicks del bot�n izquierdo del rat�n");
    m_Color2Button.setToolTipText("Elegir el color asignado a los clicks del bot�n derecho del rat�n");

    //Edici�n de instancias
    m_DrawingPanel.addMouseListener(m_AddInstancesListener);

    //Selecci�n de la funci�n a utilizar cuando estemos en modo de inserci�n de instancias m�ltiple
    m_DistributionEditor.setClassType(oaidtb.misc.pointDistributions.PointDistribution.class);
    m_DistributionEditor.setValue(m_Distribution);
    ((GenericObjectEditor.GOEPanel) m_DistributionEditor.getCustomEditor()).addOkListener(new ActionListener(){
      public void actionPerformed(ActionEvent e){
        m_Distribution = (PointDistribution) m_DistributionEditor.getValue();
      }
    });
    m_DistributionEditor.addPropertyChangeListener(new PropertyChangeListener(){
      public void propertyChange(PropertyChangeEvent e){
        repaint();
      }
    });

    //Campo para editar el n�mero de instancias a a�adir con cada click del rat�n
    IntegerDocument document = new IntegerDocument();
    document.setMaxValue(999);
    document.setMinValue(1);
    m_NumPointsToAddPerClickTF.setDocument(document);
    m_NumPointsToAddPerClickTF.setText("10");
    m_NumPointsToAddPerClickTF.setToolTipText("N�mero de puntos a a�adir con cada click del rat�n");

    //Tooltips de las checkboxes de selecci�n del modo de inserci�n de instancias
    m_SinglePointAddCB.setToolTipText("A�adir un unico punto con cada click del rat�n");
    m_MultiplePointAddCB.setToolTipText("A�adir varios puntos con cada click del rat�n");

    //ComboBox para configurar las restricciones de inserci�n:
    //Un punto por coordenadas, un punto por coordenadas y color o todos los puntos
    m_InsertModeCoB.addItemListener(new ItemListener(){
      public void itemStateChanged(ItemEvent e){
        m_Points.setInsertMode(m_InsertModeCoB.getSelectedIndex());
      }
    });
    m_InsertModeCoB.setSelectedIndex(2);
    m_InsertModeCoB.setToolTipText("Permitir a�adir todas las instancias, s�lo una por punto y color o s�lo una por punto");


    ///////////////////////////////////////////
    /// Partici�n de las instancias en conjuntos de entrenamiento y prueba
    ///////////////////////////////////////////

    //Configuraci�n de la barra de configuraci�n del porcentaje de instancias de entrenamiento
    m_TrainPercentageLS.getSlider().setMaximum(100);
    m_TrainPercentageLS.getSlider().setMinimum(1);
    m_TrainPercentageLS.getSlider().setToolTipText("Porcentaje de instancias que ir�n al conjunto de entrenamiento");

    //A la hora de partir las instancias en conjuntos de entrenamiento y prueba, usar s�lo aquellas que sean visibles en la pantalla
    m_UseOnlyInstancesInRegionCB.setToolTipText("Al partir las instancias de entrenamiento y test, usar s�lo aquellas visibles en la pantalla");

    //Bot�n de partir las instancias en conjuntos de entrenamiento y prueba
    m_SplitButton.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e){
        if (m_UseOnlyInstancesInRegionCB.isSelected() && m_DrawingPanel.isZoomedIn())
          m_Points.createTrainAndTestInstancesInRegion(m_DrawingPanel.getRegionX(), m_DrawingPanel.getRegionY(),
                                                       m_DrawingPanel.getRegionWidth(), m_DrawingPanel.getRegionHeight(),
                                                       m_TrainPercentageLS.getSlider().getValue() / 100.0);
        else
          m_Points.createTrainAndTestInstances(m_TrainPercentageLS.getSlider().getValue() / 100.0);
        //Repintamos las instancias en el panel para poder representar la nueva partici�n de instancias
        m_DrawingPanel.setUpdateInstances(true);
        m_DrawingPanel.repaint();
        //Jefes, hemos partido las instancias
        notifyMediators(INSTANCES_SPLIT, m_Points);
      }
    });
    m_SplitButton.setToolTipText("Partir aleatoriamente las instancias en conjuntos de entrenamiento y de test");

    //Bot�n para unir las instancias de prueba a las de entrenamiento
    m_JoinButton.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e){
        m_Points.destroyTrainAndTestInstances();
        //Repintamos las instancias del panel para representar todas como instancias de entrenamiento
        m_DrawingPanel.setUpdateInstances(true);
        m_DrawingPanel.repaint();
        //Jefes, hemos unido las instancias
        notifyMediators(INSTANCES_JOIN, m_Points);
      }
    });
    m_JoinButton.setToolTipText("Unir las instancias de entrenemiento y de test");

    //Bot�n de eliminar todas las instancias de la base de datos
    m_ResetButton.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e){
        //Purgamos las instancias de la base de datos
        m_Points.removeAll();
        //Colocamos (a 0) el contador de instancias de cada color...
        updateColorSelectionButtonsText();
        //Jefes, hemos borrado todas las instancias
        notifyMediators(INSTANCES_DELETED, m_Points);
      }
    });
    m_ResetButton.setToolTipText("Borrar todas las instancias");

    ////////////////////////////////
    /// Botones de cargar / salvar
    ////////////////////////////////

    //El di�logo de selecci�n de ficheros ARFF
    m_FileChooser.setFileFilter(new ExtensionFileFilter(Instances.FILE_EXTENSION, "Arff data "));
    m_FileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

    //Bot�n de salvar las instancias a un fichero ARFF
    m_SaveARFFButton.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e){
        if (m_FileChooser.showSaveDialog(m_SaveARFFButton) == JFileChooser.APPROVE_OPTION){
          m_Points.selectAllInstances();
          saveInstancesToFile(m_FileChooser.getSelectedFile(), m_Points);
        }
      }
    });
    m_SaveARFFButton.setToolTipText("Salvar todas las instancias a un archivo ARFF");

    //Bot�n de cargar las instancias desde un fichero ARFF
    m_LoadARFFButton.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e){
        if (m_FileChooser.showOpenDialog(m_SaveARFFButton) == JFileChooser.APPROVE_OPTION){
          loadInstancesFromFile(m_FileChooser.getSelectedFile());
        }
      }
    });
    m_LoadARFFButton.setToolTipText("Cargar instancias desde un archivo ARFF");

    //El di�logo de selecci�n de ficheros de im�genes
    m_ImageFileChooser.addChoosableFileFilter(new ImageFilter());
    m_ImageFileChooser.setFileView(new ImageFileView());
    m_ImageFileChooser.setAccessory(new ImagePreview(m_ImageFileChooser));

    //Bot�n para cargar las instancias desde una imagen
    m_LoadImageButton.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e){
        if (m_ImageFileChooser.showOpenDialog(m_LoadImageButton) == JFileChooser.APPROVE_OPTION){
          loadInstancesFromImage(m_ImageFileChooser.getSelectedFile().getPath());
        }
      }
    });
    m_LoadImageButton.setToolTipText("Cargar instancias desde una imagen (beta)");

    //Bot�n para guardar el aspecto del panel de dibujado a una imagen JPEG
    //TODO: Moverlo al panel de opciones visuales
    m_SaveImageButton.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e){
        if (m_ImageFileChooser.showSaveDialog(m_SaveImageButton) == JFileChooser.APPROVE_OPTION){
          captureDrawingPanelToJPEGFile(m_ImageFileChooser.getSelectedFile().getPath());
        }
      }
    });
    m_SaveImageButton.setToolTipText("Capturar a una imagen JPEG el panel de dibujado");

    //Tooltip de la checkbox de autoescalado
    m_ScaleAfterLoadCB.setToolTipText("Tras cargar, trasladar y escalar las instancias para que se ajusten a las dimensiones"+
                                      " actuales del panel de dibujado");

    ////////////////////////////////
    /// Manejo del modo zoom
    ////////////////////////////////

    //Checkbox de entrar/salir del modo zoom
    m_ZoomModeCB.addItemListener(new ItemListener(){
      public void itemStateChanged(ItemEvent e){
        if (m_ZoomModeCB.isSelected()){
          m_DrawingPanel.removeMouseListener(m_AddInstancesListener);
          m_DrawingPanel.removeMouseListener(m_AddInstancesListenerWithZoom);
          m_DrawingPanel.enterSelectionMode();
        }
        else{
          if (m_DrawingPanel.isZoomedIn()){
            m_DrawingPanel.removeMouseListener(m_AddInstancesListenerWithZoom);
            m_DrawingPanel.addMouseListener(m_AddInstancesListenerWithZoom);
          }
          else{
            m_DrawingPanel.removeMouseListener(m_AddInstancesListener);
            m_DrawingPanel.addMouseListener(m_AddInstancesListener);
          }
          m_DrawingPanel.exitSelectionMode();
        }
      }
    });
    m_ZoomModeCB.setToolTipText("Entrar en modo zoom" + " (Alt-Z)");

    //Bot�n de zoom out
    m_ZoomOutButton.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e){
        if (m_DrawingPanel.isZoomedIn()){
          m_DrawingPanel.zoomOut();
          m_DrawingPanel.removeMouseListener(m_AddInstancesListenerWithZoom);
          if (!m_ZoomModeCB.isSelected())
            m_DrawingPanel.addMouseListener(m_AddInstancesListener);
        }
      }
    });
    m_ZoomOutButton.setEnabled(false);
    m_ZoomOutButton.setToolTipText("Volver a ver todo el dominio del problema" + " (Alt-X)");
  }

  /**
   * Este m�todo captura la imagen del panel de dibujado que aparece en pantalla
   * y la pasa a un archivo JPEG (con m�xima calidad de compresi�n);
   * al contrario que el resto de las funciones de I/O,
   * esta operaci�n no la hacemos en un thread diferente, pues acaba r�pidamente
   * en todos los casos: la compresi�n no es un proceso muy pesado y como m�ximo
   * la imagen a comprimir tendr� un tama�o casi igual al de la pantalla del
   * usuario, por lo que tenemos la certeza de que el archivo a guardar nunca
   * ser� de gran tama�o.
   *
   * <p>Si la extensi�n del fichero pasado no es "jpg" o "jpeg", se a�ade la extensi�n
   * "jpg" al nombre del fichero
   *
   * @param path El camino de acceso al archivo de acceso al fichero
   */
  private void captureDrawingPanelToJPEGFile(String path){
    try{
      //Capturamos la imagen del componente
      final Image image = oaidtb.misc.Utils.captureComponentToImage(m_DrawingPanel);

      //La pintamos en un BufferedImage de tipo INT_RGB
      final BufferedImage bi = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_RGB);
      Graphics2D g = bi.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.drawImage(image, 0, 0, bi.getWidth(), bi.getHeight(), null);
      g.dispose();

      //Abrimos el stream de salida al fichero especificado
      final BufferedOutputStream out;
      if (path.endsWith(".jpg") || path.endsWith(".jpeg"))
        out = new BufferedOutputStream(new FileOutputStream(path));
      else
        out = new BufferedOutputStream(new FileOutputStream(path + ".jpg"));

      //Utilizamos el codec jpeg distribuido con el JDK
      JPEGImageEncoder encoder = JPEGCodec.createJPEGEncoder(out);
      //Clase para configurar las opciones de configuraci�n
      JPEGEncodeParam param = encoder.getDefaultJPEGEncodeParam(bi);
      //M�xima calidad
      param.setQuality(1F, true);
      encoder.setJPEGEncodeParam(param);
      //Comprimimos la imagen
      encoder.encode(bi);

      //Cerramos el stream
      out.close();
    }
    catch (Exception ex){
      ex.printStackTrace();
    }
  }

  /**
   * Cargar las instancias desde un archivo de imagen.
   *
   * <p><p>
   * Dado que la base de datos
   * de instancias est� pensada para poder a�adir instancias interactivamente, la carga
   * de instancias "masiva" desde un fichero de imagen, ie. la transformaci�n de sus
   * pixeles en instancias que entiendan las clases de weka, a una estructura de
   * datos de este tipo es extremadamente lenta debido, principalmente, al crecimiento
   * de los �ndices de colores sobre instancias; por ejemplo, la carga de una imagen con
   * 54000 puntos y 19504 colores distintos puntos es una tarea que llega a tardar 27 minutos
   * en un AMD Athlon XP 1466@1700. Existe, en primera fase de desarrollo a�n, una clase que
   * he llamado FastBatchPoint2DInstances que es una versi�n de la clase Point2DInstances
   * pensada especialmente para esta tarea; el tiempo de carga de la misma imagen con esta clase
   * se reduce, en las mismas condiciones, a algo menos de 20 segundos, con un consumo de
   * memoria y unas funcionalidades similares.
   *
   * <p>De todas formas, debido tambi�n a la propia ineficiencia de Java en el manejo de la memoria y
   * al hecho de que las clases/estructuras de weka sean gen�ricas,
   * los m�todos y las estructuras de la librer�a no est�n preparados para manejar
   * conjuntos de datos tan vastos y esto ha hecho que me haya resultado imposible realizar operaci�n
   * alguna con estas 54000 instancias (siempre con la excepci�n <<Out of memory>>...),
   * ni a�n operando desde la l�nea de comandos; por lo tanto,
   * he descartado moment�neamente la creaci�n de una aplicaci�n independiente para poder
   * crear hip�tesis a partir de im�genes "tan grandes", debido al gran (y oscuro) trabajo
   * que ello supondr�a.
   *
   * @param imageName El path del fichero de imagen
   */
  public synchronized void loadInstancesFromImage(final String imageName){
    //Comprobar que no estemos realizando ya otra operaci�n de I/O
    if (m_IOThread == null){
      m_IOThread = new Thread(){
        public void run(){
          //Bandera que indica si el proceso ha acabado regularmente o ha fallado
          boolean isFailed = true;
          //Evitamos cambiar las instancias mientras estamos salvando
          exitEditMode();
          try{
            //Comunicar a los mediadores que hemos comenzado a cargar instancias
            notifyMediators(INSTANCES_LOAD_START, imageName);

            //Cargamos la imagen
            Image img = Toolkit.getDefaultToolkit().getImage(imageName);
            MediaTracker mediaTracker = new MediaTracker(m_LoadImageButton);
            mediaTracker.addImage(img, 0);
            //Esperamos a que finalice la carga
            mediaTracker.waitForID(0);

            final int w = img.getWidth(null);
            final int h = img.getHeight(null);
            //Avisar de que vamos a cargar una imagen muy grande
            if (w * h > 6000){
              final String mensaje = "La imagen tiene " + (w * h) + " puntos, continuar?";
              if (JOptionPane.YES_OPTION != JOptionPane.showConfirmDialog(m_LoadImageButton, mensaje))
                throw new Exception("La imagen es demasiado grande");
            }

            //Volcamos los pixeles de la imagen a un array de enteros
            final int[] rgbs = new int[w * h];
            PixelGrabber pg = new PixelGrabber(img, 0, 0, w, h, rgbs, 0, w);
            pg.grabPixels();
            if ((pg.getStatus() & ImageObserver.ABORT) != 0)
              throw new Exception("Image fetch aborted or errored");

            //Creamos las instancias con el array de enteros
            m_Points.createFromImage(rgbs, w, h);

            //Escalamos si es preciso los puntos
            if(m_ScaleAfterLoadCB.isSelected())
              scaleInstancesFromImage(m_Points, m_DrawingPanel.getWidth(), m_DrawingPanel.getHeight(), w, h);

            //Actualizamos las etiquetas de los botones de selecci�n de color
            updateColorSelectionButtonsText();

            //Hemos conseguid cargar las instancias con �xito
            isFailed = false;
          }
          catch (Exception ex){
            JOptionPane.showMessageDialog(m_LoadARFFButton,
                                          "Couldn't read '"
                                          + imageName + "' as an image file.\n"
                                          + "Reason:\n" + ex.getMessage(),
                                          "Load Image",
                                          JOptionPane.ERROR_MESSAGE);
          }
          finally{
            //Volvemos al modo de edici�n, del que ya ven�amos
            enterEditMode();
            //Notificar que ya hemos acabado de cargar las instancias
            if(isFailed)
              notifyMediators(INSTANCES_LOAD_FAIL, imageName);
            else
              notifyMediators(INSTANCES_LOAD_FINISH, imageName);
            //Podemos empezar otra operaci�n de I/O
            m_IOThread = null;
          }
        }
      };
      m_IOThread.setPriority(Thread.MIN_PRIORITY); //El interfaz debe tener mayor prioridad
      m_IOThread.start();
    }
    else{
      JOptionPane.showMessageDialog(this,
                                    "Can't load at this time,\n"
                                    + "currently busy with other IO",
                                    "Load Image",
                                    JOptionPane.WARNING_MESSAGE);
    }
  }

  /**
   * Salvar las instancias seleccionadas a un archivo con formato ARFF; lo hacemos
   * en el thread de I/O, con prioridad m�nima, no permitiendo m�s de una operaci�n
   * de I/O (salvo la de salvar la imagen) estar ejecut�ndse concurrentemente.
   *
   * @param f El archivo
   * @param inst Las instancias a salvar
   */
  protected synchronized void saveInstancesToFile(final File f, final Instances inst){
    //Comprobar que no estemos realizando ya otra operaci�n de I/O
    if (m_IOThread == null){
      m_IOThread = new Thread(){
        public void run(){
          //Evitamos cambiar las instancias mientras estamos salvando
          exitEditMode();
          try{
            //Comunicar a los mediadores que hemos empezado a salvar las instancias
            notifyMediators(INSTANCES_SAVE_START, f);

            //Abrimos el stream de salida al fichero
            Writer w = new BufferedWriter(new FileWriter(f));

            //Escribimos las instancias
            w.write(inst.toString());

            //Cerramos el stream de salida al fichero
            w.close();
          }
          catch (Exception ex){
            ex.printStackTrace();
          }
          finally{
            //Notificar que ya hemos acabado de salvar las instancias
            notifyMediators(INSTANCES_SAVE_FINISH, f);
            //Volvemos al modo de edici�n, del que ya ven�amos
            enterEditMode();
            //Podemos volver a ejecutar operaciones de I/O
            m_IOThread = null;
          }
        }
      };
      m_IOThread.setPriority(Thread.MIN_PRIORITY); //El interfaz debe tener mayor prioridad
      m_IOThread.start();
    }
    else{
      //No podemos hacerlo, pues ya estamos ocupados con otra operaci�n de I/O
      JOptionPane.showMessageDialog(this,
                                    "Can't save at this time,\n"
                                    + "currently busy with other IO",
                                    "Save Instances",
                                    JOptionPane.WARNING_MESSAGE);
    }
  }

  /**
   * Cargar las instancias desde un archivo con formato ARFF; lo hacemos
   * en el thread de I/O, con prioridad m�nima, no permitiendo m�s de una operaci�n
   * de I/O (salvo la de salvar la imagen) estar ejecut�ndse concurrentemente.
   *
   * @param f El archivo
   */
  public synchronized void loadInstancesFromFile(final File f){
    //Comprobar que no estemos realizando ya otra operaci�n de I/O
    if (m_IOThread == null){
      m_IOThread = new Thread(){
        public void run(){
          //Bandera que indica si el proceso ha acabado regularmente o ha fallado
          boolean isFailed = true;
          //Evitamos cambiar las instancias mientras estamos salvando
          exitEditMode();
          try{
            //Comunicar a los mediadores que hemos empezado a cargar las instancias
            notifyMediators(INSTANCES_LOAD_START, f);

            //Abrimos el stream de entrada asociado al fichero
            Reader r = new BufferedReader(new FileReader(f));

            //Volcamos las instancias del fichero a memoria principal
            Instances tmp = new Instances(r);
            tmp.setClassIndex(2);

            //Metemos las instancias en la base de datos
            m_Points.createFromInstances(tmp);

            //Cerramos el stream de entrada
            r.close();

            //Las operaciones de undo & redo ya no son vigentes
            flushRedoAndUndoBuffers();

            //Actualizamos el texto de los botones de selecci�n de color
            updateColorSelectionButtonsText();

            //Si las instancias no cumplen con el requisito de haber sido salvadas con
            //esta misma aplicaci�n, tratamos de reescalarlas para que aparezcan en pantalla
            //correctamente y hacemos que su nombre cumpla con los est�ndares
            //de la aplicaci�n (comience con "oaidtb_")
            //�Ojo!, desvirt�a los valores de las instancias
            if (m_ScaleAfterLoadCB.isSelected()) //&&!tmp.relationName.startsWit("oaidt_")
              scaleInstances(m_Points, m_DrawingPanel.getWidth(), m_DrawingPanel.getHeight());

            //Hemos conseguid cargar las instancias con �xito
            isFailed = false;
          }
          catch (Exception ex){
            JOptionPane.showMessageDialog(m_LoadARFFButton,
                                          "Couldn't read '"
                                          + f.getName() + "' as an arff file.\n"
                                          + "Reason:\n" + ex.getMessage(),
                                          "Load Instances",
                                          JOptionPane.ERROR_MESSAGE);
          }
          finally{
            //Notificar que ya hemos acabado de cargar las instancias
            if(isFailed)
              notifyMediators(INSTANCES_LOAD_FAIL, f);
            else
              notifyMediators(INSTANCES_LOAD_FINISH, f);
            //Volvemos al modo de edici�n, del que ya ven�amos
            enterEditMode();
            //Podemos volver a ejecutar operaciones de I/O
            m_IOThread = null;
          }
        }
      }; //RUN
      m_IOThread.setPriority(Thread.MIN_PRIORITY); // UI has most priority
      m_IOThread.start();
    }//(m_IOThread != null)
    else{
      JOptionPane.showMessageDialog(this,
                                    "Can't load at this time,\n"
                                    + "currently busy with other IO",
                                    "Load Instances",
                                    JOptionPane.WARNING_MESSAGE);
    }
  }

  /**
   * Traslada los puntos que corresponden a las instancias, de manera que
   * no haya coordenadas negativas, y las escala de manera
   * que dichas coordenadas est�n siempre en el rect�ngulo
   * (0, 0, w, h).
   *
   * @param instances Las instancias a escalar y trasladar
   * @param w La anchura del rect�ngulo
   * @param h La altura del rect�ngulo
   */
  private synchronized void scaleInstances(final Point2DInstances instances, final int w, final int h){

    if(w <=0 || h <= 0)
      return; //throw Exception

    double minXValue = Double.MAX_VALUE;
    double maxXValue = Double.MIN_VALUE;
    double minYValue = Double.MAX_VALUE;
    double maxYValue = Double.MIN_VALUE;

    //Buscamos los valores m�nimos y m�ximos de las coordenadas de las instancias
    for (int i = 0; i < instances.numInstances(); i++){
      Point2DInstances.Point2DInstance instance = (Point2DInstances.Point2DInstance) instances.instance(i);
      double x = instance.getX();
      if (x < minXValue)
        minXValue = x;
      if (x > maxXValue)
        maxXValue = x;
      double y = instance.getY();
      if (y < minYValue)
        minYValue = y;
      if (y > maxYValue)
        maxYValue = y;
    }

    //Calculamos el desplazamiento que sufrir�n los puntos para que no existan coordenadas negativas
    double xTranslate = minXValue < 0 ? -minXValue : 0;
    double yTranslate = minYValue < 0 ? -minYValue : 0;
    //Calculamos los factores de escalado
    double xScale = (double) (w - 10) / (maxXValue + xTranslate);
    double yScale = (double) (h - 10) / (maxYValue + yTranslate);

    //Trasladamos y escalamos todas las instancias
    for (int i = 0; i < instances.numInstances(); i++){
      Point2DInstances.Point2DInstance instance = (Point2DInstances.Point2DInstance) instances.instance(i);
      instance.setX(instance.getX() * xScale + xTranslate);
      instance.setY(instance.getY() * yScale + yTranslate);
    }
  }

  /**
   * Escala los puntos que corresponden a las instancias (construidas desde una imagen),
   * de manera que ocupen todo el rect�ngulo del panel de dibujado que se ve en pantalla
   * (0, 0, w, h).
   *
   * @param instances Las instancias a escalar y trasladar
   * @param w La anchura del rect�ngulo
   * @param h La altura del rect�ngulo
   * @param imgW La anchura de la imagen
   * @param imgH La altura de la imagen
   */
  private synchronized void scaleInstancesFromImage(final Point2DInstances instances,
                                                    final int w, final int h,
                                                    final int imgW, final int imgH){

    if(w <=0 || h <= 0)
      return; //throw Exception

    //Calculamos los factores de escalado
    double xScale = (double) w  / (double) imgW;
    double yScale = (double) h / (double) imgH;

    //Trasladamos y escalamos todas las instancias
    for (int i = 0; i < instances.numInstances(); i++){
      Point2DInstances.Point2DInstance instance = (Point2DInstances.Point2DInstance) instances.instance(i);
      instance.setX(instance.getX() * xScale);
      instance.setY(instance.getY() * yScale);
    }
  }

  /**
   * Distribuci�n de los componentes en el panel
   */
  private void layoutComponents(){

    setLayout(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();

    //Botones de selecci�n de colores.
    JPanel colorButtonsPanel = new JPanel(new GridLayout(1, 2));
    colorButtonsPanel.add(m_Color1Button);
    colorButtonsPanel.add(m_Color2Button);

    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    add(colorButtonsPanel, gbc);

    //Reset constraints
    gbc = new GridBagConstraints();

    //Modo de inserci�n
    JPanel addModePanel = new JPanel(new GridBagLayout());
    ButtonGroup bg = new ButtonGroup();
    bg.add(m_SinglePointAddCB);
    bg.add(m_MultiplePointAddCB);
    gbc.gridx = 0;
    gbc.gridy = 0;
    addModePanel.add(m_SinglePointAddCB, gbc);
    gbc.gridx = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    addModePanel.add(m_InsertModeCoB, gbc);
    JPanel distribPanel = new JPanel(new GridBagLayout());
    gbc = new GridBagConstraints();
    distribPanel.add(m_MultiplePointAddCB, gbc);
    gbc.gridx = 1;
    m_NumPointsToAddPerClickTF.setColumns(3);
    m_NumPointsToAddPerClickTF.setHorizontalAlignment(JTextField.RIGHT);
    distribPanel.add(m_NumPointsToAddPerClickTF);
    gbc.gridx = 2;
    distribPanel.add(new JLabel("points using"));
    gbc.gridx = 3;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    distribPanel.add(m_DistributionSelector);
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    addModePanel.add(distribPanel, gbc);
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    add(addModePanel, gbc);

    //Reset constraints
    gbc = new GridBagConstraints();

    //Botones de undo & redo
    JPanel undoPanel = new JPanel(new GridLayout(2, 2));
    undoPanel.add(m_UndoButton);
    undoPanel.add(m_RedoButton);
    undoPanel.add(m_FastEraseMode);
    undoPanel.add(m_FlushButton);

    undoPanel.setBorder(BorderFactory.createTitledBorder("Undo insertion"));

    gbc.gridx = 0;
    gbc.gridy = 2;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    add(undoPanel, gbc);

    //Reset constraints
    gbc = new GridBagConstraints();

    //Partici�n de las instancias en conjuntos de entrenamiento y test; reset
    JPanel splitPanel = new JPanel(new GridBagLayout());
    gbc.gridx = 0;
    gbc.gridy = 0;
    splitPanel.add(m_TrainPercentageLS.getSlider());
    gbc.gridx = 1;
    splitPanel.add(m_TrainPercentageLS.getLabel());
    JPanel butPan = new JPanel(new GridLayout(2, 2));
    butPan.add(m_SplitButton);
    butPan.add(m_JoinButton);
    butPan.add(m_UseOnlyInstancesInRegionCB);
    butPan.add(m_ResetButton);
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    splitPanel.add(butPan, gbc);
    gbc.gridx = 0;
    gbc.gridy = 3;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    add(splitPanel, gbc);

    //Reset constraints
    gbc = new GridBagConstraints();

    //Modo zoom
    JPanel zoomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
    zoomPanel.add(m_ZoomModeCB);
    zoomPanel.add(m_ZoomOutButton);
    gbc.gridx = 0;
    gbc.gridy = 5;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    add(zoomPanel, gbc);

    //Reset constraints
    gbc = new GridBagConstraints();

    //Botones de open & save
    JPanel openAndSavePanel = new JPanel(new GridLayout(3, 2));
    openAndSavePanel.add(m_LoadARFFButton);
    openAndSavePanel.add(m_SaveARFFButton);
    openAndSavePanel.add(m_ScaleAfterLoadCB);
    openAndSavePanel.add(new JPanel());
    openAndSavePanel.add(m_LoadImageButton);
    openAndSavePanel.add(m_SaveImageButton);
    gbc.gridx = 0;
    gbc.gridy = 6;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    add(openAndSavePanel, gbc);
  }

  /** @return El panel de dibujado que controla este panel de edici�n de instancias */
  public DrawingPanel getDrawingPanel(){
    return m_DrawingPanel;
  }

  //////////////////////////////////
  ////// UNDO & REDO FEATURES //////
  //////////////////////////////////

  /**
   * Informar que se han a�adido nuevos puntos mediante un click de rat�n
   *
   * @param number El n�mero de instancias a�adidas
   */
  private synchronized void newInsertionOp(int number){
    m_NumberOfInserted.add(new SimpleInteger(number));
    m_UndoButton.setEnabled(true);
    m_FlushButton.setEnabled(true);
  }

  /** @return El n�mero de instancias a�adidas con el �ltimo click de rat�n no deshecho*/
  private int numberOfLastInserted(){
    return ((SimpleInteger) m_NumberOfInserted.get(m_NumberOfInserted.size() - 1)).value;
  }

  /**
   * Deshace la �ltima inserci�n de instancias a trav�s del rat�n
   *
   * @throws Exception Si ocurre alg�n error al borrar las instancias de la base de datos
   */
  private synchronized void undoInsertion() throws Exception{
    //Comprobamos que haya algo que deshacer
    if (!m_NumberOfInserted.isEmpty()){
      //Borramos las instancias de la base de datos
      double[][] tmp = m_Points.removeLastPoints(numberOfLastInserted());
      //Guardamos las instancias para poder rehacer la operaci�n
      m_RedoBuffer.add(tmp);
      //Eliminamos esta operaci�n de undo del buffer
      m_NumberOfInserted.remove(m_NumberOfInserted.size() - 1);
      if (m_FastEraseMode.isSelected())
        //Vamos borrando una a una las instancias de la pantalla
        for (int i = 0; i < tmp.length; i++)
          m_DrawingPanel.erasePoint(tmp[i][0], tmp[i][1]);
      else{
        //Repintamos todas las instancias
        m_DrawingPanel.setUpdateInstances(true);
        m_DrawingPanel.repaint();
      }
    }
    //En caso de que sea el �ltimo elemento del buffer de undo, deshabilitamos la operaci�n
    if (m_NumberOfInserted.isEmpty())
      m_UndoButton.setEnabled(false);

    //Habilitar la acci�n de redo
    m_RedoButton.setEnabled(true);

    //Actualizar el texto de los botones de selecci�n de color
    updateColorSelectionButtonsText();
  }

  /** Rehace la �ltima inserci�n de instancias a trav�s del rat�n deshecha  */
  private synchronized void redoInsertion(){
    //Comprobamos que haya algo que deshacer
    if (!m_RedoBuffer.isEmpty()){
      //Accedemos las instancias que tenemos que volver a insertar
      double[][] opsToRedo = (double[][]) m_RedoBuffer.get(m_RedoBuffer.size() - 1);
      //Metemos cada punto en la base de datos y lo pintamos en la pantalla
      for (int i = opsToRedo.length - 1; i > -1; i--){
        Color c = new Color((int) opsToRedo[i][2]);
        m_Points.addPoint(opsToRedo[i][0], opsToRedo[i][1], c);
        m_DrawingPanel.drawInstance((int) opsToRedo[i][0], (int) opsToRedo[i][1], c);
      }
      //Podemos volver a deshacer la operaci�n rehecha
      newInsertionOp(opsToRedo.length);

      //Quitamos esta operaci�n del buffer de redo
      m_RedoBuffer.remove(m_RedoBuffer.size() - 1);
    }
    //Si el buffer de redo est� vac�o, deshabilitamos la operaci�n
    if (m_RedoBuffer.isEmpty())
      m_RedoButton.setEnabled(false);

    //Al menos hay una operaci�n que deshacer
    m_UndoButton.setEnabled(true);

    //Actualizamos el texto de los botones de selecci�n de color
    updateColorSelectionButtonsText();
  }

  /**
   * Libera la memoria reservada por las operaciones de undo & redo,
   * deshabilit�ndolas de este modo
   */
  private void flushRedoAndUndoBuffers(){
    m_NumberOfInserted = new ArrayList();
    m_RedoBuffer = new ArrayList();
    m_UndoButton.setEnabled(false);
    m_RedoButton.setEnabled(false);
  }

  //-----------------------------///////////////////////////////////
  //------------------ ARBITRABLE IMPLEMENTATION ///////////////////
  //-----------------------------///////////////////////////////////

  //Private field which will be used to implement the Arbitable interface.
  private Colleague m_Colleague = new Colleague(this);

  public Arbitrable getColleague(){
    return m_Colleague;
  }

  public void addMediator(Mediator mediator){
    m_Colleague.addMediator(mediator);
  }

  public void removeMediator(Mediator mediator){
    m_Colleague.removeMediator(mediator);
  }

  public void notifyMediators(int whatChanged, Object object){
    m_Colleague.notifyMediators(whatChanged, object);
  }

  public int numberOfMediators(){
    return m_Colleague.numberOfMediators();
  }

  // Change indicator constants
  /** Indicador de mensaje para los mediadores, acompa�ado de la base de datos de puntos */
  public final static int
    INSTANCES_SPLIT = 0,
    INSTANCES_JOIN = 1,
    INSTANCES_DELETED = 2;
  /**
   * Indicador de mensaje para los mediadores, acompa�ado del nombre del archivo, en caso
   * de ser una operaci�n de cagar/salvar una imagen, o del {@link File} utilizado,
   * en caso de ser una operaci�n de cargar/salvar un archvo ARFF
   */
  public final static int
    INSTANCES_LOAD_START = 20,
    INSTANCES_LOAD_FINISH = 21,
    INSTANCES_LOAD_FAIL = 22,
    INSTANCES_SAVE_START = 23,
    INSTANCES_SAVE_FINISH = 24;
}