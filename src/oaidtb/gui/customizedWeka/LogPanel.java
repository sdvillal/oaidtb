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
 *    LogPanel.java
 *    Copyright (C) 1999 Len Trigg
 *    Modified 2002 by Santiago David Villaba
 *
 *  Customized Weka changes-->
 *    ** The text component to show the log messages is now a JTextPane instead of a JTextArea, allowing
 *       further text formating capabilities
 *    ** The JTextPane isn't able to receive (and swallow) the focus
 *    ** The popup menu now is opned when right-clicking over the log test area panel, and
 *       two new items ("clear all" and "copy all to the clipboard") were added
 *    ** Añadida la posiblilidad de colocar un componente no predefinido a la izquierda de
 *       la barra de estado
 *
 */


package oaidtb.gui.customizedWeka;

import weka.gui.Logger;
import weka.gui.TaskLogger;
import weka.gui.WekaTaskMonitor;

import javax.swing.*;
import javax.swing.text.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * This panel allows log and status messages to be posted. Log messages
 * appear in a scrollable text area, and status messages appear as one-line
 * transient messages.
 *
 * @author Len Trigg (trigg@cs.waikato.ac.nz)
 * @author Santiago David Villalba (sdvb@wanadoo.es)
 * @version $Revision: 1.11 $
 */
public class LogPanel extends JPanel implements Logger, TaskLogger{

  /** Displays the current status */
  protected JLabel m_StatusLab = new JLabel("OK");

  protected JPanel m_CustomPanel = new JPanel(new BorderLayout());

  /** Displays the log messages */
  private JTextPane m_LogText = new JTextPane(){
    /**
     * Evitar que se trague el foco; no lo evita si pinchamos sobre ella, mejor
     * especificar cuál es el siguiente compoenete que recibirá el foco ( o mandarlo
     * a cycleRoot); este problema está solucionado a partir del JDK 1.4.0_01
     *
     * @return false
     */
    public boolean isFocusTraversable(){
      return false;
    }
  };

  /** The panel for monitoring the number of running tasks (if supplied)*/
  protected WekaTaskMonitor m_TaskMonitor = null;

  /**
   * Creates the log panel
   */
  public LogPanel(){
    m_LogText.setEditable(false);
    m_LogText.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    m_StatusLab.setBorder(BorderFactory.createCompoundBorder(
      BorderFactory.createTitledBorder("Status"),
      BorderFactory.createEmptyBorder(0, 5, 5, 5)));
    JPanel p1 = new JPanel();
    p1.setBorder(BorderFactory.createTitledBorder("Log"));
    p1.setLayout(new BorderLayout());
    final JScrollPane js = new JScrollPane(m_LogText);
    p1.add(js, BorderLayout.CENTER);
    js.getViewport().addChangeListener(new ChangeListener(){
      private int lastHeight;

      public void stateChanged(ChangeEvent e){
        JViewport vp = (JViewport) e.getSource();
        int h = vp.getViewSize().height;
        if (h != lastHeight){ // i.e. an addition not just a user scrolling
          lastHeight = h;
          int x = h - vp.getExtentSize().height;
          vp.setViewPosition(new Point(0, x));
        }
      }
    });
    setLayout(new BorderLayout());
    add(p1, BorderLayout.CENTER);
    JPanel p2 = new JPanel(new BorderLayout());
    p2.add(m_CustomPanel, BorderLayout.WEST);
    p2.add(m_StatusLab, BorderLayout.CENTER);
    add(p2,BorderLayout.SOUTH);
    addPopups();
  }

  /**
   * Creates the log panel
   */
  public LogPanel(WekaTaskMonitor tm){
    /*    if (!(tm instanceof java.awt.Component)) {
      throw new Exception("TaskLogger must be a graphical component");
      } */
    m_TaskMonitor = tm;
    m_LogText.setEditable(false);
    m_LogText.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    m_StatusLab.setBorder(BorderFactory.createCompoundBorder(
      BorderFactory.createTitledBorder("Status"),
      BorderFactory.createEmptyBorder(0, 5, 5, 5)));
    JPanel p1 = new JPanel();
    p1.setBorder(BorderFactory.createTitledBorder("Log"));
    p1.setLayout(new BorderLayout());
    final JScrollPane js = new JScrollPane(m_LogText);
    p1.add(js, BorderLayout.CENTER);
    js.setPreferredSize(new Dimension(100,60));
    js.getViewport().addChangeListener(new ChangeListener(){
      private int lastHeight;

      public void stateChanged(ChangeEvent e){
        JViewport vp = (JViewport) e.getSource();
        int h = vp.getViewSize().height;
        if (h != lastHeight){ // i.e. an addition not just a user scrolling
          lastHeight = h;
          int x = h - vp.getExtentSize().height;
          vp.setViewPosition(new Point(0, x));
        }
      }
    });
    setLayout(new BorderLayout());
    add(p1, BorderLayout.CENTER);
    JPanel p2 = new JPanel();
    p2.setLayout(new BorderLayout());
    p2.add(m_CustomPanel, BorderLayout.WEST);
    p2.add(m_StatusLab, BorderLayout.CENTER);
    p2.add(m_TaskMonitor, BorderLayout.EAST);
    add(p2, BorderLayout.SOUTH);
    addPopups();
  }

