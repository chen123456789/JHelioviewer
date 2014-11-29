package org.helioviewer.jhv.viewmodel.metadata;

import java.lang.reflect.Constructor;

import javax.swing.JOptionPane;

import org.helioviewer.jhv.gui.GuiState3DWCS;

public class MetaDataFactory {
	@SuppressWarnings("unchecked")
  static final Class<MetaData>[] META_DATA_CLASSES = new Class[]{
		MetaDataAIA.class,
		MetaDataEIT.class,
		MetaDataHMI.class,
		MetaDataLASCO_C2.class,
		MetaDataLASCO_C3.class,
		MetaDataMDI.class,
		MetaDataStereo.class,
		MetaDataStereoA_COR1.class,
		MetaDataStereoA_COR2.class,
		MetaDataStereoB_COR1.class,
		MetaDataStereoB_COR2.class//,
		//MetaDataSWAP.class
	};
	
	
	public static MetaData getMetaData(MetaDataContainer metaDataContainer){
		
		MetaData metaData = null;
		Object[] args = {metaDataContainer};
		for (Class<MetaData> c : META_DATA_CLASSES){
			Constructor<MetaData> constructor;
			try {
				constructor = c.getDeclaredConstructor(MetaDataContainer.class);
				metaData = constructor.newInstance(args);
			} catch (Exception e) {
				e.printStackTrace();
				metaData = null;
			}
			if (metaData != null) break;
		}
		
		if (metaData != null){
			return metaData;
		}
		
		JOptionPane.showMessageDialog(GuiState3DWCS.mainComponentView.getComponent(), "This data source's metadata could not be read.");
		return null;
		
	}
}
