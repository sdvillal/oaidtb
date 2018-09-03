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
 *    DrawingPanel.java
 *    Copyright (C) 2002 Santiago David Villalba
 *
 */

package oaidtb.gui;

import oaidtb.misc.mediator.Arbitrable;
import oaidtb.misc.mediator.Colleague;
import oaidtb.misc.mediator.Mediator;
import weka.core.FastVector;
import weka.core.Instance;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Iterator;

/**
 * Panel que permite representar un conjunto de puntos bidimensionales (instancias), así
 * como representar una imagen de hipótesis.
 *
 * <p> Siempre se mantiene sincronizado con la base de datos de puntos.
 *
 * <p> Utiliza una representación en tres niveles; se dibujan, de abajo a arriba:
 * <ol>
 * <li> La imagen de hipótesis
 * <li> La imagen de la rejilla utilizada para crear dicha imagen de hipótesis
 * <li> La imagen que representa las instancias
 * </ol>
 *
 *
 * @author <a href="mailto:sdvb@wanadoo.es">Santiago David Villalba Bartolom&eacute;</a>
 * @version $Revision: 1.1 $
 */
public class DrawingPanel extends JPanel implements Arbitrable{
  // It's fast!
  //TODO: Make it even faster (testImage etc., volatileImages con JDK 1.4 etc.)
  //TODO: Allow customized sprite instance representation
  //TODO: Limitar el tamaño mínimo que ha de tener una región de zoom (configurable)
  //TODO: Preparar para poder representar clases mediante sprites (ver DrawInstancesWithSprites en el paquete de pruebas)

  /** Las instancias que vamos a representar en este panel */
  private Point2DInstances m_Points;

  /**
   * Imagen que representa las instancias.
   * <p>
   * Pintar directamente en la pantalla es lento por 2 motivos:
   * <ol>
   *   <li> Tener que repintar cada vez que se repinta el panel todos los óvalos que representan las instancias
   *   <li> Pintar en la memoria gráfica directamente es más lento
   * </ol>
   * Por lo tanto, dibujamos las instancias en una imgen en memoria principal (con un color transparente de fondo)
   * y después la volcamos a la pantalla si es preciso
   */
  private BufferedImage m_InstancesImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
  /** La imagen de hipótesis */
  private BufferedImage m_HypothesisImage = null;
  /**
   * La imagen de la rejilla utilizada para pintar la hipotesis (podemos quitarla,
   * pues no es un proceso muy costoso el redibujarla)
   */
  private BufferedImage m_GridImage = null;

  /** Un color transparente */
  private final Color m_TransparentColor = new Color(0, 0, 0, 0);

  //What must be updated
  /** Bandera que indica si se debe repintar la imagen de instancias */
  private boolean m_UpdateInstances = true;
  /** Bandera que indica si se debe repintar la imagen de la rejilla */
  private boolean m_UpdateGrid = true;

  //What to show
  /** Bandera que indica si se deben mostrar o no las instancias de entrenamiento */
  private boolean m_ShowTrainInstances = true;
  /** Bandera que indica si se deben mostrar o no las instancias de prueba */
  private boolean m_ShowTestInstances = true;
  /** Bandera que indica si se debe mostrar o no la imagen de hipótesis */
  private boolean m_ShowHypothesisImage = true;
  /** Bandera que indica si se debe mostrar o no la imagen de la rejilla */
  private boolean m_ShowGrid = true;

  //Configuración de la rejilla
  // Para cada rectángulo (i) que lo forma:
  //           Esquina superior izquierda: (i * m_GridColumnWidth, i * m_GridRowHeight)
  //           Anchura: m_GridColumnWidth
  //           Altura: m_GridRowHeight
  /** La anchura de la rejilla en pixeles */
  private int m_GridWidth;
  /** La altura de la rejilla en pixeles */
  private int m_GridHeight;
  /** La anchura de cada columna en pixeles */
  private int m_GridColumnWidth;
  /** La altura de cada fila en pixeles */
  private int m_GridRowHeight;

  /** El tamaño de los círculos que representan las instancias */
  private int m_PointsRadius = 3;

  ///////////////
  //Zoom manage
  //////////////

  /** A listener to visually select a region in the panel */
  RegionSelector m_RegionSelector = new RegionSelector();

  /** If true, the panel is showing a region rather than be in a natural scale */
  private boolean m_IsZoomedIn = false;

  // The region we currently have made zoom over
  /** La coordenada x de la primera columna por la izquierda de la región (modo zoom)*/
  private double m_RegionX = 0;
  /** La coordenada y de la primera fila por arriba de la región (modo zoom)*/
  private double m_RegionY = 0;
  /** La anchura de la región (modo zoom)*/
  private double m_RegionWidth = -1;
  /** La altura de la región (modo zoom)*/
  private double m_RegionHeight = -1;

