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
 *    NominalToRandomPermutationOfEvenSplitOCFilter.java
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
 * A nominal to output code filter; it splits the values of the attribute processed by
 * generating a random permutation of a partition in which a half of the values are in
 * one set and the other half is in the other set.
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 * @see oaidtb.filters.AbstractNominalToOCFilter
 * @see oaidtb.filters.AbstractNominalToRandomOCFilter
 */
public class NominalToRandomPermutationOfEvenSplitOCFilter extends AbstractNominalToRandomOCFilter{

  /** A partition with half of the values in each set. */
  private BitSet m_PartitionToShuffle;


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

    //Generate the random permutation by shuffling.
    for (int i = 0; i < m_OutputCode.length; i++)
      swapBits(i, m_Random.nextInt(m_OutputCode.length), m_PartitionToShuffle);

    setPartition(partitionNumber, m_PartitionToShuffle);

    //Increment de index of the "last partition".
    if (partitionNumber == m_NumGeneratedPartitions)
      m_NumGeneratedPartitions++;
  }

  /**
   * Generate a new partition incrementing the number of partitions generated,
   * and so incrementing by 1 the length of all code words. It's equivalent
   * to newPartition(instances, m_NumGeneratedPartitions), but faster.
   *
   * @param instances Perhaps a subclasss will use some information of them.
   *
   * @throws Exception If an error occurs.
   */
  public void newPartition(Instances instances) throws Exception{

    //Generate the random permutation by shuffling.
    for (int i = 0; i < m_OutputCode.length; i++)
      swapBits(i, m_Random.nextInt(m_OutputCode.length), m_PartitionToShuffle);

    setPartition(m_NumGeneratedPartitions, m_PartitionToShuffle);

    //Increment de index of the "last partition".
    m_NumGeneratedPartitions++;
  }

  /**
   * Swaps the bits a & b in the bitset.
   *
   * @param a Index of one bit
   * @param b Index of other bit
   * @param bitset The BitSet where the bits will be swapped.
   */
  private void swapBits(int a, int b, BitSet bitset){

    boolean tmp = bitset.get(a);

    if (bitset.get(b))
      bitset.set(a);
    else
      bitset.clear(a);

    if (tmp)
      bitset.set(b);
    else
      bitset.clear(b);
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

    super.setInputFormat(instanceInfo);

    //Initialize the data structure which will hold the codes.
    m_OutputCode = new BitSet[instanceInfo.numDistinctValues(m_ProcessedAttribute)];

    for (i = 0; i < m_OutputCode.length; i++)
      m_OutputCode[i] = new BitSet();

    if (m_Random == null)
      m_Random = new Random(m_Seed);

    m_PartitionToShuffle = new BitSet();

    for (i = 0; i < m_OutputCode.length / 2; i++)
      m_PartitionToShuffle.set(i);

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
        Filter.batchFilterFile(new NominalToRandomPermutationOfEvenSplitOCFilter(), argv);
      }
      else{
        Filter.filterFile(new NominalToRandomPermutationOfEvenSplitOCFilter(), argv);
      }
    }
    catch (Exception ex){
      System.out.println(ex.getMessage());
    }
  }
}