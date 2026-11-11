package com.example.wardrobe.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

import com.example.wardrobe.R;

/**
 * 支持最大高度限制的 NestedScrollView。
 *
 * <p>首页一次展示三套 AI 搭配，文本长度不可控。这个容器在测量阶段把
 * 高度限制在 200dp 左右；内容较短时仍然按内容自然收缩，内容较长时则
 * 在卡片内部滚动，避免整张首页被 AI 回答无限撑高。</p>
 */
public class MaxHeightNestedScrollView extends NestedScrollView {

    private int maxHeightPx;

    public MaxHeightNestedScrollView(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public MaxHeightNestedScrollView(@NonNull Context context,
                                     @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public MaxHeightNestedScrollView(@NonNull Context context,
                                     @Nullable AttributeSet attrs,
                                     int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        int defaultHeight = Math.round(
                200 * context.getResources().getDisplayMetrics().density);
        TypedArray typedArray = context.obtainStyledAttributes(
                attrs, R.styleable.MaxHeightNestedScrollView);
        maxHeightPx = typedArray.getDimensionPixelSize(
                R.styleable.MaxHeightNestedScrollView_contentMaxHeight,
                defaultHeight);
        typedArray.recycle();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        // 父布局有明确可用高度时取两者较小值，避免在极小屏幕上溢出。
        int limitedHeight = heightMode == MeasureSpec.UNSPECIFIED
                ? maxHeightPx : Math.min(maxHeightPx, heightSize);
        int limitedSpec = MeasureSpec.makeMeasureSpec(
                limitedHeight, MeasureSpec.AT_MOST);
        super.onMeasure(widthMeasureSpec, limitedSpec);
    }
}
