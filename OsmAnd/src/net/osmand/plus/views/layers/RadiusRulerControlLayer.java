package net.osmand.plus.views.layers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.graphics.PointF;

import net.osmand.core.android.MapRendererView;
import net.osmand.core.jni.PointI;
import net.osmand.data.LatLon;
import net.osmand.data.QuadPoint;
import net.osmand.data.RotatedTileBox;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.settings.enums.AngularConstants;
import net.osmand.plus.settings.enums.MetricsConstants;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.utils.NativeUtilities;
import net.osmand.plus.utils.OsmAndFormatter;
import net.osmand.plus.views.OsmandMapTileView;
import net.osmand.plus.views.layers.base.OsmandMapLayer;
import net.osmand.plus.views.mapwidgets.MapWidgetRegistry;
import net.osmand.util.MapUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import static net.osmand.plus.views.mapwidgets.WidgetType.RADIUS_RULER;

public class RadiusRulerControlLayer extends OsmandMapLayer {

	private static final int TEXT_SIZE = 14;
	private static final float COMPASS_CIRCLE_FITTING_RADIUS_COEF = 1.25f;
	private static final float CIRCLE_ANGLE_STEP = 5;
	private static final int SHOW_COMPASS_MIN_ZOOM = 8;

	private OsmandApplication app;
	private View rightWidgetsPanel;

	private TextSide textSide;
	private int maxRadiusInDp;
	private float maxRadius;
	private int radius;
	private double roundedDist;

	private QuadPoint cacheCenter;
	private float cacheMapDensity;
	private MetricsConstants cacheMetricSystem;
	private int cacheIntZoom;
	private LatLon cacheCenterLatLon;
	private ArrayList<String> cacheDistances;

	private Bitmap centerIconDay;
	private Bitmap centerIconNight;
	private Paint bitmapPaint;
	private Paint triangleHeadingPaint;
	private Paint triangleNorthPaint;
	private Paint redLinesPaint;
	private Paint blueLinesPaint;

	private RenderingLineAttributes circleAttrs;
	private RenderingLineAttributes circleAttrsAlt;

	private final Path compass = new Path();
	private final Path arrow = new Path();
	private final Path arrowArc = new Path();
	private final Path redCompassLines = new Path();

	private final double[] degrees = new double[72];
	private final String[] cardinalDirections = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

	private final int[] arcColors = {Color.parseColor("#00237BFF"), Color.parseColor("#237BFF"), Color.parseColor("#00237BFF")};

	private float cachedHeading = 0;

	public RadiusRulerControlLayer(@NonNull Context ctx) {
		super(ctx);
	}

	@Override
	public void initLayer(@NonNull final OsmandMapTileView view) {
		super.initLayer(view);

		app = getApplication();
		cacheMetricSystem = app.getSettings().METRIC_SYSTEM.get();
		cacheMapDensity = getMapDensity();
		cacheDistances = new ArrayList<>();
		cacheCenter = new QuadPoint();
		maxRadiusInDp = app.getResources().getDimensionPixelSize(R.dimen.map_ruler_width);

		centerIconDay = BitmapFactory.decodeResource(view.getResources(), R.drawable.map_ruler_center_day);
		centerIconNight = BitmapFactory.decodeResource(view.getResources(), R.drawable.map_ruler_center_night);

		bitmapPaint = new Paint();
		bitmapPaint.setAntiAlias(true);
		bitmapPaint.setDither(true);
		bitmapPaint.setFilterBitmap(true);

		int colorNorthArrow = ContextCompat.getColor(app, R.color.compass_control_active);
		int colorHeadingArrow = ContextCompat.getColor(app, R.color.active_color_primary_light);

		triangleNorthPaint = initPaintWithStyle(Style.FILL, colorNorthArrow);
		triangleHeadingPaint = initPaintWithStyle(Style.FILL, colorHeadingArrow);
		redLinesPaint = initPaintWithStyle(Style.STROKE, colorNorthArrow);
		blueLinesPaint = initPaintWithStyle(Style.STROKE, colorHeadingArrow);

		float circleTextSize = TEXT_SIZE * app.getResources().getDisplayMetrics().density;

		circleAttrs = new RenderingLineAttributes("rulerCircle");
		circleAttrs.paint2.setTextSize(circleTextSize);
		circleAttrs.paint3.setTextSize(circleTextSize);

		circleAttrsAlt = new RenderingLineAttributes("rulerCircleAlt");
		circleAttrsAlt.paint2.setTextSize(circleTextSize);
		circleAttrsAlt.paint3.setTextSize(circleTextSize);

		for (int i = 0; i < 72; i++) {
			degrees[i] = Math.toRadians(i * 5);
		}
	}