  /** The factors needed to translate and scale points from/to the region */
  private double m_xScaleFactor = 1, m_yScaleFactor = 1; //m_xScaleFactorMid, m_yScaleFactorMid;

  /**
   * Map the x coordinate of a point in a region to the x coordinate of the
   * same point as if the region is scaled and translated to the current drawing panel.
   *
   * Por ejemplo, x(región) = 3.5 ==> x(pantalla) = 9
   *
   * @param x An x coordinate of a point in the region
   * @return The corresponding x coordinate in the screen
   */
  public double getXInPanel(double x){
    x -= m_RegionX; //Translate
    return x * m_xScaleFactor; //Scale
  }

  /**
   * Map the y coordinate of a point in a region to the y coordinate of the
   * same point as if the region is scaled and translated to the current drawing panel.
   *
   * Por ejemplo, y(región) = 3.5 ==> y(pantalla) = 9
   *
   * @param y An y coordinate of a point in the region
   * @return The corresponding y coordinate in the screen
   */
  public double getYInPanel(double y){
    y -= m_RegionY; //Translate
    return y * m_yScaleFactor; //Scale
  }

  /**
   * Map the x coordinate of a point in the panel to the x coordinate of the
   * same point as if the panel is scaled and translated to the current region selected.
   *
   * Por ejemplo, x(pantalla) = 9 ==>  x(región) = 3.5
   *
   * @param x An x coordinate of a point in the screen
   * @return The corresponding x coordinate in the region
   */
  public double getXFromPanel(double x){
    return (x + m_RegionX * m_xScaleFactor) / m_xScaleFactor;
  }

  /**
   * Map the y coordinate of a point in the panel to the y coordinate of the
   * same point as if the panel is scaled and translated to the current region selected.
   *
   * Por ejemplo, y(pantalla) = 9 ==>  y(región) = 3.5
   *
   * @param y An y coordinate of a point in the screen
   * @return The corresponding y coordinate in the region
   */
  public double getYFromPanel(double y){
    return (y + m_RegionY * m_yScaleFactor) / m_yScaleFactor;
  }

  /**
   * Constructor
   *
   * @param pointsDataSet La base de datos de instancias que representaremos
   */
  public DrawingPanel(Point2DInstances pointsDataSet){

    m_Points = pointsDataSet;

    //Tenemos que controlar los cambios de tamaño del componente
    addComponentListener(new ComponentAdapter(){
      public void componentResized(ComponentEvent e){
        //La imagen que representa a las instancias siempre tendrá el máximo tamaño
        //que ha llegado a alcanzar el panel en la pantalla
        final int imgW = m_InstancesImage.getWidth();
        final int imgH = m_InstancesImage.getHeight();
        //Máxima anchura que se ha alcanzado hasta ahora
        final int w = Math.max(imgW, getWidth());
        //Máxima altura que se ha alcanzado hasta ahora
        final int h = Math.max(imgH, getHeight());
        //Si el nuevo tamaño del panel en cualquiera de las dos dimensiones
        //es mayor que cualquiera alcanzado hasta ahora
        if (w != imgW || h != imgH){
          //Aumentamos el tamaño de la imagen
          final BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
          final Graphics2D g = bi.createGraphics();
          //Rellenamos la imagen con un color transparente
          g.setColor(m_TransparentColor);
          g.fillRect(0, 0, w, h);
          //Pintamos la imagen anterior
          g.drawImage(m_InstancesImage, 0, 0, imgW, imgH, null);
          m_InstancesImage = bi;
          g.dispose();
        }
        if(m_IsZoomedIn){
          /*
          Teniemdo en mente la siguiente relación, que debe cumplirse siempre:
            m_xScaleFactor * m_regionWidth = getWidth()
          es necesario cambiar el tamaño de la región sobre la que se ha hecho zoom
          para preservar la lógica del programa (por ejemplo, para que la imagen de hipótesis
          recreada no salga "movida" o para que realmente aparezcan todas las
          instancias en la región)
          */
          m_RegionWidth = (double) getWidth() / m_xScaleFactor;
          m_RegionHeight = (double) getHeight() / m_yScaleFactor;
          m_UpdateInstances = true;
          repaint();  //Mete un "chasquido visual" bastante desagradable, pero no hay otra...
        }
      }
    });
  }

  /**
   * Levantar o bajar la bandera que indica si se deben redibujar las instancias
   *
   * @param updateInstances true si se debe redibujar la imagen de instancias
   */
  public void setUpdateInstances(boolean updateInstances){
    m_UpdateInstances = updateInstances;
  }

