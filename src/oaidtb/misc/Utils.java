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
 *    Utils.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.misc;

import weka.classifiers.Classifier;
import weka.classifiers.CostMatrix;
import weka.core.*;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.beans.Introspector;
import java.beans.MethodDescriptor;
import java.beans.PropertyDescriptor;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Comparator;

/**
 * Miscellaneous (simple) utilities.
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public final class Utils{

  /**
   * Comprueba que la versión actual de la máquina virtual java
   * en que se está ejecutando la aplicación es mayor o igual a
   * otra versión
   *
   * @param minJVMVersion Una cadena conteniendo la versión de java, por ejemplo, "1.3.1"
   * @return true si la cadena que representa a la MVJ actual es mayor o igual que la pasada como parámetro
   *         (cambiarlo por algo más seguro)
   */
  public static boolean isJVMVersionGreaterOrEqualThan(String minJVMVersion){
    return minJVMVersion.compareToIgnoreCase(System.getProperty("java.version")) <= 0;
  }

  /**
   * Merge two InputMap maps
   *
   * @param dest The map where all pairs key/value will be stored
   * @param other The map which contents will be added to de dest map
   */
  public static void mergeInputMaps(InputMap dest, InputMap other){
    KeyStroke[] strokes = other.allKeys();
    if (strokes != null)
      for (int i = 0; i < strokes.length; i++)
        dest.put(strokes[i], other.get(strokes[i]));
  }

  /**
   * Merge two ActionMap maps
   *
   * @param dest The map where all pairs key/value will be stored
   * @param other The map which contents will be added to de dest map
   */
  public static void mergeActionMaps(ActionMap dest, ActionMap other){
    Object[] keys = other.allKeys();
    if (keys != null)
      for (int i = 0; i < keys.length; i++)
        dest.put(keys[i], other.get(keys[i]));
  }

  /**
   * Capture an area of the screen
   *
   * @param rectangle Rect to capture in screen coordinates
   * @return A buffered image containing the capture or null if an error happens
   */
  public static BufferedImage captureScreenShot(Rectangle rectangle){
    try{
      Robot robot = new Robot();
      return robot.createScreenCapture(rectangle);
    }
    catch (AWTException ex){
      System.err.println(ex.toString());
      return null;
    }
  }

  /**
   * @param component A component
   * @return A buffered image with component painted in
   */
  public static Image captureComponentToImage(JComponent component){

    Rectangle rect = component.getBounds();
    Image image = new BufferedImage(rect.width, rect.height, BufferedImage.TYPE_INT_ARGB);

    component.paint(image.getGraphics());

    return image;
  }


  /**
   * Check if a classifier is able to classify a type of instances.
   *
   * @param classifier The classifier
   * @param instance The instance
   * @return true or false
   */
  public static boolean classifierCanClassify(Classifier classifier, Instance instance){

    try{
      classifier.classifyInstance(instance);
      return true;
    }
    catch (Exception e){
      return false;
    }
  }

  /**
   * 1 if true, 0 if false
   *
   * @param b true or false
   * @return 1 if true, 0 if false
   */
  public static int boolToInt(boolean b){
    return b ? 1 : 0;
  }

  /**
   * Fast sum (no checks) of the components of two double arrays, storing
   * the result in the first array (arrayDest)
   *
   * @param arrayDest
   * @param arrayToSum
   */
  public static void doubleArraysSum(double[] arrayDest, double[] arrayToSum){

    for (int i = 0; i < arrayDest.length; i++)
      arrayDest[i] += arrayToSum[i];
  }

  /**
   * Search for the bigger value in an array of doubles
   *
   * @param array The array
   *
   * @return The index of the bigger value
   */
  public static int maxIndex(double[] array){
    int maxIndex = 0;

    for (int i = 1; i < array.length; i++)
      if (array[maxIndex] < array[i])
        maxIndex = i;

    return maxIndex;
  }

  /**
   * Search for the smaller value in an array of doubles
   *
   * @param array The array
   *
   * @return The index of the smaller value
   */
  public static int minIndex(double[] array){
    int minIndex = 0;

    for (int i = 1; i < array.length; i++)
      if (array[minIndex] > array[i])
        minIndex = i;

    return minIndex;
  }


  /**
   * Stores the text in a (new or overwritten) file.
   *
   * @param what The string to be written
   * @param fileName The name of the file where it will be written
   *
   * @throws Exception if an error happens
   */
  public static void printTextToFile(String what, String fileName) throws Exception{
    PrintWriter file = new PrintWriter(new FileOutputStream(fileName));
    file.println(what);
    file.close();
  }

  /**
   * Sorts an array of PropertyDescriptor objects according to their names.
   *
   * @param properties
   */
  public static void sortPropertiesByName(PropertyDescriptor[] properties){
    java.util.Arrays.sort(properties, new Comparator(){
      public int compare(Object o1, Object o2){
        PropertyDescriptor pd1 = (PropertyDescriptor) o1;
        PropertyDescriptor pd2 = (PropertyDescriptor) o2;
        return pd1.getName().compareTo(pd2.getName());
      }
    });
  }

  /**
   * Sorts an array of PropertyDescriptor objects according to a custom order;
   * all the properties not in the order will be put at the end.
   *
   * @param properties The properties to be sorted
   *
   * @param index The structure indicating the order of the properties
   *
   * @return The properties sorted
   */
  public static PropertyDescriptor[] sortProperties(PropertyDescriptor[] properties, CustomOrderDefiner index){
    PropertyDescriptor[] tmpProps = new PropertyDescriptor[properties.length];
    //We don't know if all or none  of the indexed properties are contained in the "properties" array.
    PropertyDescriptor[] withoutIndexProps = new PropertyDescriptor[properties.length];
    int withoutIndexPropsCount = 0;
    for (int i = 0; i < properties.length; i++){
      int propertyIndex = index.getIndex(properties[i].getDisplayName());
      if (propertyIndex != -1 && tmpProps[propertyIndex] == null)
        tmpProps[propertyIndex] = properties[i];
      else{
        withoutIndexProps[withoutIndexPropsCount] = properties[i];
        withoutIndexPropsCount++;
      }
    }

    for (int i = 0; i < tmpProps.length && withoutIndexPropsCount >= 0; i++){
      if (tmpProps[i] == null)
        tmpProps[i] = withoutIndexProps[--withoutIndexPropsCount];
    }

    return tmpProps;
  }

  /**
   * Returns a method to access to the cost matrix used by a cost sensitive classifier;
   * it must have the following signature: "public CostMatrix getCostMatrix()".
   *
   * @param classifier The classifier to be analyzed.
   *
   * @return The getter method or null if it doesn't exist.
   *
   * @throws Exception If an error occurs during introspection.
   */
  public static Method getCostMatrixGetter(Classifier classifier) throws Exception{

    MethodDescriptor[] methods = Introspector.getBeanInfo(classifier.getClass()).getMethodDescriptors();
    for (int i = 0; i < methods.length; i++){
      Method method = methods[i].getMethod();
      if (method.getName().equals("getCostMatrix") &&
        method.getReturnType().equals(CostMatrix.class) &&
        Modifier.isPublic(method.getModifiers()))
        return method;
    }
    return null;
  }

  /**
   * Normalize the values of an array of doubles, which can hold negative values,
   * so that the sum will be one.
   *
   * @param array The array to be normalized.
   */
  public static void secureNormalize(double[] array){

    double sumTmp = 0;
    double minValue = array[minIndex(array)];

    if (minValue < 0)
      for (int i = 0; i < array.length; i++)
        sumTmp += (array[i] -= minValue);
    else
      for (int i = 0; i < array.length; i++)
        sumTmp += array[i];

    weka.core.Utils.normalize(array, sumTmp);
  }

  /**
   * Applies Math.round and returns an integer
   *
   * @param x El ouble a redondear
   * @return El resultado de Math.round(x) como un entero
   */
  public final static int round(double x){
    return (int) Math.round(x);
  }

// TODO: create a centralized policy to handle the exceptional values produced by the Math. functions, so
// this values can't damage the algorithms (by examplo, when reweighting).
//
//  public final static double exp(double a){
//
//    if (a == Double.NaN)
//      return 0;
//
//    if (a == Double.POSITIVE_INFINITY)
//      return Double.MAX_VALUE;
//
//    return Math.exp(a);
//  }
//
//  public static double log(double a){
//
//    return Math.log(a);
//  }
}