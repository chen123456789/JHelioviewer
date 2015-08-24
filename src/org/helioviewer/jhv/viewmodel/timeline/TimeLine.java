package org.helioviewer.jhv.viewmodel.timeline;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.Timer;

import org.helioviewer.jhv.gui.MainFrame;
import org.helioviewer.jhv.gui.leftPanel.MoviePanel.ANIMATION_MODE;
import org.helioviewer.jhv.layers.AbstractImageLayer;
import org.helioviewer.jhv.layers.AbstractLayer;
import org.helioviewer.jhv.layers.LayerListener;
import org.helioviewer.jhv.layers.Layers;

public class TimeLine implements LayerListener {

	private LocalDateTime current = LocalDateTime.now();

	private boolean isPlaying = false;

	private CopyOnWriteArrayList<TimeLineListener> timeLineListeners;

	private ConcurrentSkipListSet<LocalDateTime> localDateTimes = null;

	private int speedFactor;
	private ANIMATION_MODE animationMode = ANIMATION_MODE.LOOP;

	public static TimeLine SINGLETON = new TimeLine();


	private TimeLine() {
		localDateTimes = new ConcurrentSkipListSet<LocalDateTime>();
		Layers.addNewLayerListener(this);
		timeLineListeners = new CopyOnWriteArrayList<TimeLine.TimeLineListener>();
	}

	public boolean isPlaying() {
		return isPlaying;
	}

	public void setPlaying(boolean playing) {
		this.isPlaying = playing;
	}

	public LocalDateTime nextFrame() {
		if (localDateTimes.isEmpty())
			return null;
		LocalDateTime next = localDateTimes.higher(current.plusNanos(1));
		LocalDateTime last = current;
		if (next == null) {
			next = localDateTimes.first();
		}
		current = next;
		dateTimeChanged(last);
		return current;
	}

	public LocalDateTime previousFrame() {
		if (localDateTimes.isEmpty())
			return null;
		LocalDateTime next = localDateTimes.lower(current.minusNanos(1));
		if (next == null) {
			next = localDateTimes.last();
		}
		current = next;
		dateTimeChanged(current);
		return current;
	}

	@Deprecated
	public int getMaxFrames() {
		return localDateTimes.size();
	}

	@Deprecated
	public int getCurrentFrame() {
		return localDateTimes.headSet(current).size();
	}

	public LocalDateTime getCurrentDateTime() {
		return current;
	}

	public void addListener(TimeLineListener timeLineListener) {
		timeLineListeners.add(timeLineListener);
	}

	public void removeListener(TimeLineListener timeLineListener) {
		timeLineListeners.remove(timeLineListener);
	}

	private void dateTimeChanged(LocalDateTime last) {
		for (TimeLine.TimeLineListener timeLineListener : timeLineListeners) {
			timeLineListener.timeStampChanged(current, last);
		}
	}

	private void notifyUpdateDateTimes() {
		MainFrame.MOVIE_PANEL.setEnableButtons(!localDateTimes.isEmpty());
		for (TimeLine.TimeLineListener timeLineListener : timeLineListeners) {
			timeLineListener.dateTimesChanged(localDateTimes.size());
		}
	}

	public void setSpeedFactor(int speedFactor) {
		this.speedFactor = 1000 / speedFactor;
	}

	public int getSpeedFactor() {
		return speedFactor;
	}

	@Override
	public void newlayerAdded() {
		// TODO Auto-generated method stub

	}

	@Override
	public void newlayerRemoved(int idx) {
		if (Layers.getActiveImageLayer() == null)
			localDateTimes = null;
	}

	@Override
	public void activeLayerChanged(AbstractLayer layer) {
		if (layer != null && layer.isImageLayer()){
			updateLocalDateTimes(((AbstractImageLayer)layer).getLocalDateTime());
			MainFrame.MOVIE_PANEL.setEnableButtons(!localDateTimes.isEmpty());
		}
	}

	public void setCurrentFrame(int value) {
		Iterator<LocalDateTime> it = localDateTimes.iterator();
		int i = 0;
		LocalDateTime current = null;
		while (it.hasNext() && i <= value) {
			current = it.next();
			i++;
		}
		if (current != null && !current.isEqual(this.current)) {
			LocalDateTime last = this.current;
			this.current = current;
			dateTimeChanged(last);
		}
	}

	public interface TimeLineListener {
		void timeStampChanged(LocalDateTime current, LocalDateTime last);

		@Deprecated
		void dateTimesChanged(int framecount);
	}

	public void updateLocalDateTimes(ConcurrentSkipListSet<LocalDateTime> localDateTimes) {
		this.localDateTimes = localDateTimes;
		notifyUpdateDateTimes();
	}

	public LocalDateTime getFirstDateTime() {
		if (localDateTimes == null || localDateTimes.isEmpty())
			return null;
		return localDateTimes.first();
	}

	public LocalDateTime getLastDateTime() {
		if (localDateTimes == null || localDateTimes.isEmpty())
			return null;
		return localDateTimes.last();
	}

	public void setCurrentDate(LocalDateTime currentDateTime) {
		LocalDateTime last = current;
		this.current = currentDateTime;
		dateTimeChanged(last);
	}

	public void setAnimationMode(ANIMATION_MODE animationMode) {
		this.animationMode = animationMode;
	}
	
	public void calculateNextFrameDate(long time){
		//nextFrame();
	}
}