	@Override
	public void setMapActivity(@Nullable MapActivity mapActivity) {
		super.setMapActivity(mapActivity);
		if (mapActivity != null) {
			rightWidgetsPanel = mapActivity.findViewById(R.id.map_right_widgets_panel);
		} else {
			rightWidgetsPanel = null;
		}
	}

	private Paint initPaintWithStyle(Paint.Style style, int color) {
		Paint paint = new Paint();
		paint.setStyle(style);
		paint.setColor(color);
		paint.setAntiAlias(true);
		return paint;
	}

	@Override
	public void onDraw(Canvas canvas, RotatedTileBox tb, DrawSettings drawSettings) {
		if (rulerModeOn() && !tb.isZoomAnimated()) {
			OsmandApplication app = view.getApplication();
			OsmandSettings settings = app.getSettings();
			circleAttrs.updatePaints(app, drawSettings, tb);
			circleAttrs.paint2.setStyle(Style.FILL);
			circleAttrsAlt.updatePaints(app, drawSettings, tb);
			circleAttrsAlt.paint2.setStyle(Style.FILL);
			final QuadPoint center = getCenterPoint(tb);
			canvas.rotate(-tb.getRotate(), center.x, center.y);

			RadiusRulerMode radiusRulerMode = settings.RADIUS_RULER_MODE.get();
			boolean showRadiusRuler = radiusRulerMode == RadiusRulerMode.FIRST || radiusRulerMode == RadiusRulerMode.SECOND;
			boolean showCompass = settings.SHOW_COMPASS_ON_RADIUS_RULER.get() && tb.getZoom() >= SHOW_COMPASS_MIN_ZOOM;

			boolean radiusRulerNightMode = radiusRulerMode == RadiusRulerMode.SECOND;
			drawCenterIcon(canvas, tb, center, drawSettings.isNightMode(), radiusRulerNightMode);

			if (showRadiusRuler) {
				updateData(tb, center);
				if (showCompass) {
					updateHeading();
					resetDrawingPaths();
				}

				RenderingLineAttributes attrs = radiusRulerNightMode ? circleAttrsAlt : circleAttrs;
				int compassCircleIndex = getCompassCircleIndex(tb, center);
				for (int circleIndex = 1; circleIndex <= cacheDistances.size(); circleIndex++) {
					if (showCompass && circleIndex == compassCircleIndex) {
						drawCompassCircle(canvas, tb, compassCircleIndex, center, attrs);
					} else {
						drawRulerCircle(canvas, tb, circleIndex, center, attrs);
					}
				}
			}
			canvas.rotate(tb.getRotate(), center.x, center.y);
		}
	}

	public boolean rulerModeOn() {
		MapWidgetRegistry mapWidgetRegistry = getApplication().getOsmandMap().getMapLayers().getMapWidgetRegistry();
		return mapWidgetRegistry.isWidgetVisible(RADIUS_RULER.id)
				&& (rightWidgetsPanel == null || rightWidgetsPanel.getVisibility() == View.VISIBLE);
	}

	private int getCompassCircleIndex(RotatedTileBox tb, QuadPoint center) {
		int compassCircleIndex = 2;
		float radiusLength = radius * compassCircleIndex;
		float top = center.y - radiusLength;
		float bottom = center.y + radiusLength;
		float left = center.x - radiusLength;
		float right = center.x + radiusLength;
		int width = tb.getPixWidth();
		int height = tb.getPixHeight();

		if (top < 0) {
			top = 0;
		}
		if (bottom > height) {
			bottom = height;
		}
		if (left < 0) {
			left = 0;
		}
		if (right > width) {
			right = width;
		}
		int horizontal = (int) (bottom - top) / 2;
		int vertical = (int) (right - left) / 2;
		int minFittingRadius = Math.min(horizontal, vertical);
		if (radiusLength > minFittingRadius * COMPASS_CIRCLE_FITTING_RADIUS_COEF) {
			compassCircleIndex = 1;
		}

		return compassCircleIndex;
	}

