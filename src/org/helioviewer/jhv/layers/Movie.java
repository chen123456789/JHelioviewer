package org.helioviewer.jhv.layers;

import java.awt.Rectangle;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.helioviewer.jhv.base.Globals;
import org.helioviewer.jhv.base.Telemetry;
import org.helioviewer.jhv.opengl.Texture;
import org.helioviewer.jhv.viewmodel.TimeLine.DecodeQualityLevel;
import org.helioviewer.jhv.viewmodel.jp2view.kakadu.KakaduUtils;
import org.helioviewer.jhv.viewmodel.metadata.MetaData;
import org.helioviewer.jhv.viewmodel.metadata.MetaDataFactory;
import org.helioviewer.jhv.viewmodel.metadata.UnsuitableMetaDataException;
import org.w3c.dom.Document;

import kdu_jni.Jp2_threadsafe_family_src;
import kdu_jni.Jpx_codestream_source;
import kdu_jni.Jpx_input_box;
import kdu_jni.Jpx_layer_source;
import kdu_jni.Jpx_source;
import kdu_jni.KduException;
import kdu_jni.Kdu_channel_mapping;
import kdu_jni.Kdu_codestream;
import kdu_jni.Kdu_coords;
import kdu_jni.Kdu_dims;
import kdu_jni.Kdu_global;
import kdu_jni.Kdu_quality_limiter;
import kdu_jni.Kdu_region_decompressor;
import kdu_jni.Kdu_thread_env;

//TODO: manage, cache, ... individual frames (i.e. code streams) instead of whole movies

public abstract class Movie
{
	//private final ConcurrentLinkedQueue<Jpx_input_box> openInputBoxes=new ConcurrentLinkedQueue<>();
	//private ThreadLocal<Jpx_input_box> tlsJpx_input_box=new ThreadLocal<>();
	
	private static ThreadLocal<Kdu_region_decompressor> tlsKdu_region_decompressor=ThreadLocal.withInitial(() ->
	{
		try
		{
			Kdu_region_decompressor decompressor = new Kdu_region_decompressor();
			decompressor.Set_interpolation_behaviour(0, 0);
			
			Kdu_quality_limiter q=new Kdu_quality_limiter(4f/256);
			decompressor.Set_quality_limiting(q, 0, 0);
			
			return decompressor;
		}
		catch (KduException e)
		{
			throw new RuntimeException(e);
		}
	});
	
	private static ThreadLocal<byte[]> byteArrayBuffer = new ThreadLocal<>();
	
	private final ArrayList<Jpx_source> openJpx_sources = new ArrayList<Jpx_source>(1);
	private ThreadLocal<Jpx_source> tlsJpx_source=ThreadLocal.withInitial(new Supplier<Jpx_source>()
	{
		@Override
		public Jpx_source get()
		{
			try
			{
				Jpx_source s = new Jpx_source();
				s.Open(family_src, true);
				
				synchronized(openJpx_sources)
				{
					openJpx_sources.add(s);
				}
				return s; 
			}
			catch (KduException e)
			{
				throw new RuntimeException(e);
			}
		}
	});
	
	protected MetaData[] metaDatas;
	protected long[] timeMS;
	
	public final int sourceId;
	
	public Movie(int _sourceId)
	{
		sourceId=_sourceId;
	}

	protected final Jp2_threadsafe_family_src family_src = new Jp2_threadsafe_family_src();
	protected boolean disposed;
	
