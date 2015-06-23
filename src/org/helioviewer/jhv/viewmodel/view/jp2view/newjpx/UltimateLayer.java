package org.helioviewer.jhv.viewmodel.view.jp2view.newjpx;

import java.awt.Rectangle;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Queue;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

import javax.swing.JOptionPane;

import kdu_jni.KduException;
import kdu_jni.Kdu_cache;

import org.helioviewer.jhv.base.ImageRegion;
import org.helioviewer.jhv.base.downloadmanager.AbstractRequest;
import org.helioviewer.jhv.base.downloadmanager.AbstractRequest.PRIORITY;
import org.helioviewer.jhv.base.downloadmanager.HTTPRequest;
import org.helioviewer.jhv.base.downloadmanager.JPIPDownloadRequest;
import org.helioviewer.jhv.base.downloadmanager.JPIPRequest;
import org.helioviewer.jhv.base.downloadmanager.UltimateDownloadManager;
import org.helioviewer.jhv.gui.MainFrame;
import org.helioviewer.jhv.layers.CacheableImageData;
import org.helioviewer.jhv.layers.ImageLayer;
import org.helioviewer.jhv.opengl.texture.TextureCache;
import org.helioviewer.jhv.opengl.texture.TextureCache.CachableTexture;
import org.helioviewer.jhv.viewmodel.metadata.MetaData;
import org.helioviewer.jhv.viewmodel.timeline.TimeLine;
import org.helioviewer.jhv.viewmodel.view.jp2view.kakadu.JHV_KduException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class UltimateLayer {

	public String observatory;
	public String instrument;
	public String measurement1;
	public String measurement2;
	public int sourceID;

	public static final int MAX_FRAME_SIZE = 10;
	private static final String URL = "http://api.helioviewer.org/v2/getJPX/?";
	private static final String URL_MOVIES = "http://api.helioviewer.org/jp2";
	private static final int MAX_THREAD_PER_LAYER = 2;
	private static final int MAX_THREAD_PER_LOAD_JPIP_URLS = 4;

	private KakaduRender render;

	private String fileName = null;
	private LocalDateTime[] localDateTimes;
	private TreeSet<LocalDateTime> treeSet;
	private ImageLayer newLayer;

	private ImageRegion imageRegion;
	private Thread jpipURLLoader;

	private int id;

	private ArrayList<AbstractRequest> requests = new ArrayList<AbstractRequest>();

	public UltimateLayer(int id, int sourceID, KakaduRender render,
			ImageLayer newLayer) {
		treeSet = new TreeSet<LocalDateTime>();
		this.id = id;
		this.newLayer = newLayer;
		this.sourceID = sourceID;
		this.render = render;
	}

	public UltimateLayer(int id, String filename, KakaduRender render,
			ImageLayer newLayer) {
		treeSet = new TreeSet<LocalDateTime>();
		this.id = id;
		this.newLayer = newLayer;
		this.sourceID = 0;
		this.render = render;
		this.fileName = filename;
		try {
			this.render.closeImage();
			this.render.openImage(filename);
			int framecount = this.render.getFrameCount();
			LocalDateTime[] localDateTimes = new LocalDateTime[framecount];
			for (int i = 1; i <= framecount; i++) {
				MetaData metaData = render.getMetadata(i);
				treeSet.add(metaData.getLocalDateTime());
				localDateTimes[i - 1] = metaData.getLocalDateTime();
			}
			Cache.addCacheElement(new CacheableImageData(id, localDateTimes,
					filename));

			this.render.closeImage();
		} catch (KduException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JHV_KduException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		this.timeArrayChanged();
	}

	public void setTimeRange(final LocalDateTime start,
			final LocalDateTime end, final int cadence) {

		jpipURLLoader = new Thread(new Runnable() {
			private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
			private ArrayList<HTTPRequest> httpRequests = new ArrayList<HTTPRequest>();
			private HashMap<HTTPRequest, JPIPDownloadRequest> jpipDownloadRequests = new HashMap<HTTPRequest, JPIPDownloadRequest>();
			@Override
			public void run() {
				LocalDateTime tmp = LocalDateTime.MIN;
				LocalDateTime currentStart = start;

				while (tmp.isBefore(end)) {
					tmp = currentStart.plusSeconds(cadence
							* (MAX_FRAME_SIZE - 1));
					String request = "startTime="
							+ currentStart.format(formatter) + "&endTime="
							+ tmp.format(formatter) + "&sourceId=" + sourceID
							+ "&jpip=true&verbose=true&cadence=" + cadence;
					HTTPRequest httpRequest = new HTTPRequest(URL + request,
							PRIORITY.HIGH);
					requests.add(httpRequest);
					UltimateDownloadManager.addRequest(httpRequest);
					httpRequests.add(httpRequest);
					
					request = "startTime="
							+ currentStart.format(formatter) + "&endTime="
							+ tmp.format(formatter) + "&sourceId=" + sourceID
							+ "&cadence=" + cadence;
					
					CacheableImageData cacheableImageData = new CacheableImageData(
							id, new Kdu_cache());
					JPIPDownloadRequest jpipDownloadRequest = new JPIPDownloadRequest(URL + request, PRIORITY.LOW, cacheableImageData, requests);
					jpipDownloadRequests.put(httpRequest, jpipDownloadRequest);
					requests.add(jpipDownloadRequest);
					UltimateDownloadManager.addRequest(jpipDownloadRequest);
					currentStart = tmp;
				}

				boolean finished = false;
				while (!finished) {
					if (Thread.interrupted())
						return;
					for (HTTPRequest httpRequest : httpRequests) {
						finished = false;
						finished &= httpRequest.isFinished();
						if (httpRequest.isFinished()
								&& requests.contains(httpRequest)) {
							JSONObject jsonObject;
							try {
								jsonObject = new JSONObject(httpRequest
										.getDataAsString());

								if (jsonObject.has("error")) {
									System.out.println("error during : " + httpRequest);
									requests.remove(httpRequest);
									break;
								}

								JSONArray frames = ((JSONArray) jsonObject
										.get("frames"));
								LocalDateTime[] localDateTimes = new LocalDateTime[frames
										.length()];
								for (int i = 0; i < frames.length(); i++) {
									Timestamp timestamp = new Timestamp(frames
											.getLong(i) * 1000L);
									localDateTimes[i] = timestamp
											.toLocalDateTime();
								}
								JPIPDownloadRequest jpipDownloadRequest = jpipDownloadRequests.get(httpRequest);
								String jpipURI = jsonObject.getString("uri");
								System.out.println("jpipURL : " + jpipURI);

								CacheableImageData cacheableImageData = jpipDownloadRequest.getCachaableImageData();
								cacheableImageData.setLocalDateTimes(localDateTimes);
								jpipDownloadRequests.remove(httpRequest);
								Cache.addCacheElement(cacheableImageData);
								addFramedates(localDateTimes);

								JPIPRequest jpipRequestLow = new JPIPRequest(
										jpipURI, PRIORITY.HIGH, 0, frames
												.length(), new Rectangle(256,
												256), cacheableImageData.getImageData(),
										cacheableImageData);
								// JPIPRequest jpipRequestMiddle = new
								// JPIPRequest(
								// jpipURI, PRIORITY.MEDIUM, 0, frames.length(),
								// new Rectangle(1024, 1024), kduCache,
								// cacheableImageData);
								// JPIPRequest jpipRequestHigh = new
								// JPIPRequest(jpipURI, PRIORITY.LOW, 0,
								// frames.length(), new Rectangle(4096, 4096),
								// kduCache,
								// cacheableImageData);
								requests.add(jpipRequestLow);
								// requests.add(jpipRequestMiddle);
								// requests.add(jpipRequestHigh);
								UltimateDownloadManager
										.addRequest(jpipRequestLow);
								// UltimateDownloadManager.addRequest(jpipRequestMiddle);
								// UltimateDownloadManager.addRequest(jpipRequestHigh);
								String downloadURL = URL_MOVIES
										+ jpipURI.substring(
												jpipURI.indexOf(":8090") + 5,
												jpipURI.length());
								/*JPIPDownloadRequest jpipDownloadRequest = new JPIPDownloadRequest(
										downloadURL, PRIORITY.LOW,
										cacheableImageData, requests);
								requests.add(jpipDownloadRequest);
								UltimateDownloadManager
										.addRequest(jpipDownloadRequest);*/
							} catch (JSONException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							} catch (IOException e) {
								JOptionPane.showConfirmDialog(
										MainFrame.SINGLETON,
										"No connection available from "
												+ httpRequest,
										"Error during connection",
										JOptionPane.OK_OPTION,
										JOptionPane.ERROR_MESSAGE);
							}
							requests.remove(httpRequest);
						}
					}
				}

			}
		}, "JPIP_URI_LOADER");

		jpipURLLoader.start();
	}

	public int getFrameCount() {
		if (fileName != null)
			return localDateTimes.length;
		return 1;
	}

	public void addFramedates(LocalDateTime[] localDateTimes) {
		for (LocalDateTime localDateTime : localDateTimes)
			treeSet.add(localDateTime);
		LocalDateTime[] allLocalDateTimes = treeSet
				.toArray(new LocalDateTime[treeSet.size()]);
		TimeLine.SINGLETON.setFrames(allLocalDateTimes);
	}

	public MetaData getMetaData(LocalDateTime currentDateTime)
			throws InterruptedException, ExecutionException, JHV_KduException {

		CacheableImageData cacheObject = null;

		cacheObject = Cache.getCacheElement(id, currentDateTime);
		if (Cache.getCacheElement(this.id, currentDateTime) == null)
			return null;
		fileName = cacheObject.getImageFile();

		if (fileName != null)
			return this.getMetaData(cacheObject.getIdx(currentDateTime));

		cacheObject = Cache.getCacheElement(id, currentDateTime);

		render.openImage(cacheObject.getImageData());
		MetaData metaData = this.render.getMetadata(0);
		render.closeImage();
		return metaData;
	}

	public MetaData getMetaData(int index) {
		render.openImage(fileName);
		MetaData metaData = null;
		try {
			metaData = render.getMetadata(index);
		} catch (JHV_KduException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		render.closeImage();
		return metaData;
	}

	public LocalDateTime getLocalDateTime(int index) {
		return localDateTimes[index];
	}

	@Deprecated
	public LocalDateTime[] getLocalDateTimes() {
		LocalDateTime[] localDateTimes = new LocalDateTime[treeSet.size()];
		return this.treeSet.toArray(localDateTimes);
	}

	private ByteBuffer getImageFromLocalFile(int idx, float zoomFactor,
			Rectangle imageSize) throws JHV_KduException {
		render.openImage(fileName);

		ByteBuffer intBuffer = render.getImage(idx, 8, zoomFactor, imageSize);
		render.closeImage();
		return intBuffer;
	}

	public ByteBuffer getImageData(LocalDateTime currentDateTime,
			ImageRegion imageRegion, MetaData metaData, boolean highResolution)
			throws InterruptedException, ExecutionException, JHV_KduException {
		// newLayer.getImageRegion().calculateScaleFactor(newLayer, camera);
		Queue<CachableTexture> textures = TextureCache.getCacheableTextures();
		for (CachableTexture texture : textures) {
			if (texture.compareRegion(id, imageRegion, currentDateTime)
					&& !texture.hasChanged()) {
				this.imageRegion = texture.getImageRegion();
				TextureCache.setElementAsFist(texture);
				return null;
			}
		}
		CacheableImageData cacheObject = null;

		cacheObject = Cache.getCacheElement(id, currentDateTime);
		fileName = cacheObject.getImageFile();

		if (fileName != null) {
			imageRegion.setLocalDateTime(currentDateTime);
			this.imageRegion = TextureCache.addElement(imageRegion, id);
			this.imageRegion.setMetaData(metaData);
			return getImageFromLocalFile(cacheObject.getIdx(currentDateTime),
					this.imageRegion.getZoomFactor(),
					this.imageRegion.getImageSize());
		}

		imageRegion.setLocalDateTime(currentDateTime);
		this.imageRegion = TextureCache.addElement(imageRegion, id);
		this.imageRegion.setMetaData(metaData);

		render.openImage(cacheObject.getImageData());

		ByteBuffer intBuffer = render.getImage(
				cacheObject.getLstDetectedDate(), 8,
				this.imageRegion.getZoomFactor(),
				this.imageRegion.getImageSize());
		render.closeImage();
		return intBuffer;
	}

	public ImageRegion getImageRegion() {
		return this.imageRegion;
	}

	@Deprecated
	private void timeArrayChanged() {
		LocalDateTime[] localDateTimes = new LocalDateTime[treeSet.size()];
		TimeLine.SINGLETON.updateLocalDateTimes(this.treeSet
				.toArray(localDateTimes));
	}

	public void cancelDownload() {
		if (jpipURLLoader != null && jpipURLLoader.isAlive())
			jpipURLLoader.interrupt();
		for (AbstractRequest request : requests) {
			UltimateDownloadManager.remove(request);
		}
	}

}