  /**
   * Levantar o bajar la bandera que indica si se debe redibujar la rejilla
   *
   * @param updateGrid true si se debe redibujar la imagen de la rejilla
   */
  public void setUpdateGrid(boolean updateGrid){
    m_UpdateGrid = updateGrid;
  }

  /** @return true si se están mostrando las instancias de test */
  public boolean getShowTestInstances(){
    return m_ShowTestInstances;
  }

  /**
   * Mostrar o no las instancias de test
   *
   * @param showTestInstances true si se deben mostrar las instancias de test
   */
  public void setShowTestInstances(boolean showTestInstances){
    m_ShowTestInstances = showTestInstances;
  }

  /** @return true si se están mostrando las instancias de entrenamiento */
  public boolean getShowTrainInstances(){
    return m_ShowTrainInstances;
  }

  /**
   * Mostrar o no las instancias de entrenamiento
   *
   * @param showTrainInstances true si se deben mostrar las instancias de entrenamiento
   */
  public void setShowTrainInstances(boolean showTrainInstances){
    m_ShowTrainInstances = showTrainInstances;
  }

  /** @return true si se está mostrando la imagen de hipótesis */
  public boolean getShowHypothesisImage(){
    return m_ShowHypothesisImage;
  }

  /**
   * Mostrar o no la imagen de hipótesis
   *
   * @param showHypothesisImage true si se debe mostrar la imagen de hipótesis
   */
  public void setShowHypothesisImage(boolean showHypothesisImage){
    m_ShowHypothesisImage = showHypothesisImage;
  }

  /** @return true si se está mostrando la imagen de la rejilla */
  public boolean getShowGrid(){
    return m_ShowGrid;
  }

  /**
   * Mostrar o no la imagen de la rejilla
   *
   * @param showGrid true si se debe mostrar la imagen de la rejilla
   */
  public void setShowGrid(boolean showGrid){
    m_ShowGrid = showGrid;
  }

  /** @return La base de datos de instancias que se está representando  */
  public Point2DInstances getPoints(){
    return m_Points;
  }

  /**
   * Configurar la base de datos de instancias que se representarán
   *
   * @param points La base de datos de instancias que se representarán
   */
  public void setPoints(Point2DInstances points){
    m_Points = points;
  }

  /** @return La imagen de la rejilla */
  public BufferedImage getGridImage(){
    return m_GridImage;
  }

  /**
   * Configurar la imajen de la rejilla
   *
   * @param gridImage La nueva imagen de la rejilla
   */
  public void setGridImage(BufferedImage gridImage){
    m_GridImage = gridImage;
  }

  /** @return La imagen de hipótesis */
  public BufferedImage getHypothesisImage(){
    return m_HypothesisImage;
  }

  /**
   * Configurar la imajen de hipótesis
   *
   * @param image La nueva imagen de hipótesis
   */
  public void setHypothesisImage(BufferedImage image){
    m_HypothesisImage = image;
  }

  /** @return El ancho de columna de la rejilla en pixeles */
  public int getGridColumnWidth(){
    return m_GridColumnWidth;
  }

  /**
   * Configurar el ancho de columna de la rejilla
   *
   * @param gridColumnWidth El nuevo ancho de columna de la rejilla en pixeles
   */
  public void setGridColumnWidth(int gridColumnWidth){
    m_GridColumnWidth = gridColumnWidth;
    m_UpdateGrid = true;
  }

  /** @return La altura de fila de la rejilla en pixeles */
  public int getGridRowHeight(){
    return m_GridRowHeight;
  }

  /**
   * Configurar la altura de fila de la rejilla
   *
   * @param gridRowHeight La nueva altura de fila de la rejilla en pixeles
   */
  public void setGridRowHeight(int gridRowHeight){
    m_GridRowHeight = gridRowHeight;
    m_UpdateGrid = true;
  }

  /** @return La altura de la rejilla en pixeles */
  public int getGridHeight(){
    return m_GridHeight;
  }

  /**
   * Configurar la altura de la rejilla
   *
   * @param gridHeight La nueva altura de la rejilla en pixeles
   */
  public void setGridHeight(int gridHeight){
    m_GridHeight = gridHeight;
    m_UpdateGrid = true;
  }

  /** @return La anchura de la rejilla en pixeles */
  public int getGridWidth(){
    return m_GridWidth;
  }

  /**
   * Configurar la anchura de la rejilla
   *
   * @param gridWidth La nueva anchura de la rejilla en pixeles
   */
  public void setGridWidth(int gridWidth){
    m_GridWidth = gridWidth;
    m_UpdateGrid = true;
  }

  /**
   * Set the point's ovals size.
   *
   * @param pointSize The size (radius) in pixels.
   */
  public void setPointSize(int pointSize){
    m_PointsRadius = pointSize;
    m_UpdateInstances = true;
  }

