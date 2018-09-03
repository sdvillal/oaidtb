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
 *    ErrorGraph.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 *    1.1
 *      - Utiliza FastArrayListXYSeries en vez de FastXYSeries
 *      - Documentado completamente
 */

package oaidtb.gui;

import com.jrefinery.chart.*;
import com.jrefinery.chart.tooltips.XYToolTipGenerator;
import com.jrefinery.data.*;
import oaidtb.boosters.Booster;
import oaidtb.boosters.ErrorUpperBoundComputer;
import oaidtb.boosters.costSensitive.AbstractCSB;
import oaidtb.misc.BoosterAnalyzer;
import oaidtb.misc.SimpleDouble;
import oaidtb.misc.SimpleInteger;
import weka.core.Instances;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.FileReader;
import java.util.ArrayList;

/**
 * Clase que encapsula los datos del gráfico de errores de la aplicación;
 * basada en la librería de para crear gráficas
 * <a href="http://www.object-refinery.com/jfreechart/"> JFreeChart
 * </a><p>
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.1 $
 */
public class ErrorGraph{

  /** El gráfico */
  private JFreeChart chart;

  /** El panel en el que se muestra el gráfico */
  private ChartPanel chartPanel;

  /** Los datos a representar */
  private XYSeriesCollection dataset = new XYSeriesCollection();

  /** Las series que representan a cada una de las curvas del gráfico */
  protected FastArrayListXYSeries
    beSerie = new FastArrayListXYSeries("Booster error"),
  bcSerie = new FastArrayListXYSeries("Base classifier error"),
  beTestSerie = new FastArrayListXYSeries("Booster test error"),
  bcTestSerie = new FastArrayListXYSeries("Base classifier test error"),
  beCostSerie = new FastArrayListXYSeries("Booster cost"),
  bcCostSerie = new FastArrayListXYSeries("Base classifier cost"),
  beTestCostSerie = new FastArrayListXYSeries("Booster test cost"),
  bcTestCostSerie = new FastArrayListXYSeries("Base classifier test cost"),
  beErrorBound = new FastArrayListXYSeries("Booster error bound");

  /** Constantes que representan a cada línea del gráfico */
  public static final int
    BOOSTER_ERROR = 0,
  BC_ERROR = 1,
  BOOSTER_TEST_ERROR = 2,
  BC_TEST_ERROR = 3,
  BOOSTER_COST = 4,
  BC_COST = 5,
  BOOSTER_TEST_COST = 6,
  BC_TEST_COST = 7,
  BOOSTER_ERROR_BOUND = 8;

  /** Array (debe corresponderse con las constantes anteriones) de las posibles líneas a representar. */
  protected FastArrayListXYSeries[] seriesArray = new FastArrayListXYSeries[]{
    beSerie,
    bcSerie,
    beTestSerie,
    bcTestSerie,
    beCostSerie,
    bcCostSerie,
    beTestCostSerie,
    bcTestCostSerie,
    beErrorBound
  };

  //Si es true, la serie se representará; si no no.
  private boolean[] representSerie = new boolean[seriesArray.length];

  /** Constructor. Generamos el ChartPanel por defecto.  */
  public ErrorGraph(){

    // create the chart...
    chart = ChartFactory.createXYChart("Error plot", // chart title
                                       "Iteración", // domain axis label
                                       "Error", // range axis label
                                       dataset, // dataset
                                       true     // include legend
    );

    // get a reference to the plot for further customisation...
    XYPlot plot = chart.getXYPlot();

    plot.getHorizontalValueAxis().setAutoRange(true);
    ((NumberAxis) plot.getHorizontalAxis()).setStandardTickUnits(TickUnits.createIntegerTickUnits());
//    ((NumberAxis)plot.getHorizontalAxis()).setMinimumAxisValue(0);

    //Generate the tooltip for each point in the plot
    plot.getItemRenderer().setToolTipGenerator(new XYToolTipGenerator(){
      public String generateToolTip(XYDataset data, int series, int item){
        return String.valueOf(data.getSeriesName(series) + ": " + data.getYValue(series, item));
      }
    });

    chart.setAntiAlias(false);

    // add the chart to a panel...
    chartPanel = new ChartPanel(chart);
  }

  /** @param baTest El analyzador de booster para el conjunto de prueba */
  public void setBaTest(BoosterAnalyzer baTest){
    bcTestSerie.setData(baTest.getBC_Errors());
    bcTestCostSerie.setData(baTest.getBC_Costs());
    beTestSerie.setData(baTest.getBoosterErrors());
    beTestCostSerie.setData(baTest.getBoosterCosts());
  }

