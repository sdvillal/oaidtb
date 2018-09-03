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
 *    AdaCostB2.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.boosters.costSensitive;

import weka.classifiers.DistributionClassifier;
import weka.classifiers.Evaluation;
import weka.core.Instance;

/**
 * One of the small changes proposed for AdaCost in:<p>
 *
 * <a href="http://www.gscit.monash.edu.au/~kmting/Papers/icml00.ps">
 * Ting, K.M., <i>A Comparative Study of Cost-Sensitive Boosting Algorithms.</i>.
 * Proceedings of The Seventeenth International Conference on Machine Learning. pp. 983-990.
 * San Francisco: Morgan Kaufmann. Stanford University, June 29 - July 2, 2000.
 * </a><p>
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class AdaCostB2 extends AdaCostB1{

  /**
   *  We return to the origin using the same function in AbstractCSB; in other words, not using
   *  the cost adjustment function when calculating alfa.
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
   * Main method for testing this class.
   *
   * @param argv the options
   */
  public static void main(String[] argv){

    try{
      System.out.println(Evaluation.evaluateModel(new AdaCostB2(), argv));
    }
    catch (Exception e){
      System.err.println(e.getMessage());
    }
  }

}