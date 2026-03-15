package com.github.barteksc.sample;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.editor.PdfSignatureEdit;
import com.github.barteksc.pdfviewer.editor.PdfTextEdit;
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener;
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener;
import com.github.barteksc.pdfviewer.listener.OnPageErrorListener;
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle;
import com.github.barteksc.pdfviewer.util.FitPolicy;
import com.shockwave.pdfium.PdfDocument;

import android.database.Cursor;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PDFViewActivity extends AppCompatActivity implements
        OnPageChangeListener, OnLoadCompleteListener, OnPageErrorListener {

    private static final String TAG = PDFViewActivity.class.getSimpleName();

    private static final int PERMISSION_CODE = 42042;
    private static final String SAMPLE_FILE = "sample.pdf";
    private static final String READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE";

    private PDFView pdfView;
    private Uri uri;
    private int pageNumber = 0;
    private String pdfFileName;

    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    uri = result.getData().getData();
                    pageNumber = 0;
                    displayFromUri(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pdfView = findViewById(R.id.pdfView);
        pdfView.setBackgroundColor(Color.LTGRAY);

        Button openButton = findViewById(R.id.openButton);
        Button noteButton = findViewById(R.id.noteButton);
        Button signatureButton = findViewById(R.id.signatureButton);
        Button clearButton = findViewById(R.id.clearButton);
        Button saveButton = findViewById(R.id.saveButton);

        openButton.setOnClickListener(v -> pickFile());
        noteButton.setOnClickListener(v -> addSampleTextEdit());
        signatureButton.setOnClickListener(v -> addSampleSignature());
        clearButton.setOnClickListener(v -> {
            pdfView.clearEditElements();
            Toast.makeText(this, R.string.toast_edits_cleared, Toast.LENGTH_SHORT).show();
        });
        saveButton.setOnClickListener(v -> saveEditedPdf());

        if (uri != null) {
            displayFromUri(uri);
        } else {
            displayFromAsset(SAMPLE_FILE);
        }
    }

    private void pickFile() {
        int permissionCheck = ContextCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{READ_EXTERNAL_STORAGE}, PERMISSION_CODE);
        } else {
            launchPicker();
        }
    }

    private void launchPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        try {
            filePickerLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.toast_pick_file_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void displayFromAsset(String assetFileName) {
        pdfFileName = assetFileName;
        configure(pdfView.fromAsset(assetFileName));
    }

    private void displayFromUri(Uri uri) {
        pdfFileName = getFileName(uri);
        configure(pdfView.fromUri(uri));
    }

    private void configure(PDFView.Configurator configurator) {
        configurator
                .defaultPage(pageNumber)
                .onPageChange(this)
                .enableAnnotationRendering(true)
                .onLoad(this)
                .scrollHandle(new DefaultScrollHandle(this))
                .spacing(10)
                .onPageError(this)
                .pageFitPolicy(FitPolicy.BOTH)
                .load();
    }

    private void addSampleTextEdit() {
        pdfView.addEditElement(new PdfTextEdit(
                pageNumber,
                new RectF(0.08f, 0.10f, 0.70f, 0.16f),
                getString(R.string.sample_note_text),
                Color.parseColor("#C62828"),
                18f
        ));
        Toast.makeText(this, R.string.toast_note_added, Toast.LENGTH_SHORT).show();
    }

    private void addSampleSignature() {
        Bitmap signature = createSignatureBitmap("Ayman Elbanhawy");
        pdfView.addEditElement(new PdfSignatureEdit(
                pageNumber,
                new RectF(0.58f, 0.78f, 0.92f, 0.88f),
                signature,
                true,
                Color.parseColor("#0D47A1")
        ));
        Toast.makeText(this, R.string.toast_signature_added, Toast.LENGTH_SHORT).show();
    }

    private Bitmap createSignatureBitmap(String signerName) {
        Bitmap bitmap = Bitmap.createBitmap(800, 220, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.TRANSPARENT);

        Paint signaturePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        signaturePaint.setColor(Color.parseColor("#0D47A1"));
        signaturePaint.setTextSize(86f);
        signaturePaint.setFakeBoldText(true);
        canvas.drawText(signerName, 24f, 120f, signaturePaint);

        Paint underlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        underlinePaint.setColor(Color.parseColor("#0D47A1"));
        underlinePaint.setStrokeWidth(5f);
        canvas.drawLine(24f, 160f, 700f, 160f, underlinePaint);

        Paint captionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        captionPaint.setColor(Color.DKGRAY);
        captionPaint.setTextSize(30f);
        canvas.drawText(getString(R.string.signature_caption), 24f, 204f, captionPaint);
        return bitmap;
    }

    private void saveEditedPdf() {
        try {
            File exportsDir = new File(getExternalFilesDir(null), "edited-pdfs");
            if (!exportsDir.exists() && !exportsDir.mkdirs()) {
                throw new IllegalStateException("Unable to create export folder");
            }
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File output = new File(exportsDir, "edited_" + timestamp + ".pdf");
            pdfView.exportEditedPdf(output);
            uri = Uri.fromFile(output);
            pageNumber = 0;
            displayFromUri(uri);
            Toast.makeText(this, getString(R.string.toast_saved, output.getAbsolutePath()), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to export edited PDF", e);
            Toast.makeText(this, getString(R.string.toast_save_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME);
                    result = cursor.getString(nameIndex);
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    @Override
    public void onPageChanged(int page, int pageCount) {
        pageNumber = page;
        setTitle(String.format(Locale.US, "%s %s / %s", pdfFileName, page + 1, pageCount));
    }

    @Override
    public void loadComplete(int nbPages) {
        PdfDocument.Meta meta = pdfView.getDocumentMeta();
        Log.e(TAG, "title = " + meta.getTitle());
        Log.e(TAG, "author = " + meta.getAuthor());
        Log.e(TAG, "subject = " + meta.getSubject());
        Log.e(TAG, "keywords = " + meta.getKeywords());
        Log.e(TAG, "creator = " + meta.getCreator());
        Log.e(TAG, "producer = " + meta.getProducer());
        Log.e(TAG, "creationDate = " + meta.getCreationDate());
        Log.e(TAG, "modDate = " + meta.getModDate());

        printBookmarksTree(pdfView.getTableOfContents(), "-");
    }

    private void printBookmarksTree(List<PdfDocument.Bookmark> tree, String sep) {
        for (PdfDocument.Bookmark b : tree) {
            Log.e(TAG, String.format(Locale.US, "%s %s, p %d", sep, b.getTitle(), b.getPageIdx()));
            if (b.hasChildren()) {
                printBookmarksTree(b.getChildren(), sep + "-");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CODE && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchPicker();
        }
    }

    @Override
    public void onPageError(int page, Throwable t) {
        Log.e(TAG, "Cannot load page " + page, t);
    }
}
