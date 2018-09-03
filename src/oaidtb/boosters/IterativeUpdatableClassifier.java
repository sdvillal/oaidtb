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
 *    IterativeUpdatableClassifier.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.boosters;

import weka.core.Instance;

/**
 * Interface for classifiers that can be updated (refined) at any time by performing
 * more iterations
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public interface IterativeUpdatableClassifier{

  /**
   * Make numIterations iterations.
   *
   * @param numIterations The number of iterations to perform.
   *
   * @throws Exception If an error occurs (ej. Classifier not initialized).
   */
  void nextIterations(int numIterations) throws Exception;

  /**
   * Get the number of iterations performed.
   *
   * @return The number of iterations performed.
   */
  int getNumIterationsPerformed();

  /**
   * Get the classifier's vote for the instance at iteration;
   * the (vectorial) sum of all iterations classifier's vote vectors must be
   * the final combined hypothesis of the classifier.
   *
   * @param instance The instance to be classified
   * @param iterationIndex The iteration index
   * @return The vote of the classifier's iteration to the overall classifier
   * @throws Exception if an error occurs
   */
  double[] getClassifierVote(Instance instance, int iterationIndex) throws Exception;
}