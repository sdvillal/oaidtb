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
 *    SimpleColleagueTemplate.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.misc.mediator;

/**
 * Colleague template. To be used (copy & paste) when direct Colleague inheritance isn't available
 * (no multiple inheritance is allowed in Java).
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class SimpleColleagueTemplate implements Arbitrable{

  //-----------------------------///////////////////////////////////
  //------------------ ARBITRABLE IMPLEMENTATION ///////////////////
  //-----------------------------///////////////////////////////////

  // Private field which will be used to implement the Arbitable interface.
  private Colleague m_Colleague = new Colleague(this);

  public Arbitrable getColleague(){
    return m_Colleague;
  }

  public void addMediator(Mediator mediator){
    m_Colleague.addMediator(mediator);
  }

  public void removeMediator(Mediator mediator){
    m_Colleague.removeMediator(mediator);
  }

  public void notifyMediators(int whatChanged, Object object){
    m_Colleague.notifyMediators(whatChanged, object);
  }

  public int numberOfMediators(){
    return m_Colleague.numberOfMediators();
  }
}