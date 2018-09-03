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
 *    MulticlassExtensibleBooster.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.boosters;

import weka.core.Instance;

/**
 * Interface for boosters which can handle binary nominal problems and can
 * be extended to the multiclass case using a "one-per-class" approach.
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public interface MulticlassExtensibleBooster{

  /**
   * Must the booster use its own train data copy?
   *
   * @param b True if the booster must use its own train data copy.
   */
  void setUseOwnTrainData(boolean b);

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
  double confidenceAndSign(Instance instance) throws Exception;

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
  double confidenceAndSign(Instance instance, int classifierIndex) throws Exception;
}