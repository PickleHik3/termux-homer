package com.github.mmin18.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import com.termux.R;

/**
 * Lightweight local fallback for the old JitPack blur widget dependency.
 * Uses platform blur on API 31+ and overlay tint on older versions.
 */
public class RealtimeBlurView extends FrameLayout {

    private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float blurRadiusPx = 0f;
    private int overlayColor = Color.TRANSPARENT;

    public RealtimeBlurView(Context context) {
        super(context);
        init(context, null);
    }

    public RealtimeBlurView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public RealtimeBlurView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        setWillNotDraw(false);
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.RealtimeBlurView);
            blurRadiusPx = Math.max(0f, a.getDimension(R.styleable.RealtimeBlurView_realtimeBlurRadius, 0f));
            overlayColor = a.getColor(R.styleable.RealtimeBlurView_realtimeOverlayColor, Color.TRANSPARENT);
            a.recycle();
        }
        applyBlurEffectIfSupported();
    }

    public void setBlurRadius(float radiusPx) {
        blurRadiusPx = Math.max(0f, radiusPx);
        applyBlurEffectIfSupported();
        invalidate();
    }

    public void setOverlayColor(int color) {
        overlayColor = color;
        invalidate();
    }

    private void applyBlurEffectIfSupported() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (blurRadiusPx > 0f) {
                setRenderEffect(RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP));
            } else {
                setRenderEffect(null);
            }
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (overlayColor != Color.TRANSPARENT) {
            overlayPaint.setColor(overlayColor);
            canvas.drawRect(0, 0, getWidth(), getHeight(), overlayPaint);
        }
    }
}
