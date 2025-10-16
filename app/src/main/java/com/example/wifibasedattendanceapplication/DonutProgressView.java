package com.example.wifibasedattendanceapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Paint.Align;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class DonutProgressView extends View {

	private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float progress = 0f; // 0..100
	private final RectF arcBounds = new RectF();
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

	public DonutProgressView(Context context) { super(context); init(); }
	public DonutProgressView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
	public DonutProgressView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

	private void init() {
		backgroundPaint.setStyle(Paint.Style.STROKE);
		backgroundPaint.setStrokeWidth(24f);
		backgroundPaint.setColor(0x33FFFFFF);

		progressPaint.setStyle(Paint.Style.STROKE);
		progressPaint.setStrokeCap(Paint.Cap.ROUND);
		progressPaint.setStrokeWidth(24f);
		progressPaint.setColor(0xFFFFFFFF);

        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextAlign(Align.CENTER);
        textPaint.setTextSize(48f);
	}

	public void setProgress(float progress) {
		this.progress = Math.max(0f, Math.min(100f, progress));
		invalidate();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		float padding = 24f;
		arcBounds.set(padding, padding, getWidth() - padding, getHeight() - padding);
		canvas.drawArc(arcBounds, -90, 360, false, backgroundPaint);
		canvas.drawArc(arcBounds, -90, 360f * (progress / 100f), false, progressPaint);

        // Draw percentage text centered inside
        String text = Math.round(progress) + "%";
        // Center vertically accounting for font metrics
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(text, centerX, centerY, textPaint);
	}
}
