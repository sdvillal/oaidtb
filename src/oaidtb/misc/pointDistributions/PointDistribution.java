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
 *    PointDistribution.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.misc.pointDistributions;

import java.io.Serializable;

/**
 * Interfaz sencilla para aquellas clases que, a partir de un punto "central" u "origen"
 * proporcionan números basados en dicho centro utilizando alguna función.
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public interface PointDistribution extends Serializable{

  /**
   * Configurar el punto de origen para la generación de números
   *
   * @param center El número sobre el que aplicar la función para obtener otros n´´umeros
   */
  void setCenter(double center);

  /**
   * Proporciona el siguiente número
   *
   * @return El siguiente número de la serie
   */
  double nextDouble();
}