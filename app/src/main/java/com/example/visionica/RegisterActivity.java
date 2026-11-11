package com.example.visionica;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class RegisterActivity extends AppCompatActivity {

    // Views
    private ImageView btnBack;
    private TextInputLayout tilName, tilEmail, tilPassword, tilConfirmPassword;
    private TextInputEditText etName, etEmail, etPassword, etConfirmPassword;
    private TextView tvLogin, tvPasswordStrength;
    private CardView btnRegister;
    private ProgressBar progressBar, passwordStrengthBar;
    private LinearLayout passwordStrengthLayout;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Validación de contraseña
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Ocultar ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Inicializar Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Inicializar vistas
        initViews();

        // Configurar listeners
        setupListeners();

        // Configurar validación en tiempo real
        setupPasswordValidation();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tilName = findViewById(R.id.tilName);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        tvLogin = findViewById(R.id.tvLogin);
        tvPasswordStrength = findViewById(R.id.tvPasswordStrength);
        btnRegister = findViewById(R.id.btnRegister);
        progressBar = findViewById(R.id.progressBar);
        passwordStrengthBar = findViewById(R.id.passwordStrengthBar);
        passwordStrengthLayout = findViewById(R.id.passwordStrengthLayout);
    }

    private void setupListeners() {
        // Botón de retroceso
        btnBack.setOnClickListener(v -> finish());

        // Botón de registro
        btnRegister.setOnClickListener(v -> attemptRegister());

        // Ir a login
        tvLogin.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void setupPasswordValidation() {
        etPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    passwordStrengthLayout.setVisibility(View.VISIBLE);
                    updatePasswordStrength(s.toString());
                } else {
                    passwordStrengthLayout.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void updatePasswordStrength(String password) {
        int strength = 0;
        String strengthText = "";
        int color = Color.RED;

        // Calcular fortaleza
        if (password.length() >= MIN_PASSWORD_LENGTH) strength += 25;
        if (password.matches(".*[a-z].*")) strength += 25;
        if (password.matches(".*[A-Z].*")) strength += 25;
        if (password.matches(".*[0-9].*")) strength += 15;
        if (password.matches(".*[@#$%^&+=!].*")) strength += 10;

        // Determinar texto y color
        if (strength < 40) {
            strengthText = "Débil";
            color = Color.RED;
        } else if (strength < 70) {
            strengthText = "Media";
            color = Color.parseColor("#FF9800"); // Naranja
        } else if (strength < 90) {
            strengthText = "Buena";
            color = Color.parseColor("#4CAF50"); // Verde
        } else {
            strengthText = "Excelente";
            color = Color.parseColor("#2196F3"); // Azul
        }

        tvPasswordStrength.setText("Fortaleza: " + strengthText);
        tvPasswordStrength.setTextColor(color);
        passwordStrengthBar.setProgress(strength);
        passwordStrengthBar.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
    }

    private void attemptRegister() {
        // Limpiar errores previos
        tilName.setError(null);
        tilEmail.setError(null);
        tilPassword.setError(null);
        tilConfirmPassword.setError(null);

        // Obtener valores
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        // Validaciones
        boolean isValid = true;

        // Validar nombre
        if (TextUtils.isEmpty(name)) {
            tilName.setError("El nombre es requerido");
            isValid = false;
        } else if (name.length() < 3) {
            tilName.setError("El nombre debe tener al menos 3 caracteres");
            isValid = false;
        }

        // Validar email
        if (TextUtils.isEmpty(email)) {
            tilEmail.setError("El correo es requerido");
            isValid = false;
        } else if (!isValidEmail(email)) {
            tilEmail.setError("Correo electrónico inválido");
            isValid = false;
        }

        // Validar contraseña
        if (TextUtils.isEmpty(password)) {
            tilPassword.setError("La contraseña es requerida");
            isValid = false;
        } else if (!isValidPassword(password)) {
            tilPassword.setError("La contraseña no cumple los requisitos");
            isValid = false;
        }

        // Validar confirmación de contraseña
        if (TextUtils.isEmpty(confirmPassword)) {
            tilConfirmPassword.setError("Confirma tu contraseña");
            isValid = false;
        } else if (!password.equals(confirmPassword)) {
            tilConfirmPassword.setError("Las contraseñas no coinciden");
            isValid = false;
        }

        if (!isValid) {
            return;
        }

        // Proceder con el registro
        registerUser(name, email, password);
    }

    private void registerUser(String name, String email, String password) {
        // Mostrar progress bar
        showLoading(true);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Registro exitoso
                        FirebaseUser user = mAuth.getCurrentUser();

                        // Actualizar perfil con el nombre
                        updateUserProfile(user, name);

                        // Guardar datos adicionales en Firestore
                        saveUserToFirestore(user.getUid(), name, email);

                    } else {
                        // Registro falló
                        showLoading(false);
                        String errorMessage = "Error al crear la cuenta";
                        if (task.getException() != null) {
                            errorMessage = getFirebaseErrorMessage(task.getException().getMessage());
                        }
                        Toast.makeText(RegisterActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void updateUserProfile(FirebaseUser user, String name) {
        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build();

        user.updateProfile(profileUpdates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Perfil actualizado
                        sendVerificationEmail(user);
                    }
                });
    }

    private void saveUserToFirestore(String userId, String name, String email) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("name", name);
        userData.put("email", email);
        userData.put("photoUrl", ""); // Se actualizará si usa Google
        userData.put("createdAt", System.currentTimeMillis());
        // NO guardamos rol - el usuario elige al crear/unirse a clase

        db.collection("users").document(userId)
                .set(userData)
                .addOnSuccessListener(aVoid -> {
                    // Datos guardados exitosamente
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(RegisterActivity.this,
                            "Error al guardar datos adicionales",
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void sendVerificationEmail(FirebaseUser user) {
        user.sendEmailVerification()
                .addOnCompleteListener(task -> {
                    showLoading(false);

                    if (task.isSuccessful()) {
                        Toast.makeText(RegisterActivity.this,
                                "Cuenta creada. Verifica tu correo electrónico",
                                Toast.LENGTH_LONG).show();
                        goToMainActivity();
                    } else {
                        Toast.makeText(RegisterActivity.this,
                                "Cuenta creada. No se pudo enviar el correo de verificación",
                                Toast.LENGTH_SHORT).show();
                        goToMainActivity();
                    }
                });
    }

    private void goToMainActivity() {
        Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!show);
    }

    private boolean isValidEmail(String email) {
        return !TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private boolean isValidPassword(String password) {
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return false;
        }

        boolean hasUpperCase = false;
        boolean hasLowerCase = false;
        boolean hasDigit = false;
        boolean hasSpecialChar = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpperCase = true;
            else if (Character.isLowerCase(c)) hasLowerCase = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else if ("@#$%^&+=!".indexOf(c) >= 0) hasSpecialChar = true;
        }

        return hasUpperCase && hasLowerCase && hasDigit && hasSpecialChar;
    }

    private String getFirebaseErrorMessage(String errorCode) {
        if (errorCode == null) return "Error al crear la cuenta";

        if (errorCode.contains("email address is already in use")) {
            return "Este correo ya está registrado";
        } else if (errorCode.contains("weak password")) {
            return "La contraseña es muy débil";
        } else if (errorCode.contains("invalid email")) {
            return "Correo electrónico inválido";
        } else if (errorCode.contains("network error")) {
            return "Error de conexión. Verifica tu internet";
        } else {
            return "Error al crear la cuenta";
        }
    }
}