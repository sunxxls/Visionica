package com.example.visionica;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PendingAttendanceAdapter
        extends RecyclerView.Adapter<PendingAttendanceAdapter.VH> {

    private final List<PendingAttendance> items;

    public PendingAttendanceAdapter(List<PendingAttendance> items) {
        this.items = items;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pending_attendance, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        PendingAttendance p = items.get(position);

        h.tvStudentName.setText(p.studentName);
        h.tvClassName.setText(p.className);

        // Fecha y hora legible
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy  HH:mm", Locale.getDefault());
        h.tvDateTime.setText(sdf.format(new Date(p.timestamp)));

        // Estado con color y emoji
        switch (p.status) {
            case "present":
                h.tvStatus.setText("✓ Presente");
                h.tvStatus.setTextColor(Color.parseColor("#2E7D32"));
                h.statusBar.setBackgroundColor(Color.parseColor("#4CAF50"));
                break;
            case "late":
                h.tvStatus.setText("⚠ Atraso");
                h.tvStatus.setTextColor(Color.parseColor("#E65100"));
                h.statusBar.setBackgroundColor(Color.parseColor("#FF9800"));
                break;
            case "absent":
                h.tvStatus.setText("✗ Falta");
                h.tvStatus.setTextColor(Color.parseColor("#C62828"));
                h.statusBar.setBackgroundColor(Color.parseColor("#F44336"));
                break;
            case "license":
                h.tvStatus.setText("📋 Licencia");
                h.tvStatus.setTextColor(Color.parseColor("#1565C0"));
                h.statusBar.setBackgroundColor(Color.parseColor("#1976D2"));
                break;
            default:
                h.tvStatus.setText(p.status);
                h.tvStatus.setTextColor(Color.parseColor("#757575"));
                h.statusBar.setBackgroundColor(Color.parseColor("#BDBDBD"));
        }
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvStudentName, tvClassName, tvStatus, tvDateTime;
        View     statusBar;

        VH(@NonNull View v) {
            super(v);
            tvStudentName = v.findViewById(R.id.tvStudentName);
            tvClassName   = v.findViewById(R.id.tvClassName);
            tvStatus      = v.findViewById(R.id.tvStatus);
            tvDateTime    = v.findViewById(R.id.tvDateTime);
            statusBar     = v.findViewById(R.id.statusBar);
        }
    }
}