	public void dispose()
	{
		disposed=true;
		metaDatas=null;
		
		if(disposed)
			return;
		
		synchronized(openJpx_sources)
		{
			for(Jpx_source s:openJpx_sources)
			{
				try
				{
					s.Close();
				}
				catch (KduException e)
				{
					Telemetry.trackException(e);
				}
				s.Native_destroy();
			}
			openJpx_sources.clear();
		}
		
		/*synchronized(openKdu_codestreams)
		{
			for(Kdu_codestream c:openKdu_codestreams)
				try
				{
					c.Destroy();
				}
				catch (KduException e)
				{
					Telemetry.trackException(e);
				}
			openKdu_codestreams.clear();
			
		}*/
		
		/*for(;;)
		{
			Jpx_input_box box = openInputBoxes.poll();
			if(box==null)
				break;
			
			try
			{
				box.Close();
			}
			catch (KduException e)
			{
				Telemetry.trackException(e);
			}
			box.Native_destroy();
		}*/
		
		try
		{
			family_src.Close();
		}
		catch (KduException e)
		{
			Telemetry.trackException(e);
		}
		family_src.Native_destroy();
	}
	
	public abstract boolean isBetterQualityThan(Movie _other);
	
	
	public abstract boolean isFullQuality();

	
	protected synchronized void loadMetaData(int i)
	{
		if(metaDatas[i]!=null)
			return;
		
		try
		{
			if(!family_src.Is_codestream_main_header_complete(i))
				return;
			
			metaDatas[i]=MetaDataFactory.getMetaData(readMetadataDocument(i+1));
			if(metaDatas[i]==null)
				Telemetry.trackException(new UnsuitableMetaDataException("Cannot find metadata class for:\n"+KakaduUtils.getXml(family_src, i+1)));
			else if(timeMS[i]==0)
				timeMS[i]=metaDatas[i].timeMS;
			else if(timeMS[i]!=metaDatas[i].timeMS)
			{
				System.err.println("Timestamps diverged: "+timeMS[i]+" vs "+metaDatas[i].timeMS);
				timeMS[i]=metaDatas[i].timeMS;
			}
		}
		catch (KduException e)
		{
			Telemetry.trackException(e);
		}
	}
	
	public class Match
	{
		public final int index;
		public final long timeDifferenceMS;
		public final Movie movie;
		
		Match(int _index, long _timeDifferenceMS)
		{
			index=_index;
			timeDifferenceMS=_timeDifferenceMS;
			movie=Movie.this;
		}
		
		@Override
		public boolean equals(@Nullable Object _obj)
		{
			if(!(_obj instanceof Match))
				return false;
			
			Match o=(Match)_obj;
			return index==o.index && movie==o.movie;
		}
		
		@Override
		public int hashCode()
		{
			return index ^ Long.hashCode(timeDifferenceMS);
		}
		
		public boolean decodeImage(DecodeQualityLevel _quality, float _zoomFactor, Rectangle _requiredPixels, Texture _target)
		{
			return movie.decodeImage(index, _quality, _zoomFactor, _requiredPixels, _target);
		}
		
		public @Nullable MetaData getMetaData()
		{
			return movie.getMetaData(index);
		}
		
		public long getTimeMS()
		{
			return movie.getTimeMS(index);
		}
		
		public @Nullable Document getMetaDataDocument()
		{
			return movie.readMetadataDocument(index);
		}
	}
	
	@Nullable public Match findBestIdx(long _minTimeMSInclusive, long _maxTimeMSExclusive)
	{
		long middle = (_minTimeMSInclusive + _maxTimeMSExclusive)/2;
		int bestMatch = -1;
		long bestDiff = Long.MAX_VALUE;
		
		for (int i = 0; i < timeMS.length; i++)
			if(timeMS[i]>=_minTimeMSInclusive && timeMS[i]<_maxTimeMSExclusive)
			{
				long curDiff = Math.abs(timeMS[i]-middle);
				if(curDiff<bestDiff)
				{
					bestDiff=curDiff;
					bestMatch = i;
				}
			}
		
		if(bestMatch==-1)
			return null;
		
		return new Match(bestMatch,-1);
	}
	
	@Nullable public Match findClosestIdx(long _currentDateTimeMS)
	{
		int bestI=-1;
		long minDiff = Long.MAX_VALUE;
		
		for (int i = 0; i < timeMS.length; i++)
		{
			long curDiff = Math.abs(timeMS[i]-_currentDateTimeMS);
			if(curDiff<minDiff)
			{
				minDiff=curDiff;
				bestI=i;
			}
		}
		
		if(bestI==-1)
			return null;
		else
			return new Match(bestI,minDiff);
	}
	
