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
 *    CSAdaBoostMH.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.boosters.costSensitive;

import oaidtb.boosters.AdaBoostMH;
import oaidtb.boosters.Booster;
import oaidtb.boosters.MulticlassExtensibleBooster;
import oaidtb.misc.CustomOrderDefiner;
import weka.classifiers.CostMatrix;
import weka.classifiers.Evaluation;
import weka.core.*;

import java.beans.PropertyDescriptor;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Enumeration;
import java.util.Vector;

/**
 * A class that implements a modification of AdaBoostMH for cost sensitive classification; it uses
 * an AbstractCSB booster to construct an hypothesis for each class, providing it with a 2x2 cost matrix
 * where the element (0,1) is the cost of misclassifying any other class as that class and the element
 * (1,0) is the cost of misclassifying that class as any other class.
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class CSAdaBoostMH extends AdaBoostMH{

  /** The cost matrix. */
  protected CostMatrix m_CostMatrix = new CostMatrix(1);

  /*-- Specify possible sources of the cost matrix --*/
  private static int MATRIX_ON_DEMAND = 1, MATRIX_SUPPLIED = 2, DEFAULT_MATRIX = 3;
  public static final Tag[] TAGS_MATRIX_SOURCE = {
    new Tag(MATRIX_ON_DEMAND, "Load cost matrix on demand"),
    new Tag(MATRIX_SUPPLIED, "Use explicit cost matrix"),
    new Tag(DEFAULT_MATRIX, "Use default cost matrix")
  };

  /** The cost of misclassify a minority class when using the default cost matrix */
  protected double m_DefaultMatrixCostFactor = 2.0;

  /** Indicates the current cost matrix source */
  protected int m_MatrixSource = MATRIX_ON_DEMAND;

  /** The directory used when loading cost files on demand, null indicates current directory. */
  protected File m_OnDemandDirectory = new File(System.getProperty("user.dir"));

  /** The name of the cost file, for command line options */
  protected String m_CostFile;
  /*-- Specify possible sources of the cost matrix --*/

  /** Default constructor to set CSB2 as the default base booster. */
  public CSAdaBoostMH(){
    super();
    m_Booster = new CSB2();
    ((MulticlassExtensibleBooster)m_Booster).setUseOwnTrainData(false);
  }

  /** @return The cost matrix */
  public CostMatrix getCostMatrix(){
    return m_CostMatrix;
  }

  /** @param costMatrix The cost matrix to be used  */
  public void setCostMatrix(CostMatrix costMatrix){
    m_CostMatrix = costMatrix;
    m_MatrixSource = MATRIX_SUPPLIED;
  }

  /**
   * Gets the source location method of the cost matrix. Will be one of
   * MATRIX_ON_DEMAND, MATRIX_SUPPLIED or DEFAULT_MATRIX
   *
   * @return the cost matrix source.
   */
  public SelectedTag getCostMatrixSource(){

    return new SelectedTag(m_MatrixSource, TAGS_MATRIX_SOURCE);
  }

  /**
   * Sets the source location of the cost matrix. Values other than
   * MATRIX_ON_DEMAND, MATRIX_SUPPLIED or DEFAULT_MATRIX will be ignored.
   *
   * @param newMethod the cost matrix location method.
   */
  public void setCostMatrixSource(SelectedTag newMethod){

    if (newMethod.getTags() == TAGS_MATRIX_SOURCE){
      m_MatrixSource = newMethod.getSelectedTag().getID();
    }
  }

  /**
   * Returns the directory that will be searched for cost files when
   * loading on demand.
   *
   * @return The cost file search directory.
   */
  public File getOnDemandDirectory(){

    return m_OnDemandDirectory;
  }

  /**
   * Sets the directory that will be searched for cost files when
   * loading on demand.
   *
   * @param newDir The cost file search directory.
   */
  public void setOnDemandDirectory(File newDir){

    if (newDir.isDirectory()){
      m_OnDemandDirectory = newDir;
    }
    else{
      m_OnDemandDirectory = new File(newDir.getParent());
    }
    m_MatrixSource = MATRIX_ON_DEMAND;
  }

  /** @return The cost of misclassifying the minority class when using the default cost matrix. */
  public double getDefaultMatrixCostFactor(){
    return m_DefaultMatrixCostFactor;
  }

  /** @param defaultMatrixCostFactor The cost of misclassifying the minority class when using the default cost matrix.*/
  public void setDefaultMatrixCostFactor(double defaultMatrixCostFactor){
    m_DefaultMatrixCostFactor = defaultMatrixCostFactor;
  }

  /**
   * Boosting method.
   * Reset the model and initialize data.
   *
   * @param data the training data to be used for generating the
   * boosted classifier.
   * @exception Exception if the classifier could not be built successfully
   */
  public void buildClassifier(Instances data) throws Exception{

    //Check glogal settings.
    if (m_Booster == null)
      throw new Exception("A base classifier has not been specified!");

    if (!(m_Booster instanceof MulticlassExtensibleBooster))
      throw new Exception("Base classifier must be a \"Multiclass Extensible Booster\".");

    if (!data.classAttribute().isNominal())
      throw new Exception("AdaBoostMH can't handle not nominal classes!");

    //We copy them thus ensuring AdaBoostMH nor other class mess it up.
    m_TrainData = new Instances(data);
    m_TrainData.deleteWithMissingClass();

    if ((m_NumInstances = m_TrainData.numInstances()) == 0)
      throw new Exception("No train instances without class missing!");

    if (3 > (m_NumClasses = m_TrainData.numClasses()))
      System.err.println("AdaBoostMH could not be useful with binary class problems.");

    ((MulticlassExtensibleBooster) m_Booster).setUseOwnTrainData(false);

    m_OriginalDataClasses = new double[m_NumInstances];

    for (int i = 0; i < m_NumInstances; i++)
      m_OriginalDataClasses[i] = m_TrainData.instance(i).classValue();

    //Set up the cost matrix
    if (m_MatrixSource == MATRIX_ON_DEMAND){
      String costName = data.relationName() + CostMatrix.FILE_EXTENSION;
      File costFile = new File(getOnDemandDirectory(), costName);
      if (!costFile.exists()){
        throw new Exception("On-demand cost file doesn't exist: " + costFile);
      }
      setCostMatrix(new CostMatrix(new BufferedReader(
        new FileReader(costFile))));
    }
    else if (m_MatrixSource == MATRIX_SUPPLIED){
      if (m_CostMatrix == null){
        // try loading an old format cost file
        m_CostMatrix = new CostMatrix(data.numClasses());
        m_CostMatrix.readOldFormat(new BufferedReader(
          new FileReader(m_CostFile)));
      }
    }
    else{
      m_CostMatrix = AbstractCSB.generateMinorityClassSensitiveCostMatrix(m_TrainData, m_DefaultMatrixCostFactor);
      m_MatrixSource = DEFAULT_MATRIX;
    }

    //Creamos el nuevo atributo que representará la "metaclase".
    FastVector my_nominal_values = new FastVector(2);

    my_nominal_values.addElement("0");
    my_nominal_values.addElement("1");

    int classIndex = m_TrainData.classIndex();
    m_TrainData.setClassIndex(-1);

    m_TrainData.deleteAttributeAt(classIndex);
    m_TrainData.insertAttributeAt(new Attribute("PseudoClass", my_nominal_values), classIndex);
    m_TrainData.setClassIndex(classIndex);

    m_Boosters = new Booster[m_NumClasses];
    SerializedObject serializedBooster = new SerializedObject(m_Booster);
    for (int i = 0; i < m_NumClasses; i++)
      m_Boosters[i] = (Booster) serializedBooster.getObject();

    m_InstanceWeights = new double[m_NumClasses][m_NumInstances];

    boolean showDebugInfo = m_Booster.getDebug();

    for (int i = 0; i < m_NumClasses; i++){
      CostMatrix tmp = getBinaryCostMatrix(i);
      if (showDebugInfo){
        System.err.println("\n*-*-*-*-*Building booster for class " + i + "*-*-*-*-*");
        System.err.println("Cost of missclassify the class " + i + ": " + tmp.getElement(1, 0));
        System.err.println("Cost of missclassify as class " + i + ": " + tmp.getElement(0, 1));
//        System.err.println("NOTE: Due to a ¿bug? in Matrix.toString() they can be shown rounded.")
      }
      relabel(i);
      ((AbstractCSB) m_Boosters[i]).setCostMatrix(tmp);
      m_Boosters[i].buildClassifier(m_TrainData);
      saveBoosterWeights(i);
    }
  }

  /**
   * Given a class index, returns a 2x2 cost matrix where where the element (0,1) is the cost of misclassifying
   * any other class as that class and the element (1,0) is the cost of misclassifying that class as any other class.
   *
   * @param classIndex The index of the class we will construct a booster for
   *
   * @return The binary cost matrix
   */
  private CostMatrix getBinaryCostMatrix(int classIndex){

    CostMatrix c = new CostMatrix(2);

    //Costo de clasificar la clase en cuestión como cualquier otra.
    double tmp = 0.0;
    for (int i = 0; i < m_NumClasses; i++)
      tmp += m_CostMatrix.getElement(classIndex, i);

    c.setElement(1, 0, tmp);

    //Costo de clasificar cualquier otra otra clase como la clase en cuestión.
    tmp = 0.0;
    for (int i = 0; i < m_NumClasses; i++)
      tmp += m_CostMatrix.getElement(i, classIndex);

    c.setElement(0, 1, tmp);

    return c;
  }

  /**
   * Parses a given list of options. Valid options are:<PRE>
   *
   * -C cost file
   * File name of a cost matrix to use. If this is not supplied, a cost
   * matrix will be loaded on demand. The name of the on-demand file
   * is the relation name of the training data plus ".cost", and the
   * path to the on-demand file is specified with the -D option.
   *
   * -D directory
   * Name of a directory to search for cost files when loading costs on demand
   * (default current directory).
   *
   * -F
   *  Use the default cost matrix.
   *
   * -X costFactor
   * The cost of misclassifying a minority class when using the default cost matrix.
   *
   * -B classname
   * Specify a class (must be an AbstractCSB subclass) for
   * use as the base Booster.
   *
   * </PRE>
   *
   * @param options the list of options as an array of strings
   * @exception Exception if an option is not supported
   */
  public void setOptions(String[] options) throws Exception{

    String boosterName = Utils.getOption('B', options);
    if (boosterName.length() == 0)
      setBooster((Booster) Utils.forName(AbstractCSB.class,
                                         "oaidtb.boosters.costSensitive.CSB2", options));
    else
      setBooster((Booster) Utils.forName(AbstractCSB.class, boosterName, options));

    if(null != m_Booster)
      ((MulticlassExtensibleBooster)m_Booster).setUseOwnTrainData(false);

    if (Utils.getFlag('X', options))
      setCostMatrixSource(new SelectedTag(DEFAULT_MATRIX, TAGS_MATRIX_SOURCE));

    String defaultCostMatrixFactor = Utils.getOption('F', options);
    if (defaultCostMatrixFactor.length() != 0)
      setDefaultMatrixCostFactor(Double.parseDouble(defaultCostMatrixFactor));

    String costFile = Utils.getOption('C', options);
    if (costFile.length() != 0){
      try{
        setCostMatrix(new CostMatrix(new BufferedReader(
          new FileReader(costFile))));
      }
      catch (Exception ex){
        // now flag as possible old format cost matrix. Delay cost matrix
        // loading until buildClassifer is called
        setCostMatrix(null);
      }
      setCostMatrixSource(new SelectedTag(MATRIX_SUPPLIED,
                                          TAGS_MATRIX_SOURCE));
      m_CostFile = costFile;
    }
    else{
      if (m_MatrixSource != DEFAULT_MATRIX)
        setCostMatrixSource(new SelectedTag(MATRIX_ON_DEMAND,
                                            TAGS_MATRIX_SOURCE));
    }

    String demandDir = Utils.getOption('D', options);
    if (demandDir.length() != 0){
      setOnDemandDirectory(new File(demandDir));
    }
  }

  /**
   * Gets the current settings of the Classifier.
   *
   * @return an array of strings suitable for passing to setOptions
   */
  public String[] getOptions(){

    String[] otherOptions = super.getOptions();

    String[] options = new String[otherOptions.length + 3];

    int current = 0;

    if (m_MatrixSource == MATRIX_SUPPLIED){
      if (m_CostFile != null){
        options[current++] = "-C";
        options[current++] = "" + m_CostFile;
      }
    }
    else if (m_MatrixSource == MATRIX_ON_DEMAND){
      options[current++] = "-O";
      options[current++] = "" + getOnDemandDirectory();
    }
    else{
      options[current++] = "-X";
      options[current++] = "-F";
      options[current++] = "" + getDefaultMatrixCostFactor();
    }

    System.arraycopy(otherOptions, 0,
                     options, current,
                     otherOptions.length);

    current += otherOptions.length;
    while (current < options.length)
      options[current++] = "";

    return options;
  }

  /**
   * Returns an enumeration describing the available options
   *
   * @return an enumeration of all the available options
   */
  public Enumeration listOptions(){

    Vector newVector = new Vector(8);

    newVector.addElement(new Option(
      "\tFile name of a cost matrix to use. If this is not supplied,\n"
      + "\ta cost matrix will be loaded on demand. The name of the\n"
      + "\ton-demand file is the relation name of the training data\n"
      + "\tplus \".cost\", and the path to the on-demand file is\n"
      + "\tspecified with the -D option.",
      "C", 1, "-C <cost file name>"));

    newVector.addElement(new Option(
      "\tName of a directory to search for cost files when loading\n"
      + "\tcosts on demand (default current directory).",
      "O", 1, "-O <directory>"));

    newVector.addElement(new Option(
      "\tUse a default cost matix in which only the cost of misclassify the minority class.\n"
      + "\t\n is different from the rest costs, which will be 1.",
      "X", 0, "-X"));

    newVector.addElement(new Option(
      "\tSet the cost of misclassifying the minority class when using the default cost matrix.",
      "F", 1, "-F <cost factor>"));

    newVector.addElement(new Option("", "", 0, "\nCommon boosters options."));

    Enumeration enum = super.listOptions();
    while (enum.hasMoreElements())
      newVector.addElement(enum.nextElement());

    return newVector.elements();
  }

  //--------------------------------------*************************************
  //--------------------- Configure the "GUI side" methods ********************
  //--------------------------------------*************************************

  // We need to inform about how to manage CostMatrix classes
  static{
    if(null == java.beans.PropertyEditorManager.findEditor(weka.classifiers.CostMatrix.class))
      java.beans.PropertyEditorManager.registerEditor(weka.classifiers.CostMatrix.class,
                                                      weka.gui.CostMatrixEditor.class);
  }

  /**
   * Returns a string describing this classifier.
   *
   * @return a description of the filter suitable for
   * displaying in the explorer/experimenter gui
   */
  public String globalInfo(){

    return "A class that implements a modification of AdaBoostMH for cost sensitive classification; "
      + "it uses an AbstractCSB booster to construct an hypothesis for each class, "
      + "providing it with a 2x2 cost matrix where the element (0,1) is the cost of "
      + "misclassifying any other class as that class and the element (1,0) is the cost of "
      + "misclassifying that class as any other class.";
  }

  //--------------------- ToolTips text ********************

  public static String costMatrixSourceTipText(){

    return "Sets where to get the cost matrix. The three options are"
      + "to use the supplied explicit cost matrix (the setting of the "
      + "costMatrix property), to load a cost matrix from a file when "
      + "required (this file will be loaded from the directory set by the "
      + "onDemandDirectory property and will be named relation_name"
      + CostMatrix.FILE_EXTENSION + "), "
      + "or use a default cost matrix assigning 0 cost to all correct classifications, "
      + "1 to misclassifying a not minority class and the supplied costFactor to misclassifying "
      + "the minority class.";
  }

  public static String onDemandDirectoryTipText(){

    return "Sets the directory where cost files are loaded from. This option "
      + "is used when the costMatrixSource is set to \"On Demand\".";
  }

  public static String costMatrixTipText(){

    return "Sets the cost matrix explicitly. This matrix is used if the "
      + "costMatrixSource property is set to \"Supplied\".";
  }

  public static String defaultMatrixCostFactorTipText(){

    return "Set the cost of misclassifying the minority class when using the default cost matrix.";
  }

  public String boosterTipText(){

    return "The base (AbstractCSB) booster which will be used.";
  }

  /**
   * Defines a "visual" order to the bean's properties of this booster.
   *
   * @return The properties order
   */
  protected CustomOrderDefiner getPropertiesOrder(){

    CustomOrderDefiner cod = new CustomOrderDefiner();

    cod.add("booster");
    cod.add("costMatrix");
    cod.add("costMatrixSource");
    cod.add("onDemandDirectory");
    cod.add("defaultMatrixCostFactor");

    return cod;
  }

  /**
   * Sorts the properties that will be shown in a customizedWeka.PropertySheetPanel,
   * by default following the order defined by getPropertiesOrder(). It can be used for more
   * ambitious purposes since it will be used instead of getPropertiesDescriptor there, so it will
   * prevail even over an hypothetical customized BeanInfo class.
   *
   * @param properties The properties to be sort (commonly the own class properties).
   *
   * @return The properties sorted (or even changed) according to an specified criterion.
   */
  public PropertyDescriptor[] sortProperties(PropertyDescriptor[] properties){
    return oaidtb.misc.Utils.sortProperties(properties, getPropertiesOrder());
  }


  /**
   * Main method for testing this class.
   *
   * @param argv the options
   */
  public static void main(String[] argv){

    try{
      System.out.println(Evaluation.evaluateModel(new CSAdaBoostMH(), argv));
    }
    catch (Exception e){
      System.err.println(e.getMessage());
    }
  }
}