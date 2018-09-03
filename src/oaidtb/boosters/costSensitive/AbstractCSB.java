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
 *    AbstractCSB.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.boosters.costSensitive;

import oaidtb.boosters.Booster;
import oaidtb.boosters.MulticlassExtensibleBooster;
import oaidtb.boosters.WeightedClassifierVector;
import oaidtb.misc.CustomOrderDefiner;
import weka.classifiers.Classifier;
import weka.classifiers.CostMatrix;
import weka.classifiers.DistributionClassifier;
import weka.core.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.Random;
import java.util.Vector;

/**
 * Abstract class for the cost sensitive boosters. For more information see: <p>
 *
 * <a href="http://www.gscit.monash.edu.au/~kmting/Papers/ch-cs.pdf">
 * Ting, K.M., <i>Cost Sensitive Classification Using Decision Trees, Boosting and MetaCost</i>.
 * Book chapter in Heuristic and Optimization for Knowledge Discovery.
 * Edited by Sarker, R., Abbass, H. & Newton, C. Idea Group Publishing. 2002.
 *  </a><p>
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public abstract class AbstractCSB extends Booster implements MulticlassExtensibleBooster{

  /** Must this booster use its own train data copy?. */
  private boolean m_UseOwnTrainData = true;

  /** Base classifiers used for boosting. */
  private WeightedClassifierVector m_Classifiers;

  /** The number of classes. */
  protected int m_NumClasses;

  /** The cost matrix. */
  protected CostMatrix m_CostMatrix = new CostMatrix(1);

  /*-- Specify possible sources of the cost matrix --*/
  public static int MATRIX_ON_DEMAND = 1, MATRIX_SUPPLIED = 2, DEFAULT_MATRIX = 3;
  public static final Tag[] TAGS_MATRIX_SOURCE = {
    new Tag(MATRIX_ON_DEMAND, "Load cost matrix on demand"),
    new Tag(MATRIX_SUPPLIED, "Use explicit cost matrix"),
    new Tag(DEFAULT_MATRIX, "Use default cost matrix")
  };

  /** The cost of misclassify a minority class when using the default cost matrix */
  protected double m_DefaultMatrixCostFactor = 2.0;

  /** Indicates the current cost matrix source */
  protected int m_MatrixSource = MATRIX_ON_DEMAND;

  /** The directory used when loading cost files on demand, null indicates current directory. */
  protected File m_OnDemandDirectory = new File(System.getProperty("user.dir"));

  /** The name of the cost file, for command line options */
  protected String m_CostFile;
  /*-- Specify possible sources of the cost matrix --*/

  /** Set wether or not the cost matrix will be used to initialize the instances weights. */
  private boolean m_InitializeWeightsUsingCosts = false;

  /** How to combine the base classifier to build the final hypothesis. */
  private CombinedPredictionModel m_HowClassify = new MinimumExpectedCostCriterionUsingConfidenceLevels();

  /** Constants representing the possible selections for the combined prediction model. */
  public final static int MVC = 0, MVC_UCL = 1, MECC = 2, MECC_UCL = 3;

  /** Tags representing the possible selections for the combined prediction model. */
  private static Tag[] CPM_TAGS = new Tag[]{
    new Tag(MVC, "Maximum Vote Criterion"),
    new Tag(MVC_UCL, "Maximum Vote Criterion Using Confidence Levels"),
    new Tag(MECC, "Minimum Expected Cost Criterion"),
    new Tag(MECC_UCL, "Minimum Expected Cost Criterion Using Confidence Levels")
  };

  /** Default constructor to set NaiveBayes as the default base classifier. */
  public AbstractCSB(){
    super();
    m_Classifier = new weka.classifiers.bayes.NaiveBayes();
  }

  /**
   * Set the combined prediction model to use.
   *
   * <br> Note that this can be changed even after the classifier is built since it only affects
   * the classification process.
   *
   * @param tag SelectedTag representing the combined selection model to be used.
   */
  public void setCombinedPredictionModel(SelectedTag tag){
    if (tag != null)
      switch (tag.getSelectedTag().getID()){
        case MVC:
          m_HowClassify = new MaximumVoteCriterion();
          break;
        case MVC_UCL:
          m_HowClassify = new MaximumVoteCriterionUsingConfidenceLevels();
          break;
        case MECC:
          m_HowClassify = new MinimumExpectedCostCriterion();
          break;
        case MECC_UCL:
          m_HowClassify = new MinimumExpectedCostCriterionUsingConfidenceLevels();
          break;
        default:
          m_HowClassify = new MinimumExpectedCostCriterionUsingConfidenceLevels();
      }
    else
      m_HowClassify = new MinimumExpectedCostCriterionUsingConfidenceLevels();
  }

  /** @return A SelectedTag representing the currently used combined selection model.*/
  public SelectedTag getCombinedPredictionModel(){
    return new SelectedTag(combinedPredictionModelSelected(), CPM_TAGS);
  }

  /** @return The integer that represents the currently used selected prediction model. */
  private int combinedPredictionModelSelected(){
    if (m_HowClassify instanceof MaximumVoteCriterion)
      return MVC;
    if (m_HowClassify instanceof MaximumVoteCriterionUsingConfidenceLevels)
      return MVC_UCL;
    if (m_HowClassify instanceof MinimumExpectedCostCriterion)
      return MECC;
    if (m_HowClassify instanceof MinimumExpectedCostCriterionUsingConfidenceLevels)
      return MECC_UCL;
    return -1;
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
  public Classifier getClassifier(int iterationIndex) throws Exception{
    if (iterationIndex < 0 || iterationIndex >= m_NumIterations)
      throw new Exception("Classifier's number incorrect.");
    //It would be better to return a copy; it's slower too.
    return m_Classifiers.get(iterationIndex);
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

  /** @return The cost matrix */
  public CostMatrix getCostMatrix(){
    return m_CostMatrix;
  }

  /** @param costMatrix The cost matrix to be used  */
  public void setCostMatrix(CostMatrix costMatrix){
    m_CostMatrix = costMatrix;
    m_MatrixSource = MATRIX_SUPPLIED;
  }

  /** @return If the weights will or not be initialized using the costs */
  public boolean isInitializeWeightsUsingCosts(){
    return m_InitializeWeightsUsingCosts;
  }

  /** @param initializeWeightsUsingCosts Will the weights be initialized using the costs?  */
  public void setInitializeWeightsUsingCosts(boolean initializeWeightsUsingCosts){
    m_InitializeWeightsUsingCosts = initializeWeightsUsingCosts;
  }

  /**
   * Gets the source location method of the cost matrix. Will be one of
   * MATRIX_ON_DEMAND, MATRIX_SUPPLIED or DEFAULT_MATRIX.
   *
   * @return the cost matrix source.
   */
  public SelectedTag getCostMatrixSource(){

    return new SelectedTag(m_MatrixSource, TAGS_MATRIX_SOURCE);
  }

  /**
   * Sets the source location of the cost matrix. Values other than
   * MATRIX_ON_DEMAND, MATRIX_SUPPLIED or DEFAULT_MATRIX will be ignored.
   *
   * @param newMethod the cost matrix location method.
   */
  public void setCostMatrixSource(SelectedTag newMethod){

    if (newMethod.getTags() == TAGS_MATRIX_SOURCE){
      m_MatrixSource = newMethod.getSelectedTag().getID();
    }
  }

  /**
   * Returns the directory that will be searched for cost files when
   * loading on demand.
   *
   * @return The cost file search directory.
   */
  public File getOnDemandDirectory(){

    return m_OnDemandDirectory;
  }

  /**
   * Sets the directory that will be searched for cost files when
   * loading on demand.
   *
   * @param newDir The cost file search directory.
   */
  public void setOnDemandDirectory(File newDir){

    if (newDir.isDirectory()){
      m_OnDemandDirectory = newDir;
    }
    else{
      m_OnDemandDirectory = new File(newDir.getParent());
    }
    m_MatrixSource = MATRIX_ON_DEMAND;
  }

  /** @return The cost of misclassifying the minority class when using the default cost matrix. */
  public double getDefaultMatrixCostFactor(){
    return m_DefaultMatrixCostFactor;
  }

  /** @param defaultMatrixCostFactor The cost of misclassifying the minority class when using the default cost matrix.*/
  public void setDefaultMatrixCostFactor(double defaultMatrixCostFactor){
    m_DefaultMatrixCostFactor = defaultMatrixCostFactor;
  }

  /** Reweight the train data according with the cost matrix. */
  private void initializeWeightsBasedOnCosts(){

    double[] costs = new double[m_NumClasses];

    //Calculate the cost of misclassifying a class i.
    for (int i = 0; i < m_NumClasses; i++)
      costs[i] = weka.core.Utils.sum(m_CostMatrix.getRow(i)) - m_CostMatrix.getElement(i, i);

    double newWeightsSum = 0;
    for (int i = 0; i < m_TrainData.numInstances(); i++){
      Instance instance = m_TrainData.instance(i);
      instance.setWeight(instance.weight() * costs[(int) instance.classValue()]);
      newWeightsSum += instance.weight();
    }

    normalizeWeights(newWeightsSum);
  }

  /**
   * Calculate the classifier's vote weight according to its training error.
   *
   * @param classifier The base classifier
   * @return The classifier's vote weight
   * @throws Exception If an error occurs
   */
  protected double calculateAlfa(DistributionClassifier classifier) throws Exception{

    double tmp = 0;
    double sumTmp = 0;

    for (int i = 0; i < m_NumInstances; i++){

      Instance instance = m_TrainData.instance(i);
      double[] distributionForInstance = classifier.distributionForInstance(instance);
      int classifiedAs = oaidtb.misc.Utils.maxIndex(distributionForInstance);

      if (classifiedAs == instance.classValue())
        tmp += instance.weight() * distributionForInstance[classifiedAs];
      else
        tmp -= instance.weight() * distributionForInstance[classifiedAs];

      sumTmp += instance.weight();
    }

    tmp = (tmp + NO_DIVISION_BY_ZERO) / (sumTmp + NO_DIVISION_BY_ZERO);

    return Math.log((1 + tmp) / (1 - tmp)) / 2;
  }

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
    if (m_Classifier == null || !(m_Classifier instanceof DistributionClassifier))
      throw new Exception("A base classifier has not been specified or it isn't a DistributionClassifier.");

    if (m_UseOwnTrainData){
      //Check the correct format of training instances.
      if (data.checkForStringAttributes())
        throw new Exception("Can't handle string attributes!");

      if (data.classAttribute().isNumeric())
        throw new Exception("CSB can't handle a numeric class!");

      //We copy them thus ensuring CSB nor other class mess it up.
      m_TrainData = new Instances(data);
      m_BoosterReady = false;  //No "return" can be done from here.
      m_TrainData.deleteWithMissingClass();
      if (m_TrainData.numInstances() == 0)
        throw new Exception("No train instances without class missing!");
    }
    else
    //We entrust to the "caller class" the correctness of the train data instances.
      m_TrainData = data;

    //Set up the number of instances.
    m_NumInstances = m_TrainData.numInstances();

    //Set up the number of classes.
    m_NumClasses = m_TrainData.numClasses();

    //Set up the number of performed iterations.
    m_NumIterations = 0;

    //Initialize the classifiers array.
    m_Classifiers = new WeightedClassifierVector();

    //Set up the cost matrix
    if (m_MatrixSource == MATRIX_ON_DEMAND){
      String costName = data.relationName() + CostMatrix.FILE_EXTENSION;
      File costFile = new File(getOnDemandDirectory(), costName);
      if (!costFile.exists()){
        throw new Exception("On-demand cost file doesn't exist: " + costFile);
      }
      setCostMatrix(new CostMatrix(new BufferedReader(
        new FileReader(costFile))));
    }
    else if (m_MatrixSource == MATRIX_SUPPLIED){
      if (m_CostMatrix == null){
        // try loading an old format cost file
        m_CostMatrix = new CostMatrix(data.numClasses());
        m_CostMatrix.readOldFormat(new BufferedReader(
          new FileReader(m_CostFile)));
      }
    }
    else{
      m_CostMatrix = generateMinorityClassSensitiveCostMatrix(m_TrainData, m_DefaultMatrixCostFactor);
      m_MatrixSource = DEFAULT_MATRIX;
    }

//    m_CostMatrix = normalizeCostMatrix(m_CostMatrix);

    if (m_Debug)
      System.err.println("Cost matrix: \n" + m_CostMatrix.toString());

    //Initialize instance's weight & m_NormFactor.
    initializeNormFactor();

    if (m_InitializeWeightsUsingCosts)
      initializeWeightsBasedOnCosts();
    else
      initializeWeights();

    //We can now perform new iterations.
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

      // Calculate the classifier's vote weight
      double alfa = calculateAlfa((DistributionClassifier) baseClassifier);

      //Weights redistribution.
      reweight((DistributionClassifier) baseClassifier, alfa);

      //"Commit".
      m_Classifiers.add(baseClassifier, alfa);
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

    Classifier baseClassifier;
    SerializedObject serializedClassifier = new SerializedObject(m_Classifier);
    Random randomInstance = new Random(m_ResampleSeed); //Random number generator for resample.
    Instances sample;                                   //The resampled training dataset.

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

      // Calculate the classifier's vote weight
      double alfa = calculateAlfa((DistributionClassifier) baseClassifier);

      //Weights redistribution.
      reweight((DistributionClassifier) baseClassifier, alfa);

      //"Commit"
      m_Classifiers.add(baseClassifier, alfa);
      m_NumIterations++;
    }
  }

  /**
   * Return the class predicted (<0 == class 0 and >0 == class 1) and the
   * confidence of this prediction (its absolute value).
   *
   * @param instance The instance to be classified
   *
   * @return The class predicted and its confidence.
   *
   * @throws java.lang.Exception if the instance can´t be classified succesfully.
   */
  public double confidenceAndSign(Instance instance) throws Exception{
    double[] distributionForInstance = distributionForInstance(instance);
//    return distributionForInstance[1] - distributionForInstance[0];
    return distributionForInstance[0] > distributionForInstance[1] ? -distributionForInstance[0] :
      distributionForInstance[1];
  }

  /**
   * Return the class predicted (<0 == class 0 and >0 == class 1) and the
   * confidence of this prediction (its absolute value) of the classifier indicated.
   * Similar to Booster.distributionForInstance(instance,classifierIndex).
   *
   * @param instance The instance to be classified
   * @param classifierIndex The classifier's index
   *
   * @return The class predicted and its confidence.
   *
   * @throws Exception if an error happens.
   */
  public double confidenceAndSign(Instance instance, int classifierIndex) throws Exception{
    double[] distributionForInstance = getClassifierVote(instance, classifierIndex);
    return distributionForInstance[1] - distributionForInstance[0];
  }


  /**
   * Assign new weights for the train instances.
   *
   * @param classifier The classifier to be used to calculate new instances weights.
   * @param alfa The classifier's vote weight
   */
  protected abstract void reweight(DistributionClassifier classifier, double alfa) throws Exception;

  /**
   * Constructs a CostMatrix for the data assigning 0 cost to all correct classifications,
   * 1 to misclassifying a not minority class and the supplied costFactor to misclassifying
   * the minority class.
   *
   * @param data The instances
   * @param costFactor The cost of misclassifying a minority class
   * @return The cost matrix constructed
   */
  public final static CostMatrix generateMinorityClassSensitiveCostMatrix(Instances data, double costFactor){

    int numClasses = data.numClasses();
    CostMatrix tmp = new CostMatrix(numClasses);
    double classCount[] = new double[numClasses];

    for (int i = 0; i < data.numInstances(); i++)
      classCount[(int) data.instance(i).classValue()]++;

    int minorityClass = oaidtb.misc.Utils.minIndex(classCount);

    //Optimize these loops wouldn't be legible
    for (int i = 0; i < numClasses; i++)
      for (int j = 0; j < numClasses; j++)
        if (i != j)
          tmp.setElement(j, i, 1);

    for (int i = 0; i < numClasses; i++)
      if (i != minorityClass)
        tmp.setElement(minorityClass, i, costFactor);

    return tmp;
  }

  /**
   * Normalize a cost matrix so that the sum of all its elements will be 1.
   *
   * @param costMatrix The cost matriz
   * @return The cost matrix "normalized"
   */
  public final static CostMatrix normalizeCostMatrix(CostMatrix costMatrix){

    double sumTmp = 0;

    //A cost matrix is always squared.
    for (int i = 0; i < costMatrix.numRows(); i++)
      for (int j = 0; j < costMatrix.numColumns(); j++)
        sumTmp += costMatrix.getElement(i, j);

    if (sumTmp == 0)
      return costMatrix;

    CostMatrix normalizedCostMatrix = new CostMatrix(costMatrix.numRows());

    for (int i = 0; i < costMatrix.numRows(); i++)
      for (int j = 0; j < costMatrix.numColumns(); j++)
        normalizedCostMatrix.setElement(i, j, costMatrix.getElement(i, j) / sumTmp);

    return normalizedCostMatrix;
  }

  /**
   * @param costMatrix A cost matrix
   * @return A representation of the cost matrix as an array of doubles
   */
  public final static double[][] costMatrixToDoubleArray(CostMatrix costMatrix){
    double[][] doubles = new double[costMatrix.numRows()][];
    for (int i = 0; i < costMatrix.numRows(); i++)
      doubles[i] = costMatrix.getRow(i);
    return doubles;
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
    return m_HowClassify.distributionForInstance(instance);
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
    return m_HowClassify.getClassifierVote(instance, classifierIndex);
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
      text.append("Cost Matrix: " + m_CostMatrix);
      text.append(m_Classifiers.get(0).toString() + "\n");
    }
    else{
      text.append("Cost Matrix: " + m_CostMatrix);
      text.append("CSB: Base classifiers: \n\n");
      for (int i = 0; i < m_NumIterations; i++){
        text.append(m_Classifiers.get(i) + "\n Vote: " + m_Classifiers.getWeight(i) + "\n\n");
      }
      text.append("Number of performed Iterations: " + m_NumIterations + "\n");
    }

    return text.toString();
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
   * Interface for the internal classes that will serve to configure how the
   * base classifiers will be combined to obtain the final hypothesis
   * for a given instance.
   */
  private interface CombinedPredictionModel extends Serializable{

    /**
     * Calculates the class membership probabilities for the given test instance.
     *
     * @param instance the instance to be classified
     *
     * @return predicted class probability distribution
     *
     * @exception java.lang.Exception if instance could not be classified successfully
     */
    public double[] distributionForInstance(Instance instance) throws Exception;

    /**
     * Get the classifier's vote for the instance; the (vectorial) sum of all base classifiers vote vectors must be
     * the final combined hypothesis of the booster (obviously, not normalized).
     *
     * @param instance The instance to be classified
     * @param classifierIndex The base classifier index
     * @return The vote of the base classifier to the overall classifier
     * @throws Exception if an error occurs
     */
    public double[] getClassifierVote(Instance instance, int classifierIndex) throws Exception;
  }

  /** Classify using the minimum expected cost criterion. */
  private class MinimumExpectedCostCriterion implements CombinedPredictionModel{

    public double[] distributionForInstance(Instance instance) throws Exception{

      double[] distributionForInstance;
      double[] tmp = new double[m_NumClasses];

      for (int i = 0; i < m_NumIterations; i++){
        double weight = m_Classifiers.getWeight(i);
        distributionForInstance = ((DistributionClassifier) m_Classifiers.get(i)).distributionForInstance(instance);
        int classifiedAs = oaidtb.misc.Utils.maxIndex(distributionForInstance);
        double confidence = distributionForInstance[classifiedAs];
        for (int j = 0; j < classifiedAs; j++)
          tmp[j] += weight * confidence * m_CostMatrix.getElement(classifiedAs, j);
        for (int j = classifiedAs + 1; j < m_NumClasses; j++)
          tmp[j] += weight * confidence * m_CostMatrix.getElement(classifiedAs, j);
      }

      int[] sortedArray = weka.core.Utils.sort(tmp);

      double[] swapTmp = new double[m_NumClasses];
      int maxIndex = m_NumClasses - 1;
      double sumTmp = 0;
      for (int i = 0; i < m_NumClasses; i++){
        sumTmp += swapTmp[sortedArray[maxIndex]] = tmp[sortedArray[i]];
        maxIndex--;
      }

      for (int i = 0; i < m_NumClasses; i++)
        swapTmp[i] /= sumTmp;

      return swapTmp;
    }

    public double[] getClassifierVote(Instance instance, int classifierIndex) throws Exception{

      if (classifierIndex >= m_NumIterations || classifierIndex < 0)
        throw new Exception("Classifier index is invalid");

      double[] distributionForInstance;
      double[] tmp = new double[m_NumClasses];

      double weight = m_Classifiers.getWeight(classifierIndex);
      distributionForInstance = ((DistributionClassifier) m_Classifiers.get(classifierIndex)).distributionForInstance(instance);
      int classifiedAs = oaidtb.misc.Utils.maxIndex(distributionForInstance);
      double confidence = distributionForInstance[classifiedAs];
      for (int j = 0; j < classifiedAs; j++)
        tmp[j] += weight * confidence * m_CostMatrix.getElement(classifiedAs, j);
      for (int j = classifiedAs + 1; j < m_NumClasses; j++)
        tmp[j] += weight * confidence * m_CostMatrix.getElement(classifiedAs, j);

      int[] sortedArray = weka.core.Utils.sort(tmp);

      double[] swapTmp = new double[m_NumClasses];
      int maxIndex = m_NumClasses - 1;
      for (int i = 0; i < m_NumClasses; i++){
        swapTmp[sortedArray[maxIndex]] = tmp[sortedArray[i]];
        maxIndex--;
      }

      return swapTmp;
    }
  }

  /** Classify using the minimum expected cost criterion and confidence levels of the base classifier predictions. */
  private class MinimumExpectedCostCriterionUsingConfidenceLevels implements CombinedPredictionModel{

    public double[] distributionForInstance(Instance instance) throws Exception{

      double[] distributionForInstance;
      double[] tmp = new double[m_NumClasses];

      for (int i = 0; i < m_NumIterations; i++){
        double weight = m_Classifiers.getWeight(i);
        distributionForInstance = ((DistributionClassifier) m_Classifiers.get(i)).distributionForInstance(instance);

        for (int j = 0; j < m_NumClasses; j++){
          for (int k = 0; k < j; k++)
            tmp[j] += weight * distributionForInstance[k] * m_CostMatrix.getElement(k, j);
          for (int k = j + 1; k < m_NumClasses; k++)
            tmp[j] += weight * distributionForInstance[k] * m_CostMatrix.getElement(k, j);
        }
      }

      int[] sortedArray = weka.core.Utils.sort(tmp);

      double[] swapTmp = new double[m_NumClasses];
      int maxIndex = m_NumClasses - 1;
      double sumTmp = 0;
      for (int i = 0; i < m_NumClasses; i++){
        sumTmp += swapTmp[sortedArray[maxIndex]] = tmp[sortedArray[i]];
        maxIndex--;
      }

      for (int i = 0; i < m_NumClasses; i++)
        swapTmp[i] /= sumTmp;

      return swapTmp;
    }

    public double[] getClassifierVote(Instance instance, int classifierIndex) throws Exception{

      if (classifierIndex >= m_NumIterations || classifierIndex < 0)
        throw new Exception("Classifier index is invalid");

      double[] distributionForInstance;
      double[] tmp = new double[m_NumClasses];


      double weight = m_Classifiers.getWeight(classifierIndex);
      distributionForInstance = ((DistributionClassifier) m_Classifiers.get(classifierIndex)).distributionForInstance(instance);

      for (int j = 0; j < m_NumClasses; j++){
        for (int k = 0; k < j; k++)
          tmp[j] += weight * distributionForInstance[k] * m_CostMatrix.getElement(k, j);
        for (int k = j + 1; k < m_NumClasses; k++)
          tmp[j] += weight * distributionForInstance[k] * m_CostMatrix.getElement(k, j);
      }

      int[] sortedArray = weka.core.Utils.sort(tmp);

      double[] swapTmp = new double[m_NumClasses];
      int maxIndex = m_NumClasses - 1;

      for (int i = 0; i < m_NumClasses; i++){
        swapTmp[sortedArray[maxIndex]] = tmp[sortedArray[i]];
        maxIndex--;
      }

      return swapTmp;
    }
  }

  /** Classify using the maximum vote criterion. */
  private class MaximumVoteCriterion implements CombinedPredictionModel{

    public double[] distributionForInstance(Instance instance) throws Exception{
      double[] distributionForInstance;
      double[] tmp = new double[m_NumClasses];

      for (int i = 0; i < m_NumIterations; i++){
        distributionForInstance = ((DistributionClassifier) m_Classifiers.get(i)).distributionForInstance(instance);
        int classifiedAs = oaidtb.misc.Utils.maxIndex(distributionForInstance);
        tmp[classifiedAs] += m_Classifiers.getWeight(i) * distributionForInstance[classifiedAs];
      }

      weka.core.Utils.normalize(tmp);
      return tmp;
    }

    public double[] getClassifierVote(Instance instance, int classifierIndex) throws Exception{

      if (classifierIndex >= m_NumIterations || classifierIndex < 0)
        throw new Exception("Classifier index is invalid");

      double[] distributionForInstance;
      double[] tmp = new double[m_NumClasses];

      distributionForInstance = ((DistributionClassifier) m_Classifiers.get(classifierIndex)).distributionForInstance(instance);
      int classifiedAs = oaidtb.misc.Utils.maxIndex(distributionForInstance);
      tmp[classifiedAs] += m_Classifiers.getWeight(classifierIndex) * distributionForInstance[classifiedAs];

      return tmp;
    }
  }

  /** Classify using the maximum vote criterion and confidence levels of the base classifiers predictions. */
  private class MaximumVoteCriterionUsingConfidenceLevels implements CombinedPredictionModel{

    public double[] distributionForInstance(Instance instance) throws Exception{
      double[] distributionForInstance;
      double[] tmp = new double[m_NumClasses];

      for (int i = 0; i < m_NumIterations; i++){
        distributionForInstance = ((DistributionClassifier) m_Classifiers.get(i)).distributionForInstance(instance);
        double weight = m_Classifiers.getWeight(i);
        for (int j = 0; j < m_NumClasses; j++)
          tmp[j] += weight * distributionForInstance[j];
      }

      weka.core.Utils.normalize(tmp);
      return tmp;
    }

    public double[] getClassifierVote(Instance instance, int classifierIndex) throws Exception{

      if (classifierIndex >= m_NumIterations || classifierIndex < 0)
        throw new Exception("Classifier index is invalid");

      double[] distributionForInstance;
      double[] tmp = new double[m_NumClasses];

      distributionForInstance = ((DistributionClassifier) m_Classifiers.get(classifierIndex)).distributionForInstance(instance);
      double weight = m_Classifiers.getWeight(classifierIndex);
      for (int j = 0; j < m_NumClasses; j++)
        tmp[j] += weight * distributionForInstance[j];

      return tmp;
    }
  }

  /**
   * Parses a given list of options. Valid options are:<PRE>
   *
   * -M &ltcombined prediction selection model&gt
   * Specify how to combine the base classifiers in the hypothesis construction; valid options are
   * (case insensitive):
   *   - MVC = Maximum vote criterion
   *   - MVC_UCL = Maximum vote criterion using confidence levels
   *   - MECC = Minimum expected cost criterion
   *   - MECC_UCL = Minimum expected cost criterion using confidence levels
   *
   * -C cost file
   * File name of a cost matrix to use. If this is not supplied, a cost
   * matrix will be loaded on demand. The name of the on-demand file
   * is the relation name of the training data plus ".cost", and the
   * path to the on-demand file is specified with the -D option.
   *
   * -O directory
   * Name of a directory to search for cost files when loading costs on demand
   * (default current directory).
   *
   * -U
   * Initialize the weights using the costs.
   *
   * -F
   *  Use the default cost matrix.
   *
   * -X costFactor
   * The cost of misclassifying a minority class when using the default cost matrix.
   *
   * Plus the rest of the superclass options.
   *
   * </PRE>
   *
   * @param options the list of options as an array of strings
   * @exception Exception if an option is not supported
   */
  public void setOptions(String[] options) throws Exception{

    super.setOptions(options);

    String combinedPredictionModel = Utils.getOption('M', options);
    if (combinedPredictionModel.length() != 0){
      if (combinedPredictionModel.compareToIgnoreCase("MVC") == 0)
        setCombinedPredictionModel(new SelectedTag(MVC, CPM_TAGS));
      else if (combinedPredictionModel.compareToIgnoreCase("MVC_UCL") == 0)
        setCombinedPredictionModel(new SelectedTag(MVC_UCL, CPM_TAGS));
      else if (combinedPredictionModel.compareToIgnoreCase("MECC") == 0)
        setCombinedPredictionModel(new SelectedTag(MECC, CPM_TAGS));
      else if (combinedPredictionModel.compareToIgnoreCase("MECC_UCL") == 0)
        setCombinedPredictionModel(new SelectedTag(MECC_UCL, CPM_TAGS));
    }

    setInitializeWeightsUsingCosts(Utils.getFlag('U', options));

    if (Utils.getFlag('X', options))
      setCostMatrixSource(new SelectedTag(DEFAULT_MATRIX, TAGS_MATRIX_SOURCE));

    String defaultCostMatrixFactor = Utils.getOption('F', options);
    if (defaultCostMatrixFactor.length() != 0)
      setDefaultMatrixCostFactor(Double.parseDouble(defaultCostMatrixFactor));

    String costFile = Utils.getOption('C', options);
    if (costFile.length() != 0){
      try{
        setCostMatrix(new CostMatrix(new BufferedReader(
          new FileReader(costFile))));
      }
      catch (Exception ex){
        // now flag as possible old format cost matrix. Delay cost matrix
        // loading until buildClassifer is called
        setCostMatrix(null);
      }
      setCostMatrixSource(new SelectedTag(MATRIX_SUPPLIED,
                                          TAGS_MATRIX_SOURCE));
      m_CostFile = costFile;
    }
    else{
      if (m_MatrixSource != DEFAULT_MATRIX)
        setCostMatrixSource(new SelectedTag(MATRIX_ON_DEMAND,
                                            TAGS_MATRIX_SOURCE));
    }

    String demandDir = Utils.getOption('O', options);
    if (demandDir.length() != 0){
      setOnDemandDirectory(new File(demandDir));
    }
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

    String combinedPredictionModelSelected;
    switch (combinedPredictionModelSelected()){
      case MVC:
        combinedPredictionModelSelected = "MVC";
        break;
      case MVC_UCL:
        combinedPredictionModelSelected = "MVC_UCL";
        break;
      case MECC:
        combinedPredictionModelSelected = "MECC";
        break;
      case MECC_UCL:
        combinedPredictionModelSelected = "MECC_UCL";
        break;
      default:
        combinedPredictionModelSelected = "Undefined";
    }
    options[current++] = "-M";
    options[current++] = "" + combinedPredictionModelSelected;

    if (isInitializeWeightsUsingCosts())
      options[current++] = "-U";

    if (m_UseOwnTrainData){
      if (m_MatrixSource == MATRIX_SUPPLIED){
        if (m_CostFile != null){
          options[current++] = "-C";
          options[current++] = "" + m_CostFile;
        }
      }
      else if (m_MatrixSource == MATRIX_ON_DEMAND){
        options[current++] = "-O";
        options[current++] = "" + getOnDemandDirectory();
      }
      else{
        options[current++] = "-X";
        options[current++] = "-F";
        options[current++] = "" + getDefaultMatrixCostFactor();
      }
    }

    System.arraycopy(otherOptions, 0,
                     options, current,
                     otherOptions.length);

    current += otherOptions.length;
    while (current < options.length)
      options[current++] = "";

    return options;
  }

  /**
   * Returns an enumeration describing the available options
   *
   * @return an enumeration of all the available options
   */
  public Enumeration listOptions(){

    Vector newVector = new Vector(11);

    newVector.addElement(new Option(
      "\tSpecify the model used to combine the base classifiers when creating the combined hypothesis."
      + "\n\tValid options are:"
      + "\n\t - MVC (Maximum Vote Criterion)"
      + "\n\t - MVC_UCL (Maximum Vote Criterion Using Confidence Levels)"
      + "\n\t - MECC (Minimum Expected Cost Criterion)"
      + "\n\t - MECC_UCL (Minimum Expected Cost Criterion Using Confidence Levels)"
      + "\n\t(default MECC_UCL).\n.",
      "M", 1, "-M <combine prediction model>"));

    newVector.addElement(new Option(
      "\tFile name of a cost matrix to use. If this is not supplied,\n"
      + "\ta cost matrix will be loaded on demand. The name of the\n"
      + "\ton-demand file is the relation name of the training data\n"
      + "\tplus \".cost\", and the path to the on-demand file is\n"
      + "\tspecified with the -D option.",
      "C", 1, "-C <cost file name>"));

    newVector.addElement(new Option(
      "\tName of a directory to search for cost files when loading\n"
      + "\tcosts on demand (default current directory).",
      "O", 1, "-O <directory>"));

    newVector.addElement(new Option(
      "\tUse a default cost matix in which only the cost of misclassify the minority class.\n"
      + "\t\n is different from the rest costs, which will be 1.",
      "X", 0, "-X"));

    newVector.addElement(new Option(
      "\tSet the cost of misclassifying the minority class when using the default cost matrix.",
      "F", 1, "-F <cost factor>"));

    newVector.addElement(new Option(
      "\tInitialize the weights using the costs.",
      "U", 0, "-U"));

    newVector.addElement(new Option("", "", 0, "\nCommon boosters options."));

    Enumeration enum = super.listOptions();
    while (enum.hasMoreElements()){
      newVector.addElement(enum.nextElement());
    }

    return newVector.elements();
  }

  //--------------------------------------*************************************
  //--------------------- Configure the "GUI side" methods ********************
  //--------------------------------------*************************************

  /** We need to inform about how to manage CostMatrix classes */
  static{
    java.beans.PropertyEditorManager
      .registerEditor(weka.classifiers.CostMatrix.class,
                      weka.gui.CostMatrixEditor.class);
  }

  //--------------------- ToolTips text ********************

  public static String costMatrixSourceTipText(){

    return "Sets where to get the cost matrix. The three options are"
      + "to use the supplied explicit cost matrix (the setting of the "
      + "costMatrix property), to load a cost matrix from a file when "
      + "required (this file will be loaded from the directory set by the "
      + "onDemandDirectory property and will be named relation_name"
      + CostMatrix.FILE_EXTENSION + "), "
      + "or use a default cost matrix assigning 0 cost to all correct classifications, "
      + "1 to misclassifying a not minority class and the supplied costFactor to misclassifying "
      + "the minority class.";
  }

  public static String onDemandDirectoryTipText(){

    return "Sets the directory where cost files are loaded from. This option "
      + "is used when the costMatrixSource is set to \"On Demand\".";
  }

  public static String costMatrixTipText(){

    return "Sets the cost matrix explicitly. This matrix is used if the "
      + "costMatrixSource property is set to \"Supplied\".";
  }

  public static String defaultMatrixCostFactorTipText(){

    return "Set the cost of misclassifying the minority class when using the default cost matrix.";
  }

  public static String initializeWeightsUsingCostsTipText(){

    return "Initialize the weights using the costs.";
  }

  public static String combinedPredictionModelTipText(){

    return "Specify how to combine the base classifiers in the hypothesis construction.";
  }

  /**
   * Defines a "visual" order to the bean's properties of this booster.
   *
   * @return The properties order
   */
  protected CustomOrderDefiner getPropertiesOrder(){

    CustomOrderDefiner cod = new CustomOrderDefiner();

    cod.add("costMatrix");
    cod.add("costMatrixSource");
    cod.add("onDemandDirectory");
    cod.add("defaultMatrixCostFactor");
    cod.add("initializeWeightsUsingCosts");
    cod.add("combinedPredictionModel");

    CustomOrderDefiner otherOptions = super.getPropertiesOrder();
    otherOptions.mergeWith(cod);

    return otherOptions;
  }
}