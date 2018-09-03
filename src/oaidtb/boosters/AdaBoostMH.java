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
 *    AdaBoostMH.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.boosters;

import weka.classifiers.DistributionClassifier;
import weka.classifiers.Evaluation;
import weka.core.*;

import java.util.Enumeration;
import java.util.Vector;

/**
 * Class for boosting using Schapire & Singer's AdaBoostMH. For more information see:<p>
 *
 * <a href="http://www.research.att.com/~schapire/cgi-bin/uncompress-papers/SchapireSi98.ps">
 * Schapire R.E. & & Singer Y.:
 *  <i>Improved boosting algorithms using confidence rated-predictions</i>.
 * Machine Learning, 37(3):297-336, 1999
 * </a></p>
 *
 * This implementation follows the guidelines given in:<p>
 *
 * <a href="http://www-stat.stanford.edu/~jhf/ftp/boost.ps">
 * Jerome Friedman, Trevor Hastie and Robert Tibshirani:
 *  <i>Additive logistic regression: a statistical view of boosting</i>.
 * The Annals of Statistics, 38(2): 337-374, April 2000.
 * </a></p>

 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class AdaBoostMH extends DistributionClassifier
  implements OptionHandler, WeightedInstancesHandler, IterativeUpdatableClassifier{

  /** The number of different classes in the original dataset. */
  protected int m_NumClasses;

  /** The instance classes in the original dataset. */
  protected double[] m_OriginalDataClasses;

  /** The number of instances in the original dataset. */
  protected int m_NumInstances;

  /** The training instances. */
  protected Instances m_TrainData;

  /** The instances weights for the nth booster {m_NumClasse x m_NumInstances}-->R. */
  protected double[][] m_InstanceWeights;

  /** The base (MultiClassExtensible) booster. */
  protected Booster m_Booster = new RealAdaBoost();

  /** The base (MultiClassExtensible) boosters. */
  protected Booster[] m_Boosters = new Booster[0];

  /** Default constructor: inform the base booster it will be used by a meta-algorithm */
  public AdaBoostMH(){
    if (null != m_Booster)
      ((MulticlassExtensibleBooster) m_Booster).setUseOwnTrainData(false);
  }

  /**
   * Calculates the class membership probabilities for the given test instance.
   *
   * @param instance the instance to be classified
   *
   * @return predicted class probability distribution
   *
   * @exception Exception if instance could not be classified successfully
   */
  public double[] distributionForInstance(Instance instance) throws Exception{

    double[] distributionForInstance = new double[m_NumClasses];
    double minValue = 0;

    instance = new Instance(instance);
    instance.setDataset(m_TrainData);

    for (int i = 0; i < m_NumClasses; i++){
      distributionForInstance[i] = ((MulticlassExtensibleBooster) m_Boosters[i]).confidenceAndSign(instance);
      if (minValue > distributionForInstance[i])
        minValue = distributionForInstance[i];
    }

    //Normalize.
    double sumTmp = oaidtb.boosters.Booster.NO_DIVISION_BY_ZERO;

    for (int i = 0; i < m_NumClasses; i++){
      distributionForInstance[i] -= minValue;
      sumTmp += distributionForInstance[i];
    }

    for (int i = 0; i < m_NumClasses; i++)
      distributionForInstance[i] /= sumTmp;

    return distributionForInstance;
  }

  /**
   * Get the classifier's vote for the instance; the (vectorial) sum of all base classifiers vote vectors must be
   * the final combined hypothesis of the booster.
   *
   * @param instance The instance to be classified
   * @param classifierIndex The base classifier index
   * @return The vote of the base classifier to the overall classifier
   * @throws Exception if an error occurs
   */
  public double[] getClassifierVote(Instance instance, int classifierIndex) throws Exception{

    double[] distributionForInstance = new double[m_NumClasses];
    double minValue = 0;

    instance = new Instance(instance);
    instance.setDataset(m_TrainData);

    for (int i = 0; i < m_NumClasses; i++){
      distributionForInstance[i] = ((MulticlassExtensibleBooster) m_Boosters[i]).confidenceAndSign(instance, classifierIndex);
      if (minValue > distributionForInstance[i])
        minValue = distributionForInstance[i];
    }

    for (int i = 0; i < m_NumClasses; i++)
      distributionForInstance[i] -= minValue;

    return distributionForInstance;
  }

  /**
   * Classifies the given test instance. The instance has to belong to a
   * dataset when it's being classified.
   *
   * @param instance the instance to be classified
   * @return the predicted most likely class for the instance or
   * Instance.missingValue() if no prediction is made
   * @exception Exception if an error occurred during the prediction
   */
  public double classifyInstance(Instance instance) throws Exception{

    instance = new Instance(instance);
    instance.setDataset(m_TrainData);

    double maxValue = ((MulticlassExtensibleBooster) m_Boosters[0]).confidenceAndSign(instance);
    double maxIndex = 0;

    for (int i = 1; i < m_NumClasses; i++){
      double currentValue = ((MulticlassExtensibleBooster) m_Boosters[i]).confidenceAndSign(instance);
      if (maxValue < currentValue){
        maxIndex = i;
        maxValue = currentValue;
      }
    }

    return maxIndex;
  }

  /**
   * Boosting method.
   * Reset the model and initialize data.
   *
   * @param data the training data to be used for generating the
   * boosted classifier.
   * @exception Exception if the classifier could not be built successfully
   */
  public void buildClassifier(Instances data) throws Exception{

    //Check glogal settings.
    if (m_Booster == null)
      throw new Exception("A base classifier has not been specified!");

    if (!(m_Booster instanceof MulticlassExtensibleBooster))
      throw new Exception("Base classifier must be a \"Multiclass Extensible Booster\".");

    if (!data.classAttribute().isNominal())
      throw new Exception("AdaBoostMH can't handle not nominal classes!");

    //We copy them thus ensuring AdaBoostMH nor other class mess it up.
    m_TrainData = new Instances(data);
    m_TrainData.deleteWithMissingClass();

    if ((m_NumInstances = m_TrainData.numInstances()) == 0)
      throw new Exception("No train instances without class missing!");

    if (2 > (m_NumClasses = m_TrainData.numClasses()))
      System.err.println("AdaBoostMH could not be useful with binary class problems.");

    ((MulticlassExtensibleBooster) m_Booster).setUseOwnTrainData(false);

    m_OriginalDataClasses = new double[m_NumInstances];

    for (int i = 0; i < m_NumInstances; i++)
      m_OriginalDataClasses[i] = m_TrainData.instance(i).classValue();

    //Creamos el nuevo atributo que representará la "metaclase".
    FastVector my_nominal_values = new FastVector(2);

    my_nominal_values.addElement("0");
    my_nominal_values.addElement("1");

    int classIndex = m_TrainData.classIndex();
    m_TrainData.setClassIndex(-1);

    m_TrainData.deleteAttributeAt(classIndex);
    m_TrainData.insertAttributeAt(new Attribute("PseudoClass", my_nominal_values), classIndex);
    m_TrainData.setClassIndex(classIndex);

    //Create the boosters via serialization
    m_Boosters = new Booster[m_NumClasses];
    SerializedObject serializedBooster = new SerializedObject(m_Booster);
    for (int i = 0; i < m_NumClasses; i++)
      m_Boosters[i] = (Booster) serializedBooster.getObject();

    m_InstanceWeights = new double[m_NumClasses][m_NumInstances];

    boolean showDebugInfo = m_Booster.getDebug();

    for (int i = 0; i < m_NumClasses; i++){
      if (showDebugInfo)
        System.err.println("\n*-*-*-*-*Building booster for class " + i + "*-*-*-*-*");
      relabel(i);
      m_Boosters[i].buildClassifier(m_TrainData);
      saveBoosterWeights(i);
    }
  }

  /**
   * Get the number of iterations performed.
   *
   * @return The number of iterations performed.
   */
  public int getNumIterationsPerformed(){
    if (m_Boosters.length == 0)
      return 0;
    return m_Boosters[0].getNumIterationsPerformed();
  }

  /**
   * Make numIterations iterations.
   *
   * @param numIterations The number of iterations to perform.
   *
   * @throws Exception If an error occurs (ej. Booster not initialized).
   */
  public void nextIterations(int numIterations) throws Exception{

    if (m_Boosters == null || !m_Boosters[0].isReadyToIterate())
      throw new Exception("Booster is not initialized properly.");

    boolean showDebugInfo = m_Booster.getDebug();

    for (int i = 0; i < m_NumClasses; i++){
      if (showDebugInfo)
        System.err.println("\n*-*-*-*-*Updating booster for class " + i + "*-*-*-*-*");
      loadBoosterWeights(i);
      relabel(i);
      m_Boosters[i].nextIterations(numIterations);
      saveBoosterWeights(i);
    }
  }

  /**
   * Assign "PseudoClass = 1" to those instances which originally belongs to class
   * "oldClassValue" and "PseudoClass = 0" to the rest.
   *
   * @param oldClassValue The class value index to be set as 1
   */
  protected void relabel(double oldClassValue){
    for (int i = 0; i < m_NumInstances; i++){
      Instance instance = m_TrainData.instance(i);
      instance.setClassValue(m_OriginalDataClasses[i] == oldClassValue ? 1 : 0);
    }
  }

  /**
   * Load the train instances weights associated to the booster indicated
   *
   * @param boosterIndex The booster index to which those weights are related
   */
  private void loadBoosterWeights(int boosterIndex){
    for (int i = 0; i < m_NumInstances; i++)
      m_TrainData.instance(i).setWeight(m_InstanceWeights[boosterIndex][i]);
  }

  /**
   * Save the train instances weights associated to the booster indicated
   *
   * @param boosterIndex The booster index to which those weights are related
   */
  protected void saveBoosterWeights(int boosterIndex){
    for (int i = 0; i < m_NumInstances; i++)
      m_InstanceWeights[boosterIndex][i] = m_TrainData.instance(i).weight();
  }

  /**
   * Free the memory reserved to the train dataset.
   *
   * WARNING: After a call to this function the booster will not be ready to perform more
   *          iterations.
   */
  public void purgeTrainData(){
    for (int i = 0; i < m_NumClasses; i++)
      m_Boosters[i].purgeTraindata();

    m_TrainData = null;
  }

  /**
   * Eliminate the last numIterations performed from the final combined hypothesis.
   *
   * Note: To free the memory allocated, this method must be overriden
   *
   * @param numIterations The number of iterations to get rid of
   * @throws Exception If numIterations parameter is incorrect
   */
  public void purgeIterations(int numIterations) throws Exception{
    for (int i = 0; i < m_NumClasses; i++)
      m_Boosters[i].purgeIterations(numIterations);
  }

  public void setDebug(boolean debug){
    for (int i = 0; i < m_NumClasses; i++)
      m_Boosters[i].setDebug(debug);
  }

  public boolean getDebug(){
    if (m_Boosters.length > 0)
      return m_Boosters[0].getDebug();
    return false;
  }


  /**
   * Returns an enumeration of all the available options.
   *
   * @return an enumeration of all available options
   */
  public Enumeration listOptions(){
    Vector newVector = new Vector();

    newVector.addElement(new Option(
      "\tSpecify a class (must be an MulticlassExtensibleBooster subclass) for use as the basis for AdaBoostMH."
      + "\n\t(default RealAdaBoost).\n.",
      "B", 1, "-B"));

    if ((m_Booster != null)){
      newVector.addElement(new Option(
        "",
        "", 0, "\nOptions specific to booster "
               + m_Booster.getClass().getName() + ":"));
      Enumeration enum = m_Booster.listOptions();
      while (enum.hasMoreElements()){
        newVector.addElement(enum.nextElement());
      }
    }
    return newVector.elements();
  }

  /**
   * Parses a given list of options. Valid options are:<p>
   *
   * -B classname<br>
   * Specify a class (must be an MulticlassExtensibleBooster subclass) for
   * use as the base Booster.
   * <p>
   *
   * @param options the list of options as an array of strings
   *
   * @exception java.lang.Exception if an option is not supported
   */
  public void setOptions(String[] options) throws Exception{

    String boosterName = Utils.getOption('B', options);
    if (boosterName.length() == 0)
      setBooster((Booster) Utils.forName(MulticlassExtensibleBooster.class,
                                         "oaidtb.boosters.RealAdaBoost", options));
    else
      setBooster((Booster) Utils.forName(MulticlassExtensibleBooster.class, boosterName, options));

    if (null != m_Booster)
      ((MulticlassExtensibleBooster) m_Booster).setUseOwnTrainData(false);
  }

  /**
   * Gets the current settings of the Classifier.
   *
   * @return an array of strings suitable for passing to setOptions
   */
  public String[] getOptions(){

    String[] boosterOptions = new String[0];
    if (m_Booster != null)
      boosterOptions = m_Booster.getOptions();

    String[] options = new String[boosterOptions.length + 3];

    int current = 0;

    if (m_Booster != null){
      options[current++] = "-B";
      options[current++] = "" + m_Booster.getClass().getName();
    }

    options[current++] = "--";

    System.arraycopy(boosterOptions, 0, options, current, boosterOptions.length);
    current += boosterOptions.length;
    while (current < options.length)
      options[current++] = "";

    return options;
  }

  /**
   * Set the booster used to perform the classification.
   *
   * No check about the booster class is made here, but it must be a MulticlassExtensibleBooster.
   *
   * @param booster The base (MulticlassExtensibleBooster) booster class to be used
   */
  public void setBooster(Booster booster){
    m_Booster = booster;
  }

  /** @return The base booster used.  */
  public Booster getBooster(){
    return m_Booster;
  }

  public Instances getTrainData(){
    return m_TrainData;
  }

  /**
   * Get the booster used to classify the instances belonging to the class specified
   *
   * @param boosterIndex The index of the booster (class value)
   *
   * @return The booster
   *
   * @throws Exception If boosterIndex is invalid.
   */
  public Booster getBooster(int boosterIndex) throws Exception{
    if (boosterIndex > m_Boosters.length)
      throw new Exception("Invalid booster index.");

    return m_Boosters[boosterIndex];
  }

  /**
   * Returns a DistributionClassifier that represents the classifier at iteration
   * itIndex
   *
   * <p> The distribution classifier obtained is equivalent to
   * <code> secNormalize(getClassifierVote(instance, itIndex)
   * </code> </p>
   *
   * TODO: Control the persistence of the classifier returned even after the enclosing
   * MH class have been reset or dropped (maintain the references).
   *
   * @param itIndex The iteration index
   *
   * @return The distribution classifier
   *
   * @throws Exception if an error happens
   */
  public DistributionClassifier getClassifier(final int itIndex) throws Exception{

    if (itIndex > getNumIterationsPerformed())
      throw new Exception("Invalid iteration index.");

    return new DistributionClassifier(){
      public double[] distributionForInstance(Instance instance) throws Exception{
        double[] distributionForInstance = new double[m_NumClasses];
        double minValue = 0;

        instance = new Instance(instance);
        instance.setDataset(m_TrainData);

        for (int i = 0; i < m_NumClasses; i++){
          distributionForInstance[i] = ((MulticlassExtensibleBooster) m_Boosters[i]).confidenceAndSign(instance, itIndex);
          if (minValue > distributionForInstance[i])
            minValue = distributionForInstance[i];
        }

        //Normalize.
        double sumTmp = oaidtb.boosters.Booster.NO_DIVISION_BY_ZERO;

        for (int i = 0; i < m_NumClasses; i++){
          distributionForInstance[i] -= minValue;
          sumTmp += distributionForInstance[i];
        }

        for (int i = 0; i < m_NumClasses; i++)
          distributionForInstance[i] /= sumTmp;

        return distributionForInstance;
      }

      public void buildClassifier(Instances data) throws Exception{
        throw new Exception("Read only classifier");
      }
    };
  }

  /** @return The number of classes of the train data */
  public int getNumClasses(){
    return m_NumClasses;
  }

  /**
   * Returns description of the boosted classifier.
   *
   * @return description of the boosted classifier as a string
   */
  public String toString(){

    StringBuffer text = new StringBuffer();

    if (m_Boosters.length == 0 || m_Boosters[0].getNumIterationsPerformed() == 0){
      text.append("AdaBoostOC: No model built yet.\n");
    }
    else{
      text.append("AdaBoostMH: Base boosters. \n\n");

      for (int i = 0; i < m_NumClasses; i++){
        text.append("Booster: " + i + ".\n");
        text.append(m_Boosters[i].toString() + "\n\n");
      }
    }
    return text.toString();
  }

  //--------------------------------------*************************************
  //--------------------- Configure the "GUI side" methods ********************
  //--------------------------------------*************************************

//  //We need to inform about how to manage Booster classes.
  static{
    if(null == java.beans.PropertyEditorManager.findEditor(oaidtb.boosters.Booster.class))
      java.beans.PropertyEditorManager.registerEditor(oaidtb.boosters.Booster.class,
                                                      weka.gui.GenericObjectEditor.class);
  }

  /**
   * Returns a string describing this classifier.
   *
   * @return a description of the filter suitable for
   * displaying in the explorer/experimenter gui
   */
  public String globalInfo(){

    return "An implementation of AdaBoostMH.";
  }

  //--------------------- ToolTips text ********************

  public String boosterTipText(){

    return "The base (MulticlassExtensibleBooster) booster which will be used.";
  }

  /**
   * Main method for testing this class.
   *
   * @param argv the options
   */
  public static void main(String[] argv){

    try{
      System.out.println(Evaluation.evaluateModel(new AdaBoostMH(), argv));
    }
    catch (Exception e){
      System.err.println(e.getMessage());
    }
  }
}