	private void updateHeading() {
		Float heading = getApplication().getMapViewTrackingUtilities().getHeading();
		if (heading != null && heading != cachedHeading) {
			cachedHeading = heading;
		}
	}

	private void resetDrawingPaths() {
		redCompassLines.reset();
		arrowArc.reset();
		compass.reset();
		arrow.reset();
	}

	private void drawCenterIcon(Canvas canvas, RotatedTileBox tb, QuadPoint center,
								boolean nightMode, boolean radiusRulerNightMode) {
		if (nightMode || radiusRulerNightMode) {
			canvas.drawBitmap(centerIconNight, center.x - centerIconNight.getWidth() / 2f,
					center.y - centerIconNight.getHeight() / 2f, bitmapPaint);
		} else {
			canvas.drawBitmap(centerIconDay, center.x - centerIconDay.getWidth() / 2f,
					center.y - centerIconDay.getHeight() / 2f, bitmapPaint);
		}
	}

	private void updateData(RotatedTileBox tb, QuadPoint center) {
		if (tb.getPixHeight() > 0 && tb.getPixWidth() > 0 && maxRadiusInDp > 0
				&& !Double.isNaN(tb.getLatitude()) && !Double.isNaN(tb.getLongitude())) {
			if (cacheCenter.y != center.y || cacheCenter.x != center.x) {
				cacheCenter = center;
				updateCenter(tb, center);
			}

			MetricsConstants currentMetricSystem = app.getSettings().METRIC_SYSTEM.get();
			float mapDensity = getMapDensity();
			boolean updateCache = tb.getZoom() != cacheIntZoom
					|| !tb.getCenterLatLon().equals(cacheCenterLatLon) || mapDensity != cacheMapDensity
					|| cacheMetricSystem != currentMetricSystem;

			if (!tb.isZoomAnimated() && updateCache) {
				cacheMetricSystem = currentMetricSystem;
				cacheIntZoom = tb.getZoom();
				LatLon centerLatLon = tb.getCenterLatLon();
				cacheCenterLatLon = new LatLon(centerLatLon.getLatitude(), centerLatLon.getLongitude());
				cacheMapDensity = mapDensity;
				updateDistance(tb);
			}
		}
	}

	private void updateCenter(RotatedTileBox tb, QuadPoint center) {
		float topDist = center.y;
		float bottomDist = tb.getPixHeight() - center.y;
		float leftDist = center.x;
		float rightDist = tb.getPixWidth() - center.x;
		float maxVertical = Math.max(topDist, bottomDist);
		float maxHorizontal = Math.max(rightDist, leftDist);

		if (maxVertical >= maxHorizontal) {
			maxRadius = maxVertical;
			textSide = TextSide.VERTICAL;
		} else {
			maxRadius = maxHorizontal;
			textSide = TextSide.HORIZONTAL;
		}
		if (radius != 0) {
			updateText();
		}
	}

	private void updateDistance(RotatedTileBox tb) {
		double pixDensity = tb.getPixDensity();
		roundedDist = OsmAndFormatter.calculateRoundedDist(maxRadiusInDp / pixDensity, app);
		radius = (int) (pixDensity * roundedDist);
		updateText();
	}

	private void updateText() {
		cacheDistances.clear();
		double maxCircleRadius = maxRadius;
		int i = 1;
		while ((maxCircleRadius -= radius) > 0) {
			cacheDistances.add(OsmAndFormatter.getFormattedDistance((float) roundedDist * i++, app, false));
		}
	}

	private void drawRulerCircle(Canvas canvas, RotatedTileBox tb, int circleNumber, QuadPoint center, RenderingLineAttributes attrs) {
		drawCircle(canvas, tb, circleNumber, center, attrs);

		String text = cacheDistances.get(circleNumber - 1);
		float circleRadius = radius * circleNumber;
		float[] textCoords = calculateTextCoords(text, text, circleRadius, center, tb, attrs);
		drawTextCoords(canvas, text, textCoords, attrs);
	}

