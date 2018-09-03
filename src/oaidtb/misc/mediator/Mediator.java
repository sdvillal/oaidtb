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
 *    Mediator.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.misc.mediator;

/**
 * Interfaz que hace de mediador entre distintos objetos {@link Arbitrable}, definiendo
 * la manera (formato de los mensajes) en que se comunica con ellos.
 *
 * <p><p>
 * Para más información sobre el patrón de diseño Mediator, visitar la página:
 *  <a href="http://my.execpc.com/~gopalan/design/behavioral/mediator/mediator.html">
 *  http://my.execpc.com/~gopalan/design/behavioral/mediator/mediator.html
 * </a>
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public interface Mediator{

  /**
   * Indicar a este mediador que un objeto {@link Arbitrable} ha cambiado (ha realizado alguna
   * acción) para que haga las gestiones pertinentes.
   *
   * @param colleague La identidad del objeto que ha producido el mensaje
   * @param changeIndicator Un indicador del tiupo de acción/cambio realizado
   * @param object Cualquier objeto que el mediador pueda necesitar;
   *               también puede hacer la función de changeIndicator
   */
  void colleagueChanged(Arbitrable colleague,final int changeIndicator, final Object object);
}