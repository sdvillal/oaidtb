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
 *    Point2DInstances.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */
//En desarrollo está una variación de la clase que se está mostrando sensiblemente más eficiente

package oaidtb.gui;

import oaidtb.misc.SimpleInteger;
import weka.core.*;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.Serializable;
import java.util.*;

/**
 * A specialized weka's instances class to store bidimensional points and their colors;
 * several methods of the superclass should be not used directly if we want to preserve
 * the index construction (by example, the instances from reader loader).
 * <p>
 * TODO: Make it fully consistent with {@link weka.core.Instances} class methods; complicated,
 *       because it has a lot of final methods.
 * </p>
 *
 * <PRE>
 *
 * We maintain the following points orders:
 *
 *   --  By insertion, so we can do undo & redo (implement the history buffer) operations.
 *   --  By class (color). In every class space, by coordinates (x & y).
 *   --  By class index.
 *
 * </PRE>
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class Point2DInstances extends Instances{

  /** Fast access by index (nominal class value). Class index --> Color (real world significance) */
  private FastVector m_Colors = new FastVector();

  /** Fast acces by color & fast avoid duplicates. Color --> class index 
   * @associates SimpleInteger*/
  private HashMap m_ColorIndex = new HashMap();

  /** Comparador para el índice de puntos ordenados por colores y coordenadas */
  private Point2DInstanceComparator m_PointComparator = new Point2DInstanceComparator();
  /** Index of all points, sorted by color as primary order and by coordinates as secondary one
   * @associates Point2DInstance*/
  private TreeSet m_Points = new TreeSet(m_PointComparator);

  /** Count of the number of instances of each color */
  private int[] m_NumberOfInstancesOfColor = new int[0];

  /** Array containing all the instances ordered by insertion */
  private FastVector m_AllInstances;

  /** Array representing the train instances */
  private FastVector m_TrainInstances = new FastVector();
  /** Array representing the test instances */
  private FastVector m_TestInstances = new FastVector();

  /** Indicador de que las instancias han sido generadas sólo desde un subconjunto del total */
  private boolean m_TrainAndTestGeneratedFromregion = false;

  /** To perform the random partition of the instances set into the train and test datasets */
  private long m_InstanceChooserSeed = 0;
  /** To perform the random partition of the instances set into the train and test datasets */
  private Random m_InstanceChooser = new Random(m_InstanceChooserSeed);

  //Insert mode constants & setup
  /** Permitir la inserción de todos los puntos */
  public final static int ALLOW_ALL_POINTS_INSERT_MODE = 0;
  /** Permitir sólo un punto por coordenada y color */
  public final static int ONE_POINT_PER_CLASS_AND_COORDINATES_INSERT_MODE = 1;
  /** Permitir sólo un punto por coordenadas */
  public final static int ONE_POINT_PER_COORDINATES_INSERT_MODE = 2;
  /** El modo de inserción elegido */
  private int m_InsertMode = ONE_POINT_PER_CLASS_AND_COORDINATES_INSERT_MODE;

  /**
   * Constructor; relation name will be prefixed with the string "oaidtb_".
   *
   * @param name The name of the relation
   * @param capacity The initial capacity of the dataset.
   */
  public Point2DInstances(String name, int capacity){

    super("oaidtb_" + name, new FastVector(), capacity);

    insertAttributeAt(new Attribute("xPos"), 0);
    insertAttributeAt(new Attribute("yPos"), 1);
    insertAttributeAt(new Attribute("class"), 2);

    m_AllInstances = m_Instances;

    setClassIndex(2);
  }

  /**
   * Recrea la base de datos desde una imagen
   *
   * @param bi La imagen
   *
   * @throws Exception Si ocurre un error
   *
   * @deprecated Since a new version of the class much more faster is coming
   */
  public void createFromImage(BufferedImage bi) throws Exception{
    //TODO: Make it really fast (burbuja con sacudida, no comparaciones para la inserción etc.)

    //Borramos todas las instancias
    removeAll();

    //El modo más rápido de inserción
    int insertMode = m_InsertMode;
    m_InsertMode = ALLOW_ALL_POINTS_INSERT_MODE;

    //Vamos punto por punto metiéndolo en la base de datos; esto es extremadamente lento a medida que el árbol crece
    for (int i = 0; i < bi.getWidth(); i++)
      for (int j = 0; j < bi.getHeight(); j++)
        addPoint(i, j, new Color(bi.getRGB(i, j)));

    m_InsertMode = insertMode;
  }

  /**
   * Recrea la base de datos desde una imagen; un punto en la imagen
   * se corresponde con el valor del array de rgbs con la siguiente fórmula:
   * (x,y)-->rgbs[y*w + x]
   *
   * @param rgbs El array de rgbs para cada punto
   * @param w La anchura de la imagen
   * @param h La altura de la imagen
   *
   * @throws Exception Si ocurre un error
   */
  //TODO: Make it really fast (burbuja con sacudida, no comparaciones para la inserción etc.)
  public void createFromImage(int[] rgbs, int w, int h) throws Exception{

    removeAll();

    int insertMode = m_InsertMode;
    m_InsertMode = ALLOW_ALL_POINTS_INSERT_MODE;

    //Vamos punto por punto metiéndolo en la base de datos; esto es extremadamente lento a medida que el árbol crece
    for (int j = 0; j < h; j++){
      int base = j * w;
      for (int i = 0; i < w; i++){
        addPoint(i, j, new Color(rgbs[base + i]));
      }
    }

    m_InsertMode = insertMode;
  }

  //TODO: Make it really fast; direct copy the fastvector (not use Point2DInstance, less legibility)
  /**
   * Recrea la base de datos a partir de las instancias pasadas como argumento; este método
   * considera que las instancias han sido salvadas anteriormente por la aplicación
   * si y sólo si su nombre empieza por "oaidtb_", y en tal caso crea el array
   * de colores apartir de la información de la cabecera de las instancias;
   *
   *
   * <p><p> Nota: este método puede cambiar las instancias pasadas como argumento, así que si
   * no se desea que esto ocurra, se deben copiar antes las mismas.
   *
   * @param instances Las instancias que queremos
   *
   * @throws Exception Si el formato de las instancias no es válido o se produce un error en la inserción de las mismas
   */
  public void createFromInstances(Instances instances) throws Exception{

    //Comprobamos que el formato de las instancias sea compatible con esta base de datos
    if (instances.numAttributes() != 3 ||
      !instances.attribute(0).isNumeric() ||
      !instances.attribute(1).isNumeric() ||
      !instances.attribute(2).isNominal())
      throw new Exception("Invalid instances format");

    //Comprobamos que al menos exista una clase
    instances.deleteWithMissingClass();
    if (instances.numInstances() == 0)
      throw new Exception("No instances without class missing");

    //Creamos el array de colores
    final int numColors = instances.numClasses();
    if (instances.relationName().startsWith("oaidtb_")){
      //En este caso, los colores estarán representados en la cabecera de las instancias por sus valores argb
      final Color[] colorIndex = new Color[numColors];
      for (int i = 0; i < numColors; i++){
        colorIndex[i] = new Color(Integer.parseInt(instances.attribute(2).value(i)));
        ;
      }
      //Borramos todas las instancias que existieran
      removeAll();
      //Metemos las nuevas instancias, creando los índices
      for (int i = 0; i < instances.numInstances(); i++){
        Instance instance = instances.instance(i);
        addPoint(instance.value(0), instance.value(1), colorIndex[(int) instance.value(2)]);
      }
      setRelationName(instances.relationName());
    }
    else{
      Random random = new Random(1);
      final HashMap colorIndex = new HashMap(numColors);
      final Color[] colorArray = new Color[numColors];
      for (int i = 0; i < numColors; i++){
        Color color = new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256));
        //En este caso, vamos a generar el color que representa a cada clase de manera aleatoria;
        //TODO: Pensar en una manera más determinista y segura de hcerlo
        if (null != colorIndex.put(new SimpleInteger(i), color)) //Refine this
          System.err.println("One color represents two distinct classes");
        //throw new Exception("Same color represents two distinct classes");
        colorArray[i] = color;
      }
      //Borramos todas las instancias que existieran
      removeAll();
      //Metemos las nuevas instancias, creando los índices
      for (int i = 0; i < instances.numInstances(); i++){
        Instance instance = instances.instance(i);
        addPoint(instance.value(0), instance.value(1), colorArray[(int) instance.value(2)]);
      }
      setRelationName("oaidtb_" + instances.relationName());
    }
  }

  /**
   * Conocer cuántas instancias hay de un determinado color; por efeciencia, no se hace comprobación
   * de la validez de índice del color
   *
   * @param colorIndex El índice del color
   *
   * @return El número de instancias que existen de ese color en la base de datos
   */
  public int getNumPointsOfColor(int colorIndex){
    return m_NumberOfInstancesOfColor[colorIndex];
  }

  /**
   * Conocer cuántas instancias hay de un determinado color; por efeciencia, no se hace comprobación
   * de la validez de índice del color
   *
   * @param color El color
   *
   * @return El número de instancias que existen de ese color en la base de datos
   */
  public int getNumPointsOfColor(Color color){
    if (getColorIndex(color) == -1)
      return 0;
    return m_NumberOfInstancesOfColor[getColorIndex(color)];
  }

  /** @return Un array con todos los colores existentes en la base de datos, ordenados según la clase que representen */
  public FastVector getColors(){
    return m_Colors;
  }

  /**
   * Retorna un conjunto de datos con el mismo esquema que este objeto
   * Point2DInstances y con las instancias en el FastVector que se pasa
   * como argumento.
   *
   * No hace ningún tipo de comprobación acerca de la validez del formato de las instancias
   *
   * @param instances las instancias que queremos que contenga el conjunto de datos retornado
   *
   * @return Un objeto Instances con el esquema actual y las instancias pasadas
   */
  public Instances getInstances(FastVector instances){
    Instances tmp = new Instances(this, instances.size());
    //We must do this since m_instances has protected acces mode in the package weka.core,
    //so we can't assign directly "instances" to m_Instances
    //Another approach could be to return a Point2DInstances object, but it wouldn't
    //be index-consistent unless we made extra (overload) ops.
    for (int i = 0; i < instances.size(); i++)
      tmp.add((Instance) instances.elementAt(i));

    return tmp;
  }

  /**
   * Busca en la base de datos todas las instancias que representen puntos dentro
   * de una determinada región del espacio
   *
   * @param region La región en que deben encontrarse los puntos
   *
   * @return El array de instancias de la base de datos que se encuentran en dicha región
   */
  public FastVector getRegion(Rectangle2D.Double region){
    return getRegion(region.x, region.y, region.width, region.height);
  }

  /**
   * Busca en la base de datos todas las instancias que representen puntos dentro
   * de una determinada región del espacio
   *
   * @param x La coordenada x de la esquina superior izquierda de la región
   * @param y La coordenada y de la esquina superior izquierda de la región
   * @param width La anchura de la región
   * @param height La altura de la región
   *
   * @return El array de instancias de la base de datos que se encuentran en dicha región
   */
  public FastVector getRegion(final double x, final double y, final double width, final double height){

    //Calculamos el máximo valor de cada coordenada para que el punto esté en la región
    final double maxX = x + width;
    final double maxY = y + height;

    //El vector que retornaremos
    FastVector vector = new FastVector();

    //Iterador de instancias ordenadas por color y coordenadas
    Iterator iterator = m_Points.iterator();

    //El color de las instancias que se está procesando en cada momento
    int currentColor = -1;
    //El númedo de instancias que quedan por procesar del color actual
    int currentColorRem = 0;

    while (iterator.hasNext()){

      //Procesamos el posible cambio de color de las instancias
      if (currentColorRem <= 0){
        currentColor++;
        if (currentColor < getNumColors())
          currentColorRem = getNumPointsOfColor(currentColor);
      }

      //Actualizamos el índice del próximo punto a procesar
      currentColorRem--;

      //Recuperamos las coordenadas x e y del punto que representa la instancia
      Point2DInstance point = (Point2DInstance) iterator.next();
      final double py = point.getY();
      final double px = point.getX();

      //Debido a la ordenación por coordenadas que se hace en las instancias de cada color,
      //sabemos que estamos iterando siempre en orden creciente de la coordenada y;
      //por tanto, si ya hemos superado maxY con esa coordenada sabemos que no va
      //a haber más puntos del color dentro de la región
      if (py > maxY){
        while (--currentColorRem >= 0)
          iterator.next();
        continue;
      }

      //El punto está a la derecha de la región
      if (px > maxX)
        continue;

      //El punto está encima o a la izquierda de la región
      if (py < y || px < x)
        continue;

      //El punto está en la región
      vector.addElement(point);
    }

    return vector;
  }

  /**
   * Preguntar si un punto está o no dentro de una determinada región del espacio seguún la política
   * aplicada en todos los métodos de la clase
   *
   * @param px La coordenada x del punto
   * @param py La coordenada y del punto
   * @param x La coordenada x de la esquina superior izquierda de la región
   * @param y La coordenada y de la esquina superior izquierda de la región
   * @param width La anchura de la región
   * @param height La altura de la región
   *
   * @return true si el punto pertenece a la región
   */
  public final static boolean inRegion(final double px, final double py,
                                       final double x, final double y, final double width, final double height){
    return px >= x &&
      px <= x + width &&
      py >= y &&
      py <= y + height;
  }

  /**
   * Destruye los conjuntos de entrenamiento y de prueba
   */
  public void destroyTrainAndTestInstances(){
    m_TrainInstances.removeAllElements();
    m_TestInstances.removeAllElements();
    m_TrainAndTestGeneratedFromregion = false;
  }

  /**
   * En la clase {@link weka.core.Instances} la estratificación se hace ordenando
   * el conjunto de instancias por clase, pero esta ordenación la hacen por
   * el método de la burbula; nos aprovechamos de que con las estructuras de índices creadas
   * la tenemos hecha esa ordenación para crear una versión rápida del método estratificar de
   * la superclase y así mejorar la velocidad de la validación cruzada.
   *
   * @param numFolds the number of folds in the cross-validation
   */
  public void fastStratify(int numFolds) throws Exception{

    //Hacemos que m_Instances contenga todas las instancias ordenadas por clase
    Iterator iterator = m_Points.iterator();
    m_Instances = new FastVector(m_Points.size());
    while (iterator.hasNext())
      m_Instances.addElement(iterator.next());

    stratStep(numFolds);
  }

  /**
   * Help function needed for stratification of set.
   *
   * @param numFolds the number of folds for the stratification
   */
  private void stratStep(int numFolds){

    FastVector newVec = new FastVector(m_Instances.capacity());
    int start = 0, j;

    // create stratified batch
    while (newVec.size() < numInstances()){
      j = start;
      while (j < numInstances()){
        newVec.addElement(instance(j));
        j = j + numFolds;
      }
      start++;
    }
    m_Instances = newVec;
  }

  /**
   * Crea dos conjuntos: de entrenamiento y de prueba; lo hacemos sin complicarnos la
   * existencia, de manera aleatoria.
   *
   * @param trainingInstancesPercentage El porcentaje de instancias de entrenamiento a tomar como referencia
   */
  public void createTrainAndTestInstances(final double trainingInstancesPercentage){
    //Refine to stratification (faster than use stratify method, so override).
    //Asegurar que siempre se cojen instancias de todos los colores en ambos conjuntos.
    //Preparar para cojer sólo de un porcentaje del total de las instancias
    //Test: Suma train + Suma Test = Suma Total
    //Test: No hay instancias compartidas

    if (trainingInstancesPercentage < 0 || trainingInstancesPercentage > 1.0)
      return;

    //Cogemos las instancias
    final Iterator iterator = m_Points.iterator();

    //Destruimos los conjuntos de entrenamiento y de prueba que existiesen previamente
    destroyTrainAndTestInstances();

    //Para cada instancia, asignamos la probabilidad de estar en uno u otro conjunto en función
    //del porcentaje de instancias de entrenamiento que tenemos como referencia
    while (iterator.hasNext())
      if (m_InstanceChooser.nextDouble() < trainingInstancesPercentage)
        m_TrainInstances.addElement(iterator.next());
      else
        m_TestInstances.addElement(iterator.next());

    //Hemos generado los conjuntos usando todas las instancias
    m_TrainAndTestGeneratedFromregion = false;
  }

  /**
   * Crea los conjuntos de entrenamiento y de prueba usando el método
   * procedimiento que {@link #createTrainAndTestInstances}, sólo que ahora
   * excuimos todas aquellas instancias que no se encuentren en la región especificada.
   * Levanta la bandera que indica que la partición ha sido generada utilizando
   * sólo un subconjunto del total de instancias en la base de datos
   *
   * @param regionX La coordenada x de la esquina superior izquierda de la región
   * @param regionY La coordenada y de la esquina superior izquierda de la región
   * @param regionWidth La anchura de la región
   * @param regionHeight La altura de la región
   * @param trainingInstancesPercentage El porcentaje de instancias de entrenamiento a tomar como referencia
   */
  public void createTrainAndTestInstancesInRegion(final double regionX, final double regionY,
                                                  final double regionWidth, final double regionHeight,
                                                  final double trainingInstancesPercentage){

    if (trainingInstancesPercentage < 0 || trainingInstancesPercentage > 1.0)
      return;

    //Recuperamos todas las instancias
    final Iterator iterator = m_Points.iterator();

    //Destuimos los conjuntos de entrenamiento y prueba existentes
    destroyTrainAndTestInstances();

    //Vamos añadiendo, según la probabilidad asignada por parámetro, las instancias al conjunto de prueba
    //o al conjunto de test
    while (iterator.hasNext()){
      Point2DInstance p = (Point2DInstance) iterator.next();
      //Sólo añadimos la instancia si se encuentra en la región
      if (inRegion(p.getX(), p.getY(), regionX, regionY, regionWidth, regionHeight))
        if (m_InstanceChooser.nextDouble() < trainingInstancesPercentage)
          m_TrainInstances.addElement(p);
        else
          m_TestInstances.addElement(p);
    }

    //Levantamos la bandera que indica que esta partición ha sido generada usando
    //sólo un subconjunto del total de instancias en la base de datos
    m_TrainAndTestGeneratedFromregion = true;
  }

  /** @return true if exist a test set, false otherwise */
  public boolean existsTestSet(){
    return m_TestInstances.size() != 0;
  }

  /** @return true if exist a train set, false otherwise */
  public boolean existsTrainSet(){
    return m_TrainInstances.size() != 0;
  }

  /**
   * Select (if they exist) the train instances so that the dataset will appear to be
   * composed only of them for a weka class (classifiers, filters...)
   */
  public void selectTrainInstances(){
    if (existsTrainSet())
      m_Instances = m_TrainInstances;
  }

  /**
   * Select (if they exist) the test instances so that the dataset will appear to be
   * composed only of them for a weka class (classifiers, filters...)
   */
  public void selectTestInstances(){
    if (existsTestSet())
      m_Instances = m_TestInstances;
  }

  /** Select all the instances so that the dataset will appear to have them all for weka classes */
  public void selectAllInstances(){
    m_Instances = m_AllInstances;
  }

  /** @return The train instances FastVector  */
  public FastVector getTrainInstances(){
    return m_TrainInstances;
  }

  /** @return The test instances FastVector  */
  public FastVector getTestInstances(){
    return m_TestInstances;
  }

  /** @return true If the train and test sets were generated from a custom region  */
  public boolean isTrainAndTestGeneratedFromregion(){
    return m_TrainAndTestGeneratedFromregion;
  }

  /** @param trainAndTestGeneratedFromregion Indicator for the client classes that the train and test instances
   *                                         were generated or not form a custom space region
   */
  public void setTrainAndTestGeneratedFromregion(boolean trainAndTestGeneratedFromregion){
    m_TrainAndTestGeneratedFromregion = trainAndTestGeneratedFromregion;
  }

  /** Reset the dataset to a virgin one  */
  public void removeAll(){
    m_AllInstances.removeAllElements();
    destroyTrainAndTestInstances();
    m_Points = new TreeSet(m_PointComparator);
    m_Colors.removeAllElements();
    m_ColorIndex = new HashMap();
    trickyChangeClassAttribute();
    m_NumberOfInstancesOfColor = new int[0];
    m_InstanceChooser = new Random(m_InstanceChooserSeed);
  }

  /**
   * Removes and returns the last numPoints points input to the dataset,
   * undo all possible changes made by those insertions and return
   * a bidimensional integer arrar of the form:
   *
   * [insertionIndex]-->[x,y,rgb]
   *
   * @param numPoints The number of points to be removed
   * @return An array containing a description of the points deleted
   * @throws Exception If numPoints is great than the total number of points in the dataset
   */
  public double[][] removeLastPoints(int numPoints) throws Exception{
    int numInstances = m_AllInstances.size();
    if (numPoints > numInstances)
      throw new Exception("Can't remove " + numPoints + " from " + numInstances);
    double[][] undoBuffer = new double[numPoints][3];
    while (numPoints-- > 0){
      numInstances--;
      Point2DInstance instance = (Point2DInstance) m_AllInstances.lastElement();
      m_AllInstances.removeElementAt(numInstances);
      m_Points.remove(instance);
      undoBuffer[numPoints][0] = instance.getX();
      undoBuffer[numPoints][1] = instance.getY();
      undoBuffer[numPoints][2] = getColor((int) instance.getColor()).getRGB();
      if ((--m_NumberOfInstancesOfColor[(int) instance.getColor()]) == 0)
        removeColor((Color) m_Colors.elementAt((int) instance.getColor()));
    }

    //Es muy radical, pero obliga al usuario a volver a crear estos conjuntos.
    destroyTrainAndTestInstances();
    selectAllInstances();

    return undoBuffer;
  }

  /**
   * Configura la semilla y resetea el generador de números aleatorios para generar los
   * conjuntos de entrenamiento y prueba
   *
   * @param seed La nueva semilla
   */
  public void setInstanceChooserSeed(long seed){
    m_InstanceChooserSeed = seed;
    m_InstanceChooser = new Random(seed);
  }

  /**
   * Setup when a point will be accepted as a valid one. The possibilities are: <PRE>
   *
   *   ALLOW_ALL_POINTS_INSERT_MODE
   *   ONE_POINT_PER_CLASS_AND_COORDINATES_INSERT_MODE
   *   ONE_POINT_PER_COORDINATES_INSERT_MODE
   *
   * </PRE>
   *
   * @param mode The insert mode
   */
  public void setInsertMode(int mode){
    if (mode > -1 || mode < 3)
      m_InsertMode = mode;
  }

  /** @return The current insert mode */
  public int getInsertMode(){
    return m_InsertMode;
  }

  /**
   * Recrea el atributo de clase para que represente a todos los colores existentes
   * en la base de datos
   */
  private void trickyChangeClassAttribute(){
    // It seems like weka guys doesn't want anybody to exted the core classes, because they
    // mark their methods and fields as friendly, private and/or final, so this can't be done
    // from outside of the weka.core package:
    //    newAtt.setIndex(2); <--Friendly method
    //    m_Attributes.removeElementAt(2);
    //    m_Attributes.insertElementAt(newAtt, 2);
    //

    FastVector newClassAttributeInfo = new FastVector(m_Colors.size());
    for (int i = 0; i < m_Colors.size(); i++)
      newClassAttributeInfo.addElement(Integer.toString(((Color) m_Colors.elementAt(i)).getRGB()));

    //We don't like that insert & delete attribute methods change anything in the instances
    //(set class values as missing), so we deceive them with this trick.
    FastVector tmpInstances = m_Instances;
    m_Instances = new FastVector(0);

    setClassIndex(-1);
    deleteAttributeAt(2);
    insertAttributeAt(new Attribute("class", newClassAttributeInfo), 2);
    setClassIndex(2);

    m_Instances = tmpInstances;
  }

  /**
   * Añade, si no existía ya, un nuevo color a la base de datos; sólo debería hacerse
   * tras la inserción de la primera instancia de dicho color
   *
   * @param color El núevo color a añadir
   */
  private void addColor(Color color){

    //Lo añadimos al índice (color-->número de clase), con índices secuenciales
    SimpleInteger integer = (SimpleInteger) m_ColorIndex.put(color, new SimpleInteger(m_Colors.size()));

    //El color ya estaba
    if (integer != null){
      m_ColorIndex.put(color, integer);
      return;
    }

    //Lo añadimos al array de colores (número de clase-->color)
    m_Colors.addElement(color);

    //Preparamos el invento para poder almacenar el número de instancias de un color determinado
    int[] tmp = new int[m_NumberOfInstancesOfColor.length + 1];
    System.arraycopy(m_NumberOfInstancesOfColor, 0, tmp, 0, m_NumberOfInstancesOfColor.length);
    m_NumberOfInstancesOfColor = tmp;
    m_NumberOfInstancesOfColor[m_Colors.size() - 1] = 0;

    //Actualizar el esquema de atributos
    trickyChangeClassAttribute();
  }

  /**
   * Elimina un color de la base de datos; sólo debería hacerse cuando ya no
   * existan instancias asociadas a dicho color
   *
   * @param color El color a eliminar
   */
  private void removeColor(Color color){
    SimpleInteger integer = (SimpleInteger) m_ColorIndex.remove(color);

    //El color no estaba
    if (integer == null)
      return;

    m_Colors.removeElementAt(integer.value);

    // Como será secuencial, sabemos que siempre va a ser el último color añadido.
    int[] tmp = new int[m_NumberOfInstancesOfColor.length - 1];
    System.arraycopy(m_NumberOfInstancesOfColor, 0, tmp, 0, m_NumberOfInstancesOfColor.length - 1);
    m_NumberOfInstancesOfColor = tmp;

    //Actualizar el esquema de atributos
    trickyChangeClassAttribute();
  }

  /**
   * Añade una nueva instancia a la base de datos respetando la política de inserción configurada
   *
   * @param x La coordenada x de la instancia
   * @param y La coordenada y de la instancia
   * @param colorIndex El índice del color que representa la instancia
   *
   * @return true si la inserción se ha completado, falso en otro caso
   */
  private boolean addPoint(double x, double y, int colorIndex){

    final Point2DInstance tmpPoint = new Point2DInstance(x, y, colorIndex);

    //Better have an index by coordinates?
    //Caso de permitir sólo una instancia por cada par de coordenadas (x,y)
    if (m_InsertMode == ONE_POINT_PER_COORDINATES_INSERT_MODE){

      //Comprobamos que no exista ya una instancia con esas coordenadas en la base de datos
      for (int i = 0; i < getNumColors(); i++){
        tmpPoint.setColor(i);
        if (m_Points.contains(tmpPoint))
          return false;
      }

      //Todo ok, metemos la instancia en la base de datos
      tmpPoint.setDataset(this);
      tmpPoint.setColor(colorIndex);
      m_Points.add(tmpPoint);
      m_AllInstances.addElement(tmpPoint);
      if (existsTrainSet())
        m_TrainInstances.addElement(tmpPoint);
      m_NumberOfInstancesOfColor[colorIndex]++;
      return true;
    }//FIN del Caso de permitir sólo una instancia por cada par de coordenadas (x,y)


    // Añadimos el punto dependiendo de que nos deje añadirlo al índice principal
    if (m_Points.add(tmpPoint)){
      tmpPoint.setDataset(this);
      m_AllInstances.addElement(tmpPoint);
      if (existsTrainSet())
        m_TrainInstances.addElement(tmpPoint);
      m_NumberOfInstancesOfColor[colorIndex]++;
      return true;
    }

    return false;
  }

  /**
   * Añade una nueva instancia a la base de datos respetando la política de inserción configurada
   *
   * @param x La coordenada x de la instancia
   * @param y La coordenada y de la instancia
   * @param color El color que representa la instancia
   *
   * @return true si la inserción se ha completado, falso en otro caso
   */
  public boolean addPoint(double x, double y, Color color){
    int colorIndex = getColorIndex(color);
    if (colorIndex == -1)
      //Es un nuevo color, .lo añadimos a la base de datos
      addColor(color);
    return addPoint(x, y, getColorIndex(color));
  }

  /**
   * Acceder al índice que representa a un determinado color en la base de datos
   *
   * @param color El color
   *
   * @return El índice (valor de la clase) que representa a dicho color ó -1 si no existe dicho color en la BD
   */
  public int getColorIndex(Color color){
    SimpleInteger index = (SimpleInteger) m_ColorIndex.get(color);
    return index == null ? -1 : index.value;
  }

  /**
   * Retorna todas las instancias existentes en la base de datos ordenadas por color y, dentro
   * de cada color, ordenadas por coordenadas
   *
   * @return Un iterador sobre el total de las instancias ordenadas por color y por coordenadas
   */
  public Iterator getAllInstancesIterator(){
    return m_Points.iterator();
  }

  /**
   * Retorna un array con todas las instancias en el orden en que se insertaron en la base de datos
   *
   * @return Un array con todas las instancias en el orden en que se insertaron en la base de datos
   */
  public FastVector getAllInstancesFastVector(){
    return m_AllInstances;
  }

  /**
   * Retorna un array con todas las instancias existentes en la base de datos ordenadas por color y, dentro
   * de cada color, ordenadas por coordenadas
   *
   * @return Un array sobre el total de las instancias ordenadas por color y por coordenadas
   */
  public FastVector getAllInstancesSortedFastVector(){
    FastVector vector = new FastVector(m_Points.size());
    Iterator iterator = m_Points.iterator();
    while (iterator.hasNext())
      vector.addElement(iterator.next());
    return vector;
  }

  /**@return The total number of colors (classes) in the dataset  */
  public int getNumColors(){
    return m_Colors.size();
  }

  /**
   * No index correctness is done
   *
   * @param colorIndex a color (class) index
   * @return The color associated to that class index
   */
  public Color getColor(int colorIndex){
    return (Color) m_Colors.elementAt(colorIndex);
  }

  /**
   * @return A copy of this dataset
   * @throws Exception If a serialization error happens
   */
  public Point2DInstances getCopy() throws Exception{
    SerializedObject so = new SerializedObject(this);
    return (Point2DInstances) so.getObject();
  }

  /**
   * Class which stores a point in a 2D space into weka's Instance format.
   * <br>
   * R x R --> N.
   * <br>
   * For legibility.
   */
  public static class Point2DInstance extends Instance{

    public Point2DInstance(double x, double y, double pointColor){
      super(3);
      m_AttValues[0] = x;
      m_AttValues[1] = y;
      m_AttValues[2] = pointColor;
    }

    public double getX(){
      return m_AttValues[0];
    }

    public void setX(double x){
      m_AttValues[0] = x;
    }

    public int getXInt(){
      return (int) Math.round(m_AttValues[0]);
    }

    public double getY(){
      return m_AttValues[1];
    }

    public void setY(double y){
      m_AttValues[1] = y;
    }

    public int getYInt(){
      return (int) Math.round(m_AttValues[1]);
    }

    public double getColor(){
      return m_AttValues[2];
    }

    public void setColor(double colorIndex){
      m_AttValues[2] = colorIndex;
    }
  }

  /**
   * Defines the order by class index, putting together all the points
   * of the same class (color); in every class subtree, it
   * defines the order in a two dimensional space, where a point is of
   * order less than other if its Y coordinate is lesser
   * or, if their Y coordinates are equal, if its X coordinate is lesser.
   *
   * Respects the insert setup if all points insertion must be allowed,
   * it will never return 0.
   */
  private final class Point2DInstanceComparator implements Comparator, Serializable{

    public int compare(Object o1, Object o2){

      final Point2DInstance p1 = (Point2DInstance) o1;
      final Point2DInstance p2 = (Point2DInstance) o2;
      final int coordinatesOrder = compareCoordinates(p1, p2);
      final int classOrder = (int) (p1.getColor() - p2.getColor());

      if (m_InsertMode == ALLOW_ALL_POINTS_INSERT_MODE)
        return classOrder != 0 ? classOrder : coordinatesOrder != 0 ? coordinatesOrder : 1;

      if (m_InsertMode == ONE_POINT_PER_CLASS_AND_COORDINATES_INSERT_MODE
        && coordinatesOrder == 0
        || classOrder != 0)
        return classOrder;

      return coordinatesOrder;
    }

    /** Computes the coordinates order.  */
    private int compareCoordinates(Point2DInstance p1, Point2DInstance p2){

      final double yDifference = p1.getY() - p2.getY();

      if (yDifference != 0)
        return yDifference < 0 ? -1 : 1;

      final double xDifference = p1.getX() - p2.getX();

      if (xDifference != 0)
        return xDifference < 0 ? -1 : 1;

      return 0;
    }
  }
}