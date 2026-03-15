package com.github.barteksc.pdfviewer.editor;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

public abstract class PdfEditElement {

    private final int page;
    private final RectF bounds;

    protected PdfEditElement(int page, RectF bounds) {
        this.page = page;
        this.bounds = new RectF(bounds);
    }

    public int getPage() {
        return page;
    }

    public RectF getBounds() {
        return new RectF(bounds);
    }

    public abstract void draw(Canvas canvas, RectF targetBounds, float pageWidthPoints, float pageHeightPoints, Paint paint);
}