	private void drawCircle(Canvas canvas, RotatedTileBox tb, int circleNumber, QuadPoint center,
							RenderingLineAttributes attrs) {
		float circleRadius = radius * circleNumber;
		List<List<QuadPoint>> arrays = new ArrayList<>();
		List<QuadPoint> points = new ArrayList<>();
		LatLon centerLatLon = getCenterLatLon(tb);
		for (int a = -180; a <= 180; a += CIRCLE_ANGLE_STEP) {
			LatLon latLon = MapUtils.rhumbDestinationPoint(centerLatLon, circleRadius / tb.getPixDensity(), a);
			if (Math.abs(latLon.getLatitude()) > 90 || Math.abs(latLon.getLongitude()) > 180) {
				if (points.size() > 0) {
					arrays.add(points);
					points = new ArrayList<>();
				}
				continue;
			}

			PointF screenPoint = latLonToScreenPoint(latLon, tb);
			points.add(new QuadPoint(screenPoint.x, screenPoint.y));
		}
		if (points.size() > 0) {
			arrays.add(points);
		}

		for (List<QuadPoint> pts : arrays) {
			Path path = new Path();
			for (QuadPoint pt : pts) {
				if (path.isEmpty()) {
					path.moveTo(pt.x, pt.y);
				} else {
					path.lineTo(pt.x, pt.y);
				}
			}
			canvas.drawPath(path, attrs.shadowPaint);
			canvas.drawPath(path, attrs.paint);
		}
	}

	private void drawTextCoords(Canvas canvas, String text, float[] textCoords, RenderingLineAttributes attrs) {
		if (!Float.isNaN(textCoords[0]) && !Float.isNaN(textCoords[1])) {
			canvas.drawText(text, textCoords[0], textCoords[1], attrs.paint3);
			canvas.drawText(text, textCoords[0], textCoords[1], attrs.paint2);
		}
		if (!Float.isNaN(textCoords[2]) && !Float.isNaN(textCoords[3])) {
			canvas.drawText(text, textCoords[2], textCoords[3], attrs.paint3);
			canvas.drawText(text, textCoords[2], textCoords[3], attrs.paint2);
		}
	}

	private float[] calculateTextCoords(String topOrLeftText, String rightOrBottomText,
										@Nullable QuadPoint topOrLeftPoint, @Nullable QuadPoint rightOrBottomPoint,
										RenderingLineAttributes attrs) {
		Rect boundsDistance = new Rect();
		Rect boundsHeading;

		if (topOrLeftText.equals(rightOrBottomText)) {
			boundsHeading = boundsDistance;
		} else {
			boundsHeading = new Rect();
			attrs.paint2.getTextBounds(rightOrBottomText, 0, rightOrBottomText.length(), boundsHeading);
		}
		attrs.paint2.getTextBounds(topOrLeftText, 0, topOrLeftText.length(), boundsDistance);

		float x1 = topOrLeftPoint == null ? Float.NaN : topOrLeftPoint.x - boundsHeading.width() / 2f;
		float y1 = topOrLeftPoint == null ? Float.NaN : topOrLeftPoint.y + boundsHeading.height() / 2f;
		float x2 = rightOrBottomPoint == null ? Float.NaN : rightOrBottomPoint.x - boundsDistance.width() / 2f;
		float y2 = rightOrBottomPoint == null ? Float.NaN : rightOrBottomPoint.y + boundsDistance.height() / 2f;
		return new float[]{x1, y1, x2, y2};
	}

