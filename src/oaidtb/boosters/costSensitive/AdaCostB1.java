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
 *    AdaCostB1.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.boosters.costSensitive;

import weka.classifiers.Evaluation;

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
public class AdaCostB1 extends AdaCost{

  /** We set c(+) = c(-) = c(n) */
  protected double costAdjustmentFunction(int trueClass, boolean isMisclassified){
    return m_CustomCostMatrix[trueClass];
  }

  /**
   * Main method for testing this class.
   *
   * @param argv the options
   */
  public static void main(String[] argv){

    try{
      System.out.println(Evaluation.evaluateModel(new AdaCostB1(), argv));
    }
    catch (Exception e){
      System.err.println(e.getMessage());
    }
  }
}