  /**
   * Get the point's ovals size.
   *
   * @return The size (radius) in pixels.
   */
  public int getPointSize(){
    return m_PointsRadius;
  }

  /**
   * Este es el método principal de la clase; en él se encuentra
   * toda la lógica de representación
   *
   * @param g Un objeto graphics en que se pintará el panel
   */
  public void paint(Graphics g){

    //Llamamos al método de la superclase, para luego pintar encima lo que sea necesario
    super.paint(g);

    //Pintamos la imagen de hipótesis (abajo del todo)
    if (m_ShowHypothesisImage && m_HypothesisImage != null)
      g.drawImage(m_HypothesisImage, 0, 0, m_HypothesisImage.getWidth(this), m_HypothesisImage.getHeight(this), this);

    //Pintamos la imajen de la rejilla (encima de la imagen de hipótesis)
    if (m_ShowGrid){
      //Si es necesario la recreamos
      if (m_UpdateGrid)
        drawGrid();
      if (m_GridImage != null)
        g.drawImage(m_GridImage, 0, 0, m_GridImage.getWidth(), m_GridImage.getHeight(), this);
    }

    //Si tenemos que repintar las instancias
    if (m_UpdateInstances){
      //Este puede ser un trabajo costoso, avisamos a los mediadores
      notifyMediators(INSTANCES_REPAINT_BEGIN, m_Points);
      if (m_IsZoomedIn)
        //Elegimos el método más adecuado (y rápido) para pintar las instancias
        if (!m_Points.isTrainAndTestGeneratedFromregion())
          drawRegionInstances(m_RegionX, m_RegionY, m_RegionWidth, m_RegionHeight);
        else
          drawRegionTrainAndTestInstances();
      else if (m_Points.existsTestSet())
        drawTrainAndTestInstances();
      else
        drawAllInstances();
      notifyMediators(INSTANCES_REPAINT_FINISH, m_Points);
      //Ya no tenemos que hacerlo
      m_UpdateInstances = false;
    }

    //Si procede, pintamos en la pantalla la imagen de instancias (encima del resto de imágenes)
    if ((m_ShowTrainInstances || m_ShowTestInstances && m_Points.existsTestSet()) && m_InstancesImage != null)
      g.drawImage(m_InstancesImage, 0, 0, m_InstancesImage.getWidth(this), m_InstancesImage.getHeight(this), this);

    //Pintamos el "rectángulo transparente" que representa la selección actual
    if (m_RegionSelector.existsSelection)
      m_RegionSelector.drawSelection((Graphics2D) g);
  }

  /** Dibujar la rejilla en la imagen de rejilla */
  private void drawGrid(){

    //No valid grid has been set up
    if (m_GridWidth <= 0 || m_GridHeight <= 0 || m_GridRowHeight <= 0 || m_GridColumnWidth <= 0){
      m_GridImage = null;
      m_UpdateGrid = false;
      return;
    }

    //Creamos la imagen
    m_GridImage = new BufferedImage(m_GridWidth, m_GridHeight, BufferedImage.TYPE_INT_ARGB);

    //La rellenamos con un color transparente
    Graphics2D g = m_GridImage.createGraphics();
    g.setColor(m_TransparentColor);
    g.fillRect(0, 0, m_GridWidth, m_GridHeight);

    //Pintamos las líneas de color negro
    g.setColor(Color.black);

    //Poner un stroke güeno

    //Vertical lines
    int coord = 0;
    while (coord < m_GridWidth){
      g.drawLine(coord, 0, coord, m_GridHeight);
      coord += m_GridColumnWidth;
    }

    //Horizontal lines
    coord = 0;
    while (coord < m_GridHeight){
      g.drawLine(0, coord, m_GridWidth, coord);
      coord += m_GridRowHeight;
    }

    //Ya la hemos actualizado
    m_UpdateGrid = false;
  }

  /**
   * Dibujar una nueva instancia
   *
   * @param x La coordenada x
   * @param y La coordenada y
   * @param color El color que representa la clase de la instancia
   */
  public void drawInstance(final int x, final int y, final Color color){
    //La dibujamos en la pantalla...
    Graphics2D g2 = (Graphics2D) getGraphics();
    g2.setColor(color);
    g2.fillOval(x, y, m_PointsRadius, m_PointsRadius);
    //...y en la imagen de instancias
    g2 = m_InstancesImage.createGraphics();
    g2.setColor(color);
    g2.fillOval(x, y, m_PointsRadius, m_PointsRadius);
    g2.dispose();
  }

