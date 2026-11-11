package com.example.visionica;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;

import de.hdodenhof.circleimageview.CircleImageView;

public class PeopleFragment extends Fragment {

    private static final String ARG_CLASS_ID   = "classId";
    private static final String ARG_IS_TEACHER = "isTeacher";

    private String  classId;
    private boolean isTeacher;

    // Views
    private CircleImageView ivTeacherPhoto;
    private TextView        tvTeacherName, tvStudentCountPeople;
    private RecyclerView    rvStudents;
    private LinearLayout    emptyStudents;

    // Data
    private FirebaseFirestore db;
    private StudentAdapter    adapter;
    private List<Student>     studentList;

    public static PeopleFragment newInstance(String classId, boolean isTeacher) {
        PeopleFragment fragment = new PeopleFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CLASS_ID, classId);
        args.putBoolean(ARG_IS_TEACHER, isTeacher);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            classId   = getArguments().getString(ARG_CLASS_ID);
            isTeacher = getArguments().getBoolean(ARG_IS_TEACHER);
        }
        db = FirebaseFirestore.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_people, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ivTeacherPhoto       = view.findViewById(R.id.ivTeacherPhoto);
        tvTeacherName        = view.findViewById(R.id.tvTeacherName);
        tvStudentCountPeople = view.findViewById(R.id.tvStudentCountPeople);
        rvStudents           = view.findViewById(R.id.rvStudents);
        emptyStudents        = view.findViewById(R.id.emptyStudents);

        studentList = new ArrayList<>();
        adapter     = new StudentAdapter(getContext(), studentList);
        rvStudents.setLayoutManager(new LinearLayoutManager(getContext()));
        rvStudents.setAdapter(adapter);

        // ── Click en estudiante — solo si es profesor ─────────────────────────
        adapter.setOnStudentClickListener(student -> {
            if (isTeacher) showManualAttendanceDialog(student);
        });

        loadClassPeople();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CARGAR DATOS DE LA CLASE
    // ─────────────────────────────────────────────────────────────────────────
    private void loadClassPeople() {
        db.collection("classes").document(classId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!isAdded() || getActivity() == null) return;
                    if (!documentSnapshot.exists()) return;

                    String teacherName = documentSnapshot.getString("teacherName");
                    String teacherId   = documentSnapshot.getString("teacherId");
                    tvTeacherName.setText(teacherName != null ? teacherName : "Profesor");

                    if (teacherId != null) loadUserPhoto(teacherId, ivTeacherPhoto);

                    List<String> studentIds = (List<String>) documentSnapshot.get("students");
                    if (studentIds != null && !studentIds.isEmpty()) {
                        loadStudents(studentIds);
                    } else {
                        showEmptyState();
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CARGAR ESTUDIANTES — una sola consulta
    // ─────────────────────────────────────────────────────────────────────────
    private void loadStudents(List<String> studentIds) {
        studentList.clear();

        if (studentIds == null || studentIds.isEmpty()) {
            showEmptyState();
            return;
        }

        tvStudentCountPeople.setText(studentIds.size() + " estudiantes");

        for (String studentId : studentIds) {
            db.collection("users").document(studentId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (!isAdded() || getActivity() == null) return;

                        Student student = new Student();
                        student.setId(studentId);

                        if (doc.exists()) {
                            student.setName(doc.getString("name"));
                            student.setEmail(doc.getString("email"));
                            student.setPhotoUrl(doc.getString("photoUrl"));
                        } else {
                            student.setName("Estudiante");
                            student.setEmail("");
                            student.setPhotoUrl("");
                        }

                        studentList.add(student);
                        checkFaceRegistration(studentId, student);
                        updateUI();
                    })
                    .addOnFailureListener(e -> {
                        if (!isAdded() || getActivity() == null) return;

                        Student student = new Student();
                        student.setId(studentId);
                        student.setName("Estudiante");
                        student.setEmail("");
                        student.setPhotoUrl("");

                        studentList.add(student);
                        updateUI();
                    });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VERIFICAR ROSTRO REGISTRADO
    // ─────────────────────────────────────────────────────────────────────────
    private void checkFaceRegistration(String studentId, Student student) {
        db.collection("faceData")
                .whereEqualTo("userId",  studentId)
                .whereEqualTo("classId", classId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!isAdded() || getActivity() == null) return;
                    student.setFaceRegistered(!queryDocumentSnapshots.isEmpty());
                    adapter.notifyDataSetChanged();
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CARGAR FOTO DE USUARIO
    // ─────────────────────────────────────────────────────────────────────────
    private void loadUserPhoto(String userId, CircleImageView imageView) {
        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded() || getActivity() == null) return;

                    String photoUrl = doc.getString("photoUrl");
                    if (photoUrl != null && !photoUrl.isEmpty()) {
                        Glide.with(this).load(photoUrl)
                                .placeholder(R.drawable.ic_user_placeholder)
                                .circleCrop().into(imageView);
                    } else {
                        Glide.with(this).load(R.drawable.ic_user_placeholder)
                                .circleCrop().into(imageView);
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DIALOG DE ASISTENCIA MANUAL — solo para ausentes
    // ─────────────────────────────────────────────────────────────────────────
    private void showManualAttendanceDialog(Student student) {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_manual_attendance, null);

        TextView tvDialogStudentName = view.findViewById(R.id.tvDialogStudentName);
        TextView btnAbsent = view.findViewById(R.id.btnAbsent);
        TextView btnPermit = view.findViewById(R.id.btnPermit);
        TextView btnLicense = view.findViewById(R.id.btnLicense);
        TextView btnCancelManual = view.findViewById(R.id.btnCancelManual);

        tvDialogStudentName.setText("Estudiante: " + student.getName());

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(view)
                .create();

        btnAbsent.setOnClickListener(v -> {
            saveManualAttendance(student, "absent", "Falta");
            dialog.dismiss();
        });

        btnPermit.setOnClickListener(v -> {
            saveManualAttendance(student, "permit", "Permiso");
            dialog.dismiss();
        });

        btnLicense.setOnClickListener(v -> {
            saveManualAttendance(student, "license", "Licencia");
            dialog.dismiss();
        });

        btnCancelManual.setOnClickListener(v -> dialog.dismiss());

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialog.show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GUARDAR ASISTENCIA MANUAL EN FIRESTORE
    // ─────────────────────────────────────────────────────────────────────────
    private void saveManualAttendance(Student student, String status, String statusLabel) {
        SimpleDateFormat dateFormat =
                new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String today = dateFormat.format(new Date());

        // Verifica si ya tiene asistencia hoy
        db.collection("attendance")
                .whereEqualTo("studentId", student.getId())
                .whereEqualTo("classId",   classId)
                .get()
                .addOnSuccessListener(query -> {
                    if (!isAdded() || getActivity() == null) return;

                    for (DocumentSnapshot doc : query.getDocuments()) {
                        Long ts = doc.getLong("timestamp");
                        if (ts != null) {
                            String docDate = dateFormat.format(new Date(ts));
                            if (docDate.equals(today)) {
                                Toast.makeText(getContext(),
                                        student.getName() + " ya tiene asistencia registrada hoy",
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }
                    }

                    // No tiene asistencia hoy — guarda
                    Map<String, Object> data = new HashMap<>();
                    data.put("classId",     classId);
                    data.put("studentId",   student.getId());
                    data.put("studentName", student.getName());
                    data.put("status",      status);
                    data.put("timestamp",   System.currentTimeMillis());

                    db.collection("attendance").add(data)
                            .addOnSuccessListener(ref ->
                                    Toast.makeText(getContext(),
                                            "✓ " + statusLabel + " registrado para "
                                                    + student.getName(),
                                            Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e ->
                                    Toast.makeText(getContext(),
                                            "Error al registrar asistencia",
                                            Toast.LENGTH_SHORT).show());
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI
    // ─────────────────────────────────────────────────────────────────────────
    private void updateUI() {
        tvStudentCountPeople.setText(studentList.size() + " estudiantes");
        if (studentList.isEmpty()) {
            showEmptyState();
        } else {
            rvStudents.setVisibility(View.VISIBLE);
            emptyStudents.setVisibility(View.GONE);
            adapter.notifyDataSetChanged();
        }
    }

    private void showEmptyState() {
        tvStudentCountPeople.setText("0 estudiantes");
        rvStudents.setVisibility(View.GONE);
        emptyStudents.setVisibility(View.VISIBLE);
    }
}