  /** @param baTrain El analyzador del booster para el conjunto de entrenamiento */
  public void setBaTrain(BoosterAnalyzer baTrain){
    bcSerie.setData(baTrain.getBC_Errors());
    bcCostSerie.setData(baTrain.getBC_Costs());
    beSerie.setData(baTrain.getBoosterErrors());
    beCostSerie.setData(baTrain.getBoosterCosts());
  }

  /**
   * Comprobar si un número de serie es válido (está en el rango definido)
   *
   * @param serieID El número identificativo de la serie de datos
   * @return true si el número se corresponde con una serie, falso si no
   */
  private boolean validSerieID(int serieID){
    return serieID < seriesArray.length && serieID > -1;
  }

  /**
   * Añade un punto a una serie (si el identificador no es válido, el método
   * retorna sin avisar) del gráfico
   *
   * @param serieID El identificador de la serie
   * @param x La abcisa del punto
   * @param y La ordenada del punto
   */
  public void addPoint(int serieID, double x, double y){
    if (!validSerieID(serieID))
      return;  //throw Exception
    seriesArray[serieID].add(new XYDataPair(x, y));
  }

  /**
   * Mostrar o no el gráfico correspondiente a una determinada serie de datos
   *
   * @param serieID El identificador de la serie
   *
   * @param represent true si la serie se debe mostrar en el gráfico, false si no
   */
  public void setRepresentSerie(int serieID, boolean represent){
    if (!validSerieID(serieID))
      return;  //throw Exception
    if (representSerie[serieID] != represent){
      representSerie[serieID] = represent;
      if (represent){
        dataset.addSeries(seriesArray[serieID]);
        updateBoosterErrorgraph();
        seriesArray[serieID].fireSeriesChanged();
      }
      else
        dataset.removeSeries(seriesArray[serieID]);
    }
  }

  /**
   * Mostrar o no el gráfico correspondiente a una determinada serie de datos
   *
   * @param serieID El identificador de la serie
   *
   * @param represent true si la serie se debe mostrar en el gráfico, false si no
   */
  public void resetSerie(int serieID, boolean represent){
    if (!validSerieID(serieID))
      return;  //throw Exception
    seriesArray[serieID] = new FastArrayListXYSeries(seriesArray[serieID].getName());
  }

  /** @return The default chart panel to show the graph in an application  */
  public ChartPanel getChartPanel(){
    return chartPanel;
  }

  /** @return The chart itself  */
  public JFreeChart getChart(){
    return chart;
  }

  /**
   * @param serieID A valid serie ID
   * @return The serie or null if serieID is erroneous
   */
  public FastArrayListXYSeries getSerie(int serieID){
    if (!validSerieID(serieID))
      return null;  //throw Exception
    return seriesArray[serieID];
  }

  /**
   * Update all the lines of the graph with the info from
   * the BoosterAnalyzer classes
   */
  public void updateBoosterErrorgraph(){

    //Update the graph
    for (int i = 0; i < seriesArray.length; i++){
      if (representSerie[i])
        seriesArray[i].fireSeriesChanged();
    }
  }

  /**
   * Una clase que extiende XYSeries para poder añadir datos a cada una de las líneas del gráfico
   * bastante más rápido; ver las notas del método add
   */
  public static class FastXYSeries extends XYSeries{

    /** Constructor */
    public FastXYSeries(String name){
      super(name);
    }

    /**
     * Adds a dataset item to the series.
     * Presuponemos:
     *   <p> Inserción secuencial (un punto de abcisa menor es insertado antes que otro de abcisa superior)
     *   <p> La llamada a fireSeriesChanged se hace siempre desde el exterior (para no sobrecargar al sistema con
     *       un evento por cada punto metido).
     *
     * <p> Nota: esta es la función que, por lo menos en la versión 1.6 de la clase XYSeries, siempre
     *     se acaba llamado cuando se inserta un nuevo punto.
     *
     * @param pair The (x, y) pair.
     */
    public void add(XYDataPair pair) throws SeriesException{
      data.add(pair);
    }
  }

  /**
   * Una clase que extiende XYSeries para poder añadir datos a cada una de las líneas del gráfico
   * bastante más rápido; utiliza un ArrayList de SimpleDoubles para almacenar los datos, pudiendo
   * este ArrayList ser compartido por más objetos;  ver las notas del método add.
   */
  public static class FastArrayListXYSeries extends XYSeries{

    /** Constructor */
    public FastArrayListXYSeries(String name){
      super(name);
      data = new ArrayList();
    }

    /** @param data El arraylist de datos */
    public void setData(ArrayList data){
      this.data = data;
    }

