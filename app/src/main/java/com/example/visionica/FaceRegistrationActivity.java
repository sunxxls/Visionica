package com.example.visionica;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FaceRegistrationActivity extends AppCompatActivity {

    private static final String TAG = "FaceRegistration";
    private static final int CAMERA_PERMISSION_CODE = 100;

    // ── Verificación de la foto de FRENTE (paso 1) ──────────────────────────
    // El rostro debe cumplir todas las condiciones sin interrupción durante este tiempo
    private static final long FRONT_HOLD_MS = 1500;
    // Ancho mínimo del rostro respecto al ancho de la imagen (bájalo a 0.14 si te pide acercarte siempre)
    private static final float MIN_FACE_WIDTH_RATIO = 0.18f;
    // Inclinación máxima permitida (arriba/abajo y de lado) para la foto de frente
    private static final float MAX_FRONT_TILT = 20f;

    private String classId;
    private String className;
    private String studentId;
    private String studentName;

    // Views
    private PreviewView previewView;
    private TextView tvInstruction;
    private TextView tvProgress;
    private ProgressBar progressBar;

    // Camera
    private ProcessCameraProvider cameraProvider;
    private Preview preview;
    // Ahora el registro abre con la cámara TRASERA (se puede cambiar con el botón)
    private boolean isUsingFrontCamera = false;
    private ImageAnalysis imageAnalysis;
    private ExecutorService cameraExecutor;

    // ML Kit
    private FaceDetector faceDetector;

    // Firebase
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    // Estado del registro
    private enum RegistrationStep {
        STEP_1_FRONT(0,   "Mira al frente"),
        STEP_2_LEFT(25,   "Gira un poco a la izquierda"),
        STEP_3_RIGHT(50,  "Gira un poco a la derecha"),
        STEP_4_BLINK(75,  "Parpadea"),
        STEP_5_COMPLETE(100, "Registro completo");

        final int progress;
        final String instruction;

        RegistrationStep(int progress, String instruction) {
            this.progress = progress;
            this.instruction = instruction;
        }
    }

    private RegistrationStep currentStep        = RegistrationStep.STEP_1_FRONT;
    private volatile boolean  isProcessing       = false;
    private boolean           hasDetectedBlink   = false;
    private long              lastBlinkCheckTime = 0;
    private float             previousEyeOpenProbability = 1.0f;

    // Desde cuándo el rostro de frente cumple todas las condiciones (0 = no cumple)
    private long frontStableSince = 0;
    // Para restaurar la instrucción cuando el rostro vuelve a aparecer
    private boolean noFaceShown = false;

    // contador para no capturar en cada frame
    private int framesAnalyzed = 0;

    // Embedding capturado
    private List<List<Float>> capturedEmbeddings = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_registration);

        Log.d(TAG, "════════════════════════════════════");
        Log.d(TAG, "onCreate() - Iniciando FaceRegistrationActivity");

        FaceEmbeddingGenerator.initialize(this);
        FaceEmbeddingGenerator.setFrontCamera(isUsingFrontCamera);
        Log.d(TAG, "✓ FaceEmbeddingGenerator inicializado");

        FloatingActionButton fabSwitchCamera = findViewById(R.id.fabSwitchCamera);
        fabSwitchCamera.setOnClickListener(v -> switchCamera());

        // Obtener datos del Intent
        classId     = getIntent().getStringExtra("classId");
        className   = getIntent().getStringExtra("className");
        Log.d(TAG, "classId=" + classId + " | className=" + className);

        // Firebase
        db        = FirebaseFirestore.getInstance();
        mAuth     = FirebaseAuth.getInstance();

        // verificar que el usuario esté autenticado antes de acceder
        if (mAuth.getCurrentUser() == null) {
            Log.e(TAG, "✗ ERROR: Usuario no autenticado");
            Toast.makeText(this, "Error: sesión no iniciada", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        studentId   = mAuth.getCurrentUser().getUid();
        studentName = mAuth.getCurrentUser().getDisplayName();
        Log.d(TAG, "studentId=" + studentId + " | studentName=" + studentName);

        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Registrar Rostro");
        }

        // Inicializar vistas
        previewView   = findViewById(R.id.previewView);
        tvInstruction = findViewById(R.id.tvInstruction);
        tvProgress    = findViewById(R.id.tvProgress);
        progressBar   = findViewById(R.id.progressBar);

        // Executor para cámara
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Configurar Face Detector
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .enableTracking()
                .build();

        faceDetector = FaceDetection.getClient(options);
        Log.d(TAG, "✓ FaceDetector configurado");

        // Verificar si ya tiene rostro registrado
        checkExistingFaceRegistration();
        Log.d(TAG, "════════════════════════════════════");
    }

    // ─────────────────────────────────────────────────────────────────────────
    private void switchCamera() {
        isUsingFrontCamera = !isUsingFrontCamera;
        FaceEmbeddingGenerator.setFrontCamera(isUsingFrontCamera);
        frontStableSince = 0;
        Log.d(TAG, "switchCamera() → usando " + (isUsingFrontCamera ? "FRONTAL" : "TRASERA"));

        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            bindCameraUseCases();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    private void checkExistingFaceRegistration() {
        Log.d(TAG, "checkExistingFaceRegistration() - consultando Firestore...");

        db.collection("faceEmbeddings")
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("classId", classId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        Log.w(TAG, "Ya existe registro facial para este estudiante/clase");
                        Toast.makeText(this, "Ya tienes un rostro registrado en esta clase", Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        Log.d(TAG, "✓ No hay registro previo, iniciando proceso...");
                        checkCameraPermission();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "✗ Error al verificar registro: " + e.getMessage());
                    Toast.makeText(this, "Error al verificar registro, continuando...",
                            Toast.LENGTH_SHORT).show();
                    checkCameraPermission();
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permiso de cámara NO concedido, solicitando...");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE);
        } else {
            Log.d(TAG, "✓ Permiso de cámara ya concedido");
            startCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "✓ Permiso de cámara concedido por el usuario");
                startCamera();
            } else {
                Log.e(TAG, "✗ Permiso de cámara denegado");
                Toast.makeText(this, "Se necesita permiso de cámara", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    private void startCamera() {
        Log.d(TAG, "startCamera() llamado");

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                Log.d(TAG, "✓ CameraProvider obtenido");
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "✗ Error al iniciar cámara: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // ─────────────────────────────────────────────────────────────────────────
    private void bindCameraUseCases() {
        Log.d(TAG, "bindCameraUseCases() - cámara " + (isUsingFrontCamera ? "FRONTAL" : "TRASERA"));

        preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        CameraSelector cameraSelector = isUsingFrontCamera
                ? CameraSelector.DEFAULT_FRONT_CAMERA
                : CameraSelector.DEFAULT_BACK_CAMERA;

        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeFace);

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
            Log.d(TAG, "✓ Cámara vinculada al ciclo de vida");
            updateInstruction();
        } catch (Exception e) {
            Log.e(TAG, "✗ Error al vincular cámara: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @SuppressLint("UnsafeOptInUsageError")
    private void analyzeFace(ImageProxy imageProxy) {
        framesAnalyzed++;

        // Log cada 30 frames para no saturar Logcat
        if (framesAnalyzed % 30 == 0) {
            Log.d(TAG, "analyzeFace() frame #" + framesAnalyzed
                    + " | paso=" + currentStep.name()
                    + " | isProcessing=" + isProcessing);
        }

        if (isProcessing || currentStep == RegistrationStep.STEP_5_COMPLETE) {
            imageProxy.close();
            return;
        }

        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            Log.w(TAG, "mediaImage es NULL en frame #" + framesAnalyzed);
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.getImageInfo().getRotationDegrees()
        );

        faceDetector.process(image)
                .addOnSuccessListener(faces -> processFaceDetectionResult(faces, imageProxy))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "✗ Error en detección ML Kit: " + e.getMessage());
                    imageProxy.close();
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    private void processFaceDetectionResult(List<Face> faces, ImageProxy imageProxy) {
        // Si ya hay una captura en curso, se ignoran resultados atrasados
        if (isProcessing) {
            imageProxy.close();
            return;
        }

        if (faces.isEmpty()) {
            frontStableSince = 0;
            noFaceShown = true;
            if (framesAnalyzed % 30 == 0) {
                Log.d(TAG, "No se detectó ningún rostro en frame #" + framesAnalyzed);
            }
            runOnUiThread(() -> {
                tvInstruction.setText("No se detecta rostro");
                tvInstruction.setTextColor(getColor(android.R.color.holo_red_dark));
            });
            imageProxy.close();
            return;
        }

        // El rostro volvió a aparecer: se restaura la instrucción del paso actual
        if (noFaceShown) {
            noFaceShown = false;
            updateInstruction();
        }

        // Elegir el rostro más grande (el más cercano a la cámara)
        Face face = faces.get(0);
        for (Face f : faces) {
            if (f.getBoundingBox().width() * f.getBoundingBox().height()
                    > face.getBoundingBox().width() * face.getBoundingBox().height()) {
                face = f;
            }
        }

        float angleY = face.getHeadEulerAngleY();
        float angleX = face.getHeadEulerAngleX();

        // Log cada 30 frames
        if (framesAnalyzed % 30 == 0) {
            Log.d(TAG, "Rostro detectado | angleY=" + String.format("%.1f", angleY)
                    + " | angleX=" + String.format("%.1f", angleX)
                    + " | paso=" + currentStep.name());
        }

        switch (currentStep) {
            case STEP_1_FRONT: {
                // La foto de frente NO se toma al instante: el rostro debe estar bien
                // puesto (de frente, buen tamaño, centrado, ojos abiertos) y quieto
                String problem = checkFrontQuality(face, imageProxy);
                long now = System.currentTimeMillis();

                if (problem != null) {
                    frontStableSince = 0;
                    showHint(problem);
                    break;
                }

                if (frontStableSince == 0) {
                    frontStableSince = now;
                }

                if (now - frontStableSince < FRONT_HOLD_MS) {
                    showHint("Mantente quieto...");
                    break;
                }

                frontStableSince = 0;
                Log.d(TAG, "✓ Rostro al frente estable, capturando embedding...");
                captureFaceEmbedding(imageProxy, face);
                return; // imageProxy se cierra dentro de captureFaceEmbedding
            }

            case STEP_2_LEFT:
                Log.d(TAG, "STEP_2_LEFT | angleY=" + String.format("%.1f", angleY));
                if (isFacingLeft(face)) {
                    Log.d(TAG, "✓ Giro a la izquierda detectado, capturando...");
                    captureFaceEmbedding(imageProxy, face);
                    return;
                }
                break;

            case STEP_3_RIGHT:
                Log.d(TAG, "STEP_3_RIGHT | angleY=" + String.format("%.1f", angleY));
                if (isFacingRight(face)) {
                    Log.d(TAG, "✓ Giro a la derecha detectado, capturando...");
                    captureFaceEmbedding(imageProxy, face);
                    return;
                }
                break;

            case STEP_4_BLINK:
                Float leftEye  = face.getLeftEyeOpenProbability();
                Float rightEye = face.getRightEyeOpenProbability();
                if (framesAnalyzed % 10 == 0) {
                    Log.d(TAG, "STEP_4_BLINK | leftEye=" + leftEye + " | rightEye=" + rightEye);
                }
                if (detectBlink(face)) {
                    Log.d(TAG, "✓ Parpadeo detectado, completando registro...");
                    completeRegistration();
                }
                break;

            default:
                break;
        }

        imageProxy.close();
    }

    // ─────────────────────────────────────────────────────────────────────────
    /** @return mensaje del problema, o null si el rostro es apto para la foto de frente. */
    private String checkFrontQuality(Face face, ImageProxy imageProxy) {
        float angleY = face.getHeadEulerAngleY();
        float angleX = face.getHeadEulerAngleX();
        float angleZ = face.getHeadEulerAngleZ();

        if (Math.abs(angleY) >= 10
                || Math.abs(angleX) > MAX_FRONT_TILT
                || Math.abs(angleZ) > MAX_FRONT_TILT) {
            return "Mira de frente a la cámara";
        }

        int rotation = imageProxy.getImageInfo().getRotationDegrees();
        boolean swap = (rotation == 90 || rotation == 270);
        int imgW = swap ? imageProxy.getHeight() : imageProxy.getWidth();
        int imgH = swap ? imageProxy.getWidth() : imageProxy.getHeight();

        Rect box = face.getBoundingBox();

        if (box.width() < MIN_FACE_WIDTH_RATIO * imgW) {
            return "Acerca la cámara al rostro";
        }

        if (box.left < 0 || box.top < 0 || box.right > imgW || box.bottom > imgH) {
            return "Centra tu rostro y aléjate un poco";
        }

        Float leftEye  = face.getLeftEyeOpenProbability();
        Float rightEye = face.getRightEyeOpenProbability();
        if (leftEye != null && rightEye != null && (leftEye < 0.5f || rightEye < 0.5f)) {
            return "Abre bien los ojos";
        }

        return null;
    }

    private void showHint(String text) {
        runOnUiThread(() -> {
            tvInstruction.setText(text);
            tvInstruction.setTextColor(getColor(R.color.primary_purple));
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    private boolean isFacingLeft(Face face) {
        float angleY = face.getHeadEulerAngleY();
        // con cámara frontal el eje Y está invertido
        return isUsingFrontCamera
                ? (angleY < -15 && angleY > -45)
                : (angleY >  15 && angleY <  45);
    }

    private boolean isFacingRight(Face face) {
        float angleY = face.getHeadEulerAngleY();
        // con cámara frontal el eje Y está invertido
        return isUsingFrontCamera
                ? (angleY >  15 && angleY <  45)
                : (angleY < -15 && angleY > -45);
    }

    // ─────────────────────────────────────────────────────────────────────────
    private boolean detectBlink(Face face) {
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastBlinkCheckTime < 200) return false;
        lastBlinkCheckTime = currentTime;

        Float leftEyeOpenProb  = face.getLeftEyeOpenProbability();
        Float rightEyeOpenProb = face.getRightEyeOpenProbability();

        if (leftEyeOpenProb == null || rightEyeOpenProb == null) {
            Log.w(TAG, "detectBlink: probabilidad de ojos es NULL");
            return false;
        }

        float avgEyeOpenProb = (leftEyeOpenProb + rightEyeOpenProb) / 2;

        if (previousEyeOpenProbability > 0.7f && avgEyeOpenProb < 0.3f) {
            Log.d(TAG, "detectBlink: ojos CERRADOS detectados (avg=" + avgEyeOpenProb + ")");
            hasDetectedBlink = true;
        } else if (hasDetectedBlink && avgEyeOpenProb > 0.7f) {
            Log.d(TAG, "detectBlink: ojos ABIERTOS de nuevo → parpadeo completo ✓");
            hasDetectedBlink = false;
            return true;
        }

        previousEyeOpenProbability = avgEyeOpenProb;
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    private void captureFaceEmbedding(ImageProxy imageProxy, Face face) {
        Log.d(TAG, "════════════════════════════════════");
        Log.d(TAG, "captureFaceEmbedding() llamado");

        if (isProcessing) {
            Log.d(TAG, "Ya está procesando, ignorando frame");
            imageProxy.close();
            return;
        }

        isProcessing = true;

        Log.d(TAG, "1. Convirtiendo ImageProxy a Bitmap...");
        int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
        Bitmap bitmap = imageProxyToBitmap(imageProxy);
        imageProxy.close();

        if (bitmap == null) {
            Log.e(TAG, "✗ Bitmap es NULL después de conversión");
            isProcessing = false;
            return;
        }

        Log.d(TAG, "✓ Bitmap obtenido: " + bitmap.getWidth() + "x" + bitmap.getHeight());
        FaceEmbeddingGenerator.setRotationDegrees(rotationDegrees);
        List<Float> embedding = FaceEmbeddingGenerator.generateEmbedding(bitmap, face);
        bitmap.recycle();

        if (embedding != null && !embedding.isEmpty()) {
            Log.d(TAG, "✓✓✓ EMBEDDING CAPTURADO EXITOSAMENTE ✓✓✓");
            Log.d(TAG, "Tamaño del embedding: " + embedding.size());

            capturedEmbeddings.add(embedding);
            runOnUiThread(this::advanceStep);
        } else {
            Log.e(TAG, "✗✗✗ ERROR: Embedding NULL o vacío ✗✗✗");
            runOnUiThread(() -> {
                Toast.makeText(this, "Error al procesar rostro, intenta de nuevo", Toast.LENGTH_SHORT).show();
                isProcessing = false;
            });
        }

        Log.d(TAG, "════════════════════════════════════");
    }

    // ─────────────────────────────────────────────────────────────────────────
    @SuppressLint("UnsafeOptInUsageError")
    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        try {
            Image image = imageProxy.getImage();
            if (image == null) {
                Log.e(TAG, "imageProxyToBitmap: image es NULL");
                return null;
            }

            Log.d(TAG, "imageProxyToBitmap: " + image.getWidth() + "x" + image.getHeight()
                    + " format=" + image.getFormat());

            ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
            ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
            ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21,
                    image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(
                    new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()),
                    100, out
            );
            byte[] imageBytes = out.toByteArray();

            Bitmap result = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            if (result == null) {
                return null;
            }
            Log.d(TAG, "✓ imageProxyToBitmap OK: " + result.getWidth() + "x" + result.getHeight());
            return result;

        } catch (Exception e) {
            Log.e(TAG, "✗ imageProxyToBitmap ERROR: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    private void advanceStep() {
        isProcessing = false;

        Log.d(TAG, "advanceStep() desde " + currentStep.name());

        switch (currentStep) {
            case STEP_1_FRONT:  currentStep = RegistrationStep.STEP_2_LEFT;  break;
            case STEP_2_LEFT:   currentStep = RegistrationStep.STEP_3_RIGHT; break;
            case STEP_3_RIGHT:  currentStep = RegistrationStep.STEP_4_BLINK; break;
            default: break;
        }

        Log.d(TAG, "→ nuevo paso: " + currentStep.name());
        updateInstruction();
    }

    // ─────────────────────────────────────────────────────────────────────────
    private void updateInstruction() {
        runOnUiThread(() -> {
            tvInstruction.setText(currentStep.instruction);
            tvInstruction.setTextColor(getColor(R.color.primary_purple));
            progressBar.setProgress(currentStep.progress);

            int stepNumber = currentStep.ordinal() + 1;
            tvProgress.setText("Paso " + stepNumber + " de 4");

            Log.d(TAG, "UI actualizada → paso " + stepNumber + ": " + currentStep.instruction);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    private void completeRegistration() {
        currentStep  = RegistrationStep.STEP_5_COMPLETE;
        isProcessing = true;

        Log.d(TAG, "completeRegistration() - guardando en Firebase...");

        runOnUiThread(() -> {
            tvInstruction.setText("✓ Registro completo");
            tvInstruction.setTextColor(getColor(android.R.color.holo_green_dark));
            progressBar.setProgress(100);
            tvProgress.setText("Guardando...");
        });

        saveFaceEmbedding();
    }

    // ─────────────────────────────────────────────────────────────────────────
    private void saveFaceEmbedding() {
        if (capturedEmbeddings == null || capturedEmbeddings.isEmpty()) {
            Log.e(TAG, "✗ saveFaceEmbedding: capturedEmbedding es NULL");
            Toast.makeText(this, "Error: No se capturó el embedding", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "saveFaceEmbedding() | tamaño embeddings=" + capturedEmbeddings.size());

        Map<String, Object> embeddingData = new HashMap<>();
        embeddingData.put("studentId",    studentId);
        embeddingData.put("studentName",  studentName != null ? studentName : "Sin nombre");
        embeddingData.put("classId",      classId);
        embeddingData.put("embedding",    capturedEmbeddings.get(0));
        if (capturedEmbeddings.size() > 1)
            embeddingData.put("embeddingLeft",  capturedEmbeddings.get(1));
        if (capturedEmbeddings.size() > 2)
            embeddingData.put("embeddingRight", capturedEmbeddings.get(2));
        embeddingData.put("registeredAt", System.currentTimeMillis());

        db.collection("faceEmbeddings")
                .add(embeddingData)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "✓✓✓ Embedding guardado en Firestore: " + documentReference.getId());
                    runOnUiThread(() -> {
                        Toast.makeText(this, "✓ Rostro registrado exitosamente", Toast.LENGTH_LONG).show();
                        finish();
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "✗ Error al guardar en Firestore: " + e.getMessage());
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        isProcessing = false;
                    });
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy() - liberando recursos");

        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            Log.d(TAG, "✓ cameraExecutor cerrado");
        }
        if (faceDetector != null) {
            faceDetector.close();
            Log.d(TAG, "✓ faceDetector cerrado");
        }
        FaceEmbeddingGenerator.release();
        Log.d(TAG, "✓ FaceEmbeddingGenerator liberado");
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}