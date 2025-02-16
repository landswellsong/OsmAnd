package net.osmand.plus.myplaces;

import android.os.AsyncTask;

import net.osmand.GPXUtilities;
import net.osmand.GPXUtilities.GPXFile;
import net.osmand.plus.track.helpers.GpxDisplayItem;
import net.osmand.plus.track.helpers.GpxSelectionHelper.GpxDisplayItemType;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.track.helpers.SavingTrackHelper;
import net.osmand.plus.mapmarkers.MapMarkersGroup;
import net.osmand.plus.mapmarkers.MapMarkersHelper;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Set;

public class DeletePointsTask extends AsyncTask<Void, Void, Void> {

	private OsmandApplication app;
	private GPXFile gpx;
	private Set<GpxDisplayItem> selectedItems;
	private WeakReference<OnPointsDeleteListener> listenerRef;

	public DeletePointsTask(OsmandApplication app, GPXFile gpxFile, Set<GpxDisplayItem> selectedItems, OnPointsDeleteListener listener) {
		this.app = app;
		this.gpx = gpxFile;
		this.selectedItems = selectedItems;
		this.listenerRef = new WeakReference<>(listener);
	}

	@Override
	protected void onPreExecute() {
		OnPointsDeleteListener listener = listenerRef.get();
		if (listener != null) {
			listener.onPointsDeletionStarted();
		}
	}

	@Override
	protected Void doInBackground(Void... params) {
		SavingTrackHelper savingTrackHelper = app.getSavingTrackHelper();
		if (gpx != null) {
			for (GpxDisplayItem item : selectedItems) {
				if (gpx.showCurrentTrack) {
					savingTrackHelper.deletePointData(item.locationStart);
				} else {
					if (item.group.getType() == GpxDisplayItemType.TRACK_POINTS) {
						gpx.deleteWptPt(item.locationStart);
					} else if (item.group.getType() == GpxDisplayItemType.TRACK_ROUTE_POINTS) {
						gpx.deleteRtePt(item.locationStart);
					}
				}
			}
			if (!gpx.showCurrentTrack) {
				GPXUtilities.writeGpxFile(new File(gpx.path), gpx);
				boolean selected = app.getSelectedGpxHelper().getSelectedFileByPath(gpx.path) != null;
				if (selected) {
					app.getSelectedGpxHelper().setGpxFileToDisplay(gpx);
				}
			}
			syncGpx(gpx);
		}
		return null;
	}

	private void syncGpx(GPXFile gpxFile) {
		MapMarkersHelper helper = app.getMapMarkersHelper();
		MapMarkersGroup group = helper.getMarkersGroup(gpxFile);
		if (group != null) {
			helper.runSynchronization(group);
		}
	}

	@Override
	protected void onPostExecute(Void aVoid) {
		OnPointsDeleteListener listener = listenerRef.get();
		if (listener != null) {
			listener.onPointsDeleted();
		}
	}

	public interface OnPointsDeleteListener {
		void onPointsDeletionStarted();

		void onPointsDeleted();
	}
}