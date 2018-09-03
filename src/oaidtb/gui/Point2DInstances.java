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
//En desarrollo est� una variaci�n de la clase que se est� mostrando sensiblemente m�s eficiente

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

  /** Comparador para el �ndice de puntos ordenados por colores y coordenadas */
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

  /** Indicador de que las instancias han sido generadas s�lo desde un subconjunto del total */
  private boolean m_TrainAndTestGeneratedFromregion = false;

  /** To perform the random partition of the instances set into the train and test datasets */
  private long m_InstanceChooserSeed = 0;
  /** To perform the random partition of the instances set into the train and test datasets */
  private Random m_InstanceChooser = new Random(m_InstanceChooserSeed);

  //Insert mode constants & setup
  /** Permitir la inserci�n de todos los puntos */
  public final static int ALLOW_ALL_POINTS_INSERT_MODE = 0;
  /** Permitir s�lo un punto por coordenada y color */
  public final static int ONE_POINT_PER_CLASS_AND_COORDINATES_INSERT_MODE = 1;
  /** Permitir s�lo un punto por coordenadas */
  public final static int ONE_POINT_PER_COORDINATES_INSERT_MODE = 2;
  /** El modo de inserci�n elegido */
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
    //TODO: Make it really fast (burbuja con sacudida, no comparaciones para la inserci�n etc.)

    //Borramos todas las instancias
    removeAll();

    //El modo m�s r�pido de inserci�n
    int insertMode = m_InsertMode;
    m_InsertMode = ALLOW_ALL_POINTS_INSERT_MODE;

    //Vamos punto por punto meti�ndolo en la base de datos; esto es extremadamente lento a medida que el �rbol crece
    for (int i = 0; i < bi.getWidth(); i++)
      for (int j = 0; j < bi.getHeight(); j++)
        addPoint(i, j, new Color(bi.getRGB(i, j)));

    m_InsertMode = insertMode;
  }

  /**
   * Recrea la base de datos desde una imagen; un punto en la imagen
   * se corresponde con el valor del array de rgbs con la siguiente f�rmula:
   * (x,y)-->rgbs[y*w + x]
   *
   * @param rgbs El array de rgbs para cada punto
   * @param w La anchura de la imagen
   * @param h La altura de la imagen
   *
   * @throws Exception Si ocurre un error
   */
  //TODO: Make it really fast (burbuja con sacudida, no comparaciones para la inserci�n etc.)
  public void createFromImage(int[] rgbs, int w, int h) throws Exception{

    removeAll();

    int insertMode = m_InsertMode;
    m_InsertMode = ALLOW_ALL_POINTS_INSERT_MODE;

    //Vamos punto por punto meti�ndolo en la base de datos; esto es extremadamente lento a medida que el �rbol crece
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
   * Recrea la base de datos a partir de las instancias pasadas como argumento; este m�todo
   * considera que las instancias han sido salvadas anteriormente por la aplicaci�n
   * si y s�lo si su nombre empieza por "oaidtb_", y en tal caso crea el array
   * de colores apartir de la informaci�n de la cabecera de las instancias;
   *
   *
   * <p><p> Nota: este m�todo puede cambiar las instancias pasadas como argumento, as� que si
   * no se desea que esto ocurra, se deben copiar antes las mismas.
   *
   * @param instances Las instancias que queremos
   *
   * @throws Exception Si el formato de las instancias no es v�lido o se produce un error en la inserci�n de las mismas
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
      //En este caso, los colores estar�n representados en la cabecera de las instancias por sus valores argb
      final Color[] colorIndex = new Color[numColors];
      for (int i = 0; i < numColors; i++){
        colorIndex[i] = new Color(Integer.parseInt(instances.attribute(2).value(i)));
        ;
      }
      //Borramos todas las instancias que existieran
      removeAll();
      //Metemos las nuevas instancias, creando los �ndices
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
        //TODO: Pensar en una manera m�s determinista y segura de hcerlo
        if (null != colorIndex.put(new SimpleInteger(i), color)) //Refine this
          System.err.println("One color represents two distinct classes");
        //throw new Exception("Same color represents two distinct classes");
        colorArray[i] = color;
      }
      //Borramos todas las instancias que existieran
      removeAll();
      //Metemos las nuevas instancias, creando los �ndices
      for (int i = 0; i < instances.numInstances(); i++){
        Instance instance = instances.instance(i);
        addPoint(instance.value(0), instance.value(1), colorArray[(int) instance.value(2)]);
      }
      setRelationName("oaidtb_" + instances.relationName());
    }
  }

  /**
   * Conocer cu�ntas instancias hay de un determinado color; por efeciencia, no se hace comprobaci�n
   * de la validez de �ndice del color
   *
   * @param colorIndex El �ndice del color
   *
   * @return El n�mero de instancias que existen de ese color en la base de datos
   */
  public int getNumPointsOfColor(int colorIndex){
    return m_NumberOfInstancesOfColor[colorIndex];
  }

  /**
   * Conocer cu�ntas instancias hay de un determinado color; por efeciencia, no se hace comprobaci�n
   * de la validez de �ndice del color
   *
   * @param color El color
   *
   * @return El n�mero de instancias que existen de ese color en la base de datos
   */
  public int getNumPointsOfColor(Color color){
    if (getColorIndex(color) == -1)
      return 0;
    return m_NumberOfInstancesOfColor[getColorIndex(color)];
  }

  /** @return Un array con todos los colores existentes en la base de datos, ordenados seg�n la clase que representen */
  public FastVector getColors(){
    return m_Colors;
  }

  /**
   * Retorna un conjunto de datos con el mismo esquema que este objeto
   * Point2DInstances y con las instancias en el FastVector que se pasa
   * como argumento.
   *
   * No hace ning�n tipo de comprobaci�n acerca de la validez del formato de las instancias
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
   * de una determinada regi�n del espacio
   *
   * @param region La regi�n en que deben encontrarse los puntos
   *
   * @return El array de instancias de la base de datos que se encuentran en dicha regi�n
   */
  public FastVector getRegion(Rectangle2D.Double region){
    return getRegion(region.x, region.y, region.width, region.height);
  }

  /**
   * Busca en la base de datos todas las instancias que representen puntos dentro
   * de una determinada regi�n del espacio
   *
   * @param x La coordenada x de la esquina superior izquierda de la regi�n
   * @param y La coordenada y de la esquina superior izquierda de la regi�n
   * @param width La anchura de la regi�n
   * @param height La altura de la regi�n
   *
   * @return El array de instancias de la base de datos que se encuentran en dicha regi�n
   */
  public FastVector getRegion(final double x, final double y, final double width, final double height){

    //Calculamos el m�ximo valor de cada coordenada para que el punto est� en la regi�n
    final double maxX = x + width;
    final double maxY = y + height;

    //El vector que retornaremos
    FastVector vector = new FastVector();

    //Iterador de instancias ordenadas por color y coordenadas
    Iterator iterator = m_Points.iterator();

    //El color de las instancias que se est� procesando en cada momento
    int currentColor = -1;
    //El n�medo de instancias que quedan por procesar del color actual
    int currentColorRem = 0;

    while (iterator.hasNext()){

      //Procesamos el posible cambio de color de las instancias
      if (currentColorRem <= 0){
        currentColor++;
        if (currentColor < getNumColors())
          currentColorRem = getNumPointsOfColor(currentColor);
      }

      //Actualizamos el �ndice del pr�ximo punto a procesar
      currentColorRem--;

      //Recuperamos las coordenadas x e y del punto que representa la instancia
      Point2DInstance point = (Point2DInstance) iterator.next();
      final double py = point.getY();
      final double px = point.getX();

      //Debido a la ordenaci�n por coordenadas que se hace en las instancias de cada color,
      //sabemos que estamos iterando siempre en orden creciente de la coordenada y;
      //por tanto, si ya hemos superado maxY con esa coordenada sabemos que no va
      //a haber m�s puntos del color dentro de la regi�n
      if (py > maxY){
        while (--currentColorRem >= 0)
          iterator.next();
        continue;
      }

      //El punto est� a la derecha de la regi�n
      if (px > maxX)
        continue;

      //El punto est� encima o a la izquierda de la regi�n
      if (py < y || px < x)
        continue;

      //El punto est� en la regi�n
      vector.addElement(point);
    }

    return vector;
  }

  /**
   * Preguntar si un punto est� o no dentro de una determinada regi�n del espacio segu�n la pol�tica
   * aplicada en todos los m�todos de la clase
   *
   * @param px La coordenada x del punto
   * @param py La coordenada y del punto
   * @param x La coordenada x de la esquina superior izquierda de la regi�n
   * @param y La coordenada y de la esquina superior izquierda de la regi�n
   * @param width La anchura de la regi�n
   * @param height La altura de la regi�n
   *
   * @return true si el punto pertenece a la regi�n
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
   * En la clase {@link weka.core.Instances} la estratificaci�n se hace ordenando
   * el conjunto de instancias por clase, pero esta ordenaci�n la hacen por
   * el m�todo de la burbula; nos aprovechamos de que con las estructuras de �ndices creadas
   * la tenemos hecha esa ordenaci�n para crear una versi�n r�pida del m�todo estratificar de
   * la superclase y as� mejorar la velocidad de la validaci�n cruzada.
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
    //Preparar para cojer s�lo de un porcentaje del total de las instancias
    //Test: Suma train + Suma Test = Suma Total
    //Test: No hay instancias compartidas

    if (trainingInstancesPercentage < 0 || trainingInstancesPercentage > 1.0)
      return;

    //Cogemos las instancias
    final Iterator iterator = m_Points.iterator();

    //Destruimos los conjuntos de entrenamiento y de prueba que existiesen previamente
    destroyTrainAndTestInstances();

    //Para cada instancia, asignamos la probabilidad de estar en uno u otro conjunto en funci�n
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
   * Crea los conjuntos de entrenamiento y de prueba usando el m�todo
   * procedimiento que {@link #createTrainAndTestInstances}, s�lo que ahora
   * excuimos todas aquellas instancias que no se encuentren en la regi�n especificada.
   * Levanta la bandera que indica que la partici�n ha sido generada utilizando
   * s�lo un subconjunto del total de instancias en la base de datos
   *
   * @param regionX La coordenada x de la esquina superior izquierda de la regi�n
   * @param regionY La coordenada y de la esquina superior izquierda de la regi�n
   * @param regionWidth La anchura de la regi�n
   * @param regionHeight La altura de la regi�n
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

    //Vamos a�adiendo, seg�n la probabilidad asignada por par�metro, las instancias al conjunto de prueba
    //o al conjunto de test
    while (iterator.hasNext()){
      Point2DInstance p = (Point2DInstance) iterator.next();
      //S�lo a�adimos la instancia si se encuentra en la regi�n
      if (inRegion(p.getX(), p.getY(), regionX, regionY, regionWidth, regionHeight))
        if (m_InstanceChooser.nextDouble() < trainingInstancesPercentage)
          m_TrainInstances.addElement(p);
        else
          m_TestInstances.addElement(p);
    }

    //Levantamos la bandera que indica que esta partici�n ha sido generada usando
    //s�lo un subconjunto del total de instancias en la base de datos
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
   * Configura la semilla y resetea el generador de n�meros aleatorios para generar los
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
   * A�ade, si no exist�a ya, un nuevo color a la base de datos; s�lo deber�a hacerse
   * tras la inserci�n de la primera instancia de dicho color
   *
   * @param color El n�evo color a a�adir
   */
  private void addColor(Color color){

    //Lo a�adimos al �ndice (color-->n�mero de clase), con �ndices secuenciales
    SimpleInteger integer = (SimpleInteger) m_ColorIndex.put(color, new SimpleInteger(m_Colors.size()));

    //El color ya estaba
    if (integer != null){
      m_ColorIndex.put(color, integer);
      return;
    }

    //Lo a�adimos al array de colores (n�mero de clase-->color)
    m_Colors.addElement(color);

    //Preparamos el invento para poder almacenar el n�mero de instancias de un color determinado
    int[] tmp = new int[m_NumberOfInstancesOfColor.length + 1];
    System.arraycopy(m_NumberOfInstancesOfColor, 0, tmp, 0, m_NumberOfInstancesOfColor.length);
    m_NumberOfInstancesOfColor = tmp;
    m_NumberOfInstancesOfColor[m_Colors.size() - 1] = 0;

    //Actualizar el esquema de atributos
    trickyChangeClassAttribute();
  }

  /**
   * Elimina un color de la base de datos; s�lo deber�a hacerse cuando ya no
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

    // Como ser� secuencial, sabemos que siempre va a ser el �ltimo color a�adido.
    int[] tmp = new int[m_NumberOfInstancesOfColor.length - 1];
    System.arraycopy(m_NumberOfInstancesOfColor, 0, tmp, 0, m_NumberOfInstancesOfColor.length - 1);
    m_NumberOfInstancesOfColor = tmp;

    //Actualizar el esquema de atributos
    trickyChangeClassAttribute();
  }

  /**
   * A�ade una nueva instancia a la base de datos respetando la pol�tica de inserci�n configurada
   *
   * @param x La coordenada x de la instancia
   * @param y La coordenada y de la instancia
   * @param colorIndex El �ndice del color que representa la instancia
   *
   * @return true si la inserci�n se ha completado, falso en otro caso
   */
  private boolean addPoint(double x, double y, int colorIndex){

    final Point2DInstance tmpPoint = new Point2DInstance(x, y, colorIndex);

    //Better have an index by coordinates?
    //Caso de permitir s�lo una instancia por cada par de coordenadas (x,y)
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
    }//FIN del Caso de permitir s�lo una instancia por cada par de coordenadas (x,y)


    // A�adimos el punto dependiendo de que nos deje a�adirlo al �ndice principal
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
   * A�ade una nueva instancia a la base de datos respetando la pol�tica de inserci�n configurada
   *
   * @param x La coordenada x de la instancia
   * @param y La coordenada y de la instancia
   * @param color El color que representa la instancia
   *
   * @return true si la inserci�n se ha completado, falso en otro caso
   */
  public boolean addPoint(double x, double y, Color color){
    int colorIndex = getColorIndex(color);
    if (colorIndex == -1)
      //Es un nuevo color, .lo a�adimos a la base de datos
      addColor(color);
    return addPoint(x, y, getColorIndex(color));
  }

  /**
   * Acceder al �ndice que representa a un determinado color en la base de datos
   *
   * @param color El color
   *
   * @return El �ndice (valor de la clase) que representa a dicho color � -1 si no existe dicho color en la BD
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