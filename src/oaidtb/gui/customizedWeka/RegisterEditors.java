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
 *    RegisterEditors.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.gui.customizedWeka;

/**
 * Clase que sirve para registrar los editores de propiedades necesarios para las
 * nuevas clases que queramos especificar que las trate el GOE;
 * para no tener que estar trasteando con los fuentes del explorador de weka etc.
 *
 * Se debe poner bien en el directorio desde donde se ejecuta como una clase sin paquete,
 * bien en el paquete al que pertenezca la clase "GenericObjectEditor" en cuestión
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class RegisterEditors{

  /* Register the property editors we need */
  static{
    java.beans.PropertyEditorManager.registerEditor(weka.core.SelectedTag.class,
                                                    weka.gui.SelectedTagEditor.class);
    java.beans.PropertyEditorManager.registerEditor(weka.filters.Filter.class,
                                                    oaidtb.gui.customizedWeka.GenericObjectEditor.class);
    java.beans.PropertyEditorManager.registerEditor(weka.classifiers.Classifier[].class,
                                                    oaidtb.gui.customizedWeka.GenericArrayEditor.class);
    java.beans.PropertyEditorManager.registerEditor(weka.classifiers.Classifier.class,
                                                    oaidtb.gui.customizedWeka.GenericObjectEditor.class);
    java.beans.PropertyEditorManager.registerEditor(weka.classifiers.DistributionClassifier.class,
                                                    oaidtb.gui.customizedWeka.GenericObjectEditor.class);
    java.beans.PropertyEditorManager.registerEditor(oaidtb.boosters.Booster.class,
                                                    oaidtb.gui.customizedWeka.GenericObjectEditor.class);
    java.beans.PropertyEditorManager.registerEditor(oaidtb.boosters.costSensitive.AbstractCSB.class,
                                                    oaidtb.gui.customizedWeka.GenericObjectEditor.class);
    java.beans.PropertyEditorManager.registerEditor(oaidtb.filters.AbstractNominalToOCFilter.class,
                                                    oaidtb.gui.customizedWeka.GenericObjectEditor.class);
    java.beans.PropertyEditorManager.registerEditor(weka.classifiers.CostMatrix.class,
                                                    weka.gui.CostMatrixEditor.class);
  }
}