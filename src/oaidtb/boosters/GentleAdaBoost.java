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
 *    GentleAdaBoost.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.boosters;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SerializedObject;

import java.util.ArrayList;
import java.util.Random;

/**
 * Class for boosting using GentleAdaBoost. For more information see: <p>
 * <a href="http://www-stat.stanford.edu/~jhf/ftp/boost.ps">
 * Jerome Friedman, Trevor Hastie and Robert Tibshirani:
 *  <i>Additive logistic regression: a statistical view of boosting</i>.
 * The Annals of Statistics, 38(2): 337-374, April 2000.
 * </a></p>
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class GentleAdaBoost extends Booster implements MulticlassExtensibleBooster{

  /** Must this booster use its own train data copy?. */
  private boolean m_UseOwnTrainData = true;

  /** Base classifiers used for boosting. 
   * @associates Classifier*/
  private ArrayList m_Classifiers;

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
        throw new Exception("GentleAdaBoost can't handle a numeric class!");

      //Check the number of classes.
      if (2 != data.numClasses())
        throw new Exception("GentleAdaBoost can handle binary problems only.");

      //We copy them thus ensuring GentleAdaBoost nor other class mess it up.
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

    //TODO: Create a version of AdaBoostMH which preprocess this
    //if (m_UseOwnTrainData){}

    //Create the numeric attribute wich will be the new class.
    Attribute newNumericClass = new Attribute("PseudoClass");

    //Insert the new class.
    int classIndex = m_TrainData.classIndex();
    m_TrainData.insertAttributeAt(newNumericClass, classIndex);

    //Set the new class values.
    for (int i = 0,k = 0; i < m_NumInstances; i++, k++)
      if (data.instance(k).classValue() == 0)
        m_TrainData.instance(i).setValue(classIndex, -1);
      else
        m_TrainData.instance(i).setValue(classIndex, 1);

    //Set the new class & remove old class attribute.
    m_TrainData.setClassIndex(classIndex);
    m_TrainData.deleteAttributeAt(classIndex + 1);

    //Initialize instance's weight & m_NormFactor.
    initializeNormFactor();
    initializeWeights();

    //Set up the number of performed iterations.
    m_NumIterations = 0;

    //Initialize the classifier array.
    m_Classifiers = new ArrayList(m_InitialIterations);

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
   * @exception Exception if the classifier could not be built successfully
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

      //Update instance weights.
      reweight(baseClassifier);

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
   * @exception Exception if the classifier could not be built successfully
   */
  protected void buildClassifierUsingResampling(int numIterations) throws Exception{

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

      //Update instance weights.
      reweight(baseClassifier);

      //"Commit"
      m_Classifiers.add(baseClassifier);
      m_NumIterations++;
    }
  }

  /**
   * Assign new weight to every instance in the training dataset according to its actual weight
   * and the predictions of the base classifier.
   *
   * w(i) = w(i)*exp(-y(i)baseClassifier.classifyInstance(x(i))
   *
   * @param baseClassifier The base classifier.
   *
   * @throws Exception If a classification error happens.
   */
  private void reweight(Classifier baseClassifier) throws Exception{

    Instance instance;
    double newWeightsSum = 0;

    for (int i = 0; i < m_NumInstances; i++){
      instance = m_TrainData.instance(i);
      instance.setWeight(instance.weight() *
                         Math.exp(-instance.classValue() * baseClassifier.classifyInstance(instance)));
      newWeightsSum += instance.weight();
    }

    normalizeWeights(newWeightsSum);
  }

  /**
   * Return the class predicted (<0 == class 0 and >0 == class 1) and the
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

    instance = (Instance) instance.copy();
    instance.setDataset(m_TrainData);

    for (int i = 0; i < m_NumIterations; i++)
      confidenceAndSign += ((Classifier) m_Classifiers.get(i)).classifyInstance(instance);

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


    instance = (Instance) instance.copy();
    instance.setDataset(m_TrainData);

    return ((Classifier) m_Classifiers.get(classifierIndex)).classifyInstance(instance);
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

    double[] distributionForInstance = new double[2];

    instance = (Instance) instance.copy();
    instance.setDataset(m_TrainData);

    for (int i = 0; i < m_NumIterations; i++){
      double classifiedAs = ((Classifier) m_Classifiers.get(i)).classifyInstance(instance);
      if (classifiedAs < 0)
        distributionForInstance[0] -= classifiedAs;
      else
        distributionForInstance[1] += classifiedAs;
    }

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

    double[] distributionForInstance = new double[2];

    instance = (Instance) instance.copy();
    instance.setDataset(m_TrainData);

    double classifiedAs = ((Classifier) m_Classifiers.get(classifierIndex)).classifyInstance(instance);
    if (classifiedAs < 0)
      distributionForInstance[0] = -classifiedAs;
    else
      distributionForInstance[1] = classifiedAs;

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

    return confidenceAndSign(instance) < 0 ? 0 : 1;
  }

  /**
   * Safely (and thus slower) inputs a new training instance.
   * Not implemented yet.
   *
   * @param instance The instance to addMemberName to the training set.
   *
   * @throws Exception if instance format isn't compatible with the dataset.
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
   * @throws Exception if instance number is incorrect or if there are no more training instances.
   */
  public void deleteTrainingInstance(int numInstance) throws Exception{
    throw new Exception("Not implemented yet.");
    //super.deleteTrainingInstance(numInstance);
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
      text.append("GentleAdaBoost: Base classifiers: \n\n");
      for (int i = 0; i < m_NumIterations; i++){
        text.append(m_Classifiers.get(i).toString() + "\n\n");
      }
      text.append("Number of performed Iterations: "
                  + m_NumIterations + "\n");
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
   * Get the base classifier built on specified iteration.
   *
   * @param numClassifier The number of the base classifier.
   *
   * @return The base classifier.
   *
   * @throws Exception If numClassifier is incorrect (<0 or >m_NumIterations).
   */
  public Classifier getClassifier(int numClassifier) throws Exception{

    if (numClassifier < 0 || numClassifier >= m_NumIterations)
      throw new Exception("Classifier's number incorrect.");
    //It would be better to return a copy; it's slower too.
    return (Classifier) m_Classifiers.get(numClassifier);
  }

  /**
   * Main method for testing this class.
   *
   * @param argv the options
   */
  public static void main(String[] argv){

    try{
      System.out.println(Evaluation.evaluateModel(new GentleAdaBoost(), argv));
    }
    catch (Exception e){
      System.err.println(e.getMessage());
    }
  }
}