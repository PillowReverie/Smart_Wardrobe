package com.example.wardrobe.util;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.color.MaterialColors;
import com.yalantis.ucrop.view.GestureCropImageView;
import com.yalantis.ucrop.view.OverlayView;
import com.yalantis.ucrop.view.UCropView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 对第三方 UCrop 界面做本地适配。
 *
 * <p>UCrop 2.2.8 的底部工具栏包含“裁剪 / 旋转 / 缩放”三个页签，点击“缩放”
 * 会展示带百分比的滚轮。本项目希望缩放只依赖双指捏合，因此打开页面后隐藏
 * 缩放页签与滚轮，只保留裁剪和旋转。</p>
 *
 * <p>另外，应用开启了沉浸式状态栏，而 UCrop 的 Toolbar 不会自动避让状态栏。
 * 这里在 Activity 创建完成后给 Toolbar 补充状态栏高度的顶部内边距，保证标题
 * 与时间、电量等系统图标不再重叠。</p>
 */
public final class UcropUiHelper {

    /**
     * 裁剪框相对图片边缘的额外留白。0 表示初始裁剪框与图片实际范围重合。
     */
    private static final int CROP_INSET_DP = 0;
    private static final int EDGE_LEFT = 1;
    private static final int EDGE_TOP = 2;
    private static final int EDGE_RIGHT = 4;
    private static final int EDGE_BOTTOM = 8;
    private static final Method UPDATE_GRID_POINTS = findUpdateGridPoints();

    private UcropUiHelper() {
    }

    /**
     * 由项目自己的 CropActivity 在 onCreate 完成后调用。
     */
    public static void apply(Activity activity) {
        hideScaleControlsWhenReady(activity);
        applyToolbarInsets(activity);
        syncSystemBarContrast(activity);
        enablePinchZoom(activity);
        fitCropBoundsWhenReady(activity);
    }

    /**
     * UCrop 默认只在“缩放”页签下启用双指缩放。该页签已被隐藏，
     * 因此这里在裁剪状态下主动打开缩放手势，并在切回裁剪页签时重新打开。
     */
    private static void enablePinchZoom(Activity activity) {
        View ucropView = activity.findViewById(com.yalantis.ucrop.R.id.ucrop);
        if (!(ucropView instanceof UCropView)) {
            return;
        }
        GestureCropImageView imageView =
                ((UCropView) ucropView).getCropImageView();
        OverlayView overlay = ((UCropView) ucropView).getOverlayView();
        // 允许图片手势穿透裁剪框内部，这样任意位置都能双指缩放。
        overlay.setFreestyleCropMode(
                OverlayView.FREESTYLE_CROP_MODE_ENABLE_WITH_PASS_THROUGH);
        imageView.setScaleEnabled(true);
        // 裁剪状态下双指只负责缩放，关闭旋转手势避免图片跟着手指转动。
        imageView.setRotateEnabled(false);
        // 每次触摸前再次关闭旋转，防止旋转页签把开关重新打开后双指仍能旋转。
        imageView.setOnTouchListener((view, event) -> {
            imageView.setRotateEnabled(false);
            return false;
        });
        installEdgeDrag(overlay, imageView);

        View cropTab =
                activity.findViewById(com.yalantis.ucrop.R.id.state_aspect_ratio);
        bindTabGestureState(cropTab, imageView, true);
        // 旋转页只允许使用底部滚轮旋转，双指旋转始终关闭。
        View rotateTab =
                activity.findViewById(com.yalantis.ucrop.R.id.state_rotate);
        bindTabGestureState(rotateTab, imageView, false);
    }

