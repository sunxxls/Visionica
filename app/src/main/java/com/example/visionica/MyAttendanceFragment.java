package com.example.visionica;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class MyAttendanceFragment extends Fragment {

    private static final String ARG_CLASS_ID = "classId";

    private String classId;
    private String currentUserId;

    // Views
    private TextView  tvPresentCount, tvLateCount, tvAbsentCount, tvTotalClasses;

    private TextView tvLicenseCount, tvPermitCount;
    private TextView  tvUserName, tvUserEmail;
    private ImageView ivUserAvatar;
    private RecyclerView rvMyAttendance;
    private LinearLayout emptyAttendance;

    // Data
    private FirebaseFirestore db;
    private FirebaseAuth      mAuth;
    private AttendanceAdapter adapter;
    private List<AttendanceRecord> myAttendanceList;

    public static MyAttendanceFragment newInstance(String classId) {
        MyAttendanceFragment fragment = new MyAttendanceFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CLASS_ID, classId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            classId = getArguments().getString(ARG_CLASS_ID);
        }
        db            = FirebaseFirestore.getInstance();
        mAuth         = FirebaseAuth.getInstance();
        currentUserId = mAuth.getCurrentUser().getUid();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_my_attendance, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Inicializar vistas existentes
        tvPresentCount = view.findViewById(R.id.tvPresentCount);
        tvLateCount    = view.findViewById(R.id.tvLateCount);
        tvAbsentCount  = view.findViewById(R.id.tvAbsentCount);
        tvTotalClasses = view.findViewById(R.id.tvTotalClasses);
        rvMyAttendance = view.findViewById(R.id.rvMyAttendance);
        emptyAttendance = view.findViewById(R.id.emptyAttendance);

        tvLicenseCount = view.findViewById(R.id.tvLicenseCount);
        tvPermitCount  = view.findViewById(R.id.tvPermitCount);

        // Nuevas vistas del perfil
        ivUserAvatar = view.findViewById(R.id.ivUserAvatar);
        tvUserName   = view.findViewById(R.id.tvUserName);
        tvUserEmail  = view.findViewById(R.id.tvUserEmail);

        // Cargar datos del usuario
        loadUserProfile();

        // Setup RecyclerView
        myAttendanceList = new ArrayList<>();
        adapter = new AttendanceAdapter(getContext(), myAttendanceList);
        rvMyAttendance.setLayoutManager(new LinearLayoutManager(getContext()));
        rvMyAttendance.setAdapter(adapter);

        loadMyAttendance();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CARGAR FOTO Y DATOS DEL USUARIO
    // ─────────────────────────────────────────────────────────────────────────
    private void loadUserProfile() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || ivUserAvatar == null) return;

        // Nombre y email desde Firebase Auth
        String displayName = user.getDisplayName();
        String email       = user.getEmail();

        if (tvUserName  != null) tvUserName.setText(displayName != null ? displayName : "Usuario");
        if (tvUserEmail != null) tvUserEmail.setText(email != null ? email : "");

        // Foto desde Google (Firebase Auth)
        if (user.getPhotoUrl() != null) {
            Glide.with(this)
                    .load(user.getPhotoUrl())
                    .placeholder(R.drawable.ic_user_placeholder)
                    .circleCrop()
                    .into(ivUserAvatar);
        } else {
            // Si no tiene foto de Google, buscar en Firestore (foto subida por el usuario)
            db.collection("users").document(currentUserId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            String photoUrl = doc.getString("photoUrl");
                            String name     = doc.getString("name");

                            if (name != null && !name.isEmpty() && tvUserName != null) {
                                tvUserName.setText(name);
                            }

                            if (photoUrl != null && !photoUrl.isEmpty()) {
                                Glide.with(this)
                                        .load(photoUrl)
                                        .placeholder(R.drawable.ic_user_placeholder)
                                        .circleCrop()
                                        .into(ivUserAvatar);
                            }
                        }
                    });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CARGAR ASISTENCIA
    // ─────────────────────────────────────────────────────────────────────────
    private void loadMyAttendance() {
        android.util.Log.d("MyAttendance", "ClassID: " + classId + " | StudentID: " + currentUserId);

        db.collection("attendance")
                .whereEqualTo("classId", classId)
                .whereEqualTo("studentId", currentUserId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((queryDocumentSnapshots, error) -> {
                    if (error != null) {
                        android.util.Log.e("MyAttendance", "Error: " + error.getMessage());
                        rvMyAttendance.setVisibility(View.GONE);
                        emptyAttendance.setVisibility(View.VISIBLE);
                        return;
                    }

                    if (queryDocumentSnapshots == null) return;

                    myAttendanceList.clear();

                    int presentCount = 0, lateCount = 0, licenseCount = 0,
                            absentCount = 0, permitCount = 0; // agrega permitCount

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        AttendanceRecord record = document.toObject(AttendanceRecord.class);
                        record.setId(document.getId());
                        myAttendanceList.add(record);

                        String status = record.getStatus();
                        if      ("present".equals(status)) presentCount++;
                        else if ("late".equals(status))    lateCount++;
                        else if ("license".equals(status)) licenseCount++;
                        else if ("absent".equals(status))  absentCount++;
                        else if ("permit".equals(status))  permitCount++;
                    }

                    tvPresentCount.setText(String.valueOf(presentCount));
                    tvLateCount.setText(String.valueOf(lateCount));
                    tvAbsentCount.setText(String.valueOf(absentCount));
                    if (tvLicenseCount != null) tvLicenseCount.setText(String.valueOf(licenseCount));
                    if (tvPermitCount  != null) tvPermitCount.setText(String.valueOf(permitCount));
                    tvTotalClasses.setText("Total: " + myAttendanceList.size() + " registros");

                    if (myAttendanceList.isEmpty()) {
                        rvMyAttendance.setVisibility(View.GONE);
                        emptyAttendance.setVisibility(View.VISIBLE);
                    } else {
                        rvMyAttendance.setVisibility(View.VISIBLE);
                        emptyAttendance.setVisibility(View.GONE);
                        adapter.notifyDataSetChanged();
                    }
                });
    }
}