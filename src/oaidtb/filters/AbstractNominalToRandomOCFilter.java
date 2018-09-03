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
 *    AbstractNominalToRandomOCFilter.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.filters;

import weka.core.Option;
import weka.core.Utils;
import weka.core.Instances;

import java.util.Enumeration;
import java.util.Random;
import java.util.Vector;

/**
 * Abstract class defining the structure and common code to "nominal to output code"
 * filters which exhibit randomness in its behaviour (ie. they use a random number generator
 * when constructing new partitions).
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 * @see oaidtb.filters.AbstractNominalToOCFilter
 */
public abstract class AbstractNominalToRandomOCFilter extends AbstractNominalToOCFilter{

  /** The random number generator used to create each partition. */
  protected Random m_Random = new Random();

  /** The m_Random's seed, unless it's a clock based seed. */
  protected long m_Seed = 0;

  /**
   * Get the random number generator used to get the random behavior of the filter.
   *
   * @return The m_Random number generator.
   */
  public Random getRandom(){
    return m_Random;
  }

  /**
   * Set the random number generator to be used as the basis for partition generation.
   *
   * @param random The random number generator.
   */
  public void setRandom(Random random){
    m_Random = random;
  }

  /**
   * Set the seed used by the random number generator.
   *
   * @param seed The seed.
   */
  public void setSeed(long seed){
    m_Seed = seed;
    m_Random = new Random(m_Seed); //Use m_Random.setResampleSeed
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

    //It must give the same results when running twice over the same dataset
    if(super.setInputFormat(instanceInfo)){
      m_Random = new Random(m_Seed);
      return true;
    }

    return false;
  }

  /**
   * Get the seed used by the random number generator.
   *
   * @return The seed.
   */
  public long getSeed(){
    return m_Seed;
  }

  /**
   * Parses a given list of options controlling the behaviour of this object.
   * Valid options are:<p>
   *
   * -R seed<br>
   * Random number seed for the filter. Default is 0. If you call it with a -R 0, it will
   * use a clock based seed.
   * <p>
   *
   * @param options the list of options as an array of strings
   * @exception Exception if an option is not supported
   */
  public void setOptions(String[] options) throws Exception{

    super.setOptions(options);

    String seedString = Utils.getOption('R', options);
    if (seedString.length() != 0){
      long seed = Long.parseLong(seedString);
      if (seed != 0)
        setSeed(seed);
      else
        setRandom(new Random()); //Clock based.
    }
    else
      setSeed(0);
  }

  /**
   * Gets the current settings of the filter.
   *
   * @return an array of strings suitable for passing to setOptions
   */
  public String[] getOptions(){

    String[] otherOptions = super.getOptions();

    String[] options = new String[otherOptions.length + 2];

    int current = 0;

    options[current++] = "-R";
    options[current++] = "" + getSeed();

    System.arraycopy(otherOptions, 0,
                     options, current,
                     otherOptions.length);

    current += otherOptions.length;
    while (current < options.length){
      options[current++] = "";
    }

    return options;
  }

  /**
   * Returns an enumeration describing the available options
   *
   * @return an enumeration of all the available options
   */
  public Enumeration listOptions(){

    Vector newVector = new Vector(2);

    newVector.addElement(new Option(
      "\tRandom number seed for the filter.\n",
      "R", 1, "-R <class name>"));

    Enumeration enum = super.listOptions();
    while (enum.hasMoreElements()){
      newVector.addElement(enum.nextElement());
    }

    return newVector.elements();
  }
}