    /**
     * UCrop 切换页签时会重新设置手势开关，这里在点击完成后覆盖为项目需要的状态。
     */
    private static void bindTabGestureState(View tab,
                                            GestureCropImageView imageView,
                                            boolean scaleEnabled) {
        if (tab == null) {
            return;
        }
        tab.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_CANCEL) {
                view.post(() -> {
                    imageView.setScaleEnabled(scaleEnabled);
                    imageView.setRotateEnabled(false);
                });
            }
            return false;
        });
    }

    /**
     * UCrop 2.2.8 只识别四个角的拖拽。这里补上上下左右四条边的拖动，
     * 所有移动都限制在图片实际区域内，避免裁剪框跑进黑边。
     */
    private static void installEdgeDrag(OverlayView overlay,
                                        GestureCropImageView imageView) {
        float density = overlay.getResources().getDisplayMetrics().density;
        float touchThreshold = 24f * density;
        int minSize = Math.round(48f * density);

        overlay.setOnTouchListener(new View.OnTouchListener() {
            private int activeEdges;
            private float downX;
            private float downY;
            private RectF startRect;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        RectF rect = overlay.getCropViewRect();
                        activeEdges = findEdges(rect, event.getX(), event.getY(),
                                touchThreshold);
                        if (activeEdges == 0) {
                            return false;
                        }
                        downX = event.getX();
                        downY = event.getY();
                        startRect = new RectF(rect);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (activeEdges == 0 || startRect == null
                                || event.getPointerCount() > 1) {
                            return false;
                        }
                        applyEdgeDrag(overlay, imageView, startRect,
                                activeEdges,
                                event.getX() - downX,
                                event.getY() - downY,
                                minSize);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        boolean consumed = activeEdges != 0;
                        activeEdges = 0;
                        startRect = null;
                        return consumed;
                    default:
                        return activeEdges != 0;
                }
            }
        });
    }

    private static int findEdges(RectF rect,
                                 float x,
                                 float y,
                                 float threshold) {
        // 四角交给 UCrop 自己处理，避免两套逻辑抢同一个触摸事件。
        if (isNearCorner(rect, x, y, threshold * 1.4f)) {
            return 0;
        }
        int edges = 0;
        if (Math.abs(x - rect.left) <= threshold
                && y >= rect.top - threshold && y <= rect.bottom + threshold) {
            edges |= EDGE_LEFT;
        }
        if (Math.abs(x - rect.right) <= threshold
                && y >= rect.top - threshold && y <= rect.bottom + threshold) {
            edges |= EDGE_RIGHT;
        }
        if (Math.abs(y - rect.top) <= threshold
                && x >= rect.left - threshold && x <= rect.right + threshold) {
            edges |= EDGE_TOP;
        }
        if (Math.abs(y - rect.bottom) <= threshold
                && x >= rect.left - threshold && x <= rect.right + threshold) {
            edges |= EDGE_BOTTOM;
        }
        return edges;
    }

    private static boolean isNearCorner(RectF rect,
                                        float x,
                                        float y,
                                        float threshold) {
        return distance(x, y, rect.left, rect.top) < threshold
                || distance(x, y, rect.right, rect.top) < threshold
                || distance(x, y, rect.left, rect.bottom) < threshold
                || distance(x, y, rect.right, rect.bottom) < threshold;
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static void applyEdgeDrag(OverlayView overlay,
                                      GestureCropImageView imageView,
                                      RectF startRect,
                                      int edges,
                                      float dx,
                                      float dy,
                                      int minSize) {
        RectF rect = new RectF(startRect);
        float leftLimit = overlay.getPaddingLeft();
        float topLimit = overlay.getPaddingTop();
        float rightLimit = overlay.getWidth() - overlay.getPaddingRight();
        float bottomLimit = overlay.getHeight() - overlay.getPaddingBottom();

        if ((edges & EDGE_LEFT) != 0) {
            rect.left = Math.max(leftLimit,
                    Math.min(startRect.left + dx, rect.right - minSize));
        } else if ((edges & EDGE_RIGHT) != 0) {
            rect.right = Math.min(rightLimit,
                    Math.max(startRect.right + dx, rect.left + minSize));
        }
        if ((edges & EDGE_TOP) != 0) {
            rect.top = Math.max(topLimit,
                    Math.min(startRect.top + dy, rect.bottom - minSize));
        } else if ((edges & EDGE_BOTTOM) != 0) {
            rect.bottom = Math.min(bottomLimit,
                    Math.max(startRect.bottom + dy, rect.top + minSize));
        }

        RectF current = overlay.getCropViewRect();
        if (current.equals(rect)) {
            return;
        }
        current.set(rect);
        imageView.setCropRect(new RectF(rect));
        refreshCropGrid(overlay);
        overlay.postInvalidate();
    }

    private static void refreshCropGrid(OverlayView overlay) {
        if (UPDATE_GRID_POINTS == null) {
            return;
        }
        try {
            UPDATE_GRID_POINTS.invoke(overlay);
        } catch (Exception ignored) {
            // 网格刷新失败不影响裁剪矩形的实际计算。
        }
    }

    private static Method findUpdateGridPoints() {
        try {
            Method method =
                    OverlayView.class.getDeclaredMethod("updateGridPoints");
            method.setAccessible(true);
            return method;
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 把裁剪范围限制在图片实际显示区域内。
     *
     * <p>UCrop 默认按整个视图限制裁剪框，当图片上下或左右留黑边时，裁剪框
     * 可以被拖到黑边里。这里根据图片矩阵计算图片真实绘制矩形，把它设置为
     * OverlayView 的 padding，并额外留出一点触控余量，再按图片比例重置裁剪框。</p>
     */
    private static void fitCropBoundsWhenReady(Activity activity) {
        View decorView = activity.getWindow().getDecorView();
        decorView.post(new Runnable() {
            private int attempts;

            @Override
            public void run() {
                if (fitCropBounds(activity)) {
                    // 工具栏状态栏内边距可能在图片加载后才稳定，再校正一次居中。
                    decorView.postDelayed(
                            () -> fitCropBounds(activity), 400L);
                    return;
                }
                if (++attempts < 40) {
                    decorView.postDelayed(this, 50L);
                }
            }
        });
    }

    private static boolean fitCropBounds(Activity activity) {
        View ucropView = activity.findViewById(com.yalantis.ucrop.R.id.ucrop);
        if (!(ucropView instanceof UCropView)) {
            return false;
        }
        UCropView view = (UCropView) ucropView;
        GestureCropImageView imageView = view.getCropImageView();
        OverlayView overlay = view.getOverlayView();
        Bitmap bitmap = imageView.getViewBitmap();
        if (bitmap == null || bitmap.isRecycled()
                || imageView.getWidth() == 0 || imageView.getHeight() == 0
                || overlay.getWidth() == 0 || overlay.getHeight() == 0) {
            return false;
        }

        int imagePadLeft = imageView.getPaddingLeft();
        int imagePadTop = imageView.getPaddingTop();
        int contentWidth = imageView.getWidth()
                - imagePadLeft - imageView.getPaddingRight();
        int contentHeight = imageView.getHeight()
                - imagePadTop - imageView.getPaddingBottom();
        if (contentWidth <= 0 || contentHeight <= 0) {
            return false;
        }

        // 自己计算一次 fit-center 矩阵，避免不同设备上 uCrop 初始定位不一致。
        float scale = Math.min(
                contentWidth / (float) bitmap.getWidth(),
                contentHeight / (float) bitmap.getHeight());
        float dx = (contentWidth - bitmap.getWidth() * scale) / 2f;
        float dy = (contentHeight - bitmap.getHeight() * scale) / 2f;
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        matrix.postTranslate(dx, dy);
        imageView.setImageMatrix(matrix);

        int width = overlay.getWidth();
        int height = overlay.getHeight();
        int inset = Math.round(
                activity.getResources().getDisplayMetrics().density
                        * CROP_INSET_DP);
        int padLeft = Math.round(Math.max(0f, imagePadLeft + dx)) + inset;
        int padTop = Math.round(Math.max(0f, imagePadTop + dy)) + inset;
        int padRight = Math.round(Math.max(0f,
                width - (imagePadLeft + dx + bitmap.getWidth() * scale))) + inset;
        int padBottom = Math.round(Math.max(0f,
                height - (imagePadTop + dy + bitmap.getHeight() * scale))) + inset;

        // 极端比例下避免 padding 把可用区域挤没。
        int maxHorizontal = Math.max(0, width / 3);
        int maxVertical = Math.max(0, height / 3);
        padLeft = Math.min(padLeft, maxHorizontal);
        padRight = Math.min(padRight, maxHorizontal);
        padTop = Math.min(padTop, maxVertical);
        padBottom = Math.min(padBottom, maxVertical);

        overlay.setPadding(padLeft, padTop, padRight, padBottom);
        final int availableWidth = width - padLeft - padRight;
        final int availableHeight = height - padTop - padBottom;
        // 等 padding 参与布局后，再按图片比例重置裁剪框。
        overlay.postDelayed(() -> {
            updateOverlayLayoutSize(overlay, availableWidth, availableHeight);
            overlay.setTargetAspectRatio(imageView.getTargetAspectRatio());
            overlay.postInvalidate();
            imageView.setScaleEnabled(true);
            imageView.setRotateEnabled(false);
        }, 32L);
        return true;
    }

    /**
     * OverlayView 只在自身尺寸变化时重算 padding 后的可用宽高。
     * 这里同步修正这两个私有字段，保证重置后的裁剪框不会超出图片范围。
     * 若后续升级 UCrop 导致字段变化，会走直接设置裁剪矩形的兜底逻辑。
     */
    private static void updateOverlayLayoutSize(OverlayView overlay,
                                                int width,
                                                int height) {
        try {
            Field widthField =
                    OverlayView.class.getDeclaredField("mThisWidth");
            Field heightField =
                    OverlayView.class.getDeclaredField("mThisHeight");
            widthField.setAccessible(true);
            heightField.setAccessible(true);
            widthField.setInt(overlay, Math.max(1, width));
            heightField.setInt(overlay, Math.max(1, height));
        } catch (Exception ignored) {
            // 反射失败时不阻断裁剪流程，裁剪框仍可手动拖动。
        }
    }

    /**
     * 隐藏底部“缩放”页签与百分比滚轮，双指捏合缩放不受影响。
     *
     * <p>底部控件属于 UCrop 内部的 Fragment，Activity 的 onCreate 刚结束时
     * 视图树可能还没挂载，因此用短间隔重试，最多等待约 1 秒。</p>
     */
    private static void hideScaleControlsWhenReady(Activity activity) {
        View decorView = activity.getWindow().getDecorView();
        decorView.post(new Runnable() {
            private int attempts;

            @Override
            public void run() {
                if (hideScaleControls(activity)) {
                    return;
                }
                if (++attempts < 20) {
                    decorView.postDelayed(this, 50L);
                }
            }
        });
    }

    private static boolean hideScaleControls(Activity activity) {
        View scaleState =
                activity.findViewById(com.yalantis.ucrop.R.id.state_scale);
        if (scaleState == null) {
            return false;
        }
        hide(scaleState);
        hide(activity.findViewById(com.yalantis.ucrop.R.id.layout_scale_wheel));
        return true;
    }

    private static void hide(View view) {
        if (view != null) {
            view.setVisibility(View.GONE);
        }
    }

    /**
     * 给 UCrop 的 Toolbar 增加状态栏内边距。
     *
     * <p>Toolbar 高度是 wrap_content，设置顶部 padding 后会自然增高，
     * 背景色也会延伸到状态栏区域，形成连贯的顶部栏。</p>
     */
    private static void applyToolbarInsets(Activity activity) {
        View toolbar = activity.findViewById(com.yalantis.ucrop.R.id.toolbar);
        if (toolbar == null) {
            return;
        }
        final int basePaddingTop = toolbar.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars()
                            | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(
                    view.getPaddingLeft(),
                    basePaddingTop + insets.top,
                    view.getPaddingRight(),
                    view.getPaddingBottom());
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(toolbar);
    }

    /**
     * 根据工具栏底色自动切换状态栏图标明暗，深色模式下保持清晰可见。
     */
    private static void syncSystemBarContrast(Activity activity) {
        int surfaceColor = MaterialColors.getColor(
                activity,
                com.google.android.material.R.attr.colorSurface,
                Color.WHITE);
        boolean lightBackground =
                ColorUtils.calculateLuminance(surfaceColor) > 0.5;
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(
                        activity.getWindow(),
                        activity.getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(lightBackground);
        controller.setAppearanceLightNavigationBars(lightBackground);
    }
}