	@Nullable public MetaData getAnyMetaData()
	{
		for(int i=0;i<metaDatas.length;i++)
		{
			loadMetaData(i);
			if(metaDatas[i]!=null)
				return metaDatas[i];
		}
		
		return null;
	}
	
	@Nullable
	public MetaData getMetaData(int idx)
	{
		loadMetaData(idx);
		return metaDatas[idx];
	}
	
	public long getTimeMS(int idx)
	{
		return timeMS[idx];
	}
	
	@Nullable
	public Document readMetadataDocument(int _index)
	{
		try
		{
			String xmlText = KakaduUtils.getXml(family_src, _index);
			if (xmlText == null)
				return null;
			xmlText = xmlText.trim().replace("&", "&amp;").replace("$OBS", "");
			
			try(InputStream in = new ByteArrayInputStream(xmlText.getBytes("UTF-8")))
			{
				DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
				Document doc = builder.parse(in);
				doc.getDocumentElement().normalize();
				
				return doc;
			}
		}
		catch (Exception ex)
		{
			Telemetry.trackException(ex);
		}
		return null;
	}
	
	protected void loadCodestreamIntoCache(long _codestreamId) throws Exception
	{
	}
	
	protected void unloadCodestreamFromCache(long _codestreamId) throws Exception
	{
	}
	
	private static ExecutorService exec = Executors.newFixedThreadPool(Globals.CORES);
	
