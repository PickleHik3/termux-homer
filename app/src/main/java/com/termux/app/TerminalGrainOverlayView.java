package com.termux.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * Draws a subtle tiled grain overlay for terminal background styling.
 */
public class TerminalGrainOverlayView extends View {

    private static final int NOISE_TILE_SIZE = 128;
    private static final int MAX_GRAIN_ALPHA = 96;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private Bitmap mNoiseBitmap;
    private int mIntensity;

    public TerminalGrainOverlayView(Context context) {
        this(context, null);
    }

    public TerminalGrainOverlayView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TerminalGrainOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        ensureNoiseShader();
    }

    public void setIntensity(int intensity) {
        int clamped = Math.max(0, Math.min(100, intensity));
        if (mIntensity == clamped) {
            return;
        }
        mIntensity = clamped;
        invalidate();
    }

    private void ensureNoiseShader() {
        if (mNoiseBitmap != null) {
            return;
        }
        mNoiseBitmap = Bitmap.createBitmap(NOISE_TILE_SIZE, NOISE_TILE_SIZE, Bitmap.Config.ARGB_8888);
        Random random = new Random(0x5445524d55584cL);
        int[] pixels = new int[NOISE_TILE_SIZE * NOISE_TILE_SIZE];
        for (int i = 0; i < pixels.length; i++) {
            int value = 90 + random.nextInt(166);
            pixels[i] = Color.argb(255, value, value, value);
        }
        mNoiseBitmap.setPixels(pixels, 0, NOISE_TILE_SIZE, 0, 0, NOISE_TILE_SIZE, NOISE_TILE_SIZE);
        BitmapShader shader = new BitmapShader(mNoiseBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        mPaint.setShader(shader);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mIntensity <= 0) {
            return;
        }
        ensureNoiseShader();
        mPaint.setAlpha((mIntensity * MAX_GRAIN_ALPHA) / 100);
        mRect.set(0, 0, getWidth(), getHeight());
        canvas.drawRect(mRect, mPaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mNoiseBitmap != null) {
            mNoiseBitmap.recycle();
            mNoiseBitmap = null;
        }
        super.onDetachedFromWindow();
    }
}
