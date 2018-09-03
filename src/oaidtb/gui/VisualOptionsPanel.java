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
 *    VisualOptionsPanel.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.gui;

import oaidtb.boosters.AdaBoostMH;
import oaidtb.boosters.Booster;
import oaidtb.boosters.IterativeUpdatableClassifier;
import oaidtb.misc.Utils;
import oaidtb.misc.guiUtils.LabeledSlider;
import oaidtb.misc.guiUtils.MyAbstractAction;
import oaidtb.misc.javaTutorial.SwingWorker;
import oaidtb.misc.mediator.Arbitrable;
import oaidtb.misc.mediator.Colleague;
import oaidtb.misc.mediator.Mediator;
import weka.classifiers.Classifier;
import weka.classifiers.DistributionClassifier;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SerializedObject;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * Panel en el que se muestran las opciones de visualización de un DrawingPanel; también permite
 * la construcción de imágenes que representen la hipótesis de un clasificador para mostrarlas
 * en él.
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.2 $
 */
public class VisualOptionsPanel extends JPanel implements Arbitrable{

  /** El panel de dibujado */
  private DrawingPanel m_DrawingPanel;

  /** El clasificador sobre el que se está trabajando actualmente */
  private Classifier m_Classifier;

  //Mostrar o no las instancias de entrenamiento y test, la imagen de hipótesis y la rejilla */
  /** Checkbox de mostrar o no las instancias de entrenamiento */
  private JCheckBox m_ShowTrainInstancesCB = new JCheckBox("Show train set", true);
  /** Checkbox de mostrar o no las instancias de prueba */
  private JCheckBox m_ShowTestInstancesCB = new JCheckBox("Show test set", true);
  /** Checkbox de mostrar o no la imagen de hipótesis */
  private JCheckBox m_ShowHypImageCB = new JCheckBox("Show hypothesis", true);
  /** Checkbox de mostrar o no la imagen de la rejilla */
  private JCheckBox m_ShowGridCB = new JCheckBox("Show grid", false);

  //Permitir la reutilización de las operaciones ya realizadas para construir la imagen de hipótesis
  //cuando estemos utilizando un Booster (no volver a hacer lo mismo).
  /** Checkbox para elejir guardar o no los resultados de los boosters */
  private JCheckBox m_StoreBoosterResultsCB = new JCheckBox("Store classifier results", true);
  /** Última iteración utilizada para construir la imagen de hipótesis de un booster */
  private int m_LastIterationUsed = -1;
  /** Controlar la configuración de la rejilla utilizada para construir la última imagen de hipótesis */
  private int m_LastGridWidth = -1, m_LastGridHeigt = -1;
  /** Controlar la última región para la que hemos creado una imagen de hipótesis */
  private Rectangle2D.Double m_LastRegion = new Rectangle2D.Double(0, 0, 0, 0);
  /** Los resultados del booster: [Índice de punto] --> [distribución de probabilidades de colores */
  private double[][] m_BoosterResults = null;
  /** Indica si el booster ha sido reiniciado o es otro */
  private boolean m_NewIterativeUpdatableClassifier = true;

  /** Etiqueta donde mostramos el clasificador de trabajo actual */
  private JLabel m_ClassifierLabel = new JLabel("Classifier: none");

  //Las imágenes (utilizando y no utilizando grados de confianza) que representan la hipótesis del clasificador
  /** Imagen que representa la última hipótesis construida utilizando grados de confianza */
  private BufferedImage m_HypothesisSumImage = null;
  /** Imagen que representa la última hipótesis construida */
  private BufferedImage m_HypothesisImage = null;

  /** Configurar el porcentaje del color original que será utilizado para representarlo en las imágenes de hipótesis */
  private LabeledSlider m_ColorTransformSlider = new LabeledSlider("Original color percentage: ");
  /** El porcentaje del color original que será utilizado para representarlo en las imágenes de hipótesis */
  private double m_ColorTransform = 0.5;

  /** Utilizar (si están disponibles) los grados de confianza del clasificador para construir la imagen de hipótesis */
  protected JCheckBox m_UseConfidenceLevelsCB = new JCheckBox("Use confidence levels");

  //Hacer transiciones suaves de color (gradiente) entre los cuadros de la rejilla
  //(puede desvirtuar la fidelidad de la imagen)
  //TODO private JCheckBox m_UseGradientColors = new JCheckBox();

  /** Controlar el tamaño de los puntos que representan a las instancias */
  private LabeledSlider m_PointSize = new LabeledSlider("Point size: ");

  /** El panel que controla el aspecto de la rejilla */
  private GridDimensionOptionsPanel m_Gdop = new GridDimensionOptionsPanel();

  //Botones de repintado y creación de la imagen de hipótesis
  /** Botón de repintado del panel de dibujado */
  private JButton m_RepaintButton = new JButton("Repaint");

  /** Botón de creación de la imagen de hipótesis */
  private JButton m_RefreshButton = new JButton("Refresh");
  /** Botón de cancelación de la creación de la imagen de hipótesis */
  private JButton m_CancelRefreshButton = new JButton("Cancel");

  /** Selección de un clasificador base de un booster */
  private JComboBox m_BaseClassifierSelector = new JComboBox();
  /** Creación de la imagen de hipótesis de un clasificador base de un booster */
  private JButton m_RefreshWithBaseClassifierButton = new JButton("View BC Hypothesis");

  //Used to construct the hypothesis image
  //We need to store them because we allow m_Points to change between classifier constructions
  /** Los colores que representa cada clase del esquema de instancias que usa el clasificador actual */
  private Color[] m_ColorsUsedByCurrentClassifier = null;
  /**
   *  El esquema de las instancias que utiliza el clasificador actual, necesario para el proceso de construcción
   *  de la imagen de hipótesis
   */
  private Instances m_InstancesSchemeUsedByCurrentClassifier = null;

  /** En el thread de despacho de eventos hacemos el trabajo de construcción de la imagen de hipótesis */
  private SwingWorker m_RefreshWorker = null;

  /** Barra que muestra el estado del proceso de construcción de la imagen de hipótesis */
  private JProgressBar m_ProgressBar = new JProgressBar();

  //////////////////////
  //Acciones accesibles desde el exterior
  //////////////////////

  /** Repintar el panel de dibujado */
  private MyAbstractAction m_RepaintAction;

  /** Crear la imagen de hipótesis */
  private MyAbstractAction m_RefreshAction;

  /** Crear la imagen de hipótesis de un clasificador base (si el clasificador principal es un booster) */
  private MyAbstractAction m_RefreshBCAction;

  /** Cancelar la creación de la imagen de hipótesis */
  private MyAbstractAction m_CancelRefreshAction;

  /** Mostrar o no la rejilla */
  private MyAbstractAction m_ShowGridAction;

  /** Mostrar o no las instancias de entrenamiento */
  private MyAbstractAction m_ShowTrainAction;

  /** Mostrar o no las instancias de test */
  private MyAbstractAction m_ShowTestAction;

  /** Mostrar o no la imagen de hipótesis */
  private MyAbstractAction m_ShowHypothesisAction;

  /** Utilizar o no grados de confianza ( si el clasificador lo permite) en la imagen de hipótesis */
  private MyAbstractAction m_UseConfidenceLevelsAction;