	private float[] calculateTextCoords(String topOrLeftText, String rightOrBottomText, float drawingTextRadius, QuadPoint center, RotatedTileBox tb, RenderingLineAttributes attrs) {
		Rect boundsDistance = new Rect();
		Rect boundsHeading;

		if (topOrLeftText.equals(rightOrBottomText)) {
			boundsHeading = boundsDistance;
		} else {
			boundsHeading = new Rect();
			attrs.paint2.getTextBounds(rightOrBottomText, 0, rightOrBottomText.length(), boundsHeading);
		}
		attrs.paint2.getTextBounds(topOrLeftText, 0, topOrLeftText.length(), boundsDistance);

		// coords of left or top text
		float x1 = 0;
		float y1 = 0;
		// coords of right or bottom text
		float x2 = 0;
		float y2 = 0;

		if (textSide == TextSide.VERTICAL) {
			x1 = center.x - boundsHeading.width() / 2f;
			y1 = center.y - drawingTextRadius + boundsHeading.height() / 2f;
			x2 = center.x - boundsDistance.width() / 2f;
			y2 = center.y + drawingTextRadius + boundsDistance.height() / 2f;
		} else if (textSide == TextSide.HORIZONTAL) {
			x1 = center.x - drawingTextRadius - boundsHeading.width() / 2f;
			y1 = center.y + boundsHeading.height() / 2f;
			x2 = center.x + drawingTextRadius - boundsDistance.width() / 2f;
			y2 = center.y + boundsDistance.height() / 2f;
		}
		PointF topOrLeftPoint = screenPointFromPoint(x1, y1, true, tb);
		PointF rightOrBottomPoint = screenPointFromPoint(x2, y2, true, tb);
		return new float[]{topOrLeftPoint.x, topOrLeftPoint.y, rightOrBottomPoint.x, rightOrBottomPoint.y};
	}

	private void drawCompassCircle(Canvas canvas, RotatedTileBox tb, int circleNumber,
								   QuadPoint center, RenderingLineAttributes attrs) {
		float radiusLength = radius * circleNumber;
		float innerRadiusLength = radiusLength - attrs.paint.getStrokeWidth() / 2;
		QuadPoint centerPixels = getCenterPoint(tb);

		drawCircle(canvas, tb, circleNumber, center, attrs);
		drawCompassCents(centerPixels, innerRadiusLength, radiusLength, tb, canvas, attrs);
		drawCardinalDirections(canvas, center, radiusLength, tb, attrs);
		drawLightingHeadingArc(radiusLength, cachedHeading, center, tb, canvas, attrs);
		drawTriangleArrowByRadius(radiusLength, 0, center, attrs.shadowPaint, triangleNorthPaint, tb, canvas);
		drawTriangleArrowByRadius(radiusLength, cachedHeading, center, attrs.shadowPaint, triangleHeadingPaint, tb, canvas);
		drawCompassCircleText(canvas, tb, circleNumber, radiusLength, center, attrs);
	}

	private void drawCompassCircleText(Canvas canvas, RotatedTileBox tb, int circleNumber, float radiusLength,
									   QuadPoint center, RenderingLineAttributes attrs) {
		String distance = cacheDistances.get(circleNumber - 1);
		String heading = OsmAndFormatter.getFormattedAzimuth(cachedHeading, AngularConstants.DEGREES360) + " " + getCardinalDirectionForDegrees(cachedHeading);

		float offset = (textSide == TextSide.HORIZONTAL) ? 15 : 20;
		float drawingTextRadius = radiusLength + AndroidUtils.dpToPx(app, offset);
		float[] textCoords = calculateTextCoords(distance, heading, drawingTextRadius, center, tb, attrs);

		setAttrsPaintsTypeface(attrs, Typeface.DEFAULT_BOLD);
		canvas.drawText(heading, textCoords[0], textCoords[1], attrs.paint3);
		canvas.drawText(heading, textCoords[0], textCoords[1], attrs.paint2);

		setAttrsPaintsTypeface(attrs, null);
		canvas.drawText(distance, textCoords[2], textCoords[3], attrs.paint3);
		canvas.drawText(distance, textCoords[2], textCoords[3], attrs.paint2);
	}

