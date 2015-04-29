package org.helioviewer.jhv.opengl.camera;

import java.awt.Component;
import java.awt.Point;
import java.awt.event.MouseEvent;

import org.helioviewer.jhv.base.math.Quaternion3d;
import org.helioviewer.jhv.base.math.Vector3d;
import org.helioviewer.jhv.opengl.raytrace.RayTrace;
import org.helioviewer.jhv.opengl.raytrace.RayTrace.Ray;
import org.helioviewer.jhv.viewmodel.view.LinkedMovieManager;
import org.helioviewer.jhv.viewmodel.view.opengl.CompenentView;

public class CameraRotationInteraction extends CameraInteraction{

	private Vector3d currentRotationStartPoint;
	private Vector3d currentRotationEndPoint;
	private volatile Quaternion3d currentDragRotation;
	private boolean yAxisBlocked = false;
	private boolean played;
	private Component component;

	public CameraRotationInteraction(CompenentView compenentView) {
		super(compenentView);
		enable = true;
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		this.currentRotationEndPoint = getVectorFromSphere(e.getPoint());
		try {
			if (currentRotationStartPoint != null
					&& currentRotationEndPoint != null) {
				if (yAxisBlocked) {
					double s = currentRotationEndPoint.x
							* currentRotationStartPoint.z
							- currentRotationEndPoint.z
							* currentRotationStartPoint.x;
					double c = currentRotationEndPoint.x
							* currentRotationStartPoint.x
							+ currentRotationEndPoint.z
							* currentRotationStartPoint.z;
					double angle = Math.atan2(s, c);
					currentDragRotation = Quaternion3d.createRotation(angle,
							new Vector3d(0, 1, 0));
					compenentView.getRotation().rotate(currentDragRotation);

				} else {
					currentDragRotation = Quaternion3d.calcRotation(
							currentRotationStartPoint, currentRotationEndPoint);
					compenentView.getRotation().rotate(currentDragRotation);
					currentRotationStartPoint = currentRotationEndPoint;
				}
			}
		} catch (IllegalArgumentException exc) {
			System.out.println("GL3DTrackballCamera.mouseDragged: Illegal Rotation ignored!");
            exc.printStackTrace();
		}
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		this.currentRotationStartPoint = null;
		this.currentRotationEndPoint = null;
		
		if (this.played){
			LinkedMovieManager.getActiveInstance().playLinkedMovies();
		}
	}

	@Override
	public void mousePressed(MouseEvent e) {
		this.played = LinkedMovieManager.getActiveInstance().isPlaying();
		if (played){
			LinkedMovieManager.getActiveInstance().pauseLinkedMovies();
		}
		// The start point of the rotation remains the same during a drag,
		// because the
		// mouse should always point to the same Point on the Surface of the
		// sphere.
		this.currentRotationStartPoint = getVectorFromSphere(e.getPoint());
	}

	protected Vector3d getVectorFromSphere(Point p) {
		RayTrace rayTrace = new RayTrace();
		Ray ray = rayTrace.cast(p.x, p.y, compenentView);

		Vector3d hitPoint = ray.getHitpoint();
		return hitPoint;
	}

	public void setYAxisBlocked(boolean selected) {
		this.yAxisBlocked = selected;
	}
}