  /**
   * Constructor
   *
   * @param drawingPanel El {@link DrawingPanel} sobre el que se basará este panel de opciones
   */
  public VisualOptionsPanel(DrawingPanel drawingPanel){

    m_DrawingPanel = drawingPanel;

    //Configuración de máximos de la rejilla
    m_Gdop.setOriginalXDimensionSize(m_DrawingPanel.getWidth());
    m_Gdop.setOriginalYDimensionSize(m_DrawingPanel.getHeight());

    //Hacer concordar las CheckBoxes con el estado del panel de dibujo
    m_ShowGridCB.setSelected(m_DrawingPanel.getShowGrid());
    m_ShowHypImageCB.setSelected(m_DrawingPanel.getShowHypothesisImage());
    m_ShowTrainInstancesCB.setSelected(m_DrawingPanel.getShowTrainInstances());
    m_ShowTestInstancesCB.setSelected(m_DrawingPanel.getShowTestInstances());

    //Color inicial de la etiqueta que muestra el clasificador utilizado
    m_ClassifierLabel.setForeground(Color.red);

    //Crear las acciones
    setUpActions(getInputMap(WHEN_IN_FOCUSED_WINDOW), getActionMap());

    //Inicializar el comportamiento y el aspecto del panel
    initGUI();

    //Posicionar los componentes sobre el panel
    layoutComponents();
  }

  ///////////////
  // Métodos de acceso a las acciones
  ///////////////
  /**
   * La acción de repintado actualiza la información del panel de dibujado con
   * la configuración actual del panel de opciones visuales y lo "repinta"
   * (actualiza la imagen de instancias y la de hipótesis, pero no la de
   * la rejilla).
   *
   * @return la acción de repintado
   */
  public MyAbstractAction getRepaintAction(){
    return m_RepaintAction;
  }

  /** @return La acción de crear la imagen de hipótesis */
  public MyAbstractAction getRefreshAction(){
    return m_RefreshAction;
  }

  /** @return La acción de cancelar la creación de la imagen de hipótesis */
  public MyAbstractAction getCancelRefreshAction(){
    return m_CancelRefreshAction;
  }

  /** @return La acción para mostrar o no la rejilla */
  public MyAbstractAction getShowGridAction(){
    return m_ShowGridAction;
  }

  /** @return La acción para mostrar o no la imagen de hipótesis */
  public MyAbstractAction getShowHypothesisAction(){
    return m_ShowHypothesisAction;
  }

  /** @return La acción para mostrar o no las instancias de prueba */
  public MyAbstractAction getShowTestAction(){
    return m_ShowTestAction;
  }

  /** @return La acción para mostrar o no las instancias de entrenamiento */
  public MyAbstractAction getShowTrainAction(){
    return m_ShowTrainAction;
  }

  /** @return La acción para usar o no los grados de confianza del clasificador */
  public MyAbstractAction getUseConfidenceLevelsAction(){
    return m_UseConfidenceLevelsAction;
  }

  /** @return El clasificador sobre el que se está trabajando actualmente */
  public Classifier getClassifier(){
    return m_Classifier;
  }

  /**
   * Configura el panel para trabajar con un nuevo clasificador (habilitar /deshabilitar
   * botones etc.) y restaura el panel de dibujado.
   *
   * @param classifier El nuevo clasificador
   * @param isRebuilt true si el clasificador es el mismo que ya estaba configurado, pero ha sido reconstruido
   */
  public void setClassifier(Classifier classifier,
                            Instances instancesScheme,
                            Color[] colors,
                            boolean isRebuilt){

    if (classifier == null)
      return; //lanzar una excepción

    //Borramos el dibujo de la hipótesis (si existiera) e informamos de que el clasificador es nuevo
    if (!classifier.equals(m_Classifier) || isRebuilt)
      reset();

    //Actualizamos el clasificador
    m_Classifier = classifier;

    //Preparamos la construcción de la imagen de hipótesis
    m_InstancesSchemeUsedByCurrentClassifier = instancesScheme;
    m_ColorsUsedByCurrentClassifier = colors;

    if (m_InstancesSchemeUsedByCurrentClassifier == null ||
      m_ColorsUsedByCurrentClassifier == null ||
      m_ColorsUsedByCurrentClassifier.length != m_InstancesSchemeUsedByCurrentClassifier.numClasses())
      return;  //lanzar una excepción

    //Configuramos la (des)habilitación de acciones
    enableRefreshAction();

    //Colocamos el texto de la etiqueta del clasificador
    m_ClassifierLabel.setText("Classifier: " + m_Classifier.getClass().getName());

    //Comprobar si el clasificador puede o no clasificar el formato de instancias necesario
    Instance instance = new Instance(0, new double[]{0, 0, 0});
    instance.setDataset(m_InstancesSchemeUsedByCurrentClassifier);
    if (Utils.classifierCanClassify(m_Classifier, instance))
      m_ClassifierLabel.setForeground(Color.green);
    else
      m_ClassifierLabel.setForeground(Color.red);
  }

  /** Método que "resetea" el aspecto del panel de dibujado */
  public void reset(){

    //Las iteraciones guardadas no nos sirven
    m_NewIterativeUpdatableClassifier = true;

    //Reseteamos el aspecto del panel de dibujado
    m_HypothesisImage = null;
    m_HypothesisSumImage = null;
    m_DrawingPanel.setGridColumnWidth(0);
    m_DrawingPanel.setHypothesisImage(null);
    m_DrawingPanel.repaint();
  }

  /** @return The current hypothesis image  */
  public BufferedImage getHypothesisImage(){
    return m_HypothesisImage;
  }

  /** @param hypothesisImage The image to be used as the hypothesis image */
  public void setHypothesisImage(BufferedImage hypothesisImage){
    m_HypothesisImage = hypothesisImage;
  }

  /** @return The current hypothesis (using confidence levels) image  */
  public BufferedImage getHypothesisSumImage(){
    return m_HypothesisSumImage;
  }

  /** @param hypothesisSumImage The image to be used as the hypothesis (using confidence levels) image */
  public void setHypothesisSumImage(BufferedImage hypothesisSumImage){
    m_HypothesisSumImage = hypothesisSumImage;
  }

  /**
   * Informar de que el {@link IterativeUpdatableClassifier} actual ha realizado nuevas
   * iteraciones
   */
  public void newIterationsMade(){
    IterativeUpdatableClassifier c = (IterativeUpdatableClassifier) m_Classifier;
    int currentItemIndex = m_BaseClassifierSelector.getSelectedIndex();

    //Más lento, pero seguro ante cambios como por ejemplo que se cargue un
    //booster dede un archivo con iteraciones ya realizadas
    m_BaseClassifierSelector.removeAllItems();

    //Las añadimos a la lista de iteraciones
    for (int i = m_BaseClassifierSelector.getItemCount(); i < c.getNumIterationsPerformed(); i++)
      m_BaseClassifierSelector.addItem("Iteration " + String.valueOf(i));

    if (currentItemIndex != -1)
      m_BaseClassifierSelector.setSelectedIndex(currentItemIndex);
  }

  /**
   * Método que centraliza la (des)habilitación de las acciones de creación
   * de las imágenes de hipótesis
   */
  private void enableRefreshAction(){
    if (m_RefreshWorker == null){  //Si ya se está creando, no hacer nada

      if (m_Classifier == null){
        m_RefreshAction.setEnabled(false);
        m_RefreshBCAction.setEnabled(false);
        m_CancelRefreshAction.setEnabled(false);
        return;
      }

      //Comprobar si el clasificador puede o no clasificar el formato de instancias necesario
      Instance instance = new Instance(0, new double[]{0, 0, 0});
      instance.setDataset(m_InstancesSchemeUsedByCurrentClassifier);
      m_RefreshAction.setEnabled(Utils.classifierCanClassify(m_Classifier, instance));

      //Pase lo que pase, si no estamos haciendo algo no podemos parar de hacerlo...
      m_CancelRefreshAction.setEnabled(false);

      //Comprobar si es un booster
      if (m_Classifier instanceof IterativeUpdatableClassifier){
        //Comprobar si ya ha realizado alguna iteración
        if (((IterativeUpdatableClassifier) m_Classifier).getNumIterationsPerformed() != 0){
//          m_BaseClassifierSelector.removeAllItems();
          newIterationsMade();
          m_RefreshBCAction.setEnabled(true);
          m_BaseClassifierSelector.setEnabled(true);
        }
        else{
          m_BaseClassifierSelector.removeAllItems();
          m_RefreshBCAction.setEnabled(false);
          m_BaseClassifierSelector.setEnabled(false);
        }
      }
      else{
        m_BaseClassifierSelector.removeAllItems();
        m_RefreshBCAction.setEnabled(false);
        m_BaseClassifierSelector.setEnabled(false);
      }
    }
  }

