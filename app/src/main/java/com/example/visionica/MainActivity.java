package com.example.visionica;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.ApiException;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.AuthCredential;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import de.hdodenhof.circleimageview.CircleImageView;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    // Views
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;
    private ImageView btnMenu, ivUserProfile;
    private FloatingActionButton fabAddClass;
    private RecyclerView rvClasses;
    private LinearLayout emptyState;
    private CardView btnJoinClassCenter, btnCreateClassCenter;

    // Adapter
    private ClassAdapter classAdapter;
    private List<ClassModel> classList;

    private SharedPreferences prefs;

    private GoogleSignInClient googleSignInClient;
    private final ActivityResultLauncher<Intent> switchAccountLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) {
                            try {
                                GoogleSignInAccount account = GoogleSignIn
                                        .getSignedInAccountFromIntent(result.getData())
                                        .getResult(ApiException.class);
                                switchFirebaseAccount(account);
                            } catch (ApiException e) {
                                Toast.makeText(this, "Error al cambiar cuenta: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });

    // Constantes
    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CODE_LENGTH = 7;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(getResources().getColor(R.color.background_light));
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("visionica_prefs", MODE_PRIVATE);

        // Inicializar Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // Verificar sesión
        if (currentUser == null) {
            goToWelcome();
            return;
        }

        // Inicializar vistas
        initViews();

        // Configurar toolbar y drawer
        setupToolbar();
        setupDrawer();
        setupNavigationHeader();

        // Configurar RecyclerView
        setupRecyclerView();

        // Cargar clases del usuario
        loadUserClasses();

        // Configurar listeners
        setupListeners();
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        toolbar = findViewById(R.id.toolbar);
        ivUserProfile = findViewById(R.id.ivUserProfile);
        fabAddClass = findViewById(R.id.fabAddClass);
        rvClasses = findViewById(R.id.rvClasses);
        emptyState = findViewById(R.id.emptyState);
        btnJoinClassCenter = findViewById(R.id.btnJoinClassCenter);
        btnCreateClassCenter = findViewById(R.id.btnCreateClassCenter);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        NavigationView navigationView = findViewById(R.id.navigationView);
        navigationView.setItemTextAppearance(R.style.NavItemTextAppearance);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
    }

    private void setupDrawer() {
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(this);
    }

    private void setupNavigationHeader() {
        View headerView = navigationView.getHeaderView(0);
        CircleImageView ivNavProfile = headerView.findViewById(R.id.ivNavProfile);
        TextView tvNavName = headerView.findViewById(R.id.tvNavName);
        TextView tvNavEmail = headerView.findViewById(R.id.tvNavEmail);

        if (currentUser == null) return;

        tvNavName.setText(currentUser.getDisplayName() != null
                ? currentUser.getDisplayName() : "Usuario");
        tvNavEmail.setText(currentUser.getEmail());

        // Carga inmediata desde caché
        SharedPreferences prefs = getSharedPreferences("visionica_prefs", MODE_PRIVATE);
        String cachedPhoto = prefs.getString("cached_photo_url", null);
        if (cachedPhoto != null) {
            Glide.with(this).load(cachedPhoto).circleCrop().into(ivNavProfile);
            Glide.with(this).load(cachedPhoto).circleCrop().into(ivUserProfile);
        }


        // Primero intenta con la foto de Firestore (Cloudinary)
        db.collection("users").document(currentUser.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    String photoUrl = doc.getString("photoUrl");
                    if (photoUrl != null && !photoUrl.isEmpty()) {
                        Glide.with(this).load(photoUrl).placeholder(R.drawable.ic_user_placeholder).circleCrop().into(ivNavProfile);
                        Glide.with(this).load(photoUrl).placeholder(R.drawable.ic_user_placeholder).circleCrop().into(ivUserProfile);
                    } else if (currentUser.getPhotoUrl() != null) {
                        Glide.with(this).load(currentUser.getPhotoUrl()).placeholder(R.drawable.ic_user_placeholder).into(ivNavProfile);
                        Glide.with(this).load(currentUser.getPhotoUrl()).placeholder(R.drawable.ic_user_placeholder).into(ivUserProfile);
                    }
                });
    }

    private void setupRecyclerView() {
        classList = new ArrayList<>();
        classAdapter = new ClassAdapter(this, classList);
        rvClasses.setLayoutManager(new LinearLayoutManager(this));
        rvClasses.setAdapter(classAdapter);
    }

    private void setupListeners() {
        // Botón menú

        // Foto de perfil - mostrar opciones
        ivUserProfile.setOnClickListener(v -> showAccountOptions());

        // FAB
        fabAddClass.setOnClickListener(v -> showAddClassOptions());

        // Botones centrales
        btnCreateClassCenter.setOnClickListener(v -> showCreateClassDialog());
        btnJoinClassCenter.setOnClickListener(v -> showJoinClassDialog());
    }

    private void loadUserClasses() {
        String userId = currentUser.getUid();

        // ── Muestra clases cacheadas instantáneamente ─────────────────────────
        String cachedJson = prefs.getString("cached_classes_" + userId, null);
        if (cachedJson != null) {
            try {
                org.json.JSONArray arr = new org.json.JSONArray(cachedJson);
                classList.clear();
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject obj = arr.getJSONObject(i);
                    ClassModel m = new ClassModel();
                    m.setId(obj.optString("id"));
                    m.setName(obj.optString("name"));
                    m.setSection(obj.optString("section"));
                    m.setRoom(obj.optString("room"));
                    m.setSubject(obj.optString("subject"));
                    m.setCode(obj.optString("code"));
                    m.setTeacherId(obj.optString("teacherId"));
                    m.setTeacherName(obj.optString("teacherName"));
                    m.setBannerUrl(obj.optString("bannerUrl"));
                    int count = obj.optInt("studentCount", 0);
                    java.util.List<String> fakeStudents = new java.util.ArrayList<>();
                    for (int j = 0; j < count; j++) fakeStudents.add("");
                    m.setStudents(fakeStudents);
                    classList.add(m);
                }
                updateUI();
            } catch (Exception ignored) {}
        }

        // ── Lanza las dos consultas en paralelo ───────────────────────────────
        List<ClassModel> tempList = new ArrayList<>();

        com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot> teacherTask =
                db.collection("classes").whereEqualTo("teacherId", userId).get();

        com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot> studentTask =
                db.collection("classes").whereArrayContains("students", userId).get();

        com.google.android.gms.tasks.Tasks.whenAllSuccess(teacherTask, studentTask)
                .addOnSuccessListener(results -> {
                    classList.clear();

                    com.google.firebase.firestore.QuerySnapshot teacherSnap =
                            (com.google.firebase.firestore.QuerySnapshot) results.get(0);
                    com.google.firebase.firestore.QuerySnapshot studentSnap =
                            (com.google.firebase.firestore.QuerySnapshot) results.get(1);

                    for (QueryDocumentSnapshot doc : teacherSnap) {
                        Boolean archived = doc.getBoolean("archived");
                        if (archived == null || !archived) {
                            ClassModel m = doc.toObject(ClassModel.class);
                            m.setId(doc.getId());
                            classList.add(m);
                        }
                    }

                    for (QueryDocumentSnapshot doc : studentSnap) {
                        Boolean archived = doc.getBoolean("archived");
                        if (archived == null || !archived) {
                            ClassModel m = doc.toObject(ClassModel.class);
                            m.setId(doc.getId());
                            classList.add(m);
                        }
                    }

                    updateUI();

                    // Guarda en caché para la próxima vez
                    try {
                        org.json.JSONArray arr = new org.json.JSONArray();
                        for (ClassModel m : classList) {
                            org.json.JSONObject obj = new org.json.JSONObject();
                            obj.put("id",          m.getId());
                            obj.put("name",        m.getName());
                            obj.put("section",     m.getSection());
                            obj.put("room",        m.getRoom());
                            obj.put("subject",     m.getSubject());
                            obj.put("code",        m.getCode());
                            obj.put("teacherId",   m.getTeacherId());
                            obj.put("teacherName", m.getTeacherName());
                            obj.put("bannerUrl",   m.getBannerUrl() != null ? m.getBannerUrl() : "");
                            obj.put("studentCount", m.getStudents() != null ? m.getStudents().size() : 0);
                            arr.put(obj);
                        }
                        prefs.edit().putString("cached_classes_" + userId, arr.toString()).apply();
                    } catch (Exception ignored) {}
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error al cargar clases", Toast.LENGTH_SHORT).show());
    }



    private void updateUI() {
        if (classList.isEmpty()) {
            rvClasses.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            rvClasses.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
            classAdapter.notifyDataSetChanged();
        }
    }

    private void showAddClassOptions() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_class_options);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        CardView btnJoinClass = dialog.findViewById(R.id.btnJoinClass);
        CardView btnCreateClass = dialog.findViewById(R.id.btnCreateClass);

        btnJoinClass.setOnClickListener(v -> {
            dialog.dismiss();
            showJoinClassDialog();
        });

        btnCreateClass.setOnClickListener(v -> {
            dialog.dismiss();
            showCreateClassDialog();
        });

        dialog.show();
    }

    private void showCreateClassDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_create_class);

        TextInputLayout tilClassName = dialog.findViewById(R.id.tilClassName);
        TextInputEditText etClassName = dialog.findViewById(R.id.etClassName);
        TextInputEditText etSection = dialog.findViewById(R.id.etSection);
        TextInputEditText etRoom = dialog.findViewById(R.id.etRoom);
        TextInputEditText etSubject = dialog.findViewById(R.id.etSubject);
        TextView btnCancel = dialog.findViewById(R.id.btnCancel);
        TextView btnCreate = dialog.findViewById(R.id.btnCreate);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnCreate.setOnClickListener(v -> {
            String className = etClassName.getText().toString().trim();
            String section = etSection.getText().toString().trim();
            String room = etRoom.getText().toString().trim();
            String subject = etSubject.getText().toString().trim();

            if (TextUtils.isEmpty(className)) {
                tilClassName.setError("El nombre de la clase es requerido");
                return;
            }

            // Generar código único
            String classCode = generateUniqueCode();

            // Crear clase en Firestore
            createClass(className, section, room, subject, classCode);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void createClass(String className, String section, String room, String subject, String code) {
        Map<String, Object> classData = new HashMap<>();
        classData.put("name", className);
        classData.put("section", section);
        classData.put("room", room);
        classData.put("subject", subject);
        classData.put("code", code);
        classData.put("teacherId", currentUser.getUid());
        classData.put("teacherName", currentUser.getDisplayName());
        classData.put("students", new ArrayList<String>());
        classData.put("createdAt", System.currentTimeMillis());

        db.collection("classes")
                .add(classData)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "Clase creada: " + code, Toast.LENGTH_LONG).show();
                    loadUserClasses();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al crear clase", Toast.LENGTH_SHORT).show();
                });
    }

    private void showJoinClassDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_join_class);

        TextInputLayout tilClassCode = dialog.findViewById(R.id.tilClassCode);
        TextInputEditText etClassCode = dialog.findViewById(R.id.etClassCode);
        TextView btnCancelJoin = dialog.findViewById(R.id.btnCancelJoin);
        TextView btnJoin = dialog.findViewById(R.id.btnJoin);

        btnCancelJoin.setOnClickListener(v -> dialog.dismiss());

        btnJoin.setOnClickListener(v -> {
            String code = etClassCode.getText().toString().trim();

            if (TextUtils.isEmpty(code)) {
                tilClassCode.setError("Ingresa el código de la clase");
                return;
            }

            // Buscar y unirse a la clase
            joinClass(code);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void joinClass(String code) {
        String codeToSearch = code.trim();

        db.collection("classes")
                .whereEqualTo("code", codeToSearch)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {

                    if (queryDocumentSnapshots.isEmpty()) {
                        Toast.makeText(this,
                                "Código de clase inválido. Verifica e intenta de nuevo",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    QueryDocumentSnapshot document =
                            (QueryDocumentSnapshot) queryDocumentSnapshots
                                    .getDocuments().get(0);

                    // Revisa si el estudiante ya pertenece a la clase
                    List<String> students =
                            (List<String>) document.get("students");

                    if (students != null &&
                            students.contains(currentUser.getUid())) {

                        Toast.makeText(this,
                                "Ya estás en esta clase",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Lo agrega sin sobrescribir a otros ni crear duplicados
                    document.getReference()
                            .update(
                                    "students",
                                    FieldValue.arrayUnion(currentUser.getUid())
                            )
                            .addOnSuccessListener(aVoid -> {
                                prefs.edit()
                                        .remove("cached_classes_"
                                                + currentUser.getUid())
                                        .apply();

                                Toast.makeText(this,
                                        "Te uniste a la clase exitosamente",
                                        Toast.LENGTH_LONG).show();

                                loadUserClasses();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this,
                                            "Error al unirse: "
                                                    + e.getMessage(),
                                            Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Error al buscar la clase: "
                                        + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    private String generateUniqueCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return code.toString();
    }

    private void showAccountOptions() {
        // Fuerza mostrar el selector de cuentas de Google
        googleSignInClient.signOut().addOnCompleteListener(task -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            switchAccountLauncher.launch(signInIntent);
        });
    }

    private void switchFirebaseAccount(GoogleSignInAccount account) {
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnSuccessListener(authResult -> {
                    currentUser = mAuth.getCurrentUser();
                    // Actualizar UI con la nueva cuenta
                    setupNavigationHeader();
                    loadUserClasses();
                    Toast.makeText(this,
                            "Cuenta cambiada: " + currentUser.getDisplayName(),
                            Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_classes) {
            // Ya estamos en clases
        } else if (id == R.id.nav_calendar) {
            startActivity(new Intent(this, CalendarActivity.class));
        } else if (id == R.id.nav_notifications) {
            startActivity(new Intent(this, NotificationsActivity.class));
        } else if (id == R.id.nav_archived_classes) {
            startActivity(new Intent(this, ArchivedClassesActivity.class));
        } else if (id == R.id.nav_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (id == R.id.nav_logout) {
            logout();
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    private void logout() {
        mAuth.signOut();
        Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show();
        goToWelcome();
    }

    private void goToWelcome() {
        Intent intent = new Intent(MainActivity.this, WelcomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAuth != null && mAuth.getCurrentUser() != null) {
            currentUser = mAuth.getCurrentUser();
            setupNavigationHeader(); // <- esto es lo que faltaba
            loadUserClasses();
        }
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}