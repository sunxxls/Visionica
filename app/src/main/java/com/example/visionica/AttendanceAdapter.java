package com.example.visionica;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

public class AttendanceAdapter extends RecyclerView.Adapter<AttendanceAdapter.ViewHolder> {

    private Context context;
    private List<AttendanceRecord> recordList;
    private SimpleDateFormat dateFormat;

    public AttendanceAdapter(Context context, List<AttendanceRecord> recordList) {
        this.context = context;
        this.recordList = recordList;
        this.dateFormat = new SimpleDateFormat("dd MMM yyyy - hh:mm a", new Locale("es", "ES"));
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_attendance, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AttendanceRecord record = recordList.get(position);

        holder.tvStudentName.setText(record.getStudentName());
        holder.tvAttendanceDate.setText(dateFormat.format(new Date(record.getTimestamp())));

        // Estado de asistencia
        String status = record.getStatus();
        if ("present".equals(status)) {
            holder.tvAttendanceStatus.setText("Presente");
            holder.tvAttendanceStatus.setTextColor(context.getResources().getColor(R.color.success));
            holder.tvAttendanceStatus.setBackgroundResource(R.drawable.bg_status_present);
        } else if ("late".equals(status)) {
            holder.tvAttendanceStatus.setText("Atraso");
            holder.tvAttendanceStatus.setTextColor(context.getResources().getColor(R.color.warning));
            holder.tvAttendanceStatus.setBackgroundResource(R.drawable.bg_status_late);
        } else if ("absent".equals(status)) {
            holder.tvAttendanceStatus.setText("Falta");
            holder.tvAttendanceStatus.setTextColor(context.getResources().getColor(R.color.error));
            holder.tvAttendanceStatus.setBackgroundResource(R.drawable.bg_status_absent);
        } else if ("license".equals(status)) {
            holder.tvAttendanceStatus.setText("Licencia");
            holder.tvAttendanceStatus.setTextColor(context.getResources().getColor(R.color.primary_purple));
            holder.tvAttendanceStatus.setBackgroundResource(R.drawable.bg_status_late);
        } else if ("permit".equals(status)) {
            holder.tvAttendanceStatus.setText("Permiso");
            holder.tvAttendanceStatus.setTextColor(0xFF1565C0); // azul
            holder.tvAttendanceStatus.setBackgroundResource(R.drawable.bg_status_late);
        }

        // Cargar foto
        if (record.getStudentPhoto() != null && !record.getStudentPhoto().isEmpty()) {
            Glide.with(context)
                    .load(record.getStudentPhoto())
                    .placeholder(R.drawable.ic_user_placeholder)
                    .circleCrop()
                    .into(holder.ivStudentPhoto);
        } else if (record.getStudentId() != null) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(record.getStudentId())
                    .get()
                    .addOnSuccessListener(doc -> {
                        String photoUrl = doc.getString("photoUrl");
                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            Glide.with(context)
                                    .load(photoUrl)
                                    .placeholder(R.drawable.ic_user_placeholder)
                                    .circleCrop()
                                    .into(holder.ivStudentPhoto);
                        }
                    });
        } else {
            holder.ivStudentPhoto.setImageResource(R.drawable.ic_user_placeholder);
        }
    }

    @Override
    public int getItemCount() {
        return recordList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CircleImageView ivStudentPhoto;
        TextView tvStudentName, tvAttendanceDate, tvAttendanceStatus;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivStudentPhoto = itemView.findViewById(R.id.ivStudentPhoto);
            tvStudentName = itemView.findViewById(R.id.tvStudentName);
            tvAttendanceDate = itemView.findViewById(R.id.tvAttendanceDate);
            tvAttendanceStatus = itemView.findViewById(R.id.tvAttendanceStatus);
        }
    }
}