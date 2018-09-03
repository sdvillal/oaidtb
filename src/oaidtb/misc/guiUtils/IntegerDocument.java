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
 *    IntegerDocument.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.misc.guiUtils;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;

/**
 * Clase que, añadida a un componente de texto con el método setDocument() hace que
 * no se permita la inserción de texto que no represente a un entero
 * comprendido entre los valores máximo y mínimo especificados
 * (por defecto, permite la inclusión de todos los enteros); por supuesto, también
 * permite la inserción del símbolo menos.
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class IntegerDocument extends PlainDocument{

  private int maxValue = Integer.MAX_VALUE;
  private int minValue = Integer.MIN_VALUE;

  public int getMaxValue(){
    return maxValue;
  }

  public void setMaxValue(int maxValue){
    this.maxValue = maxValue;
  }

  public int getMinValue(){
    return minValue;
  }

  public void setMinValue(int minValue){
    this.minValue = minValue;
  }

  public void remove(int offs, int len) throws BadLocationException{
    String tmp = getText(0, getLength()).substring(offs, offs + len);
    super.remove(offs, len);
    String text = getText(0, getLength());
    if (text.length() == 0)
      insertString(offs, tmp, null);
    else{
      int intValue = Integer.parseInt(text);
      if (intValue < minValue || intValue > maxValue)
        insertString(offs, tmp, null);
    }
  }

  public void insertString(int offset, String string, AttributeSet attributes) throws BadLocationException{

    if (string == null || "-".equals(string) && minValue >= 0){
      Toolkit.getDefaultToolkit().beep();
      return;
    }

    int length = getLength();

    String newValue;

    if (length == 0){
      if ("-".equals(string)){
        super.insertString(offset, string, attributes);
        return;
      }
      newValue = string;
    }
    else{
      String currentContent = getText(0, length);
      StringBuffer currentBuffer = new StringBuffer(currentContent);
      currentBuffer.insert(offset, string);
      newValue = currentBuffer.toString();
    }

    if(newValue.length() == 0)
      return;

    try{
      int intValue = Integer.parseInt(newValue);
      //TODO: Cambiarlo, pues bajo ciertas circunstancias puede producir q el documento no pueda ser editado
      if (intValue > maxValue || intValue < minValue)
        throw new NumberFormatException("Invalid value: limits violated.");
      super.insertString(offset, string, attributes);
    }
    catch (NumberFormatException exception){
      Toolkit.getDefaultToolkit().beep();
    }
  }
}