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
 *    Colleague.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.misc.mediator;

/**
 * Una implementación estándar del interfaz {@link Arbitrable} de la que pueden extender aquellas
 * clases que se desea implementen esta interfaz y que no necesiten extender ninguna
 * otra clase; si la clase necesita extender alguna otra clase y no se
 * desea añadir ninguna funcionalidad especial en la comunicación con
 * los mediadores, se puede usar (copy & paste) la clase {@link SimpleColleagueTemplate},
 * que utiliza un objeto Colleague para implentar la interfaz {@link Arbitrable}.
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class Colleague implements Arbitrable{

  /** El array de mediadores */
  private Mediator[] m_Mediators = new Mediator[]{};
  /** La identidad del objeto que se pasará como fuente del mensaje al mediador */
  private Arbitrable m_ColleagueIdentity;

  /**
   * Constructor
   * @param colleagueIdentity la identidad del objeto que se pasará como fuente del mensaje al mediador
   */
  public Colleague(Arbitrable colleagueIdentity){
    m_ColleagueIdentity = colleagueIdentity;
  }

  /**
   * Constructor
   * Se pasará this como la identidad del objeto que se pasará como fuente del mensaje al mediador
   */
  public Colleague(){
    m_ColleagueIdentity = this;
  }

  /** @param mediator Un mediador al que se le pasarán los mensajes generados */
  public void addMediator(Mediator mediator){
    Mediator[] tmpArray = new Mediator[m_Mediators.length + 1];
    System.arraycopy(m_Mediators, 0, tmpArray, 0, m_Mediators.length);
    tmpArray[m_Mediators.length] = mediator;
    m_Mediators = tmpArray;
  }

  /** @param mediator Un mediador al que ya no queremos seguir mandando mensajes  */
  public void removeMediator(Mediator mediator){

    int mediatorIndex = -1;
    for (int i = 0; i < m_Mediators.length; i++){
      if (m_Mediators[i] == mediator){
        mediatorIndex = i;
        break;
      }
    }

    if(mediatorIndex == -1)
      return;

    Mediator[] tmpArray = new Mediator[m_Mediators.length - 1];
    for (int i = 0; i < m_Mediators.length; i++){
      if (m_Mediators[i] == mediator)
        continue;
      tmpArray[i] = m_Mediators[i];
    }
    m_Mediators = tmpArray;
  }

  /**
   * Notificar a todos los mediadores una acción realizada
   * por este colega.
   *
   * @param whatChanged Identificador del cambio
   * @param object Un objeto que identifique la acción o ayude al mediador a cumplir su función
   */
  public void notifyMediators(int whatChanged, Object object){
    for (int i = 0; i < m_Mediators.length; i++)
      m_Mediators[i].colleagueChanged(m_ColleagueIdentity, whatChanged, object);
  }

  /** @return El número de mediadores conocidos por este objeto {@link Arbitrable} */
  public int numberOfMediators(){
    return m_Mediators.length;
  }

  /** @return La identidad del objeto que será considerado como el emisor de los mensajes por los mediadores */
  public Arbitrable getColleague(){
    return m_ColleagueIdentity;
  }
}