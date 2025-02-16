package net.osmand.plus.profiles.data;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;

import androidx.annotation.NonNull;

import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.utils.ColorUtilities;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.utils.UiUtilities;
import net.osmand.plus.helpers.FontCache;

import java.util.List;

public class PredefinedProfilesGroup extends ProfilesGroup {

	private final String type;

	public PredefinedProfilesGroup(@NonNull String title,
	                               @NonNull String type,
	                               @NonNull List<RoutingDataObject> profiles) {
		super(title, profiles);
		this.type = type;
	}

	public String getType() {
		return type;
	}

	@Override
	public CharSequence getDescription(@NonNull final OsmandApplication ctx, boolean nightMode) {
		String fullDescription = ctx.getString(R.string.provided_by, description);
		int color = ColorUtilities.getActiveColor(ctx, nightMode);
		Typeface typeface = FontCache.getRobotoMedium(ctx);
		final String url = description.toString();
		SpannableString spannable = UiUtilities.createCustomFontSpannable(typeface, fullDescription, url);
		int startInd = fullDescription.indexOf(url);
		spannable.setSpan(new ForegroundColorSpan(color), startInd, startInd + url.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

		ClickableSpan clickableSpan = new ClickableSpan() {
			@Override
			public void onClick(@NonNull View widget) {
				Intent intent = new Intent(Intent.ACTION_VIEW);
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				intent.setData(Uri.parse(url));
				AndroidUtils.startActivityIfSafe(ctx, intent);
			}

			@Override
			public void updateDrawState(@NonNull TextPaint ds) {
				super.updateDrawState(ds);
				ds.setUnderlineText(false);
			}
		};
		spannable.setSpan(clickableSpan, startInd, startInd + url.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

		return spannable;
	}
}
