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
 *    BoosterAnalyzer.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.misc;

import oaidtb.boosters.AdaBoostMH;
import oaidtb.boosters.Booster;
import oaidtb.boosters.IterativeUpdatableClassifier;
import weka.classifiers.Classifier;
import weka.classifiers.CostMatrix;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.OptionHandler;

import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * A simple class which allows to keep tracking of a booster (& base classifiers) evolution.<PRE>
 * Example of use:
 * <code>
 *   ...
 *   Instances instances = new Instances(new FileReader("./data/conus-torus/train.arff"));
 *   Instances testInstances = new Instances(new FileReader("./data/conus-torus/test.arff"));
 *   instances.setClassIndex(instances.numAttributes() - 1);
 *   testInstances.setClassIndex(testInstances.numAttributes() - 1);
 *
 *   Booster booster = new oaidtb.boosters.AdaBoostECC();
 *   booster.buildClassifier(instances);
 *
 *   //Create the booster analyzer
 *   BoosterAnalyzer ba = new BoosterAnalyzer(booster, instances, null);
 *   BoosterAnalyzer baTest = new BoosterAnalyzer(booster, testInstances, null);
 *
 *   //Inform about what we want keep tracking of
 *   ba.setSaveBoosterErrors(true);
 *   ba.setSaveBoosterCosts(false);
 *   baTest.setSaveBaseClassifiersCosts(false);
 *   baTest.setSaveBaseClassifiersErrors(false);
 *
 *   //Initialize the booster analyzer
 *   ba.initialize();
 *   baTest.initialize();
 *
 *   //Update the statistics
 *   ba.updateStatistics();
 *   baTest.updateStatistics();
 *
 *   //Perform a little more iterations
 *   booster.nextIterations(10);
 *
 *   //Update the statistics
 *   ba.updateStatistics();
 *   baTest.updateStatistics();
 *
 *   ...
 * </code>
 * </PRE>
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class BoosterAnalyzer{

  /** El booster */
  private final IterativeUpdatableClassifier m_Booster;

  /** Las instancias sobre las que se recogerán las estadísticas */
  private final Instances m_Data;

  /** Necesario si el booster cambia el esquema de los datos de entrada para que clasificadores como J48 no fallen */
  private Instances m_TrainDataFormat;

  /** La matriz de costos (por defecto, todos los costos valdrán 1) */
  private CostMatrix m_CostMatrix = null;
  /** El método para recuperar la matriz de costos del booster (si existe) */
  private Method m_CostMatrixGetter = null;

  /** El array que contiene los errores del booster */
  private ArrayList m_BoosterErrors = new ArrayList();
  /** El array que contiene los costes del booster */
  private ArrayList m_BoosterCosts = new ArrayList();
  /** El array que contiene los errores del clasificador base */
  private ArrayList m_BC_Errors = new ArrayList();
  /** El array que contiene los costes del clasificador base */
  private ArrayList m_BC_Costs = new ArrayList();

  /** El mayor error alcanzado por el clasificador base */
  private double m_BiggestBC_Error = 0.0;
  /** El mayor error alcanzado por el booster */
  private double m_BiggestBoosterError = 0.0;
  /** El menor error alcanzado por el clasificador base */
  private double m_SmallestBC_Error = 1.0;
  /** El mayor error alcanzado por el booster */
  private double m_SmallestBoosterError = 1.0;
  /** El mayor coste alcanzado por el clasificador base */
  private double m_BiggestBC_Cost = Double.MIN_VALUE;
  /** El mayor coste alcanzado por el booster */
  private double m_BiggestBoosterCost = Double.MIN_VALUE;
  /** El menor coste alcanzado por el clasificador base */
  private double m_SmallestBC_Cost = Double.MAX_VALUE;
  /** El menor coste alcanzado por el booster */
  private double m_SmallestBoosterCost = Double.MAX_VALUE;

  /** El mayor costo de entre el mayor costo alcanzado por el booster y el mayor costo alcanzado por el clasificador base */
  private double m_BiggestCost;

  /** Primera iteración en que se alcanzó el mayor error del clasificador base */
  private int m_BiggestBC_ErrorAt = -1;
  /** Primera iteración en que se alcanzó el mayor error del booster */
  private int m_BiggestBoosterErrorAt = -1;
  /** Primera iteración en que se alcanzó el menor error del clasificador base */
  private int m_SmallestBC_ErrorAt = -1;
  /** Primera iteración en que se alcanzó el menor error del booster */
  private int m_SmallestBoosterErrorAt = -1;
  /** Primera iteración en que se alcanzó el mayor coste del clasificador base */
  private int m_BiggestBC_CostAt = -1;
  /** Primera iteración en que se alcanzó el mayor coste del booster */
  private int m_BiggestBoosterCostAt = -1;
  /** Primera iteración en que se alcanzó el menor coste del clasificador base */
  private int m_SmallestBC_CostAt = -1;
  /** Primera iteración en que se alcanzó el menor coste del booster */
  private int m_SmallestBoosterCostAt = -1;

  /** Las predicciones del booster para cada una de las clases de cada instancia */
  private double[][] m_BC_VotesForInstances;

  /** Computar o no los errores del booster */
  private boolean m_SaveBoosterErrors = true;
  /** Computar o no los costes del booster */
  private boolean m_SaveBoosterCosts = true;
  /** Computar o no los errores del clasificador base */
  private boolean m_SaveBaseClassifiersErrors = true;
  /** Computar o no los costes del clasificador base */
  private boolean m_SaveBaseClassifiersCosts = true;

  /** Saber si ya se ha comenzado la recolección de estadísticas */
  private boolean m_ProcessStarted = false;

  /** El número de iteraciones analizadas hasta el momento */
  private int m_IterationsAnalyzedSoFar = 0;

  /**
   * Constructor
   *
   * @param booster El booster sobre el que se van a recoger estadísticas
   * @param data El conjunto de datos
   * @param costMatrix La matriz de costos (si es null, se aplicará la política definida en {@link #initialize})
   *
   * @throws Exception si los parámetros no son válidos o hay un error de introspección
   */
  public BoosterAnalyzer(IterativeUpdatableClassifier booster, Instances data, CostMatrix costMatrix) throws Exception{

    if (data == null || data.numInstances() == 0)
      throw new Exception("No se ha especificado el conjunto de datos sobre el que trabajar.");

    if (booster == null)
      throw new Exception("No se ha especificado el booster sobre el que trabajar.");

    m_Data = data;
    m_Booster = booster;

    m_CostMatrix = costMatrix;
    if (isCostSensitiveBooster((Classifier) booster))
      m_CostMatrixGetter = oaidtb.misc.Utils.getCostMatrixGetter((Classifier) booster);
  }

  /**
   * Este método debe ser llamado cuando el booster ya haya sido debidamente configurado
   * (se hace automáticamente la primera vez que se llama a {@link #updateStatistics} si no
   * ha sido invocado antes). Sus cometidos son:
   *
   * <p> Inicializar la matriz de costos; si es null, se pide al booster que provea una y si éste no
   * es capaz se usa la matriz por defecto en la que los costes valen siempre 1
   *
   * <p> Comprobar que el clasificador realmente sea capaz de clasificar las instancias
   *
   * <p> Hacer otras inicializaciones
   *
   * @throws Exception Si el clasificador no es capaz de clasificar las instancias
   * @throws Exception Si se falla al invocar el método del booster que provee la matriz de costes
   */
  public void initialize() throws Exception{

    if (!oaidtb.misc.Utils.classifierCanClassify((Classifier) m_Booster, m_Data.instance(0)))
      throw new Exception("Los datos con los que analizar no son compatibles "
                          + "con las instancias de entrenamiento del booster.");

    if (m_SaveBaseClassifiersCosts || m_SaveBaseClassifiersErrors)
      if ((m_CostMatrix == null || m_CostMatrix.size() != m_Data.numClasses()) && m_CostMatrixGetter != null)
        m_CostMatrix = (CostMatrix) m_CostMatrixGetter.invoke(m_Booster, new Object[]{});

    if (m_CostMatrix == null)
// No podemos hacer esto porque getElement(int,int) es final
//      m_CostMatrix = new CostMatrix(0){
//        public double getElement(int row, int column){
//          return 0.0;
//        }
//      };
      m_CostMatrix = new CostMatrix(m_Data.numClasses());

    m_BC_VotesForInstances = new double[m_Data.numInstances()][m_Data.numClasses()];

    if(m_Booster instanceof AdaBoostMH)
      m_TrainDataFormat = new Instances(((AdaBoostMH)m_Booster).getTrainData(), 0);
    else
      m_TrainDataFormat = new Instances(((Booster)m_Booster).getTrainData(), 0);

    m_ProcessStarted = true;
  }

  /**
   * Actualiza las estadísticas, si existen nuevas iteraciones que no han sido utilizadas
   * para tal fin.
   *
   * @throws Exception Si se produce algún error
   */
  public void updateStatistics() throws Exception{
    if(!m_ProcessStarted)
      initialize();

    //Es un booster simple o es un meta-booster?
    if (m_Booster instanceof AdaBoostMH)
      while (m_IterationsAnalyzedSoFar < m_Booster.getNumIterationsPerformed()){
        retrieveStatisticsFromMH(m_IterationsAnalyzedSoFar);
        m_IterationsAnalyzedSoFar++;
      }
    else
      while (m_IterationsAnalyzedSoFar < m_Booster.getNumIterationsPerformed()){
        retrieveStatistics(m_IterationsAnalyzedSoFar);
        m_IterationsAnalyzedSoFar++;
      }
    m_BiggestCost = (m_BiggestBoosterCost > m_BiggestBC_Cost ? m_BiggestBoosterCost : m_BiggestBC_Cost);
  }

  /**
   * Recoge las estadísticas de un booster "convencional"
   *
   * @param itIndex la iteración de la que recoger las estadísticas
   *
   * @throws Exception si algo falla...
   */
  private void retrieveStatistics(final int itIndex) throws Exception{

    int boosterFails = 0;
    double boosterCost = 0;
    int bcFails = 0;
    double bcCost = 0;

    //Nos hacemos con el clasificador base correspondiente
    Classifier baseClassifier = ((Booster) m_Booster).getClassifier(itIndex);

    for (int i = 0; i < m_Data.numInstances(); i++){

      Instance instance = m_Data.instance(i);
      int trueClassValue = (int) instance.classValue();
      int classifiedAs;

      //Calculamos los errores del booster
      if (m_SaveBoosterErrors || m_SaveBoosterCosts){

        Utils.doubleArraysSum(m_BC_VotesForInstances[i],
                              m_Booster.getClassifierVote(instance, itIndex));
        classifiedAs = Utils.maxIndex(m_BC_VotesForInstances[i]);

        if (classifiedAs != trueClassValue){
          boosterFails++;
          boosterCost += m_CostMatrix.getElement(trueClassValue, classifiedAs);
        }
      }

      //Calculamos los errores del clasificador base
      //Un poco ineficiente, dividirlo en dos buclse para realizar esta comprobación sólo una vez
      if (m_SaveBaseClassifiersErrors || m_SaveBaseClassifiersCosts){
        //Los clasificadores de weka no deberían depender para clasificar de que la instancia
        //pertenezca a algún dataset, pero dependen (por ejemplo, J48 pregunta por el número
        //de clases de la instancia a clasificar...)
        instance = new Instance(instance);
        instance.setDataset(m_TrainDataFormat);
        classifiedAs = (int) baseClassifier.classifyInstance(instance);
        if (classifiedAs != trueClassValue){
          bcFails++;
          bcCost += m_CostMatrix.getElement(trueClassValue, classifiedAs);
        }
      }
    }
    saveStatistics(itIndex, boosterFails, boosterCost, bcFails, bcCost);
  }

  /**
   * Recoge las estadísticas de un meta-booster
   *
   * @param itIndex la iteración de la que recoger las estadísticas
   *
   * @throws Exception si algo falla...
   */
  private void retrieveStatisticsFromMH(final int itIndex) throws Exception{

    int boosterFails = 0;
    double boosterCost = 0;
    int bcFails = 0;
    double bcCost = 0;

    AdaBoostMH mh = (AdaBoostMH) m_Booster;
    //Nos hacemos con los clasificadores base
    Classifier[] baseClassifiers = new Classifier[mh.getNumClasses()];
    for (int j = 0; j < mh.getNumClasses(); j++)
      baseClassifiers[j] = mh.getBooster(j).getClassifier(itIndex);

    for (int i = 0; i < m_Data.numInstances(); i++){

      Instance instance = m_Data.instance(i);
      int trueClassValue = (int) instance.classValue();
      int classifiedAs;

      if (m_SaveBoosterErrors || m_SaveBoosterCosts){
        Utils.doubleArraysSum(m_BC_VotesForInstances[i],
                              m_Booster.getClassifierVote(instance, itIndex));
        classifiedAs = Utils.maxIndex(m_BC_VotesForInstances[i]);
        if (classifiedAs != trueClassValue){
          boosterFails++;
          boosterCost += m_CostMatrix.getElement(trueClassValue, classifiedAs);
        }
      }

      //Calculamos los errores del clasificador base
      //Consideramos el número de fallos total del clasificador base
      //como la suma de los errores de cada CB en la iteración
      //clasificando la clase correspondiente
      if (m_SaveBaseClassifiersErrors || m_SaveBaseClassifiersCosts){

        //PEAZO DE FALLO, 2 HORAS DE DEPURADO POR GAÑÁN
        //instance.setDataset(trainDataFormat);
        //Los clasificadores de weka no deberían depender para clasificar de que la instancia
        //pertenezca a algún dataset, pero dependen (por ejemplo, J48 pregunta por el número
        //de clases de la instancia a clasificar...)
        instance = new Instance(instance);
        instance.setDataset(m_TrainDataFormat);
        classifiedAs = (int) baseClassifiers[trueClassValue].classifyInstance(instance);
        if (classifiedAs != 1){
          bcFails++;
          bcCost += m_CostMatrix.getElement(trueClassValue, classifiedAs);
        }
      }
    }
    saveStatistics(itIndex, boosterFails, boosterCost, bcFails, bcCost);
  }

  /**
   * Salva las estadísticas en los correspondientes arrays
   *
   * @param itIndex El número de iteración
   * @param boosterFails El número de errores del booster
   * @param boosterCost El coste de los errores del booster
   * @param bcFails El número de fallos del clasifixador base
   * @param bcCost El coste de los errores del clasificador base
   */
  private void saveStatistics(final int itIndex,
                              final int boosterFails,
                              final double boosterCost,
                              final int bcFails,
                              final double bcCost){
    //Booster error
    if (m_SaveBoosterErrors){
      double error = (double) boosterFails / (double) m_Data.numInstances();
      m_BoosterErrors.add(itIndex, new SimpleDouble(error));
      if (m_BiggestBoosterError < error){
        m_BiggestBoosterError = error;
        m_BiggestBoosterErrorAt = itIndex;
      }
      if (m_SmallestBoosterError > error){
        m_SmallestBoosterError = error;
        m_SmallestBoosterErrorAt = itIndex;
      }
    }

    //Booster cost
    if (m_SaveBoosterCosts){
      m_BoosterCosts.add(itIndex, new SimpleDouble(boosterCost));
      if (m_BiggestBoosterCost < boosterCost){
        m_BiggestBoosterCost = boosterCost;
        m_BiggestBoosterCostAt = itIndex;
      }
      if (m_SmallestBoosterCost > boosterCost){
        m_SmallestBoosterCost = boosterCost;
        m_SmallestBoosterCostAt = itIndex;
      }
    }

    //Base classifier error
    if (m_SaveBaseClassifiersErrors){
      double error = (double) bcFails / (double) m_Data.numInstances();
      m_BC_Errors.add(itIndex, new SimpleDouble(error));
      if (m_BiggestBC_Error < error){
        m_BiggestBC_Error = error;
        m_BiggestBC_ErrorAt = itIndex;
      }
      if (m_SmallestBC_Error > error){
        m_SmallestBC_Error = error;
        m_SmallestBC_ErrorAt = itIndex;
      }
    }

    //Base classifier cost
    if (m_SaveBaseClassifiersCosts){
      m_BC_Costs.add(itIndex, new SimpleDouble(bcCost));
      if (m_BiggestBC_Cost < bcCost){
        m_BiggestBC_Cost = bcCost;
        m_BiggestBC_CostAt = itIndex;
      }
      if (m_SmallestBC_Cost > bcCost){
        m_SmallestBC_Cost = bcCost;
        m_SmallestBC_CostAt = itIndex;
      }
    }
  }

  /**
   * Método ad-hoc para conocer rápidamente (sin tener que hacer introspección) si
   * un booster puede proporcionar una matriz de costes; de momento, se emplea
   * la norma de que esto es así si y sólo si pertenece al paquete
   * {@link oaidtb.boosters.costSensitive}
   *
   * @param booster El booster en cuestión
   * @return true si el booster pertenece a {@link oaidtb.boosters.costSensitive}
   */
  private boolean isCostSensitiveBooster(Classifier booster){
    if (booster instanceof Booster){
      String name = booster.getClass().getName();
      if (name.startsWith("oaidtb.boosters.costSensitive.")){
        return true;
      }
    }
    return false;
  }

  /**
   * Salvar los costes del clasificador base?
   * (sólo si aún no se ha inicializado el analizador)
   *
   * @param saveBaseClassifiersCosts sí o no
   */
  public void setSaveBaseClassifiersCosts(boolean saveBaseClassifiersCosts){
    if (!m_ProcessStarted)
      m_SaveBaseClassifiersCosts = saveBaseClassifiersCosts;
  }

  /**
   * Salvar los errores del clasificador base?
   * (sólo si aún no se ha inicializado el analizador)
   *
   * @param saveBaseClassifiersErrors sí o no
   */
  public void setSaveBaseClassifiersErrors(boolean saveBaseClassifiersErrors){
    if (!m_ProcessStarted)
      m_SaveBaseClassifiersErrors = saveBaseClassifiersErrors;
  }

  /**
   * Salvar los errores del booster?
   * (sólo si aún no se ha inicializado el analizador)
   *
   * @param saveBoosterCosts sí o no
   */
  public void setSaveBoosterCosts(boolean saveBoosterCosts){
    if (!m_ProcessStarted)
      m_SaveBoosterCosts = saveBoosterCosts;
  }

  /**
   * Salvar los costes del booster?
   * (sólo si aún no se ha inicializado el analizador)
   *
   * @param saveBoosterErrors sí o no
   */
  public void setSaveBoosterErrors(boolean saveBoosterErrors){
    if (!m_ProcessStarted)
      m_SaveBoosterErrors = saveBoosterErrors;
  }

  /**
   * Indicar explícitamente la matriz de costes; si es incorrecta, no
   * se hace nada (quizás habría que lanzar una excepción, pero eso
   * lo dejo para otro día)
   *
   * @param costMatrix La matriz de costes
   */
  public void setCostMatrix(CostMatrix costMatrix){
    if (!m_ProcessStarted && null != costMatrix)
      if (costMatrix.size() == m_Data.numClasses())
        m_CostMatrix = costMatrix;
      else //throw an exception of invalid format
        ;
  }

  /** @return La primera iteración en la que se ha alcanzado el mayor coste del clasificador base hasta ahora */
  public int getBiggestBC_CostAt(){
    return m_BiggestBC_CostAt;
  }

  /** @return La primera iteración en la que se ha alcanzado el mayor error del clasificador base hasta ahora */
  public int getBiggestBC_ErrorAt(){
    return m_BiggestBC_ErrorAt;
  }

  /** @return La primera iteración en la que se ha alcanzado el mayor coste del booster hasta ahora */
  public int getBiggestBoosterCostAt(){
    return m_BiggestBoosterCostAt;
  }

  /** @return La primera iteración en la que se ha alcanzado el mayor error del booster hasta ahora */
  public int getBiggestBoosterErrorAt(){
    return m_BiggestBoosterErrorAt;
  }

  /** @return La primera iteración en la que se ha alcanzado el menor coste del clasificador base hasta ahora */
  public int getSmallestBC_CostAt(){
    return m_SmallestBC_CostAt;
  }

  /** @return La primera iteración en la que se ha alcanzado el menor error del clasificador base hasta ahora */
  public int getSmallestBC_ErrorAt(){
    return m_SmallestBC_ErrorAt;
  }

  /** @return La primera iteración en la que se ha alcanzado el menor coste del booster hasta ahora */
  public int getSmallestBoosterCostAt(){
    return m_SmallestBoosterCostAt;
  }

  /** @return La primera iteración en la que se ha alcanzado el menor error del booster hasta ahora */
  public int getSmallestBoosterErrorAt(){
    return m_SmallestBoosterErrorAt;
  }

  /**
   * @param iterationIndex El número de iteración
   * @return El error del booster en la iteracion
   */
  public double getBoosterError(int iterationIndex){
    return ((SimpleDouble) m_BoosterErrors.get(iterationIndex)).value;
  }

  /**
   * @param iterationIndex El número de iteración
   * @return El error del clasificador base en la iteración
   */
  public double getBaseClassifierError(int iterationIndex){
    return ((SimpleDouble) m_BC_Errors.get(iterationIndex)).value;
  }

  /**
   * @param iterationIndex El número de iteración
   * @return El coste de los errores del booster en dicha iteración
   */
  public double getBoosterCost(int iterationIndex){
    return ((SimpleDouble) m_BoosterCosts.get(iterationIndex)).value;
  }

  /**
   * @param iterationIndex El número de iteración
   * @return El coste de los errores del clasificador base en dicha iteración
   */
  public double getBaseClassifierCost(int iterationIndex){
    return ((SimpleDouble) m_BC_Costs.get(iterationIndex)).value;
  }

  /**
   * Definimos el coste relativo como el cociente del coste de los errores en una determinada
   * iteración entre el mayor coste alcanzado hasta entonces [0,1]
   *
   * @param iterationIndex El número de iteración
   * @return El coste relativo del booster [0,1]
   */
  public double getBoosterRelativeCost(int iterationIndex){
    return ((SimpleDouble) m_BoosterCosts.get(iterationIndex)).value / m_BiggestCost;
  }

  /**
   * Definimos el coste relativo como el cociente del coste de los errores en una determinada
   * iteración entre el mayor coste alcanzado hasta entonces [0,1]
   *
   * @param iterationIndex El número de iteración
   * @return El coste relativo del clasificador base [0,1]
   */
  public double getBaseClassifierRelativeCost(int iterationIndex){
    return ((SimpleDouble) m_BC_Costs.get(iterationIndex)).value / m_BiggestCost;
  }

  /** @return El mayor coste alcanzado por el booster */
  public double getBiggestBoosterCost(){
    return m_BiggestBoosterCost;
  }

  /** @return El mayor coste alcanzado por el clasificador base */
  public double getBiggestBC_Cost(){
    return m_BiggestBC_Cost;
  }

  /** @return El {@link ArrayList} que contiene los costes de los clasificadores base  */
  public ArrayList getBC_Costs(){
    return m_BC_Costs;
  }

  /** @return El {@link ArrayList} que contiene los errores de los clasificadores base  */
  public ArrayList getBC_Errors(){
    return m_BC_Errors;
  }

  /** @return El {@link ArrayList} que contiene los costes del booster en cada iteración  */
  public ArrayList getBoosterCosts(){
    return m_BoosterCosts;
  }

  /** @return El {@link ArrayList} que contiene los errores del booster en cada iteración  */
  public ArrayList getBoosterErrors(){
    return m_BoosterErrors;
  }

  /** @return El número de iteraciones analizadas hasta el momento*/
  public int numIterationsAnalyzed(){
    return m_IterationsAnalyzedSoFar;
  }

  /** @return Los datos sobre los que se está haciendo el análisis */
  public Instances getData(){
    return m_Data;
  }

  /** @return El {@link IterativeUpdatableClassifier} que se está analizando */
  public IterativeUpdatableClassifier getBooster(){
    return m_Booster;
  }

  /** @return una descripción en modo texto, CSV (Comma Separated Values) de las estadísticas  */
  public String toCSV(){
    StringBuffer sb = new StringBuffer();
    ArrayList[] collectedStats = new ArrayList[4];
    int numCollected = 0;
    sb.append(";;;FORMAT: itIndex");
    if(m_SaveBoosterErrors){
      collectedStats[numCollected] = m_BoosterErrors;
      numCollected++;
      sb.append(",booster error");
    }
    if(m_SaveBoosterCosts){
      collectedStats[numCollected] = m_BoosterCosts;
      numCollected++;
      sb.append(",booster cost");
    }
    if(m_SaveBaseClassifiersErrors){
      collectedStats[numCollected] = m_BC_Errors;
      numCollected++;
      sb.append(",base classifier error");
    }
    if(m_SaveBaseClassifiersCosts){
      collectedStats[numCollected] = m_BC_Costs;
      numCollected++;
      sb.append(",base classifier cost");
    }
    sb.append("\n\n");

    for(int i=0; i < m_IterationsAnalyzedSoFar - 1; i++){
      sb.append(i +",");
      for(int j=0; j < numCollected - 1; j++)
        sb.append(collectedStats[j].get(i) +",");
      sb.append(collectedStats[numCollected - 1].get(i) +"\n");
    }

    sb.append(m_IterationsAnalyzedSoFar - 1 +",");
    for(int j=0; j < numCollected - 1; j++)
      sb.append(collectedStats[j].get(m_IterationsAnalyzedSoFar - 1) +",");
    sb.append(collectedStats[numCollected - 1].get(m_IterationsAnalyzedSoFar - 1));

    return sb.toString();
  }

  /** @return Una representación en modo texto de las estadísticas */
  public String toString(){

    if(m_IterationsAnalyzedSoFar == 0)
      return new String("No iterations are analyzed");

    StringBuffer sb = new StringBuffer();

    sb.append("Analysis of " + m_Booster.getClass().getName());
    sb.append("\nwith the options:\n");
    String[] options = ((OptionHandler) m_Booster).getOptions();
    for (int i = 0; i < options.length; i++){
      if (options[i].equals(""))
        break;
      sb.append(options[i] + " ");
    }
    sb.append("\n\n Relation info:\n" + m_Data.toSummaryString() + "\n\n");

    if (m_SaveBoosterErrors && m_SaveBoosterCosts){
      sb.append("=== Booster information: errors & costs ===\n\n");
      sb.append("Biggest error: " + m_BiggestBoosterError + " at iteration: " + m_BiggestBoosterErrorAt);
      sb.append("\nSmallest error: " + m_SmallestBoosterError + " at iteration: " + m_SmallestBoosterErrorAt);
      sb.append("\nBiggest cost: " + m_BiggestBoosterCost + " at iteration: " + m_BiggestBoosterCostAt);
      sb.append("\nSmallest cost: " + m_SmallestBoosterCost + " at iteration: " + m_SmallestBoosterCostAt);
      for (int i = 0; i < m_BoosterErrors.size(); i++)
        sb.append("\n" + i + " ==> " + getBoosterError(i) + ", " + getBoosterCost(i));
    }
    else if (m_SaveBoosterErrors){
      sb.append("=== Booster information: errors ===\n\n");
      sb.append("Biggest error: " + m_BiggestBoosterError + " at iteration: " + m_BiggestBoosterErrorAt);
      sb.append("\nSmallest error: " + m_SmallestBoosterError + " at iteration: " + m_SmallestBoosterErrorAt);
      for (int i = 0; i < m_BoosterErrors.size(); i++)
        sb.append("\n" + i + " ==> " + getBoosterError(i));
    }
    else if (m_SaveBoosterCosts){
      sb.append("=== Booster information: costs ===\n\n");
      sb.append("Biggest cost: " + m_BiggestBoosterCost + " at iteration: " + m_BiggestBoosterCostAt);
      sb.append("\nSmallest cost: " + m_SmallestBoosterCost + " at iteration: " + m_SmallestBoosterCostAt);
      for (int i = 0; i < m_BoosterCosts.size(); i++)
        sb.append("\n" + i + " ==> " + getBoosterCost(i));
    }

    if (m_SaveBaseClassifiersErrors && m_SaveBaseClassifiersCosts){
      sb.append("\n\n=== Base classifiers information: errors & costs ===\n\n");
      sb.append("Biggest error: " + m_BiggestBC_Error + " at iteration: " + m_BiggestBC_ErrorAt);
      sb.append("\nSmallest error: " + m_SmallestBC_Error + " at iteration: " + m_SmallestBC_ErrorAt);
      sb.append("\nBiggest cost: " + m_BiggestBC_Cost + " at iteration: " + m_BiggestBC_CostAt);
      sb.append("\nSmallest cost: " + m_SmallestBC_Cost + " at iteration: " + m_SmallestBC_CostAt);
      for (int i = 0; i < m_BC_Errors.size(); i++)
        sb.append("\n" + i + " ==> " + getBaseClassifierError(i) + ", " + getBaseClassifierCost(i));
    }
    else if (m_SaveBaseClassifiersErrors){
      sb.append("\n\n=== Base classifiers information: errors ===\n\n");
      sb.append("Biggest error: " + m_BiggestBC_Error + " at iteration: " + m_BiggestBC_ErrorAt);
      sb.append("\nSmallest error: " + m_SmallestBC_Error + " at iteration: " + m_SmallestBC_ErrorAt);
      for (int i = 0; i < m_BC_Errors.size(); i++)
        sb.append("\n" + i + " ==> " + getBaseClassifierError(i));
    }
    else if (m_SaveBaseClassifiersCosts){
      sb.append("\n\n=== Base classifiers information: costs ===\n\n");
      sb.append("Biggest cost: " + m_BiggestBC_Cost + " at iteration: " + m_BiggestBC_CostAt);
      sb.append("\nSmallest cost: " + m_SmallestBC_Cost + " at iteration: " + m_SmallestBC_CostAt);
      for (int i = 0; i < m_BC_Costs.size(); i++)
        sb.append("\n" + i + " ==> " + getBaseClassifierCost(i));
    }

    return sb.toString();
  }

  /**
   * Clase de test (pública y estática, así se puede omitir del producto final, pues no es interesante)
   */
  public static class Test{

    /**
     * Testeamos la clase
     *
     * @param args ignored.
     */
    public static void main(String[] args){

      try{

        Instances instances = new Instances(new FileReader("./data/conus-torus/train.arff"));
        Instances testInstances = new Instances(new FileReader("./data/conus-torus/test.arff"));
        instances.setClassIndex(instances.numAttributes() - 1);
        testInstances.setClassIndex(testInstances.numAttributes() - 1);
//      Booster booster = new oaidtb.boosters.costSensitive.CSB0();
//      Booster booster = new oaidtb.boosters.AdaBoostECC();
        AdaBoostMH booster = new AdaBoostMH();

        booster.setDebug(true);
//      ((AbstractCSB) booster).
//        setCostMatrixSource(new SelectedTag(AbstractCSB.DEFAULT_MATRIX, AbstractCSB.TAGS_MATRIX_SOURCE));
        booster.buildClassifier(instances);

        BoosterAnalyzer ba = new BoosterAnalyzer(booster, instances, null);
        BoosterAnalyzer baTest = new BoosterAnalyzer(booster, testInstances, null);

        ba.setSaveBaseClassifiersCosts(false);
        ba.setSaveBoosterCosts(false);
//      ba.setSaveBaseClassifiersErrors(false);
        baTest.initialize();
        ba.initialize();
        ba.updateStatistics();

        booster.nextIterations(10);
        ba.updateStatistics();
        baTest.updateStatistics();

        System.err.println(ba.toString());
        System.err.println(baTest.toString());
        System.err.println(ba.toCSV());
        System.err.println(baTest.toCSV());
       }
      catch (Exception ex){
        ex.printStackTrace();
        System.err.println(ex.getMessage());
      }
    }
  }
}