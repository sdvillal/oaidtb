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
 *    NormalDistribution.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.misc.pointDistributions;

import edu.cornell.lassp.houle.RngPack.RandomElement;
import cern.jet.random.AbstractDistribution;

/**
 * Clase que implementa el interfaz {@link PointDistribution}, generando números
 * mediante la distribución normal, utilizando la librería de funciones matemáticas
 * Colt que se puede encontrar en la
 *
 * <p><a href="http://tilde-hoschek.home.cern.ch/~hoschek/colt/" >
 * Colt distribution homepage
 * </a></p>
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class NormalDistribution extends cern.jet.random.Normal implements PointDistribution{

  public NormalDistribution(){
    super(1, 20, AbstractDistribution.makeDefaultGenerator());
  }

  /**
   * Se tomará el centro como la media de la distribución
   *
   * @param center La media de la distribución
   */
  public void setCenter(double center){
    setState(center,standardDeviation);
  }

  public void setStandardDeviation(double standardDeviation){
    setState(mean,standardDeviation);
  }

  public double getStandardDeviation(){
    return standardDeviation;
  }

  public NormalDistribution(double mean, double standardDeviation, RandomElement randomGenerator){
    super(mean, standardDeviation, randomGenerator);
  }
}