  /**
   * Dibujar las últimas instancias añadidas a la base de datos en la imagen de
   * instancias; método pensado para dibujar de golpe y rápidamente un conjunto de instancias
   * del mismo color añadidas secuencialmente; no se llama a repaint(), así
   * que si se quiere actualizar también el contenido de la
   * pantalla se debe llamar a ese método externamente.
   *
   * @param num El número de instancias a pintar
   * @param color El color de dichas instancias
   */
  public void drawLastInstances(int num, final Color color){
    //Seleccionamos todas las instancias
    final FastVector vector = m_Points.getAllInstancesFastVector();
    final int numInstances = vector.size();
    //Las pintamos en la imagen de instancias
    Graphics2D g2 = m_InstancesImage.createGraphics();
    g2.setColor(color);
    while (num > 0){
      Instance instance = (Instance) vector.elementAt(numInstances - num);
      g2.setColor(color);
      g2.fillOval((int) instance.value(0), (int) instance.value(1), m_PointsRadius, m_PointsRadius);
      num--;
    }
    g2.dispose();
  }

  /**
   * Dibuja un conjunto de instancias de un determinado color en la imagen de hipótesis;
   * no se llama a repaint(), así que si se quiere actualizar también el contenido de la
   * pantalla se debe llamar a ese método externamente.
   *
   * @param instances Las instancias a pintar
   * @param color El color de dichas instancias
   */
  public void drawInstances(FastVector instances, final Color color){
    Graphics2D g2 = m_InstancesImage.createGraphics();
    g2.setColor(color);
    //Dibujamos todas las instancias en la imagen de instancias
    for (int i = 0; i < instances.size(); i++){
      Instance instance = (Instance) instances.elementAt(i);
      g2.setColor(color);
      g2.fillOval((int) instance.value(0), (int) instance.value(1), m_PointsRadius, m_PointsRadius);
    }
    g2.dispose();
  }

  /**
   * Borra una instancia de la imagen de instancias pintando encima un círculo
   * igual con color transparente. ¡Ojo!, puede borrar instancias que haya "debajo"
   * de la que estamos borrando, y no aparecerán hasta que repintemos la imagen
   * de instancias
   *
   * @param x La coordenada x de la instancia
   * @param y La coordenada y de la instancia
   */
  public void erasePoint(final double x, final double y){
    Graphics2D g2 = m_InstancesImage.createGraphics();
    g2.setComposite(AlphaComposite.Src);
    g2.setColor(m_TransparentColor);
    g2.fillOval((int) Math.round(x), (int) Math.round(y), m_PointsRadius, m_PointsRadius);
    g2.dispose();
    repaint();
  }

  /**
   * Pinta todas las intancias que existan en la base de datos en la imagen de instancias
   */
  private void drawAllInstances(){

    //Si no hay que pintarlas, salimos sin hacer nada
    if (!m_ShowTrainInstances)
      return;

    //Recogemos todas las instancias ordenadas por color
    Iterator iterator = m_Points.getAllInstancesIterator();

    Graphics2D g = m_InstancesImage.createGraphics();

    //"Limpiamos" la imagen de instancias
    g.setColor(m_TransparentColor);
    g.setComposite(AlphaComposite.Src);
    g.fillRect(0, 0, m_InstancesImage.getWidth(), m_InstancesImage.getHeight());

    //Nos aprovechamos de que están ordenadas por color
    int colorIndex = -1;
    while (iterator.hasNext()){
      colorIndex++;
      //Preguntamos por cuántas instancias hay de ese color
      int countDown = m_Points.getNumPointsOfColor(colorIndex);
      g.setColor(m_Points.getColor(colorIndex));
      while (countDown-- > 0){
        Point2DInstances.Point2DInstance p = (Point2DInstances.Point2DInstance) iterator.next();
        g.fillOval((int) p.getX(), (int) p.getY(), m_PointsRadius, m_PointsRadius);
      }
    }

    g.dispose();
  }

