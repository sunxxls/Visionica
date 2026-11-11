package com.example.visionica;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class StudentQRActivity extends AppCompatActivity {

    private String classId;
    private String className;

    private ImageView ivQRCode;
    private TextView tvQRInfo, tvClassName, tvStudentName;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_qr);

        // Inicializar Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Obtener datos
        classId = getIntent().getStringExtra("classId");
        className = getIntent().getStringExtra("className");

        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Mi Código QR");
        }

        // Inicializar vistas
        ivQRCode = findViewById(R.id.ivQRCode);
        tvQRInfo = findViewById(R.id.tvQRInfo);
        tvClassName = findViewById(R.id.tvClassName);
        tvStudentName = findViewById(R.id.tvStudentName);

        // Generar y mostrar QR
        generateAndShowQR();
    }

    private void generateAndShowQR() {
        String studentId = mAuth.getCurrentUser().getUid();
        String studentName = mAuth.getCurrentUser().getDisplayName();

        if (studentName == null || studentName.isEmpty()) {
            studentName = mAuth.getCurrentUser().getEmail();
        }

        // Mostrar información
        tvClassName.setText("Clase: " + className);
        tvStudentName.setText("Estudiante: " + studentName);

        // Generar código QR
        Bitmap qrBitmap = QRCodeHelper.generateQRCode(
                studentId,
                studentName,
                classId,
                className,
                500 // Tamaño del QR
        );

        if (qrBitmap != null) {
            ivQRCode.setImageBitmap(qrBitmap);
            tvQRInfo.setText("Muestra este código QR a tu profesor para registrar tu asistencia");
        } else {
            Toast.makeText(this, "Error al generar código QR", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}