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
 *    NominalToRandomOCFilter.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.filters;

import weka.core.Instances;
import weka.core.Option;
import weka.core.Utils;
import weka.filters.Filter;

import java.util.BitSet;
import java.util.Enumeration;
import java.util.Random;
import java.util.Vector;

/**
 * A nominal to output code filter; it choose to which subset (0 or 1) each attribute value
 * drop uniformly and independently at random.
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 * @see oaidtb.filters.AbstractNominalToOCFilter
 * @see oaidtb.filters.AbstractNominalToRandomOCFilter
 */
public class NominalToRandomOCFilter extends AbstractNominalToRandomOCFilter{

  /**
   * Generate a new partition at specified position even though it has already been generated
   * (it will be overwritten).
   *
   * It doesn't use the instances information (so it can be null).
   *
   * @param instances Perhaps a subclasss will use some information of them.
   * @param partitionNumber The number of the partition to generate.
   *
   * @throws Exception If partitionNumber is incorrect or another error occurs.
   */
  public void newPartition(Instances instances, int partitionNumber) throws Exception{

    if (partitionNumber > m_NumGeneratedPartitions || partitionNumber < 0)
      throw new Exception("Partition index must be between 0 and "
                          + String.valueOf(m_NumGeneratedPartitions + 1));

    for (int i = 0; i < m_OutputCode.length; i++)
      if (m_Random.nextBoolean())
        m_OutputCode[i].set(partitionNumber);
      else
        m_OutputCode[i].clear(partitionNumber);

    if (partitionNumber == m_NumGeneratedPartitions)
      m_NumGeneratedPartitions++;
  }

  /**
   * Generate a new partition incrementing the number of partitions generated,
   * and so incrementing by 1 the length of all code words. It's equivalent
   * to newPartition(instances, m_NumGeneratedPartitions), but faster.
   *
   * @param instances Perhaps a subclasss will use some information of them.
   */
  public void newPartition(Instances instances){

    for (int i = 0; i < m_OutputCode.length; i++)
      if (m_Random.nextBoolean())
        m_OutputCode[i].set(m_NumGeneratedPartitions);

    m_NumGeneratedPartitions++;
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

    super.setInputFormat(instanceInfo);

    m_OutputCode = new BitSet[instanceInfo.numDistinctValues(m_ProcessedAttribute)];

    for (int i = 0; i < m_OutputCode.length; i++)
      m_OutputCode[i] = new BitSet();

    if (m_Random == null)
      m_Random = new Random(m_Seed);

    newPartition(null);

    return true;
  }

  /**
   * Main method for testing this class.
   *
   * @param argv the options
   */
  public static void main(String[] argv){

    try{
      if (Utils.getFlag('b', argv)){
        Filter.batchFilterFile(new NominalToRandomOCFilter(), argv);
      }
      else{
        Filter.filterFile(new NominalToRandomOCFilter(), argv);
      }
    }
    catch (Exception ex){
      System.out.println(ex.getMessage());
    }
  }
}