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
 *    ChiSquareDistribution.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.misc.pointDistributions;

import cern.jet.random.AbstractDistribution;

/**
 * Clase que implementa el interfaz {@link PointDistribution}, generando n�meros
 * mediante la distribuci�n chi-cuadrado, utilizando la librer�a de funciones matem�ticas
 * Colt que se puede encontrar en la
 *
 * <p><a href="http://tilde-hoschek.home.cern.ch/~hoschek/colt/">
 * Colt distribution homepage
 * </a></p>
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class ChiSquareDistribution extends cern.jet.random.ChiSquare implements PointDistribution{

  public void setCenter(double center){
    setState(center);
  }

  public ChiSquareDistribution(){
    super(1.0, AbstractDistribution.makeDefaultGenerator());
  }
}