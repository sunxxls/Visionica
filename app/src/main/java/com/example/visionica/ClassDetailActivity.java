package com.example.visionica;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.format.Border;
import jxl.format.BorderLineStyle;
import jxl.format.Orientation;
import jxl.format.VerticalAlignment;
import jxl.write.Label;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WritableFont;
import jxl.write.WritableCellFormat;
import jxl.format.Colour;
import jxl.format.Alignment;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ClassDetailActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 101;

    // FIX: flag estático para inicializar MediaManager solo una vez
    private static boolean mediaManagerInitialized = false;

    private String classId;
    private String className;
    private String classCode;
    private String teacherId;
    private boolean isTeacher;

    // Datos de la clase para el Excel
    private String classSubject = "";
    private String classSection = "";
    private String classRoom    = "";
    private String classTeacher = "";

    // Views
    private TextView tvClassName, tvClassSection, tvTeacherName, tvStudentCount, tvClassCode, tvFaceButtonText;
    private CardView btnFaceRegistration, btnExportExcel, btnDeleteFace;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private ImageView ivClassBanner;

    // Firebase
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    // Mes/año seleccionado
    private int selectedMonth = -1;
    private int selectedYear  = -1;

    private String currentBannerUrl = "";

    private SharedPreferences prefs;

    // Banners prediseñados
    private static final String[] PRESET_BANNERS = {
            "https://res.cloudinary.com/danwtc2dq/image/upload/q_auto/f_auto/v1778895608/7_bpc3e8.jpg",
            "https://res.cloudinary.com/danwtc2dq/image/upload/q_auto/f_auto/v1778895607/2_z6f9ay.jpg",
            "https://res.cloudinary.com/danwtc2dq/image/upload/q_auto/f_auto/v1778895599/3_fsltod.jpg",
            "https://res.cloudinary.com/danwtc2dq/image/upload/q_auto/f_auto/v1778895598/9_qi9nca.jpg",
            "https://res.cloudinary.com/danwtc2dq/image/upload/q_auto/f_auto/v1778895597/6_xspxzl.jpg",
            "https://res.cloudinary.com/danwtc2dq/image/upload/q_auto/f_auto/v1778895597/10_ycaqaq.jpg",
            "https://res.cloudinary.com/danwtc2dq/image/upload/q_auto/f_auto/v1778895593/5_lkek11.jpg",
            "https://res.cloudinary.com/danwtc2dq/image/upload/q_auto/f_auto/v1778895592/8_gxydvo.jpg",
            "https://res.cloudinary.com/danwtc2dq/image/upload/q_auto/f_auto/v1778895591/4_sx0n3p.jpg",
            "https://res.cloudinary.com/danwtc2dq/image/upload/q_auto/f_auto/v1778895590/1_sv7b8e.jpg"
    };

    // Launcher para elegir imagen del dispositivo
    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) uploadImageToCloudinary(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_class_detail);

        prefs = getSharedPreferences("visionica_prefs", MODE_PRIVATE);

        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        classId   = getIntent().getStringExtra("classId");
        className = getIntent().getStringExtra("className");
        classCode = getIntent().getStringExtra("classCode");

        if (classId == null) {
            Toast.makeText(this, "Error: ID de clase no válido", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_back);
            getSupportActionBar().setTitle(className != null ? className : "Clase");
        }

        initViews();

        // FIX: inicializar MediaManager solo si no fue inicializado antes
        if (!mediaManagerInitialized) {
            Map<String, String> config = new HashMap<>();
            config.put("cloud_name", "danwtc2dq");
            config.put("api_key",    "497818151286275");
            config.put("api_secret", "fv3iD812-JQFzSxJabvmjzuXoA4");
            MediaManager.init(this, config);
            mediaManagerInitialized = true;
        }

        loadClassData();
        setupListeners();
    }

    private void initViews() {
        tvClassName         = findViewById(R.id.tvClassName);
        tvClassSection      = findViewById(R.id.tvClassSection);
        tvTeacherName       = findViewById(R.id.tvTeacherName);
        tvStudentCount      = findViewById(R.id.tvStudentCount);
        tvClassCode         = findViewById(R.id.tvClassCode);
        btnFaceRegistration = findViewById(R.id.btnFaceRegistration);
        btnExportExcel      = findViewById(R.id.btnExportExcel);
        btnDeleteFace = findViewById(R.id.btnDeleteFace);
        tabLayout           = findViewById(R.id.tabLayout);
        viewPager           = findViewById(R.id.viewPager);
        ivClassBanner       = findViewById(R.id.ivClassBanner);
    }

    private void loadClassData() {
        // ── Muestra datos cacheados INSTANTÁNEAMENTE ──────────────────────────
        String cachedName    = prefs.getString("cached_name_"    + classId, null);
        String cachedSection = prefs.getString("cached_section_" + classId, null);
        String cachedCode    = prefs.getString("cached_code_"    + classId, null);
        String cachedCount   = prefs.getString("cached_count_"   + classId, null);
        String cachedBanner  = prefs.getString("cached_banner_"  + classId, null);
        String cachedTeacher = prefs.getString("cached_teacher_" + classId, null);

        if (cachedName    != null) tvClassName.setText(cachedName);
        if (cachedSection != null) tvClassSection.setText(cachedSection);
        if (cachedCode    != null) tvClassCode.setText(cachedCode);
        if (cachedCount   != null) tvStudentCount.setText(cachedCount);
        if (cachedTeacher != null) tvTeacherName.setText(cachedTeacher);
        if (cachedBanner  != null && !cachedBanner.isEmpty()) {
            Glide.with(this).load(cachedBanner)
                    .placeholder(R.drawable.bg_class_header)
                    .into(ivClassBanner);
        }

        com.google.android.gms.tasks.Task<com.google.firebase.firestore.DocumentSnapshot> classTask =
                db.collection("classes").document(classId).get();

        classTask.addOnSuccessListener(doc -> {

                    if (!doc.exists()) {
                        Toast.makeText(this, "Clase no encontrada", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    String name    = doc.getString("name");
                    String section = doc.getString("section");
                    String room    = doc.getString("room");
                    String subject = doc.getString("subject");
                    String code    = doc.getString("code");
                    teacherId      = doc.getString("teacherId");

                    classSubject = subject != null ? subject : "";
                    classSection = section != null ? section : "";
                    classRoom    = room    != null ? room    : "";

                    String bannerUrl = doc.getString("bannerUrl");
                    currentBannerUrl = bannerUrl != null ? bannerUrl : "";
                    if (!currentBannerUrl.isEmpty()) {
                        Glide.with(this).load(currentBannerUrl)
                                .placeholder(R.drawable.bg_class_header)
                                .into(ivClassBanner);
                    }

                    List<String> students = (List<String>) doc.get("students");
                    isTeacher = teacherId != null &&
                            teacherId.equals(mAuth.getCurrentUser().getUid());

                    tvClassName.setText(name);
                    String sectionInfo = "";
                    if (section != null && !section.isEmpty()) sectionInfo += section;
                    if (room != null && !room.isEmpty())
                        sectionInfo += (sectionInfo.isEmpty() ? "" : " • ") + room;
                    tvClassSection.setText(sectionInfo.isEmpty() ? "Sin sección" : sectionInfo);
                    tvClassCode.setText(code != null ? code : "");
                    tvStudentCount.setText((students != null ? students.size() : 0) + " estudiantes");

                    prefs.edit()
                            .putString("cached_name_"    + classId, name)
                            .putString("cached_section_" + classId, tvClassSection.getText().toString())
                            .putString("cached_code_"    + classId, code != null ? code : "")
                            .putString("cached_count_"   + classId, (students != null ? students.size() : 0) + " estudiantes")
                            .putString("cached_banner_"  + classId, currentBannerUrl)
                            .apply();

                    setupTabs();

                    tvFaceButtonText   = findViewById(R.id.tvFaceButtonText);
                    android.widget.ImageView iv = findViewById(R.id.ivFaceIcon);

                    if (isTeacher) {
                        tvFaceButtonText.setText("Tomar Asistencia Facial");
                        iv.setImageResource(R.drawable.ic_face_recognition);
                        btnFaceRegistration.setVisibility(View.VISIBLE);
                        btnExportExcel.setVisibility(View.VISIBLE);
                        View btnEditBanner = findViewById(R.id.btnEditBanner);
                        if (btnEditBanner != null) {
                            btnEditBanner.setVisibility(View.VISIBLE);
                            btnEditBanner.setOnClickListener(v2 -> showBannerOptions());
                        }
                    } else {
                        tvFaceButtonText.setText("Registrar mi Rostro");
                        iv.setImageResource(R.drawable.ic_face_recognition);
                        btnFaceRegistration.setVisibility(View.VISIBLE);
                        btnExportExcel.setVisibility(View.GONE);

                        String studentId = mAuth.getCurrentUser().getUid();
                        db.collection("faceEmbeddings")
                                .whereEqualTo("studentId", studentId)
                                .whereEqualTo("classId", classId)
                                .get()
                                .addOnSuccessListener(snap -> {
                                    if (!snap.isEmpty()) {
                                        // Ya tiene rostro — mostrar basurero
                                        btnDeleteFace.setVisibility(View.VISIBLE);
                                        tvFaceButtonText.setText("Re-registrar mi Rostro");
                                    } else {
                                        btnDeleteFace.setVisibility(View.GONE);
                                    }
                                });
                    }

                    // ── Consulta al profesor en paralelo, no espera a que cargue la clase ──
                    if (teacherId != null) {
                        db.collection("users").document(teacherId)
                                .get()
                                .addOnSuccessListener(userDoc -> {
                                    String teacherName  = userDoc.getString("name");
                                    String teacherPhoto = userDoc.getString("photoUrl");

                                    classTeacher = teacherName != null ? teacherName : "";
                                    tvTeacherName.setText(teacherName != null ? teacherName : "Profesor");
                                    // Guarda en caché para la próxima vez
                                    prefs.edit().putString("cached_teacher_" + classId, teacherName).apply();

                                    if (teacherName != null) {
                                        db.collection("classes").document(classId)
                                                .update("teacherName", teacherName);
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al cargar clase: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void setupTabs() {
        if (isTeacher) {
            ViewPagerAdapter adapter = new ViewPagerAdapter(this, classId, true);
            viewPager.setAdapter(adapter);
            new TabLayoutMediator(tabLayout, viewPager, (tab, position) ->
                    tab.setText(position == 0 ? "Asistencia" : "Personas")).attach();
        } else {
            ViewPagerAdapterStudent adapter = new ViewPagerAdapterStudent(this, classId);
            viewPager.setAdapter(adapter);
            new TabLayoutMediator(tabLayout, viewPager, (tab, position) ->
                    tab.setText("Mi Asistencia")).attach();
        }
    }

    private void setupListeners() {
        btnFaceRegistration.setOnClickListener(v -> {
            if (isTeacher) openFaceAttendanceActivity();
            else           openFaceRegistrationActivity();
        });
        btnDeleteFace.setOnClickListener(v -> confirmDeleteFace());
        btnExportExcel.setOnClickListener(v -> {
            if (isTeacher) showMonthYearDialog();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BANNER
    // ─────────────────────────────────────────────────────────────────────────

    private void showBannerOptions() {
        String[] options = {"Elegir banner prediseñado", "Subir foto propia"};
        new AlertDialog.Builder(this)
                .setTitle("Cambiar banner de la clase")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showPresetBanners();
                    else            imagePickerLauncher.launch("image/*");
                })
                .show();
    }

    private void showPresetBanners() {
        android.widget.GridView grid = new android.widget.GridView(this);
        grid.setNumColumns(2);
        grid.setPadding(16, 16, 16, 16);
        grid.setHorizontalSpacing(16);
        grid.setVerticalSpacing(16);

        grid.setAdapter(new android.widget.BaseAdapter() {
            @Override public int getCount()               { return PRESET_BANNERS.length; }
            @Override public Object getItem(int i)        { return PRESET_BANNERS[i]; }
            @Override public long getItemId(int i)        { return i; }

            @Override
            public View getView(int i, View convertView, android.view.ViewGroup parent) {
                ImageView iv = new ImageView(ClassDetailActivity.this);
                iv.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                        android.widget.AbsListView.LayoutParams.MATCH_PARENT, 200));
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Glide.with(ClassDetailActivity.this)
                        .load(PRESET_BANNERS[i])
                        .into(iv);
                return iv;
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Elegir banner")
                .setView(grid)
                .setNegativeButton("Cancelar", null)
                .create();

        grid.setOnItemClickListener((parent, view, position, id) -> {
            applyBanner(PRESET_BANNERS[position]);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void uploadImageToCloudinary(Uri imageUri) {
        Toast.makeText(this, "Subiendo imagen...", Toast.LENGTH_SHORT).show();

        // FIX: usar unsigned upload con preset "ml_default"
        // En Cloudinary Console → Settings → Upload → Add upload preset → Unsigned
        MediaManager.get().upload(imageUri)
                .unsigned("ml_default")   // ← preset unsigned de Cloudinary
                .option("folder", "banners")
                .callback(new UploadCallback() {
                    @Override public void onStart(String requestId) {}

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                        // Podés mostrar progreso si querés
                    }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String url = (String) resultData.get("secure_url");
                        if (url != null) {
                            runOnUiThread(() -> applyBanner(url));
                        }
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        runOnUiThread(() ->
                                Toast.makeText(ClassDetailActivity.this,
                                        "Error al subir: " + error.getDescription(),
                                        Toast.LENGTH_LONG).show());
                    }

                    @Override public void onReschedule(String requestId, ErrorInfo error) {}
                })
                .dispatch();
    }

    private void applyBanner(String url) {
        currentBannerUrl = url;
        Glide.with(this)
                .load(url)
                .placeholder(R.drawable.bg_class_header)
                .into(ivClassBanner);

        db.collection("classes").document(classId)
                .update("bannerUrl", url)
                .addOnSuccessListener(a ->
                        Toast.makeText(this, "✓ Banner actualizado", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error al guardar banner", Toast.LENGTH_SHORT).show());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SELECTOR DE MES Y AÑO
    // ─────────────────────────────────────────────────────────────────────────
    private void showMonthYearDialog() {
        Calendar cal = Calendar.getInstance();

        NumberPicker monthPicker = new NumberPicker(this);
        monthPicker.setMinValue(1);
        monthPicker.setMaxValue(12);
        monthPicker.setValue(cal.get(Calendar.MONTH) + 1);
        monthPicker.setDisplayedValues(new String[]{
                "Enero","Febrero","Marzo","Abril","Mayo","Junio",
                "Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre"
        });

        NumberPicker yearPicker = new NumberPicker(this);
        int currentYear = cal.get(Calendar.YEAR);
        yearPicker.setMinValue(currentYear - 3);
        yearPicker.setMaxValue(currentYear + 1);
        yearPicker.setValue(currentYear);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        layout.setPadding(40, 20, 40, 20);
        layout.setGravity(android.view.Gravity.CENTER);

        android.widget.LinearLayout.LayoutParams params =
                new android.widget.LinearLayout.LayoutParams(
                        0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(8, 0, 8, 0);
        layout.addView(monthPicker, params);
        layout.addView(yearPicker, params);

        new AlertDialog.Builder(this)
                .setTitle("Seleccionar mes y año")
                .setView(layout)
                .setPositiveButton("Exportar", (dialog, which) -> {
                    selectedMonth = monthPicker.getValue();
                    selectedYear  = yearPicker.getValue();
                    checkPermissionsAndExport();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PERMISOS
    // ─────────────────────────────────────────────────────────────────────────
    private void checkPermissionsAndExport() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportAttendanceToExcel(selectedYear, selectedMonth);
        } else {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE);
            } else {
                exportAttendanceToExcel(selectedYear, selectedMonth);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            exportAttendanceToExcel(selectedYear, selectedMonth);
        } else {
            Toast.makeText(this, "Se necesita permiso para guardar el archivo",
                    Toast.LENGTH_LONG).show();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONSULTA FIREBASE
    // ─────────────────────────────────────────────────────────────────────────
    private void exportAttendanceToExcel(int year, int month) {
        Toast.makeText(this, "Consultando asistencias de "
                + getMonthName(month) + " " + year + "...", Toast.LENGTH_SHORT).show();

        Calendar start = Calendar.getInstance();
        start.set(year, month - 1, 1, 0, 0, 0);
        start.set(Calendar.MILLISECOND, 0);

        Calendar end = Calendar.getInstance();
        end.set(year, month - 1, getDaysInMonth(year, month), 23, 59, 59);
        end.set(Calendar.MILLISECOND, 999);

        db.collection("attendance")
                .whereEqualTo("classId", classId)
                .whereGreaterThanOrEqualTo("timestamp", start.getTimeInMillis())
                .whereLessThanOrEqualTo("timestamp", end.getTimeInMillis())
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        Toast.makeText(this,
                                "No hay asistencias en " + getMonthName(month) + " " + year,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    List<DocumentSnapshot> docs = querySnapshot.getDocuments();
                    docs.sort((a, b) -> {
                        Long t1 = a.getLong("timestamp");
                        Long t2 = b.getLong("timestamp");
                        if (t1 == null) return 1;
                        if (t2 == null) return -1;
                        return t1.compareTo(t2);
                    });
                    createExcelFile(docs, year, month);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CREAR EXCEL
    // ─────────────────────────────────────────────────────────────────────────
    private void createExcelFile(List<DocumentSnapshot> documents, int year, int month) {
        try {
            String monthName = getMonthName(month);
            String safeClass = className != null
                    ? className.replaceAll("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ]", "_") : "Clase";
            String fileName  = "Asistencia_" + safeClass + "_" + monthName + "_" + year + ".xls";

            WorkbookSettings settings = new WorkbookSettings();
            settings.setLocale(new Locale("es", "BO"));

            OutputStream outputStream;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.ms-excel");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver()
                        .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    Toast.makeText(this, "Error al crear archivo", Toast.LENGTH_SHORT).show();
                    return;
                }
                outputStream = getContentResolver().openOutputStream(uri);
            } else {
                java.io.File dir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                outputStream = new java.io.FileOutputStream(new java.io.File(dir, fileName));
            }

            if (outputStream == null) {
                Toast.makeText(this, "Error al abrir stream", Toast.LENGTH_SHORT).show();
                return;
            }

            WritableWorkbook workbook = Workbook.createWorkbook(outputStream, settings);
            WritableSheet    sheet    = workbook.createSheet("Asistencia", 0);

            // ESTILOS
            WritableFont titleFont = new WritableFont(WritableFont.ARIAL, 12, WritableFont.BOLD);
            WritableCellFormat titleFmt = new WritableCellFormat(titleFont);
            titleFmt.setAlignment(Alignment.CENTRE);
            titleFmt.setVerticalAlignment(VerticalAlignment.CENTRE);
            titleFmt.setBackground(Colour.ICE_BLUE);
            titleFmt.setBorder(Border.ALL, BorderLineStyle.THIN);
            titleFmt.setWrap(true);

            WritableFont infoFont = new WritableFont(WritableFont.ARIAL, 9, WritableFont.BOLD);
            WritableCellFormat infoFmt = new WritableCellFormat(infoFont);
            infoFmt.setAlignment(Alignment.LEFT);
            infoFmt.setVerticalAlignment(VerticalAlignment.CENTRE);
            infoFmt.setBackground(Colour.GRAY_25);
            infoFmt.setBorder(Border.ALL, BorderLineStyle.THIN);
            infoFmt.setWrap(true);

            WritableFont colFont = new WritableFont(WritableFont.ARIAL, 8, WritableFont.BOLD);
            WritableCellFormat colFmt = new WritableCellFormat(colFont);
            colFmt.setAlignment(Alignment.CENTRE);
            colFmt.setVerticalAlignment(VerticalAlignment.CENTRE);
            colFmt.setBackground(Colour.LIGHT_BLUE);
            colFmt.setBorder(Border.ALL, BorderLineStyle.THIN);
            colFmt.setWrap(true);

            WritableCellFormat dateVertFmt = new WritableCellFormat(colFont);
            dateVertFmt.setAlignment(Alignment.CENTRE);
            dateVertFmt.setVerticalAlignment(VerticalAlignment.CENTRE);
            dateVertFmt.setBackground(Colour.GRAY_25);
            dateVertFmt.setBorder(Border.ALL, BorderLineStyle.THIN);
            dateVertFmt.setOrientation(Orientation.PLUS_90);

            WritableCellFormat colVertFmt = new WritableCellFormat(colFont);
            colVertFmt.setAlignment(Alignment.CENTRE);
            colVertFmt.setVerticalAlignment(VerticalAlignment.CENTRE);
            colVertFmt.setBackground(Colour.LIGHT_BLUE);
            colVertFmt.setBorder(Border.ALL, BorderLineStyle.THIN);
            colVertFmt.setOrientation(Orientation.PLUS_90);
            colVertFmt.setWrap(true);

            WritableCellFormat centerFmt = new WritableCellFormat();
            centerFmt.setAlignment(Alignment.CENTRE);
            centerFmt.setVerticalAlignment(VerticalAlignment.CENTRE);
            centerFmt.setBorder(Border.ALL, BorderLineStyle.THIN);

            WritableCellFormat leftFmt = new WritableCellFormat();
            leftFmt.setAlignment(Alignment.LEFT);
            leftFmt.setVerticalAlignment(VerticalAlignment.CENTRE);
            leftFmt.setBorder(Border.ALL, BorderLineStyle.THIN);

            WritableFont boldFont = new WritableFont(WritableFont.ARIAL, 9, WritableFont.BOLD);
            WritableCellFormat totalFmt = new WritableCellFormat(boldFont);
            totalFmt.setAlignment(Alignment.CENTRE);
            totalFmt.setVerticalAlignment(VerticalAlignment.CENTRE);
            totalFmt.setBorder(Border.ALL, BorderLineStyle.THIN);
            totalFmt.setBackground(Colour.GRAY_25);

            // Extraer fechas únicas con registro
            SimpleDateFormat displayFmt = new SimpleDateFormat("d/M/yyyy", Locale.getDefault());
            SimpleDateFormat keyFmt     = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

            Set<String> uniqueDateKeys     = new LinkedHashSet<>();
            Map<String, String> keyToLabel = new LinkedHashMap<>();

            for (DocumentSnapshot doc : documents) {
                Long ts = doc.getLong("timestamp");
                if (ts != null) {
                    java.util.Date d = new java.util.Date(ts);
                    String key   = keyFmt.format(d);
                    String label = displayFmt.format(d);
                    if (uniqueDateKeys.add(key)) keyToLabel.put(key, label);
                }
            }

            List<String> dateKeys   = new ArrayList<>(uniqueDateKeys);
            List<String> dateLabels = new ArrayList<>();
            for (String k : dateKeys) dateLabels.add(keyToLabel.get(k));

            int numDates     = dateKeys.size();
            int firstDateCol = 2;
            int lastDateCol  = firstDateCol + numDates - 1;
            int summaryStart = lastDateCol + 1;

            String[] summaryHeaders = {
                    "DIAS\nTRABAJADOS", "PERMISO", "ATRASO",
                    "FALTA", "LICENCIA", "PRESENTE", "% ASISTENCIA"
            };
            int totalCols = summaryStart + summaryHeaders.length;

            // Anchos
            sheet.setColumnView(0, 4);
            sheet.setColumnView(1, 32);
            for (int c = firstDateCol; c <= lastDateCol; c++) sheet.setColumnView(c, 4);
            for (int c = summaryStart; c < totalCols; c++)    sheet.setColumnView(c, 6);

            // Fila 0: Institución
            sheet.mergeCells(0, 0, totalCols - 1, 0);
            sheet.addCell(new Label(0, 0, "INSTITUTO TECNOLÓGICO AYACUCHO", titleFmt));
            sheet.setRowView(0, 500);

            // Fila 1: Título
            sheet.mergeCells(0, 1, totalCols - 1, 1);
            sheet.addCell(new Label(0, 1,
                    "VALORACIÓN CUANTITATIVA — PLANILLA DE ASISTENCIA", titleFmt));
            sheet.setRowView(1, 450);

            // Filas 2-4: Info clase
            int half = totalCols / 2;
            sheet.mergeCells(0, 2, half - 1, 2);
            sheet.addCell(new Label(0, 2, "PROFESOR: " + classTeacher, infoFmt));
            sheet.mergeCells(half, 2, totalCols - 1, 2);
            sheet.addCell(new Label(half, 2, "CLASE: " + (className != null ? className : ""), infoFmt));
            sheet.setRowView(2, 400);

            sheet.mergeCells(0, 3, half - 1, 3);
            sheet.addCell(new Label(0, 3, "ASIGNATURA: " + classSubject, infoFmt));
            sheet.mergeCells(half, 3, totalCols - 1, 3);
            sheet.addCell(new Label(half, 3, "SECCIÓN: " + classSection, infoFmt));
            sheet.setRowView(3, 400);

            sheet.mergeCells(0, 4, half - 1, 4);
            sheet.addCell(new Label(0, 4, "AULA: " + classRoom, infoFmt));
            sheet.mergeCells(half, 4, totalCols - 1, 4);
            sheet.addCell(new Label(half, 4, "MES: " + monthName + "     AÑO: " + year, infoFmt));
            sheet.setRowView(4, 400);

            // Fila 5: "FECHAS DE TRABAJO" y "PORCENTAJE"
            if (numDates > 0) sheet.mergeCells(firstDateCol, 5, lastDateCol, 5);
            sheet.addCell(new Label(firstDateCol, 5, "FECHAS DE TRABAJO", colFmt));
            sheet.mergeCells(summaryStart, 5, totalCols - 1, 5);
            sheet.addCell(new Label(summaryStart, 5, "PORCENTAJE", colFmt));
            sheet.setRowView(5, 400);

            // Fila 6: encabezados
            sheet.addCell(new Label(0, 6, "N°",                  colFmt));
            sheet.addCell(new Label(1, 6, "APELLIDOS Y NOMBRES", colFmt));
            for (int i = 0; i < dateLabels.size(); i++) {
                sheet.addCell(new Label(firstDateCol + i, 6, dateLabels.get(i), dateVertFmt));
            }
            for (int s = 0; s < summaryHeaders.length; s++) {
                sheet.addCell(new Label(summaryStart + s, 6, summaryHeaders[s], colVertFmt));
            }
            sheet.setRowView(6, 1800);

            // Datos de estudiantes
            Map<String, StudentAttendanceData> studentsMap = new LinkedHashMap<>();
            for (DocumentSnapshot doc : documents) {
                String studentId   = doc.getString("studentId");
                String studentName = doc.getString("studentName");
                String status      = doc.getString("status");
                Long   ts          = doc.getLong("timestamp");
                if (studentId == null || studentName == null) continue;
                studentsMap.putIfAbsent(studentId, new StudentAttendanceData(studentName));
                if (ts != null) {
                    String dateKey = keyFmt.format(new java.util.Date(ts));
                    studentsMap.get(studentId).addAttendance(dateKey, getStatusCode(status));
                }
            }

            int row = 7, num = 1;
            for (StudentAttendanceData student : studentsMap.values()) {
                sheet.addCell(new jxl.write.Number(0, row, num, centerFmt));
                sheet.addCell(new Label(1, row, student.name, leftFmt));

                int dTrabajados = 0, nPermiso = 0, nAtraso = 0,
                        nFalta = 0, nLicencia = 0, nPresente = 0;

                for (int i = 0; i < dateKeys.size(); i++) {
                    String code = student.getAttendance(dateKeys.get(i));
                    sheet.addCell(new Label(firstDateCol + i, row, code, centerFmt));
                    if (!code.isEmpty()) dTrabajados++;
                    switch (code) {
                        case "P": nPresente++; break;
                        case "A": nAtraso++;   break;
                        case "L": nLicencia++; break;
                        case "F": nFalta++;    break;
                        case "E": nPermiso++;  break;
                    }
                }

                double pct = dTrabajados > 0
                        ? (double)(nPresente + nAtraso) / dTrabajados * 100.0 : 0.0;

                int si = summaryStart;
                sheet.addCell(new jxl.write.Number(si,     row, dTrabajados, totalFmt));
                sheet.addCell(new jxl.write.Number(si + 1, row, nPermiso,    totalFmt));
                sheet.addCell(new jxl.write.Number(si + 2, row, nAtraso,     totalFmt));
                sheet.addCell(new jxl.write.Number(si + 3, row, nFalta,      totalFmt));
                sheet.addCell(new jxl.write.Number(si + 4, row, nLicencia,   totalFmt));
                sheet.addCell(new jxl.write.Number(si + 5, row, nPresente,   totalFmt));
                sheet.addCell(new Label(si + 6, row,
                        String.format(Locale.US, "%.0f%%", pct), totalFmt));

                sheet.setRowView(row, 400);
                row++;
                num++;
            }

            workbook.write();
            workbook.close();
            outputStream.close();

            Toast.makeText(this, "✓ Excel guardado:\n" + fileName, Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AUXILIARES
    // ─────────────────────────────────────────────────────────────────────────

    private String getStatusCode(String status) {
        if (status == null) return "";
        switch (status) {
            case "present": return "P";
            case "late":    return "A";
            case "license": return "L";
            case "absent":  return "F";
            case "permit":  return "E";
            default:        return "";
        }
    }

    private String getMonthName(int month) {
        String[] months = {"Enero","Febrero","Marzo","Abril","Mayo","Junio",
                "Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre"};
        return (month >= 1 && month <= 12) ? months[month - 1] : "Mes";
    }

    private int getDaysInMonth(int year, int month) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, 1);
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    private static class StudentAttendanceData {
        String name;
        Map<String, String> attendanceByDate;

        StudentAttendanceData(String name) {
            this.name = name;
            this.attendanceByDate = new java.util.HashMap<>();
        }

        void addAttendance(String dateKey, String code) {
            attendanceByDate.put(dateKey, code);
        }

        String getAttendance(String dateKey) {
            String val = attendanceByDate.get(dateKey);
            return val != null ? val : "";
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NAVEGACIÓN
    // ─────────────────────────────────────────────────────────────────────────

    private void openFaceAttendanceActivity() {
        Intent intent = new Intent(this, FaceAttendanceActivity.class);
        intent.putExtra("classId",   classId);
        intent.putExtra("className", className);
        startActivity(intent);
    }

    private void openFaceRegistrationActivity() {
        Intent intent = new Intent(this, FaceRegistrationActivity.class);
        intent.putExtra("classId",   classId);
        intent.putExtra("className", className);
        startActivity(intent);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADAPTERS
    // ─────────────────────────────────────────────────────────────────────────

    private static class ViewPagerAdapter extends FragmentStateAdapter {
        private final String  classId;
        private final boolean isTeacher;

        ViewPagerAdapter(@NonNull AppCompatActivity a, String classId, boolean isTeacher) {
            super(a);
            this.classId   = classId;
            this.isTeacher = isTeacher;
        }

        @NonNull @Override
        public Fragment createFragment(int position) {
            return PeopleFragment.newInstance(classId, isTeacher);
        }

        @Override public int getItemCount() { return 1; }
    }

    private static class ViewPagerAdapterStudent extends FragmentStateAdapter {
        private final String classId;

        ViewPagerAdapterStudent(@NonNull AppCompatActivity a, String classId) {
            super(a);
            this.classId = classId;
        }

        @NonNull @Override
        public Fragment createFragment(int position) {
            return MyAttendanceFragment.newInstance(classId);
        }

        @Override public int getItemCount() { return 1; }
    }

    private void confirmDeleteFace() {
        new AlertDialog.Builder(this)
                .setTitle("Borrar registro facial")
                .setMessage("¿Seguro que quieres borrar tu rostro registrado? Tendrás que registrarte de nuevo.")
                .setPositiveButton("Borrar", (dialog, which) -> deleteFaceRegistration())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void deleteFaceRegistration() {
        String studentId = mAuth.getCurrentUser().getUid();

        db.collection("faceEmbeddings")
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("classId", classId)
                .get()
                .addOnSuccessListener(snap -> {
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        doc.getReference().delete();
                    }
                    Toast.makeText(this,
                            "Rostro eliminado. Puedes registrarte de nuevo.",
                            Toast.LENGTH_LONG).show();
                    btnDeleteFace.setVisibility(View.GONE);
                    tvFaceButtonText.setText("Registrar mi Rostro");
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Error al borrar: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadClassData();
    }
}