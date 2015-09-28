package org.helioviewer.jhv.opengl;

import java.time.LocalDateTime;
import java.util.LinkedList;

import org.helioviewer.jhv.base.ImageRegion;
import org.helioviewer.jhv.viewmodel.jp2view.newjpx.KakaduLayer;

//FIXME: handle concurrency, by using the cache only from awt/gl thread
public class TextureCache
{
	//FIXME: handle pool running out
	private static final int TEXTURE_CACHE_SIZE = 10;
	private static LinkedList<Texture> cache = new LinkedList<Texture>();
	
	static
	{
		int[] textures = OpenGLHelper.createTextureIDs(TEXTURE_CACHE_SIZE);
		for (int texture : textures)
			cache.add(new Texture(texture));
	}
	
	public static void init()
	{
	}

	public synchronized static ImageRegion add(ImageRegion _imageRegion, KakaduLayer _id)
	{
		Texture texture = cache.removeFirst();
		texture.setNewImageRegion(_id, _imageRegion);
		cache.add(texture);
		return _imageRegion;
	}
	
	private static void moveElementToFront(Texture texture)
	{
		cache.remove(texture);
		cache.add(texture);
	}
	
	public synchronized static void invalidate(int _sourceId, LocalDateTime _localDateTimes)
	{
		for (Texture cacheableTexture : cache)
			if (cacheableTexture.compareTexture(_sourceId, _localDateTimes))
				cacheableTexture.invalidate();
	}

	public synchronized static ImageRegion get(KakaduLayer id, ImageRegion imageRegion, LocalDateTime localDateTime)
	{
		for (Texture texture : cache)
			if (texture.compareRegion(id, imageRegion, localDateTime))
			{
				TextureCache.moveElementToFront(texture);
				return texture.getImageRegion();
			}
		
		return null;
	}
}
