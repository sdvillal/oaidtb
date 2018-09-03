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
 *    SimpleInteger.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.misc;

/**
 * Clase que encapsula un tipo primitivo double en un objeto
 * con mayor eficiencia en términos de memoria que la clase Integer.
 *
 * <a href="http://www.javaworld.com/javaworld/javatips/jw-javatip130.html">
 * Vladimir Roubtsov. <i>Do you know your data size?</i>.
 * JavaWorld tips and tricks nº 130
 * </a><p>
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class SimpleDouble extends Number{

  //No lo hacemos final para poder hacer cosas como value++
  public double value;

  public SimpleDouble(final double value){
    this.value = value;
  }

  public boolean equals(Object obj){
    if (obj instanceof SimpleDouble)
      return value == ((SimpleDouble) obj).value;
    return false;
  }

  public int hashCode() {
    return new Double(value).hashCode();
  }

  public String toString() {
    return String.valueOf(value);
  }

  /**
   * Returns the value of the specified number as a <code>double</code>.
   * This may involve rounding.
   *
   * @return  the numeric value represented by this object after conversion
   *          to type <code>double</code>.
   */
  public double doubleValue(){
    return value;
  }

  /**
   * Returns the value of the specified number as a <code>float</code>.
   * This may involve rounding.
   *
   * @return  the numeric value represented by this object after conversion
   *          to type <code>float</code>.
   */
  public float floatValue(){
    return (float) value;
  }

  /**
   * Returns the value of the specified number as an <code>int</code>.
   * This may involve rounding.
   *
   * @return  the numeric value represented by this object after conversion
   *          to type <code>int</code>.
   */
  public int intValue(){
    return (int) value;
  }

  /**
   * Returns the value of the specified number as a <code>long</code>.
   * This may involve rounding.
   *
   * @return  the numeric value represented by this object after conversion
   *          to type <code>long</code>.
   */
  public long longValue(){
    return (long) value;
  }
}