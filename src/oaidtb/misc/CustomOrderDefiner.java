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
 *    CustomOrderDefinerHashMap.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.misc;

import java.util.*;
import java.io.Serializable;

/**
 * Class which defines a customized order of elements; in other words, an index of
 * objects over they absolute positions in the order (from 0 to numObjects-1).
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class CustomOrderDefiner implements Serializable{

  /** Absolute position --> object 
   * @associates Object*/
  protected ArrayList retrieveByOrder = new ArrayList();

  /** Object --> Absolute position 
   * @associates SimpleInteger*/
  protected HashMap retrieveByObject = new HashMap();

  /**
   * Adds an object with index the number of already added objects - 1.
   *
   * @param o The object
   *
   * @return True if the object isn't yet in the index, false otherwise
   */
  public boolean add(Object o){
    if(retrieveByObject.containsKey(o))
      return false;

    retrieveByObject.put(o, new SimpleInteger(retrieveByObject.size()));
    retrieveByOrder.add(o);
    return true;
  }

  /**
   * Get the position of the specified object
   *
   * @param o The object we are searching for its position in the order
   *
   * @return The position or -1 if the object isn't in the index
   */
  public int getIndex(Object o){
    SimpleInteger index = (SimpleInteger) retrieveByObject.get(o);
    return(index == null ? -1 : index.value);
  }

  /**
   * Get the object in the specified position
   *
   * @param index The position of the object
   *
   * @return The object
   */
  public Object getObject(int index){
    return retrieveByOrder.get(index);
  }

  /** @return The number of objects in the index. */
  public int size(){
    return retrieveByObject.size();
  }

  /**
   * Append all the elements, not already in the index, to the end of this order,
   * following the order defined by "other".
   *
   * @param other The other ordered objects to be merged with this one
   */
  public void mergeWith(CustomOrderDefiner other){
    for(int i=0; i<other.retrieveByOrder.size();i++)
      add(other.retrieveByOrder.get(i));
  }
}