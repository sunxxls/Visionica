package com.example.visionica;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class LoginActivity extends AppCompatActivity {

    private static final int RC_SIGN_IN = 9001;

    // Views
    private ImageView btnBack;
    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private TextView tvForgotPassword, tvRegister;
    private CardView btnLogin, btnGoogleSignIn;
    private ProgressBar progressBar;

    // Firebase
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Ocultar ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Inicializar Firebase
        mAuth = FirebaseAuth.getInstance();
        configureGoogleSignIn();

        // Inicializar vistas
        initViews();

        // Configurar listeners
        setupListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvRegister = findViewById(R.id.tvRegister);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        progressBar = findViewById(R.id.progressBar);
    }

    private void configureGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    private void setupListeners() {
        // Botón de retroceso
        btnBack.setOnClickListener(v -> finish());

        // Botón de login
        btnLogin.setOnClickListener(v -> attemptLogin());

        // Google Sign In
        btnGoogleSignIn.setOnClickListener(v -> signInWithGoogle());

        // ¿Olvidaste tu contraseña?
        tvForgotPassword.setOnClickListener(v -> resetPassword());

        // Ir a registro
        tvRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }

    private void attemptLogin() {
        // Limpiar errores previos
        tilEmail.setError(null);
        tilPassword.setError(null);

        // Obtener valores
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // Validaciones
        boolean isValid = true;

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
        } else if (password.length() < 8) {
            tilPassword.setError("La contraseña debe tener al menos 8 caracteres");
            isValid = false;
        }

        if (!isValid) {
            return;
        }

        // Proceder con el login
        loginUser(email, password);
    }

    private void loginUser(String email, String password) {
        // Mostrar progress bar
        showLoading(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    showLoading(false);

                    if (task.isSuccessful()) {
                        // Login exitoso
                        FirebaseUser user = mAuth.getCurrentUser();
                        Toast.makeText(LoginActivity.this,
                                "Bienvenido " + user.getEmail(),
                                Toast.LENGTH_SHORT).show();
                        goToMainActivity();
                    } else {
                        // Login falló
                        String errorMessage = "Error de autenticación";
                        if (task.getException() != null) {
                            errorMessage = getFirebaseErrorMessage(task.getException().getMessage());
                        }
                        Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) {
                    firebaseAuthWithGoogle(account.getIdToken());
                }
            } catch (ApiException e) {
                Toast.makeText(this, "Error al iniciar sesión con Google", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        showLoading(true);
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);

        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    showLoading(false);

                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        Toast.makeText(LoginActivity.this,
                                "Bienvenido " + user.getDisplayName(),
                                Toast.LENGTH_SHORT).show();
                        goToMainActivity();
                    } else {
                        Toast.makeText(LoginActivity.this,
                                "Error en la autenticación",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void resetPassword() {
        String email = etEmail.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            tilEmail.setError("Ingresa tu correo para recuperar la contraseña");
            etEmail.requestFocus();
            return;
        }

        if (!isValidEmail(email)) {
            tilEmail.setError("Correo electrónico inválido");
            return;
        }

        showLoading(true);

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    showLoading(false);

                    if (task.isSuccessful()) {
                        Toast.makeText(LoginActivity.this,
                                "Se ha enviado un correo de recuperación a " + email,
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(LoginActivity.this,
                                "Error al enviar el correo de recuperación",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void goToMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!show);
        btnGoogleSignIn.setEnabled(!show);
    }

    private boolean isValidEmail(String email) {
        return !TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private String getFirebaseErrorMessage(String errorCode) {
        if (errorCode == null) return "Error de autenticación";

        if (errorCode.contains("no user record")) {
            return "No existe una cuenta con este correo";
        } else if (errorCode.contains("password is invalid")) {
            return "Contraseña incorrecta";
        } else if (errorCode.contains("network error")) {
            return "Error de conexión. Verifica tu internet";
        } else if (errorCode.contains("too-many-requests")) {
            return "Demasiados intentos. Intenta más tarde";
        } else {
            return "Error: Credenciales inválidas";
        }
    }
}