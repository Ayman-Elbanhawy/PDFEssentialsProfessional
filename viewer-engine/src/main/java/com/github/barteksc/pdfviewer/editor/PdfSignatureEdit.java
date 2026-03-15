package com.github.barteksc.pdfviewer.editor;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

public class PdfSignatureEdit extends PdfEditElement {

    private final Bitmap signatureBitmap;
    private final boolean drawBorder;
    private final int borderColor;

    public PdfSignatureEdit(int page, RectF bounds, Bitmap signatureBitmap, boolean drawBorder, int borderColor) {
        super(page, bounds);
        this.signatureBitmap = signatureBitmap;
        this.drawBorder = drawBorder;
        this.borderColor = borderColor;
    }

    public Bitmap getSignatureBitmap() {
        return signatureBitmap;
    }

    public boolean isDrawBorder() {
        return drawBorder;
    }

    public int getBorderColor() {
        return borderColor;
    }

    @Override
    public void draw(Canvas canvas, RectF targetBounds, float pageWidthPoints, float pageHeightPoints, Paint paint) {
        if (signatureBitmap == null || signatureBitmap.isRecycled()) {
            return;
        }
        paint.reset();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        canvas.drawBitmap(signatureBitmap, null, targetBounds, paint);
        if (drawBorder) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2f, targetBounds.width() * 0.01f));
            paint.setColor(borderColor);
            canvas.drawRect(targetBounds, paint);
        }
    }
}
