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
 *    AdaCost.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.boosters.costSensitive;

import weka.classifiers.DistributionClassifier;
import weka.classifiers.Evaluation;
import weka.core.Instance;

/**
 * An implementation of AdaCost; for more information, see:<p>
 *
 * <a href="http://www.cs.columbia.edu/~sal/hpapers/ICML99-adacost.ps.gz">
 * Fan, Stolfo, Zhang & Chan, <i>AdaCost: Misclassification cost-sensitive boosting.</i>.
 * Proceedings of The Sixteenth International Conference on Machine Learning. pp. 97-105.
 * San Francisco: Morgan Kaufmann.
 * </a><p>
 *
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class AdaCost extends AbstractCSB{

  /** The cost matrix containing the total costs of misclassify each class */
  protected double[] m_CustomCostMatrix;

  /**
   * Make numIterations iterations. Construct the cost matrix required for the algorithm to work.
   *
   * @param numIterations The number of iterations to perform.
   *
   * @throws Exception If an error occurs (ej. Booster not initialized).
   */
  public void nextIterations(int numIterations) throws Exception{

    //Compute the total costs of misclassify each class and normalize them to be in [0,1]
    if (m_NumIterations == 0){
      m_CustomCostMatrix = new double[m_NumClasses];
      double maxValue = 0;
      for (int i = 0; i < m_NumClasses; i++){
        for (int j = 0; j < m_NumClasses; j++) //m_CostMatrix.getElement(i,i) must be 0 for all i
          m_CustomCostMatrix[i] += m_CostMatrix.getElement(i, j);

        if (m_CustomCostMatrix[i] > maxValue)
          maxValue = m_CustomCostMatrix[i];
      }

      for (int i = 0; i < m_NumClasses; i++)
        m_CustomCostMatrix[i] /= maxValue;
    }

    super.nextIterations(numIterations);
  }

  /**
   * Calculate the classifier's vote weight according to its training error and the cost adjustment function.
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
      int trueClass = (int) instance.classValue();

      if (classifiedAs == trueClass)
        tmp += instance.weight() * distributionForInstance[classifiedAs] * costAdjustmentFunction(trueClass, false);
      else
        tmp -= instance.weight() * distributionForInstance[classifiedAs] * costAdjustmentFunction(trueClass, true);

      sumTmp += instance.weight();
    }

    tmp = (tmp + NO_DIVISION_BY_ZERO) / (sumTmp + NO_DIVISION_BY_ZERO);

    return Math.log((1 + tmp) / (1 - tmp)) / 2;
  }

  /** @param classifier The classifier to be used to calculate new instances weights.  */
  protected void reweight(DistributionClassifier classifier, double alfa) throws Exception{
    Instance instance;
    double newWeightsSum = 0;
    for (int i = 0; i < m_NumInstances; i++){
      instance = m_TrainData.instance(i);
      int trueClassValue = (int) instance.classValue();
      double[] distributionForInstance = classifier.distributionForInstance(instance);
      int classifiedAs = oaidtb.misc.Utils.maxIndex(distributionForInstance);
      if (classifiedAs != trueClassValue)
        instance.setWeight(instance.weight() * m_CostMatrix.getElement(trueClassValue, classifiedAs) *
                           Math.exp(distributionForInstance[classifiedAs] *
                                    alfa *
                                    costAdjustmentFunction(trueClassValue, true)));
      else
        instance.setWeight(instance.weight() *
                           Math.exp(-distributionForInstance[classifiedAs] *
                                    alfa *
                                    costAdjustmentFunction(trueClassValue, false)));
      newWeightsSum += instance.weight();
    }

    normalizeWeights(newWeightsSum);
  }

  /**
   * Calculate the cost adjustment function for an instance based on if it's or not misclassified.
   *
   * @param trueClass The class the instance belongs to
   * @param isMisclassified True if the classifier correctly classifies the instance, false otherwise
   *
   * @return The cost adjustment function proposed by Fan et. el.
   */
  protected double costAdjustmentFunction(int trueClass, boolean isMisclassified){
   //TODO: Do the operations once and store the results.
    if (!isMisclassified)
      return -0.5 * m_CustomCostMatrix[trueClass] + 0.5;
    return 0.5 * m_CustomCostMatrix[trueClass] + 0.5;
  }

  /**
   * Main method for testing this class.
   *
   * @param argv the options
   */
  public static void main(String[] argv){

    try{
      System.out.println(Evaluation.evaluateModel(new AdaCost(), argv));
    }
    catch (Exception e){
      System.err.println(e.getMessage());
    }
  }
}