	private void drawTriangleArrowByRadius(double radius, double angle, QuadPoint center, Paint shadowPaint, Paint colorPaint, RotatedTileBox tb, Canvas canvas) {
		double headOffsesFromRadius = AndroidUtils.dpToPx(app, 9);
		double triangleSideLength = AndroidUtils.dpToPx(app, 12);
		double triangleHeadAngle = 60;
		double zeroAngle = angle - 90 + (hasMapRenderer() ? 0 : tb.getRotate());

		double radians = Math.toRadians(zeroAngle);
		double firstPointX = center.x + Math.cos(radians) * (radius + headOffsesFromRadius);
		double firstPointY = center.y + Math.sin(radians) * (radius + headOffsesFromRadius);
		PointF firstScreenPoint = screenPointFromPoint(firstPointX, firstPointY, false, tb);

		double radians2 = Math.toRadians(zeroAngle + triangleHeadAngle / 2 + 180);
		double secondPointX = firstPointX + Math.cos(radians2) * triangleSideLength;
		double secondPointY = firstPointY + Math.sin(radians2) * triangleSideLength;
		PointF secondScreenPoint = screenPointFromPoint(secondPointX, secondPointY, false, tb);

		double radians3 = Math.toRadians(zeroAngle - triangleHeadAngle / 2 + 180);
		double thirdPointX = firstPointX + Math.cos(radians3) * triangleSideLength;
		double thirdPointY = firstPointY + Math.sin(radians3) * triangleSideLength;
		PointF thirdScreenPoint = screenPointFromPoint(thirdPointX, thirdPointY, false, tb);

		arrow.reset();
		arrow.moveTo(firstScreenPoint.x, firstScreenPoint.y);
		arrow.lineTo(secondScreenPoint.x, secondScreenPoint.y);
		arrow.lineTo(thirdScreenPoint.x, thirdScreenPoint.y);
		arrow.lineTo(firstScreenPoint.x, firstScreenPoint.y);
		arrow.close();
		canvas.drawPath(arrow, shadowPaint);
		canvas.drawPath(arrow, colorPaint);
	}

	private void drawLightingHeadingArc(double radius, double angle, QuadPoint center, RotatedTileBox tb, Canvas canvas, RenderingLineAttributes attrs) {
		PointF gradientArcStartPoint = getPointFromCenterByRadius(radius, (angle - 30), tb);
		PointF gradientArcEndPoint = getPointFromCenterByRadius(radius, (angle + 30), tb);
		LinearGradient shader = new LinearGradient(gradientArcStartPoint.x, gradientArcStartPoint.y, gradientArcEndPoint.x, gradientArcEndPoint.y, arcColors, null, Shader.TileMode.CLAMP);
		blueLinesPaint.setShader(shader);
		blueLinesPaint.setStrokeWidth(attrs.paint.getStrokeWidth());

		arrowArc.reset();
		int startArcAngle = (int)angle - 45;
		int endArcAngle = (int)angle + 45;
		List<List<QuadPoint>> arrays = new ArrayList<>();
		List<QuadPoint> points = new ArrayList<>();
		LatLon centerLatLon = getCenterLatLon(tb);
		for (int a = startArcAngle; a <= endArcAngle; a += CIRCLE_ANGLE_STEP) {
			LatLon latLon = MapUtils.rhumbDestinationPoint(centerLatLon, radius / tb.getPixDensity(), a);
			PointF screenPoint = latLonToScreenPoint(latLon, tb);
			if (arrowArc.isEmpty()) {
				arrowArc.moveTo(screenPoint.x, screenPoint.y);
			} else {
				arrowArc.lineTo(screenPoint.x, screenPoint.y);
			}
		}
		canvas.drawPath(arrowArc, blueLinesPaint);
	}

