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
 *    AbstractNominalToOCFilter.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.filters;

import weka.core.*;
import weka.filters.Filter;

import java.util.BitSet;
import java.util.Enumeration;
import java.util.Vector;

/**
 * Abstract class defining the structure and common code to our nominal to output codes classes.
 * For an approach to output codes in machine learning theory, see:
 *
 * <a href="ftp://ftp.cs.orst.edu/pub/tgd/papers/jair-ecoc.ps.gz">
 * Dietterich, T. G., Bakiri, G. (1995) Solving Multiclass Learning Problems via Error-Correcting Output Codes.
 * Journal of Artificial Intelligence Research 2: 263-286.
 * </a><p>
 *
 * <PRE>
 * How is stored the code?. We use a fixed array of Java's ArrayBits, where:
 *   - Each BitSet correspond to one attribute's value (normally the class). Of course, it must be nominal.
 *   - A bit in the nth position of a BitSet informs about in with subset is a value in the nth partition.
 *   - We call "code word" to each BitSet.
 *
 * Look at this matrix:
 *
 * BitSet (Class Value) -->               0 1 2 3 4 ... numDistinctValues()
 *-------------------------------------------------------------------------
 * Partition 0                      |     1 0 1 0 1 ...
 * Partition 1                      |     0 0 1 0 1 ...
 * Partition 2                      |     1 1 0 0 0 ...
 * Partition ...                    |     .............
 *
 *   ** Each row is a partition.
 *   ** Each column is a code word.
 *
 * Of course, this hold method can be changed by subclasses.
 *
 * Why program them with the appearance of a weka's filter, when obviously it's unnecessary complex?
 *   - Learn about how weka filters work.
 *   - It could be useful for other purposes than simply use them in AdaBoostOC;
 *     output codes are a standalone topic in machine learning theory.
 * </PRE>
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public abstract class AbstractNominalToOCFilter extends Filter implements OptionHandler{

  // TO DO: Prepare for string attributes.

  /** Generated code.*/
  protected BitSet[] m_OutputCode;

  /** Number of generated partitions. It must be compact/not fragmented (it doesn't exist not generated partitions).*/
  protected int m_NumGeneratedPartitions;

  /**
   * Index of processed attribute in the input dataset (it will be transformed into a binary one
   * in the output dataset).
   */
  protected int m_ProcessedAttribute = -1;

  /** Flag to know if m_ProcessedAttribute has been set by the user explicitly. */
  protected boolean m_IsCustomProcessedAttribute = false;

  /**
   * Generate a new partition incrementing the number of partitions generated,
   * and so incrementing by 1 the length of all code words. It's equivalent
   * to newPartition(instances, m_NumGeneratedPartitions), but it must be implemented to
   * be faster.
   *
   * @param instances Perhaps a subclasss will use some information of them.
   *
   * @throws Exception If an error occurs.
   */
  public void newPartition(Instances instances) throws Exception{
    newPartition(instances, m_NumGeneratedPartitions);
  }

  /**
   * Generate a new partition at specified position even though it has already been generated
   * (it will be overwritten).
   *
   * @param instances Perhaps a subclasss will use some information of them.
   * @param partitionNumber The number of the partition to generate.
   *
   * @throws Exception If partitionNumber is incorrect or another error occurs.
   */
  public abstract void newPartition(Instances instances, int partitionNumber) throws Exception;

  /**
   * Get the partitionNumber'nth partition.
   *
   * @param partitionNumber Index of the partition.
   *
   * @return The corresponding partition.
   *
   * @throws Exception if index is invalid.
   */
  public BitSet getPartition(int partitionNumber) throws Exception{

    if (partitionNumber < m_NumGeneratedPartitions && partitionNumber > -1){
      BitSet partition = new BitSet(m_OutputCode.length);
      for (int i = 0; i < m_OutputCode.length; i++)
        if (m_OutputCode[i].get(partitionNumber))
          partition.set(i);
      return partition;
    }
    throw new Exception("Partition index incorrect, not computed yet.");
  }

  /**
   * Set the partitionNumber partition.
   *
   * @param partitionNumber Index of the partition.
   * @param newPartition The new partition to put in the partitionNumber index.
   *
   * @throws Exception if index is invalid.
   */
  public void setPartition(int partitionNumber, BitSet newPartition) throws Exception{

    if (partitionNumber > m_NumGeneratedPartitions || partitionNumber < 0)
      throw new Exception("Partition number must be between 0 and " + (m_NumGeneratedPartitions - 1));

    for (int i = 0; i < m_OutputCode.length; i++)
      if (newPartition.get(i))
        m_OutputCode[i].set(partitionNumber);
      else
        m_OutputCode[i].clear(partitionNumber);
  }

  /**
   * Get the i code word.
   *
   * @param i Index of the code word..
   *
   * @return The code word.
   *
   * @throws Exception if index is invalid.
   */
  public BitSet getCodeWord(int i) throws Exception{

    if (i >= m_OutputCode.length)
      throw new Exception("Index of code word is incorrect.");

    return m_OutputCode[i];
  }

  /**
   * Set the i code word.
   *
   * @param i Index of the code word..
   * @param codeWord The code word to put at i.
   *
   * @throws Exception if index is invalid.
   */
  public void setCodeWord(int i, BitSet codeWord) throws Exception{

    if (i >= m_OutputCode.length)
      throw new Exception("Index of code word is incorrect.");

    m_OutputCode[i] = codeWord;
  }

  /***
   * Get to which subset (0 or 1) belongs the attribute value "attValue" at partition "numPartition".
   * It doesn't make any parameter error control, so be careful.
   *
   * @param numPartition The partition to look at.
   * @param attValue The value we ask to which set belongs.
   *
   * @return The set (0 or 1).
   */
  public int getCode(int numPartition, int attValue){

    return m_OutputCode[attValue].get(numPartition) ? 1 : 0;
  }

  /**
   * Sets the format of the input instances.
   *
   * @param instanceInfo an Instances object containing the input instance structure.
   *
   * @return true if the outputFormat may be collected immediately
   * @exception Exception if the format couldn't be set successfully
   */
  public boolean setInputFormat(Instances instanceInfo) throws Exception{

    int i;
    Instances outputFormat;

    // Prepare to manage string attributes (eg. see AttributeFilter.java).
    if (instanceInfo.checkForStringAttributes())
      throw new Exception("Can´t manage string attributes yet.");

    //"calling inputFormat() should initialize the filter's internal state, but
    // not alter any variables corresponding to user-provided command line options".
    if (!m_IsCustomProcessedAttribute){
      if ((m_ProcessedAttribute = instanceInfo.classIndex()) < 0){
        m_ProcessedAttribute = instanceInfo.numAttributes() - 1;
      }
    }
    else if (m_ProcessedAttribute >= instanceInfo.numAttributes())
      throw new Exception("Attribute choose is incorrect: it doesn't exist.");

    if (!instanceInfo.attribute(m_ProcessedAttribute).isNominal())
      throw new Exception("The attribute isn't nominal.");

    //Ojito con esto.
    if (instanceInfo.numDistinctValues(m_ProcessedAttribute) < 3)
      System.err.println("Choosen attribute has less than 3 classes, it's not useful to apply the filter.");

    super.setInputFormat(instanceInfo);

    //Create the output structure.
    FastVector atributos = new FastVector(instanceInfo.numAttributes());

    for (i = 0; i < m_ProcessedAttribute; i++)
      atributos.addElement(instanceInfo.attribute(i).copy());

    //We create the new binary attribute which will represent the "metaclass".
    FastVector my_nominal_values = new FastVector(2);

    my_nominal_values.addElement("0");
    my_nominal_values.addElement("1");

    atributos.addElement(new Attribute(instanceInfo.attribute(m_ProcessedAttribute).name(), my_nominal_values));

    for (i = m_ProcessedAttribute + 1; i < instanceInfo.numAttributes(); i++)
      atributos.addElement(instanceInfo.attribute(i).copy());

    outputFormat = new Instances(instanceInfo.relationName(), atributos, 0);
    outputFormat.setClassIndex(instanceInfo.classIndex());

    setOutputFormat(outputFormat);

    m_NumGeneratedPartitions = 0;

    return true;
  }

  /**
   * Input an instance for filtering. Ordinarily the instance is processed
   * and made available for output immediately. Some filters require all
   * instances be read before producing output.
   *
   * @param instance the input instance
   * @return true if the filtered instance may now be
   * collected with output().
   * @exception IllegalStateException if no input format has been defined.
   */
  public boolean input(Instance instance) throws Exception{

    int i;

    if (getInputFormat() == null)
      throw new IllegalStateException("No input instance format defined");

    double[] vals = new double[getOutputFormat().numAttributes()];

    for (i = 0; i < m_ProcessedAttribute; i++)
      vals[i] = instance.value(i);

    vals[m_ProcessedAttribute] = getCode(m_NumGeneratedPartitions - 1, (int) instance.value(m_ProcessedAttribute));

    for (i = m_ProcessedAttribute + 1; i < getOutputFormat().numAttributes(); i++)
      vals[i] = instance.value(i);

    Instance inst;
    if (instance instanceof SparseInstance){
      inst = new SparseInstance(instance.weight(), vals);
    }
    else{
      inst = new Instance(instance.weight(), vals);
    }

    inst.setDataset(getOutputFormat());
    push(inst);
    return true;
  }

  /**
   * Parses a given list of options controlling the behaviour of this object.
   * Valid options are:<p>
   *
   * -C index <br>
   * Specify attribute index to be processed.
   * (default last attribute or class attribute if it's set)<p>
   *
   * @param options the list of options as an array of strings
   * @exception Exception if an option is not supported
   */
  public void setOptions(String[] options) throws Exception{

    String atributoProcesado = Utils.getOption('C', options);
    if (atributoProcesado.length() != 0)
      setProcessedAttribute(Integer.parseInt(atributoProcesado));
  }

  /**
   * Gets the current settings of the filter.
   *
   * @return an array of strings suitable for passing to setOptions
   */
  public String[] getOptions(){

    String[] options = new String[2];
    int current = 0;

    options[current++] = "-C";
    options[current++] = String.valueOf(m_ProcessedAttribute);

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

    Vector newVector = new Vector(1);

    newVector.addElement(new Option(
      "\tSpecify what attribute will be processed."
      + "\tDefault: class attribute or last attribute if class is missing.",
      "C", 1, "-C <index>"));

    return newVector.elements();
  }

  /**
   * Sets the (nominal, numDifferentValues > 2) attribute which will be processed.
   * A priori, it doesn't know anything about the dataset, so no error control is done.
   * If it's called with a value less than 0, the filter will choose the class attribute or the last
   * attribute if class isn't defined when a call to setInputFormat() is done.
   *
   * @param processedAttribute The attribute (index) which will be processed.
   */
  public void setProcessedAttribute(int processedAttribute){
    m_ProcessedAttribute = processedAttribute;
    m_IsCustomProcessedAttribute = processedAttribute < 0 ? false : true;
  }

  /**
   * Get the processed attribute index.
   *
   * @return The processed attribute index.
   */
  public int getProcessedAttribute(){
    return m_ProcessedAttribute;
  }
}
