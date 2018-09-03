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
 *    ClassifierPanel.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.gui;

import com.jrefinery.chart.ChartPanel;
import com.jrefinery.chart.TextTitle;
import com.jrefinery.ui.about.AboutFrame;
import com.jrefinery.ui.RefineryUtilities;
import oaidtb.boosters.AdaBoostMH;
import oaidtb.boosters.Booster;
import oaidtb.boosters.ErrorUpperBoundComputer;
import oaidtb.boosters.IterativeUpdatableClassifier;
import oaidtb.gui.customizedWeka.GenericObjectEditor;
import oaidtb.gui.customizedWeka.PropertyPanel;
import oaidtb.misc.BoosterAnalyzer;
import oaidtb.misc.guiUtils.IntegerDocument;
import oaidtb.misc.javaTutorial.SwingWorker;
import oaidtb.misc.Utils;
import oaidtb.misc.guiUtils.MyAbstractAction;
import oaidtb.misc.mediator.Arbitrable;
import oaidtb.misc.mediator.Colleague;
import oaidtb.misc.mediator.Mediator;
import weka.classifiers.Classifier;
import weka.classifiers.CostMatrix;
import weka.classifiers.Evaluation;
import weka.core.FastVector;
import weka.core.Instances;
import weka.gui.ExtensionFileFilter;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Panel que permite seleccionar un clasificador, configurarlo, entrenarlo
 * con los datos que se desee, evaluarlo y guardarlo.
 *
 * <p> Para ejecutarlo como una aplicación aparte: java oaidtb.gui.ClassifierPanel$Test
 *
 * <p><p> Hace uso de la librería JFreeChart para crear la grafica del error; esta es de libre
 * distribución bajo la licencia GNU Lesser General Public License, que puede ser encontrada
 * en su página web:
 * <a href="http://www.object-refinery.com/jfreechart/index.html">
 * JFreeChart
 * </a><p>
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class ClassifierPanel extends JPanel implements Arbitrable{

  //TODO: CheckBoxes para: AutoUpdateBoosterHistory, usar train y/o test para evaluaar, auto actualizar el gráfico

  /** Lets the user configure the classifier */
  private GenericObjectEditor m_ClassifierEditor = new GenericObjectEditor();

  /** The panel showing the current classifier selection */
  private PropertyPanel m_ClassifierSelector = new PropertyPanel(m_ClassifierEditor);

  /** Referencia al clasificador mostrado en {@link #m_ClassifierSelector} */
  private Classifier m_Classifier;

  /** Permite al usuario configurar el número de iteraciones a realizar */
  private JTextField m_NumIterations = new JTextField();
  /** El Document de {@link #m_NumIterations}, para poder mostrarlo en otros documentos */
  private IntegerDocument m_BoosterIterationsDocument;

  /** El botón de construcción de un clasificador/iterar un booster */
  private JButton m_BuildButton = new JButton("Build classifier");
  /** Cancelar la construcción/iteración de un clasificador/booster */
  private JButton m_CancelBuildButton = new JButton("Cancel");
  /** Resetear el panel (instancias, gráfico de errores etc. */
  private JButton m_ResetButton = new JButton("Reset");

  /** Área de texto en la que se volcarán los resultados de los experimentos y otras cosas */
  private JTextArea m_TextInfoArea = new JTextArea(){
    /**
     * Evitar que el área de texto se "trague" el foco
     *
     * @return false
     */
    public boolean isFocusTraversable(){
      return false;
    }
  };

  /** ChackBox que controla si tras la construcción de un clasificador se debe evaluar automáticamente su precisión */
  private JCheckBox m_AutoEvalueCB = new JCheckBox("Auto evalue", true);
  /** Realizar la evaluación de un clasificador manualmente */
  private JButton m_EvalueButton = new JButton("Evaluate");
  /** Mostrar el resultado del método toString() del clasificador en el área de texto */
  private JButton m_ToStringButton = new JButton("To string");
  /** Mostrar el historial de iteraciones de un booster en el área de texto*/
  private JButton m_BoosterHistoryButton = new JButton("History");
  /** Mostrar el conjunto de entrenamiento en el área de texto */
  private JButton m_ShowTrainDataButton = new JButton("Show train data");
  /** Mostrar el conjunto de test en el área de texto */
  private JButton m_ShowTestDataButton = new JButton("Show test data");

  /**
   * Panel en el que se muestra la gráfica de errores del booster, así como permite
   * dividir un poco las funcionalidades y los datos para hacer más legible la clase.
   */
  private BoosterPanel m_BoosterPanel;

  /** Panel en el que se muestra el área de texto y el panel del booster */
  private JTabbedPane m_Tabs = new JTabbedPane();

  /** Thread que permite construir un clasificador sin bloquear la interfaz gráfica */
  private ClassifierBuildThread m_CBThread = null;
  /** SwinWorker que permite iterar un booster sin bloquear la interfaz gráfica */
  private SwingWorker m_BoosterWorker;

  /** Método (si existe) para recuperar la matriz de costes usada por el booster */
  private Method m_CostMatrixGetterMethod = null;

  /** El conjunto de entrenamiento sobre el que se trabaja */
  private Instances m_TrainData = new Instances("dummyTrain", new FastVector(), 0);
  /** El conjunto de test sobre el que se trabaja */
  private Instances m_TestData = new Instances("dummyTest", new FastVector(), 0);

  /**
   * Indica de dónde se deben escoger ls instancias: si de {@link #m_Points} o
   * directamente usar {@link #m_CustomTrainInstances} y {@link #m_CustomTestInstances}.
   * <p> Ver {@link #chooseInstances()}
   */
  private JCheckBox m_SharedPoint2DInstancesModeCB = new JCheckBox("Point 2D Instances Mode", true);

  /**
   * Conjunto de datos compartido con la aplicación; cada vez que se recogen instancias del
   * mismo, se hace una copia de las mismas para evitar que concurrentemente alguien
   * las cambies y las deje en estado inconsistente con el que deben tener
   * para no producir errores en el proceso de iteración de un booster; se debe cambiar
   * ese aspecto si la aplicación se transforma en algo menos interactivo
   * (ver FastBatchPoint2DInstances), pues es bastante malo para la eficiencia.
   */
  private Point2DInstances m_Points;

  /**
   * Debido a que, si estamos en el modo compartido de instancias ({@link #m_SharedPoint2DInstancesModeCB}),
   * nos encontramos en un entorno en el que el esquema de las instancias puede cambiar
   * en cualquier moemnto, debemos guardar los colores que representan a cada clase
   * del conjunto específico de instancias de entrenamiento y test que estamos usando
   */
  private Color[] m_Colors;

  /** Instancias proporcionadas directamente para su uso (sin pasar por {@link #m_Points})*/
  private Instances m_CustomTrainInstances = new Instances("dummyTrain", new FastVector(), 0);
  /** Instancias proporcionadas directamente para su uso (sin pasar por {@link #m_Points})*/
  private Instances m_CustomTestInstances = new Instances("dummyTest", new FastVector(), 0);

  /** Muestra el nombre y el número de las instancias de entrenamiento que se usan en cada moemento */
  private JLabel m_TrainInstancesLabel = new JLabel("Train data info");
  /** Muestra el nombre y el número de las instancias de test que se usan en cada moemento */
  private JLabel m_TestInstancesLabel = new JLabel("Test data info");

  //////////////////////
  //Acciones accesibles desde el exterior
  //////////////////////

  /** Simplemente, hacer un click programado sobre el botón de clasificar/iterar */
  private MyAbstractAction m_BuildOrIterateAction;
  /** Simplemente, hacer un click programado sobre el botón de cancelar */
  private MyAbstractAction m_CancelBuildOrIterateAction;
  /** Construcción de un clasificador */
  private MyAbstractAction m_BuildClassifierAction;
  /** Iteración de un booster */
  private MyAbstractAction m_BoosterIterateAction;
  /** Cancelación de la construcción iteración de un clasificador */
  private MyAbstractAction m_CancelBuildClassifierAction;
  /** Cancelación de la iteración de un booster */
  private MyAbstractAction m_CancelBoosterIterateAction;
  /** Resetear el panel (releer las instancias, volver a construir un booster etc. */
  private MyAbstractAction m_ResetAction;

  /**
   * Constructor
   *
   * @param points Un conjunto de datos {@link Point2DInstances} o null si se usarán otros datos
   */
  public ClassifierPanel(Point2DInstances points){
    m_Points = points;
    m_SharedPoint2DInstancesModeCB.setSelected(m_Points != null);
    initGUI();
  }

  /** @return La acción de hacer click sobre el botón de construcción/iteración */
  public MyAbstractAction getBuildOrIterateAction(){
    return m_BuildOrIterateAction;
  }

  /** @return La acción de construir un clasificador */
  public MyAbstractAction getBuildClassifierAction(){
    return m_BuildClassifierAction;
  }

  /** @return  La acción de iterar un booster */
  public MyAbstractAction getBoosterIterateAction(){
    return m_BoosterIterateAction;
  }

  /** @return  La acción de cancelar la construcciónd e un clasificador */
  public MyAbstractAction getCancelBuildClassifierAction(){
    return m_CancelBuildClassifierAction;
  }

  /** @return  La acción de cancelar la iteración de un booster */
  public MyAbstractAction getCancelBoosterIterateAction(){
    return m_CancelBoosterIterateAction;
  }

  /** @return La acción de resetear el panel */
  public MyAbstractAction getResetAction(){
    return m_ResetAction;
  }

  /**
   * Configurar las acciones definidas; por defecto, las metemos en el InputMap
   * de WHEN_IN_FOCUSED_WINDOW
   *
   * @param im Un InputMap donde las teclas serán mapeadas a los nombres de las acciones
   * @param am Un ActionMap donde los nombres de las acciones serán mapeados a las acciones
   */
  public void setUpActions(final InputMap im, final ActionMap am){

    ////////////////////////////////////////////////////
    // Construir o iterar (Alt-B)
    ////////////////////////////////////////////////////
    m_BuildOrIterateAction = new MyAbstractAction("Build_Or_Iterate"){
      public void actionPerformed(ActionEvent e){
        if (isEnabled())
          m_BuildButton.doClick();
      }
    };
    m_BuildOrIterateAction.setToolTipText("Construir el clasificador / hacer las siguientes iteraciones" + " (Alt-B)");
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_B, KeyEvent.ALT_MASK), "Build_Or_Iterate");
    am.put("Build_Or_Iterate", m_BuildOrIterateAction);

    ////////////////////////////////////////////////////
    // Cancelar (Ctrl-B)
    ////////////////////////////////////////////////////
    m_CancelBuildOrIterateAction = new MyAbstractAction("Cancel_Build_Or_Iterate"){
      public void actionPerformed(ActionEvent e){
        if (isEnabled())
          m_CancelBuildButton.doClick();
      }
    };
    m_CancelBuildOrIterateAction.setEnabled(false);
    m_CancelBuildOrIterateAction.setToolTipText("Cancelar la construcción/iteración de un clasificador/booster" + " (Ctrl-B");
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_B, KeyEvent.CTRL_MASK), "Cancel_Build_Or_Iterate");
    am.put("Cancel_Build_Or_Iterate", m_CancelBuildOrIterateAction);

    ////////////////////////////////////////////////////
    // Construir un clasificador (no booster) (Alt-B)
    ////////////////////////////////////////////////////
    m_BuildClassifierAction = new MyAbstractAction("Build_Classifier"){
      public void actionPerformed(ActionEvent e){
        if (!isEnabled() || m_CBThread != null)
          return;
        m_CBThread = new ClassifierBuildThread();
        //Permitir configurar la prioridad al usuario
        m_CBThread.setPriority(Thread.MIN_PRIORITY);
        m_CBThread.start();
      }
    };
    m_BuildClassifierAction.setToolTipText("Construir un nuevo clasificador" + " (Alt-B)");

    ////////////////////////////////////////////////////
    // Iterar un booster (Alt-B)
    ////////////////////////////////////////////////////
    m_BoosterIterateAction = new MyAbstractAction("Booster_Iterate"){
      public void actionPerformed(ActionEvent e){
        if (!isEnabled() || m_BoosterWorker != null)
          return;
        m_BoosterWorker = m_BoosterPanel.getNewBoosterIterateWorker();
        m_BoosterWorker.start();
      }
    };
    m_BoosterIterateAction.setToolTipText("Realizar nuevas iteraciones" + " (Alt-B)");

    ////////////////////////////////////////////////////
    // Cancelar la construcción de un clasificador (Ctrl-B)
    ////////////////////////////////////////////////////
    m_CancelBuildClassifierAction = new MyAbstractAction("Cancel_Build_Classifier"){
      public void actionPerformed(ActionEvent e){
        if (m_CBThread != null){
          //Seguramente t.interrupt() no hará nada, ver el siguiente comentario.
          //Lo matamos de manera abrupta, asíncronamente, ya que los métodos buildClassifier de los clasificadores
          //de weka no están preparados para manejar el estado interrupt del thread en que están
          //siendo ejecutados.
          m_CBThread.stop();
          notifyMediators(CLASSIFIER_BUILD_CANCELED, getClassifier());
        }
      }
    };
    m_CancelBuildClassifierAction.setEnabled(false);
    m_CancelBuildClassifierAction.setToolTipText("Cancelar la construcción del clasificador" + " Ctrl-B");

    ////////////////////////////////////////////////////
    // Cancelar la construcción de un booster (Ctrl-B)
    ////////////////////////////////////////////////////
    m_CancelBoosterIterateAction = new MyAbstractAction("Cancel_Booster_Iterate"){
      public void actionPerformed(ActionEvent e){
        if (m_BoosterWorker != null){
          m_BoosterWorker.interrupt();
          notifyMediators(BOOSTER_ITERATE_CANCELED, getClassifier());
        }
      }
    };
    m_CancelBoosterIterateAction.setEnabled(false);
    m_CancelBoosterIterateAction.setToolTipText("Cancelar la iteración del booster" + " (Ctrl-B");

    ////////////////////////////////////////////////////
    // Resetear el problema del booster (Ctrl + Alt + B)
    ////////////////////////////////////////////////////
    m_ResetAction = new MyAbstractAction("Reset_Booster_Problem"){
      public void actionPerformed(ActionEvent e){
        if (!isEnabled())
          return;
        chooseInstances();
        m_TextInfoArea.setText("");
        //Liberar la memoria reservada por el booster para almacenar las instancias de entrenamiento
        //y para almacenar los clasificadores base, destruir pues el booster
        if (m_Classifier instanceof IterativeUpdatableClassifier){
          m_BoosterPanel.resetGraph();
          try{
            if(m_Classifier instanceof AdaBoostMH){
              ((AdaBoostMH)m_Classifier).purgeTrainData();
              ((AdaBoostMH)m_Classifier).purgeIterations(((AdaBoostMH)m_Classifier).getNumIterationsPerformed());
            }
            else{
              ((Booster)m_Classifier).purgeTraindata();
              ((Booster)m_Classifier).purgeIterations(((Booster)m_Classifier).getNumIterationsPerformed());
            }
          }
          catch(Exception ex){
            ex.printStackTrace();
          }
        }
      }
    };
    m_ResetAction.setToolTipText("Resetear el estado del booster y/o elegir las instancias" + " (Ctrl-Alt-B)");
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_B, KeyEvent.CTRL_MASK & KeyEvent.CTRL_MASK), "Reset_Booster_Problem");
    am.put("Reset_Booster_Problem", m_ResetAction);
  }

  /**
   * Inicializar los componentes
   */
  private void initGUI(){

    //Configuramos el campo de introducción del número de iteraciones del booster a realizar
    m_NumIterations.setHorizontalAlignment(JTextField.RIGHT);
    m_BoosterIterationsDocument = new IntegerDocument();
    m_BoosterIterationsDocument.setMinValue(1);
    m_BoosterIterationsDocument.setMaxValue(999999);
    m_NumIterations.setDocument(m_BoosterIterationsDocument);
    m_NumIterations.setColumns(6);
    m_NumIterations.setText("10");

    ///////////////////////////////////////////////////////////
    //Configuramos el selector & editor del clasificador
    ///////////////////////////////////////////////////////////

    //La clase: weka.classifiers.Classifier
    m_ClassifierEditor.setClassType(weka.classifiers.Classifier.class);

    //El clasificador inicial: AdaBoostECC
    m_ClassifierEditor.setValue(new oaidtb.boosters.AdaBoostECC());

    //Repintar con el nuevo nombre del clasificador el panel correspondiente
    m_ClassifierEditor.addPropertyChangeListener(new PropertyChangeListener(){
      public void propertyChange(PropertyChangeEvent e){
        repaint();
      }
    });

    //Configuramos el icono que aparecerá para las clases que sean seleccionables
    ((GenericObjectEditor.GOEPanel)m_ClassifierEditor.getCustomEditor()).
      getObjectChooser().setSelectableIcon(AllContainerPanel.PROFX_ICON);

    //Controlar las nuevas elecciones de clasificador
    ((GenericObjectEditor.GOEPanel) m_ClassifierEditor.getCustomEditor()).addOkListener(new ActionListener(){
      public void actionPerformed(ActionEvent e){
        try{
          updateClassifier(getClassifier());
        }
        catch (Exception ex){
        }
      }
    });

    ///////////////////////////////////
    //Crear las acciones pertinentes
    ///////////////////////////////////
    setUpActions(getInputMap(WHEN_IN_FOCUSED_WINDOW), getActionMap());

    //TipTexts para los botones de construcción y cancelación
    m_BuildButton.setToolTipText(m_BuildOrIterateAction.getTip());
    m_CancelBuildButton.setToolTipText(m_CancelBuildOrIterateAction.getTip());

    //Botón de reset
    m_ResetButton.addActionListener(m_ResetAction);
    m_ResetAction.addStateDependantComponent(m_ResetButton);
    m_ResetButton.setToolTipText(m_ResetAction.getTip());

    //(Des)Habilitar el campo de edición del número de iteraciones cuando sea pertinente
    m_BoosterIterateAction.addStateDependantComponent(m_NumIterations);

    //Distribuir componentes; necesario hacerlo ahora para inicializar correctamente m_Tabs
    layoutComponents();

    //Configuramos todo con el clasificador actual en el editor del clasificador
    try{
      updateClassifier(getClassifier());
    }
    catch (Exception e){
      System.err.println(e.toString());
    }

    //ToolTip para la CheckBox de auto evaluar
    m_AutoEvalueCB.setToolTipText("Auto evaluar tras construir / iterar");

    //Botón de evaluación
    m_EvalueButton.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e){
        if (m_TrainData != null && m_TrainData.numInstances() != 0 ||
          m_TestData != null && m_TestData.numInstances() != 0)
          evalue(getClassifier());
        else
          m_TextInfoArea.setText("No evaluation possible");
      }
    });
    m_EvalueButton.setToolTipText("Evaluar el clasificador sobre los conjuntos de entrenamiento y/o de test");

    //Botón para mostrar la descripción textual del clasificador
    m_ToStringButton.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e){
        m_TextInfoArea.setText(getClassifier().toString());
      }
    });
    m_ToStringButton.setToolTipText("Mostrar la representación textual del clasificador");

    //Botón para mostrar el historial del booster
    m_BoosterHistoryButton.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e){
        StringBuffer sb = new StringBuffer();
        if (m_BoosterPanel.m_TrainAnalyzer != null){
          sb.append("-----------PERFORMANCE ANALYSIS OVER TRAIN DATA-----------\n\n");
          sb.append(m_BoosterPanel.m_TrainAnalyzer.toString());
//          sb.append("\n" +m_BoosterPanel.m_TrainAnalyzer.toCSV());
        }
        if (m_BoosterPanel.m_TestAnalyzer != null){
          sb.append("\n\n-----------PERFORMANCE ANALYSIS OVER TEST DATA-----------\n\n");
          sb.append(m_BoosterPanel.m_TestAnalyzer.toString());
        }
        if (sb.length() == 0)
          m_TextInfoArea.setText("No analysis made");
        else
          m_TextInfoArea.setText(sb.toString());
      }
    });
    m_BoosterHistoryButton.setToolTipText("Mostrar el historial de iteraciones del booster");

    //Botón para mostrar las instancias de entrenamiento
    m_ShowTrainDataButton.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e){
        if (m_TrainData != null && m_TrainData.numInstances() != 0)
          m_TextInfoArea.setText(m_TrainData.toString());
        else
          m_TextInfoArea.setText("No train data available");
      }
    });
    m_ShowTrainDataButton.setToolTipText("Mostrar las instancias de entrenamiento");

    //Botón para mostrar las instancias de test
    m_ShowTestDataButton.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e){
        if (m_TestData != null && m_TestData.numInstances() != 0)
          m_TextInfoArea.setText(m_TestData.toString());
        else
          m_TextInfoArea.setText("No test data available");
      }
    });
    m_ShowTestDataButton.setToolTipText("Mostrar las instancias de test");

    //Añadimos un menu de contexto al área de texto, que permite:
    //   - Borrar su contenido
    //   - Copiarlo al clipboard del sistema
    m_TextInfoArea.addMouseListener(new MouseAdapter(){
      public void mouseClicked(MouseEvent e){
        if (SwingUtilities.isRightMouseButton(e)){

          JPopupMenu popupMenu = new JPopupMenu();

          //Borrar el contenido del área de texto
          JMenuItem clear = new JMenuItem("Clear the text");
          clear.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e2){
              m_TextInfoArea.setText("");
            }
          });
          popupMenu.add(clear);

          //Copiar el contenido al clipboard
          JMenuItem copyToClipBoard = new JMenuItem("Copy the text to the clipboard");
          copyToClipBoard.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e2){
              String text = m_TextInfoArea.getSelectedText();
              if (text == null)
                text = m_TextInfoArea.getText();
              StringSelection ss = new StringSelection(text);
              Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, null);
            }
          });
          popupMenu.add(copyToClipBoard);

          popupMenu.show(m_TextInfoArea, e.getX(), e.getY());
        }
      }
    });

    //Configuramos los colores de las etiquetas que muestran las instancias utilizadas en cada momento
    m_TrainInstancesLabel.setForeground(Color.blue);
    m_TestInstancesLabel.setForeground(Color.magenta);
  }

  /**
   * Elige las instancias de test y de entreneamiento que se usarán cuando se presione
   * de nuevo el botón de clasificar o el de reset; se tomarán de m_Points si
   * {@link #m_SharedPoint2DInstancesModeCB} está seleccionada y si no de
   * {@link #m_CustomTrainInstances} y de {@link #m_CustomTestInstances} si no
   */
  private void chooseInstances(){
    //TODO: usar getInstances(Instances) de m_Points hace gastar mucha memoria
    //      bloquear m_Points hasta que deje de usarse y usar directamente
    //      sus datos mediante selectTrainInstances y selectTestInstances()
    if (m_SharedPoint2DInstancesModeCB.isSelected() && m_Points != null){
      if (m_Points.existsTrainSet()){
        m_TrainData = m_Points.getInstances(m_Points.getTrainInstances());
        if (m_Points.existsTestSet())
          m_TestData = m_Points.getInstances(m_Points.getTestInstances());
        else
          m_TestData = new Instances(m_TrainData, 0);
      }
      else{
        m_TrainData = m_Points.getInstances(m_Points.getAllInstancesSortedFastVector());
        m_TestData = new Instances(m_TrainData, 0);
      }
      //Necesitamos guardar esta información para mantener el contacto con el "mundo real",
      //ya que m_Points puede cambiar en cualquier momento
      m_Colors = new Color[m_Points.getNumColors()];
      for (int i = 0; i < m_Colors.length; i++)
        m_Colors[i] = m_Points.getColor(i);
    }
    else{
      //Usaremos las instancias provistas explícitamente desde otra clase
      m_TrainData = m_CustomTrainInstances;
      m_TestData = m_CustomTestInstances;
    }
    m_TrainInstancesLabel.setText("Train set: " + m_TrainData.relationName() + ", " + m_TrainData.numInstances() + " instances");
    m_TestInstancesLabel.setText("Test set: " + m_TestData.relationName() + ", " + m_TestData.numInstances() + " instances");
  }

  /** @return Si existe, el {@link Point2DInstances} utilizado para recuperar las instancias, si no null */
  public Point2DInstances getPoints(){
    return m_Points;
  }

  /**
   * Si points no es null, el panel es colocado en el modo de instancias {@link Point2DInstances} compartidas,
   * si no es colocado en el modo de instancias suministradas explícitamente; ver {@link #chooseInstances}.
   *
   * @param points El {@link Point2DInstances} pertinente o null
   */
  public void setPoints(Point2DInstances points){
    m_Points = points;
    m_SharedPoint2DInstancesModeCB.setSelected(m_Points != null);
  }

  /** @return Las instancias de entrenamiento que se usarán en caso de no estar en "modo compartido" */
  public Instances getCustomTrainInstances(){
    return m_CustomTrainInstances;
  }

  /** @param customTrainInstances Las instancias de entrenamiento o null si se desea "limpiar"  */
  public void setCustomTrainData(Instances customTrainInstances){
    if (customTrainInstances != null)
      m_CustomTrainInstances = customTrainInstances;
    else
      m_CustomTrainInstances = new Instances("dummyTrain", new FastVector(), 0);
  }

  /** @return Las instancias de test que se usarán en caso de no estar en "modo compartido" */
  public Instances getCustomTestInstances(){
    return m_CustomTestInstances;
  }

  /** @param customTestInstances Las instancias de test o null si se desea "limpiar"  */
  public void setCustomTestData(Instances customTestInstances){
    if (customTestInstances != null)
      m_CustomTestInstances = customTestInstances;
    else
      m_CustomTestInstances = new Instances("dummyTest", new FastVector(), 0);
  }

  /** @return Las instancias de test en el momento de la llamada al método  */
  public Instances getTestData(){
    return m_TestData;
  }

  /** @return Las instancias de entrenamiento en el momento de la llamada al método  */
  public Instances getTrainData(){
    return m_TrainData;
  }

  /** @return El array de colores correspondientes a cada valor del atributo clase de los ultimos
   *          datos de entrenamiento recogidos de un {@link Point2DInstances}
   */
  public Color[] getColors(){
    return m_Colors;
  }

  /**
   * Método ideado, principalmente, para poder invocar a {@link PropertyPanel#showPropertyDialog}
   * desde una tercera clase.
   *
   * @return El panel de selección del clasificador
   */
  public PropertyPanel getClassifierSelector(){
    return m_ClassifierSelector;
  }

  /**
   * Indica si en el panel se están realizando trabajos concurrentemente (ie. construir
   * un clasficador / iterar un booster).
   *
   * @return true si se está construyendo un clasificador / iterando un booster
   */
  public boolean isBusy(){
    return m_CancelBuildOrIterateAction.isEnabled();
  }

  /**
   * Método ideado, principalmente, para poder editar el número de iteraciones a realizar por el booster
   * desde una tercera clase.
   *
   * @return El {@link javax.swing.text.Document} usado por el editor de iteraciones
   */
  public IntegerDocument getBoosterIterationsDocument(){
    return m_BoosterIterationsDocument;
  }

  /** @return El clasificador utilizado actualmente en el panel */
  public Classifier getClassifier(){
    return (Classifier) m_ClassifierEditor.getValue();
  }

  /**
   * Este método es invocado cada vez que se acepta la selección de un clasificador
   * en el diálogo de selección y configuración de clasificadores; se ocupa de
   * resetear, configurar y (des)habilitar los componentes necesarios para mantener
   * la lógica de acciones y la consistencia de datos en el panel.
   *
   * Si el clasificador es el mismo que ya existía (y lo será si, por ejemplo,
   * sólo se han cambiado las opciones del mismo), este método no hace nada, por lo tanto,
   * para que las nueva configuración sea aplicada se deberá, en la mayoría de los
   * casos, pulsar el botón de construcción del clasificador y/o de reset del booster
   *
   * @param classifier El clasificador en cuestión
   * @throws Exception Si ocurre un error de introspección al buscar el método de acceso
   *                   a la matriz de costes del clasificador
   */
  private void updateClassifier(Classifier classifier) throws Exception{

    if (classifier == null || m_Classifier != null && m_Classifier == classifier)
    //Silencioso noop, aunque sea una redundancia, ya que cualquier noop es, por definición, silencioso ;)
      return;

    //Buscamos, mediante introspección, la existencia de un método para recuperar
    //la matriz de costes utilizada por el clasificador
    m_CostMatrixGetterMethod = oaidtb.misc.Utils.getCostMatrixGetter(classifier);

    //Si el clasificador es un booster
    if (classifier instanceof IterativeUpdatableClassifier){
      //Configuramos el panel del booster
      if (m_BoosterPanel == null){
        m_BoosterPanel = new BoosterPanel((IterativeUpdatableClassifier) classifier);
        m_Tabs.setComponentAt(1, m_BoosterPanel);
      }
      else
        m_BoosterPanel.setBooster((IterativeUpdatableClassifier) classifier);

      //Permitimos al usuario acceder al panel del booster
      m_Tabs.setEnabledAt(1, true);

      //(Des)habilitamos las acciones y los componentes pertienenter
      m_BuildButton.removeActionListener(m_BuildClassifierAction);
      m_BuildButton.removeActionListener(m_BoosterIterateAction);
      m_BuildButton.addActionListener(m_BoosterIterateAction);
      m_CancelBuildButton.removeActionListener(m_CancelBuildClassifierAction);
      m_CancelBuildButton.removeActionListener(m_CancelBoosterIterateAction);
      m_CancelBuildButton.addActionListener(m_CancelBoosterIterateAction);
      m_CancelBuildButton.setEnabled(false);
      m_BoosterIterateAction.setEnabled(true);
      m_BuildClassifierAction.setEnabled(false);
      m_BuildButton.setText("Iterate");
      m_BoosterHistoryButton.setEnabled(true);
    }
    else{  //Si el clsificador no es un booster...
      //No permitimos al usuario acceder al panel del booster
      m_Tabs.setEnabledAt(1, false);
      m_Tabs.setSelectedIndex(0);

      //Liberamos la memoria reservada por el panel del booster
      m_BoosterPanel.setBooster(null);

      //(Des)habilitamos las acciones y los componentes pertienenter
      m_BuildClassifierAction.setEnabled(true);
      m_BuildButton.removeActionListener(m_BoosterIterateAction);
      m_BuildButton.removeActionListener(m_BuildClassifierAction);
      m_BuildButton.addActionListener(m_BuildClassifierAction);
      m_CancelBuildButton.removeActionListener(m_CancelBoosterIterateAction);
      m_CancelBuildButton.removeActionListener(m_CancelBuildClassifierAction);
      m_CancelBuildButton.addActionListener(m_CancelBuildClassifierAction);
      m_CancelBuildButton.setEnabled(false);
      m_BoosterIterateAction.setEnabled(false);
      m_BuildClassifierAction.setEnabled(true);
      m_BuildButton.setText("Build");
      m_BoosterHistoryButton.setEnabled(false);

      //Liberamos las últimas referencias si es que el último clasificador era un booster
      m_BoosterPanel.setBooster(null);
    }

    //Limpiamos el texto del área de información
    m_TextInfoArea.setText("");

    //Actualizamos la referencia al clasificador
    m_Classifier = classifier;

    //Que los jefes sepan lo que hemos hecho
    notifyMediators(CLASSIFIER_CHANGED, classifier);
  }

  /**
   * Evalúa la precisión de un clasificador en los conjuntos de entrenamiento y/o de test
   * actualmente elegidos. Si los conjuntos de entrenamiento y test n contienen instancias
   * o si el clasificador no puede clasificarlas, se avias de ello en el área de texto
   *
   * @param classifier El clasificador a evaluar
   */
  private void evalue(final Classifier classifier){

    try{

      Evaluation evaluation;
      StringBuffer sb = new StringBuffer();
      CostMatrix cm = null;

      //Intentamos usar la matriz de costes utilizada por el clasificador, si es que éste es sensible al costo
      if (m_CostMatrixGetterMethod != null)
        cm = (CostMatrix) m_CostMatrixGetterMethod.invoke(classifier, new Object[]{});

      if (m_TrainData != null &&
        m_TrainData.numInstances() > 0 &&
        Utils.classifierCanClassify(classifier, m_TrainData.instance(0))){
        if (cm == null)
          evaluation = new Evaluation(m_TrainData);
        else
          evaluation = new Evaluation(m_TrainData, cm);

        evaluation.evaluateModel(classifier, m_TrainData);
        sb.append("///////--------Train statistics\n" + evaluation.toSummaryString(false) + "\n");
        sb.append(evaluation.toClassDetailsString() + "\n");
        sb.append(evaluation.toMatrixString());
      }

      if (m_TestData != null &&
        m_TestData.numInstances() > 0 &&
        Utils.classifierCanClassify(classifier, m_TestData.instance(0))){
        if (cm == null)
          evaluation = new Evaluation(m_TestData);
        else
          evaluation = new Evaluation(m_TestData, cm);

        evaluation.evaluateModel(classifier, m_TestData);
        sb.append("\n\n///////--------Test statistics\n" + evaluation.toSummaryString(false) + "\n");
        sb.append(evaluation.toClassDetailsString() + "\n");
        sb.append(evaluation.toMatrixString());
      }

      m_TextInfoArea.setText(sb.toString());
    }
    catch (Exception e){
      m_TextInfoArea.setText(e.toString());
//      System.err.println(e.toString());
    }
  }

  /**
   * Distribución de los componentes en el panel
   */
  private void layoutComponents(){

    //El panel que muestra y permite cambiar la selección del clasificador actual
    JPanel classifierSelectorPanel = new JPanel(new BorderLayout());
    classifierSelectorPanel.add(m_ClassifierSelector, BorderLayout.NORTH);
    classifierSelectorPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Classifier"),
                                                                         BorderFactory.createEmptyBorder(0, 5, 5, 5)));

    //El panel de botones que seleccionan lo que se debe mostrar en el área de texto
    JPanel textOptionsPanel = new JPanel(new GridLayout(2, 1));
    JPanel firstRowPanel = new JPanel();
    firstRowPanel.add(m_AutoEvalueCB);
    firstRowPanel.add(m_EvalueButton);
    firstRowPanel.add(m_BoosterHistoryButton);
    JPanel secondRowPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    secondRowPanel.add(m_ToStringButton, gbc);
    gbc.gridx = 1;
    gbc.gridy = 0;
    secondRowPanel.add(m_ShowTrainDataButton, gbc);
    gbc.gridx = 2;
    gbc.gridy = 0;
    secondRowPanel.add(m_ShowTestDataButton, gbc);
    textOptionsPanel.add(firstRowPanel);
    textOptionsPanel.add(secondRowPanel);

    //El panel que contiene el área de texto y los botones que la configuran
    JPanel textInfoPanel = new JPanel(new BorderLayout());
    textInfoPanel.add(textOptionsPanel, BorderLayout.SOUTH);
    m_TextInfoArea.setEditable(false);
    m_TextInfoArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
    JScrollPane infoPanel = new JScrollPane(m_TextInfoArea);
    textInfoPanel.add(infoPanel, BorderLayout.CENTER);

    //El tabbed pane con el panel de información en modo texto y el panel de la gráfica de errores
    m_Tabs.addTab("Text Info", textInfoPanel);
    m_Tabs.setSelectedIndex(0);
    m_Tabs.addTab("Graph Info", new JPanel());
    m_Tabs.setEnabledAt(1, false);
    m_Tabs.setBorder(BorderFactory.createLineBorder(Color.gray));

    //El panel con los botones de clasificación, cancelación y reset
    JPanel classifyPanel = new JPanel();
    classifyPanel.add(m_NumIterations);
    classifyPanel.add(m_BuildButton);
    classifyPanel.add(m_CancelBuildButton);
    classifyPanel.add(m_ResetButton);
    JPanel dataLabelPane = new JPanel(new GridLayout(2, 1));
    dataLabelPane.add(m_TrainInstancesLabel);
    dataLabelPane.add(m_TestInstancesLabel);
    JPanel southPanel = new JPanel(new BorderLayout());
    southPanel.add(classifyPanel, BorderLayout.NORTH);
    southPanel.add(dataLabelPane, BorderLayout.SOUTH);

    //Lo ponemos todo junto
    setLayout(new BorderLayout());
    add(classifierSelectorPanel, BorderLayout.NORTH);
    add(m_Tabs, BorderLayout.CENTER);
    add(southPanel, BorderLayout.SOUTH);
  }

  /**
   * (Des)Habilitar las acciones y los componentes pertinentes; este método se
   * encarga de evitar que cualquier cambio se pueda realizar en el clasificador y
   * en las instancias de entrenamiento y/o test mientras estamos realizando
   * tareas de clasificación
   *
   * @param enable True para habilitar, false para deshabilitar
   */
  public void setEnableClassifyActions(boolean enable){

    final boolean isClassifying;

    //Habilitamos y deshabilitamos las acciones y los componentes pertinentes
    if(m_Classifier instanceof IterativeUpdatableClassifier){
      isClassifying = null != m_BoosterWorker;
      m_BoosterIterateAction.setEnabled(enable);
      if(isClassifying)
        m_CancelBoosterIterateAction.setEnabled(!enable);
    }
    else{
      isClassifying = null != m_CBThread;
      m_BuildClassifierAction.setEnabled(enable);
      if(isClassifying)
        m_CancelBuildClassifierAction.setEnabled(!enable);
    }

    m_BuildButton.setEnabled(enable);
    if(isClassifying){
      m_CancelBuildOrIterateAction.setEnabled(!enable);
      m_CancelBuildButton.setEnabled(!enable);
    }
    m_ResetAction.setEnabled(enable);
    //No queremos que nadie cambie el clasificador mientras lo estamos construyendo
    m_ClassifierSelector.setEnabled(enable);
  }

  /**
   *  El thread en que realizaremos la clasificación; sólo puede ser detenido
   * abruptamente, mediante el método (desaprobado) stop (ver los comentarios
   * de la definición del cuerpo de m_CancelBuildClassifierAction en
   * el método setUpActions()).
   */
  private class ClassifierBuildThread extends Thread{

    public void run(){

      /** El clasificador que tratamos de construir */
      Classifier toBuildClassifier = getClassifier();

      try{
        //Jefe, voy a empezar un trabajillo más o menos gordo
        notifyMediators(CLASSIFIER_BUILD_START, toBuildClassifier);

        //Siempre releemos las instancias --> no es necesario pulsar reset
        chooseInstances();

        //Deshabilitamos las acciones y los componentes pertinentes
         setEnableClassifyActions(false);

        //El trabajillo gordo, construir el clasificador
        toBuildClassifier.buildClassifier(m_TrainData);

        notifyMediators(CLASSIFIER_BUILD_FINISH, toBuildClassifier);

        //Evaluar automáticamente
        if (m_AutoEvalueCB.isSelected())
          evalue(toBuildClassifier);
      }
      catch (InterruptedException ex){
        System.err.println("Salida por interrupt, no me lo puedo ni de creer " + ex.toString());
        ex.printStackTrace();
        notifyMediators(CLASSIFIER_BUILD_CANCELED, toBuildClassifier);
      }
      catch (Exception ex){
        System.err.println(ex.toString());
        notifyMediators(CLASSIFIER_BUILD_CANCELED, toBuildClassifier);
      }
      finally{
        //Habilitamos las acciones y los componentes pertinentes
        setEnableClassifyActions(true);
        //Marcamos que podemos volver a crear un clasificador
        m_CBThread = null;
      }
    }
  }//FIN de ClassifierBuildThread

  /**
   * Panel que contiene la gráfica de errores del booster y la barra de progreso
   * que muestra el porcentaje de iteración del mismo; el mayor propósito
   * de esta clase es mejorar la legibilidad.
   */
  private class BoosterPanel extends JPanel{

    /** Una referencia al clasificador actual como booster */
    private IterativeUpdatableClassifier m_Booster;

    /** Se deben guardar los valores umbrales del error teóricos calculados por algunos boosters? */
    private boolean m_ComputeErrorBound = false;

    /** Analizador del booster para el conjunto de entrenamiento */
    private BoosterAnalyzer m_TrainAnalyzer;
    /** Analizador del booster para el conjunto de test */
    private BoosterAnalyzer m_TestAnalyzer;

    /** La gráfica de errores */
    private ErrorGraph m_ErrorGraph;
    /** El panel en el que se muestra la gráfica de errores */
    private ChartPanel m_ErrorPanel;
    /** Listener que permite elegir entre las posibles series de datos a representar */
    private ErrorGraph.SerieSelectorListener m_SerieSelectorListener;

    /** La barra de progreso para mostrar el porcentaje del proceso de iteración del booster */
    private JProgressBar m_ProgressBar = new JProgressBar();

    /**
     * Bandera que indica si:
     *   - El booster debe volver a ser construido usando, posiblemente, nuevas instancias de entrenamiento y/o test?
     *   - La gráfica de errores debe ser reiniciada
     */
    private boolean m_MustInitialize = true;

    /**
     * Constructor
     *
     * @param booster El primer booster que configura el panel
     */
    public BoosterPanel(IterativeUpdatableClassifier booster){

      //Configuramos todo según el booster
      setBooster(booster);

      //Inicializamos el panel de la gráfica del error
      m_ErrorPanel = new ChartPanel(m_ErrorGraph.getChart());
      m_ErrorPanel.setPreferredSize(new Dimension(320, 240));

      //Distribuimos los componentes en el panel
      layoutComponents();
    }

    /**
     * Resetear y configurar el panel de acuerdo al booster
     *
     * @param booster El nuevo booster a ser utilizado (null para resetear el panel y liberar memoria)
     */
    public void setBooster(IterativeUpdatableClassifier booster){

      //Resetear y liberar memoria; levantar la bandera que indica que se debe comenzar a iterar
      m_Booster = booster;
      resetGraph();

      //Sólo queríamos resetear el problema
      if (m_Booster == null)
        return;
    }

    /**
     * Distribuciñon de los componentes en el panel
     */
    private void layoutComponents(){

      //La gráfica de errores
      setLayout(new BorderLayout());
      add(m_ErrorPanel, BorderLayout.CENTER);

      //Barra de progreso
      JPanel pbPanel = new JPanel(new BorderLayout());
      //Necesario para que no cambie la altura según se pinte o no la etiqueta
      m_ProgressBar.setStringPainted(true);
      m_ProgressBar.setPreferredSize(m_ProgressBar.getPreferredSize());
      m_ProgressBar.setMinimumSize(m_ProgressBar.getPreferredSize());
      m_ProgressBar.setStringPainted(false);
      m_ProgressBar.setToolTipText("Progreso del proceso de iteración de un booster");
      pbPanel.add(m_ProgressBar, BorderLayout.NORTH);
      pbPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Booster iterate progress"),
                                                           BorderFactory.createEmptyBorder(0, 5, 5, 5)));
      add(pbPanel, BorderLayout.SOUTH);
    }

    /**
     * Resetear el gráfico, liberando la memoria reservada para los analizadores del booster
     * y levantando la bandera que indica que se debe volver a comenzar la construcción de un booster
     * (ver {@link #initializeGraph} y {@link #iterate}).
     */
    public void resetGraph(){

      //Nueva gráfica
      m_ErrorGraph = new ErrorGraph();
      if (null == m_ErrorPanel)
        m_ErrorPanel = new ChartPanel(m_ErrorGraph.getChart());
      else
        m_ErrorPanel.setChart(m_ErrorGraph.getChart());

      //Liberar la memoria reservada para los analizadores
      m_TrainAnalyzer = null;
      m_TestAnalyzer = null;

      //Levantar la bandera
      m_MustInitialize = true;
    }

    /**
     * Inicializar el gráfico y los analizadores y bajar la bandera de inicialización.
     */
    private void initializeGraph(){

      //Configuramos el nombre del gráfico
      //TODO: Poner una fuente más bonita
      ArrayList graphTitle = new ArrayList();
      String boosterName = m_Booster.getClass().getName();
      boosterName = boosterName.substring(boosterName.lastIndexOf(".") + 1);
      TextTitle title1 = new TextTitle("Error plot for " + boosterName);
      TextTitle title3 = new TextTitle(m_TrainInstancesLabel.getText() + " //-// " + m_TestInstancesLabel.getText());

      graphTitle.add(title1);
      graphTitle.add(title3);
      m_ErrorGraph.getChart().setTitles(graphTitle);

      //Comprobar las características del problema a analizar
      final boolean existsTrainData = m_TrainData != null && m_TrainData.numInstances() != 0;
      final boolean existsTestData = m_TestData != null && m_TestData.numInstances() != 0;
      final boolean existsCostMatrix = m_CostMatrixGetterMethod != null;

      //Comprobar si se deben cambiar los analizadores de los boosters
      final boolean mustChangeTrainAnalyzer = m_TrainAnalyzer == null || m_TrainAnalyzer.getData() !=  m_TrainData;
      final boolean mustChangeTestAnalyzer = m_TestAnalyzer == null || m_TestAnalyzer.getData() !=  m_TrainData;

      try{
        //Inicializar el analizador para los datos de entrenamiento
        if (existsTrainData && mustChangeTrainAnalyzer){
          m_TrainAnalyzer = new BoosterAnalyzer(m_Booster, m_TrainData, null);
          m_ErrorGraph.setBaTrain(m_TrainAnalyzer);
          m_TrainAnalyzer.setSaveBaseClassifiersCosts(existsCostMatrix);
          m_TrainAnalyzer.setSaveBoosterCosts(existsCostMatrix);
        }
        //Inicializar el analizador para los datos de test
        if (existsTestData && mustChangeTestAnalyzer){
          m_TestAnalyzer = new BoosterAnalyzer(m_Booster, m_TestData, null);
          m_ErrorGraph.setBaTest(m_TestAnalyzer);
          m_TestAnalyzer.setSaveBaseClassifiersCosts(existsCostMatrix);
          m_TestAnalyzer.setSaveBoosterCosts(existsCostMatrix);
        }
      }
      catch (Exception ex){
        System.err.println(ex.toString());
      }

      //Configuramos el diálogo que permite elegir entre las distintas líneas
      //a representar en el gráfico
      m_ErrorPanel.removeChartMouseListener(m_SerieSelectorListener);
      m_SerieSelectorListener = new ErrorGraph.SerieSelectorListener(m_ErrorGraph);
      m_ErrorPanel.addChartMouseListener(m_SerieSelectorListener);

      //Configurar qué líneas podemos representar y cuáles se harán por defecto
      //Usando el conjunto de entrenamiento
      m_ErrorGraph.setRepresentSerie(ErrorGraph.BOOSTER_ERROR, existsTrainData);
      m_ErrorGraph.setRepresentSerie(ErrorGraph.BC_ERROR, existsTrainData);
      m_SerieSelectorListener.setRepresentableSerie(ErrorGraph.BOOSTER_ERROR, existsTrainData);
      m_SerieSelectorListener.setRepresentableSerie(ErrorGraph.BC_ERROR, existsTrainData);
      m_SerieSelectorListener.setRepresentableSerie(ErrorGraph.BOOSTER_COST, existsTrainData && existsCostMatrix);
      m_SerieSelectorListener.setRepresentableSerie(ErrorGraph.BC_COST, existsTrainData && existsCostMatrix);

      //Configurar qué líneas podemos representar y cuáles se harán por defecto
      //Usando el conjunto de text
      m_ErrorGraph.setRepresentSerie(ErrorGraph.BOOSTER_TEST_ERROR, existsTestData);
      m_ErrorGraph.setRepresentSerie(ErrorGraph.BC_TEST_ERROR, existsTestData);
      m_SerieSelectorListener.setRepresentableSerie(ErrorGraph.BOOSTER_TEST_ERROR, existsTestData);
      m_SerieSelectorListener.setRepresentableSerie(ErrorGraph.BC_TEST_ERROR, existsTestData);
      m_SerieSelectorListener.setRepresentableSerie(ErrorGraph.BOOSTER_TEST_COST, existsTestData && existsCostMatrix);
      m_SerieSelectorListener.setRepresentableSerie(ErrorGraph.BC_TEST_COST, existsTestData && existsCostMatrix);

      //Vamos a calcular algún límite teórico del error?
      if (m_Booster instanceof ErrorUpperBoundComputer){
        if (((ErrorUpperBoundComputer) m_Booster).getCalculateErrorUpperBound())
          m_ComputeErrorBound = true;
        else
          m_ComputeErrorBound = false;
      }
      else
        m_ComputeErrorBound = false;

      //Configurar si vamos a representar o no la evolución de alguna medida de límite teórico del
      //error del booster
      if (m_ComputeErrorBound){
        m_ErrorGraph.getSerie(ErrorGraph.BOOSTER_ERROR_BOUND).setName(
          ((ErrorUpperBoundComputer) m_Booster).getErrorUpperBoundName());
        m_SerieSelectorListener.setRepresentableSerie(ErrorGraph.BOOSTER_ERROR_BOUND, true);
        m_ErrorGraph.setRepresentSerie(ErrorGraph.BOOSTER_ERROR_BOUND, true);
      }
      else
        m_SerieSelectorListener.setRepresentableSerie(ErrorGraph.BOOSTER_ERROR_BOUND, false);

      //Para que realmente lo que aparezca reflejado en el panel sea lo que estamos representando
      m_SerieSelectorListener.synchronizeCheckBoxes();

      //Bajamos la bandera de inicialización
      m_MustInitialize = false;
    }

    /**
     * Método que se encarga de realizar las siguientes iteraciones del booster en cuestión,
     * así como de actualizar la línea que representa algún tipo de seguridad
     * teórica acerca del límite máximo de alguna medida del error (que línea más farragosa)
     *
     * @param numIterations El número de iteraciones a realizar
     *
     * @throws InterruptedException Si se cancela el proceso de iteración
     * @throws Exception Si ocurre un error
     */
    private void iterate(int numIterations) throws Exception{

      //Tal y como está programado ahora mismo, este caso no se puede dar
      if (m_Booster == null)
        throw new Exception("BoosterPanel.iterate: El booster es nulo");

      //Para llevar la cuenta de las iteraciones que nos quedan
      int itIndex = 0;

      //Preparamos la barra de progreso
      m_ProgressBar.setMinimum(0);
      m_ProgressBar.setMaximum(numIterations);
      m_ProgressBar.setStringPainted(true);

      //En el caso de que tengamos que volver a crear el booster
      if (m_MustInitialize || m_Booster.getNumIterationsPerformed() == 0){

        //Elegimos las instancias
        chooseInstances();
        //Inicializamos el gráfico y los analizadores, y bajamos la bandera de inicialización
        initializeGraph();

        //Vamos a realizar la primera iteración
        updatePBStatus(1);

        if (m_Booster instanceof AdaBoostMH){
          ((AdaBoostMH) m_Booster).getBooster().setInitialIterations(1);
          ((AdaBoostMH) m_Booster).buildClassifier(m_TrainData);
        }
        else{
          ((Booster) m_Booster).setInitialIterations(1);
          ((Booster) m_Booster).buildClassifier(m_TrainData);
        }
        //Calculamos, si procede, el límite teórico de alguna medida del error
        if (m_ComputeErrorBound){
          final double errorUpperBound = ((ErrorUpperBoundComputer) m_Booster).getErrorUpperBound();
          m_ErrorGraph.addPoint(ErrorGraph.BOOSTER_ERROR_BOUND,
                                0,
                                errorUpperBound > 1 ? 1 : errorUpperBound);
        }

        //Ya hemos hecho la primera iteración
        itIndex = 1;
        notifyMediators(BOOSTER_FIRST_ITERATION, m_Booster);
      }

      if (m_ComputeErrorBound){
        //Iteramos calculando el límite teórico de alguna medida del error
        int numIterationsperformed = m_Booster.getNumIterationsPerformed();

        for (itIndex++; itIndex <= numIterations; itIndex++){

          //Comprobamos que el usuario no haya cancelado el proceso
          if (Thread.interrupted())
            throw new InterruptedException();

          //Actualizar la barra de progreso
          updatePBStatus(itIndex);

          //Hacer una iteración más
          m_Booster.nextIterations(1);

          //Recuperamos el límite del error para la iteración
          final double errorUpperBound = ((ErrorUpperBoundComputer) m_Booster).getErrorUpperBound();

          //Añadimos el punto a la línea del límite del error en el gráfico
          m_ErrorGraph.addPoint(ErrorGraph.BOOSTER_ERROR_BOUND,
                                numIterationsperformed,
                                errorUpperBound > 1 ? 1 : errorUpperBound);

          //Actualizamos la abcisa para el siguiente punto de la línea del límite del error en el gráfico
          numIterationsperformed++;
        }
      }
      else//No vamos a calcular el límite del error
        for (itIndex++; itIndex <= numIterations; itIndex++){

          //Comprobamos que el usuario no haya cancelado el proceso
          if (Thread.interrupted())
            throw new InterruptedException();

          //Actualizamos la barra de progreso
          updatePBStatus(itIndex);

          //Realizamos una iteración más
          m_Booster.nextIterations(1);
        }
    }

    /**
     * Método que se encarga de actualizar las estadísticas y el gráfico
     * de errores
     *
     * @throws Exception Si existe algún error al actualizar las estadísticas
     */
    private void updateBoosterStatistics() throws Exception{
      //Actualizar las estadísticas
      if (null != m_TrainAnalyzer)
        m_TrainAnalyzer.updateStatistics();
      if (null != m_TestAnalyzer)
        m_TestAnalyzer.updateStatistics();

      //Actualizar el gráfico de errores
      m_ErrorGraph.updateBoosterErrorgraph();
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

    /** @return Un nuevo {@link oaidtb.misc.javaTutorial.SwingWorker} en el que realizar las iteraciones */
    private BoosterIterateWorker getNewBoosterIterateWorker(){
      return new BoosterIterateWorker();
    }

    /**
     * Clase que implementa un SwingWorker para realizar las iteraciones del booster y la actualización
     * de las estadísticas en el hilo de despacho de eventos; ver
     * <a href="http://java.sun.com/products/jfc/tsc/articles/threads/threads1.html#single_thread_rule">
     *el tutorial de java de sun
     * </a>
     */
    private class BoosterIterateWorker extends SwingWorker{

      /**
       * Bandera que indica si el proceso de iteración ha terminado de manera regular
       * (por muerte natural o cancelación del usuario) en cuyo caso se pueden actualizar
       * las estadísticas correctamente
       */
      private boolean finishedOK = false;

      /**
       * Método en el que se crea la imagen
       *
       * @return La imagen o null si no ha sido creada
       */
      public Object construct(){

        try{

          //Notificar a los mediadores que ha comenzado la construcción de la imagen
          notifyMediators(BOOSTER_ITERATE_START, m_Booster);

          //(Des)Habilitamos las acciones y los componentes pertinentes
          setEnableClassifyActions(false);

          //Hacer las siguientes iteraciones
          iterate(Integer.parseInt(m_NumIterations.getText()));

          //Notificar a los mediadores que ha finalizado con éxito la construcción de la imagen
          notifyMediators(BOOSTER_ITERATE_FINISHED, m_Booster);

          //Hemos acabado bien
          finishedOK = true;
        }
        catch (InterruptedException ex){
          //Se ha cancelado la creación por parte del usuario,
          //pero aún podemos actualizar las estadísticas con las iteraciones que se hayan hecho
          finishedOK = true;
        }
        catch (Exception ex){
          //Ha habido un error
          notifyMediators(BOOSTER_ITERATE_CANCELED, m_Booster);
          System.err.println(ex.toString());
        }
        finally{
          //(Des)Habilitamos las acciones y los componentes pertinentes
          setEnableClassifyActions(true);

          //Marcamos que podemos volver a mandar realizar más iteraciones
          m_BoosterWorker = null;
          if (finishedOK)
            return m_Booster;  //SwingWorker.getValue()
          return null;         //SwingWorker.getValue()
        }
      }

      /** Código que ejecuta el SwingWorker tras retornar de construct */
      public void finished(){

        //Reseteamos la barra de progreso
        m_ProgressBar.setStringPainted(false);
        updatePBStatus(0);

        //TODO: Una JCheckBox "autoUpdateStatisticsAndErrorPlot" y un Botón "UpdateStat..."
        //Actualizar las estadísticas sólo si no se ha salido de construct por un error
        if (finishedOK){
          try{
            updateBoosterStatistics();
          }
          catch (Exception ex){
            System.err.println(ex.toString());
          }
          //Evaluar si procede la precisión del clasificador en los conjuntos de entrenamiento y de test
          if (m_AutoEvalueCB.isSelected())
            evalue((Classifier) m_Booster);
        }
      }
    }//FIN de BoosterIterateWorker
  }//FIN de BoosterPanel

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
  // En todos los casos, se pasa el clasificador en cuestión a los mediadores
  /** Indicador de mensaje para los mediadores, acompañado del clasificador en cuestión */
  public final static int
    CLASSIFIER_CHANGED = 0,
    CLASSIFIER_BUILD_START = 10,
    CLASSIFIER_BUILD_FINISH = 11,
    CLASSIFIER_BUILD_CANCELED = 12,
    BOOSTER_ITERATE_START = 20,
    BOOSTER_ITERATE_FINISHED = 21,
    BOOSTER_ITERATE_CANCELED = 22,
    BOOSTER_FIRST_ITERATION = 23;

  /**
   * Clase para testear el panel; por si sola, ya es una aplicación plenamente
   * funcional y útil para comprobar la eficacacia de los clasificadores de weka,
   * especialmente de los boosters creados en el proyecto.
   *
   * Para ejecutarla: java oaidtb.gui.ClassifierPanel$Test
   */
  public static class Test{

    /** El frame "acerca de", con información relativa al proyecto, las licencias e información del sistema */
    private static AboutFrame m_AboutFrame;

    /**
     * Vuelca a memoria las instancias desde un archivo ARFF
     *
     * @param f El archivo del que cargar las instancias
     */
    public static Instances loadInstancesFromFile(final File f, final JButton source){
      try{
        Reader r = new BufferedReader(new FileReader(f));
        Instances tmp = new Instances(r);
        tmp.setClassIndex(tmp.numAttributes() - 1);
        r.close();
        return tmp;
      }
      catch (Exception ex){
        JOptionPane.showMessageDialog(source,
                                      "Couldn't read '"
                                      + f.getName() + "' as an arff file.\n"
                                      + "Reason:\n" + ex.getMessage(),
                                      "Load Instances",
                                      JOptionPane.ERROR_MESSAGE);
        return null;
      }
    }

    /**
     * Testeamos el componente
     * TODO: Hacer que se parezca más a las aplicacions de weka etc., con un panel de log,
     * historial de resultados... reutilizar la implementación del mediador
     * de AllContainerPanel.java
     * TODO: Mejorar la lógica de acción cuando se resetea o se cargan nuevos datos (sobre todo de test)
     *       con un booster ya iterado (cuidar de sincronizar todo: gráfico de errores, analizadores etc.),
     *       que ahora es penosa.
     *
     * @param args ignored.
     */
    public static void main(String[] args){

      try{
        //No estamos en "modo compartido de instancias"
        final ClassifierPanel cp = new ClassifierPanel(null);

        //Configuramos el selector de archivos
        final JFileChooser fileChooser = new JFileChooser(new File(System.getProperty("user.dir")));
        fileChooser.setFileFilter(new ExtensionFileFilter(Instances.FILE_EXTENSION, "Arff data "));
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        //Botón de carga de instancias de entrenamiento
        final JButton loadTrainButton = new JButton("Load train data");
        loadTrainButton.addActionListener(new ActionListener(){
          public void actionPerformed(ActionEvent e){
            if (cp.isBusy())
              return; //Lanzar un mensaje de alerta
            if (fileChooser.showOpenDialog(loadTrainButton) == JFileChooser.APPROVE_OPTION){
              Instances tmp = loadInstancesFromFile(fileChooser.getSelectedFile(), loadTrainButton);
              if (tmp != null){
                cp.setCustomTrainData(tmp);
                cp.m_ResetAction.actionPerformed(null);
              }
            }
          }
        });

        //Botón de carga de instancias de test
        final JButton loadTestButton = new JButton("Load test data");
        loadTestButton.addActionListener(new ActionListener(){
          public void actionPerformed(ActionEvent e){
            if (cp.isBusy())
              return; //Lanzar un mensaje de alerta
            if (fileChooser.showOpenDialog(loadTrainButton) == JFileChooser.APPROVE_OPTION){
              Instances tmp = loadInstancesFromFile(fileChooser.getSelectedFile(), loadTrainButton);
              if (tmp != null){
                cp.setCustomTestData(tmp);
                cp.chooseInstances();
                cp.m_BoosterPanel.initializeGraph();
              }
            }
          }
        });

        //Botones de purga de las instancias de entrenamiento y de test
        final JButton resetTrainDataButton = new JButton("Reset train data");
        final JButton resetTestDataButton = new JButton("Reset test data");

        resetTrainDataButton.addActionListener(new ActionListener(){
          public void actionPerformed(ActionEvent e){
            if (cp.isBusy())
              return; //Lanzar un mensaje de alerta
            cp.setCustomTrainData(null);
            cp.m_ResetAction.actionPerformed(null);
          }
        });

        resetTestDataButton.addActionListener(new ActionListener(){
          public void actionPerformed(ActionEvent e){
            if (cp.isBusy())
              return; //Lanzar un mensaje de alerta
            cp.setCustomTestData(null);
            cp.chooseInstances();
            cp.m_BoosterPanel.initializeGraph();
          }
        });

        //Añadimos los tip-texts
        loadTrainButton.setToolTipText("Cargar instancias de entrenamiento");
        loadTestButton.setToolTipText("Cargar instancias de test");
        resetTrainDataButton.setToolTipText("Purgar las instancias de entrenamiento");
        resetTrainDataButton.setToolTipText("Purgar las instancias de test");

        final JButton about = new JButton("About");

        //Listener para abrir el frame de about
        about.addActionListener(new ActionListener(){

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

        //Ponemos todos los botones en un nuevo panel
        final JPanel loadPanel = new JPanel();
        loadPanel.add(loadTrainButton);
        loadPanel.add(loadTestButton);
        loadPanel.add(resetTrainDataButton);
        loadPanel.add(resetTestDataButton);
        loadPanel.add(about);

        //Creamos el frame de la aplicación
        final JFrame jf = new JFrame("Classifier Panel");
        jf.getContentPane().setLayout(new BorderLayout());
        jf.getContentPane().add(cp, BorderLayout.CENTER);
        jf.getContentPane().add(loadPanel, BorderLayout.SOUTH);
        jf.addWindowListener(new WindowAdapter(){
          public void windowClosing(WindowEvent e){
            jf.dispose();
            System.exit(0);
          }
        });
        jf.pack();
        jf.setSize(800, 600);
        jf.setVisible(true);
      }
      catch (Exception ex){
        ex.printStackTrace();
      }
    }
  }
}