package net.osmand.plus.track.cards;

import static net.osmand.plus.track.helpers.GpxSelectionHelper.isGpxFileSelected;
import static net.osmand.util.Algorithms.capitalizeFirstLetter;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import net.osmand.GPXUtilities.GPXFile;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.base.bottomsheetmenu.BaseBottomSheetItem;
import net.osmand.plus.base.bottomsheetmenu.BottomSheetItemWithCompoundButton;
import net.osmand.plus.base.bottomsheetmenu.BottomSheetItemWithDescriptionDifHeight;
import net.osmand.plus.base.bottomsheetmenu.SimpleBottomSheetItem;
import net.osmand.plus.base.bottomsheetmenu.simpleitems.DividerItem;
import net.osmand.plus.base.bottomsheetmenu.simpleitems.DividerSpaceItem;
import net.osmand.plus.helpers.FontCache;
import net.osmand.plus.plugins.OsmandPlugin;
import net.osmand.plus.plugins.osmedit.OsmEditingPlugin;
import net.osmand.plus.routepreparationmenu.cards.MapBaseCard;
import net.osmand.plus.track.helpers.SelectedGpxFile;
import net.osmand.plus.track.helpers.TrackDisplayHelper;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.utils.ColorUtilities;
import net.osmand.plus.utils.FileUtils;
import net.osmand.plus.utils.UiUtilities;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class OptionsCard extends MapBaseCard {

	public static final int SHOW_ON_MAP_BUTTON_INDEX = 0;
	public static final int APPEARANCE_BUTTON_INDEX = 1;
	public static final int DIRECTIONS_BUTTON_INDEX = 2;
	public static final int JOIN_GAPS_BUTTON_INDEX = 3;
	public static final int ANALYZE_ON_MAP_BUTTON_INDEX = 4;
	public static final int ANALYZE_BY_INTERVALS_BUTTON_INDEX = 5;
	public static final int SHARE_BUTTON_INDEX = 6;
	public static final int UPLOAD_OSM_BUTTON_INDEX = 7;
	public static final int EDIT_BUTTON_INDEX = 8;
	public static final int RENAME_BUTTON_INDEX = 9;
	public static final int CHANGE_FOLDER_BUTTON_INDEX = 10;
	public static final int GPS_FILTER_BUTTON_INDEX = 11;
	public static final int ALTITUDE_CORRECTION_BUTTON_INDEX = 12;
	public static final int DELETE_BUTTON_INDEX = 13;

	private final TrackDisplayHelper displayHelper;
	private final SelectedGpxFile selectedGpxFile;
	private final GPXFile gpxFile;
	private final List<BaseBottomSheetItem> items = new ArrayList<>();

	public OptionsCard(@NonNull MapActivity mapActivity, TrackDisplayHelper displayHelper, SelectedGpxFile selectedGpxFile) {
		super(mapActivity);
		this.displayHelper = displayHelper;
		this.gpxFile = displayHelper.getGpx();
		this.selectedGpxFile = selectedGpxFile;
	}

	@Override
	public int getCardLayoutId() {
		return R.layout.card_container;
	}

	@Override
	public void updateContent() {
		ViewGroup itemsContainer = (ViewGroup) view;
		itemsContainer.removeAllViews();
		items.clear();

		boolean fileAvailable = gpxFile.path != null && !gpxFile.showCurrentTrack;
		if (!FileUtils.isTempFile(app, gpxFile.path)) {
			items.add(createShowOnMapItem());
			items.add(createAppearanceItem());
			if (fileAvailable) {
				items.add(createDirectionsItem());
			}
			items.add(createDividerItem());
		}
		if (gpxFile.getGeneralTrack() != null) {
			items.add(createJoinGapsItem());
		}
		items.add(createAnalyzeOnMapItem());
		items.add(createAnalyzeByIntervalsItem());

		items.add(createDividerItem());
		items.add(createShareItem());

		if (fileAvailable) {
			BaseBottomSheetItem uploadOsmItem = createUploadOsmItem();
			if (uploadOsmItem != null) {
				items.add(uploadOsmItem);
			}

			items.add(createDividerItem());

			if (!FileUtils.isTempFile(app, gpxFile.path)) {
				items.add(createEditItem());
			}
			items.add(createRenameItem());
			items.add(createChangeFolderItem());

			items.add(createDividerItem());

			boolean plainTrack = gpxFile.hasTrkPt() && !gpxFile.hasRoute() && !gpxFile.hasRtePt();
			if (plainTrack) {
				items.add(createGpsFilterItem());
			}
			items.add(createAltitudeCorrectionItem());
			items.add(createDividerItem());
			items.add(createDeleteItem());
		}
		items.add(new DividerSpaceItem(mapActivity, AndroidUtils.dpToPx(app, 6)));

		inflateItems();
	}

	private BaseBottomSheetItem createDividerItem() {
		DividerItem dividerItem = new DividerItem(mapActivity);
		int start = getDimen(R.dimen.measurement_tool_options_divider_margin_start);
		int verticalMargin = AndroidUtils.dpToPx(app, 6);
		dividerItem.setMargins(start, verticalMargin, 0, verticalMargin);

		return dividerItem;
	}

	private BaseBottomSheetItem createShowOnMapItem() {

		final Drawable showIcon = getActiveIcon(R.drawable.ic_action_view);
		final Drawable hideIcon = getContentIcon(R.drawable.ic_action_hide);
		boolean gpxFileSelected = isGpxFileSelected(app, gpxFile);

		final BottomSheetItemWithCompoundButton[] showOnMapItem = new BottomSheetItemWithCompoundButton[1];
		showOnMapItem[0] = (BottomSheetItemWithCompoundButton) new BottomSheetItemWithCompoundButton.Builder()
				.setChecked(gpxFileSelected)
				.setIcon(gpxFileSelected ? showIcon : hideIcon)
				.setTitle(app.getString(R.string.shared_string_show_on_map))
				.setLayoutId(R.layout.bottom_sheet_item_with_switch_pad_32)
				.setOnClickListener(v -> {
					boolean checked = !showOnMapItem[0].isChecked();
					showOnMapItem[0].setChecked(checked);
					showOnMapItem[0].setIcon(checked ? showIcon : hideIcon);

					notifyButtonPressed(SHOW_ON_MAP_BUTTON_INDEX);
				})
				.create();
		return showOnMapItem[0];
	}

	private BaseBottomSheetItem createAppearanceItem() {
		return new SimpleBottomSheetItem.Builder()
				.setIcon(getActiveIcon(R.drawable.ic_action_appearance))
				.setTitle(app.getString(R.string.shared_string_appearance))
				.setLayoutId(R.layout.bottom_sheet_item_simple_pad_32dp)
				.setOnClickListener(v -> notifyButtonPressed(APPEARANCE_BUTTON_INDEX))
				.create();
	}

	private BaseBottomSheetItem createDirectionsItem() {
		return new SimpleBottomSheetItem.Builder()
				.setIcon(getActiveIcon(R.drawable.ic_action_gdirections_dark))
				.setTitle(app.getString(R.string.follow_track))
				.setLayoutId(R.layout.bottom_sheet_item_simple_pad_32dp)
				.setOnClickListener(v -> notifyButtonPressed(DIRECTIONS_BUTTON_INDEX))
				.create();
	}

	private BaseBottomSheetItem createJoinGapsItem() {
		final Drawable joinGapsEnabledIcon = getActiveIcon(R.drawable.ic_action_join_segments);
		final Drawable joinGapsDisabledIcon = getContentIcon(R.drawable.ic_action_join_segments);
		boolean joinSegments = displayHelper.isJoinSegments();

		final BottomSheetItemWithCompoundButton[] joinGapsItem = new BottomSheetItemWithCompoundButton[1];
		joinGapsItem[0] = (BottomSheetItemWithCompoundButton) new BottomSheetItemWithCompoundButton.Builder()
				.setChecked(joinSegments)
				.setIcon(joinSegments ? joinGapsEnabledIcon : joinGapsDisabledIcon)
				.setTitle(app.getString(R.string.gpx_join_gaps))
				.setLayoutId(R.layout.bottom_sheet_item_with_switch_pad_32)
				.setOnClickListener(v -> {
					boolean checked = !joinGapsItem[0].isChecked();
					joinGapsItem[0].setChecked(checked);
					joinGapsItem[0].setIcon(checked ? joinGapsEnabledIcon : joinGapsDisabledIcon);

					notifyButtonPressed(JOIN_GAPS_BUTTON_INDEX);
				})
				.create();
		return joinGapsItem[0];
	}

	private BaseBottomSheetItem createAnalyzeOnMapItem() {
		return new SimpleBottomSheetItem.Builder()
				.setIcon(getActiveIcon(R.drawable.ic_action_analyze_intervals))
				.setTitle(app.getString(R.string.analyze_on_map))
				.setLayoutId(R.layout.bottom_sheet_item_simple_pad_32dp)
				.setOnClickListener(v -> notifyButtonPressed(ANALYZE_ON_MAP_BUTTON_INDEX))
				.create();
	}

	private BaseBottomSheetItem createAnalyzeByIntervalsItem() {
		return new SimpleBottomSheetItem.Builder()
				.setIcon(getActiveIcon(R.drawable.ic_action_table))
				.setTitle(app.getString(R.string.analyze_by_intervals))
				.setLayoutId(R.layout.bottom_sheet_item_simple_pad_32dp)
				.setOnClickListener(v -> notifyButtonPressed(ANALYZE_BY_INTERVALS_BUTTON_INDEX))
				.create();
	}

	private BaseBottomSheetItem createShareItem() {
		Drawable shareIcon = getActiveIcon(R.drawable.ic_action_gshare_dark);
		return new SimpleBottomSheetItem.Builder()
				.setIcon(AndroidUtils.getDrawableForDirection(app, shareIcon))
				.setTitle(app.getString(R.string.shared_string_share))
				.setLayoutId(R.layout.bottom_sheet_item_simple_pad_32dp)
				.setOnClickListener(v -> notifyButtonPressed(SHARE_BUTTON_INDEX))
				.create();
	}

	private BaseBottomSheetItem createUploadOsmItem() {
		OsmEditingPlugin osmEditingPlugin = OsmandPlugin.getActivePlugin(OsmEditingPlugin.class);
		if (osmEditingPlugin != null && selectedGpxFile.getTrackAnalysis(app).isTimeMoving()) {
			return new SimpleBottomSheetItem.Builder()
					.setIcon(getActiveIcon(R.drawable.ic_action_upload_to_openstreetmap))
					.setTitle(app.getString(R.string.upload_to_openstreetmap))
					.setLayoutId(R.layout.bottom_sheet_item_simple_pad_32dp)
					.setOnClickListener(v -> notifyButtonPressed(UPLOAD_OSM_BUTTON_INDEX))
					.create();
		}
		return null;
	}

	private BaseBottomSheetItem createEditItem() {
		Drawable editIcon = getActiveIcon(R.drawable.ic_action_edit_track);
		return new SimpleBottomSheetItem.Builder()
				.setIcon(AndroidUtils.getDrawableForDirection(app, editIcon))
				.setTitle(app.getString(R.string.edit_track))
				.setLayoutId(R.layout.bottom_sheet_item_simple_pad_32dp)
				.setOnClickListener(v -> notifyButtonPressed(EDIT_BUTTON_INDEX))
				.create();
	}

	private BaseBottomSheetItem createRenameItem() {
		Drawable renameIcon = getActiveIcon(R.drawable.ic_action_name_field);
		return new SimpleBottomSheetItem.Builder()
				.setIcon(AndroidUtils.getDrawableForDirection(app, renameIcon))
				.setTitle(app.getString(R.string.rename_track))
				.setLayoutId(R.layout.bottom_sheet_item_simple_pad_32dp)
				.setOnClickListener(v -> notifyButtonPressed(RENAME_BUTTON_INDEX))
				.create();
	}

	private BaseBottomSheetItem createChangeFolderItem() {
		File file = new File(gpxFile.path).getParentFile();
		String folder = file != null ? file.getName() : null;
		Drawable changeFolderIcon = getActiveIcon(R.drawable.ic_action_folder_move);

		return new BottomSheetItemWithDescriptionDifHeight.Builder()
				.setMinHeight(getDimen(R.dimen.setting_list_item_group_height))
				.setDescriptionColorId(ColorUtilities.getSecondaryTextColorId(nightMode))
				.setDescription(capitalizeFirstLetter(folder))
				.setIcon(AndroidUtils.getDrawableForDirection(app, changeFolderIcon))
				.setTitle(app.getString(R.string.change_folder))
				.setLayoutId(R.layout.bottom_sheet_item_with_descr_pad_32dp)
				.setOnClickListener(v -> notifyButtonPressed(CHANGE_FOLDER_BUTTON_INDEX))
				.create();
	}

	private BaseBottomSheetItem createGpsFilterItem() {
		Drawable gpxFilterIcon = getActiveIcon(R.drawable.ic_action_filter);
		return new SimpleBottomSheetItem.Builder()
				.setIcon(AndroidUtils.getDrawableForDirection(app, gpxFilterIcon))
				.setTitle(app.getString(R.string.shared_string_gps_filter))
				.setLayoutId(R.layout.bottom_sheet_item_simple_pad_32dp)
				.setOnClickListener(v -> notifyButtonPressed(GPS_FILTER_BUTTON_INDEX))
				.create();
	}

	private BaseBottomSheetItem createAltitudeCorrectionItem() {
		Drawable altitudeCorrectionIcon = getActiveIcon(R.drawable.ic_action_altitude_average);
		return new SimpleBottomSheetItem.Builder()
				.setIcon(AndroidUtils.getDrawableForDirection(app, altitudeCorrectionIcon))
				.setTitle(app.getString(R.string.altitude_correction))
				.setLayoutId(R.layout.bottom_sheet_item_simple_pad_32dp)
				.setOnClickListener(v -> notifyButtonPressed(ALTITUDE_CORRECTION_BUTTON_INDEX))
				.create();
	}

	private BaseBottomSheetItem createDeleteItem() {
		String delete = app.getString(R.string.shared_string_delete);
		Typeface typeface = FontCache.getRobotoMedium(app);
		return new SimpleBottomSheetItem.Builder()
				.setTitleColorId(R.color.color_osm_edit_delete)
				.setIcon(getColoredIcon(R.drawable.ic_action_delete_dark, R.color.color_osm_edit_delete))
				.setTitle(UiUtilities.createCustomFontSpannable(typeface, delete, delete))
				.setLayoutId(R.layout.bottom_sheet_item_simple_pad_32dp)
				.setOnClickListener(v -> notifyButtonPressed(DELETE_BUTTON_INDEX))
				.create();
	}

	private void inflateItems() {
		for (BaseBottomSheetItem item : items) {
			item.inflate(mapActivity, (ViewGroup) view, nightMode);
		}
	}
}