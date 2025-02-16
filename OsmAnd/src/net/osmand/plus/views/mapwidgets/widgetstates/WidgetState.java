package net.osmand.plus.views.mapwidgets.widgetstates;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.settings.backend.OsmandSettings;

import androidx.annotation.NonNull;

public abstract class WidgetState {

	protected final OsmandApplication app;
	protected final OsmandSettings settings;

	public WidgetState(@NonNull OsmandApplication app) {
		this.app = app;
		this.settings = app.getSettings();
	}

	@NonNull
	public OsmandApplication getApp() {
		return app;
	}

	@NonNull
	public abstract String getTitle();

	public abstract int getSettingsIconId(boolean nightMode);

	public abstract void changeToNextState();
}