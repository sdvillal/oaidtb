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
 *    Arbitrable.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.misc.mediator;

/**
 * Interfaz para las clases que hacen el papel de "colegas" usando esta implementaci�n
 * del patr�n de dis�o Mediator.
 *
 * <p><p>
 * Para m�s informaci�n sobre el patr�n de dise�o Mediator, visitar la p�gina:
 *  <a href="http://my.execpc.com/~gopalan/design/behavioral/mediator/mediator.html">
 * http://my.execpc.com/~gopalan/design/behavioral/mediator/mediator.html
 * </a>
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public interface Arbitrable{

  /** @return La identidad del objeto que ser� considerado como el emisor de los mensajes por los mediadores */
    Arbitrable getColleague();

  /** @param mediator Un mediador al que se le pasar�n los mensajes generados */
  void addMediator(Mediator mediator);

  /** @param mediator Un mediador al que ya no queremos seguir mandando mensajes  */
  void removeMediator(Mediator mediator);

  /** @return El n�mero de mediadores conocidos por este objeto {@link Arbitrable} */
  int numberOfMediators();

  /**
   * Notificar a todos los mediadores una acci�n realizada
   * por este colega
   *
   * @param whatChanged Identificador del cambio
   * @param object Un objeto que identifique la acci�n o ayude al mediador a cumplir su funci�n
   */
  void notifyMediators(final int whatChanged, final Object object);
}