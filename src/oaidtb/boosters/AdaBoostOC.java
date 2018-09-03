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
 *    AdaBoostOC.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.boosters;

import oaidtb.filters.AbstractNominalToOCFilter;
import oaidtb.filters.NominalToRandomPermutationOfEvenSplitOCFilter;
import oaidtb.misc.CustomOrderDefiner;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.*;

import java.util.BitSet;
import java.util.Enumeration;
import java.util.Random;
import java.util.Vector;

/**
 * Class for boosting using Schapire's AdaBoostOC. For more information, see<p>
 *
 * <a href="http://www.research.att.com/%7Eschapire/cgi-bin/uncompress-papers/Schapire97.ps">
 * Robert E. Schapire. <i>Using output codes to boost multiclass learning problems</i>.
 * In Machine Learning: Proceedings of the Fourteenth Internatinal Conferences, pages 313-321, 1997.
 * </a><p>
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class AdaBoostOC extends Booster implements ErrorUpperBoundComputer{

  // TODO: Implement addTrainingInstance, getTrainingInstance.
  // TODO: 1/m_NumClasses del espacio en m_MislabelDistribution no se usa (tantos doubles como instancias)
  //       pensar en alguna alternativa: OC, ECC & M2
  //       Implement a method for faster resize of m_TrainData (abstract in Booster).
  //       Implement Sourcable interface.
  //       boolToInt, inSet, calculateMislabelDistribution -> private if not CSAdaBoostOC...

  /** Filter for "coloring" (partition of class set into two sets of classes). */
  protected AbstractNominalToOCFilter m_Coloring = new NominalToRandomPermutationOfEvenSplitOCFilter();

  /** Weight distribution indicating wich (incorrect) classes are more or less difficult
   *  to identify for base classifier.
   *  {m_NumInstances x m_NumClasses} --> [0,1] ; SUM == 1
   */
  protected double[][] m_MislabelDistribution;

  /** Original training instance belongs to class...
   *  {m_NumInstances}-->[0,m_NumClasses)
   */
  protected int[] m_OriginalDataClasses;

  /** We store the base classifiers and their weights dynamically. I choose this approach
   *  instead of using a faster fixed length array because I want to be able of control
   *  interactively the number of performed iterations (next method...).
   */
  private WeightedClassifierVector m_Classifiers;

  /** Number of retries in the task of find a good coloring (which maximizes U). */
  protected int m_Max_U_CalculatingIterations = 1;

  /** The number of classes. */
  protected int m_NumClasses;

  /** The theoretical training error upper bound. */
  protected double m_TrainingErrorUpperBound;

  /**
   *  A variable where we store the weights distribution sum before normalizing it; needed
   * to compute the traininh error upper bound of Asymmetric AdaBoostECC, but we put it here
   * because it hasn't any performance penalization.
   */
  protected double m_LastDistributionWeightSumBeforeNormalize = 0;

  /** Calculate or not the training error upper bound. */
  protected boolean m_CalculateTrainingErrorUpperBound = false;

  /**
   * Boosting method.
   * Reset the model and initialize data.
   *
   * @param data the training data to be used for generating the
   * boosted classifier.
   * @exception java.lang.Exception if the classifier could not be built successfully
   */
  public void buildClassifier(Instances data) throws Exception{

    //Check glogal settings.
    if (m_Classifier == null)
      throw new Exception("A base classifier has not been specified!");

    if (m_Coloring == null)
      throw new Exception("The \"coloring function\" hasn't been specified.");

    //Check the correct format of training instances.
    if (data.checkForStringAttributes())
      throw new Exception("Can't handle string attributes!");

    if (data.classAttribute().isNumeric())
      throw new Exception("AdaBoostOC can't handle a numeric class!");

    //We copy them thus ensuring AdaBoostOC nor other class mess it up.
    m_TrainData = new Instances(data);
    m_BoosterReady = false;  //No "return" can be done from here.
    m_TrainData.deleteWithMissingClass();

    if (m_TrainData.numInstances() == 0)
      throw new Exception("No train instances without class missing!");

    //Set up the number of classes. Be careful with binary class problems (not use AdaBoostOC).
    if (3 > (m_NumClasses = m_TrainData.numClasses()))
      System.err.println("AdaBoostOC could be not useful with binary class problems.");
    //throw new Exception("AdaBoostOC is not useful with binary class problems.");

    //Set up the number of instances.
    m_NumInstances = m_TrainData.numInstances();

    //Initialize the normalization factor.
    initializeNormFactor();

    //We store which class belongs to each training instance.
    m_OriginalDataClasses = new int[m_NumInstances];
    for (int i = 0; i < m_NumInstances; i++)
      m_OriginalDataClasses[i] = (int) m_TrainData.instance(i).classValue();

    //Set up the number of performed iterations.
    m_NumIterations = 0;

    //Initialize the coloring.
    m_Coloring.setProcessedAttribute(-1); //So it will take the class attribute.
    m_Coloring.setInputFormat(m_TrainData);

    //It's neccesary to set m_TrainData class attribute as a binary one
    //to avoid problems with base classifiers (ej. VotedPerceptron).
    //This could be done by this line of code:
    //m_TrainData=Filter.useFilter(m_TrainData,m_Coloring);
    //We do it in a faster way.
    int classIndex = m_TrainData.classIndex();

    //Create the binary attribute wich will be the neww class
    FastVector my_nominal_values = new FastVector(2);

    my_nominal_values.addElement("0");
    my_nominal_values.addElement("1");

    Attribute newBinaryClass = new Attribute(m_TrainData.attribute(m_TrainData.classIndex()).name(), my_nominal_values);

    //Remove old class attribute.
    m_TrainData.setClassIndex(-1);
    m_TrainData.deleteAttributeAt(classIndex);

    //Insert and set the new class.
    m_TrainData.insertAttributeAt(newBinaryClass, classIndex);
    m_TrainData.setClassIndex(classIndex);

    //Initialize the classifiers and their weights array.
    m_Classifiers = new WeightedClassifierVector();

    //Initialize the "mislabel distribution".
    //TODO: Make it more efficient (two loops)
    m_MislabelDistribution = new double[m_NumInstances][m_NumClasses];
    double incorrectLabelWeight = 1.0 / (m_NumInstances * (m_NumClasses - 1));
    for (int i = 0; i < m_NumInstances; i++)
      for (int j = 0; j < m_NumClasses; j++)
        m_MislabelDistribution[i][j] = (m_OriginalDataClasses[i] != j ? incorrectLabelWeight : 0.0);

    //Initialize the training error upper bound.
    m_TrainingErrorUpperBound = 1;

    if (m_Debug && m_CalculateTrainingErrorUpperBound)
      System.err.println("Calculating the training error upper bound.");

    //Now we can perform new iterations.
    m_BoosterReady = true;

    //Perform first "InitialIterations" iterations.
    nextIterations(m_InitialIterations);
  }

  /**
   * Boosting method. Boosts any classifier that can handle weighted
   * instances.
   *
   * @param numIterations The number of iterations to perform.
   *
   * @exception java.lang.Exception if the classifier could not be built successfully
   */
  protected void buildClassifierWithWeights(int numIterations) throws Exception{

    double epsilon, beta;          //Base classifier's error (pseudoloss) and weight.
    Classifier baseClassifier;
    SerializedObject serializedClassifier = new SerializedObject(m_Classifier);

    if (m_Debug)
      System.err.println("Boosting without resampling.");

    // Do iterations.
    for (numIterations--; numIterations >= 0; numIterations--){

      if (m_Debug)
        System.err.println("Training classifier " + (m_NumIterations + 1));

      //Colororing genetation & U calculation.
      double u = calculateU();

      //Weights redistribution.
      reweight(u);

      //Coloring application.
      relabel();

      //Copy the base classifier.
      baseClassifier = (Classifier) serializedClassifier.getObject();

      // Build the classifier.
      baseClassifier.buildClassifier(m_TrainData);

      //Error (pseudoloss) calculation.
      epsilon = calculatePseudoLoss(baseClassifier);

      //Determine the weight to assign to this model.
      beta = calculateClassifierWeight(epsilon);

      if (m_Debug)
        System.err.println("\terror (pseudoloss) rate = " + epsilon + "  beta = " + beta);

      //Mislabel distribution calculation.
      calculateMislabelDistribution(baseClassifier, beta);

      //Update the training error upper bound.
      if (m_CalculateTrainingErrorUpperBound)
        updateTrainingErrorUpperBound(epsilon, u);

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
   * @exception java.lang.Exception if the classifier could not be built successfully
   */
  protected void buildClassifierUsingResampling(int numIterations) throws Exception{

    double epsilon, beta;          //Base classifier's error (pseudoloss) and weight.
    Random randomInstance = new Random(m_ResampleSeed); //Random number generator for resample.
    Instances sample;              //The resampled training dataset.
    Classifier baseClassifier;
    SerializedObject serializedClassifier = new SerializedObject(m_Classifier);

    if (m_Debug)
      System.err.println("Boosting with resampling.");

    // Do iterations
    for (numIterations--; numIterations >= 0; numIterations--){

      if (m_Debug)
        System.err.println("Training classifier " + (m_NumIterations + 1));

      //Colororing genetation & U calculation.
      double u = calculateU();

      //Weights redistribution.
      reweight(u);

      //Coloring application.
      relabel();

      // Resample.
      sample = m_TrainData.resampleWithWeights(randomInstance);

      //Copy the base classifier.
      baseClassifier = (Classifier) serializedClassifier.getObject();

      // Build the classifier.
      baseClassifier.buildClassifier(sample);

      //Error (pseudoloss) calculation.
      epsilon = calculatePseudoLoss(baseClassifier);

      // Determine the weight to assign to this model
      beta = calculateClassifierWeight(epsilon);

      if (m_Debug)
        System.err.println("\terror (pseudoloss) rate = " + epsilon + "  beta = " + beta);

      //Mislabel distribution calculation.
      if (m_CalculateTrainingErrorUpperBound)
        calculateMislabelDistribution(baseClassifier, beta);

      //Update the training error upper bound.
      if (m_CalculateTrainingErrorUpperBound)
        updateTrainingErrorUpperBound(epsilon, u);

      //"Commit".
      m_Classifiers.add(baseClassifier, beta);
      m_NumIterations++;
    }
  }

  /**
   * Calculate U; this method calls m_Coloring.newPartition to generate a new binary
   * split of the label set until it obtains U>=0.5 or has generated m_Max_U_CalculatingIterations
   * partitions; it´s a little refined, blind way (it hopes for the m_Coloring to have
   * a good partition method) to do this.
   *
   * @return Maximum U obtained in the way described.
   *
   * @throws java.lang.Exception if it has any problems with the "coloring class".
   */
  protected double calculateU() throws Exception{

    int set;  //Set "each training instance belongs to..." in the new partition.
    BitSet bestPartition = null; //Partition which maximizes U.
    double u, bestU = 0;
    int it = 0; //Number of generated partitions.

    do{

      // Lo hacemos 1 vez más de lo necesario en todo el proceso de entrenamiento
      // (ya se invoca en "m_Coloring.setInputFormat").
      m_Coloring.newPartition(m_TrainData, m_NumIterations);

      u = 0;

      //Calculate U.
      for (int i = 0; i < m_NumInstances; i++){
        set = m_Coloring.getCode(m_NumIterations, m_OriginalDataClasses[i]);
        for (int j = 0; j < m_NumClasses; j++)
          u += m_MislabelDistribution[i][j] * boolToInt(!inSet(set, j, m_NumIterations));
      }

      //We expect for a value of U greater or equal to 1/2
      if (u >= 0.5)
        return u;

      //Is this coloring the best so far?.
      if (u > bestU){
        bestPartition = m_Coloring.getPartition(m_NumIterations);
        bestU = u;
      }

      it++;
    }
    while (it < m_Max_U_CalculatingIterations);

    //Let's use the best coloring function.
    if (u < bestU){
      m_Coloring.setPartition(m_NumIterations, bestPartition);
      return bestU;
    }

    return u;
  }

  /**
   *  Relabel the training data according to the coloring function calculated in calculateU.
   *  Equivalent to (but more efficient than) m_TrainData=Filter.useFilter(OriginalData, m_Coloring)
   */
  protected void relabel(){
    for (int i = 0; i < m_NumInstances; i++)
      m_TrainData.instance(i).setClassValue(m_Coloring.getCode(m_NumIterations, m_OriginalDataClasses[i]));
  }

  /**
   * Assign a new weight to every instance in the training dataset according to U and
   * its associated  mislabel distribution.
   *
   * @param u U calculated by Schapire's formulae</a>
   */
  protected void reweight(double u){

    double sumTmp;
    int clasificadaEn;
    m_LastDistributionWeightSumBeforeNormalize = 0;

    if (m_Debug){
      System.err.println("\tU = " + u);
      try{
        System.err.println("\tClasses in subset 1: " + m_Coloring.getPartition(m_NumIterations));
      }
      catch (Exception e){
        System.err.println(e.toString());
      }
    }

    u += NO_DIVISION_BY_ZERO;

    for (int i = 0; i < m_NumInstances; i++){

      //Some weka classifiers fails when passing them instances with a sum of weights of zero.
      //By example, DecisionStump fails when attemps to normalize the distribution of class values.
      sumTmp = 0;

      clasificadaEn = m_Coloring.getCode(m_NumIterations, m_OriginalDataClasses[i]);

      for (int j = 0; j < m_NumClasses; j++)
        sumTmp += m_MislabelDistribution[i][j] *
          boolToInt(!inSet(clasificadaEn, j, m_NumIterations));

      m_TrainData.instance(i).setWeight((sumTmp + NO_DIVISION_BY_ZERO) / u);
      m_LastDistributionWeightSumBeforeNormalize += m_TrainData.instance(i).weight();
    }

    normalizeWeights(m_LastDistributionWeightSumBeforeNormalize);
  }

  /**
   * Calculate base classifier's pseudoloss.
   *
   * Extract from <a href="http://www.research.att.com/%7Eschapire/cgi-bin/uncompress-papers/Schapire97.ps">
   *                           Schapire's paper:</a>
   * <p><i> This loss measure penalizes the weak hypothesis for failing to include
   * the correct label y(i) in the plausible set associated with example x(i)...,and further
   * penalizes each incorrect label l != y(i) which is included in the plausible set.</i></p>
   *
   * This is here applicated to the class partition previously generated, so that the plausible
   * set is the set of classes which belongs to the same partition (0 or 1) in which the base
   * classifier predicts each instance is in.
   *
   * @param baseClassifier From which calculate the error.
   *
   * @return The base classifier error related to the training data.
   *
   * @throws java.lang.Exception If base classifier can´t classify a training instance.
   */
  private double calculatePseudoLoss(Classifier baseClassifier) throws Exception{

    double sumTmp = 0;
    int clasificadaEn;

    for (int i = 0; i < m_NumInstances; i++){
      clasificadaEn = (int) baseClassifier.classifyInstance(m_TrainData.instance(i));
      for (int j = 0; j < m_NumClasses; j++)
        sumTmp += m_MislabelDistribution[i][j]
          * (boolToInt(!inSet(clasificadaEn, m_OriginalDataClasses[i], m_NumIterations))
          + boolToInt(inSet(clasificadaEn, j, m_NumIterations)));
    }
    return sumTmp / 2; // >>1
  }

  /**
   * Calculate the base classifier weight related to its error. If the error is greater than
   * 0.5, the classifier produces a negative vote for the class in which it clssifies an
   * instance, which is nearly the same effect of taking the inverse of such classifier
   * (remember, it's a binary classification problem).
   *
   * @param epsilon Classifier's  error.
   *
   * @return Classifier´s weight.
   */
  private double calculateClassifierWeight(double epsilon){
    return Math.log((1 - epsilon + NO_DIVISION_BY_ZERO) / (epsilon + NO_DIVISION_BY_ZERO)) / 2;
  }

  /**
   * Update the "mislabel distribution, with respect to the last base classifier generated
   * and its weight.
   *
   * @param baseClassifier The classifier from we obtain the fresh new information.
   * @param beta The weight of the currente classifier.
   *
   * @throws java.lang.Exception If it fails.
   */
  protected void calculateMislabelDistribution(Classifier baseClassifier, double beta) throws Exception{

    double sumTmp = 0; // Faster than use Utils.normalize
    int clasificadaEn;

    for (int i = 0; i < m_NumInstances; i++){
      clasificadaEn = (int) baseClassifier.classifyInstance(m_TrainData.instance(i));
      for (int j = 0; j < m_NumClasses; j++)
        sumTmp +=
          (m_MislabelDistribution[i][j] = m_MislabelDistribution[i][j] *
          Math.exp(beta * (boolToInt(!inSet(clasificadaEn, m_OriginalDataClasses[i], m_NumIterations))
                           + boolToInt(inSet(clasificadaEn, j, m_NumIterations)))));
    }
    //Normalize.
    for (int i = 0; i < m_NumInstances; i++)
      for (int j = 0; j < m_NumClasses; j++)
        m_MislabelDistribution[i][j] = m_MislabelDistribution[i][j] / sumTmp;
  }

  /**
   * Simple function which transforms a boolean into an int (1 for true and 0 for false);
   * Its main purpouse is legibility.
   *
   * @param b The boolean
   *
   * @return 1 for true, 0 for false.
   */
  protected int boolToInt(boolean b){
    return b ? 1 : 0;
  }

  /**
   * Is the class "clase" in the set "set" in the partition "iteration"?.
   *
   * There is no error (parameter) control.
   *
   * @param set The subset of the partition (either 1 or 0).
   * @param clase The class.
   * @param iteration The partition number.
   *
   * @return true if class belongs to the set, otherwise false
   */
  protected boolean inSet(int set, int clase, int iteration){
    return m_Coloring.getCode(iteration, clase) == set;
  }

  /**
   * Update the training error upper limit; the training error of the final combined hypothesis is theoretically
   * guaranteed to be under this value.
   *
   * @param pseudoLoss The pseudoloss of the last base classifier trained.
   * @param u U associated to the last coloring computed & base classifier trained.
   */
  private void updateTrainingErrorUpperBound(double pseudoLoss, double u){

    //Base classifier's error can be expressed simply in terms of its pseudoloss measure.
    double gamma = (0.5 - pseudoLoss) / u;

    double gammaPorU = gamma * u;
    m_TrainingErrorUpperBound *= Math.sqrt(1 - 4 * gammaPorU * gammaPorU);

    if (m_Debug)
      System.err.println("\tTraining error upper bound = " + m_TrainingErrorUpperBound);
  }

  /**
   * Get the training error upper bound.
   *
   * @return The training error upper bound or -1 if no iterations have been done.
   */
  public double getErrorUpperBound(){
    if (m_NumIterations > 0){
      if (m_CalculateTrainingErrorUpperBound)
        return (m_NumClasses - 1) * m_TrainingErrorUpperBound;
      return 1;
    }
    return -1;
  }

  /** @return The error upper bound string (by example, "Training error upper bound.") */
  public String getErrorUpperBoundName(){
    return "Training error upper bound";
  }

  /**
   * Calculates the class membership probabilities for the given test instance.
   *
   * @param instance the instance to be classified
   *
   * @return predicted class probability distribution
   *
   * @exception java.lang.Exception if instance could not be classified successfully
   */
  public double[] distributionForInstance(Instance instance) throws Exception{

    if (m_NumIterations == 0)
      throw new Exception("No model built");

    double[] sums = new double[m_NumClasses];
    int clasificadaEn;

    //Necessary to avoid problems (by example, J48.classifyInstance uses numClasses() instance
    //method, which must return 2 and not the number of data classes in the original dataset).
    instance = new Instance(instance);
    instance.setDataset(m_TrainData);

    for (int i = 0; i < m_NumIterations; i++){
      clasificadaEn = (int) m_Classifiers.get(i).classifyInstance(instance);
      for (int j = 0; j < m_NumClasses; j++)
        sums[j] += m_Classifiers.getWeight(i) *
          boolToInt(inSet(clasificadaEn, j, i));
    }

    //Do it directly, without using Utils.normalize().
    try{
      Utils.normalize(sums);
    }
    catch (IllegalArgumentException e){
    } // sums[i]==0 para todo i

    return sums;
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
    if (classifierIndex > m_NumIterations || classifierIndex < 0)
      throw new Exception("Classifier index is invalid");

    double[] sums = new double[m_NumClasses];
    int clasificadaEn;

    //Necessary to avoid problems (by example, J48.classifyInstance uses numClasses() instance
    //method, which must return 2 and not the number of data classes in the original dataset).
    instance = new Instance(instance);
    instance.setDataset(m_TrainData);

    clasificadaEn = (int) m_Classifiers.get(classifierIndex).classifyInstance(instance);
    for (int j = 0; j < m_NumClasses; j++)
      sums[j] = m_Classifiers.getWeight(classifierIndex) * boolToInt(inSet(clasificadaEn, j, classifierIndex));

    return sums;
  }

  /**
   * Safely (and thus slower) inputs a new training instance.
   * Not implemented yet.
   *
   * @param instance The instance to addMemberName to the training set.
   *
   * @throws java.lang.Exception if instance format isn't compatible with the dataset.
   */
  public void addTrainingInstance(Instance instance) throws Exception{
    throw new Exception("Not implemented yet.");
    //super.addTrainingInstance(instance);
  }

  /**
   * Safely (and thus slower) removes a training instance.
   * Not implemented yet.
   *
   * @param numInstance The index of instance to delete from the training set.
   *
   * @throws java.lang.Exception if instance number is incorrect or if there are no more training instances.
   */
  public void deleteTrainingInstance(int numInstance) throws Exception{
    throw new Exception("Not implemented yet.");
    //super.deleteTrainingInstance(numInstance);
  }

  /**
   * Parses a given list of options. Valid options are:<PRE>
   *
   * -F classname
   * Specify a class (must be an AbstractNominalToOCFilter subclass) for
   * compute the coloring with.
   *
   *
   * -U numIterations
   * Set the number of retries searching for a coloring function producing U>=1/2.
   * (default 1)
   *
   *
   * -B
   * Calculate the training error upper bound (default false).
   *
   * Plus the rest of the superclass options.
   * </PRE>
   *
   *
   * @param options the list of options as an array of strings
   *
   * @exception java.lang.Exception if an option is not supported
   */
  public void setOptions(String[] options) throws Exception{

    super.setOptions(options);

    setCalculateErrorUpperBound(Utils.getFlag('B', options));

    String U_Iterations = Utils.getOption('U', options);
    if (U_Iterations.length() != 0)
      setMax_U_CalculatingIterations(Integer.parseInt(U_Iterations));
    if (m_Max_U_CalculatingIterations < 1)
      setMax_U_CalculatingIterations(1);

    String filterName = Utils.getOption('F', options);
    if (filterName.length() == 0)
      setColoring((AbstractNominalToOCFilter) Utils.forName(AbstractNominalToOCFilter.class,
                                                            "oaidtb.filters.NominalToRandomPermutationOfEvenSplitOCFilter", options));
    else
      setColoring((AbstractNominalToOCFilter) Utils.forName(AbstractNominalToOCFilter.class, filterName, options));
  }

  /**
   * Gets the current settings of the Classifier.
   *
   * @return an array of strings suitable for passing to setOptions
   */
  public String[] getOptions(){

    String[] otherOptions = super.getOptions();

    String[] options = new String[otherOptions.length + 5];

    int current = 0;

    if (getCalculateErrorUpperBound())
      options[current++] = "-B";
    if(null!=m_Coloring){
      options[current++] = "-F";
      options[current++] = "" + getColoring().getClass().getName();
    }
    options[current++] = "-U";
    options[current++] = "" + getMax_U_CalculatingIterations();

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

    Vector newVector = new Vector(10);

    newVector.addElement(new Option(
      "\tSpecify a class (must be an AbstractNominalToOCFilter subclass) for compute the coloring with."
      + "\n\t(default NominalToRandomPermutationOfEvenSplitOCFilter).\n.",
      "F", 1, "-F <filter name>"));

    newVector.addElement(new Option(
      "\tSet the number of retries searching for a coloring function producing U>=1/2.\n"
      + "\t(default 1)",
      "U", 1, "-U <num>"));

    newVector.addElement(new Option(
      "\tCalculate the training error upper bound.\n",
      "B", 0, "-B"));
    newVector.addElement(new Option("", "", 0, "\nCommon boosters options."));

    Enumeration enum = super.listOptions();
    while (enum.hasMoreElements()){
      newVector.addElement(enum.nextElement());
    }

    return newVector.elements();
  }

  /**
   * Set the number of iterations searching for a coloring function producing U>=1/2
   *
   * @param numIterations The number of iterations.
   */
  public void setMax_U_CalculatingIterations(int numIterations){
    m_Max_U_CalculatingIterations = numIterations;
  }

  /**
   *  Gets the number of retries searching for a coloring function producing U>=1/2
   *
   * @return The number of iterations.
   */
  public int getMax_U_CalculatingIterations(){
    return m_Max_U_CalculatingIterations;
  }

  /**
   * Set the filter used to compute the coloring.
   *
   * @param filter The filter.
   */
  public void setColoring(AbstractNominalToOCFilter filter){
    m_Coloring = filter;
  }

  /**
   * Get the filter used to compute the coloring.
   *
   * @return filter The filter.
   */
  public AbstractNominalToOCFilter getColoring(){
    return m_Coloring;
  }

  /**
   * Get the base classifier built on specified iteration. Be careful of not modifying it.
   *
   * @param numClassifier The number of the base classifier.
   *
   * @return The base classifier.
   *
   * @throws java.lang.Exception If numClassifier is incorrect (\<0 or >m_NumIterations).
   */
  public Classifier getClassifier(int numClassifier) throws Exception{

    if (numClassifier < 0 || numClassifier >= m_NumIterations)
      throw new Exception("Classifier's number incorrect.");
    //It would be better to return a copy; it's slower too.
    return m_Classifiers.get(numClassifier);
  }

  /**
   * Get the weight of the base classifier built on specified iteration.
   *
   * @param numClassifier The number of the base classifier.
   *
   * @return Classifier's weight.
   *
   * @throws java.lang.Exception If numClassifier is incorrect.
   */
  public double getClassifierWeight(int numClassifier) throws Exception{

    if (numClassifier < 0 || numClassifier >= m_NumIterations)
      throw new Exception("Classifier's number incorrect.");
    return m_Classifiers.getWeight(numClassifier);
  }

  /**
   * Get if the training error upper bound  is being calculated.
   *
   * @return true if it's being computed.
   */
  public boolean getCalculateErrorUpperBound(){
    return m_CalculateTrainingErrorUpperBound;
  }

  /**
   * Set (only if no iterations have be done) if the training error upper bound must be
   * wether or not calculated.
   *
   * @param b If the training error upper bound must or not be calculated.
   */
  public void setCalculateErrorUpperBound(boolean b){
    if (m_NumIterations == 0)
      m_CalculateTrainingErrorUpperBound = b;
  }

  /**
   * Returns description of the boosted classifier.
   *
   * @return description of the boosted classifier as a string
   */
  public String toString(){

    StringBuffer text = new StringBuffer();

    if (m_NumIterations == 0){
      text.append("AdaBoostOC: No model built yet.\n");
    }
    else if (m_NumIterations == 1){
      text.append("AdaBoostOC: No boosting possible, one classifier used!\n");
      text.append(m_Classifiers.get(0).toString() + "\n");
    }
    else{
      text.append("AdaBoostOC: Base classifiers and their weights: \n\n");

      for (int i = 0; i < m_NumIterations; i++){
        text.append("Classifier: " + i + ".\n");
        try{
          text.append("Classes in the subset \"1\": " + m_Coloring.getPartition(i).toString() + "\n");
        }
        catch (Exception e){
          text.append("Exception: " + e.toString() + "\n");
        }
        text.append("Weight: " + Utils.roundDouble(m_Classifiers.getWeight(i), 2) + "\n");
        text.append(m_Classifiers.get(i).toString() + "\n\n");
      }
      text.append("Number of performed Iterations: "
                  + m_NumIterations + "\n");
      if (m_CalculateTrainingErrorUpperBound)
        text.append("Training error must be less than "
                    + getErrorUpperBound() + "\n");
    }
    return text.toString();
  }

  //--------------------------------------*************************************
  //--------------------- Configure the "GUI side" methods ********************
  //--------------------------------------*************************************

  //We need to inform about how to manage AbstractNominalToOCFilter classes.
  static{
    if(null == java.beans.PropertyEditorManager.findEditor(oaidtb.filters.AbstractNominalToOCFilter.class))
    java.beans.PropertyEditorManager
      .registerEditor(oaidtb.filters.AbstractNominalToOCFilter.class,
                      weka.gui.GenericObjectEditor.class);
  }

  /**
   * Returns a string describing this classifier.
   *
   * @return a description of the filter suitable for
   * displaying in the explorer/experimenter gui
   */
  public String globalInfo(){

    return "An implementation of Schapire's AdaBoostOC.";
  }

  //--------------------- ToolTips text ********************

  public String calculateErrorUpperBoundTipText(){

    return "Calculate or not the training error upper bound (only if no iteration have been done yet).\n" +
      "See Schapire's paper.";
  }

  public static String coloringTipText(){

    return "Set the NominalToOC filter used to partition the class space into two sets.";
  }

  public static String max_U_CalculatingIterationsTipText(){

    return "Set the number of retries in the searching of a class partition which produces U>1/2.\n" +
      "See Schapire's paper.";
  }

  /**
   * Defines a "visual" order to the bean's properties of this booster.
   *
   * @return The properties order
   */
  protected CustomOrderDefiner getPropertiesOrder(){

    CustomOrderDefiner cod = new CustomOrderDefiner();
    cod.add("coloring");
    cod.add("max_U_CalculatingIterations");
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
      System.out.println(Evaluation.evaluateModel(new AdaBoostOC(), argv));
    }
    catch (Exception e){
      System.err.println(e.getMessage());
    }
  }
}