package com.example.visionica;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class AttendanceFragment extends Fragment {

    private static final String ARG_CLASS_ID = "classId";
    private static final String ARG_IS_TEACHER = "isTeacher";

    private String classId;
    private boolean isTeacher;

    // Views
    private TextView tvPresentCount, tvLateCount, tvAbsentCount;
    private RecyclerView rvAttendance;
    private LinearLayout emptyAttendance;
    private Button btnSelectDate;

    // Data
    private FirebaseFirestore db;
    private AttendanceAdapter adapter;
    private List<AttendanceRecord> attendanceList;

    public static AttendanceFragment newInstance(String classId, boolean isTeacher) {
        AttendanceFragment fragment = new AttendanceFragment();
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
            classId = getArguments().getString(ARG_CLASS_ID);
            isTeacher = getArguments().getBoolean(ARG_IS_TEACHER);
        }
        db = FirebaseFirestore.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_attendance, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Inicializar vistas
        tvPresentCount = view.findViewById(R.id.tvPresentCount);
        tvLateCount = view.findViewById(R.id.tvLateCount);
        tvAbsentCount = view.findViewById(R.id.tvAbsentCount);
        rvAttendance = view.findViewById(R.id.rvAttendance);
        emptyAttendance = view.findViewById(R.id.emptyAttendance);
        btnSelectDate = view.findViewById(R.id.btnSelectDate);

        // Setup RecyclerView
        attendanceList = new ArrayList<>();
        adapter = new AttendanceAdapter(getContext(), attendanceList);
        rvAttendance.setLayoutManager(new LinearLayoutManager(getContext()));
        rvAttendance.setAdapter(adapter);

        // Cargar asistencias
        loadAttendanceRecords();

        // Setup listeners
        btnSelectDate.setOnClickListener(v -> {
            // TODO: Mostrar DatePicker
        });
    }

    private void loadAttendanceRecords() {
        db.collection("attendance")
                .whereEqualTo("classId", classId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    attendanceList.clear();

                    int presentCount = 0;
                    int lateCount = 0;
                    int absentCount = 0;

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        AttendanceRecord record = document.toObject(AttendanceRecord.class);
                        record.setId(document.getId());
                        attendanceList.add(record);

                        // Contar por estado
                        String status = record.getStatus();
                        if ("present".equals(status)) presentCount++;
                        else if ("late".equals(status)) lateCount++;
                        else if ("absent".equals(status)) absentCount++;
                    }

                    // Actualizar contadores
                    tvPresentCount.setText(String.valueOf(presentCount));
                    tvLateCount.setText(String.valueOf(lateCount));
                    tvAbsentCount.setText(String.valueOf(absentCount));

                    // Actualizar UI
                    if (attendanceList.isEmpty()) {
                        rvAttendance.setVisibility(View.GONE);
                        emptyAttendance.setVisibility(View.VISIBLE);
                    } else {
                        rvAttendance.setVisibility(View.VISIBLE);
                        emptyAttendance.setVisibility(View.GONE);
                        adapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    rvAttendance.setVisibility(View.GONE);
                    emptyAttendance.setVisibility(View.VISIBLE);
                });
    }
}