	private void drawCompassCents(QuadPoint center, float innerRadiusLength, float radiusLength, RotatedTileBox tb, Canvas canvas, RenderingLineAttributes attrs) {
		for (int i = 0; i < degrees.length; i++) {
			double degree = degrees[i] + (hasMapRenderer() ? 0 : tb.getRotate());
			float x = (float) Math.cos(degree);
			float y = -(float) Math.sin(degree);

			float lineStartX = center.x + x * innerRadiusLength;
			float lineStartY = center.y + y * innerRadiusLength;

			float lineLength = getCompassLineHeight(i);

			float lineStopX = center.x + x * (innerRadiusLength - lineLength);
			float lineStopY = center.y + y * (innerRadiusLength - lineLength);

			if (i == 18) {
				float shortLineMargin = AndroidUtils.dpToPx(app, 5.66f);
				float shortLineHeight = AndroidUtils.dpToPx(app, 2.94f);
				float startY = center.y + y * (radiusLength - shortLineMargin);
				float stopY = center.y + y * (radiusLength - shortLineMargin - shortLineHeight);

				PointF startScreenPoint = screenPointFromPoint(center.x, startY, false, tb);
				PointF stopScreenPoint = screenPointFromPoint(center.x, stopY, false, tb);
				compass.moveTo(startScreenPoint.x, startScreenPoint.y);
				compass.lineTo(stopScreenPoint.x, stopScreenPoint.y);
			} else {
				PointF startScreenPoint = screenPointFromPoint(lineStartX, lineStartY, false, tb);
				PointF stopScreenPoint = screenPointFromPoint(lineStopX, lineStopY, false, tb);
				compass.moveTo(startScreenPoint.x, startScreenPoint.y);
				compass.lineTo(stopScreenPoint.x, stopScreenPoint.y);
			}
			if (i % 9 == 0 && i != 18) {
				PointF startScreenPoint = screenPointFromPoint(lineStartX, lineStartY, false, tb);
				PointF stopScreenPoint = screenPointFromPoint(lineStopX, lineStopY, false, tb);
				redCompassLines.moveTo(startScreenPoint.x, startScreenPoint.y);
				redCompassLines.lineTo(stopScreenPoint.x, stopScreenPoint.y);
			}
		}
		redLinesPaint.setStrokeWidth(attrs.paint.getStrokeWidth());
		canvas.drawPath(compass, attrs.shadowPaint);
		canvas.drawPath(compass, attrs.paint);
		canvas.drawPath(redCompassLines, redLinesPaint);
	}

	private QuadPoint getCenterPoint(RotatedTileBox tb) {
		if (hasMapRenderer()) {
			PointF centerPixels;
			if (tb.isCenterShifted()) {
				PointI windowSize = getMapRenderer().getState().getWindowSize();
				int sx = windowSize.getX() / 2;
				int sy = windowSize.getY() / 2;
				PointI center31 = NativeUtilities.get31FromPixel(getMapRenderer(), tb, sx, sy, true);
				if (center31 != null) {
					centerPixels = NativeUtilities.getPixelFrom31(getMapRenderer(), tb, center31);
					return new QuadPoint(centerPixels.x, centerPixels.y);
				}
			}

			PointI center31 = getMapRenderer().getState().getTarget31();
			centerPixels = NativeUtilities.getPixelFrom31(getMapRenderer(), tb, center31);
			return new QuadPoint(centerPixels.x, centerPixels.y);
		} else {
			return tb.getCenterPixelPoint();
		}
	}

	private LatLon getCenterLatLon(RotatedTileBox tb) {
		if (hasMapRenderer()) {
			PointI center31;
			if (tb.isCenterShifted()) {
				PointI windowSize = getMapRenderer().getState().getWindowSize();
				int sx = windowSize.getX() / 2;
				int sy = windowSize.getY() / 2;
				center31 = NativeUtilities.get31FromPixel(getMapRenderer(), tb, sx, sy, true);
				if (center31 != null) {
					return point31ToLatLon(center31);
				}
			}

			center31 = getMapRenderer().getState().getTarget31();
			return point31ToLatLon(center31);
		} else {
			return tb.getCenterLatLon();
		}
	}

	private PointF screenPointFromPoint(double x, double y, boolean compensateMapRotation, RotatedTileBox tb) {
		if (hasMapRenderer()) {
			QuadPoint circleCenterPoint = getCenterPoint(tb);
			double dX = circleCenterPoint.x - x;
			double dY = circleCenterPoint.y - y;
			double distanceFromCenter = Math.sqrt(dX * dX + dY * dY);
			double angleFromCenter = Math.toDegrees(Math.atan2(dY, dX)) - 90;
			angleFromCenter = compensateMapRotation ? angleFromCenter - tb.getRotate() : angleFromCenter; //??
			return getPointFromCenterByRadius(distanceFromCenter, angleFromCenter, tb);
		} else {
			return new PointF((float)x, (float)y);
		}
	}

	private PointF getPointFromCenterByRadius(double radius, double angle, RotatedTileBox tb) {
		LatLon centerLatLon = getCenterLatLon(tb);
		LatLon latLon = MapUtils.rhumbDestinationPoint(centerLatLon, radius / tb.getPixDensity(), angle);
		return NativeUtilities.getPixelFromLatLon(getMapRenderer(), tb, latLon.getLatitude(), latLon.getLongitude());
	}