  /**
   * Añade un componente a la izquierda de la barra de log
   *
   * @param customPanel El componente(panel) a añadir)
   */
  public void addCustomPanel(JPanel customPanel){
    m_CustomPanel.add(customPanel, BorderLayout.CENTER);
  }

  /**
   * Add a popup menu for displaying the amount of free memory
   * and running the garbage collector, and another
   * to copy the log text to the system clipboard.
   */
  private void addPopups(){
    m_LogText.addMouseListener(new MouseAdapter(){
      public void mouseClicked(MouseEvent e){
        if (SwingUtilities.isRightMouseButton(e)){

          JPopupMenu popupMenu = new JPopupMenu();

          JMenuItem clear = new JMenuItem("Clear the text");
          clear.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e2){
              m_LogText.setText("");
            }
          });
          popupMenu.add(clear);

          JMenuItem copyToClipBoard = new JMenuItem("Copy the text to the clipboard");
          copyToClipBoard.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e2){
              String text = m_LogText.getSelectedText();
              if(text == null)
                text = m_LogText.getText();
              StringSelection ss = new StringSelection(text);
              Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, null);
            }
          });
          popupMenu.add(copyToClipBoard);

          JMenuItem availMem = new JMenuItem("Available memory");
          availMem.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent ee){
              System.gc();
              Runtime currR = Runtime.getRuntime();
              long freeM = currR.freeMemory();
              logMessage("Available memory : " + freeM + " bytes");
            }
          });
          popupMenu.add(availMem);

          JMenuItem runGC = new JMenuItem("Run garbage collector");
          runGC.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent ee){
              statusMessage("Running garbage collector");
              System.gc();
              statusMessage("OK");
            }
          });
          popupMenu.add(runGC);

          popupMenu.show(m_LogText, e.getX(), e.getY());
        }
      }
    });
  }

  /**
   * Record the starting of a new task
   */
  public void taskStarted(){
    if (m_TaskMonitor != null){
      m_TaskMonitor.taskStarted();
    }
  }

  /**
   * Record a task ending
   */
  public void taskFinished(){
    if (m_TaskMonitor != null){
      m_TaskMonitor.taskFinished();
    }
  }

  /**
   * Gets a string containing current date and time.
   *
   * @return a string containing the date and time.
   */
  protected static String getTimestamp(){

    return (new SimpleDateFormat("HH:mm:ss:")).format(new Date());
  }

  /**
   * Sends the supplied message to the log area. The current timestamp will
   * be prepended.
   *
   * @param message a value of type 'String'
   */
  public void logMessage(String message){

    StyledDocument doc = (StyledDocument) m_LogText.getDocument();

    try{
      doc.insertString(doc.getLength(), LogPanel.getTimestamp() + ' ' + message +"\n", null);
      m_LogText.setCaretPosition(doc.getLength());
    }
    catch(BadLocationException e){}
  }

  /**
   * Sends the supplied message to the status line.
   *
   * @param message the status message
   */
  public void statusMessage(String message){

    m_StatusLab.setText(message);
  }

  /** @return The JTextPane component */
  public JTextPane getLogText(){
    return m_LogText;
  }

  /**
   * Tests out the log panel from the command line.
   *
   * @param args ignored
   */
  public static void main(String[] args){

    try{
      final javax.swing.JFrame jf = new javax.swing.JFrame("Log Panel");
      jf.getContentPane().setLayout(new BorderLayout());
      final LogPanel lp = new LogPanel();
      jf.getContentPane().add(lp, BorderLayout.CENTER);
      jf.addWindowListener(new java.awt.event.WindowAdapter(){
        public void windowClosing(java.awt.event.WindowEvent e){
          jf.dispose();
          System.exit(0);
        }
      });
      jf.pack();
      jf.setVisible(true);
      lp.logMessage("Welcome to the generic log panel!");
      lp.statusMessage("Hi there");
      lp.logMessage("Funky chickens");

    }
    catch (Exception ex){
      ex.printStackTrace();
      System.err.println(ex.getMessage());
    }
  }
}
