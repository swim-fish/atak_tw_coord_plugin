package com.atakmap.android.twcoord.nativeentry;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ScrollView;
import androidx.annotation.Nullable;
import com.atakmap.android.twcoord.plugin.R;

/** Shrink-wraps compact coordinate panes while keeping tall content above ATAK-owned controls. */
public final class BoundedPaneScrollView extends ScrollView {

  public BoundedPaneScrollView(Context context) {
    this(context, null);
  }

  public BoundedPaneScrollView(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public BoundedPaneScrollView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int maxHeight = getResources().getDimensionPixelSize(R.dimen.native_entry_pane_max_height);
    int heightMode = View.MeasureSpec.getMode(heightMeasureSpec);
    int availableHeight = View.MeasureSpec.getSize(heightMeasureSpec);

    if (heightMode == View.MeasureSpec.UNSPECIFIED || availableHeight > maxHeight) {
      heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST);
    }
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }
}
