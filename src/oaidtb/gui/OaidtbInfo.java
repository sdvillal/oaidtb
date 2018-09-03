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
 *    OaidtbInfo.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.gui;

import com.jrefinery.JCommon;
import com.jrefinery.chart.JFreeChart;
import com.jrefinery.ui.about.Contributor;
import com.jrefinery.ui.about.Library;
import com.jrefinery.ui.about.Licences;
import com.jrefinery.ui.about.ProjectInfo;

import java.util.Arrays;

/**
 * Clase que contiene la información del proyecto para mostrar en las ventanas
 * about (acerca de).
 *
 * Extiende una clase de la librería JCommon:
 * <a href="http://www.object-refinery.com">
 *  JCommon
 * </a><p>
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class OaidtbInfo extends ProjectInfo{

  public OaidtbInfo(){

    this.name = "O.A.I.D.T.B. (Otra Aplicación Interactiva Demostrando Técnicas de Boosting)";
    this.version = "1.1";
    this.info = "http://perso.wanadoo.es/sdvb";

    this.copyright = "(C)opyright 2002, by Santiago David Villaba Bartolomé";

    this.logo = AllContainerPanel.PROFX_ICON.getImage();

    this.licenceName = "GPL";
    this.licenceText = Licences.GPL;

    this.contributors = Arrays.asList(
      new Contributor[]{
        new Contributor("Santiago David Villalba Bartolomé", "sdvb@wanadoo.es")
      }
    );

    this.libraries = Arrays.asList(
      new Library[]{
        new Library("Weka", "3.3.3", "GPL", "http://www.cs.waikato.ac.nz/~ml/weka/"),
        new Library("Colt Distribution", "1.0.2", "Libre (ver la página web)", "http://tilde-hoschek.home.cern.ch/~hoschek/colt/"),
        new Library(JFreeChart.INFO),
        new Library(JCommon.INFO)
      }
    );
  }
}