  /**
   * Pinta las instancias que pertenecen a la región representada por el panel,
   * haciendo las correspondientes transformaciones de coordenadas
   *
   * @param regionX La coordenada x de la esquina superior izquierda de la región
   * @param regionY La coordenada y de la esquina superior izquierda de la región
   * @param regionWidth La anchura de la región
   * @param regionHeight La altura de la región
   */
  private void drawRegionInstances(final double regionX, final double regionY,
                                   final double regionWidth, final double regionHeight){

    Graphics2D g = m_InstancesImage.createGraphics();
    //Limpiamos la imagen de instancias
    g.setColor(m_TransparentColor);
    g.setComposite(AlphaComposite.Src);
    g.fillRect(0, 0, m_InstancesImage.getWidth(), m_InstancesImage.getHeight());

    //Si tenemos que representar tanto el conjunto de entrenamiento como el de test...
    if (m_Points.existsTestSet() && m_ShowTestInstances){

      //Recuperamos las instancias de entrenamiento y de test
      final FastVector train = m_Points.getTrainInstances();
      final FastVector test = m_Points.getTestInstances();

      //Pintamos las instancias de entrenamiento
      if (m_ShowTrainInstances){
        for (int i = 0; i < train.size(); i++){
          Point2DInstances.Point2DInstance p = (Point2DInstances.Point2DInstance) train.elementAt(i);
          if (Point2DInstances.inRegion(p.getX(), p.getY(),
                                        regionX, regionY, regionWidth, regionHeight)){
            int colorIndex = (int) p.getColor();
            g.setColor(m_Points.getColor(colorIndex));
            //Hacemos la transformación de coordenadas correspondiente
            g.fillOval((int) Math.round(getXInPanel(p.getX())), (int) Math.round(getYInPanel(p.getY())),
                       m_PointsRadius, m_PointsRadius);
          }
        }
      }

      //Pintamos las instancias de test
      for (int i = 0; i < test.size(); i++){
        Point2DInstances.Point2DInstance p = (Point2DInstances.Point2DInstance) test.elementAt(i);
        if (Point2DInstances.inRegion(p.getX(), p.getY(),
                                      regionX, regionY, regionWidth, regionHeight)){
          int colorIndex = (int) p.getColor();
          g.setColor(m_Points.getColor(colorIndex));
          //Hacemos la transformación de coordenadas correspondiente
          g.drawOval((int) Math.round(getXInPanel(p.getX())), (int) Math.round(getYInPanel(p.getY())),
                     m_PointsRadius, m_PointsRadius);
        }
      }
    }
    else{ //Si tenemos que pintar todas las instancias en la región

      //Recuperamos todas las instancias que están en la región
      FastVector regionInstances = m_Points.getRegion(regionX, regionY, regionWidth, regionHeight);

      if (m_ShowTrainInstances)
        for (int i = 0; i < regionInstances.size(); i++){
          Point2DInstances.Point2DInstance p = (Point2DInstances.Point2DInstance) regionInstances.elementAt(i);
          int colorIndex = (int) p.getColor();
          g.setColor(m_Points.getColor(colorIndex));
          //Hacemos la transformación de coordenadas correspondiente
          g.fillOval((int) Math.round(getXInPanel(p.getX())), (int) Math.round(getYInPanel(p.getY())),
                     m_PointsRadius, m_PointsRadius);
        }
    }

    g.dispose();
  }

  /**
   * Dibuja las instancias de entrenamiento y de test en la imagen de instancias
   */
  private void drawTrainAndTestInstances(){

    //Recuperamos los conjuntos de entrenamiento y de test
    FastVector train = m_Points.getTrainInstances();
    FastVector test = m_Points.getTestInstances();

    Graphics2D g = m_InstancesImage.createGraphics();
    //"Limpiamos" la imagen de instancias
    g.setColor(m_TransparentColor);
    g.setComposite(AlphaComposite.Src);
    g.fillRect(0, 0, m_InstancesImage.getWidth(), m_InstancesImage.getHeight());

    //Dibujamos si procede las instancias de entrenamiento
    if (m_ShowTrainInstances)
      for (int i = 0; i < train.size(); i++){
        Point2DInstances.Point2DInstance p = (Point2DInstances.Point2DInstance) train.elementAt(i);
        int colorIndex = (int) p.getColor();
        g.setColor(m_Points.getColor(colorIndex));
        g.fillOval((int) p.getX(), (int) p.getY(), m_PointsRadius, m_PointsRadius);
      }

    //Dibujamos si procede las instancias de test
    if (m_ShowTestInstances)
      for (int i = 0; i < test.size(); i++){
        Point2DInstances.Point2DInstance p = (Point2DInstances.Point2DInstance) test.elementAt(i);
        int colorIndex = (int) p.getColor();
        g.setColor(m_Points.getColor(colorIndex));
        g.drawOval((int) p.getX(), (int) p.getY(), m_PointsRadius, m_PointsRadius);
      }

    g.dispose();
  }

