package com.example.visionica;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class TensorFlowHelper {

    private static final String TAG = "TensorFlowHelper";

    // Nombre exacto del modelo en app/src/main/assets/
    private static final String MODEL_FILE = "facenet.tflite";

    // Configuración del modelo FaceNet
    private static final int INPUT_SIZE    = 160;
    private static final int BATCH_SIZE    = 1;
    private static final int PIXEL_SIZE    = 3;
    private static final int EMBEDDING_SIZE = 512;

    private static final float IMAGE_MEAN = 127.5f;
    private static final float IMAGE_STD  = 128.0f;

    // FIX #1: gpuDelegate declarado como campo de clase
    private GpuDelegate  gpuDelegate;
    private Interpreter  interpreter;

    public TensorFlowHelper(Context context) {
        Log.e("PRUEBA_TF", "ENTRE AL CONSTRUCTOR DE TENSORFLOW");
        Log.d(TAG, "════════════════════════════════════");
        Log.d(TAG, "INTENTANDO CARGAR MODELO...");

        try {
            Log.d(TAG, "1. Cargando archivo del modelo...");
            MappedByteBuffer modelBuffer = loadModelFile(context);
            Log.d(TAG, "✓ Archivo cargado. Tamaño: " + modelBuffer.capacity() + " bytes");

            Log.d(TAG, "2. Configurando opciones...");
            Interpreter.Options options = new Interpreter.Options();

            // FIX #2: CompatibilityList cerrado con try-with-resources
            try (CompatibilityList compatList = new CompatibilityList()) {
                if (compatList.isDelegateSupportedOnThisDevice()) {
                    GpuDelegate.Options delegateOptions = compatList.getBestOptionsForThisDevice();
                    gpuDelegate = new GpuDelegate();
                    options.addDelegate(gpuDelegate);
                    Log.d(TAG, "✓ GPU habilitada");
                } else {
                    options.setNumThreads(4);
                    Log.d(TAG, "✓ Usando CPU (4 threads)");
                }
            }

            Log.d(TAG, "3. Creando intérprete...");
            interpreter = new Interpreter(modelBuffer, options);
            Log.d(TAG, "✓ Intérprete creado");

            Log.d(TAG, "4. Verificando dimensiones...");
            int[] inputShape  = interpreter.getInputTensor(0).shape();
            int[] outputShape = interpreter.getOutputTensor(0).shape();

            Log.d(TAG, "PRUEBA LOGCAT - TensorFlowHelper ejecutándose");

            Log.d(TAG, "DIMENSIONES REALES ENTRADA: " +
                    java.util.Arrays.toString(inputShape));

            Log.d(TAG, "DIMENSIONES REALES SALIDA: " +
                    java.util.Arrays.toString(outputShape));



            Log.d(TAG, "✓✓✓ MODELO CARGADO EXITOSAMENTE ✓✓✓");
            Log.d(TAG, "Input shape:  " + java.util.Arrays.toString(inputShape));
            Log.d(TAG, "Output shape: " + java.util.Arrays.toString(outputShape));
            Log.d(TAG, "════════════════════════════════════");



        } catch (Exception e) {
            Log.e(TAG, "✗✗✗ ERROR AL CARGAR MODELO ✗✗✗");
            Log.e(TAG, "Mensaje: " + e.getMessage());
            Log.e(TAG, "Clase:   " + e.getClass().getName());
            e.printStackTrace();
            Log.d(TAG, "════════════════════════════════════");
        }
    }

    // FIX #3: FileInputStream cerrado con try-with-resources
    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        try (AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_FILE);
             FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor())) {

            FileChannel fileChannel  = inputStream.getChannel();
            long startOffset         = fileDescriptor.getStartOffset();
            long declaredLength      = fileDescriptor.getDeclaredLength();

            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        }
    }

    public List<Float> generateEmbedding(Bitmap bitmap) {
        Log.d(TAG, "────────────────────────────────────");
        Log.d(TAG, "generateEmbedding() llamado");

        if (interpreter == null) {
            Log.e(TAG, "✗ ERROR: Intérprete es NULL");
            Log.d(TAG, "────────────────────────────────────");
            return null;
        }

        Log.d(TAG, "✓ Intérprete OK");

        // FIX #4: bitmap temporal declarado fuera del bloque para poder reciclarlo en finally
        Bitmap resizedBitmap = null;

        try {
            Log.d(TAG, "1. Bitmap original: " + bitmap.getWidth() + "x" + bitmap.getHeight());

            Log.d(TAG, "2. Redimensionando a " + INPUT_SIZE + "x" + INPUT_SIZE + "...");
            resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
            Log.d(TAG, "✓ Redimensionado OK");

            Log.d(TAG, "3. Convirtiendo a ByteBuffer...");
            ByteBuffer inputBuffer = convertBitmapToByteBuffer(resizedBitmap);
            Log.d(TAG, "✓ ByteBuffer creado. Capacidad: " + inputBuffer.capacity());

            Log.d(TAG, "4. Preparando output...");
            float[][] output = new float[BATCH_SIZE][EMBEDDING_SIZE];

            Log.d(TAG, "5. Ejecutando inferencia...");
            interpreter.run(inputBuffer, output);
            Log.d(TAG, "✓ Inferencia ejecutada");

            Log.d(TAG, "6. Normalizando embedding...");
            List<Float> embedding  = new ArrayList<>(EMBEDDING_SIZE);
            float[]     embeddingArray = output[0];

            Log.d(TAG, "Primeros valores raw: [" +
                    embeddingArray[0] + ", " +
                    embeddingArray[1] + ", " +
                    embeddingArray[2] + "...]");

            float norm = 0f;
            for (float value : embeddingArray) {
                norm += value * value;
            }
            norm = (float) Math.sqrt(norm);
            Log.d(TAG, "Norma L2: " + norm);

            if (norm > 0) {
                for (float value : embeddingArray) {
                    embedding.add(value / norm);
                }
            } else {
                for (float value : embeddingArray) {
                    embedding.add(value);
                }
            }

            Log.d(TAG, "✓✓✓ EMBEDDING GENERADO EXITOSAMENTE ✓✓✓");
            Log.d(TAG, "Dimensiones: " + embedding.size());
            Log.d(TAG, "Primeros valores normalizados: [" +
                    embedding.get(0) + ", " +
                    embedding.get(1) + ", " +
                    embedding.get(2) + "...]");
            Log.d(TAG, "────────────────────────────────────");
            return embedding;

        } catch (Exception e) {
            Log.e(TAG, "✗✗✗ ERROR AL GENERAR EMBEDDING ✗✗✗");
            Log.e(TAG, "Mensaje: " + e.getMessage());
            Log.e(TAG, "Clase:   " + e.getClass().getName());
            e.printStackTrace();
            Log.d(TAG, "────────────────────────────────────");
            return null;

        } finally {
            // FIX #4: reciclar el bitmap redimensionado si es distinto al original
            if (resizedBitmap != null && resizedBitmap != bitmap) {
                resizedBitmap.recycle();
                Log.d(TAG, "✓ Bitmap temporal reciclado");
            }
        }
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(
                BATCH_SIZE * INPUT_SIZE * INPUT_SIZE * PIXEL_SIZE * 4
        );
        byteBuffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        int pixel = 0;
        for (int i = 0; i < INPUT_SIZE; i++) {
            for (int j = 0; j < INPUT_SIZE; j++) {
                final int val = pixels[pixel++];

                float r = ((val >> 16) & 0xFF);
                float g = ((val >> 8)  & 0xFF);
                float b = ( val        & 0xFF);

                byteBuffer.putFloat((r - IMAGE_MEAN) / IMAGE_STD);
                byteBuffer.putFloat((g - IMAGE_MEAN) / IMAGE_STD);
                byteBuffer.putFloat((b - IMAGE_MEAN) / IMAGE_STD);
            }
        }

        return byteBuffer;
    }

    // FIX #2 + #3: close() libera intérprete Y gpuDelegate
    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
            Log.d(TAG, "✓ Intérprete cerrado");
        }
        if (gpuDelegate != null) {
            gpuDelegate.close();
            gpuDelegate = null;
            Log.d(TAG, "✓ GpuDelegate cerrado");
        }
    }
}