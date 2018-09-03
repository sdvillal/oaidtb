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
 *    ErrorUpperBoundComputer.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.boosters;

/**
 * Interface for boosters that can calculate any kind of theoretical guaranteed error
 * measure upper bound (academical & validation stuff); obviously, it isn' t neccessary
 * for the booster to work and it can be harmful for the algorithm performance,
 * so the implementations of this interface  must be carefully revised to not become a bottleneck.
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public interface ErrorUpperBoundComputer{

  /** @return The error upper bound string (by example, "Training error upper bound.") */
  String getErrorUpperBoundName();

  /**
   * Get if the error bound is being calculated.
   *
   * @return true if it's being computed.
   */
  boolean getCalculateErrorUpperBound();

  /**
   * Set (only if no iterations have be done) if the error bound must be
   * wether or not calculated.
   *
   * @param b If the training error upper bound must or not be calculated.
   */
  void setCalculateErrorUpperBound(boolean b);

  /**
   * Get the error bound.
   *
   * @return The training error upper bound or -1 if no iterations have been done.
   */
  double getErrorUpperBound();
}