  /**
   * Este método se aprovecha de que los conjuntos de entrenamiento y test que existen
   * en la base de datos hayan sido creados utilizando sólo las instancias
   * que pertenecen a la región actualmente representada por el panel.
   *
   * Hace las transformaciones de coordenadas necesarias-
   *
   * Sólo se diferencia de drawTrainAndTestInstances() en que transforma las coordenadas
   * de los puntos.
   */
  private void drawRegionTrainAndTestInstances(){

    //Recuperamos las instancias de entrenamiento y test
    FastVector train = m_Points.getTrainInstances();
    FastVector test = m_Points.getTestInstances();

    Graphics2D g = m_InstancesImage.createGraphics();
    //"Limpiamos" la imagen de instancias
    g.setColor(m_TransparentColor);
    g.setComposite(AlphaComposite.Src);
    g.fillRect(0, 0, m_InstancesImage.getWidth(), m_InstancesImage.getHeight());

    //Pintamos si es necesario las instancias de entrenamiento
    if (m_ShowTrainInstances)
      for (int i = 0; i < train.size(); i++){
        Point2DInstances.Point2DInstance p = (Point2DInstances.Point2DInstance) train.elementAt(i);
        int colorIndex = (int) p.getColor();
        g.setColor(m_Points.getColor(colorIndex));
        //Tenemos que trasladar las coordenadas reales del punto a las de la pantalla
        g.fillOval((int) Math.round(getXInPanel(p.getX())), (int) Math.round(getYInPanel(p.getY())),
                   m_PointsRadius, m_PointsRadius);
      }

    //Pintamos si es necesario las instancias de test
    if (m_ShowTestInstances)
      for (int i = 0; i < test.size(); i++){
        Point2DInstances.Point2DInstance p = (Point2DInstances.Point2DInstance) test.elementAt(i);
        int colorIndex = (int) p.getColor();
        g.setColor(m_Points.getColor(colorIndex));
        //Tenemos que trasladar las coordenadas reales del punto a las de la pantalla
        g.drawOval((int) Math.round(getXInPanel(p.getX())), (int) Math.round(getYInPanel(p.getY())),
                   m_PointsRadius, m_PointsRadius);
      }

    g.dispose();
  }


  /** Borra el "rectángulo transparente" que representa la región seleccionada */
  public void clearSelection(){
    m_RegionSelector.resetSelection();
  }

  /** Permite seleccionar gráficamente una región dentro del panel. */
  private class RegionSelector extends MouseAdapter implements MouseMotionListener{

    int origX,origY;
    //Siempre en coordenadas absolutas de la pantalla
    int regionX, regionY, regionWidth, regionHeight;
    Color selectionColor = Color.blue.darker();
    boolean existsSelection = false;

    //Los tamaños mínimos de una selección
    //TODO: Permitir que sea configurable
    double minWidth = 0.0002, minHeight = 0.0002;

    /** Recogemos las coordenada donde hemos pulsado el ratón */
    public void mousePressed(MouseEvent e){
      origX = e.getX();
      origY = e.getY();
    }

    /** Cancelar la selección */
    public void mouseClicked(MouseEvent e){
      if (existsSelection)
        resetSelection();
    }

    /**
     * Controlamos si hemos realizado una selección (arrastre del ratón con el botón izquierdo)
     *
     * @param e El evento del ratón
     */
    public void mouseReleased(MouseEvent e){
      if (SwingUtilities.isRightMouseButton(e)) //Canceled selection process
        resetSelection();
      else if (existsSelection){
        Rectangle2D.Double sel = getLastSelection();
        //Evitamos la selección de una región muy pequeña
        if (sel.width < minWidth || sel.height < minHeight)
          resetSelection();
        else
          notifyMediators(REGION_SELECTED, getLastSelection());
      }
    }

    /**
     * Actualizar la selección actual
     *
     * @param e El evento del ratón
     */
    public void mouseDragged(MouseEvent e){

      existsSelection = true;

      //No permitimos que la selección se "salga del panel"
      final int w = getWidth();
      final int x = e.getX();

      if(x < 0)
        regionWidth = 0;
      else if(x > w)
        regionWidth = w;
      else
        regionWidth = x;

      //No permitimos que la selección se "salga del panel"
      final int h = getHeight();
      final int y = e.getY();

      if(y < 0)
        regionHeight = 0;
      else if(y > h)
        regionHeight = h;
      else
        regionHeight = y;

      //Actualizamos los valores de la región seleccionada
      regionX = Math.min(origX, regionWidth);
      regionY = Math.min(origY, regionHeight);
      regionWidth = Math.abs(origX - regionWidth);
      regionHeight = Math.abs(origY - regionHeight);

      //Repintamos el "rectángulo transparente" que representa la selección
      repaint();
    }

    /** @return La última región seleccionada, en coordenadas del dominio del problema  */
    public Rectangle2D.Double getLastSelection(){
      if (!m_IsZoomedIn)
        return new Rectangle2D.Double(regionX, regionY, regionWidth, regionHeight);
      else{
        final double trueX = getXFromPanel(regionX);
        final double trueY = getYFromPanel(regionY);
        final double trueWidth = regionWidth / m_xScaleFactor;
        final double trueHeight = regionHeight / m_yScaleFactor;
        return new Rectangle2D.Double(trueX, trueY, trueWidth, trueHeight);
      }
    }


