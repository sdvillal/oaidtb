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
 *    WeightedClassifierVector.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.boosters;

import weka.classifiers.Classifier;

import java.util.ArrayList;
import java.io.Serializable;

/**
 * Container class, type-conscious, to store classifiers with its vote weights.
 * It's a more ironclad solution than simply use an ArrayList in the client class.
 * Legibility is its main objective. It doesn´t make any error check, so be careful.
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class WeightedClassifierVector implements Serializable{

  /** The classifiers list. 
   * @associates ClassifierAndWeight*/
  private ArrayList classifierList = new ArrayList();

  /**
   * Inner class. It stores a classifier with it's vote weight.
   * Another solution could be use two ArrayList, one for classifiers and other for weights.
   *
   * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
   */
  private static class ClassifierAndWeight implements Serializable{

    //Friendly members; not a good object oriented approach, ¿if efficiency is not a relevant issue,
    //set them private and supply getter and setter methods?.
    Classifier classifier;
    double weight;

    /**
     * Constructs with specified parameters.
     *
     * @param classifier The classifier to be stored.
     * @param weight The weight of the classifier's vote.
     */
    public ClassifierAndWeight(Classifier classifier, double weight){
      this.classifier = classifier;
      this.weight = weight;
    }

    /**
     * Constructs with specified parameters. Weight is equal to zero.
     *
     * @param classifier The classifier to be stored.
     */
    public ClassifierAndWeight(Classifier classifier){
      this.classifier = classifier;
    }

    /**
     * Set the classifier's vote weight.
     *
     * @param weight The weight.
     */
    public void setWeight(double weight){
      this.weight = weight;
    }
  }

  /**
   * Adds a classifier to the array with a weight of zero.
   *
   * @param c The classifier to addMemberName to the array.
   */
  public void add(Classifier c){
    classifierList.add(new ClassifierAndWeight(c));
  }

  /**
   * Adds a classifier to the array with the specified weight.
   *
   * @param c The classifier to addMemberName to the array.
   * @param w The classifier's weight.
   */
  public void add(Classifier c, double w){
    classifierList.add(new ClassifierAndWeight(c, w));
  }

  /**
   * Set the classifier's vote weight of classifier added at the specified index;
   * it doesn't handle a possible array's bound error, so be careful.
   *
   * @param index The classifier's index.
   * @param weight The classifier's vote weight.
   */
  public void setWeight(int index, double weight){
    ((ClassifierAndWeight) classifierList.get(index)).weight = weight;
  }

  /**
   * Get the classifier added at the specified index; it doesn't handle a possible
   * array's bound error, so be careful.
   *
   * @param index The classifier's index.

   * @return The classifier.
   */
  public Classifier get(int index){
    return ((ClassifierAndWeight) classifierList.get(index)).classifier;
  }

  /**
   * Get the classifier's vote weight of classifier added at the specified index;
   * it doesn't handle a possible array's bound error, so be careful.
   *
   * @param index The classifier's index.
   *
   * @return The classifier's vote weight.
   */
  public double getWeight(int index){
    return ((ClassifierAndWeight) classifierList.get(index)).weight;
  }

  /**
   * Returns the number of classifiers in the list.
   *
   * @return The number of classifiers in the list.
   */
  public int size(){
    return classifierList.size();
  }
}