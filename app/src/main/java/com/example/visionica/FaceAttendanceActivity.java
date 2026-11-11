package com.example.visionica;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class FaceAttendanceActivity extends AppCompatActivity {

    private static final String TAG = "FaceAttendance";
    private static final int CAMERA_PERMISSION_CODE = 100;

    // ── Ajustes de vivacidad (parpadeo) ─────────────────────────────
    // Tiempo máximo esperando un parpadeo antes de avisar "presencia humana real"
    private static final long BLINK_TIMEOUT_MS = 8000;
    // Tras parpadear, durante este tiempo se reintenta reconocer en cada frame
    private static final long LIVENESS_WINDOW_MS = 6000;
    private static final float EYE_CLOSED_PROB = 0.4f;
    private static final float EYE_OPEN_PROB = 0.6f;

    // ── Ajustes de calidad de rostro ────────────────────────────────
    private static final float MAX_HEAD_ANGLE = 25f;          // grados (giro lateral / inclinación)
    private static final float MIN_FACE_WIDTH_RATIO = 0.18f;  // ancho del rostro / ancho de la imagen

    // ── Confirmación: cuántos frames seguidos deben coincidir con el mismo estudiante.
    //    Pon 1 si quieres aceptar con el primer frame que supere el umbral.
    private static final int REQUIRED_CONSECUTIVE_MATCHES = 2;

    private final Set<String> registeredInSession =
            Collections.synchronizedSet(new HashSet<String>());

    private String classId;
    private String className;

    // Views
    private PreviewView previewView;
    private TextView tvStatus;
    private TextView tvStudentName;
    private ProgressBar progressBar;
    private FloatingActionButton fabSwitchCamera;

    // Camera
    private ProcessCameraProvider cameraProvider;
    private Preview preview;
    private ImageAnalysis imageAnalysis;
    private ExecutorService cameraExecutor;

    // Empieza con cámara trasera, pero se puede cambiar a frontal
    private int lensFacing = CameraSelector.LENS_FACING_BACK;

    // ML Kit
    private FaceDetector faceDetector;

    // Firebase
    private FirebaseFirestore db;

    // Datos de estudiantes registrados
    private List<FaceEmbedding> registeredFaces;

    // Estado de procesamiento / vivacidad
    private volatile boolean isProcessing = false;
    private volatile boolean destroyed = false;

    private boolean eyesClosedSeen = false;
    private volatile long livenessValidUntil = 0;
    private boolean livenessWasActive = false;
    private Integer livenessTrackingId = null;
    private volatile int attemptsInWindow = 0;
    private long faceFirstSeenTime = 0;
    private long warningUntil = 0;

    private volatile String candidateId = null;
    private volatile int consecutiveMatches = 0;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Dialog activeDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_attendance);

        FaceEmbeddingGenerator.initialize(this);
        FaceEmbeddingGenerator.setFrontCamera(lensFacing == CameraSelector.LENS_FACING_FRONT);

        classId = getIntent().getStringExtra("classId");
        className = getIntent().getStringExtra("className");

        db = FirebaseFirestore.getInstance();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Tomar Asistencia");
        }

        previewView = findViewById(R.id.previewView);
        tvStatus = findViewById(R.id.tvStatus);
        tvStudentName = findViewById(R.id.tvStudentName);
        progressBar = findViewById(R.id.progressBar);
        fabSwitchCamera = findViewById(R.id.fabSwitchCamera);

        cameraExecutor = Executors.newSingleThreadExecutor();

        fabSwitchCamera.setOnClickListener(v -> switchCamera());

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .enableTracking()
                .build();

        faceDetector = FaceDetection.getClient(options);

        loadRegisteredFaces();
    }

    private void switchCamera() {
        if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            lensFacing = CameraSelector.LENS_FACING_FRONT;
            FaceEmbeddingGenerator.setFrontCamera(true);
            Toast.makeText(this, "Cámara frontal", Toast.LENGTH_SHORT).show();
        } else {
            lensFacing = CameraSelector.LENS_FACING_BACK;
            FaceEmbeddingGenerator.setFrontCamera(false);
            Toast.makeText(this, "Cámara trasera", Toast.LENGTH_SHORT).show();
        }

        resetScanning();

        if (cameraProvider != null) {
            bindCameraUseCases();
        }
    }

    private void loadRegisteredFaces() {
        registeredFaces = new ArrayList<>();

        Log.d(TAG, "Cargando rostros de classId: " + classId);

        db.collection("faceEmbeddings")
                .whereEqualTo("classId", classId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        FaceEmbedding faceEmbedding = doc.toObject(FaceEmbedding.class);

                        if (faceEmbedding != null) {
                            faceEmbedding.setId(doc.getId());
                            registeredFaces.add(faceEmbedding);

                            Log.d(TAG, "Rostro cargado: " + faceEmbedding.getStudentName());
                            Log.d(TAG, "StudentId: " + faceEmbedding.getStudentId());
                        }
                    }

                    Log.d(TAG, "Rostros cargados: " + registeredFaces.size());

                    if (registeredFaces.isEmpty()) {
                        Toast.makeText(this, "No hay estudiantes registrados en esta clase",
                                Toast.LENGTH_LONG).show();
                    }

                    checkCameraPermission();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this,
                            "No se pudieron cargar los rostros. Revisa tu conexión y vuelve a intentar.",
                            Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error cargando rostros: " + e.getMessage());
                    checkCameraPermission();
                });
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE
            );

        } else {
            startCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Se necesita permiso de cámara", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error al iniciar cámara: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeFace);

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build();

        try {
            cameraProvider.unbindAll();

            cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
            );

            Log.d(TAG, lensFacing == CameraSelector.LENS_FACING_FRONT
                    ? "Cámara frontal activada"
                    : "Cámara trasera activada");

        } catch (Exception e) {
            Log.e(TAG, "Error al vincular cámara: " + e.getMessage());
            Toast.makeText(this, "No se pudo cambiar la cámara", Toast.LENGTH_SHORT).show();
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  ANÁLISIS DE FRAMES
    // ════════════════════════════════════════════════════════════════

    @SuppressLint("UnsafeOptInUsageError")
    private void analyzeFace(ImageProxy imageProxy) {
        if (isProcessing || destroyed) {
            imageProxy.close();
            return;
        }

        Image mediaImage = imageProxy.getImage();

        if (mediaImage == null) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.getImageInfo().getRotationDegrees()
        );

        faceDetector.process(image)
                .addOnSuccessListener(faces -> {
                    // Si recognizeFace toma el control del ImageProxy, él lo cierra.
                    boolean ownershipTaken = false;
                    try {
                        ownershipTaken = processFaceDetectionResult(faces, imageProxy);
                    } catch (Exception e) {
                        Log.e(TAG, "Error procesando rostros: " + e.getMessage());
                    } finally {
                        if (!ownershipTaken) {
                            imageProxy.close();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error en detección: " + e.getMessage());
                    imageProxy.close();
                });
    }

    /**
     * @return true si recognizeFace se quedó con el ImageProxy (y lo cerrará él).
     */
    private boolean processFaceDetectionResult(List<Face> faces, ImageProxy imageProxy) {
        // Si ya hay un reconocimiento en curso, ignorar resultados atrasados.
        if (isProcessing || destroyed) {
            return false;
        }

        long now = System.currentTimeMillis();

        // ── Sin rostro: se reinicia todo el estado de vivacidad ──
        if (faces.isEmpty()) {
            faceFirstSeenTime = 0;
            eyesClosedSeen = false;
            livenessWasActive = false;
            livenessValidUntil = 0;
            livenessTrackingId = null;
            resetRecognitionState();

            if (now >= warningUntil) {
                setStatus("No se detecta rostro", R.color.text_secondary);
            }
            runOnUiThread(() -> tvStudentName.setVisibility(View.GONE));
            return false;
        }

        // ── Elegir el rostro más grande (el más cercano a la cámara) ──
        Face face = faces.get(0);
        for (Face f : faces) {
            if (area(f.getBoundingBox()) > area(face.getBoundingBox())) {
                face = f;
            }
        }

        if (faceFirstSeenTime == 0) {
            faceFirstSeenTime = now;
        }

        // ── Si cambió la persona (tracking id) se invalida la vivacidad anterior ──
        Integer trackId = face.getTrackingId();
        if (livenessWasActive && livenessTrackingId != null && trackId != null
                && !trackId.equals(livenessTrackingId)) {
            Log.d(TAG, "Cambió el rostro rastreado, se exige nuevo parpadeo");
            livenessValidUntil = 0;
            livenessWasActive = false;
            livenessTrackingId = null;
            resetRecognitionState();
        }

        boolean livenessOk = now < livenessValidUntil;

        // ── La ventana de vivacidad venció ──
        if (!livenessOk && livenessWasActive) {
            livenessWasActive = false;
            livenessTrackingId = null;
            int attempts = attemptsInWindow;
            resetRecognitionState();
            faceFirstSeenTime = now;

            if (attempts > 0) {
                warningUntil = now + 2500;
                setStatus("Estudiante no registrado en esta clase",
                        android.R.color.holo_orange_dark);
            }
        }

        // ── Detectar parpadeo y abrir ventana de vivacidad ──
        if (!livenessOk && detectBlink(face)) {
            livenessValidUntil = now + LIVENESS_WINDOW_MS;
            livenessWasActive = true;
            livenessTrackingId = trackId;
            resetRecognitionState();
            livenessOk = true;
            Log.d(TAG, "Parpadeo detectado: ventana de reconocimiento abierta");
        }

        // ── Todavía sin vivacidad: pedir parpadeo ──
        if (!livenessOk) {
            if (now - faceFirstSeenTime > BLINK_TIMEOUT_MS) {
                warningUntil = now + 2500;
                faceFirstSeenTime = now;
                setStatus("⚠️ No se detecta parpadeo — se requiere presencia humana real",
                        android.R.color.holo_red_dark);
            } else if (now >= warningUntil) {
                setStatus("Parpadea para verificar presencia humana...", R.color.primary_purple);
            }
            return false;
        }

        // ── Con vivacidad: validar calidad del rostro y reconocer ──
        int rotation = imageProxy.getImageInfo().getRotationDegrees();
        boolean swap = (rotation == 90 || rotation == 270);
        int imgW = swap ? imageProxy.getHeight() : imageProxy.getWidth();
        int imgH = swap ? imageProxy.getWidth() : imageProxy.getHeight();

        String problem = checkFaceQuality(face, imgW, imgH);
        if (problem != null) {
            setStatus(problem, R.color.primary_purple);
            return false;
        }

        return recognizeFace(imageProxy, face, rotation);
    }

    private long area(Rect r) {
        return (long) r.width() * (long) r.height();
    }

    /** @return mensaje de problema, o null si el rostro es apto para reconocer. */
    private String checkFaceQuality(Face face, int imgW, int imgH) {
        Rect box = face.getBoundingBox();

        if (Math.abs(face.getHeadEulerAngleY()) > MAX_HEAD_ANGLE
                || Math.abs(face.getHeadEulerAngleZ()) > MAX_HEAD_ANGLE) {
            return "Mira de frente a la cámara";
        }

        if (box.width() < MIN_FACE_WIDTH_RATIO * imgW) {
            return "Acerca la cámara al rostro";
        }

        if (box.left < 0 || box.top < 0 || box.right > imgW || box.bottom > imgH) {
            return "Centra tu rostro y aléjate un poco";
        }

        return null;
    }

    /**
     * Parpadeo = se vio el ojo cerrado y después se vio abierto.
     * Sin limitar la frecuencia de muestreo para no perder parpadeos rápidos.
     */
    private boolean detectBlink(Face face) {
        Float leftEyeOpenProb = face.getLeftEyeOpenProbability();
        Float rightEyeOpenProb = face.getRightEyeOpenProbability();

        if (leftEyeOpenProb == null || rightEyeOpenProb == null) {
            return false;
        }

        float avg = (leftEyeOpenProb + rightEyeOpenProb) / 2f;

        if (avg < EYE_CLOSED_PROB) {
            eyesClosedSeen = true;
        } else if (avg > EYE_OPEN_PROB && eyesClosedSeen) {
            eyesClosedSeen = false;
            return true;
        }

        return false;
    }

    // ════════════════════════════════════════════════════════════════
    //  RECONOCIMIENTO (en hilo de fondo)
    // ════════════════════════════════════════════════════════════════

    /**
     * @return true si tomó el control del ImageProxy (lo cierra en el hilo de fondo).
     */
    private boolean recognizeFace(ImageProxy imageProxy, Face face, int rotationDegrees) {
        isProcessing = true;
        setStatus("Analizando rostro...", R.color.primary_purple);

        try {
            cameraExecutor.execute(() -> {
                Bitmap bitmap = null;
                try {
                    // Ya devuelve el bitmap rotado: coincide con el boundingBox de ML Kit
                    bitmap = imageProxyToBitmap(imageProxy, rotationDegrees);
                } finally {
                    imageProxy.close();
                }

                if (bitmap == null) {
                    isProcessing = false;
                    return;
                }

                try {
                    processEmbedding(bitmap, face);
                } catch (Exception e) {
                    Log.e(TAG, "Error en reconocimiento: " + e.getMessage());
                    isProcessing = false;
                }
            });
            return true;

        } catch (RejectedExecutionException e) {
            isProcessing = false;
            return false;
        }
    }

    private void processEmbedding(Bitmap bitmap, Face face) {
        // El bitmap ya está rotado, por eso se indica 0 al generador (evita rotar dos veces)
        FaceEmbeddingGenerator.setRotationDegrees(0);

        List<Float> currentEmbedding = FaceEmbeddingGenerator.generateEmbedding(bitmap, face);

        if (currentEmbedding == null || currentEmbedding.isEmpty()) {
            Log.d(TAG, "No se pudo generar embedding en este frame, se reintenta");
            isProcessing = false;
            return;
        }

        attemptsInWindow++;

        FaceEmbedding match = findMatchingStudent(currentEmbedding);

        if (match == null) {
            candidateId = null;
            consecutiveMatches = 0;
            isProcessing = false;   // reintenta con el siguiente frame SIN pedir otro parpadeo
            return;
        }

        String id = match.getStudentId();
        if (Objects.equals(id, candidateId)) {
            consecutiveMatches++;
        } else {
            candidateId = id;
            consecutiveMatches = 1;
        }

        if (consecutiveMatches < REQUIRED_CONSECUTIVE_MATCHES) {
            isProcessing = false;   // falta confirmar con otro frame
            return;
        }

        // ── Confirmado ──
        livenessValidUntil = 0;
        livenessWasActive = false;
        resetRecognitionState();
        onStudentRecognized(match);   // isProcessing sigue en true hasta resetScanning()
    }

    private void onStudentRecognized(FaceEmbedding student) {
        if (destroyed) return;

        final String id = student.getStudentId();
        final String name = student.getStudentName();

        if (id != null && registeredInSession.contains(id)) {
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                tvStatus.setText("Ya registrado en esta sesión:");
                tvStatus.setTextColor(getColor(android.R.color.holo_orange_dark));
                tvStudentName.setText(name);
                tvStudentName.setVisibility(View.VISIBLE);
            });
            mainHandler.postDelayed(this::resetScanning, 2500);
            return;
        }

        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            tvStatus.setText("Estudiante detectado:");
            tvStatus.setTextColor(getColor(android.R.color.holo_green_dark));
            tvStudentName.setText(name);
            tvStudentName.setVisibility(View.VISIBLE);
        });

        mainHandler.postDelayed(() -> showAttendanceStatusDialog(id, name), 1000);
    }

    private void resetRecognitionState() {
        candidateId = null;
        consecutiveMatches = 0;
        attemptsInWindow = 0;
    }

    private FaceEmbedding findMatchingStudent(List<Float> currentEmbedding) {

        if (registeredFaces == null || registeredFaces.isEmpty()
                || currentEmbedding == null || currentEmbedding.isEmpty()) {
            return null;
        }

        float threshold = FaceRecognitionHelper.getSimilarityThreshold();
        float minMargin = FaceRecognitionHelper.getMinMargin();

        float bestSimilarity = -1f;
        float secondBestSimilarity = -1f;

        FaceEmbedding bestMatch = null;

        Log.d(TAG, "========== COMPARACIÓN FACIAL ==========");
        Log.d(TAG, "Estudiantes registrados: " + registeredFaces.size());
        Log.d(TAG, "Umbral: " + threshold + " | Margen: " + minMargin);

        for (FaceEmbedding registered : registeredFaces) {

            float frontal = FaceRecognitionHelper.calculateCosineSimilarity(
                    currentEmbedding, registered.getEmbedding());

            float left = registered.getEmbeddingLeft() != null
                    ? FaceRecognitionHelper.calculateCosineSimilarity(
                    currentEmbedding, registered.getEmbeddingLeft())
                    : -1f;

            float right = registered.getEmbeddingRight() != null
                    ? FaceRecognitionHelper.calculateCosineSimilarity(
                    currentEmbedding, registered.getEmbeddingRight())
                    : -1f;

            // Mayor similitud entre las tres posiciones.
            // Las fotos de lado pesan menos: son menos confiables que la frontal
            float similarity = Math.max(frontal, Math.max(left, right) - 0.05f);

            Log.d(TAG, "Estudiante: " + registered.getStudentName()
                    + " | Frontal: " + frontal
                    + " | Izq: " + left
                    + " | Der: " + right
                    + " | Mejor similitud: " + similarity);

            if (similarity > bestSimilarity) {
                secondBestSimilarity = bestSimilarity;
                bestSimilarity = similarity;
                bestMatch = registered;
            } else if (similarity > secondBestSimilarity) {
                secondBestSimilarity = similarity;
            }
        }

        Log.d(TAG, "Mejor puntuación: " + bestSimilarity);
        Log.d(TAG, "Segunda puntuación: " + secondBestSimilarity);

        if (bestMatch == null || bestSimilarity < threshold) {
            Log.d(TAG, "Sin coincidencia suficiente");
            return null;
        }

        // Con más de un estudiante, el mejor debe destacar sobre el segundo
        if (registeredFaces.size() > 1 && (bestSimilarity - secondBestSimilarity) < minMargin) {
            Log.d(TAG, "Coincidencia ambigua (margen insuficiente)");
            return null;
        }

        Log.d(TAG, "Identidad candidata: " + bestMatch.getStudentName());

        return bestMatch;
    }

    // ════════════════════════════════════════════════════════════════
    //  CONVERSIÓN DE IMAGEN
    // ════════════════════════════════════════════════════════════════

    /** Convierte el frame YUV_420_888 a Bitmap respetando rowStride/pixelStride y lo rota. */
    @SuppressLint("UnsafeOptInUsageError")
    private Bitmap imageProxyToBitmap(ImageProxy imageProxy, int rotationDegrees) {
        try {
            Image image = imageProxy.getImage();

            if (image == null) {
                return null;
            }

            int width = image.getWidth();
            int height = image.getHeight();

            byte[] nv21 = yuv420ToNv21(image);

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, width, height), 95, out);

            byte[] imageBytes = out.toByteArray();
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

            if (bitmap == null) {
                return null;
            }

            if (rotationDegrees != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotationDegrees);
                Bitmap rotated = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                if (rotated != bitmap) {
                    bitmap.recycle();
                }
                return rotated;
            }

            return bitmap;

        } catch (Exception e) {
            Log.e(TAG, "Error converting image: " + e.getMessage());
            return null;
        }
    }

    private byte[] yuv420ToNv21(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        Image.Plane[] planes = image.getPlanes();

        byte[] nv21 = new byte[width * height * 3 / 2];
        int pos = 0;

        // Plano Y (puede tener padding al final de cada fila)
        ByteBuffer yBuf = planes[0].getBuffer();
        int yRowStride = planes[0].getRowStride();
        for (int row = 0; row < height; row++) {
            yBuf.position(row * yRowStride);
            yBuf.get(nv21, pos, width);
            pos += width;
        }

        // Planos U y V intercalados como VU (formato NV21)
        ByteBuffer uBuf = planes[1].getBuffer();
        ByteBuffer vBuf = planes[2].getBuffer();
        int uvRowStride = planes[1].getRowStride();
        int uvPixelStride = planes[1].getPixelStride();

        for (int row = 0; row < height / 2; row++) {
            for (int col = 0; col < width / 2; col++) {
                int index = row * uvRowStride + col * uvPixelStride;
                nv21[pos++] = vBuf.get(index);
                nv21[pos++] = uBuf.get(index);
            }
        }

        return nv21;
    }

    // ════════════════════════════════════════════════════════════════
    //  DIÁLOGO Y GUARDADO DE ASISTENCIA
    // ════════════════════════════════════════════════════════════════

    private void showAttendanceStatusDialog(String studentId, String studentName) {
        if (destroyed || isFinishing()) return;

        Dialog dialog = new Dialog(this);
        activeDialog = dialog;
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_facial_status);
        // Evita que un toque fuera cierre el diálogo y deje el escaneo bloqueado
        dialog.setCancelable(false);

        TextView tvStudentNameDialog = dialog.findViewById(R.id.tvStudentName);
        RadioGroup rgStatus = dialog.findViewById(R.id.rgStatus);
        RadioButton rbPresent = dialog.findViewById(R.id.rbPresent);
        Button btnCancel = dialog.findViewById(R.id.btnCancelStatus);
        Button btnConfirm = dialog.findViewById(R.id.btnConfirmStatus);

        tvStudentNameDialog.setText("Estudiante: " + studentName);
        rbPresent.setChecked(true);

        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
            resetScanning();
        });

        btnConfirm.setOnClickListener(v -> {
            int selectedId = rgStatus.getCheckedRadioButtonId();
            String status = "present";

            if (selectedId == R.id.rbPresent) {
                status = "present";
            } else if (selectedId == R.id.rbLate) {
                status = "late";
            }

            registerAttendance(studentId, studentName, status);
            dialog.dismiss();
        });

        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT
            );
            window.setGravity(android.view.Gravity.BOTTOM);
        }
    }

    private void registerAttendance(String studentId, String studentName, String status) {

        // Evita duplicados en la misma sesión
        if (registeredInSession.contains(studentId)) {
            Toast.makeText(this, studentName + " ya fue registrado en esta sesión",
                    Toast.LENGTH_SHORT).show();
            resetScanning();
            return;
        }
        registeredInSession.add(studentId);

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String today = dateFormat.format(new Date());

        db.collection("attendance")
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("classId", classId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    boolean alreadyRegisteredToday = false;

                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Long timestamp = doc.getLong("timestamp");

                        if (timestamp != null) {
                            String docDate = dateFormat.format(new Date(timestamp));

                            if (docDate.equals(today)) {
                                alreadyRegisteredToday = true;
                                break;
                            }
                        }
                    }

                    if (alreadyRegisteredToday) {
                        Toast.makeText(this,
                                "Asistencia ya registrada para " + studentName + " hoy",
                                Toast.LENGTH_LONG).show();
                        resetScanning();
                        return;
                    }

                    saveAttendance(studentId, studentName, status);
                })
                .addOnFailureListener(e -> saveAttendance(studentId, studentName, status));
    }

    private void saveAttendance(String studentId, String studentName, String status) {
        SyncManager.saveAttendance(
                /* context     */ this,
                /* classId     */ classId,
                /* className   */ className,
                /* studentId   */ studentId,
                /* studentName */ studentName,
                /* status      */ status,

                // Callback: se guardó en Firebase (hay internet)
                () -> runOnUiThread(() -> {
                    saveAttendanceNotification(studentId, status);
                    tvStatus.setText("✓ Asistencia registrada");
                    tvStudentName.setText(studentName);
                    tvStudentName.setVisibility(View.VISIBLE);
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this,
                            "Asistencia de " + studentName + " guardada",
                            Toast.LENGTH_SHORT).show();
                    mainHandler.postDelayed(this::resetScanning, 2500);
                }),

                // Callback: no hay internet, guardado localmente
                () -> runOnUiThread(() -> {
                    tvStatus.setText("📴 Sin conexión — asistencia guardada localmente");
                    tvStudentName.setText(studentName);
                    tvStudentName.setVisibility(View.VISIBLE);
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this,
                            "Asistencia guardada sin conexión. Se sincronizará cuando vuelva el internet.",
                            Toast.LENGTH_LONG).show();
                    mainHandler.postDelayed(this::resetScanning, 2500);
                })
        );
    }

    private void saveAttendanceNotification(String studentId, String status) {
        NotificationModel notif =
                new NotificationModel(studentId, classId, className, status);

        Map<String, Object> data = new HashMap<>();
        data.put("userId",    notif.getUserId());
        data.put("type",      notif.getType());
        data.put("title",     notif.getTitle());
        data.put("message",   notif.getMessage());
        data.put("classId",   notif.getClassId());
        data.put("className", notif.getClassName());
        data.put("status",    notif.getStatus());
        data.put("timestamp", notif.getTimestamp());
        data.put("read",      false);

        db.collection("notifications")
                .add(data)
                .addOnSuccessListener(ref ->
                        Log.d(TAG, "Notificación de asistencia guardada: " + ref.getId()))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Error al guardar notificación: " + e.getMessage()));
    }

    // ════════════════════════════════════════════════════════════════
    //  UTILIDADES
    // ════════════════════════════════════════════════════════════════

    private void setStatus(String text, int colorRes) {
        runOnUiThread(() -> {
            if (destroyed) return;
            tvStatus.setText(text);
            tvStatus.setTextColor(getColor(colorRes));
        });
    }

    private void resetScanning() {
        isProcessing = false;
        eyesClosedSeen = false;
        livenessValidUntil = 0;
        livenessWasActive = false;
        livenessTrackingId = null;
        faceFirstSeenTime = 0;
        warningUntil = 0;
        resetRecognitionState();

        runOnUiThread(() -> {
            if (destroyed) return;
            tvStatus.setText("Verificando... acerca la cámara al rostro");
            tvStatus.setTextColor(getColor(R.color.text_secondary));
            tvStudentName.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);
        });
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        super.onDestroy();

        mainHandler.removeCallbacksAndMessages(null);

        if (activeDialog != null && activeDialog.isShowing()) {
            activeDialog.dismiss();
        }

        if (cameraExecutor != null) {
            try {
                // release() se ejecuta en el mismo hilo que usa el generador, tras las tareas pendientes
                cameraExecutor.execute(FaceEmbeddingGenerator::release);
            } catch (RejectedExecutionException e) {
                FaceEmbeddingGenerator.release();
            }
            cameraExecutor.shutdown();
        }

        if (faceDetector != null) {
            faceDetector.close();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}