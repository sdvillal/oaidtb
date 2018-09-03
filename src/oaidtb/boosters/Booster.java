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
 *    Booster.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.boosters;

import oaidtb.misc.CustomOrderDefiner;
import weka.classifiers.Classifier;
import weka.classifiers.DistributionClassifier;
import weka.core.*;

import java.beans.PropertyDescriptor;
import java.util.Enumeration;
import java.util.Vector;

/**
 * Abstract class defining the structure ("iterative & interactive") and common options
 * to our boosting implementations.
 *
 * Partially based in "AdaBoostM1" weka class.
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public abstract class Booster extends DistributionClassifier
  implements OptionHandler, WeightedInstancesHandler, IterativeUpdatableClassifier{

  //TODO Implement setMaxNumberTrainingInstances (method for faster resize of m_TrainData).

  /** The model base classifier to use. We initialize it (initial value for the weka gui). Inefficient? */
  protected Classifier m_Classifier = new weka.classifiers.trees.DecisionStump();

  /** The number of boost iterations to perform on first call (buildClassifier).*/
  protected int m_InitialIterations = 10;

  /** The number of successfully generated base classifiers (ie. the number of performed iterations). */
  protected int m_NumIterations;

  /** Is the booster ready to iterate? (Have we successffully called buildClassifier?). */
  protected boolean m_BoosterReady;

  /** Debugging mode, gives extra output if true
   *  Para mejorar la eficiencia, eliminarlo de las opciones y hacerlo una constante para que el
   *  compilador pueda optimizarlo eliminando o añadiendo el código.
   */
  protected boolean m_Debug;

  /** Use boosting with resampling? */
  protected boolean m_UseResampling;

  /** Seed for boosting with resampling. */
  protected int m_ResampleSeed = 1;

  /** The number of instances in the original training set.*/
  protected int m_NumInstances;

  /** The training instance set. */
  protected Instances m_TrainData;

  /** A small constant used to avoid degenerated cases (division by zero error). */
  protected final static double NO_DIVISION_BY_ZERO = weka.core.Utils.SMALL;

  /** What will be the instances sum of weights at each iteration when normalizing?. */
  protected double m_NormFactor = 0;

  /** Constant indicating that the weights sum will be normalized to the initial weights sum. */
  protected final static int SUM_OF_WEIGHTS_NORM_FACTOR = 0;
  /** Constant indicating that the weights sum will be normalized to a custom number. */
  protected final static int CUSTOM_NORM_FACTOR = 1;
  /** Constant indicating that the weights sum will not be normalized */
  protected final static int NOT_NORMALIZE = 2;

  /** What will be the normalization factor?. */
  protected int m_NormFactorUsed = SUM_OF_WEIGHTS_NORM_FACTOR;

  /**
   * Returns an enumeration describing the available options
   *
   * @return an enumeration of all the available options
   */
  public Enumeration listOptions(){

    Vector newVector = new Vector(7);

    newVector.addElement(new Option(
      "\tTurn on debugging output.",
      "D", 0, "-D"));

    newVector.addElement(new Option(
      "\tNumber of boost iterations on first call.\n"
      + "\t(default 10)",
      "I", 1, "-I <num>"));

    newVector.addElement(new Option(
      "\tFull name of classifier to boost.\n"
      + "\teg: weka.classifiers.NaiveBayes",
      "W", 1, "-W <class name>"));

    newVector.addElement(new Option(
      "\tUse resampling for boosting.",
      "Q", 0, "-Q"));

    newVector.addElement(new Option(
      "\tSeed for resampling. (Default 1)",
      "S", 1, "-S <num>"));

    newVector.addElement(new Option(
      "\tNormalization factor (sum of weights will be this). There are three options:"
      + "\n\t- If less than 0 then the sum will not be normalized."
      + "\n\t- If equal to 0 then the sum will be normalized to the initial sum of weights (usually the number of instances)."
      + "\n\t- If greater than 0 then the sum will be normalized to that value."
      + "\n (default == 0).",
      "N", 1, "-N <normFactor>"));

    if ((m_Classifier != null) &&
      (m_Classifier instanceof OptionHandler)){
      newVector.addElement(new Option(
        "",
        "", 0, "\nOptions specific to classifier "
               + m_Classifier.getClass().getName() + ":"));
      Enumeration enum = ((OptionHandler) m_Classifier).listOptions();
      while (enum.hasMoreElements()){
        newVector.addElement(enum.nextElement());
      }
    }
    return newVector.elements();
  }


  /**
   * Parses a given list of options. Valid options are:<p>
   *
   * -D <br>
   * Turn on debugging output.<p>
   *
   * -W classname <br>
   * Specify the full class name of a classifier as the basis for
   * boosting (required).<p>
   *
   * -I num <br>
   * Set the number of boost iterations on first call to BuildClassifier (default 10). <p>
   *
   * -Q <br>
   * Use resampling instead of reweighting.<p>
   *
   * -S seed <br>
   * Random number seed for resampling (default 1).<p>
   *
   * -N normFactor <br>
   * Normalization policy and factor; options: <PRE>
   *
   *   - If <0  --> Sum of weights at each iteration will not be normalized
   *   - If ==0 --> Sum of weights at each iteration will be the original sum of weights in the training set
   *   - If >0  --> Sum of weights at each iteration will be normFactor
   * </PRE>
   *
   * Options after -- are passed to the designated classifier.<p>
   *
   * @param options the list of options as an array of strings
   * @exception Exception if an option is not supported
   */
  public void setOptions(String[] options) throws Exception{

    setDebug(Utils.getFlag('D', options));

    String boostIterations = Utils.getOption('I', options);
    if (boostIterations.length() != 0){
      setInitialIterations(Integer.parseInt(boostIterations));
    }
    else{
      setInitialIterations(10);
    }

    setUseResampling(Utils.getFlag('Q', options));

    String seedString = Utils.getOption('S', options);
    if (seedString.length() != 0){
      setResampleSeed(Integer.parseInt(seedString));
    }
    else{
      setResampleSeed(1);
    }

    String classifierName = Utils.getOption('W', options);
    if (classifierName.length() == 0){
      throw new Exception("A classifier must be specified with"
                          + " the -W option.");
    }
    setClassifier(Classifier.forName(classifierName,
                                     Utils.partitionOptions(options)));

    String normFactor = Utils.getOption('N', options);
    if (normFactor.length() != 0)
      setNormFactor(Double.parseDouble(normFactor));
  }

  /**
   * Gets the current settings of the Classifier.
   *
   * @return an array of strings suitable for passing to setOptions
   */
  public String[] getOptions(){

    String[] classifierOptions = new String[0];
    if ((m_Classifier != null) && (m_Classifier instanceof OptionHandler))
      classifierOptions = ((OptionHandler) m_Classifier).getOptions();


    String[] options = new String[classifierOptions.length + 12];
    int current = 0;
    if (getDebug()){
      options[current++] = "-D";
    }
    if (getUseResampling()){
      options[current++] = "-Q";
    }
    options[current++] = "-I";
    options[current++] = "" + getInitialIterations();
    options[current++] = "-S";
    options[current++] = "" + getResampleSeed();
    options[current++] = "-N";
    if (m_NormFactorUsed == SUM_OF_WEIGHTS_NORM_FACTOR)
      options[current++] = "" + 0;
    else if (m_NormFactorUsed == NOT_NORMALIZE)
      options[current++] = "" + -1;
    else
      options[current++] = "" + getNormFactor();


    if (getClassifier() != null){
      options[current++] = "-W";
      options[current++] = getClassifier().getClass().getName();
    }

    options[current++] = "--";

    System.arraycopy(classifierOptions, 0, options, current,
                     classifierOptions.length);
    current += classifierOptions.length;
    while (current < options.length)
      options[current++] = "";

    return options;
  }

  /**
   * Set the classifier for boosting.
   *
   * @param newClassifier the Classifier to use.
   */
  public void setClassifier(Classifier newClassifier){

    m_Classifier = newClassifier;
  }

  /**
   * Get the classifier used as the classifier
   *
   * @return the classifier used as the base classifier.
   */
  public Classifier getClassifier(){

    return m_Classifier;
  }

  /**
   * Get the classifier used as the base classifier in the specified iteration
   *
   * @param iterationIndex the iteration
   *
   * @return the classifier used as the base classifier
   *
   * @throws Exception if iterationIndex is incorrect
   */
  public abstract Classifier getClassifier(int iterationIndex) throws Exception;


  /**
   * Set the maximum number of boost iterations
   */
  public void setInitialIterations(int maxIterations){

    m_InitialIterations = maxIterations;
  }

  /**
   * Get the number of boost iterations on first call to BuildClassifier.
   *
   * @return The maximum number of boost iterations on first call to BuildClassifier.
   */
  public int getInitialIterations(){

    return m_InitialIterations;
  }

  /**
   * Get the number of iterations performed.
   *
   * @return The number of iterations performed.
   */
  public int getNumIterationsPerformed(){
    return m_NumIterations;
  }

  /**
   * Set seed for resampling.
   *
   * @param seed the seed for resampling
   */
  public void setResampleSeed(int seed){

    m_ResampleSeed = seed;
  }

  /**
   * Get seed for resampling.
   *
   * @return the seed for resampling
   */
  public int getResampleSeed(){

    return m_ResampleSeed;
  }

  /**
   * Set debugging mode
   *
   * @param debug true if debug output should be printed
   */
  public void setDebug(boolean debug){

    m_Debug = debug;
  }

  /**
   * Get whether debugging is turned on
   *
   * @return true if debugging output is on
   */
  public boolean getDebug(){

    return m_Debug;
  }

  /**
   * Is the Booster ready to iterate?.
   *
   * @return true if booster is ready to iterate.
   */
  public boolean isReadyToIterate(){

    return m_BoosterReady;
  }

  /**
   * Set resampling mode
   *
   * @param r true if resampling should be done
   */
  public void setUseResampling(boolean r){

    m_UseResampling = r;
  }

  /**
   * Get whether resampling is turned on
   *
   * @return true if resampling output is on
   */
  public boolean getUseResampling(){

    return m_UseResampling;
  }

  /**
   * Get the training dataset. Warning: training dataset must not be externally changed; instead,
   * addTrainingInstance & deleteTrainingInstance should be used.
   *
   * @return The training dataset.
   */
  public Instances getTrainData(){

    return m_TrainData;
  }

  /**
   * Set the normalization factor (and policy) which will be used; options: <PRE>
   *
   *   - If <0  --> Sum of weights at each iteration will not be normalized
   *   - If ==0 --> Sum of weights at each iteration will be the original sum of weights in the training set
   *   - If >0  --> Sum of weights at each iteration will be normFactor
   *
   * </PRE>
   *
   * @param normFactor Normalization policy indicator & sum of weights at each iteration if > 0
   */
  public void setNormFactor(double normFactor){
    if (normFactor < 0){
      m_NormFactor = -1;
      m_NormFactorUsed = NOT_NORMALIZE;
    }
    else if (normFactor == 0)
    //m_NormFactor will be calculated every time buildClassifier is called with initialNormFactor method.
      m_NormFactorUsed = SUM_OF_WEIGHTS_NORM_FACTOR;
    else{
      m_NormFactor = normFactor;
      m_NormFactorUsed = CUSTOM_NORM_FACTOR;
    }
  }

  /**
   * Get the normalization factor used (ie. what will be the sum of weights after each iteration).
   *
   * @return The normalization factor.
   */
  public double getNormFactor(){
    return m_NormFactor;
  }

  /** Initializes the normalization factor if it must be the initial sum of weights. */
  protected void initializeNormFactor(){
    if (m_NormFactorUsed == SUM_OF_WEIGHTS_NORM_FACTOR)
      m_NormFactor = m_TrainData.sumOfWeights();

    if (m_Debug)
      switch (m_NormFactorUsed){
        case SUM_OF_WEIGHTS_NORM_FACTOR:
          System.err.println("Weights sum at each iteration will be the original weights sum: " + m_NormFactor);
          break;
        case CUSTOM_NORM_FACTOR:
          System.err.println("Weights sum at each iteration will be: " + m_NormFactor);
          break;
        case NOT_NORMALIZE:
          System.err.println("Weights sum will not be normalized in any way.");
        default :
      }
  }

  /** Initializes the weights if they must sum a custom number. */
  protected void initializeWeights(){
    if (m_NormFactorUsed == CUSTOM_NORM_FACTOR)
      normalizeWeights(m_TrainData.sumOfWeights());
  }

  /**
   * Normalize the weight sum of the train data following the predefined directives.
   *
   * @param weightSum The actual sum of weights of the train data.
   */
  protected final void normalizeWeights(double weightSum){
    if (m_NormFactorUsed != Booster.NOT_NORMALIZE){

      weightSum /= m_NormFactor;
      weightSum += Booster.NO_DIVISION_BY_ZERO;

      //Normalize (it isn´t usually necessary for properly work of the classifiers).
      for (int i = 0; i < m_NumInstances; i++){
        Instance instance = m_TrainData.instance(i);
        instance.setWeight((instance.weight() + Booster.NO_DIVISION_BY_ZERO) / weightSum);
      }
    }
  }

  /**
   * Set the weight of an instance.
   *
   * When a mathematical exceptional value (NaN, Infinite) is passed as the value
   * of the weight of an instance (i.e. Math.exp(Double.POSITIVE_INFINITY)) it probably
   * will spoil the things.
   *
   * TODO: Set up a policy to deal with those values, centralize it in this method or in
   *       ad hoc methods for each afectable operation (exp, log...)
   *
   * @param instance The instance
   * @param weight The weight
   * @throws Exception If weight is an invalid value
   */
  protected final void secureSetWeight(Instance instance, double weight) throws Exception{

    if (Double.isNaN(weight) || Double.isInfinite(weight))
      throw new Exception("The weight value " + weight + " is incorrect.");

    instance.setWeight(weight);
  }

  /**
   * Make numIterations iterations.
   *
   * @param numIterations The number of iterations to perform.
   *
   * @throws Exception If an error occurs (ej. Booster not initialized).
   */
  public void nextIterations(int numIterations) throws Exception{
    if (!m_BoosterReady)
      throw new Exception("Booster is not initialized properly.");
    if ((!m_UseResampling) && (m_Classifier instanceof WeightedInstancesHandler))
      buildClassifierWithWeights(numIterations);
    else
      buildClassifierUsingResampling(numIterations);
  }

  /**
   * Boosting method. Boosts any classifier that can handle weighted
   * instances.
   *
   * @param numIterations The number of iterations to perform.
   *
   * @exception Exception if the classifier could not be built successfully
   */
  protected abstract void buildClassifierWithWeights(int numIterations) throws Exception;

  /**
   * Boosting method. Boosts any classifier using training instances
   * weight for resampling.
   *
   * @param numIterations The number of iterations to perform.
   *
   * @exception Exception if the classifier could not be built successfully
   */
  protected abstract void buildClassifierUsingResampling(int numIterations) throws Exception;

  /**
   * Safely (and thus slower) inputs a new training instance.
   *
   * @param instance The instance to addMemberName to the training set.
   *
   * @throws Exception if instance format isn't compatible with the dataset.
   */
  public void addTrainingInstance(Instance instance) throws Exception{

    if (!m_TrainData.checkInstance(instance))
      throw new Exception("Instance not compatible with input set.");

    instance = (Instance) instance.copy();
    instance.setDataset(m_TrainData);

    if (instance.classIsMissing())
      throw new Exception("Instance has no class defined.");

    m_TrainData.add(instance);
    m_NumInstances++;
  }

  /**
   * Safely (and thus slower) removes a training instance.
   *
   * @param numInstance The index of instance to delete from the training set.
   *
   * @throws Exception if instance number is incorrect or if there is no more training instances.
   */
  public void deleteTrainingInstance(int numInstance) throws Exception{
    if (numInstance < m_NumInstances && m_NumInstances > 1){
      m_TrainData.delete(numInstance);
      m_NumInstances--;
    }
    else
      throw new Exception("Incorrect instance number or unique training instance.");
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
    return (oaidtb.misc.Utils.maxIndex(distributionForInstance(instance)));
  }

  /**
   * Free the memory reserved to the train dataset.
   *
   * WARNING: After a call to this function the booster will not be ready to perform more
   *          iterations.
   */
  public void purgeTraindata(){
    m_TrainData = null;
    m_BoosterReady = false;
  }

  /**
   * Eliminate the last numIterations performed from the final combined hypothesis.
   *
   * <li>Note: To free the memory allocated, this method must be overriden
   * <li>Note: The weights distribution of the training instances will not return back in the time.
   *
   * @param numIterations The number of iterations to get rid of
   * @throws Exception If numIterations parameter is incorrect
   */
  public void purgeIterations(int numIterations) throws Exception{
    if (m_NumIterations < numIterations || numIterations < 0)
      throw new Exception("There aren't so many iterations.");

    m_NumIterations -= numIterations;
  }

  /**
   * Classify the given instance using only the first numIterationsToUse base classifiers
   *
   * @param instance The instance to be classified
   * @param numIterationsToUse Number of base classifiers to use
   * @return The instance clasification distribution
   * @throws Exception If an error happens
   */
  public double[] distributionForInstance(Instance instance, int numIterationsToUse) throws Exception{

    if (numIterationsToUse > m_NumIterations || numIterationsToUse < 0)
      throw new Exception("Invaled numIterations parameter.");

    int swapTmp = m_NumIterations;
    m_NumIterations = numIterationsToUse;

    try{
      double[] distributionForInstance = distributionForInstance(instance);
      m_NumIterations = swapTmp;
      return distributionForInstance;
    }
    catch (Exception e){
      m_NumIterations = swapTmp;
      throw e;
    }
  }

  /**
   * Classify the given instance using only the first numIterationsToUse base classifiers
   *
   * @param instance The instance to be classified
   * @param numIterationsToUse Number of base classifiers to use
   * @return The instance clasification
   * @throws Exception If an error happens
   */
  public double classifyInstance(Instance instance, int numIterationsToUse) throws Exception{

    if (numIterationsToUse > m_NumIterations || numIterationsToUse < 0)
      throw new Exception("Invaled numIterations parameter.");

    int swapTmp = m_NumIterations;
    m_NumIterations = numIterationsToUse;

    try{
      double classForInstance = classifyInstance(instance);
      m_NumIterations = swapTmp;
      return classForInstance;
    }
    catch (Exception e){
      m_NumIterations = swapTmp;
      throw e;
    }
  }

  /**
   * Get the classifier's vote for the instance; the (vectorial) sum of all base classifiers vote vectors must be
   * the final combined hypothesis of the booster (obviously, not normalized).
   *
   * @param instance The instance to be classified
   * @param classifierIndex The base classifier index
   * @return The vote of the base classifier to the overall classifier
   * @throws Exception if an error occurs
   */
  public abstract double[] getClassifierVote(Instance instance, int classifierIndex) throws Exception;

  //--------------------------------------*************************************
  //--------------------- Configure the "GUI side" methods ********************
  //--------------------------------------*************************************

  //--------------------- ToolTips text ********************

  public static String normFactorTipText(){

    return "Set the normalization policy and factor. Options: \n"
      + " - If <0  --> Sum of weights at each iteration will not be normalized\n"
      + " - If ==0 --> Sum of weights at each iteration will be the original sum of weights in the training set\n"
      + " - If >0  --> Sum of weights at each iteration will be normFactor";
  }

  public static String debugTipText(){

    return "Send or not debug information to the error stream.";
  }

  public static String useResamplingTipText(){

    return "Force the booster to use resampling even if the base classifier can handle weights.";
  }

  public static String resampleSeedTipText(){

    return "Set the seed to use when boosting with resampling.";
  }

  public static String classifierTipText(){

    return "The base classifier which will be used.";
  }

  public static String initialIterationsTipText(){

    return "Set the number of iterations to perform in the first call.";
  }

  /**
   * Defines a "visual" order to the bean's properties of this booster.
   *
   * @return The properties order
   */
  protected CustomOrderDefiner getPropertiesOrder(){

    CustomOrderDefiner index = new CustomOrderDefiner();

    index.add("classifier");
    index.add("initialIterations");
    index.add("useResampling");
    index.add("resampleSeed");
    index.add("normFactor");
    index.add("debug");

    return index;
  }

  /**
   * Sorts the properties that will be shown in a customizedWeka.PropertySheetPanel,
   * by default following the order defined by getPropertiesOrder(). It can be used for more
   * ambitious purposes since it will be used instead of getPropertiesDescriptor there, so it will
   * prevail even over an hypothetical customized BeanInfo class.
   *
   * @param properties The properties to be sort (commonly the own class properties).
   *
   * @return The properties sorted (or even changed) according to an specified criterion.
   */
  public PropertyDescriptor[] sortProperties(PropertyDescriptor[] properties){
    return oaidtb.misc.Utils.sortProperties(properties, getPropertiesOrder());
  }
}