    public void mouseMoved(MouseEvent e){
    }

    /** Dibuja el "rectángulo transparente" que representa la región seleccionada */
    public void drawSelection(final Graphics2D g2){
      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2F));
      g2.setColor(selectionColor);
      g2.fillRect(regionX, regionY, regionWidth, regionHeight);
    }

    /** Borra el "rectángulo transparente" que representa la región seleccionada */
    private void resetSelection(){
      repaint(1, regionX, regionY, regionWidth, regionHeight);
      existsSelection = false;
      regionX = regionY = regionWidth = regionHeight = 0;
    }
  }

  /**
   * Añade los oyentes del ratón que permiten seleccionar visualmente una región del espacio representado
   * por el panel
   */
  public void enterSelectionMode(){
    addMouseListener(m_RegionSelector);
    addMouseMotionListener(m_RegionSelector);
  }

  /**
   * Elimina los oyentes del ratón que permiten seleccionar visualmente una región del espacio representado
   * por el panel
   */
  public void exitSelectionMode(){
    removeMouseListener(m_RegionSelector);
    removeMouseMotionListener(m_RegionSelector);
  }


  /**
   * Hace zoom sobre una región. Las coordenadas deben ser absolutas en relación al dominio del problema
   *
   * @param regionX La coordenada x de la esquina superior izquierda de la región
   * @param regionY La coordenada y de la esquina superior izquierda de la región
   * @param regionWidth La anchura de la región
   * @param regionHeight La altura de la región
   */
  public void zoomIn(final double regionX, final double regionY, final double regionWidth, final double regionHeight){

    m_RegionX = regionX;
    m_RegionY = regionY;
    m_RegionWidth = regionWidth;
    m_RegionHeight = regionHeight;
    m_xScaleFactor = (double) getWidth() / regionWidth;
    m_yScaleFactor = (double) getHeight() / regionHeight;

    m_IsZoomedIn = true;

    //Tenemos que redibujar
    m_UpdateInstances = true;

    notifyMediators(ZOOM_IN, new Rectangle2D.Double(regionX, regionY, regionWidth, regionHeight));
  }


  /**
   * Hace zoom out; resetea las variables que indican la región seleccionada
   */
  public void zoomOut(){

    if(!m_IsZoomedIn)
      return;

    m_RegionX = 0;
    m_RegionY = 0;
    m_RegionWidth = -1;
    m_RegionHeight = -1;
    m_xScaleFactor = 1;
    m_yScaleFactor = 1;

    m_IsZoomedIn = false;

    m_UpdateInstances = true;

    notifyMediators(ZOOM_OUT, new Rectangle2D.Double(m_RegionX, m_RegionY, m_RegionWidth, m_RegionHeight));
  }

  /** @return true si el panel está representando una región interior del dominio del problema, salvo que represente
   *          el problema a escala natural
   */
  public boolean isZoomedIn(){
    return m_IsZoomedIn;
  }

  /** @return La coordenada x de la esquina superior izquierda de la región representada */
  public double getRegionX(){
    return m_RegionX;
  }

  /** @return La coordenada y de la esquina superior izquierda de la región representada */
  public double getRegionY(){
    return m_RegionY;
  }

  /** @return La anchura de la región representada   */
  public double getRegionWidth(){
    return m_RegionWidth;
  }

  /** @return La altura de la región representada   */
  public double getRegionHeight(){
    return m_RegionHeight;
  }

  /** @return El factor de escalado horizontal (relación tamaño_del_panel / tamaño_de_la_región) */
  public double getXScaleFactor(){
    return m_xScaleFactor;
  }

  /** @return El factor de escalado vertical (relación tamaño_del_panel / tamaño_de_la_región) */
  public double getYScaleFactor(){
    return m_yScaleFactor;
  }

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

  // Change indicator constants
  /** Indicador de mensaje para los mediadores, acompañado de la base de datos de puntos */
  public final static int
    INSTANCES_REPAINT_BEGIN = 0,
    INSTANCES_REPAINT_FINISH = 1;
  /**
   * Indicador de mensaje para los mediadores,
   * acompañado de la región seleccionada, en coordenadas de pantalla,
   * almacenada en un {@link Rectangle2D.Double}
   */
  public final static int
    REGION_SELECTED = 20,
    REGION_DESELECTED = 21;
  /**
   * Indicador de mensaje para los mediadores,
   * acompañado de la región sobre la que se ha hecho/deshecho zoom,
   * en coordenadas del dominio del problema,
   * almacenada en un {@link Rectangle2D.Double}
   */
  public final static int
    ZOOM_IN = 30,
    ZOOM_OUT = 31;
}