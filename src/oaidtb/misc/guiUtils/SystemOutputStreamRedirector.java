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
 *    SystemOutputStreamRedirector.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 *  Lista de cambios:
 *  - v1.1--> Se manda a dormir al thread tras comprobar la existencia de y/o redireccionar mnsajes
 *            un tiempo configurable, que antes se me calentaba mucho el aparato (sin dobles intenciones;)
 */

package oaidtb.misc.guiUtils;

import javax.swing.*;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.StyledDocument;
import java.io.*;

/**
 * Clase que implementa un thread que redirige uno de los streams de salida estándar
 * (System.out o System err) a un JEditorPane, permitiendo especificar
 * el formato de salida y si la salida también debe ser redirigida al
 * stream original (que no tiene por qué ser el original del sistema).
 *
 * Cuando un thread de esta clase es interrumpido, retorna el sistema a su estado original.
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.1 $
 */
public final class SystemOutputStreamRedirector extends Thread{

  /** El panel al que es redirigida la salida del stream */
  private final JEditorPane redirectedTo;

  /** El {@link javax.swing.text.Document} del panel de texto, que no debe ser cambiado */
  private final StyledDocument doc;

  /** Los atributos del texto (color, fuente...) para los mensajes redirigidos */
  private MutableAttributeSet textAtts;

  /** El stream al que, en el momento de llamar al constructor, es enviada la información del stream */
  private final PrintStream originalStream;

  /** Hacer o no un fork con {@link #originalStream} */
  private boolean isFork;

  /** Redirigimos System.err o System.out? */
  private final boolean isSystemErr;

  /**
   * Cada cuánto tiempo debe comprobar el thread la existencia o no de mensajes
   * Se debe buscar una solución de compromiso entre la velocidad de respuesta y el tiempo de proceso que se quiere
   * utilizar para esta tarea
   */
  private final int TIME_TO_SLEEP = 300;

  /** @return el stream para la salida de error estándar de la plataforma en que nos encontremos */
  public final static PrintStream getDefaultSystemErr(){
    //return new PrintStream(new BufferedOutputStream(new FileOutputStream(FileDescriptor.err),128));
    return new PrintStream(new FileOutputStream(FileDescriptor.err), true);
  }

  /** @return el stream para la salida estándar de la plataforma en que nos encontremos */
  public final static PrintStream getDefaultSystemOut(){
    //return new PrintStream(new BufferedOutputStream(new FileOutputStream(FileDescriptor.out),128));
    return new PrintStream(new FileOutputStream(FileDescriptor.out), true);
  }

  /**
   * Constructor
   *
   * @param area El área de texto a la que redirigiremos los mensajes
   * @param atts los atributos de testo de los mensajes redirigidos
   * @param isSystemErr Redirigimos System.err o System.out?
   * @param isFork Hacemos un fork con el stream ya existente?
   */
  public SystemOutputStreamRedirector(JEditorPane area, MutableAttributeSet atts, boolean isSystemErr, boolean isFork){

    this.isSystemErr = isSystemErr;

    if (isSystemErr)
      originalStream = System.err;
    else
      originalStream = System.out;

    redirectedTo = area;
    doc = (StyledDocument) area.getDocument();
    textAtts = atts;
    this.isFork = isFork;

    //Default thread settings
    setDaemon(true);
    setPriority(Thread.MIN_PRIORITY);
  }

  /** @return Si se está o no haciendo fork con el stream original */
  public boolean isFork(){
    return isFork;
  }

  /** @param fork Hacer o no fork con el stream original*/
  public void setFork(boolean fork){
    isFork = fork;
  }

  /** @return Los atributos de texto que se están aplicando a los mensajes dirigidos al área especificada */
  public MutableAttributeSet getTextAtts(){
    return textAtts;
  }

  /** @param textAtts Los atributos de texto que se aplicarán a los mensajes dirigidos al área especificada */
  public void setTextAtts(MutableAttributeSet textAtts){
    this.textAtts = textAtts;
  }

  /**
   * Deja el sistema en el estado que se encontraba antes de llamar a {@link #run}
   */
  public void restoreOriginal(){
    if (isSystemErr)
      System.setErr(originalStream);
    else
      System.setOut(originalStream);
  }

  /**
   * El método que "escucha" la llegada de nuevos mensajes y los dirige
   * al área especificada y, si se está haciendo fork, también
   * al stream original
   *
   * @param pi El stream que recoge los mensajes
   */
  private void redirect(final PipedInputStream pi){

    final byte[] buf = new byte[1024];
    try{
      //Mientras no interrumpamos el Thread
      while (!interrupted()){
        //Mientras exista texto por procesar
        while (pi.available() != 0){

          final int len = pi.read(buf);
          final String tmp = new String(buf, 0, len);

          //Lo mandamos a donde corresponda
          doc.insertString(doc.getLength(), tmp, textAtts);
          if (isFork)
            originalStream.println(tmp);

          //Hacemos que la última línea siempre sea visible
          redirectedTo.setCaretPosition(redirectedTo.getDocument().getLength() - 1);
        }
//        System.out.println("A dormir");
        sleep(TIME_TO_SLEEP);
      }
    }
    catch(InterruptedException ex){
//      Thread.currentThread().interrupt();
    }
    catch (Exception e){
      System.err.println(e.toString());
      e.printStackTrace();
    }
    finally{
      restoreOriginal();
    }
  }

  /** Pos eso, el método run del thread */
  public void run(){

    final PipedInputStream pi;
    final PipedOutputStream po;

    try{
      pi = new PipedInputStream();
      po = new PipedOutputStream(pi);
    }
    catch (IOException ex){
      System.err.println(ex.toString());
      return;
    }

    //Redirigimos la salida que corresponda a po...
    if (isSystemErr)
      System.setErr(new PrintStream(po, true));
    else
      System.setOut(new PrintStream(po, true));

    //...que a su vez está concetado con pi
    redirect(pi);
  }
}