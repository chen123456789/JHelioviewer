package org.helioviewer.jhv;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.LinkedBlockingQueue;

import javax.swing.JFileChooser;

/**
 * Intended to be a class for static functions and fields relevant to the
 * application as a whole.
 */
public class JHVGlobals {
    public static final String VERSION = System.getProperty("jhvVersion") == null ? "developer" : System.getProperty("jhvVersion");
    public static final String RAYGUN_TAG = System.getProperty("raygunTag");
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
    public static final DateTimeFormatter FILE_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH.mm.ss'Z'");
    
	/**
	 * AIA 193
	 */
    public static final int STARTUP_LAYER_ID = 10;

    //TODO check all invocations of file dialogs, check should happen centralized
	public static final boolean USE_JAVA_FX;
	
	static
	{
		boolean javaFxAvailable = true;
		try
		{
			Class.forName("com.sun.javafx.runtime.VersionInfo");
		}
		catch (ClassNotFoundException e)
		{
			javaFxAvailable = false;
			System.err.println("No JavaFX detected. Please install a Java 1.8 with JavaFX");
		}
		
		USE_JAVA_FX = javaFxAvailable;
	}

    private JHVGlobals()
    {
    }
    
    
    public static boolean isReleaseVersion()
    {
        return RAYGUN_TAG!=null;
    }
    
    public static boolean is64Bit()
    {
        return System.getProperty("os.arch").contains("64");
    }
    
    public static boolean isWindows()
    {
        return System.getProperty("os.name").toUpperCase().contains("WIN");
    }
    
    public static boolean isLinux()
    {
        return System.getProperty("os.name").toUpperCase().contains("LINUX");
    }

    public static boolean isOSX()
    {
        return System.getProperty("os.name").toUpperCase().contains("MAC OS X");
    }

    /**
     * Opens the specified web page in the default web browser
     * 
     * @param url
     *            A web address (URL) of a web page (e.g
     *            "http://www.jhelioviewer.org/")
     */
    public static void openURL(String url)
    {
        try
        {
            Desktop.getDesktop().browse(new URI(url));
        }
        catch(Exception e)
        {
            e.printStackTrace();
            try
            {
                new ProcessBuilder("x-www-browser",url).start();
            }
            catch(Exception e2)
            {
                e2.printStackTrace();
            }
        }
    }
    
    private static LinkedBlockingQueue<JFileChooser> fileChooser=new LinkedBlockingQueue<>();
    
    public static JFileChooser getJFileChooser()
    {
        return getJFileChooser(null);
    }
    
    public static JFileChooser getJFileChooser(String _directory)
    {
        try
        {
            JFileChooser instance=fileChooser.take();
            fileChooser.add(instance);
            
            instance.setFileHidingEnabled(false);
            instance.setMultiSelectionEnabled(false);
            instance.setAcceptAllFileFilterUsed(false);
            instance.setFileSelectionMode(JFileChooser.FILES_ONLY);
            instance.resetChoosableFileFilters();
            instance.setSelectedFile(null);
            
            if(_directory==null)
                instance.setCurrentDirectory(null);
            else
                instance.setCurrentDirectory(new File(_directory));
            
            return instance;
        }
        catch(InterruptedException e)
        {
            //shouldn't happen, except on shutdown
            return null;
        }
    }
    
    public static void initFileChooserAsync()
    {
        Thread t=new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                fileChooser.add(new JFileChooser());
            }
        });
        
        t.setDaemon(true);
        t.start();
    }
}
