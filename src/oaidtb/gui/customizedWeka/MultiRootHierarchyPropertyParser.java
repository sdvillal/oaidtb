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
 *    MultiRootHierarchyPropertyParser.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.gui.customizedWeka;

import weka.gui.HierarchyPropertyParser;

import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.TreeMap;

/**
 * Wrapper class which allows more than one "root" package (by example "weka." & "myWeka.")
 * with weka's HierarchyPropertyParser.
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */

public class MultiRootHierarchyPropertyParser{

  /** An index of roots over the HierarchyPropertyParsers. 
   * @associates HierarchyPropertyParser*/
  protected TreeMap m_Roots = new TreeMap();

  /** The caracters that separates the individual members of the hiererchies. */
  private String m_HierarchyMembersSeparator = ", ";

  /** The caracters that separates the levels of each hiererchy. */
  private String m_HierarchyLevelsSeparator = ".";

  /**
   * Add a single property name to the tree if it is not already in it.
   *
   * @param propertyValue The property name
   */
  public void add(String propertyValue){

    HierarchyPropertyParser hpp;

    propertyValue.trim();
    StringTokenizer st = new StringTokenizer(propertyValue, m_HierarchyLevelsSeparator);
    String root = st.nextToken();

    if (null == (hpp = (HierarchyPropertyParser) m_Roots.get(root))){
      hpp = new HierarchyPropertyParser();
      hpp.setSeperator(m_HierarchyLevelsSeparator);
      m_Roots.put(new String(root), hpp);
    }

    hpp.add(propertyValue);
  }

  /**
   * Try to build a new multiroot tree and if it success replaces the old one with the new one.
   *
   * @param propertiesNames The hierarchical properties to build the tree from.
   */
  public void build(String propertiesNames){

    if(propertiesNames == null)
      return; //throw an exception

    MultiRootHierarchyPropertyParser tmp_mrhpp = new MultiRootHierarchyPropertyParser();

    StringTokenizer st = new StringTokenizer(propertiesNames, m_HierarchyMembersSeparator);
    while (st.hasMoreTokens()){
      String nextPropertyValue = st.nextToken();
      nextPropertyValue.trim();
      tmp_mrhpp.add(nextPropertyValue);
    }

    //If all is ok, replace with the new structure
    m_Roots = tmp_mrhpp.m_Roots;
  }

  /**
   * Search for a property name in the tree
   *
   * @param propertyValue The property name to search for.
   * @return True if the property name is in the tree, false otherwise
   */
  public boolean contains(String propertyValue){

    HierarchyPropertyParser hpp;

    propertyValue.trim();
    StringTokenizer st = new StringTokenizer(propertyValue, ". ");
    String root = st.nextToken();

    if (null == (hpp = (HierarchyPropertyParser) m_Roots.get(root)))
      return false;

    if (hpp.contains(propertyValue))
      return true;

    return false;
  }

  /** @return The number of roots. */
  public int size(){
    return m_Roots.size();
  }

  /**
   * @return An iterator over the HierarchyPropertyParser's
   */
  public Iterator iterator(){
    return m_Roots.values().iterator();
  }

  /**
   * @return An array containing the HierarchyPropertyParser's
   */
  public HierarchyPropertyParser[] getHPPsArray(){

    Iterator iterator = iterator();
    HierarchyPropertyParser[] tmp = new HierarchyPropertyParser[m_Roots.size()];

    for (int i = 0; i < tmp.length; i++)
      tmp[i] = (HierarchyPropertyParser) iterator.next();

    return tmp;
  }

  /*--------------------- GETTER & SETTER MEHODS ---------------------*/

  /**
   * @return The caracters that separates the levels of each hierarchy.
   */
  public String getHierarchyLevelsSeparator(){
    return m_HierarchyLevelsSeparator;
  }

  /**
   * @param hierarchyLevelsSeparator The caracters that separates the levels of each hierarchy.
   */
  public void setHierarchyLevelsSeparator(String hierarchyLevelsSeparator){
    m_HierarchyLevelsSeparator = hierarchyLevelsSeparator;
  }

  /**
   * @return The caracters that separates the individual members of the hierarchies.
   */
  public String getHierarchyMembersSeparator(){
    return m_HierarchyMembersSeparator;
  }

  /**
   * @param hierarchyMembersSeparator The caracters that separates the individual members of the hierarchies.
   */
  public void setHierarchyMembersSeparator(String hierarchyMembersSeparator){
    m_HierarchyMembersSeparator = hierarchyMembersSeparator;
  }

  /**
   * @return The first value introduced in the tree.
   * @throws Exception If an error occurs
   */
  public String getFirstValue() throws Exception{
    HierarchyPropertyParser hpp = (HierarchyPropertyParser) m_Roots.get(m_Roots.firstKey());

    if (hpp.depth() > 0){
      hpp.goToRoot();
      while (!hpp.isLeafReached())
        hpp.goToChild(0);
    }

    return hpp.fullValue();
  }
}