    /**
     * Returns the x-value at the specified index.
     *
     * @param index The index.
     *
     * @return The x-value.
     */
    public Number getXValue(int index){
      return new Integer(index);
    }

    /**
     * Returns the y-value at the specified index.
     *
     * @param index The index.
     *
     * @return The y-value.
     */
    public Number getYValue(int index){
      return (SimpleDouble) data.get(index);
    }

    /**
     * Return the data pair with the specified index.
     *
     * @param index The index.
     */
    public XYDataPair getDataPair(int index){
      return new XYDataPair(new SimpleInteger(index), (SimpleDouble) data.get(index));
    }

    /**
     * Adds a dataset item to the series.
     * Presuponemos:
     *   <p> Inserción secuencial (un punto de abcisa menor es insertado antes que otro de abcisa superior)
     *   <p> La llamada a fireSeriesChanged se hace siempre desde el exterior (para no sobrecargar al sistema con
     *       un evento por cada punto metido).
     *
     * <p> Nota: esta es la función que, por lo menos en la versión 1.6 de la clase XYSeries, siempre
     *     se acaba llamado cuando se inserta un nuevo punto.
     *
     * @param pair The (x, y) pair.
     */
    public void add(XYDataPair pair) throws SeriesException{
      data.add(new SimpleDouble(pair.getY().doubleValue()));
    }
  }

  /**
   * Una clase que extiende XYSeries para poder añadir datos a cada una de las líneas del gráfico
   * bastante más rápido; utiliza un ArrayList de SimpleDoubles para almacenar los datos, pudiendo
   * este ArrayList ser compartido por más objetos;  ver las notas del método add.
   *
   * Además esta clase permite pasar un faxtor de normalización que se aplicará cada
   * vez que se recuperen los datos.
   *
   * TODO: Utilizar esta clase para crear líneas de costes representando el coste "relativo"
   * en vez del total (al estilo de lo que se hacía en la versión 1.0); permitir elegir
   * entre ambas representaciones en el panel de selección de series.
   */
  public static class FastArrayListXYSeriesWithNormalizationFactor extends FastArrayListXYSeries{

    /** El factor de normalización */
    private double normFactor = 1;

    /** Constructor */
    public FastArrayListXYSeriesWithNormalizationFactor(String name){
      super(name);
    }

    /** @param normFactor El número por el que serán divididos los valores en cada llamada a getXXX    */
    public void setNormFactor(double normFactor){
      if (normFactor != 0)
        this.normFactor = normFactor;
    }

    /**
     * Returns the y-value at the specified index.
     *
     * @param index The index.
     *
     * @return The y-value.
     */
    public Number getYValue(int index){
      return new SimpleDouble(((SimpleDouble) data.get(index)).value / normFactor);
    }

    /**
     * Return the data pair with the specified index.
     *
     * @param index The index.
     */
    public XYDataPair getDataPair(int index){
      return new XYDataPair(new SimpleInteger(index), new SimpleDouble(((SimpleDouble) data.get(index)).value / normFactor));
    }
  }

  /**
   * Un lístener que hace aparecer un menú con las posibles líneas a representar
   * cuando el usuario hace click con el botón izquierdo
   * sobre un panel que contiene la gráfica
   */
  public static class SerieSelectorListener implements ChartMouseListener{

    /** Array (debe corresponderse con las constantes anteriones) de las posibles líneas a representar. */
    private final JCheckBox[] representableSeriesArray;

    /** El panel en el que irán las checkboxes de selección */
    private final JPanel serieSelectorPanel;

    /** El gráfico de error del que vamos a seleccionar las series a representar */
    private final ErrorGraph errorGraph;

    /** Listener que actualiza el gráfico cuando un checkbos es (de)seleccionada */
    private final ItemListener representSerieListener = new ItemListener(){
      public void itemStateChanged(ItemEvent e){
        final JCheckBox source = (JCheckBox) e.getSource();
        int serieIndex;
        for (serieIndex = 0; serieIndex < representableSeriesArray.length; serieIndex++)
          if (source == representableSeriesArray[serieIndex])
            break;
        errorGraph.setRepresentSerie(serieIndex, source.isSelected());
      }
    };

