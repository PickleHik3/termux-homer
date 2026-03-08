package com.github.mmin18.widget;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;

import com.termux.R;

/**
 * Realtime blur overlay that captures activity decor, blurs it, and draws the result.
 * Restores pre-placeholder behavior used by Termux-Monet lineage.
 */
public class RealtimeBlurView extends View {

    private static int RENDERING_COUNT;
    private static int BLUR_IMPL;
    private static final StopException STOP_EXCEPTION = new StopException();

    private float mDownsampleFactor;
    private int mOverlayColor;
    private float mBlurRadius;
    private boolean mDownsampleFactorOptimization;

    private final BlurImpl mBlurImpl;
    private boolean mDirty;
    private Bitmap mBitmapToBlur;
    private Bitmap mBlurredBitmap;
    private Canvas mBlurringCanvas;
    private boolean mIsRendering;
    private final Paint mPaint;
    private final Rect mRectSrc = new Rect();
    private final Rect mRectDst = new Rect();

    private View mDecorView;
    private boolean mDifferentRoot;

    public RealtimeBlurView(Context context) {
        this(context, null);
    }

    public RealtimeBlurView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RealtimeBlurView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mBlurImpl = getBlurImpl();
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.RealtimeBlurView);
        mBlurRadius = a.getDimension(
            R.styleable.RealtimeBlurView_realtimeBlurRadius,
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, context.getResources().getDisplayMetrics())
        );
        mDownsampleFactor = a.getFloat(R.styleable.RealtimeBlurView_realtimeDownsampleFactor, 4f);
        mOverlayColor = a.getColor(R.styleable.RealtimeBlurView_realtimeOverlayColor, 0xAAFFFFFF);
        mDownsampleFactorOptimization = a.getBoolean(
            R.styleable.RealtimeBlurView_downsampleFactorOptimization,
            true
        );
        a.recycle();

        mPaint = new Paint();
    }

    protected BlurImpl getBlurImpl() {
        if (BLUR_IMPL == 0) {
            try {
                AndroidStockBlurImpl impl = new AndroidStockBlurImpl();
                Bitmap bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
                impl.prepare(getContext(), bmp, 4f);
                impl.release();
                bmp.recycle();
                BLUR_IMPL = 1;
            } catch (Throwable ignored) {
                BLUR_IMPL = -1;
            }
        }

        if (BLUR_IMPL == 1) return new AndroidStockBlurImpl();
        return new EmptyBlurImpl();
    }

    public void setBlurRadius(float radius) {
        if (mBlurRadius != radius) {
            mBlurRadius = radius;
            mDirty = true;
            invalidate();
        }
    }

    public void setDownsampleFactor(float factor) {
        if (factor <= 0f) {
            throw new IllegalArgumentException("Downsample factor must be greater than 0.");
        }
        if (mDownsampleFactor != factor) {
            mDownsampleFactor = factor;
            mDirty = true;
            releaseBitmap();
            invalidate();
        }
    }

    public void setOverlayColor(int color) {
        if (mOverlayColor != color) {
            mOverlayColor = color;
            invalidate();
        }
    }

    private void releaseBitmap() {
        if (mBitmapToBlur != null) {
            mBitmapToBlur.recycle();
            mBitmapToBlur = null;
        }
        if (mBlurredBitmap != null) {
            mBlurredBitmap.recycle();
            mBlurredBitmap = null;
        }
    }

    protected void release() {
        releaseBitmap();
        mBlurImpl.release();
    }

    protected boolean prepare() {
        if (mBlurRadius == 0) {
            release();
            return false;
        }

        float downsampleFactor = mDownsampleFactor;
        float radius = mBlurRadius / downsampleFactor;
        if (radius > 25f) {
            if (mDownsampleFactorOptimization) {
                downsampleFactor = (int) (radius / 25f) + 1f;
                radius = radius / downsampleFactor;
            } else {
                downsampleFactor = downsampleFactor * radius / 25f;
                radius = 25f;
            }
        }

        int width = getWidth();
        int height = getHeight();
        int scaledWidth = Math.max(1, (int) (width / downsampleFactor));
        int scaledHeight = Math.max(1, (int) (height / downsampleFactor));

        boolean dirty = mDirty;

        if (mBlurringCanvas == null || mBlurredBitmap == null
            || mBlurredBitmap.getWidth() != scaledWidth
            || mBlurredBitmap.getHeight() != scaledHeight) {
            dirty = true;
            releaseBitmap();

            boolean success = false;
            try {
                mBitmapToBlur = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
                if (mBitmapToBlur == null) return false;
                mBlurringCanvas = new Canvas(mBitmapToBlur);

                mBlurredBitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
                if (mBlurredBitmap == null) return false;

                success = true;
            } catch (OutOfMemoryError ignored) {
            } finally {
                if (!success) {
                    release();
                    return false;
                }
            }
        }

        if (dirty) {
            if (mBlurImpl.prepare(getContext(), mBitmapToBlur, radius)) {
                mDirty = false;
            } else {
                return false;
            }
        }

        return true;
    }

    protected void blur(Bitmap bitmapToBlur, Bitmap blurredBitmap) {
        mBlurImpl.blur(bitmapToBlur, blurredBitmap);
    }

    private final ViewTreeObserver.OnPreDrawListener mPreDrawListener = new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
            int[] locations = new int[2];
            Bitmap oldBmp = mBlurredBitmap;
            View decor = mDecorView;
            if (decor != null && isShown() && prepare()) {
                boolean redrawBitmap = mBlurredBitmap != oldBmp;

                decor.getLocationOnScreen(locations);
                int x = -locations[0];
                int y = -locations[1];

                getLocationOnScreen(locations);
                x += locations[0];
                y += locations[1];

                mBitmapToBlur.eraseColor(mOverlayColor & 0x00FFFFFF);

                int rc = mBlurringCanvas.save();
                mIsRendering = true;
                RENDERING_COUNT++;
                try {
                    mBlurringCanvas.scale(
                        1f * mBitmapToBlur.getWidth() / getWidth(),
                        1f * mBitmapToBlur.getHeight() / getHeight()
                    );
                    mBlurringCanvas.translate(-x, -y);
                    if (decor.getBackground() != null) {
                        decor.getBackground().draw(mBlurringCanvas);
                    }
                    decor.draw(mBlurringCanvas);
                } catch (StopException ignored) {
                } finally {
                    mIsRendering = false;
                    RENDERING_COUNT--;
                    mBlurringCanvas.restoreToCount(rc);
                }

                blur(mBitmapToBlur, mBlurredBitmap);

                if (redrawBitmap || mDifferentRoot) {
                    invalidate();
                }
            }
            return true;
        }
    };

    protected View getActivityDecorView() {
        Context ctx = getContext();
        for (int i = 0; i < 4 && ctx != null && !(ctx instanceof Activity) && ctx instanceof ContextWrapper; i++) {
            ctx = ((ContextWrapper) ctx).getBaseContext();
        }
        if (ctx instanceof Activity) {
            return ((Activity) ctx).getWindow().getDecorView();
        }
        return null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mDecorView = getActivityDecorView();
        if (mDecorView != null) {
            mDecorView.getViewTreeObserver().addOnPreDrawListener(mPreDrawListener);
            mDifferentRoot = mDecorView.getRootView() != getRootView();
            if (mDifferentRoot) {
                mDecorView.postInvalidate();
            }
        } else {
            mDifferentRoot = false;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mDecorView != null) {
            mDecorView.getViewTreeObserver().removeOnPreDrawListener(mPreDrawListener);
        }
        release();
        super.onDetachedFromWindow();
    }

    @Override
    public void draw(Canvas canvas) {
        if (mIsRendering) {
            throw STOP_EXCEPTION;
        } else if (RENDERING_COUNT > 0) {
            // Overlapping blur views are not supported.
        } else {
            super.draw(canvas);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawBlurredBitmap(canvas, mBlurredBitmap, mOverlayColor);
    }

    protected void drawBlurredBitmap(Canvas canvas, Bitmap blurredBitmap, int overlayColor) {
        if (blurredBitmap != null) {
            mRectSrc.right = blurredBitmap.getWidth();
            mRectSrc.bottom = blurredBitmap.getHeight();
            mRectDst.right = getWidth();
            mRectDst.bottom = getHeight();
            canvas.drawBitmap(blurredBitmap, mRectSrc, mRectDst, null);
        }
        mPaint.setColor(overlayColor);
        canvas.drawRect(mRectDst, mPaint);
    }

    private static class StopException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