  /**
   * Configurar las acciones definidas; por defecto, las metemos en el InputMap
   * de WHEN_IN_FOCUSED_WINDOW
   *
   * @param im Un InputMap donde las teclas serán mapeadas a los nombres de las acciones
   * @param am Un ActionMap donde los nombres de las acciones serán mapeados a las acciones
   */
  public void setUpActions(final InputMap im, final ActionMap am){

    ////////////////////////////////////////////////
    //Utilizar o no grados de confianza  (Alt-L)
    ////////////////////////////////////////////////
    m_UseConfidenceLevelsAction = new MyAbstractAction("Use_Confidence_Levels"){
      public void actionPerformed(ActionEvent e){
        if (e.getSource() != m_UseConfidenceLevelsCB)
          m_UseConfidenceLevelsCB.setSelected(!m_UseConfidenceLevelsCB.isSelected());

        if (m_HypothesisSumImage != null){

          if (m_UseConfidenceLevelsCB.isSelected())
            m_DrawingPanel.setHypothesisImage(m_HypothesisSumImage);
          else
            m_DrawingPanel.setHypothesisImage(m_HypothesisImage);

          m_DrawingPanel.repaint();
        }
      }
    };
    m_UseConfidenceLevelsAction.setToolTipText("Utilizar grados de confianza" + " (Alt-L)");
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_L, Event.ALT_MASK), "Use_Confidence_Levels");
    am.put("Use_Confidence_Levels", m_UseConfidenceLevelsAction);

    //////////////////////////////////////////////////
    //Reconstruir la imagen de hipótesis   (Alt-R)
    //////////////////////////////////////////////////
    m_RefreshAction = new MyAbstractAction("Refresh_Hypothesis_Image"){
      public void actionPerformed(ActionEvent e){
        if (!m_RefreshAction.isEnabled())
          return;
        try{
          m_RefreshWorker = new HypothesisImageConstructorWorker(m_Classifier);
        }
        catch (Exception ex){
          System.err.println("Serialization error: " + e.toString());
          return;
        }
        m_RefreshAction.setEnabled(false);     //Evitar estar creando concurrentemente más de una imagen
        m_RefreshBCAction.setEnabled(false);
        m_CancelRefreshAction.setEnabled(true);
        m_RefreshWorker.start();
      }
    };
    m_RefreshAction.setToolTipText("Reconstruir la imagen de hipótesis" + " (Alt-R)");
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, Event.ALT_MASK), "Refresh_Hypothesis_Image");
    am.put("Refresh_Hypothesis_Image", m_RefreshAction);

    /////////////////////////////////////////////////////
    //Cancelar la reconstrucción la imagen de hipótesis (Ctrl-R)
    /////////////////////////////////////////////////////
    m_CancelRefreshAction = new MyAbstractAction("Cancel_Hypothesis_Image_Refresh"){
      public void actionPerformed(ActionEvent e){
        m_RefreshWorker.interrupt();
        m_RefreshWorker = null;       //Marcamos que ya no estamos creando una imagen de hipótesis
        enableRefreshAction();
      }
    };
    m_CancelRefreshAction.setToolTipText("Cancelar la reconstrucción de la imagen de hipótesis" + " (Ctrl-R)");
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, Event.CTRL_MASK), "Cancel_Hypothesis_Image_Refresh");
    am.put("Cancel_Hypothesis_Image_Refresh", m_CancelRefreshAction);

    ////////////////////////////////////////////////////////////////////////
    //Reconstruir la imagen de hipótesis de un clasificador base   (Ctrl-B)
    ///////////////////////////////////////////////////////////////////////
    //TODO: Keep & use a backup of the combined hypothesis image if it exists
    m_RefreshBCAction = new MyAbstractAction("Refresh_BC_Hypothesis_Image"){
      public void actionPerformed(ActionEvent e){
        try{
          int classifierIndex = m_BaseClassifierSelector.getSelectedIndex();
          if (m_Classifier instanceof Booster){
            final Classifier tmp = m_Classifier;
            //Marcamos que el clasificador a utilizar será el clasificador base
            m_Classifier = ((Booster) m_Classifier).getClassifier(classifierIndex);
            m_RefreshAction.actionPerformed(null);
            //Dejamos las cosas como estaban
            m_Classifier = tmp;
          }
          else if (m_Classifier instanceof AdaBoostMH){
            final Classifier tmp = m_Classifier;
            //Marcamos que el clasificador a utilizar será el clasificador base
            m_Classifier = ((AdaBoostMH) m_Classifier).getClassifier(classifierIndex);
            m_RefreshAction.actionPerformed(null);
            //Dejamos las cosas como estaban
            m_Classifier = tmp;
          }
        }
        catch (Exception ex){
          System.err.println(ex.toString());
        }
      }
    };
    m_RefreshBCAction.setToolTipText("Reconstruir la imagen de hipótesis de un clasificador base" + " (Ctrl-B)");
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_B, Event.CTRL_MASK), "Refresh_BC_Hypothesis_Image");
    am.put("Refresh_BC_Hypothesis_Image", m_RefreshBCAction);

    /////////////////////////////////////////////////////
    //Repintar el panel de dibujo (Ctrl-Alt-R)
    /////////////////////////////////////////////////////
    m_RepaintAction = new MyAbstractAction("Repaint_Drawing_Panel"){
      public void actionPerformed(ActionEvent e){
        m_DrawingPanel.setUpdateInstances(true);
//        m_DrawingPanel.setUpdateGrid(true);
        if (m_UseConfidenceLevelsCB.isSelected() && m_HypothesisSumImage != null)
          m_DrawingPanel.setHypothesisImage(m_HypothesisSumImage);
        else
          m_DrawingPanel.setHypothesisImage(m_HypothesisImage);
        m_DrawingPanel.repaint();
      }
    };
    m_RepaintAction.setToolTipText("Repintar el panel de dibujo" + " (Ctrl-Alt-R)");
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, Event.CTRL_MASK | KeyEvent.ALT_MASK), "Repaint_Drawing_Panel");
    am.put("Repaint_Drawing_Panel", m_RepaintAction);

    //////////////////////////////////////////////////////////
    //Mostrar o no las instancias de entrenamiento (Alt-T)
    //////////////////////////////////////////////////////////
    m_ShowTrainAction = new MyAbstractAction("Show_Train_Instances"){
      public void actionPerformed(ActionEvent e){
        if (e.getSource() != m_ShowTrainInstancesCB)
          m_ShowTrainInstancesCB.setSelected(!m_ShowTrainInstancesCB.isSelected());
        m_DrawingPanel.setShowTrainInstances(m_ShowTrainInstancesCB.isSelected());
        m_DrawingPanel.setUpdateInstances(true);
        m_DrawingPanel.repaint();
      }
    };
    m_ShowTrainAction.setToolTipText("Mostrar las instancias de entrenamiento" + " (Alt-T)");
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.ALT_MASK), "Show_Train_Instances");
    am.put("Show_Train_Instances", m_ShowTrainAction);

    //////////////////////////////////////////////////////////
    //Mostrar o no las instancias de test (Ctrl-T)
    //////////////////////////////////////////////////////////
    m_ShowTestAction = new MyAbstractAction("Show_Test_Instances"){
      public void actionPerformed(ActionEvent e){
        if (e.getSource() != m_ShowTestInstancesCB)
          m_ShowTestInstancesCB.setSelected(!m_ShowTestInstancesCB.isSelected());
        m_DrawingPanel.setShowTestInstances(m_ShowTestInstancesCB.isSelected());
        m_DrawingPanel.setUpdateInstances(true);
        m_DrawingPanel.repaint();
      }
    };
    m_ShowTestAction.setToolTipText("Mostrar las instancias de test" + " (Ctrl-T)");
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.CTRL_MASK), "Show_Test_Instances");
    am.put("Show_Test_Instances", m_ShowTestAction);

    //////////////////////////////////////////////////////////
    //Mostrar o no la rejilla (Alt-G)
    //////////////////////////////////////////////////////////
    m_ShowGridAction = new MyAbstractAction("Show_Grid"){
      public void actionPerformed(ActionEvent e){
        if (e.getSource() != m_ShowGridCB)
          m_ShowGridCB.setSelected(!m_ShowGridCB.isSelected());
        m_DrawingPanel.setShowGrid(m_ShowGridCB.isSelected());
        m_DrawingPanel.repaint();
      }
    };
    m_ShowGridAction.setToolTipText("Mostrar la rejilla" + " (Alt-G)");
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_G, KeyEvent.ALT_MASK), "Show_Grid");
    am.put("Show_Grid", m_ShowGridAction);

    //////////////////////////////////////////////////////////
    //Mostrar o no la imagen de hipótesis (Alt-H)
    //////////////////////////////////////////////////////////
    m_ShowHypothesisAction = new MyAbstractAction("Show_Hipothesis"){
      public void actionPerformed(ActionEvent e){
        if (e.getSource() != m_ShowHypImageCB)
          m_ShowHypImageCB.setSelected(!m_ShowHypImageCB.isSelected());
        m_DrawingPanel.setShowHypothesisImage(m_ShowHypImageCB.isSelected());
        m_DrawingPanel.repaint();
      }
    };
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_H, KeyEvent.ALT_MASK), "Show_Hipothesis");
    m_ShowHypothesisAction.setToolTipText("Mostrar la imagen de hipótesis" + " (Alt-H)");
    am.put("Show_Hipothesis", m_ShowHypothesisAction);
  }

  /**
   * Configuración del panel (asociación de acciones con botones, creación de marcos,
   * creación de listeners para las acciones no definidas como {@link Action} etc.)
   */
  private void initGUI(){

    //Botón de repintado
    m_RepaintButton.addActionListener(m_RepaintAction);
    m_RepaintButton.setToolTipText(m_RepaintAction.getTip());
    m_RepaintAction.addStateDependantComponent(m_RepaintButton);

    //Botones de reconstrucción (y cancelación) de la imagen de hipótesis
    m_RefreshButton.addActionListener(m_RefreshAction);
    m_RefreshButton.setToolTipText(m_RefreshAction.getTip());
    m_RefreshAction.addStateDependantComponent(m_RefreshButton);
    m_CancelRefreshButton.addActionListener(m_CancelRefreshAction);
    m_CancelRefreshButton.setToolTipText(m_CancelRefreshAction.getTip());
    m_CancelRefreshAction.addStateDependantComponent(m_CancelRefreshButton);
    //Selección de un clasificador base
    m_RefreshWithBaseClassifierButton.addActionListener(m_RefreshBCAction);
    m_RefreshWithBaseClassifierButton.setToolTipText(m_RefreshBCAction.getTip());
    m_RefreshBCAction.addStateDependantComponent(m_RefreshWithBaseClassifierButton);
    m_BaseClassifierSelector.setToolTipText("Seleccionar el índice del clasificador base");
    enableRefreshAction();

    //CheckBox de utilización de grados de confianza
    m_UseConfidenceLevelsCB.addActionListener(m_UseConfidenceLevelsAction);
    m_UseConfidenceLevelsCB.setToolTipText(m_UseConfidenceLevelsAction.getTip());

    //CheckBox para elegir entre mostrar o no los diversos elementos en el panel de dibujo
    m_ShowTrainInstancesCB.addActionListener(m_ShowTrainAction);
    m_ShowTrainInstancesCB.setToolTipText(m_ShowTrainAction.getTip());
    m_ShowTestInstancesCB.addActionListener(m_ShowTestAction);
    m_ShowTestInstancesCB.setToolTipText(m_ShowTestAction.getTip());
    m_ShowGridCB.addActionListener(m_ShowGridAction);
    m_ShowGridCB.setToolTipText(m_ShowGridAction.getTip());
    m_ShowHypImageCB.addActionListener(m_ShowHypothesisAction);
    m_ShowHypImageCB.setToolTipText(m_ShowHypothesisAction.getTip());

    //JSlider de configuración del tamaño de los puntos
    final JSlider slider = m_PointSize.getSlider();
    slider.setToolTipText("Tamaño de los puntos");
    slider.setMinimum(1);
    slider.setMaximum(12);
    slider.setValue(8);
    m_DrawingPanel.setPointSize(slider.getValue());
    slider.addChangeListener(new ChangeListener(){
      public void stateChanged(ChangeEvent e){
        if (slider.getValue() != m_DrawingPanel.getPointSize())
          m_DrawingPanel.setPointSize(slider.getValue());
        if (!slider.getValueIsAdjusting())
          m_DrawingPanel.repaint();
      }
    });

    //Selección del porcentaje del color original a utilizar cuando se construya la imagen de hipótesis
    //TODO: Rápido cambio (basado en la imagen actual, sin volver a clasificar; ImageTransform...
    final JSlider ctSlider = m_ColorTransformSlider.getSlider();
    ctSlider.setMaximum(100);
    ctSlider.setMinimum(35);
    ctSlider.setValue((int) (m_ColorTransform * 100));
    ctSlider.addChangeListener(new ChangeListener(){
      public void stateChanged(ChangeEvent e){
        m_ColorTransform = ((double) ctSlider.getValue()) / 100;
      }
    });
    ctSlider.setToolTipText("Porcentaje del color original en la imagen de hipótesis");

    //Cada vez que cambiamos el tamaño del panel de dibujado debemos informar al panel de configuración de la rejilla
    m_DrawingPanel.addComponentListener(new ComponentAdapter(){
      public void componentResized(ComponentEvent e){
        m_Gdop.setOriginalXDimensionSize(m_DrawingPanel.getWidth());
        m_Gdop.setOriginalYDimensionSize(m_DrawingPanel.getHeight());
      }
    });
  }

  /**
   * Distribución de los componentes en el contenedor
   */
  private void layoutComponents(){

    setLayout(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();

    //Configuración de dibujado
    JPanel showCfgPanel = new JPanel(new GridLayout(0, 1));
    showCfgPanel.add(m_UseConfidenceLevelsCB);
    showCfgPanel.add(m_ShowTrainInstancesCB);
    showCfgPanel.add(m_ShowTestInstancesCB);
    showCfgPanel.add(m_ShowHypImageCB);
    showCfgPanel.add(m_ShowGridCB);
    gbc.gridx = 0;
    gbc.gridy = 0;
    add(showCfgPanel, gbc);

    //Configuración de la rejilla
    gbc.gridx = 1;
    gbc.gridy = 0;
    add(m_Gdop, gbc);

    //Botón de repintado
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(m_RepaintButton, gbc);

    //Tamaño de los puntos
    JPanel psPanel = new JPanel(new GridLayout(2, 1));
    psPanel.add(m_PointSize.getLabel());
    JSlider slider = m_PointSize.getSlider();
    psPanel.add(slider);
    gbc.gridx = 1;
    gbc.gridy = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    add(psPanel, gbc);

    //Reset constraints
    gbc = new GridBagConstraints();

    //Botones de refresco & configuración de la transformación del color
    JPanel ctPanel = new JPanel(new GridLayout(2, 1));
    ctPanel.add(m_ColorTransformSlider.getLabel());
    ctPanel.add(m_ColorTransformSlider.getSlider());
    JPanel refpanel = new JPanel(new GridLayout(1, 2));
    refpanel.add(m_RefreshButton);
    refpanel.add(m_CancelRefreshButton);
    JPanel bcSelPanel = new JPanel(new GridLayout(1, 2));
    bcSelPanel.add(m_RefreshWithBaseClassifierButton);
    bcSelPanel.add(m_BaseClassifierSelector);
    JPanel ctYrefPanel = new JPanel(new GridLayout(3, 1));
    ctYrefPanel.add(ctPanel);
    ctYrefPanel.add(refpanel);
    ctYrefPanel.add(bcSelPanel);
    ctYrefPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Image build setup"),
                                                             BorderFactory.createEmptyBorder(0, 5, 5, 5)));
    gbc.gridx = 0;
    gbc.gridy = 4;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    add(ctYrefPanel, gbc);

    //Reset constraints
    gbc = new GridBagConstraints();

    //Barra de progreso
    JPanel pbPanel = new JPanel(new BorderLayout());
    //Necesario para que no cambie la altura según se pinte o no la etiqueta
    m_ProgressBar.setStringPainted(true);
    m_ProgressBar.setPreferredSize(m_ProgressBar.getPreferredSize());
    m_ProgressBar.setMinimumSize(m_ProgressBar.getPreferredSize());
    m_ProgressBar.setStringPainted(false);
    m_ProgressBar.setToolTipText("Progreso de la constucción de la imagen de hipótesis");
    pbPanel.add(m_ProgressBar, BorderLayout.NORTH);
    pbPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Image build progress"),
                                                         BorderFactory.createEmptyBorder(0, 5, 5, 5)));
    gbc.gridx = 0;
    gbc.gridy = 5;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    add(pbPanel, gbc);

    //Etiqueta del clasificador
    gbc.gridx = 0;
    gbc.gridy = 6;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.anchor = GridBagConstraints.CENTER;
    add(m_ClassifierLabel, gbc);
  }

  /**
   * Return a valid color component (8 bits, [0-255]) for alpha, red, green and blue;
   * neccesary because of round errors.
   *
   * @param component The component value to validate
   * @return A value between 0 and 255
   */
  private int validColorComponent(int component){
    if (component < 0)
      component = 0;
    if (component > 255)
      component = 255;

    return component;
  }

  /**
   * Actualiza el estado de la barra de progreso.
   *
   * @param i El número de iteración en que se encuentra el proceso de construcción de la imagen de hipótesis
   */
  private void updatePBStatus(final int i){
    Runnable doSetProgressBarValue = new Runnable(){
      public void run(){
        m_ProgressBar.setValue(i);
      }
    };
    SwingUtilities.invokeLater(doSetProgressBarValue);
  }

  /**
   * Clase que implementa un SwingWorker para construir la imagen de la hipótesis
   * en el hilo de despacho de eventos; ver
   * <a href="http://java.sun.com/products/jfc/tsc/articles/threads/threads1.html#single_thread_rule"></a>
   */
  private class HypothesisImageConstructorWorker extends SwingWorker{

    //El clasificador puede cambiar durante el proceso de construcción, así que guardamos
    //una referencia al que vamos a utilizar para construir la imagen; mejor que hacerlo sincronizado.
    volatile Classifier c;

    /**
     * Constructor.
     * Precaución, el clasificador no debe ser modificado externamente; mejor que bloquearlo
     * o utilizar otros artefactos más complicados, lo copiamos.
     *
     * @param classifier El clasificador,
     *
     * @throws Exception Si ocurre un error de serialización
     */
    public HypothesisImageConstructorWorker(Classifier classifier) throws Exception{
      // Hacemos una copia para permitir a otros objetos seguir utilizando el
      // antiguo clasificador.
      SerializedObject so = new SerializedObject(classifier);
      c = (Classifier) so.getObject();
    }

    /**
     * Método en el que se crea la imagen
     *
     * @return La imagen o null si no ha sido creada
     */
    public Object construct(){

      try{

        //Notificar a los mediadores que ha comenzado la construcción de la imagen
        notifyMediators(HYPOTHESIS_IMAGE_START, c);

        //Elegimos la manera de crearla, dependiendo del tipo de clasificador que estemos utilizando
        if (c instanceof IterativeUpdatableClassifier && m_StoreBoosterResultsCB.isSelected())
          createHypothesisSumImage((IterativeUpdatableClassifier) c);
        else if (c instanceof DistributionClassifier)
          createHypothesisSumImage((DistributionClassifier) c);
        else
          createHypothesisImage(c);

        //Notificar a los mediadores que ha finalizado con éxito la construcción de la imagen
        notifyMediators(HYPOTHESIS_IMAGE_FINISH, c);

        //Retornamos la imagen creada
        if (m_UseConfidenceLevelsCB.isSelected() && m_HypothesisSumImage != null)
          return m_HypothesisSumImage;
        return m_HypothesisImage;
      }
      catch (InterruptedException ex){
        //Se ha cancelado la creación por parte del usuario
        notifyMediators(HYPOTHESIS_IMAGE_CANCELED, c);
        return null;
      }
      catch (Exception ex){
        //Ha habido un error
        notifyMediators(HYPOTHESIS_IMAGE_CANCELED, c);
        System.err.println(ex.toString());
        return null;
      }
    }

    /** Código que ejecuta el SwingWorker tras retornar de construct */
    public void finished(){
      //Reseteamos la barra de progreso
      m_ProgressBar.setStringPainted(false);
      updatePBStatus(0);

      //Si ha habido éxito, mandamos la imagen al panel de dibujo
      if (get() != null){
        m_DrawingPanel.setHypothesisImage((BufferedImage) get());
        m_DrawingPanel.repaint();
      }

      //Marcamos que ya no estamos creando la imagen y podemos empezar a crear otra
      m_RefreshWorker = null;

      //(Des)habilitamos las acciones pertinentes
      enableRefreshAction();
    }
  }//FIN de HypothesisImageConstructorWorker

  /////////////////////////////////////////////////////////////
  ///////////Funciones para crear las imágenes de hipótesis
  /////////////////////////////////////////////////////////////

  /*
    ¡Ojo!, sería mejor crear funciones más rápidas para el caso de que la rejilla tuviera un
    tamaño de celda muy pequeña, por ejemplo, que solo se pinten pixeles individuales y luego escalarlas,
    como en la primera versión del panel

    ¡Ojo!; en el caso de que exista peligro de que las instancias cambien, se
    debe crear un monitor sobre ellas. Sólo es peligroso si se borra una clase de las
    instancias antes de empezar a clasificar (con el sistema actual, prácticamente imposible
    a menos de que el usuario sea superman o vaya muy lento; otra opción es crear una copia
    y pasarla (sólo se necesita la información de la estructura y los colores que representan cada clase
  */

  /**
   * Método que permite crear una imagen que represente la hipótesis de un clasificador
   * sobre el panel de dibujado; la construimos creando una rejilla (utilizando
   * la configuración de esta) en la que cada celda será pintada del color que
   * el clasificador predizca para su punto intermedio. Respeta el zoom que pueda existir
   * en el panel de dibujado.
   *
   * No usa grados de confianza en la predicción
   *
   * @param classifier El clasificador del que representaremos la hipótesis
   *
   * @throws InterruptedException Si el usuario cancela la creación de la imagen
   * @throws Exception Si ocurre un error
   */
  private void createHypothesisImage(Classifier classifier) throws Exception{

    //Leemos los colores que representan a cada clase
    Color colorsArray[] = new Color[m_ColorsUsedByCurrentClassifier.length];

    //Preparamos el color que representará a cada clase
    for (int i = 0; i < colorsArray.length; i++){
      Color classColor = m_ColorsUsedByCurrentClassifier[i];
      colorsArray[i] = new Color((int) (classColor.getRed() * m_ColorTransform),
                                 (int) (classColor.getGreen() * m_ColorTransform),
                                 (int) (classColor.getBlue() * m_ColorTransform),
                                 (int) (classColor.getAlpha() * m_ColorTransform));
    }

    //La instancia que representará el punto central de cada celda de la rejilla
    Instance instance = new Instance(0, new double[]{0, 0, -1});
    instance.setDataset(m_InstancesSchemeUsedByCurrentClassifier);

    //Número de columnas y filas de la rejilla
    final int numColumns = m_Gdop.getColumns();
    final int numRows = m_Gdop.getRows();

    //Tamaño real de cada celda en el panel de dibujo (ancho y alto en puntos)
    //Si se redondea por abajo--> quedan libres recuadros
    //Si se redondea por arriba--> Los recuadros son más grandes y abarcan todo el dibujo--> OK
    final int xStep = (int) Math.ceil(m_DrawingPanel.getWidth() / (double) numColumns);
    final int yStep = (int) Math.ceil(m_DrawingPanel.getHeight() / (double) numRows);

    /////////////////////
    //Variables que utilizaremos para crear la instancia representante (centro de la celda)
    //de cada celda, pero teniendo en cuanta la escala (zoom) en el que se encuentra
    //la región de dibujado
    /////////////////////
    //Tamaño de las celdas (ya escalado)
    final double xInstanceStep, yInstanceStep, xInstanceStepHalf, yInstanceStepHalf;
    //Origen de la siguiente celda (columna, fila)
    double instanceX, instanceY;
    //El origen de la rejilla en el eje de ordenadas (tenemos que recuperarlo cada vez que acabamos una fila)
    final double instanceYOrigin;
    if (m_DrawingPanel.isZoomedIn()){
      //Nos aseguramos de que la instancia es realmente representativa del correspondiente cuadro:
      //  getXFromPanel(x) <= instanceX + xInstanceStepHalf <= getXFromPanel(x + xStep)
      //  getYFromPanel(y) <= instanceY + yInstanceStepHalf <= getYFromPanel(y + yStep)
      // Independientemente del tamaño del panel
      xInstanceStep = m_DrawingPanel.getXFromPanel(2 * xStep) - m_DrawingPanel.getXFromPanel(xStep);
      yInstanceStep = m_DrawingPanel.getYFromPanel(2 * yStep) - m_DrawingPanel.getYFromPanel(yStep);

      //El origen del cuadro escalado y trasladado
      instanceX = m_DrawingPanel.getRegionX();
      instanceYOrigin = instanceY = m_DrawingPanel.getRegionY();
    }
    else{
      xInstanceStep = xStep;
      yInstanceStep = yStep;
      instanceX = 0;
      instanceYOrigin = instanceY = 0;
    }
    //El punto representativo de una celda (será pintada de aquel color que sea clasificada
    //por el clasificador) es:
    //  x = Número de columna [0, numColumnas) * xInstanceStep + xInstanceStepHalf
    //  y = Número de fila [0, numFilas)* yInstanceStep + yInstanceStepHaf
    xInstanceStepHalf = xInstanceStep / 2.0;
    yInstanceStepHalf = yInstanceStep / 2.0;

    //La imagen que representará la hipótesis
    BufferedImage bi = new BufferedImage(m_DrawingPanel.getWidth(), m_DrawingPanel.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = (Graphics2D) bi.getGraphics();

    //El índice de celda que se está computando y el número total que se computará
    int index = 1;
    int numberOfIns = numColumns * numRows;

    //Configuración de la barra de progreso
    m_ProgressBar.setMinimum(0);
    m_ProgressBar.setMaximum(numberOfIns);
    m_ProgressBar.setStringPainted(true);

//Celda (posición absoluta):
//           x = i*xStep
//           y = j*yStep
//           width = xStep
//           height = yStep
//           Punto central = (x + xStep / 2, y + yStep / 2)
    int x = 0;
    int y = 0;

    //Hacemos el recorrido de izquierda a derecha...
    for (int i = 0; i < numColumns; i++){

      //Valor x de la instancia que representa la celda
      instance.setValue(0, instanceX + xInstanceStepHalf);

      //... y de arriba hacia abajo
      for (int j = 0; j < numRows; j++){

        //Monitorizamos que el usuario decida interrumpir el proceso
        if (Thread.interrupted())
          throw new InterruptedException();

        //Valor y de la instancia que representa la celda
        instance.setValue(1, instanceY + yInstanceStepHalf);

        //Actualizamos la barra de progreso
        updatePBStatus(index);
        index++;

        //El color de la celda, el que predizca el clasificador
        g2.setColor(colorsArray[(int) classifier.classifyInstance(instance)]);
        g2.fillRect(Math.round(x), Math.round(y), xStep, yStep);

        //Actualizamos las coordenadas de la instancia para la siguiente columna
        instanceY += yInstanceStep;

        //Actualizamos las coordenadas absolutas de la celda en el panel
        y += yStep;
      }
      //Actualizamos las coordenadas de la instancia para la siguiente fila
      instanceX += xInstanceStep;
      instanceY = instanceYOrigin;

      //Actualizamos las coordenadas absolutas de la celda (fila) en el panel
      x += xStep;
      y = 0;
    }

    //Informamos al panel de dibujo de con qué configuración de rejilla hemos creado la nueva imagen
    m_DrawingPanel.setGridColumnWidth(xStep);
    m_DrawingPanel.setGridRowHeight(yStep);
    m_DrawingPanel.setGridWidth(m_DrawingPanel.getWidth());
    m_DrawingPanel.setGridHeight(m_DrawingPanel.getHeight());

    //Guardamos
    m_HypothesisImage = bi;
    m_HypothesisSumImage = null;
  }

  /**
   * Método que permite crear una imagen que represente la hipótesis de un clasificador
   * sobre el panel de dibujado; la construimos creando una rejilla (utilizando
   * la configuración de esta) en la que cada celda será pintada del color que
   * el clasificador predizca para su punto intermedio. Respeta el zoom que pueda existir
   * en el panel de dibujado.
   *
   * Usa grados de confianza en la predicción
   *
   * @param classifier El clasificador del que representaremos la hipótesis
   *
   * @throws InterruptedException Si el usuario cancela la creación de la imagen
   * @throws Exception Si ocurre un error
   */
  private void createHypothesisSumImage(DistributionClassifier classifier) throws Exception{

    //Sólo comento los puntos que son distintos de createHypothesisImage

    Color colorsArray2[] = new Color[m_ColorsUsedByCurrentClassifier.length];

    for (int i = 0; i < colorsArray2.length; i++){
      Color classColor = m_ColorsUsedByCurrentClassifier[i];
      colorsArray2[i] = new Color((int) (classColor.getRed() * m_ColorTransform),
                                  (int) (classColor.getGreen() * m_ColorTransform),
                                  (int) (classColor.getBlue() * m_ColorTransform),
                                  (int) (classColor.getAlpha() * m_ColorTransform));
    }

    int[] colorsArray = new int[colorsArray2.length];
    for (int i = 0; i < colorsArray2.length; i++)
      colorsArray[i] = colorsArray2[i].getRGB();

    Instance instance = new Instance(0, new double[]{0, 0, -1});
    instance.setDataset(m_InstancesSchemeUsedByCurrentClassifier);

    int numColumns = m_Gdop.getColumns();
    int numRows = m_Gdop.getRows();

    //Tamaño real de cada celda en el panel de dibujo (ancho y alto en puntos)
    //Si se redondea por abajo--> quedan libres recuadros
    //Si se redondea por arriba--> Los recuadros son más grandes y abarcan todo el dibujo--> OK
    final int xStep = (int) Math.ceil(m_DrawingPanel.getWidth() / (double) numColumns);
    final int yStep = (int) Math.ceil(m_DrawingPanel.getHeight() / (double) numRows);

    /////////////////////
    //Variables que utilizaremos para crear la instancia representante (centro de la celda)
    //de cada celda, pero teniendo en cuanta la escala (zoom) en el que se encuentra
    //la región de dibujado
    /////////////////////
    //Tamaño de las celdas (ya escalado)
    final double xInstanceStep, yInstanceStep, xInstanceStepHalf, yInstanceStepHalf;
    //Origen de la siguiente celda (columna, fila)
    double instanceX, instanceY;
    //El origen de la rejilla en el eje de ordenadas (tenemos que recuperarlo cada vez que acabamos una fila)
    final double instanceYOrigin;
    if (m_DrawingPanel.isZoomedIn()){
      //Nos aseguramos de que la instancia es realmente representativa del correspondiente cuadro:
      //  getXFromPanel(x) <= instanceX + xInstanceStepHalf <= getXFromPanel(x + xStep)
      //  getYFromPanel(y) <= instanceY + yInstanceStepHalf <= getYFromPanel(y + yStep)
      // Independientemente del tamaño del panel
      xInstanceStep = m_DrawingPanel.getXFromPanel(2 * xStep) - m_DrawingPanel.getXFromPanel(xStep);
      yInstanceStep = m_DrawingPanel.getYFromPanel(2 * yStep) - m_DrawingPanel.getYFromPanel(yStep);

      //El origen del cuadro escalado y trasladado
      instanceX = m_DrawingPanel.getRegionX();
      instanceYOrigin = instanceY = m_DrawingPanel.getRegionY();
    }
    else{
      xInstanceStep = xStep;
      yInstanceStep = yStep;
      instanceX = 0;
      instanceYOrigin = instanceY = 0;
    }
    //El punto representativo de una celda (será pintada de aquel color que sea clasificada
    //por el clasificador) es:
    //  x = Número de columna [0, numColumnas) * xInstanceStep + xInstanceStepHalf
    //  y = Número de fila [0, numFilas)* yInstanceStep + yInstanceStepHaf
    xInstanceStepHalf = xInstanceStep / 2.0;
    yInstanceStepHalf = yInstanceStep / 2.0;

    //Esta vez sí usamos grados de confianza para crear dos imágenes: la predicción absoluta (bi2)
    //y la que utiliza el porcentaje de confianza en cada clase (bi)
    BufferedImage bi = new BufferedImage(m_DrawingPanel.getWidth(), m_DrawingPanel.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = bi.createGraphics();
    BufferedImage bi2 = new BufferedImage(m_DrawingPanel.getWidth(), m_DrawingPanel.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = bi2.createGraphics();

    int index = 1;
    int numberOfIns = numColumns * numRows;

    m_ProgressBar.setMinimum(0);
    m_ProgressBar.setMaximum(numberOfIns);
    m_ProgressBar.setStringPainted(true);

//Celda (posición absoluta):
//           x = i*xStep
//           y = j*yStep
//           width = xStep
//           height = yStep
//           Punto central = (x + xStep / 2, y + yStep / 2)

    int x = 0;
    int y = 0;

    for (int i = 0; i < numColumns; i++){

      instance.setValue(0, instanceX + xInstanceStepHalf);

      for (int j = 0; j < numRows; j++){

        if (Thread.interrupted())
          throw new InterruptedException();

        instance.setValue(1, instanceY + yInstanceStepHalf);

        updatePBStatus(index);
        index++;

        //Consultamos por la predicción del clasificador, con porcentaje de seguridad en cada clase
        //¡Ojo!; presuponemos que las predicciones ya están normalizadas para sumar 1
        double[] components = classifier.distributionForInstance(instance);
        int red = 0, green = 0, blue = 0, alpha = 0;

        //El color de la celda en la imagen que utiliza grados de confianza será una suma
        //ponderada de las componentes de cada color según la confianza del clasificador en dicho color
        int selectedColor = 0;
        double maxValue = Double.MIN_VALUE;
        for (int k = 0; k < components.length; k++){
          double colorPct = components[k];
          if (colorPct > maxValue){
            selectedColor = k;
            maxValue = colorPct;
          }
          red += (int) Math.round(colorsArray2[k].getRed() * colorPct);
          green += (int) Math.round(colorsArray2[k].getGreen() * colorPct);
          blue += (int) Math.round(colorsArray2[k].getBlue() * colorPct);
          alpha += (int) Math.round(colorsArray2[k].getAlpha() * colorPct);
        }

        //Las componentes pueden haberse salido del rango válido por los redondeos; lo solucionamos
        alpha = validColorComponent(alpha);
        red = validColorComponent(red);
        green = validColorComponent(green);
        blue = validColorComponent(blue);

        //El color compuesto por las predicciones
        g.setColor(new Color(red, green, blue, alpha));
        g.fillRect(x, y, xStep, yStep);

        //El color en el que el clasificador tiene mayor confianza
        g2.setColor(colorsArray2[selectedColor]);
        g2.fillRect(x, y, xStep, yStep);

        instanceY += yInstanceStep;
        y += yStep;
      }
      instanceX += xInstanceStep;
      instanceY = instanceYOrigin;
      x += xStep;
      y = 0;
    }

    m_DrawingPanel.setGridColumnWidth(xStep);
    m_DrawingPanel.setGridRowHeight(yStep);
    m_DrawingPanel.setGridWidth(m_DrawingPanel.getWidth());
    m_DrawingPanel.setGridHeight(m_DrawingPanel.getHeight());

    m_HypothesisImage = bi2;
    m_HypothesisSumImage = bi;
  }

  /**
   * Método que permite crear una imagen que represente la hipótesis de un clasificador
   * sobre el panel de dibujado; la construimos creando una rejilla (utilizando
   * la configuración de esta) en la que cada celda será pintada del color que
   * el clasificador predizca para su punto intermedio. Respeta el zoom que pueda existir
   * en el panel de dibujado.
   *
   * Usa grados de confianza y construcción iterativa en la predicción
   *
   * @param classifier El clasificador del que representaremos la hipótesis
   *
   * @throws InterruptedException Si el usuario cancela la creación de la imagen
   * @throws Exception Si ocurre un error
   */
  private void createHypothesisSumImage(IterativeUpdatableClassifier classifier) throws Exception{

    //Sólo comento los puntos que son distintos de createHypothesisImage & createHypothesisSumImage (Distrib...
    Color[] colorsArray2 = new Color[m_ColorsUsedByCurrentClassifier.length];

    for (int i = 0; i < colorsArray2.length; i++){
      Color classColor = m_ColorsUsedByCurrentClassifier[i];
      colorsArray2[i] = new Color((int) (classColor.getRed() * m_ColorTransform),
                                  (int) (classColor.getGreen() * m_ColorTransform),
                                  (int) (classColor.getBlue() * m_ColorTransform),
                                  (int) (classColor.getAlpha() * m_ColorTransform));
    }

    int[] colorsArray = new int[colorsArray2.length];
    for (int i = 0; i < colorsArray2.length; i++)
      colorsArray[i] = colorsArray2[i].getRGB();

    Instance instance = new Instance(0, new double[]{0, 0, -1});
    instance.setDataset(m_InstancesSchemeUsedByCurrentClassifier);

    final int numColumns = m_Gdop.getColumns();
    final int numRows = m_Gdop.getRows();

    //Tamaño real de cada celda en el panel de dibujo (ancho y alto en puntos)
    //Si se redondea por abajo--> quedan libres recuadros
    //Si se redondea por arriba--> Los recuadros son más grandes y abarcan todo el dibujo--> OK
    final int xStep = (int) Math.ceil(m_DrawingPanel.getWidth() / (double) numColumns);
    final int yStep = (int) Math.ceil(m_DrawingPanel.getHeight() / (double) numRows);

    /////////////////////
    //Variables que utilizaremos para crear la instancia representante (centro de la celda)
    //de cada celda, pero teniendo en cuanta la escala (zoom) en el que se encuentra
    //la región de dibujado
    /////////////////////
    //Tamaño de las celdas (ya escalado)
    final double xInstanceStep, yInstanceStep, xInstanceStepHalf, yInstanceStepHalf;
    //Origen de la siguiente celda (columna, fila)
    double instanceX, instanceY;
    //El origen de la rejilla en el eje de ordenadas (tenemos que recuperarlo cada vez que acabamos una fila)
    final double instanceYOrigin;
    if (m_DrawingPanel.isZoomedIn()){
      //Nos aseguramos de que la instancia es realmente representativa de la correspondiente celda:
      //  getXFromPanel(x) <= instanceX + xInstanceStepHalf <= getXFromPanel(x + xStep)
      //  getYFromPanel(y) <= instanceY + yInstanceStepHalf <= getYFromPanel(y + yStep)
      // Independientemente del tamaño del panel
      xInstanceStep = m_DrawingPanel.getXFromPanel(2 * xStep) - m_DrawingPanel.getXFromPanel(xStep);
      yInstanceStep = m_DrawingPanel.getYFromPanel(2 * yStep) - m_DrawingPanel.getYFromPanel(yStep);

      //El origen del cuadro escalado y trasladado
      instanceX = m_DrawingPanel.getRegionX();
      instanceYOrigin = instanceY = m_DrawingPanel.getRegionY();
    }
    else{
      xInstanceStep = xStep;
      yInstanceStep = yStep;
      instanceX = 0;
      instanceYOrigin = instanceY = 0;
    }
    //El punto representativo de una celda (será pintada de aquel color que sea clasificada
    //por el clasificador) es:
    //  x = Número de columna [0, numColumnas) * xInstanceStep + xInstanceStepHalf
    //  y = Número de fila [0, numFilas)* yInstanceStep + yInstanceStepHaf
    xInstanceStepHalf = xInstanceStep / 2.0;
    yInstanceStepHalf = yInstanceStep / 2.0;

    BufferedImage bi = new BufferedImage(m_DrawingPanel.getWidth(), m_DrawingPanel.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = bi.createGraphics();
    BufferedImage bi2 = new BufferedImage(m_DrawingPanel.getWidth(), m_DrawingPanel.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = bi2.createGraphics();

    int index = 1;
    int numberOfIns = numColumns * numRows;

    m_ProgressBar.setMinimum(0);
    m_ProgressBar.setMaximum(numberOfIns);
    m_ProgressBar.setStringPainted(true);

    //Si es necesario, empezamos desde cero
    //(Clasificador nuevo, cambio de los parámetros de la rejilla, cambio del tamaño del panel o zoom)
    Rectangle2D.Double region = new Rectangle2D.Double(m_DrawingPanel.getRegionX(),
                                                       m_DrawingPanel.getRegionY(),
                                                       m_DrawingPanel.getRegionWidth(),
                                                       m_DrawingPanel.getRegionHeight());
    if (m_NewIterativeUpdatableClassifier || m_LastGridWidth != numColumns || m_LastGridHeigt != numRows
      || !m_LastRegion.equals(region) || numberOfIns != m_BoosterResults.length){
      m_LastGridWidth = numColumns;
      m_LastGridHeigt = numRows;
      m_LastIterationUsed = 0;
      m_BoosterResults = new double[numColumns * numRows][m_ColorsUsedByCurrentClassifier.length];
      m_NewIterativeUpdatableClassifier = false;
      m_LastRegion = region;
    }

    //Si se realizan nuevas iteraciones mientras se crea esta imagen no queremos que se usen
    //hasta una nueva llamada
    final int numIterationsPerformed = classifier.getNumIterationsPerformed();

//Celda (valores absolutos en la pantalla)
//           x = i*xStep
//           y = j*yStep
//           width = xStep
//           height = yStep
//           Punto central = (x + xStep / 2, y + yStep / 2)

    int x = 0;
    int y = 0;

    //Array para normalizar las predicciones, de manera que sumen 1
    double[] normalized = new double[m_ColorsUsedByCurrentClassifier.length];

    for (int i = 0; i < numColumns; i++){

      instance.setValue(0, instanceX + xInstanceStepHalf);

      for (int j = 0; j < numRows; j++){

        instance.setValue(1, instanceY + yInstanceStepHalf);

        updatePBStatus(index);
        index++;

        //Dependiendo de a que nivel (dentro de los bucles for) lo hagamos, se hará más veces,
        //pero antes se hará la cancelación; interesante si, por ejemplo, tenemos que usar 5000
        //iteraciones del booster (la cancelación tardaría mucho).
        //El nivel que más velocidad de cancelación daría
        //(y que más sobrecarga computacional tiene) es el meterlo en
        //el siguiente bucle; lo dejamos aquí por conveniencia.
        //NO sería desanconsejable usar stop() en vez de interrupt()...
        if (Thread.interrupted())
          throw new InterruptedException();

        //Usamos el api de la clase IterativeUpdatableClassifier que permite reconstruir
        //la hipótesis desde una iteración cualquiera
        double[] components = m_BoosterResults[index - 2];
        for (int it = m_LastIterationUsed; it < numIterationsPerformed; it++){
          Utils.doubleArraysSum(components, classifier.getClassifierVote(instance, it));
        }

        int red = 0, green = 0, blue = 0, alpha = 0;

        //Normalizamos el array, el resto como en el método anterior
        System.arraycopy(components, 0, normalized, 0, components.length);
        Utils.secureNormalize(normalized);

        int selectedColor = 0;
        double maxValue = Double.MIN_VALUE;
        for (int k = 0; k < normalized.length; k++){
          double colorPct = normalized[k];
          if (colorPct > maxValue){
            selectedColor = k;
            maxValue = colorPct;
          }
          red += (int) Math.round(colorsArray2[k].getRed() * colorPct);
          green += (int) Math.round(colorsArray2[k].getGreen() * colorPct);
          blue += (int) Math.round(colorsArray2[k].getBlue() * colorPct);
          alpha += (int) Math.round(colorsArray2[k].getAlpha() * colorPct);
        }

        alpha = validColorComponent(alpha);
        red = validColorComponent(red);
        green = validColorComponent(green);
        blue = validColorComponent(blue);

        g.setColor(new Color(red, green, blue, alpha));
        g.fillRect(x, y, xStep, yStep);
        g2.setColor(colorsArray2[selectedColor]);
        g2.fillRect(x, y, xStep, yStep);

        instanceY += yInstanceStep;
        y += yStep;
      }
      instanceX += xInstanceStep;
      instanceY = instanceYOrigin;
      x += xStep;
      y = 0;
    }

    //En una próxima llamada, reiniaremos los cálculos desde esta iteración
    m_LastIterationUsed = classifier.getNumIterationsPerformed();

    m_DrawingPanel.setGridColumnWidth(xStep);
    m_DrawingPanel.setGridRowHeight(yStep);
    m_DrawingPanel.setGridWidth(m_DrawingPanel.getWidth());
    m_DrawingPanel.setGridHeight(m_DrawingPanel.getHeight());

    m_HypothesisImage = bi2;
    m_HypothesisSumImage = bi;
  }

  //-----------------------------///////////////////////////////////
  //------------------ ARBITRABLE IMPLEMENTATION ///////////////////
  //-----------------------------///////////////////////////////////

  // Private field which will be used to implement the Arbitable interface.
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
  /** Indicador de mensaje para los mediadores, acompañado del clasificador utilizado para crear la imagen */
  public final static int
    HYPOTHESIS_IMAGE_START = 0,
    HYPOTHESIS_IMAGE_FINISH = 1,
    HYPOTHESIS_IMAGE_CANCELED = 2;
  //No tiene demasiado sentido testear unitariamente esta clase
}