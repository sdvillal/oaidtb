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
 *    AdaBoostM1.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.boosters;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.*;

import java.util.Enumeration;
import java.util.Random;
import java.util.Vector;

import oaidtb.misc.CustomOrderDefiner;

/**
 * Class for boosting using AdaBoostM1. For more information see: <p>
 *
 * <a href="http://www.research.att.com/~schapire/papers/FreundSc95.ps.Z">
 * Yoav Freund and Robert E. Schapire:
 *  <i>A decision-theoretic generalization of on-line learning and an application to boosting</i>.
 * Journal of Computer and System Sciences, 55(1):119-139, 1997.
 * </a></p>
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class AdaBoostM1 extends Booster implements MulticlassExtensibleBooster, ErrorUpperBoundComputer{

  /** Must this booster use its own train data copy?. */
  private boolean m_UseOwnTrainData = true;

  /** Base classifiers and the weights of their votes. */
  private WeightedClassifierVector m_Classifiers;

  /** The theoretical training error upper bound. */
  protected double m_ErrorUpperBound;

  /** Calculate or not the training error upper bound. */
  protected boolean m_CalculateErrorUpperBound = false;

  /** Number of classes in the original dataset */
  protected int m_NumClasses;

  /** The value considered as a "too big error" for the training error of the base classifiers. */
  protected double m_TooBigError = 0.5;

  /** Is the "too big error" customized by the user?  (needed for AdaBoostM1W, but added here). */
  protected boolean m_CustomizedBigError = false;

  /** Number of allowed (non) consecutive "too big errors" (maximum of the training error of the base classifiers). */
  protected int m_MaxNumOfTooBigErrors = 1;

  /** Number of "too big errors" left before the algorithm stops. */
  protected int m_TooBigErrorCountDown = 1;

  /** Must we reset the "too big errors countdown" after a base classifier error &lt the "big error"? */
  protected boolean m_TooBigErrorsMustBeConsecutives = true;

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
    if (m_Classifier == null)
      throw new Exception("A base classifier has not been specified.");

    if (m_UseOwnTrainData){

      //Check the correct format of training instances.
      if (data.checkForStringAttributes())
        throw new Exception("Can't handle string attributes!");

      if (data.classAttribute().isNumeric())
        throw new Exception("AdaBoostM1 can't handle a numeric class!");

      //We copy them thus ensuring AdaBoostM1 nor other class mess it up.
      m_TrainData = new Instances(data);
      m_BoosterReady = false;  //No "return" can be done from here.
      m_TrainData.deleteWithMissingClass();
      if (m_TrainData.numInstances() == 0)
        throw new Exception("No train instances without class missing!");
    }
    else
    //We entrust to the "caller class" the correctness of the train data instances.
      m_TrainData = data;

    //Set up the number of classes
    m_NumClasses = data.numClasses();

    //Set up the number of instances.
    m_NumInstances = m_TrainData.numInstances();

    //Initialize instance's weight & m_NormFactor.
    initializeNormFactor();
    initializeWeights();

    //Set up the number of performed iterations.
    m_NumIterations = 0;

    //Initialize the classifiers and their weights array.
    m_Classifiers = new WeightedClassifierVector();

    //We can now perform new iterations.
    m_BoosterReady = true;

    //Initialize the training error upper bound
    initializeErrorUpperBound();

    //Initialize the stop criterion
    if (!m_CustomizedBigError)
      m_TooBigError = defaultTooBigErrorValue();

    m_TooBigErrorCountDown = m_MaxNumOfTooBigErrors;

    //Perform first "InitialIterations" iterations.
    nextIterations(m_InitialIterations);
  }

  /**
   * Boosting method. Boosts any classifier that can handle weighted
   * instances.
   *
   * @param numIterations The number of iterations to perform.
   *
   * @exception Exception if the classifier could not be built successfully or the stop criterion is applied
   */
  protected void buildClassifierWithWeights(int numIterations) throws Exception{

    double beta;
    double epsilon;
    Classifier baseClassifier;
    SerializedObject serializedClassifier = new SerializedObject(m_Classifier);

    if (m_Debug)
      System.err.println("Boosting without resampling.");

    // Do iterations.
    for (numIterations--; numIterations >= 0; numIterations--){

      if (m_Debug)
        System.err.println("Training classifier " + (m_NumIterations + 1));

      //Copy the base classifier.
      baseClassifier = (Classifier) serializedClassifier.getObject();

      // Build the classifier.
      baseClassifier.buildClassifier(m_TrainData);

      // Calculate the error.
      epsilon = calculateError(baseClassifier);

      //Apply the stop criterion
      if (mustStopCriterion(epsilon)){
        if (m_Debug)
          System.err.println("/**** Exit because of stop criterion ****/ ==> error rate = " + epsilon);
        if (m_NumIterations == 0){
          //Use the first base classifier built
          beta = calculateBeta(epsilon);
          m_Classifiers.add(baseClassifier, beta);
          m_NumIterations++;
        }
        //Reset, nextIterations()
        m_TooBigErrorCountDown = m_MaxNumOfTooBigErrors;
//        return;
        throw new Exception("Stop criterion applied: iterate stopped");
      }

      //Determine the weight to assign to this model.
      beta = calculateBeta(epsilon);

      if (m_Debug)
        System.err.println("\terror rate = " + epsilon + "  beta = " + beta);

      //Assign new instances weights
      reweight(baseClassifier, beta);

      //Update the theoretical training error upper bound
      updateErrorUpperBound(epsilon);

      //"Commit"
      m_Classifiers.add(baseClassifier, beta);
      m_NumIterations++;
    }
  }

  /**
   * Boosting method. Boosts any classifier using training instances
   * weight for resampling.
   *
   * @param numIterations The number of iterations to perform.
   *
   * @exception Exception if the classifier could not be built successfully or the stop criterion is applied
   */
  protected void buildClassifierUsingResampling(int numIterations) throws Exception{

    double epsilon, beta;          //Base classifier's error and weight.
    Random randomInstance = new Random(m_ResampleSeed); //Random number generator for resample.
    Instances sample;              //The resampled training dataset.
    Classifier baseClassifier;
    SerializedObject serializedClassifier = new SerializedObject(m_Classifier);

    if (m_Debug)
      System.err.println("Boosting with resampling.");

    // Do iterations
    for (numIterations--; numIterations >= 0; numIterations--){

      if (m_Debug){
        System.err.println("Training classifier " + (m_NumIterations + 1));
      }

      // Resample.
      sample = m_TrainData.resampleWithWeights(randomInstance);

      //Copy the base classifier.
      baseClassifier = (Classifier) serializedClassifier.getObject();

      // Build the classifier.
      baseClassifier.buildClassifier(sample);

      //Calculate base classifier's error
      epsilon = calculateError(baseClassifier);

      //Apply the stop criterion
      if (mustStopCriterion(epsilon)){
        if (m_Debug)
          System.err.println("/**** Exit because of stop criterion ****/ ==> error rate = " + epsilon);
        if (m_NumIterations == 0){
          beta = calculateBeta(epsilon);
          m_Classifiers.add(baseClassifier, beta);
          m_NumIterations++;
        }
        //Reset, nextIterations()
        m_TooBigErrorCountDown = m_MaxNumOfTooBigErrors;
//        return;
        throw new Exception("Stop criterion applied: iterate stopped");
      }

      //Determine the weight to assign to this model.
      beta = calculateBeta(epsilon);

      if (m_Debug)
        System.err.println("\terror rate = " + epsilon + "  beta = " + beta);

      //Assign new instances weights
      reweight(baseClassifier, beta);

      //Update the theoretical training error upper bound.
      updateErrorUpperBound(epsilon);

      //"Commit"
      m_Classifiers.add(baseClassifier, beta);
      m_NumIterations++;
    }
  }

  /**
   * Calculate a classifiers error as the weight percentage of training instances misclassified.
   *
   * @param baseClassifier The base classifier
   *
   * @return The error in [0,1]
   *
   * @throws Exception If there is a classification error
   */
  private double calculateError(Classifier baseClassifier) throws Exception{

    double epsilon = 0;
    double totalSumOfWeights = 0;
    Instance instance;

    for (int i = 0; i < m_NumInstances; i++){
      instance = m_TrainData.instance(i);
      double weight = instance.weight();
      totalSumOfWeights += weight;
      if (baseClassifier.classifyInstance(instance) != instance.classValue())
        epsilon += weight;
    }

    return epsilon / totalSumOfWeights;
  }

  /**
   * Calculate the classifier's vote weight in the final combined hypothesis based on its error
   *
   * @param error The training error of the base classifier.
   *
   * @return The classifier's vote weight in the final combined hypothesis
   */
  protected double calculateBeta(double error){
    return Math.log((1 - error + NO_DIVISION_BY_ZERO) / (error + NO_DIVISION_BY_ZERO));
  }

  /**
   * Assign new weight to every instance in the training dataset based
   * in the classifier's vote weight and if it classifies correctly the instance.
   *
   * We use a "conservative boosting approach", only increasing the weights of misclassified
   * instances. This update will be exp(beta) if beta is &gt 0 or exp (-beta) otherwise.
   *
   * @param baseClassifier The base classifier
   * @param beta Factor in term of which misclassified training instances weight will be updated
   *
   * @throws Exception If there is a classification error
   */
  protected void reweight(Classifier baseClassifier, double beta) throws Exception{

    double newWeightsSum = 0;

    if (beta > 0)  //error < 0.5
      beta = Math.exp(beta);
    else  //error > 0.5; we still increase the weights of misclassified instances
      beta = Math.exp(-beta);

    for (int i = 0; i < m_NumInstances; i++){

      Instance instance = m_TrainData.instance(i);
      double weight = instance.weight();

      if (instance.classValue() != baseClassifier.classifyInstance(instance))
        instance.setWeight(weight * beta);

      newWeightsSum += weight;
    }

    normalizeWeights(newWeightsSum);
  }

  /**
   * Return the class predicted (&lt0 == class 0 and &gt0 == class 1) and the
   * confidence of this prediction (its absolute value).
   *
   * @param instance The instance to be classified
   *
   * @return The class predicted and its confidence.
   *
   * @throws Exception if the instance can´t be classified succesfully.
   */
  public double confidenceAndSign(Instance instance) throws Exception{

    double confidenceAndSign = 0;

    for (int i = 0; i < m_NumIterations; i++)
      if(m_Classifiers.get(i).classifyInstance(instance) == 0)
        confidenceAndSign -= m_Classifiers.getWeight(i);
      else
        confidenceAndSign += m_Classifiers.getWeight(i);

    return confidenceAndSign;
  }

  /**
   * Return the class predicted (<0 == class 0 and >0 == class 1) and the
   * confidence of this prediction (its absolute value) of the classifier indicated.
   *
   * @param instance The instance to be classified
   * @param classifierIndex The classifier's index
   *
   * @return The class predicted and its confidence.
   *
   * @throws Exception if an error happens.
   */
  public double confidenceAndSign(Instance instance, int classifierIndex) throws Exception{

    if (classifierIndex >= m_NumIterations || classifierIndex < 0)
      throw new Exception("Classifier index is invalid");

      if(m_Classifiers.get(classifierIndex).classifyInstance(instance) == 0)
        return - m_Classifiers.getWeight(classifierIndex);
      else
        return m_Classifiers.getWeight(classifierIndex);
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

    for (int i = 0; i < m_NumIterations; i++)
      distributionForInstance[(int) m_Classifiers.get(i).classifyInstance(instance)] += m_Classifiers.getWeight(i);

    //We need to normalize...
    oaidtb.misc.Utils.secureNormalize(distributionForInstance);

    return distributionForInstance;
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
  public double[] getClassifierVote(Instance instance, int classifierIndex) throws Exception{

    if (classifierIndex >= m_NumIterations || classifierIndex < 0)
      throw new Exception("Classifier index is invalid");

    double[] distributionForInstance = new double[m_NumClasses];
    distributionForInstance[(int) m_Classifiers.get(classifierIndex).classifyInstance(instance)]
      = m_Classifiers.getWeight(classifierIndex);

    return distributionForInstance;
  }

  /** @return The value considered as a "too big error" for base classifiers */
  public double getTooBigError(){
    return m_CustomizedBigError ? m_TooBigError : defaultTooBigErrorValue();
  }

  /**
   * Set the error value which will be considered as the upper limit for the base classifiers
   * training error and will be used to determine the stop criterion of the algorithm.
   *   <li> If &gt1 ==> there is no stop criterion
   *   <li> If &lt=0 ==> the default value will be used
   *
   * @param tooBigError The value
   */
  public void setTooBigError(double tooBigError){
    if (tooBigError <= 0){
      m_CustomizedBigError = false;
      m_TooBigError = defaultTooBigErrorValue();
    }
    else{
      m_CustomizedBigError = true;
      m_TooBigError = tooBigError;
    }
  }

  /**
   * Get the maximum number of "too big errors" occurrences before the algorithm stops;
   * if getTooBigErrorsMustBeConsecutives() is true, they must be consecutive to produce the stop.
   *
   * @return The maximum number of "too big errors" occurrences.
   */
  public int getMaxNumOfTooBigErrors(){
    return m_MaxNumOfTooBigErrors;
  }


  /**
   * Set the maximum number of "too big errors" occurrences before the algorithm stops;
   * if getTooBigErrorsMustBeConsecutives() is true, they must be consecutive to produce the stop.
   *
   * @param maxNumOfTooBigErrors The maximum number of "too big errors" occurrences.
   */
  public void setMaxNumOfTooBigErrors(int maxNumOfTooBigErrors){
    m_MaxNumOfTooBigErrors = maxNumOfTooBigErrors;
  }

  /** @return Must the "too big errors" occurrences be consecutives to produce a stop? */
  public boolean getTooBigErrorsMustBeConsecutives(){
    return m_TooBigErrorsMustBeConsecutives;
  }

  /** @param tooBigErrorsMustBeConsecutives Must the "too big errors" occurrences be consecutives to produce a stop?  */
  public void setTooBigErrorsMustBeConsecutives(boolean tooBigErrorsMustBeConsecutives){
    m_TooBigErrorsMustBeConsecutives = tooBigErrorsMustBeConsecutives;
  }

  /** @return The default value of the error which will be considered as a "too big error". */
  protected double defaultTooBigErrorValue(){
    return 0.5;
  }

  /**
   * If this method return true, the boosting method must stop.
   *
   * @param error The error of the base classifier in the actual boosting iteration
   *
   * @return True if we have reached the stop condition, false otherwise.
   */
  protected boolean mustStopCriterion(double error){
    if (error > m_TooBigError){
      m_TooBigErrorCountDown--;
      if (m_TooBigErrorCountDown == 0)
        return true;
      return false;
    }
    else{
      if (m_TooBigErrorsMustBeConsecutives)
        m_TooBigErrorCountDown = m_MaxNumOfTooBigErrors;
      return false;
    }
  }

  /** @return The error upper bound string (by example, "Training error upper bound.") */
  public String getErrorUpperBoundName(){
    return "Training error upper bound";
  }

  /**
   * Get if the training error upper bound is being calculated.
   *
   * @return true if it's being computed.
   */
  public boolean getCalculateErrorUpperBound(){
    return m_CalculateErrorUpperBound;
  }

  /**
   * Set (only if no iterations have be done) if the training error upper bound must be
   * wether or not calculated.
   *
   * @param b If the training error upper bound must or not be calculated.
   */
  public void setCalculateErrorUpperBound(boolean b){
    if (m_NumIterations == 0)
      m_CalculateErrorUpperBound = b;
  }

  /**
   * Get the training error upper bound.
   *
   * @return The training error upper bound or -1 if no iterations have been done.
   */
  public double getErrorUpperBound(){
    if (m_NumIterations > 0){
      if (m_CalculateErrorUpperBound)
        return Double.isNaN(m_ErrorUpperBound) ? 1 : m_ErrorUpperBound;
      return 1;
    }
    return -1;
  }

  /** Initialize the training error upper bound. */
  protected void initializeErrorUpperBound(){
    m_ErrorUpperBound = 1;
    if (m_CalculateErrorUpperBound && m_Debug)
      System.err.println("Calculating the training error upper bound.");
  }

  /**
   * Update the theoretical training error upper bound.<PRE>
   *
   *  - Note that if the error of the base classifier is greater than 1/2 the bound is undetermined,
   *    and so its calculation will be aborted.
   *
   *  - Note that this is related to the current choice of the classifier's vote weight, so it will
   *    be invalid if this class is extended playing with its formulae (like in AdaBoostM1W);
   *    so the methods related to the training error must be overriden (with a silent noop if no bound
   *    must be calculated and returning 1 the method getErrorBound()).
   * </PRE>
   *
   * @param error The current base classifier's error
   */
  protected void updateErrorUpperBound(double error){
    if (m_CalculateErrorUpperBound && !Double.isNaN(m_ErrorUpperBound)){
      if (error > 0.5){
        m_ErrorUpperBound = Double.NaN;
        if (m_Debug)
          System.err.println("Can't bound the training error upper boud: base classifier's error is greater than 1/2.");
      }
      else{
        m_ErrorUpperBound *= 2 * Math.sqrt(error * (1 - error));
        if (m_Debug)
          System.err.println("\tTEUB--> " + m_ErrorUpperBound);
      }
    }
  }

  /**
   * Get the base classifier built on specified iteration. Be careful of not modifying it.
   *
   * @param numClassifier The number of the base classifier.
   *
   * @return The base classifier.
   *
   * @throws Exception If numClassifier is incorrect (\<0 or >m_NumIterations).
   */
  public Classifier getClassifier(int numClassifier) throws Exception{

    if (numClassifier < 0 || numClassifier >= m_NumIterations)
      throw new Exception("Classifier's number incorrect.");
    //It would be better to return a copy; it's slower too.
    return m_Classifiers.get(numClassifier);
  }

  /**
   * Get the vote's weight of the base classifier built on specified iteration.
   *
   * @param numClassifier The number of the base classifier.
   *
   * @return Classifier's weight.
   *
   * @throws Exception If numClassifier is incorrect or if the booster doesn't asign weights.
   */
  public double getClassifierWeight(int numClassifier) throws Exception{

    if (numClassifier < 0 || numClassifier >= m_NumIterations)
      throw new Exception("Classifier's number incorrect.");
    return m_Classifiers.getWeight(numClassifier);
  }

  /**
   * Must the booster use its own train data copy?
   *
   * @param b True if the booster must use its own train data copy.
   */
  public void setUseOwnTrainData(boolean b){
    m_UseOwnTrainData = b;
  }

  /**
   * Parses a given list of options. Valid options are:<p> <PRE>
   *
   * -B
   * Calculate the training error upper bound (default false).
   *
   * -E bigErrorValue
   * Set the value considered as "too big error" in the (base classifier) training procedure; it will
   * be used as an stop criterion.
   *  - If less or equal than zero, it will be reset to the default value.
   *  - If bigger than 1.0, no stop criterion will be applied.
   *
   * -C maxNumOfBigErrors
   * Set the number of times after that, happened a value considered as "too big error" in the training procedure,
   * the classifier will stop to iterate.
   *  - If less or equal than zero, no stop criterion will be applied.
   *  - Default: 1
   *
   * -V
   * The "big errors occurrences" mustn't need to be consecutives to provoke the stop.
   *
   * Plus the rest of the superclass options.
   * </PRE>
   *
   * @param options the list of options as an array of strings
   *
   * @exception Exception if an option is not supported
   */
  public void setOptions(String[] options) throws Exception{

    super.setOptions(options);

    setCalculateErrorUpperBound(Utils.getFlag('B', options));

    String bigError = Utils.getOption('E', options);
    if (bigError.length() != 0)
      setTooBigError(Double.parseDouble(bigError));

    String bigErrorCountDown = Utils.getOption('C', options);
    if (bigErrorCountDown.length() != 0)
      setMaxNumOfTooBigErrors(Integer.parseInt(bigErrorCountDown));

    setTooBigErrorsMustBeConsecutives(!Utils.getFlag('V', options));
  }

  /**
   * Gets the current settings of the Classifier.
   *
   * @return an array of strings suitable for passing to setOptions
   */
  public String[] getOptions(){

    String[] otherOptions = super.getOptions();

    String[] options = new String[otherOptions.length + 6];

    int current = 0;

    if (getCalculateErrorUpperBound())
      options[current++] = "-B";

    if (m_CustomizedBigError){
      options[current++] = "-E";
      options[current++] = "" + getTooBigError();
    }

    options[current++] = "-C";
    options[current++] = "" + getMaxNumOfTooBigErrors();

    if (!getTooBigErrorsMustBeConsecutives())
      options[current++] = "-V";

    System.arraycopy(otherOptions, 0,
                     options, current,
                     otherOptions.length);

    current += otherOptions.length;
    while (current < options.length){
      options[current++] = "";
    }
    return options;
  }

  /**
   * Returns an enumeration describing the available options
   *
   * @return an enumeration of all the available options
   */
  public Enumeration listOptions(){

    Vector newVector = new Vector();

    newVector.addElement(new Option(
      "\tSet the value considered as \"too big error\" in the (base classifier) training procedure;"
      + " it will be used as an stop criterion.\n"
      + "If less or equal than zero, it will be reset to the default value: " + defaultTooBigErrorValue() + ".\n"
      + "If bigger than 1.0, no stop criterion will be applied.\n",
      "E", 1, "-E <num>"));

    newVector.addElement(new Option(
      "\tSet the number of times after that,"
      + " happened a value considered as a \"too big error\" in the training procedure,"
      + " the classifier will stop to iterate.\n"
      + "If less or equal than zero, no stop criterion will be applied.\n"
      + "default: 1",
      "C", 1, "-C <num>"));

    newVector.addElement(new Option(
      "\tThe \"big errors occurrences\" mustn't need to be consecutives to provoke the stop.\n",
      "V", 0, "-V"));

    newVector.addElement(new Option(
      "\tCalculate the error upper bound.\n",
      "B", 0, "-B"));

    newVector.addElement(new Option("", "", 0, "\nCommon boosters options."));

    Enumeration enum = super.listOptions();
    while (enum.hasMoreElements()){
      newVector.addElement(enum.nextElement());
    }

    return newVector.elements();
  }

  /**
   * Returns description of the boosted classifier.
   *
   * @return description of the boosted classifier as a string
   */
  public String toString(){

    StringBuffer text = new StringBuffer();

    if (m_NumIterations == 0){
      text.append("Booster: No model built yet.\n");
    }
    else if (m_NumIterations == 1){
      text.append("Booster: No boosting possible, one classifier used!\n");
      text.append(m_Classifiers.get(0).toString() + "\n");
    }
    else{
      text.append("AdaBoostM1: Base classifiers and their weights: \n\n");
      for (int i = 0; i < m_NumIterations; i++){
        text.append(m_Classifiers.get(i).toString() + "\n");
        text.append("**Weight: " + m_Classifiers.getWeight(i) + "\n\n");
      }
      text.append("Number of performed Iterations: "
                  + m_NumIterations + "\n");

      if (m_CalculateErrorUpperBound)
        text.append("Training error upper bound: "
                    + getErrorUpperBound() + "\n");
    }

    return text.toString();
  }

  //--------------------------------------*************************************
  //--------------------- Configure the "GUI side" methods ********************
  //--------------------------------------*************************************

  /**
   * Returns a string describing this classifier.
   *
   * @return a description of the filter suitable for
   * displaying in the explorer/experimenter gui
   */
  public static String globalInfo(){

    return "An implementation of AdaBoostM1.";
  }

  //--------------------- ToolTips text ********************

  public String tooBigErrorTipText(){

    return "Set the value considered as \"too big error\" in the (base classifier) training procedure;"
    + " it will be used as an stop criterion.\n"
    + "- If less or equal than zero, it will be reset to the default value: " + defaultTooBigErrorValue() + ".\n"
    + "- If bigger than 1.0, no stop criterion will be applied.\n";
  }

  public static String tooBigErrorsMustBeConsecutivesTipText(){

    return "The \"big errors occurrences\" mustn't need to be consecutives to provoke the stop.\n";
  }

  public static String maxNumOfTooBigErrorsTipText(){

    return "Set the number of times after that,"
      + " happened a value considered as a \"too big error\" in the training procedure,"
      + " the classifier will stop to iterate.\n"
      + " If less or equal than zero, no stop criterion will be applied.\n"
      + "default: 1";
  }

  public String calculateErrorUpperBoundTipText(){

    return "Calculate or not the " +getErrorUpperBoundName() +" (only if no iteration have been done yet).\n" +
      "See Schapire & Freund's paper.";
  }

  /**
   * Defines a "visual" order to the bean's properties of this booster.
   *
   * @return The properties order
   */
  protected CustomOrderDefiner getPropertiesOrder(){

    CustomOrderDefiner cod = new CustomOrderDefiner();
    cod.add("tooBigError");
    cod.add("tooBigErrorsMustBeConsecutives");
    cod.add("maxNumOfTooBigErrors");
    cod.add("calculateErrorUpperBound");

    CustomOrderDefiner otherOptions = super.getPropertiesOrder();
    otherOptions.mergeWith(cod);

    return otherOptions;
  }

  /**
   * Main method for testing this class.
   *
   * @param argv the options
   */
  public static void main(String[] argv){

    try{
      System.out.println(Evaluation.evaluateModel(new AdaBoostM1(), argv));
    }
    catch (Exception e){
      System.err.println(e.getMessage());
    }
  }
}