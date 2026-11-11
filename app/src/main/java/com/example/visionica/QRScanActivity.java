package com.example.visionica;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Window;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QRScanActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 100;

    private String classId;
    private String className;

    private DecoratedBarcodeView barcodeView;
    private TextView tvScanInstructions;
    private Button btnToggleFlash;

    private FirebaseFirestore db;
    private boolean isScanning = false;
    private boolean isFlashOn = false; // Variable para controlar el estado del flash

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scan);

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
            getSupportActionBar().setTitle("Escanear QR");
        }

        // Inicializar vistas
        barcodeView = findViewById(R.id.barcodeView);
        tvScanInstructions = findViewById(R.id.tvScanInstructions);
        btnToggleFlash = findViewById(R.id.btnToggleFlash);

        // Setup flash
        btnToggleFlash.setOnClickListener(v -> toggleFlash());

        // Verificar permisos y iniciar escáner
        checkCameraPermission();
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE);
        } else {
            startScanning();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanning();
            } else {
                Toast.makeText(this, "Se necesita permiso de cámara para escanear QR",
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void startScanning() {
        isScanning = true;
        barcodeView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (result != null && isScanning) {
                    isScanning = false; // Evitar múltiples escaneos
                    processQRCode(result.getText());
                }
            }

            @Override
            public void possibleResultPoints(List<ResultPoint> resultPoints) {
                // No necesario implementar
            }
        });
    }

    private void processQRCode(String qrContent) {
        try {
            JSONObject qrData = QRCodeHelper.parseQRContent(qrContent);

            if (qrData != null) {
                String studentId = qrData.getString("studentId");
                String studentName = qrData.getString("studentName");
                String scannedClassId = qrData.getString("classId");

                // Verificar que el QR es de esta clase
                if (scannedClassId.equals(classId)) {
                    // Pausar escáner
                    barcodeView.pause();

                    // Mostrar diálogo para seleccionar estado
                    showAttendanceStatusDialog(studentId, studentName);
                } else {
                    Toast.makeText(this,
                            "⚠️ Este QR es de otra clase",
                            Toast.LENGTH_LONG).show();
                    isScanning = true; // Permitir escanear de nuevo
                }
            } else {
                Toast.makeText(this, "QR inválido", Toast.LENGTH_SHORT).show();
                isScanning = true;
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error al procesar QR: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            isScanning = true;
        }
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
            isScanning = true;
            barcodeView.resume();
        });

        btnConfirm.setOnClickListener(v -> {
            int selectedId = rgStatus.getCheckedRadioButtonId();
            String status = "present";

            if (selectedId == R.id.rbPresent) {
                status = "present";
            } else if (selectedId == R.id.rbLate) {
                status = "late";
            } else if (selectedId == R.id.rbLicense) {
                status = "license";  // NUEVO
            } else if (selectedId == R.id.rbAbsent) {
                status = "absent";
            }

            registerAttendance(studentId, studentName, status);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void registerAttendance(String studentId, String studentName, String status) {
        // LOG INICIAL
        android.util.Log.d("QRScan", "=== INICIANDO REGISTRO ===");
        android.util.Log.d("QRScan", "ClassID: " + classId);
        android.util.Log.d("QRScan", "StudentID: " + studentId);
        android.util.Log.d("QRScan", "StudentName: " + studentName);
        android.util.Log.d("QRScan", "Status: " + status);

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

                    // LOG ANTES DE GUARDAR
                    android.util.Log.d("QRScan", "Datos a guardar: " + attendance.toString());

                    db.collection("attendance")
                            .add(attendance)
                            .addOnSuccessListener(documentReference -> {
                                // LOG ÉXITO
                                android.util.Log.d("QRScan", "✓ GUARDADO EXITOSO - DocID: " + documentReference.getId());

                                String statusText = "";
                                if ("present".equals(status)) statusText = "Presente";
                                else if ("late".equals(status)) statusText = "Atraso";
                                else if ("license".equals(status)) statusText = "Licencia";  // NUEVO
                                else if ("absent".equals(status)) statusText = "Falta";

                                Toast.makeText(this,
                                        "✓ Asistencia registrada: " + studentName + " - " + statusText,
                                        Toast.LENGTH_LONG).show();

                                isScanning = true;
                                barcodeView.resume();
                            })
                            .addOnFailureListener(e -> {
                                // LOG ERROR
                                android.util.Log.e("QRScan", "✗ ERROR AL GUARDAR: " + e.getMessage());

                                Toast.makeText(this, "Error al registrar: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                                isScanning = true;
                                barcodeView.resume();
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("QRScan", "✗ ERROR AL OBTENER USUARIO: " + e.getMessage());
                });
    }

    private void toggleFlash() {
        if (isFlashOn) {
            barcodeView.setTorchOff();
            btnToggleFlash.setText("Encender Flash");
            isFlashOn = false;
        } else {
            barcodeView.setTorchOn();
            btnToggleFlash.setText("Apagar Flash");
            isFlashOn = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        barcodeView.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        barcodeView.pause();
        // Apagar el flash al pausar la actividad
        if (isFlashOn) {
            barcodeView.setTorchOff();
            isFlashOn = false;
            btnToggleFlash.setText("Encender Flash");
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}