	public boolean decodeImage(int _index, DecodeQualityLevel _quality, float _zoomPercent, Rectangle _requiredRegion, Texture _target)
	{
		if(disposed)
			throw new IllegalStateException();
		
		_target.prepareUploadBuffer(_requiredRegion.width, _requiredRegion.height);
		
		int codestreamId=-1;
		try
		{
			int discardLevels=(int)Math.round(-Math.log(_zoomPercent)/Math.log(2));
			
			{
				Jpx_source jpxSrc=tlsJpx_source.get();
		        Jpx_layer_source xlayer = jpxSrc.Access_layer(_index);
		        if(!xlayer.Exists())
		        	return false;
		        
		        codestreamId=xlayer.Get_codestream_id(0);
		        loadCodestreamIntoCache(codestreamId);
			}
	        
			final int THREAD_REGIONS = Math.min(Globals.CORES, 1+(_requiredRegion.width*_requiredRegion.height)/(128*256));
			final int THREAD_REGION_SIZE=(_requiredRegion.height+THREAD_REGIONS-1)/THREAD_REGIONS;
			
			Kdu_coords expand_numerator = new Kdu_coords(1,1);
			Kdu_coords expand_denominator = new Kdu_coords((int)Math.round(1/_zoomPercent/(1<<discardLevels)),(int)Math.round(1/_zoomPercent/(1<<discardLevels)));
			
			Kdu_channel_mapping mapping = new Kdu_channel_mapping();
			mapping.Configure(1 /* CHANNELS */, 8 /* BIT DEPTH */, false /* IS_SIGNED */);
			
			
			CountDownLatch readyLatch = new CountDownLatch((_requiredRegion.height+THREAD_REGION_SIZE-1)/THREAD_REGION_SIZE);
			for(int ystart=0;ystart<_requiredRegion.height;ystart+=THREAD_REGION_SIZE)
			{
				final int fystart=ystart;
		        final int fcodestreamId=codestreamId;
				exec.submit(() ->
				{
					try
					{
						int position=0;
						int reqHeight = Math.min(_requiredRegion.height-fystart, THREAD_REGION_SIZE);
						
						Kdu_dims requestedBufferedRegion = new Kdu_dims();
						requestedBufferedRegion.Access_pos().Set_x(_requiredRegion.x);
						requestedBufferedRegion.Access_pos().Set_y(_requiredRegion.y+fystart);
						requestedBufferedRegion.Access_size().Set_x(_requiredRegion.width);
						requestedBufferedRegion.Access_size().Set_y(reqHeight);
						
						Kdu_region_decompressor decompressor = tlsKdu_region_decompressor.get();
						switch(_quality)
						{
							case QUALITY:
								decompressor.Set_quality_limiting(new Kdu_quality_limiter(1f/256), 300f*_zoomPercent, 300f*_zoomPercent);
								break;
							case PLAYBACK:
								decompressor.Set_quality_limiting(new Kdu_quality_limiter(4f/256), 300f*_zoomPercent, 300f*_zoomPercent);
								break;
							case SPEED:
								decompressor.Set_quality_limiting(new Kdu_quality_limiter(7f/256), 300f*_zoomPercent, 300f*_zoomPercent);
								break;
							case HURRY:
								decompressor.Set_quality_limiting(new Kdu_quality_limiter(10f/256), 300f*_zoomPercent, 300f*_zoomPercent);
								break;
							default:
								throw new RuntimeException("Unsupported quality");
						}
				        
						Jpx_source jpxSrc2=tlsJpx_source.get();
				        Jpx_codestream_source xstream = jpxSrc2.Access_codestream(fcodestreamId);
				        Jpx_input_box inputBox = xstream.Open_stream();
				        
						Kdu_codestream codestream = new Kdu_codestream();
						codestream.Create(inputBox);
						codestream.Set_resilient(false);
						
						decompressor.Start(codestream,
								mapping, //MAPPING
								0,
								discardLevels,
								16384, //MAX LAYERS
								requestedBufferedRegion,
								expand_numerator,
								expand_denominator,
								false, //PRECISE
								Kdu_global.KDU_WANT_OUTPUT_COMPONENTS,
								true //FASTEST
								);
						
						Kdu_dims incompleteRegion = new Kdu_dims();
						incompleteRegion.Assign(requestedBufferedRegion);
						Kdu_dims new_region = new Kdu_dims();
						
						byte[] buf=byteArrayBuffer.get();
						if(buf==null || buf.length<(_requiredRegion.width+16)*(reqHeight+16))
						{
							buf = new byte[(_requiredRegion.width+16)*(reqHeight+16)];
							byteArrayBuffer.set(buf);
						}
						
						while(decompressor.Process(buf,
								new int[]{position}, //CHANNEL OFFSETS
								1, //PIXEL GAP
								new Kdu_coords(), //BUFFER ORIGIN
								0, //ROW GAP
								0, //SUGGESTED INCREMENT
								buf.length-position,
								incompleteRegion,
								new_region,
								8, //PRECISION BITS
								true, //MEASURE ROW GAP IN PIXELS
								0, //EXPAND MONOCHROME
								0, //FILL ALPHA
								0 //MAX COLOUR CHANNELS (0=no limit)
								))
						{
							position+=new_region.Access_size().Get_x() * new_region.Access_size().Get_y();
							if(incompleteRegion.Access_size().Get_y() == 0)
								break;
						}
						
						decompressor.Finish();
						codestream.Destroy();
						
			        	inputBox.Close();
			        	inputBox.Native_destroy();
			        	
			        	synchronized(_target.uploadBuffer)
			        	{
			        		_target.uploadBuffer.position(fystart*_requiredRegion.width);
			        		_target.uploadBuffer.put(buf, 0, _requiredRegion.width*reqHeight);
			        	}
					}
					catch (Exception _e)
					{
						Telemetry.trackException(_e);
					}
					
					readyLatch.countDown();
				});
			}
			
			readyLatch.await();
			_target.uploadBuffer.position(0);
			return true;
		}
		catch (Exception e)
		{
			Telemetry.trackException(e);
		}
		finally
		{
			if(codestreamId!=-1)
				try
				{
					unloadCodestreamFromCache(codestreamId);
				}
				catch (Exception _e)
				{
					Telemetry.trackException(_e);
				}
		}
		return false;
	}


	public int getFrameCount()
	{
		return metaDatas.length;
	}
}
