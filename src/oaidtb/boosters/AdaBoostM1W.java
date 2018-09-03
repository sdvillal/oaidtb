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
 *    AdaBoostM1W.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.boosters;

import weka.classifiers.Evaluation;

/**
 * A modification of AdaBoostM1 which works better with weak base classifiers; for more information see:<p>
 *
 * [1] - Günther Eibl & Karl Peter Pfeiffer
 * <i>How to make AdaBoost.M1 work for weak base classifiers by changing only one line of the code.</i>
 * </p>
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class AdaBoostM1W extends oaidtb.boosters.AdaBoostM1{

  /** 1 / m_NumClasses; avoid operation overload when calculating the guessing error upper bound */
  private double m_NumClassesInverse;

  /** Avoid operation overload when calculating the guessing error upper bound */
  private double m_GuesserCalculationConstant;

  /**
   * Calculate the classifier's vote weight in the final combined hypothesis based on its error.
   *
   * @param error The training error of the base classifier.
   *
   * @return The classifier's vote weight in the final combined hypothesis
   */
  protected double calculateBeta(double error){
    return Math.log(((m_NumClasses - 1) * (1 - error) + NO_DIVISION_BY_ZERO) / (error + NO_DIVISION_BY_ZERO));
  }

  /**
   * The default error value for which the algorithm will stop to iterate following the
   * customizable criterion of the number of (consecutive or not) retries the base classifier reaches a error
   * worse than this value.
   *
   * @return  The ad-hoc stopping criterion proposed in [1]
   */
  protected double defaultTooBigErrorValue(){
    if(m_NumClasses != 0)
      return 1 - 1 / m_NumClasses;
    return 0;
  }

  /** Initialize the guessing error of the training set upper bound. */
  protected void initializeErrorUpperBound(){
    if (m_CalculateErrorUpperBound){
      m_NumClassesInverse = 1.0 / m_NumClasses;
      m_GuesserCalculationConstant = 1.0 / (Math.pow(1.0 - m_NumClassesInverse, 1.0 - m_NumClassesInverse) *
        Math.pow(m_NumClassesInverse, m_NumClassesInverse));

      if (m_Debug)
        System.err.println("Calculating the guessing error upper bound.");
    }
    m_ErrorUpperBound = 1.0;
  }

  /**
   * Get the guessing error upper bound. See [1] for more info.
   *
   * @return The guessing error upper bound or -1 if no iterations have been done.
   */
  public double getErrorUpperBound(){
    if (m_NumIterations > 0){
      if (m_CalculateErrorUpperBound)
        return Double.isNaN(m_ErrorUpperBound) ? 1 : m_ErrorUpperBound;
      return 1;
    }
    return -1;
  }

  /**
   * Update the theoretical guessing error upper bound.<PRE>
   *
   *  - Note that if the error of the base classifier is greater than 1/m_NumClasses the bound is undetermined,
   *    and so its calculation will be aborted.
   * </PRE>
   *
   * @param error The current base classifier's error
   */
  protected void updateErrorUpperBound(double error){
    if (m_CalculateErrorUpperBound && !Double.isNaN(m_ErrorUpperBound)){
      if (error > (1.0 - m_NumClassesInverse)){
        m_ErrorUpperBound = Double.NaN;
        if (m_Debug)
          System.err.println("Can't bound the guessing error upper boud: base classifier's error is greater than 1/2.");
      }
      else{
        m_ErrorUpperBound *= (Math.pow(error, 1 - m_NumClassesInverse) * Math.pow(1 - error, m_NumClassesInverse)
          * m_GuesserCalculationConstant);
        if (m_Debug)
          System.err.println("\tGesser <= " + m_ErrorUpperBound);
      }
    }
  }

  /** @return The error upper bound string (by example, "Training error upper bound.") */
  public String getErrorUpperBoundName(){
    return "Guessing error of the training set upper bound";
  }

  public String calculateErrorUpperBoundTipText(){

    return "Calculate or not the " +getErrorUpperBoundName() +" (only if no iteration have been done yet).\n" +
      "See Eibl & Pfeiffer paper.";
  }

  /**
   * Main method for testing this class.
   *
   * @param argv the options
   */
  public static void main(String[] argv){

    try{
      System.out.println(Evaluation.evaluateModel(new AdaBoostM1W(), argv));
    }
    catch (Exception e){
      System.err.println(e.getMessage());
    }
  }
}