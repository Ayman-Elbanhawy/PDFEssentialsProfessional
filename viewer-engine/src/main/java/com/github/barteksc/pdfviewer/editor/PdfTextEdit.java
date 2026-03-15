package com.github.barteksc.pdfviewer.editor;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

public class PdfTextEdit extends PdfEditElement {

    private final String text;
    private final int color;
    private final float textSizeSp;

    public PdfTextEdit(int page, RectF bounds, String text, int color, float textSizeSp) {
        super(page, bounds);
        this.text = text;
        this.color = color;
        this.textSizeSp = textSizeSp;
    }

    public String getText() {
        return text;
    }

    public int getColor() {
        return color;
    }

    public float getTextSizeSp() {
        return textSizeSp;
    }

    @Override
    public void draw(Canvas canvas, RectF targetBounds, float pageWidthPoints, float pageHeightPoints, Paint paint) {
        paint.reset();
        paint.setAntiAlias(true);
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        float scale = pageHeightPoints == 0f ? 1f : targetBounds.height() / Math.max(1f, getBounds().height() * pageHeightPoints);
        paint.setTextSize(textSizeSp * scale);
        Paint.FontMetrics fontMetrics = paint.getFontMetrics();
        float baseline = targetBounds.top - fontMetrics.ascent;
        float maxBaseline = targetBounds.bottom - fontMetrics.descent;
        canvas.drawText(text, targetBounds.left, Math.min(baseline, maxBaseline), paint);
    }
}
