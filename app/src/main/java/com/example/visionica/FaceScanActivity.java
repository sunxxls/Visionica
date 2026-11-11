package com.example.visionica;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

public class FaceScanActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int CAMERA_REQUEST_CODE = 101;

    private String classId;
    private String className;

    private TextView tvInstructions;
    private Button btnStartScanning;
    private ImageView ivScanPreview;
    private Button btnProcessScan;

    private FirebaseFirestore db;
    private Bitmap scannedImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_scan);

        // Inicializar Firebase
        db = FirebaseFirestore.getInstance();

        // Obtener datos
        classId = getIntent().getStringExtra("classId");
        className = getIntent().getStringExtra("className");

        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Tomar Asistencia");
        }

        // Inicializar vistas
        tvInstructions = findViewById(R.id.tvInstructions);
        btnStartScanning = findViewById(R.id.btnStartScanning);
        ivScanPreview = findViewById(R.id.ivScanPreview);
        btnProcessScan = findViewById(R.id.btnProcessScan);

        // Ocultar preview inicialmente
        ivScanPreview.setVisibility(android.view.View.GONE);
        btnProcessScan.setVisibility(android.view.View.GONE);

        tvInstructions.setText("Apunta la cámara al rostro del estudiante. El sistema simulará el reconocimiento facial.");

        // Setup listeners
        btnStartScanning.setOnClickListener(v -> {
            checkCameraPermissionAndOpen();
        });

        btnProcessScan.setOnClickListener(v -> {
            processScannedFace();
        });
    }

    private void checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE);
        } else {
            openCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Se necesita permiso de cámara para escanear rostros",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
        } else {
            Toast.makeText(this, "No se puede abrir la cámara", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null && data.getExtras() != null) {
                scannedImage = (Bitmap) data.getExtras().get("data");

                // Mostrar preview
                ivScanPreview.setImageBitmap(scannedImage);
                ivScanPreview.setVisibility(android.view.View.VISIBLE);

                // Mostrar botón de procesar
                btnProcessScan.setVisibility(android.view.View.VISIBLE);

                // Cambiar instrucciones
                tvInstructions.setText("Rostro capturado. Procesando reconocimiento...");

                // Cambiar texto del botón
                btnStartScanning.setText("Escanear otro rostro");
            }
        }
    }

    private void processScannedFace() {
        if (scannedImage == null) {
            Toast.makeText(this, "Primero debes escanear un rostro", Toast.LENGTH_SHORT).show();
            return;
        }

        // Simular procesamiento
        btnProcessScan.setEnabled(false);
        btnProcessScan.setText("Procesando...");

        // Simular búsqueda de rostro en base de datos
        // En producción, aquí compararías el face encoding con TensorFlow

        // Buscar estudiantes con rostro registrado
        db.collection("faceData")
                .whereEqualTo("classId", classId)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            String userId = document.getString("userId");
                            String userName = document.getString("userName");

                            // Simular que encontró un match
                            Toast.makeText(this, "✓ Rostro reconocido: " + userName,
                                    Toast.LENGTH_LONG).show();

                            // Mostrar diálogo para seleccionar estado
                            showAttendanceStatusDialog(userId, userName);
                            break;
                        }
                    } else {
                        Toast.makeText(this,
                                "⚠ No se encontró coincidencia. Verifica que el estudiante haya registrado su rostro.",
                                Toast.LENGTH_LONG).show();
                        btnProcessScan.setEnabled(true);
                        btnProcessScan.setText("Procesar Rostro");
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnProcessScan.setEnabled(true);
                    btnProcessScan.setText("Procesar Rostro");
                });
    }

    private void showAttendanceStatusDialog(String studentId, String studentName) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_attendance_status);

        TextView tvStudentName = dialog.findViewById(R.id.tvStudentName);
        RadioGroup rgStatus = dialog.findViewById(R.id.rgStatus);
        RadioButton rbPresent = dialog.findViewById(R.id.rbPresent);
        Button btnCancel = dialog.findViewById(R.id.btnCancelStatus);
        Button btnConfirm = dialog.findViewById(R.id.btnConfirmStatus);

        tvStudentName.setText("Estudiante: " + studentName);
        rbPresent.setChecked(true);

        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
            btnProcessScan.setEnabled(true);
            btnProcessScan.setText("Procesar Rostro");
        });

        btnConfirm.setOnClickListener(v -> {
            int selectedId = rgStatus.getCheckedRadioButtonId();
            String status = "present";

            if (selectedId == R.id.rbPresent) {
                status = "present";
            } else if (selectedId == R.id.rbLate) {
                status = "late";
            } else if (selectedId == R.id.rbAbsent) {
                status = "absent";
            }

            registerAttendance(studentId, studentName, status);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void registerAttendance(String studentId, String studentName, String status) {
        db.collection("users").document(studentId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String photoUrl = "";
                    if (documentSnapshot.exists()) {
                        photoUrl = documentSnapshot.getString("photoUrl");
                    }

                    Map<String, Object> attendance = new HashMap<>();
                    attendance.put("classId", classId);
                    attendance.put("studentId", studentId);
                    attendance.put("studentName", studentName);
                    attendance.put("studentPhoto", photoUrl != null ? photoUrl : "");
                    attendance.put("status", status);
                    attendance.put("timestamp", System.currentTimeMillis());

                    db.collection("attendance")
                            .add(attendance)
                            .addOnSuccessListener(documentReference -> {
                                String statusText = status.equals("present") ? "Presente" :
                                        status.equals("late") ? "Atraso" : "Falta";
                                Toast.makeText(this,
                                        "✓ Asistencia registrada: " + studentName + " - " + statusText,
                                        Toast.LENGTH_LONG).show();

                                // Limpiar para escanear otro
                                ivScanPreview.setVisibility(android.view.View.GONE);
                                btnProcessScan.setVisibility(android.view.View.GONE);
                                btnStartScanning.setText("Escanear Rostro");
                                tvInstructions.setText("Escanea el rostro del siguiente estudiante");
                                scannedImage = null;
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Error al registrar: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            });
                });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}