	private PointF latLonToScreenPoint(LatLon latLon, RotatedTileBox tb) {
		return NativeUtilities.getPixelFromLatLon(getMapRenderer(), tb, latLon.getLatitude(), latLon.getLongitude());
	}

	private LatLon point31ToLatLon(PointI point31) {
		double lon = MapUtils.get31LongitudeX(point31.getX());
		double lat = MapUtils.get31LatitudeY(point31.getY());
		return new LatLon(lat, lon);
	}


	private float getCompassLineHeight(int index) {
		if (index % 6 == 0) {
			return AndroidUtils.dpToPx(app, 8);
		} else if (index % 9 == 0 || index % 2 != 0) {
			return AndroidUtils.dpToPx(app, 3);
		} else {
			return AndroidUtils.dpToPx(app, 6);
		}
	}

	private void drawCardinalDirections(Canvas canvas, QuadPoint center, float radiusLength, RotatedTileBox tb, RenderingLineAttributes attrs) {
		float margin = 24;
		float textMargin = AndroidUtils.dpToPx(app, margin);
		attrs.paint2.setTextAlign(Paint.Align.CENTER);
		attrs.paint3.setTextAlign(Paint.Align.CENTER);
		setAttrsPaintsTypeface(attrs, Typeface.DEFAULT_BOLD);

		for (int i = 0; i < degrees.length; i += 9) {
			String cardinalDirection = getCardinalDirection(i);
			if (cardinalDirection != null) {
				canvas.save();
				double textRadius = radiusLength - textMargin;
				PointF point = getPointFromCenterByRadius(textRadius, (-i * 5 - 90), tb);
				float h2 = AndroidUtils.getTextHeight(attrs.paint2);
				float h3 = AndroidUtils.getTextHeight(attrs.paint3);
				canvas.drawText(cardinalDirection, point.x , point.y + h3/4, attrs.paint3);
				canvas.drawText(cardinalDirection, point.x, point.y + h2/4, attrs.paint2);
				canvas.restore();
			}
		}
		attrs.paint2.setTextAlign(Paint.Align.LEFT);
		attrs.paint3.setTextAlign(Paint.Align.LEFT);
		setAttrsPaintsTypeface(attrs, null);
	}

	private void setAttrsPaintsTypeface(RenderingLineAttributes attrs, Typeface typeface) {
		attrs.paint2.setTypeface(typeface);
		attrs.paint3.setTypeface(typeface);
	}

	private String getCardinalDirection(int i) {
		if (i == 0) {
			return cardinalDirections[6];
		} else if (i == 9) {
			return cardinalDirections[5];
		} else if (i == 18) {
			return cardinalDirections[4];
		} else if (i == 27) {
			return cardinalDirections[3];
		} else if (i == 36) {
			return cardinalDirections[2];
		} else if (i == 45) {
			return cardinalDirections[1];
		} else if (i == 54) {
			return cardinalDirections[0];
		} else if (i == 63) {
			return cardinalDirections[7];
		}
		return null;
	}

	private String getCardinalDirectionForDegrees(double degrees) {
		while (degrees < 0) {
			degrees += 360;
		}
		int index = (int) Math.floor(((degrees + 22.5) % 360) / 45);
		if (index >= 0 && cardinalDirections.length > index) {
			return cardinalDirections[index];
		} else {
			return "";
		}
	}

	private enum TextSide {
		VERTICAL,
		HORIZONTAL
	}

	@Override
	public boolean drawInScreenPixels() {
		return false;
	}

	public enum RadiusRulerMode {

		FIRST(R.string.dark_theme, R.drawable.ic_action_ruler_circle_dark),
		SECOND(R.string.light_theme, R.drawable.ic_action_ruler_circle_light),
		EMPTY(R.string.shared_string_hide, R.drawable.ic_action_hide);

		@StringRes
		public final int titleId;
		@DrawableRes
		public final int iconId;

		RadiusRulerMode(@StringRes int titleId, @DrawableRes int iconId) {
			this.titleId = titleId;
			this.iconId = iconId;
		}

		@NonNull
		public RadiusRulerMode next() {
			int nextItemIndex = (ordinal() + 1) % values().length;
			return values()[nextItemIndex];
		}
	}
}
