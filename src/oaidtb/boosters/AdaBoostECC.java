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
 *    AdaBoostECC.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.boosters;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Random;
import java.util.Vector;

/**
 * Class for boosting using AdaBoostECC. For more information, see<p>
 *
 * <a href="ftp://theory.lcs.mit.edu/pub/people/venkat/colt99boost.ps">
 * Venkatesan Guruswami and Amit Sahai. <i>Multiclass Learning, Boosting and Error Correcting Codes</i>.
 * Proceedings of COLT'99.
 * </a><p>
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class AdaBoostECC extends AdaBoostOC{

  /**
   * We store the base classifiers and their weights dynamically. I choose this approach
   * instead of using a faster fixed length array because I want to be able of control
   * interactively the number of performed iterations (next method...).
   * @associates Classifier
   */
  private ArrayList m_Classifiers;

  /**
   * Container class for alfa and beta. We provide direct access to its fields for
   * efficiency: we only want it to store the alfas and betas in a dynamic array.
   * Note: in the symmetric version it's only neccessary to store alfa.
   */
  private static class AlfaAndBetaContainer implements Serializable{

    double alfa;
    double beta;

    public AlfaAndBetaContainer(double alfa, double beta){
      this.alfa = alfa;
      this.beta = beta;
    }
  }

  /** The votes of each base classifier, stored dinamically. 
   * @associates AlfaAndBetaContainer*/
  private ArrayList m_AlfasAndBetas;

  /** Use symmetric version to calculate alfa and beta?. Default: no. */
  private boolean m_UseSymmetricVersion = false;

  /** The theoretical training error upper bound. */
  private double m_TrainingErrorUpperBound;

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
      throw new Exception("AdaBoostECC can't handle a numeric class!");

    //We copy them thus ensuring AdaBoostECC nor other class mess it up.
    m_TrainData = new Instances(data);
    m_BoosterReady = false;  //No "return" can be done from here.
    m_TrainData.deleteWithMissingClass();

    if (m_TrainData.numInstances() == 0)
      throw new Exception("No train instances without class missing!");

    //Set up the number of classes. Be careful with binary class problems (not use AdaBoostECC).
    if (3 > (m_NumClasses = m_TrainData.numClasses()))
      System.err.println("AdaBoostECC could be not useful with binary class problems.");
    //throw new Exception("AdaBoostECC is not useful with binary class problems.");

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
    m_Classifiers = new ArrayList();

    //Initialize the "mislabel distribution".
    m_MislabelDistribution = new double[m_NumInstances][m_NumClasses];
    double incorrectLabelWeight = 1.0 / (m_NumInstances * (m_NumClasses - 1));
    for (int i = 0; i < m_NumInstances; i++)
      for (int j = 0; j < m_NumClasses; j++)
        m_MislabelDistribution[i][j] = (m_OriginalDataClasses[i] != j ? incorrectLabelWeight : 0.0);

    //Initialize tha alfas and betas array.
    m_AlfasAndBetas = new ArrayList();

    //Now we can perform new iterations.
    m_BoosterReady = true;

    //Initialize the training error upper bound.
    m_TrainingErrorUpperBound = 1;

    if (m_Debug && m_CalculateTrainingErrorUpperBound){
      System.err.println("Calculating the training error upper bound.");
      if (!m_UseSymmetricVersion && m_NormFactorUsed != Booster.NOT_NORMALIZE)
        System.err.println("Warning: because normalization will be done, this upper bound could be inaccurate.");
    }

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

    AlfaAndBetaContainer alfaAndBeta;
    Classifier baseClassifier;
    SerializedObject serializedClassifier = new SerializedObject(m_Classifier);

    if (m_Debug){
      System.err.println("Boosting without resampling.");
      if (m_UseSymmetricVersion)
        System.err.println("Using the symmetric version.");
      else
        System.err.println("Using the asymmetric version.");
    }

    // Do iterations.
    for (numIterations--; numIterations >= 0; numIterations--){

      if (m_Debug)
        System.err.println("Training classifier " + (m_NumIterations + 1));

      //Colororing genetation & U calculation
      double u = calculateU();

      // Weights redistribution.
      reweight(u);

      //Coloring application.
      relabel();

      //Copy the base classifier.
      baseClassifier = (Classifier) serializedClassifier.getObject();

      // Build the classifier.
      baseClassifier.buildClassifier(m_TrainData);

      // Alfa & Beta calculation.
      // Mislabel distribution computation.
      // Optional training error upper bound calculation.
      if (m_UseSymmetricVersion){
        alfaAndBeta = calculateSymmetricVotes(baseClassifier);
        m_AlfasAndBetas.add(m_NumIterations, alfaAndBeta);
        calculateMislabelDistribution(baseClassifier, alfaAndBeta.alfa, alfaAndBeta.beta);
        if (m_CalculateTrainingErrorUpperBound)
          updateSymmetricTrainingErrorUpperBound(alfaAndBeta.alfa, u);
      }
      else{
        alfaAndBeta = calculateAsymmetricVotes(baseClassifier);
        m_AlfasAndBetas.add(m_NumIterations, alfaAndBeta);
        if (m_CalculateTrainingErrorUpperBound)
          calculateMislabelDistribution(baseClassifier, alfaAndBeta.alfa, alfaAndBeta.beta, u);
        else
          calculateMislabelDistribution(baseClassifier, alfaAndBeta.alfa, alfaAndBeta.beta);
      }

      if (m_Debug)
        System.err.println("\talfa = " + alfaAndBeta.alfa + "  beta = " + alfaAndBeta.beta);

      //"Commit"
      m_Classifiers.add(baseClassifier);
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

    Random randomInstance = new Random(m_ResampleSeed); //Random number generator for resample.
    Instances sample;                                   //The resampled training dataset.
    AlfaAndBetaContainer alfaAndBeta;
    Classifier baseClassifier;
    SerializedObject serializedClassifier = new SerializedObject(m_Classifier);

    if (m_Debug){
      System.err.println("Boosting with resampling.");
      if (m_UseSymmetricVersion)
        System.err.println("Using the symmetric version.");
      else
        System.err.println("Using the asymmetric version.");
    }

    // Do iterations
    for (numIterations--; numIterations >= 0; numIterations--){

      if (m_Debug)
        System.err.println("Training classifier " + (m_NumIterations + 1));

      //Colororing genetation & U calculation
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

      // Alfa & Beta calculation.
      // Mislabel distribution computation.
      // Optional training error upper bound calculation.
      if (m_UseSymmetricVersion){
        alfaAndBeta = calculateSymmetricVotes(baseClassifier);
        m_AlfasAndBetas.add(m_NumIterations, alfaAndBeta);
        calculateMislabelDistribution(baseClassifier, alfaAndBeta.alfa, alfaAndBeta.beta);
        if (m_CalculateTrainingErrorUpperBound)
          updateSymmetricTrainingErrorUpperBound(alfaAndBeta.alfa, u);
      }
      else{
        alfaAndBeta = calculateAsymmetricVotes(baseClassifier);
        m_AlfasAndBetas.add(m_NumIterations, alfaAndBeta);
        if (m_CalculateTrainingErrorUpperBound)
          calculateMislabelDistribution(baseClassifier, alfaAndBeta.alfa, alfaAndBeta.beta, u);
        else
          calculateMislabelDistribution(baseClassifier, alfaAndBeta.alfa, alfaAndBeta.beta);
      }

      if (m_Debug)
        System.err.println("\talfa = " + alfaAndBeta.alfa + "  beta = " + alfaAndBeta.beta);

      //"Commit"
      m_Classifiers.add(baseClassifier);
      m_NumIterations++;
    }
  }

  /**
   * Alfa and beta claculation in the case of asymmetric version.
   *
   * @param baseClassifier The classifier used to calculate the votes.
   *
   * @return Alfa and beta.
   *
   * @throws java.lang.Exception If a classification error occurs.
   */
  private AlfaAndBetaContainer calculateAsymmetricVotes(Classifier baseClassifier)
    throws Exception{

    double sumTmpA1 = 0;
    double sumTmpA2 = 0;
    double sumTmpB1 = 0;
    double sumTmpB2 = 0;

    for (int i = 0; i < m_NumInstances; i++){
      if (baseClassifier.classifyInstance(m_TrainData.instance(i)) == 1)
        if (m_Coloring.getCode(m_NumIterations, m_OriginalDataClasses[i]) == 1)
          sumTmpA1 += m_TrainData.instance(i).weight();
        else
          sumTmpA2 += m_TrainData.instance(i).weight();
      else if (m_Coloring.getCode(m_NumIterations, m_OriginalDataClasses[i]) == 0)
        sumTmpB1 += m_TrainData.instance(i).weight();
      else
        sumTmpB2 += m_TrainData.instance(i).weight();
    }

    sumTmpA1 += Booster.NO_DIVISION_BY_ZERO;
    sumTmpA2 += Booster.NO_DIVISION_BY_ZERO;
    sumTmpB1 += Booster.NO_DIVISION_BY_ZERO;
    sumTmpB2 += Booster.NO_DIVISION_BY_ZERO;

    return new AlfaAndBetaContainer(Math.log(sumTmpA1 / sumTmpA2) / 2,
                                    -Math.log(sumTmpB1 / sumTmpB2) / 2);
  }

  /**
   * Alfa and beta calculation in the case of symmetric version.
   *
   * @param baseClassifier The classifier used to calculate the votes.
   *
   * @return Alfa and beta (= - alfa).
   *
   * @throws java.lang.Exception If a classification error occurs.
   */
  private AlfaAndBetaContainer calculateSymmetricVotes(Classifier baseClassifier)
    throws Exception{

    double sumTmpA1 = 0;
    double sumTmpA2 = 0;

    for (int i = 0; i < m_NumInstances; i++){
      if (baseClassifier.classifyInstance(m_TrainData.instance(i)) ==
        m_Coloring.getCode(m_NumIterations, m_OriginalDataClasses[i]))
        sumTmpA1 += m_TrainData.instance(i).weight();
      else
        sumTmpA2 += m_TrainData.instance(i).weight();
    }

    sumTmpA1 += Booster.NO_DIVISION_BY_ZERO;
    sumTmpA2 += Booster.NO_DIVISION_BY_ZERO;

    return new AlfaAndBetaContainer(sumTmpA1 = Math.log(sumTmpA1 / sumTmpA2) / 2, -sumTmpA1);
  }

  /**
   * Update the "mislabel distribution, with respect to the last base cassifier generated
   * and its weights (alfa and beta).
   *
   * @param baseClassifier The classifier from we obtain the fresh new information.
   * @param alfa Classifier's alfa
   * @param beta Classifier's beta
   *
   * @throws java.lang.Exception If it fails.
   */
  protected void calculateMislabelDistribution(Classifier baseClassifier, double alfa, double beta)
    throws Exception{

    double sumTmp = 0; // Faster than use Utils.normalize

    //Faster than continously call alfaOrBeta().
    alfa /= 2;
    beta /= 2;

    for (int i = 0; i < m_NumInstances; i++){
      if (baseClassifier.classifyInstance(m_TrainData.instance(i)) == 1)
        for (int j = 0; j < m_NumClasses; j++)
          sumTmp +=
            (m_MislabelDistribution[i][j] = m_MislabelDistribution[i][j] *
            Math.exp(alfa * m_Coloring.getCode(m_NumIterations, j) -
                     alfa * m_Coloring.getCode(m_NumIterations, m_OriginalDataClasses[i])));
      else
        for (int j = 0; j < m_NumClasses; j++)
          sumTmp +=
            (m_MislabelDistribution[i][j] = m_MislabelDistribution[i][j] *
            Math.exp(beta * m_Coloring.getCode(m_NumIterations, j) -
                     beta * m_Coloring.getCode(m_NumIterations, m_OriginalDataClasses[i])));
    }

    //Normalize to 1 (not z).
    for (int i = 0; i < m_NumInstances; i++)
      for (int j = 0; j < m_NumClasses; j++)
        m_MislabelDistribution[i][j] = m_MislabelDistribution[i][j] / sumTmp;
  }

  /**
   * Update the "mislabel distribution, with respect to the last base cassifier generated
   * and its weights (alfa and beta). It also updates the theoretically proved training error
   * upper bound in the asymmetric version of the algorithm.
   *
   * @param baseClassifier The classifier from we obtain the fresh new information.
   * @param alfa Classifier's alfa
   * @param beta Classifier's beta
   * @param u Current iteration u
   *
   * @throws java.lang.Exception If it fails.
   */
  private void calculateMislabelDistribution(Classifier baseClassifier, final double alfa, final double beta, double u)
    throws Exception{

    double sumTmp = 0; // Faster than use Utils.normalize

    //Faster than continously call alfaOrBeta().
    final double alfaHalf = alfa / 2;
    final double betaHalf = beta / 2;

    double z = 0;
    double sumOfWeights = 0;

    for (int i = 0; i < m_NumInstances; i++){

      Instance instance = m_TrainData.instance(i);
      sumOfWeights += instance.weight();

      if (baseClassifier.classifyInstance(m_TrainData.instance(i)) == 1){
        z += instance.weight() *
          Math.exp(m_Coloring.getCode(m_NumIterations, m_OriginalDataClasses[i]) == 0 ? alfa : -alfa);
        for (int j = 0; j < m_NumClasses; j++)
          sumTmp +=
            (m_MislabelDistribution[i][j] = m_MislabelDistribution[i][j] *
            Math.exp(alfaHalf * m_Coloring.getCode(m_NumIterations, j) -
                     alfaHalf * m_Coloring.getCode(m_NumIterations, m_OriginalDataClasses[i])));
      }
      else{
        z += instance.weight() *
          Math.exp(m_Coloring.getCode(m_NumIterations, m_OriginalDataClasses[i]) == 0 ? beta : -beta);
        for (int j = 0; j < m_NumClasses; j++)
          sumTmp +=
            (m_MislabelDistribution[i][j] = m_MislabelDistribution[i][j] *
            Math.exp(betaHalf * m_Coloring.getCode(m_NumIterations, j) -
                     betaHalf * m_Coloring.getCode(m_NumIterations, m_OriginalDataClasses[i])));
      }
    }

    //Deal with the various normalization options.
    //WARNING: is this correct?
    //if(m_NormFactorUsed != NOT_NORMALIZE){
    //}
    //Mediante estas operaciones conseguimos que z sea la que sería si no se hubieran normalizado
    //los pesos:
    // Suma antes de normalizar = m_LastDistributionWeightSumBeforeNormalize;
    // Suma tras normalizar = sumOfWeigths
    // m_LastDistributionWeightSumBeforeNormalize / sumOfWeigths --> deshace la normalización
    z *= (m_LastDistributionWeightSumBeforeNormalize / sumOfWeights);

    //Normalize to 1 (we don't use z).
    for (int i = 0; i < m_NumInstances; i++)
      for (int j = 0; j < m_NumClasses; j++)
        m_MislabelDistribution[i][j] = m_MislabelDistribution[i][j] / sumTmp;

    //Update the training error upper bound.
    m_TrainingErrorUpperBound *= (z * u + 1 - u);

    if (m_Debug)
      System.err.println("\tTEUB--> " + m_TrainingErrorUpperBound);
  }

  /**
   * Updates de training error upper bound in the case of the symmetric version.
   *
   * @param alfa Current base classifier's alfa.
   * @param u Current iteration u.
   */
  private void updateSymmetricTrainingErrorUpperBound(double alfa, double u){

    double error = 1 / (Math.exp(2 * alfa) + 1);
    double gamma = 0.5 - error;
    double update = u * Math.sqrt(1 - 4 * gamma * gamma) + 1 - u;
    m_TrainingErrorUpperBound *= update;
    if (m_Debug)
      System.err.println("\tTEUB--> " + m_TrainingErrorUpperBound);
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
    double alfaOrBeta;

    //Necessary to avoid problems (by example, J48.classifyInstance uses numClasses() instance
    //method, which must return 2 and not the number of data classes in the original dataset).
    instance = new Instance(instance);
    instance.setDataset(m_TrainData);

    for (int i = 0; i < m_NumIterations; i++){
      alfaOrBeta = (((Classifier) m_Classifiers.get(i)).classifyInstance(instance) == 1 ?
        ((AlfaAndBetaContainer) m_AlfasAndBetas.get(i)).alfa :
        ((AlfaAndBetaContainer) m_AlfasAndBetas.get(i)).beta);
      for (int j = 0; j < m_NumClasses; j++)
        sums[j] += (alfaOrBeta * m_Coloring.getCode(i, j));
    }

    //Set all classes probabilities >= 1
    double minValue = 0;

    for (int i = 0; i < m_NumClasses; i++)
      if (sums[i] < minValue)
        minValue = sums[i];

    minValue = Booster.NO_DIVISION_BY_ZERO - minValue;

    for (int i = 0; i < m_NumClasses; i++)
      sums[i] += minValue;

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
    if (classifierIndex >= m_NumIterations || classifierIndex < 0)
      throw new Exception("Classifier index is invalid");

    double[] sums = new double[m_NumClasses];
    double alfaOrBeta;

    //Necessary to avoid problems (by example, J48.classifyInstance uses numClasses() instance
    //method, which must return 2 and not the number of data classes in the original dataset).
    instance = new Instance(instance);
    instance.setDataset(m_TrainData);


    alfaOrBeta = (((Classifier) m_Classifiers.get(classifierIndex)).classifyInstance(instance) == 1 ?
      ((AlfaAndBetaContainer) m_AlfasAndBetas.get(classifierIndex)).alfa :
      ((AlfaAndBetaContainer) m_AlfasAndBetas.get(classifierIndex)).beta);
    for (int j = 0; j < m_NumClasses; j++)
      sums[j] = (alfaOrBeta * m_Coloring.getCode(classifierIndex, j));


    //Set all classes probabilities >= 1
    double minValue = 0;

    for (int i = 0; i < m_NumClasses; i++)
      if (sums[i] < minValue)
        minValue = sums[i];

    minValue = Booster.NO_DIVISION_BY_ZERO - minValue;

    for (int i = 0; i < m_NumClasses; i++)
      sums[i] += minValue;

    return sums;
  }

  /**
   * Parses a given list of options. Valid options are:</PRE>
   *
   * -A
   * Use symmetric version instead of asymmetric.
   *
   * Plus the rest of the superclass options.
   * </PRE>
   *
   * @param options the list of options as an array of strings
   *
   * @exception java.lang.Exception if an option is not supported
   */
  public void setOptions(String[] options) throws Exception{

    setUseSymmetricVersion(Utils.getFlag('A', options));

    super.setOptions(options);
  }

  /**
   * Gets the current settings of the Classifier.
   *
   * @return an array of strings suitable for passing to setOptions
   */
  public String[] getOptions(){

    String[] otherOptions = super.getOptions();

    String[] options = new String[otherOptions.length + 1];

    int current = 0;

    if (getUseSymmetricVersion())
      options[current++] = "-A";

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
      "\tIf this option is present, symmetric version will be used instead of asymmetric one." +
      " Only for \"learning purposes\".",
      "A", 0, "-A"));

    Enumeration enum = super.listOptions();
    while (enum.hasMoreElements()){
      newVector.addElement(enum.nextElement());
    }

    return newVector.elements();
  }

  /**
   * Must AdaBoostECC use the symmetric version?
   *
   * @param b True if symmetric version must be used
   */
  public void setUseSymmetricVersion(boolean b){
    m_UseSymmetricVersion = b;
  }

  /**
   * Is AdaBoostECC using the symmetric version?
   *
   * @return True if symmetric version is being used
   */
  public boolean getUseSymmetricVersion(){
    return m_UseSymmetricVersion;
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

  /**
   * Get the base classifier built on specified iteration. Be careful of not modifying it.
   *
   * @param numClassifier The number of the base classifier.
   *
   * @return The base classifier.
   *
   * @throws java.lang.Exception If numClassifier is incorrect (<0 or >m_NumIterations).
   */
  public Classifier getClassifier(int numClassifier) throws Exception{

    if (numClassifier < 0 || numClassifier >= m_NumIterations)
      throw new Exception("Classifier's number incorrect.");
    //It would be better to return a copy; it's slower too.
    return (Classifier) m_Classifiers.get(numClassifier);
  }

  /**
   * Get the weight (alfa) of the base classifier built on specified iteration.
   *
   * @param numClassifier The number of the base classifier.
   *
   * @return Classifier's alfa weight.
   *
   * @throws Exception If numClassifier is incorrect.
   */
  public double getClassifierWeight(int numClassifier) throws Exception{
    return ((AlfaAndBetaContainer) m_AlfasAndBetas.get(numClassifier)).alfa;
  }

  /**
   * Get the weight (beta) of the base classifier built on specified iteration.
   *
   * @param numClassifier The number of the base classifier.
   *
   * @return Classifier's beta weight.
   *
   * @throws Exception If numClassifier is incorrect.
   */
  public double getClassifierWeightBeta(int numClassifier) throws Exception{
    return ((AlfaAndBetaContainer) m_AlfasAndBetas.get(numClassifier)).beta;
  }

  /**
   * Returns description of the boosted classifier.
   *
   * @return description of the boosted classifier as a string
   */
  public String toString(){

    StringBuffer text = new StringBuffer();

    if (m_NumIterations == 0){
      text.append("AdaBoostECC: No model built yet.\n");
    }
    else if (m_NumIterations == 1){
      text.append("AdaBoostECC: No boosting possible, one classifier used!\n");
      text.append(m_Classifiers.get(0).toString() + "\n");
    }
    else{
      text.append("AdaBoostECC: Base classifiers and their weights: \n\n");

      for (int i = 0; i < m_NumIterations; i++){
        text.append("Classifier: " + i + ".\n");
        try{
          text.append("Classes in the subset \"1\": " + m_Coloring.getPartition(i).toString() + "\n");
        }
        catch (Exception e){
          text.append("Exception: " + e.toString() + "\n");
        }
        text.append("\t Alfa: " + ((AlfaAndBetaContainer) m_AlfasAndBetas.get(i)).alfa);
        text.append("   Beta: " + ((AlfaAndBetaContainer) m_AlfasAndBetas.get(i)).beta + ".\n");
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

  /**
   * Returns a string describing this classifier.
   *
   * @return a description of the filter suitable for
   * displaying in the explorer/experimenter gui
   */
  public String globalInfo() {

    return "An implementation of AdaBoostECC.";
  }

  //--------------------- ToolTips text ********************

  public static String useSymmetricVersionTipText(){

    return "If true, the (worse) symmetric version will be used." +
      " For \"instructional purposes\" only.";
  }

  public String calculateErrorUpperBoundTipText(){

    return "Calculate or not the " +getErrorUpperBoundName() +" (only if no iteration have been done yet).\n" +
           "See Guruswami & Sahai paper.";
  }

  /**
   * Main method for testing this class.
   *
   * @param argv the options
   */
  public static void main(String[] argv){

    try{
      System.out.println(Evaluation.evaluateModel(new AdaBoostECC(), argv));
    }
    catch (Exception e){
      System.err.println(e.getMessage());
    }
  }
}