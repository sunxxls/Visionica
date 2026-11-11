package com.example.visionica;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.util.Log;

import com.google.mlkit.vision.face.Face;

import java.util.List;

public class FaceEmbeddingGenerator {

    private static final String TAG = "FaceEmbeddingGen";
    private static TensorFlowHelper tensorFlowHelper;
    private static boolean isFrontCamera = true;
    private static int rotationDegrees = 0;

    public static void initialize(Context context) {
        Log.d(TAG, "════════════════════════════════════");
        Log.d(TAG, "initialize() llamado");
        if (tensorFlowHelper == null) {
            tensorFlowHelper = new TensorFlowHelper(context);
            Log.d(TAG, "✓ TensorFlowHelper creado");
        } else {
            Log.d(TAG, "TensorFlowHelper ya existe");
        }
        Log.d(TAG, "════════════════════════════════════");
    }

    public static void setFrontCamera(boolean frontCamera) {
        isFrontCamera = frontCamera;
        Log.d(TAG, "setFrontCamera=" + frontCamera);
    }

    public static void setRotationDegrees(int degrees) {
        rotationDegrees = degrees;
        Log.d(TAG, "setRotationDegrees=" + degrees);
    }

    public static void release() {
        if (tensorFlowHelper != null) {
            tensorFlowHelper.close();
            tensorFlowHelper = null;
            Log.d(TAG, "✓ TensorFlowHelper liberado");
        }
    }

    public static List<Float> generateEmbedding(Bitmap bitmap, Face face) {
        Log.d(TAG, "════════════════════════════════════");
        Log.d(TAG, "generateEmbedding()");
        Log.d(TAG, "Bitmap entrada: " + bitmap.getWidth() + "x" + bitmap.getHeight());
        Log.d(TAG, "isFrontCamera=" + isFrontCamera + " | rotation=" + rotationDegrees);

        if (tensorFlowHelper == null) {
            Log.e(TAG, "✗ TensorFlowHelper es NULL — ¿llamaste initialize()?");
            return null;
        }

        Bitmap workingBitmap = bitmap;

        try {
            // PASO 1: aplicar rotación + flip en una sola Matrix
            // Esto garantiza que registro y reconocimiento usen SIEMPRE la misma transformación
            Matrix matrix = new Matrix();

            if (rotationDegrees != 0) {
                matrix.postRotate(rotationDegrees);
                Log.d(TAG, "Aplicando rotación: " + rotationDegrees + "°");
            }

            if (isFrontCamera) {
                // Flip horizontal para corregir espejo de cámara frontal
                matrix.postScale(-1f, 1f, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);
                Log.d(TAG, "Aplicando flip horizontal (cámara frontal)");
            }

            boolean needsTransform = rotationDegrees != 0 || isFrontCamera;
            if (needsTransform) {
                workingBitmap = Bitmap.createBitmap(
                        bitmap, 0, 0,
                        bitmap.getWidth(), bitmap.getHeight(),
                        matrix, false
                );
                Log.d(TAG, "✓ Transformación aplicada: "
                        + workingBitmap.getWidth() + "x" + workingBitmap.getHeight());
            }

            // PASO 2: recortar rostro
            Bitmap croppedFace = cropFace(workingBitmap, face.getBoundingBox());

            if (needsTransform && workingBitmap != bitmap) {
                workingBitmap.recycle();
            }

            if (croppedFace == null) {
                Log.e(TAG, "✗ croppedFace es NULL");
                return null;
            }

            Log.d(TAG, "✓ Recorte: " + croppedFace.getWidth() + "x" + croppedFace.getHeight());

            // PASO 3: generar embedding
            List<Float> embedding = tensorFlowHelper.generateEmbedding(croppedFace);

            if (croppedFace != bitmap) {
                croppedFace.recycle();
            }

            if (embedding != null) {
                Log.d(TAG, "✓✓✓ Embedding generado: " + embedding.size() + " dimensiones");
                float sum = 0f;
                for (Float v : embedding) sum += Math.abs(v);
                Log.d(TAG, "Suma absoluta: " + String.format("%.4f", sum)
                        + (sum < 0.01f ? " ← ⚠ POSIBLE EMBEDDING VACÍO" : " ← OK"));
            } else {
                Log.e(TAG, "✗ Embedding NULL");
            }

            Log.d(TAG, "════════════════════════════════════");
            return embedding;

        } catch (Exception e) {
            Log.e(TAG, "✗✗✗ EXCEPCIÓN: " + e.getMessage());
            e.printStackTrace();
            if (workingBitmap != bitmap && workingBitmap != null) {
                workingBitmap.recycle();
            }
            Log.d(TAG, "════════════════════════════════════");
            return null;
        }
    }

    private static Bitmap cropFace(Bitmap bitmap, Rect boundingBox) {
        try {
            int expandX = (int) (boundingBox.width()  * 0.2f);
            int expandY = (int) (boundingBox.height() * 0.2f);

            int left   = Math.max(0, boundingBox.left   - expandX);
            int top    = Math.max(0, boundingBox.top    - expandY);
            int right  = Math.min(bitmap.getWidth(),  boundingBox.right  + expandX);
            int bottom = Math.min(bitmap.getHeight(), boundingBox.bottom + expandY);

            int width  = right - left;
            int height = bottom - top;

            Log.d(TAG, "cropFace: left=" + left + " top=" + top
                    + " width=" + width + " height=" + height
                    + " | bitmap=" + bitmap.getWidth() + "x" + bitmap.getHeight());

            if (width <= 0 || height <= 0) {
                Log.e(TAG, "✗ Dimensiones inválidas, usando bitmap completo");
                return Bitmap.createBitmap(bitmap);
            }

            float coverage = (float)(width * height) / (bitmap.getWidth() * bitmap.getHeight()) * 100f;
            Log.d(TAG, "Cobertura: " + String.format("%.1f", coverage) + "%"
                    + (coverage < 2f ? " ← ⚠ MUY PEQUEÑO, posible error de coordenadas" : " ← OK"));

            return Bitmap.createBitmap(bitmap, left, top, width, height);

        } catch (Exception e) {
            Log.e(TAG, "✗ cropFace error: " + e.getMessage());
            return Bitmap.createBitmap(bitmap);
        }
    }
}