    /**
     * Constructor
     *
     * @param errorGraph El gráfico de error del que vamos a elegir qué series representar
     */
    public SerieSelectorListener(ErrorGraph errorGraph){

      this.errorGraph = errorGraph;

      representableSeriesArray = new JCheckBox[errorGraph.seriesArray.length];
      for (int i = 0; i < representableSeriesArray.length; i++){
        representableSeriesArray[i] = new JCheckBox(errorGraph.seriesArray[i].getName(),
                                                    errorGraph.representSerie[i]);
        representableSeriesArray[i].setEnabled(false);
      }

      serieSelectorPanel = new JPanel(new GridLayout(5, 2));
      for (int i = 0; i < representableSeriesArray.length; i++)
        serieSelectorPanel.add(representableSeriesArray[i]);
      serieSelectorPanel.validate();

      for (int i = 0; i < representableSeriesArray.length; i++)
        representableSeriesArray[i].addItemListener(representSerieListener);
    }

    /**
     * Sincroniza la apariencia (seleccionada o no) de las checkboxes
     * con las líneas que se están representando o no en el gráfico
     */
    public void synchronizeCheckBoxes(){
      for (int i = 0; i < representableSeriesArray.length; i++)
        representableSeriesArray[i].setSelected(errorGraph.representSerie[i]);
    }

    /**
     * Mostrar o no el gráfico correspondiente a una determinada serie de datos
     *
     * @param serieID El identificador de la serie
     *
     * @param representable true si la serie se debe mostrar en el gráfico, false si no
     */
    public void setRepresentableSerie(int serieID, boolean representable){
      if (!errorGraph.validSerieID(serieID))
        return;  //throw Exception
      representableSeriesArray[serieID].setEnabled(representable);
    }


    /**
     * Callback method for receiving notification of a mouse click on a chart.
     *
     * @param event Information about the event.
     */
    public void chartMouseClicked(ChartMouseEvent event){
      ChartPanel source = (ChartPanel) event.getTrigger().getSource();
      if (SwingUtilities.isLeftMouseButton(event.getTrigger())){
        JOptionPane.showMessageDialog(JOptionPane.getFrameForComponent(source),
                                      serieSelectorPanel,
                                      "Select histories to show",
                                      JOptionPane.PLAIN_MESSAGE);
      }
    }

    /**
     * Callback method for receiving notification of a mouse movement on a chart.
     *
     * @param event Information about the event.
     */
    public void chartMouseMoved(ChartMouseEvent event){
    }
  }

  /**
   * Testeamos la clase
   */
  public static class Test{

    /** @param args ignored. */
    public static void main(String[] args){

      try{

        Instances instances = new Instances(new FileReader("./data/uci/vehicle.arff"));
        instances.setClassIndex(instances.numAttributes() - 1);

        Booster booster = new oaidtb.boosters.AdaBoostECC();

        booster.setDebug(true);
        ((ErrorUpperBoundComputer) booster).setCalculateErrorUpperBound(true);
        booster.buildClassifier(instances);

        BoosterAnalyzer ba = new BoosterAnalyzer(booster, instances, AbstractCSB.generateMinorityClassSensitiveCostMatrix(instances, 2));
        ba.setSaveBaseClassifiersCosts(true);
        ba.setSaveBoosterCosts(true);

        ba.initialize();
        ba.updateStatistics();

        booster.nextIterations(10);
        ba.updateStatistics();

        System.err.println(ba.toString());

        JFrame app = new JFrame("Prueba del gráfico");
        ErrorGraph kk = new ErrorGraph();
        kk.setBaTrain(ba);
        kk.updateBoosterErrorgraph();
        app.getContentPane().add(kk.chartPanel);
        app.setSize(200, 200);
        app.pack();
        app.addWindowListener(new WindowAdapter(){
          public void windowClosing(WindowEvent e){
            System.exit(0);
          }
        });
        app.show();
        kk.setRepresentSerie(ErrorGraph.BOOSTER_ERROR, true);
        kk.setRepresentSerie(ErrorGraph.BC_ERROR, true);
        kk.setRepresentSerie(ErrorGraph.BOOSTER_ERROR_BOUND, true);
        kk.setRepresentSerie(ErrorGraph.BOOSTER_COST, true);
        kk.setRepresentSerie(ErrorGraph.BC_COST, true);

        ((ErrorUpperBoundComputer) booster).setCalculateErrorUpperBound(true);
        booster.setDebug(false);
        int countD = 30;
        for (int i = 0; i < 300; i++){
          booster.nextIterations(1);
          double errorB = ((ErrorUpperBoundComputer) booster).getErrorUpperBound();
          kk.addPoint(ErrorGraph.BOOSTER_ERROR_BOUND, i, errorB < 1 ? errorB : 1);
          ba.updateStatistics();
          if (countD-- == 0){
            countD = 30;
            kk.updateBoosterErrorgraph();
          }
        }
        kk.updateBoosterErrorgraph();

      }
      catch (Exception ex){
        ex.printStackTrace();
        System.err.println(ex.getMessage());
      }
    }
  }
}