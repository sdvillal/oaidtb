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
 *    Circle.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.misc.pointDistributions;

import java.util.Random;

/**
 * Clase que implementa el interfaz {@link PointDistribution} y que genera números de
 * la siguiente forma: <PRE>
 * Se genera un ángulo (a) aleatoriamente, y en las dos siguientes llamadas
 * a nextDouble se generan los números:
 *    - centro + seno(a) * radio
 *    - centro + coseno(a) * radio
 * y tras esto se vuelve a elegir un ángulo aleatoriamente; así, la siguiente secuencia
 * de llamadas genera aleatoriamente un punto (c1, c2) en la circunferencia de centro (x,y) y radio r:
 *   setRadius(r)
 *   setCenter(x)
 *   c1=nextDouble()
 *   setCenter(y)
 *   c2=nextDouble()
 *</PRE>
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class Circle implements PointDistribution{

  /** El centro */
  private double center;
  /** El radio */
  private double radius = 30;
  /** Es la primera llamada de la secuencia? */
  private boolean isX = true;
  /** Generador de ángulos aleatorios */
  private Random random = new Random();
  /** Un ángulo generado aleatoriamente */
  private double currentAngle;

  /** @return El radio de la circunferencia */
  public double getRadius(){
    return radius;
  }

  /** @param radius El radio de la circunferencia  */
  public void setRadius(double radius){
    this.radius = radius;
  }

  /** @param center El centro de la circunferencia */
  public void setCenter(double center){
    this.center = center;
  }

  /** @return El siguiente número de la secuencia */
  public double nextDouble(){
    if(isX){
      currentAngle = 360 * random.nextDouble();
      isX = false;
      return center + radius * Math.sin(currentAngle);
    }
    isX = true;
    return center + radius * Math.cos(currentAngle);
  }
}