package org.helioviewer.jhv.gui.actions;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;

import javafx.application.Platform;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;

import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.helioviewer.jhv.Globals;
import org.helioviewer.jhv.Settings;
import org.helioviewer.jhv.gui.MainFrame;
import org.helioviewer.jhv.gui.actions.filefilters.AllSupportedImageTypesFilter;
import org.helioviewer.jhv.gui.actions.filefilters.FileFilter;
import org.helioviewer.jhv.layers.Layers;
import org.helioviewer.jhv.viewmodel.jp2view.newjpx.KakaduLayer;
import org.helioviewer.jhv.viewmodel.metadata.UnsuitableMetaDataException;

/**
 * Action to open a local file.
 * 
 * <p>
 * Opens a file chooser dialog, opens the selected file. Currently supports the
 * following file extensions: "jpg", "jpeg", "png", "fts", "fits", "jp2" and
 * "jpx"
 */
public class OpenLocalFileAction extends AbstractAction {

	private static final long serialVersionUID = 1L;

	public OpenLocalFileAction() {
		super("Open...");
		putValue(SHORT_DESCRIPTION, "Open image");
		putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_O, Toolkit
				.getDefaultToolkit().getMenuShortcutKeyMask()));
	}

	public void actionPerformed(ActionEvent e) {
		
		/**
		 * Native filechooser with JavaFX
		 */
		if (Globals.USE_JAVA_FX)
		{
			Platform.runLater(new Runnable()
			{
				@Override
				public void run()
				{
					FileChooser fileChooser = new FileChooser();
					fileChooser.setTitle("Open local file");
					fileChooser.setInitialDirectory(new File(Settings.getProperty("default.local.path")));
					ExtensionFilter extensionFilter = new ExtensionFilter("JPEG 2000", "*.jpx", "*.jp2");
					ExtensionFilter extensionFilter1 = new ExtensionFilter("All Files", "*.*");
					fileChooser.getExtensionFilters().addAll(extensionFilter, extensionFilter1);
					final File selectedFile = fileChooser.showOpenDialog(new Stage());
					
					if (selectedFile != null && selectedFile.exists() && selectedFile.isFile())
					{
						SwingUtilities.invokeLater(new Runnable()
						{
							@Override
							public void run()
							{
								// remember the current directory for future
								Settings.setProperty("default.local.path", selectedFile.getParent());
								
								try
								{
									Layers.addLayer(new KakaduLayer(selectedFile.toString()));
								}
								catch(UnsuitableMetaDataException _umde)
								{
									JOptionPane.showMessageDialog(MainFrame.MAIN_PANEL, "This data source's metadata could not be read.");
								}
							}
						});
					}
				}
	
			});
		}
		else
		{
			final JFileChooser fileChooser = Globals.getJFileChooser(Settings.getProperty("default.local.path"));
			fileChooser.setAcceptAllFileFilterUsed(false);
			fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
			fileChooser.addChoosableFileFilter(FileFilter.IMPLEMENTED_FILE_FILTER.JP2.getFileFilter());
			fileChooser.addChoosableFileFilter(FileFilter.IMPLEMENTED_FILE_FILTER.FITS.getFileFilter());
			fileChooser.addChoosableFileFilter(FileFilter.IMPLEMENTED_FILE_FILTER.PNG.getFileFilter());
			fileChooser.addChoosableFileFilter(FileFilter.IMPLEMENTED_FILE_FILTER.JPG.getFileFilter());
			fileChooser.setFileFilter(new AllSupportedImageTypesFilter());
			fileChooser.setMultiSelectionEnabled(false);

			int retVal = fileChooser.showOpenDialog(MainFrame.SINGLETON);
			if (retVal == JFileChooser.APPROVE_OPTION)
			{
				File selectedFile = fileChooser.getSelectedFile();
	
				if (selectedFile.exists() && selectedFile.isFile())
				{
					// remember the current directory for future
					Settings.setProperty("default.local.path", fileChooser.getSelectedFile().getParent());
					
					try
					{
						Layers.addLayer(new KakaduLayer(fileChooser.getSelectedFile().toString()));
					}
					catch(UnsuitableMetaDataException _umde)
					{
						JOptionPane.showMessageDialog(MainFrame.MAIN_PANEL, "The source's metadata could not be read.");
					}
				}
			}
		}
	}
}
