package com.example.visionica;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import java.io.ByteArrayOutputStream;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.firebase.firestore.DocumentSnapshot;
import com.yalantis.ucrop.UCrop;
import java.io.File;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;


import org.json.JSONObject;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SettingsActivity extends AppCompatActivity {

    // ─── Cloudinary ───────────────────────────────────────────────────────────
    // Reemplaza estos dos valores con los de tu cuenta de Cloudinary
    private static final String CLOUD_NAME    = "danwtc2dq";
    private static final String UPLOAD_PRESET = "ml_default"; // preset unsigned
    private static final String CLOUDINARY_URL =
            "https://api.cloudinary.com/v1_1/" + CLOUD_NAME + "/image/upload";

    // ─── Prefs ────────────────────────────────────────────────────────────────
    private static final String PREFS_NAME       = "visionica_prefs";
    private static final String PREF_NOTIF_ASIST = "notif_asistencia";
    private static final String PREF_NOTIF_CALEN = "notif_calendario";

    // ─── UI ───────────────────────────────────────────────────────────────────
    private CardView btnEditProfile, btnNotificationSettings, btnPrivacy, btnAbout;

    // ─── Firebase ─────────────────────────────────────────────────────────────
    private FirebaseFirestore db;
    private FirebaseAuth      mAuth;
    private FirebaseUser      currentUser;
    private SharedPreferences prefs;

    private boolean fotoSeleccionada = false;

    // ─── Estado del dialog de perfil ─────────────────────────────────────────
    private ImageView dialogAvatarView; // avatar dentro del dialog abierto
    private Uri       selectedImageUri; // imagen elegida por el usuario

    private final ActivityResultLauncher<Intent> cropLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedImageUri = UCrop.getOutput(result.getData());
                    fotoSeleccionada = true;
                    if (dialogAvatarView != null && selectedImageUri != null) {
                        try {
                            // Carga directa como bitmap sin Glide — no puede ser pisada
                            android.graphics.Bitmap bitmap = android.provider.MediaStore.Images.Media
                                    .getBitmap(getContentResolver(), selectedImageUri);

                            // Recorte circular manual
                            android.graphics.Bitmap circleBitmap = android.graphics.Bitmap.createBitmap(
                                    bitmap.getWidth(), bitmap.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
                            android.graphics.Canvas canvas = new android.graphics.Canvas(circleBitmap);
                            android.graphics.Paint paint = new android.graphics.Paint();
                            paint.setAntiAlias(true);
                            canvas.drawCircle(bitmap.getWidth() / 2f, bitmap.getHeight() / 2f,
                                    Math.min(bitmap.getWidth(), bitmap.getHeight()) / 2f, paint);
                            paint.setXfermode(new android.graphics.PorterDuffXfermode(
                                    android.graphics.PorterDuff.Mode.SRC_IN));
                            canvas.drawBitmap(bitmap, 0, 0, paint);

                            dialogAvatarView.setImageBitmap(circleBitmap);
                        } catch (Exception e) {
                            // Si falla el bitmap, usa Glide como fallback
                            Glide.with(this).load(selectedImageUri).circleCrop().into(dialogAvatarView);
                        }
                    }
                }
            });

    // ─── Lanzador de galería (debe ir antes de onCreate) ─────────────────────
    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    // URI de destino del recorte
                    Uri destUri = Uri.fromFile(new File(getCacheDir(), "cropped_profile.jpg"));



                    cropLauncher.launch(
                            UCrop.of(uri, destUri)
                                    .withAspectRatio(1, 1)
                                    .withMaxResultSize(500, 500)
                                    .getIntent(this)
                    );
                }
            });

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        db          = FirebaseFirestore.getInstance();
        mAuth       = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        prefs       = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Configuración");
        }

        btnEditProfile          = findViewById(R.id.btnEditProfile);
        btnNotificationSettings = findViewById(R.id.btnNotificationSettings);
        btnPrivacy              = findViewById(R.id.btnPrivacy);
        btnAbout                = findViewById(R.id.btnAbout);

        setupListeners();
    }

    private void setupListeners() {
        btnEditProfile.setOnClickListener(v          -> showEditProfileDialog());
        btnNotificationSettings.setOnClickListener(v -> showNotificationsDialog());
        btnPrivacy.setOnClickListener(v              -> showPrivacyDialog());
        btnAbout.setOnClickListener(v                -> showAboutDialog());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. EDITAR PERFIL
    // ─────────────────────────────────────────────────────────────────────────
    private void showEditProfileDialog() {
        selectedImageUri = null;
        fotoSeleccionada = false;


        ContextThemeWrapper materialContext = new ContextThemeWrapper(
                this,
                com.google.android.material.R.style.Theme_MaterialComponents_Light_NoActionBar
        );
        View view = LayoutInflater.from(materialContext)
                .inflate(R.layout.dialog_edit_profile, null);

        EditText  etName        = view.findViewById(R.id.etName);
        EditText  etPhone       = view.findViewById(R.id.etPhone);
        EditText  etInstitution = view.findViewById(R.id.etInstitution);
        EditText  etCareer      = view.findViewById(R.id.etCareer);
        dialogAvatarView        = view.findViewById(R.id.ivProfileAvatar);

        dialogAvatarView.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Editar perfil")
                .setView(view)
                .setPositiveButton("Guardar cambios", null)
                .setNegativeButton("Cancelar", (d, w) -> dialogAvatarView = null)
                .create();

        dialog.setOnShowListener(d ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String name        = etName.getText().toString().trim();
                    String phone       = etPhone.getText().toString().trim();
                    String institution = etInstitution.getText().toString().trim();
                    String career      = etCareer.getText().toString().trim();

                    if (TextUtils.isEmpty(name)) {
                        Toast.makeText(this, "El nombre no puede estar vacío",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);

                    if (selectedImageUri != null) {
                        uploadImageToCloudinary(selectedImageUri, name, phone, institution, career, dialog);
                    } else {
                        saveProfile(name, phone, institution, career, null);
                        dialog.dismiss();
                        dialogAvatarView = null;
                    }
                })
        );

        // ── Carga datos y LUEGO muestra el dialog ────────────────────────────
        if (currentUser != null) {
            db.collection("users").document(currentUser.getUid())
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            etName.setText(doc.getString("name") != null
                                    ? doc.getString("name")
                                    : currentUser.getDisplayName());
                            etPhone.setText(doc.getString("phone"));
                            etInstitution.setText(doc.getString("institution"));
                            etCareer.setText(doc.getString("career"));

                            String savedPhoto = doc.getString("photoUrl");
                            if (!fotoSeleccionada) {
                                if (savedPhoto != null && !savedPhoto.isEmpty()) {
                                    Glide.with(this)
                                            .load(savedPhoto)
                                            .skipMemoryCache(true)
                                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                                            .circleCrop()
                                            .into(dialogAvatarView);
                                } else if (currentUser.getPhotoUrl() != null) {
                                    Glide.with(this)
                                            .load(currentUser.getPhotoUrl())
                                            .skipMemoryCache(true)
                                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                                            .placeholder(R.drawable.ic_user_placeholder)
                                            .circleCrop()
                                            .into(dialogAvatarView);
                                }
                            }
                        } else {
                            etName.setText(currentUser.getDisplayName());
                            if (currentUser.getPhotoUrl() != null) {
                                Glide.with(this).load(currentUser.getPhotoUrl())
                                        .placeholder(R.drawable.ic_user_placeholder)
                                        .circleCrop().into(dialogAvatarView);
                            }
                        }
                        dialog.show(); // <- muestra el dialog DESPUÉS de cargar los datos
                    })
                    .addOnFailureListener(e -> dialog.show()); // si falla igual muestra el dialog
        } else {
            dialog.show();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sube la imagen a Cloudinary en segundo plano
    // ─────────────────────────────────────────────────────────────────────────
    private void uploadImageToCloudinary(Uri imageUri,
                                         String name, String phone,
                                         String institution, String career,
                                         AlertDialog dialog) {

        Toast.makeText(this, "Subiendo imagen…", Toast.LENGTH_SHORT).show();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(chunk)) != -1) {
                    buffer.write(chunk, 0, bytesRead);
                }
                inputStream.close();
                byte[] imageBytes = buffer.toByteArray();

                OkHttpClient client = new OkHttpClient();
                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", "profile.jpg",
                                RequestBody.create(imageBytes, MediaType.parse("image/jpeg")))
                        .addFormDataPart("upload_preset", UPLOAD_PRESET)
                        .build();

                Request request = new Request.Builder()
                        .url(CLOUDINARY_URL)
                        .post(requestBody)
                        .build();

                Response response = client.newCall(request).execute();
                String responseBody = response.body().string();

                if (response.isSuccessful()) {
                    JSONObject json = new JSONObject(responseBody);
                    String imageUrl = json.getString("secure_url"); // URL HTTPS de Cloudinary

                    runOnUiThread(() -> {
                        saveProfile(name, phone, institution, career, imageUrl);
                        dialog.dismiss();
                        dialogAvatarView = null;
                    });
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Error Cloudinary: " + response.code(),
                                Toast.LENGTH_LONG).show();
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                    });
                }

            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                });
            }
        });
    }


    // Launcher de galería — abre UCrop después de elegir



    // ─────────────────────────────────────────────────────────────────────────
    // Guarda en Firestore y Firebase Auth (photoUrl es null si no cambió)
    // ─────────────────────────────────────────────────────────────────────────
    private void saveProfile(String name, String phone,
                             String institution, String career,
                             String photoUrl) {
        if (currentUser == null) return;
        if (photoUrl != null) {
            getSharedPreferences("visionica_prefs", MODE_PRIVATE)
                    .edit()
                    .putString("cached_photo_url", photoUrl)
                    .apply();
        }
        android.util.Log.d("DEBUG", "UID actual: " + currentUser.getUid());
        android.util.Log.d("DEBUG", "Buscando clases con teacherId: " + currentUser.getUid());

        UserProfileChangeRequest.Builder builder = new UserProfileChangeRequest.Builder()
                .setDisplayName(name);
        if (photoUrl != null) builder.setPhotoUri(Uri.parse(photoUrl));
        currentUser.updateProfile(builder.build());

        Map<String, Object> data = new HashMap<>();
        data.put("name",        name);
        data.put("phone",       phone);
        data.put("institution", institution);
        data.put("career",      career);
        data.put("uid",         currentUser.getUid());
        data.put("email",       currentUser.getEmail());
        if (photoUrl != null) data.put("photoUrl", photoUrl);

        db.collection("users").document(currentUser.getUid())
                .set(data)
                .addOnSuccessListener(a ->
                        Toast.makeText(this, "✓ Perfil actualizado", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());

        db.collection("classes")
                .whereEqualTo("teacherId", currentUser.getUid())
                .get()
                .addOnSuccessListener(query -> {
                    for (DocumentSnapshot doc : query.getDocuments()) {
                        doc.getReference().update("teacherName", name);
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. NOTIFICACIONES
    // ─────────────────────────────────────────────────────────────────────────
    private void showNotificationsDialog() {
        View view = LayoutInflater.from(this)
                .inflate(R.layout.dialog_notifications, null);

        Switch switchAsistencia = view.findViewById(R.id.switchAsistencia);
        Switch switchCalendario = view.findViewById(R.id.switchCalendario);

        switchAsistencia.setChecked(prefs.getBoolean(PREF_NOTIF_ASIST, true));
        switchCalendario.setChecked(prefs.getBoolean(PREF_NOTIF_CALEN, true));

        new AlertDialog.Builder(this)
                .setTitle("Notificaciones")
                .setView(view)
                .setPositiveButton("Guardar", (dialog, which) -> {
                    prefs.edit()
                            .putBoolean(PREF_NOTIF_ASIST, switchAsistencia.isChecked())
                            .putBoolean(PREF_NOTIF_CALEN, switchCalendario.isChecked())
                            .apply();
                    Toast.makeText(this, "✓ Preferencias guardadas",
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. PRIVACIDAD
    // ─────────────────────────────────────────────────────────────────────────
    private void showPrivacyDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Privacidad y seguridad")
                .setMessage(
                        "🔒  Reconocimiento facial\n\n" +
                                "VISIÓNICA utiliza el reconocimiento facial únicamente para el " +
                                "registro de asistencia académica. Los datos faciales se usan solo " +
                                "para verificar la identidad del estudiante dentro de la aplicación " +
                                "y no son compartidos con terceros.\n\n" +
                                "📷  Permisos de cámara\n\n" +
                                "La cámara se utiliza exclusivamente durante el proceso de registro " +
                                "o verificación del rostro para tomar asistencia. No se accede a la " +
                                "cámara en ningún otro momento.\n\n" +
                                "🗄  Almacenamiento de datos\n\n" +
                                "Los embeddings faciales se almacenan de forma segura en Firebase " +
                                "Firestore y están vinculados únicamente al estudiante registrado."
                )
                .setPositiveButton("Entendido", null)
                .show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. ACERCA DE
    // ─────────────────────────────────────────────────────────────────────────
    private void showAboutDialog() {
        View view = LayoutInflater.from(this)
                .inflate(R.layout.dialog_about, null);

        new AlertDialog.Builder(this)
                .setView(view)
                .setPositiveButton("Cerrar", null)
                .show();
    }

    public static boolean isAsistenciaNotifEnabled(android.content.Context ctx) {
        return ctx.getSharedPreferences("visionica_prefs", MODE_PRIVATE)
                .getBoolean("notif_asistencia", true);
    }

    public static boolean isCalendarioNotifEnabled(android.content.Context ctx) {
        return ctx.getSharedPreferences("visionica_prefs", MODE_PRIVATE)
                .getBoolean("notif_calendario", true);
    }


    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}