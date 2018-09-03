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
 *    GridDimensionOptionsPanel.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.gui;

import oaidtb.misc.guiUtils.LabeledSlider;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

//Precondiciones: originales >0

/**
 * Panel de configuración de la rejilla que se utilizará para crear la imagen de hipótesis
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.0 $
 */
public class GridDimensionOptionsPanel extends JPanel{

  private double m_XYDimensionsOriginalRatio = 1;
  /** Para elegir el número de columnas de la rejilla */
  private LabeledSlider m_XSlider = new LabeledSlider("Grid Columns: ");
  /** Para elegir el número de filas de la rejilla */
  private LabeledSlider m_YSlider = new LabeledSlider("Grid Rows: ");

  /** La relación original entre la altura y la anchura del panel de dibujado */
    /** Se debe mantener la relación de aspecto original? */
  private JCheckBox m_MaintainAspectRatio = new JCheckBox("Maintain aspect ratio", true);

  /** @return El número de columnas de la rejilla */
  public int getColumns(){
    return m_XSlider.getSlider().getValue();
  }

  /** @return El número de filas de la rejilla  */
  public int getRows(){
    return m_YSlider.getSlider().getValue();
  }

  /**
   * Actualizar la anchura asignada como la original del componente a ser dividido por la rejilla
   *
   * @param xSize La anchura del componente
   */
  public void setOriginalXDimensionSize(int xSize){
    //throw an Exception
    if (xSize > 0){
      m_XSlider.getSlider().setMaximum(xSize);
      updateXYOriginalRatio();
    }
  }

  /**
   * Actualizar la altura asignada como la original del componente a ser dividido por la rejilla
   *
   * @param ySize La anchura del componente
   */
  public void setOriginalYDimensionSize(int ySize){
    //throw an Exception
    if (ySize > 0){
      m_YSlider.getSlider().setMaximum(ySize);
      updateXYOriginalRatio();
    }
  }

  /**
   * Actualizar la relación entre altura y anchura
   */
  private void updateXYOriginalRatio(){
    m_XYDimensionsOriginalRatio = (double) m_XSlider.getSlider().getMaximum() /
                                  (double) m_YSlider.getSlider().getMaximum();
    if(m_MaintainAspectRatio.isSelected())
      m_YSlider.getSlider().setValue((int)(m_XSlider.getSlider().getValue()/m_XYDimensionsOriginalRatio));
  }

  /**
   * Constructor
   */
  public GridDimensionOptionsPanel(){

    // Listener para mantener la relación de aspecto cuando se cambia el número de columnas
    final ChangeListener maintainRatioListener = new ChangeListener(){
      public void stateChanged(ChangeEvent e){
        m_YSlider.getSlider().setValue((int) Math.round(m_XSlider.getSlider().getValue() / m_XYDimensionsOriginalRatio));
      }
    };

    //Valores por defecto, para que no pete
    m_XSlider.getSlider().setMinimum(1);
    m_XSlider.getSlider().addChangeListener(maintainRatioListener);
    m_XSlider.getSlider().setValue(30);

    m_YSlider.getSlider().setMinimum(1);
    m_YSlider.getSlider().setEnabled(false);

    //Checkbox para elegir entre mantener o no la relación de aspecto
    m_MaintainAspectRatio.addItemListener(new ItemListener(){
      public void itemStateChanged(ItemEvent e){
        if (m_MaintainAspectRatio.isSelected()){
          m_XSlider.getSlider().addChangeListener(maintainRatioListener);
          m_YSlider.getSlider().setEnabled(false);
        }
        else{
          m_XSlider.getSlider().removeChangeListener(maintainRatioListener);
          m_YSlider.getSlider().setEnabled(true);
        }
      }
    });
    m_MaintainAspectRatio.setToolTipText("Mantener la relación de aspecto X/Y del panel de dibujo");

    //Será un BorderLayout
    setLayout(new BorderLayout());

    //Añadimos los componentes al panel
    JPanel sizeEditorsPanel = new JPanel(new GridLayout(0, 1));
    sizeEditorsPanel.add(m_XSlider.getLabel());
    sizeEditorsPanel.add(m_XSlider.getSlider());
    sizeEditorsPanel.add(m_YSlider.getLabel());
    sizeEditorsPanel.add(m_YSlider.getSlider());

    add(m_MaintainAspectRatio, BorderLayout.SOUTH);
    add(sizeEditorsPanel, BorderLayout.CENTER);

    //Creamos el borde por defecto
    setBorder(BorderFactory.createCompoundBorder(
		 BorderFactory.createTitledBorder("Grid setup"),
		 BorderFactory.createEmptyBorder(0, 5, 5, 5)
		 ));
  }
}