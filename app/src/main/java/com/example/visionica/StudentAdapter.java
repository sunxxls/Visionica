package com.example.visionica;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class StudentAdapter extends RecyclerView.Adapter<StudentAdapter.ViewHolder> {

    private Context context;
    private List<Student> studentList;
    private OnStudentClickListener listener;

    // ── Interface para el click ───────────────────────────────────────────────
    public interface OnStudentClickListener {
        void onStudentClick(Student student);
    }

    public StudentAdapter(Context context, List<Student> studentList) {
        this.context = context;
        this.studentList = studentList;
    }

    public void setOnStudentClickListener(OnStudentClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_student, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Student student = studentList.get(position);

        holder.tvStudentName.setText(student.getName() != null ? student.getName() : "Usuario");
        holder.tvStudentEmail.setText(student.getEmail() != null ? student.getEmail() : "");

        // Mostrar ícono si tiene rostro registrado
        if (student.isFaceRegistered()) {
            holder.ivFaceStatus.setVisibility(View.VISIBLE);
        } else {
            holder.ivFaceStatus.setVisibility(View.GONE);
        }

        // Cargar foto
        if (student.getPhotoUrl() != null && !student.getPhotoUrl().isEmpty()) {
            Glide.with(context)
                    .load(student.getPhotoUrl())
                    .placeholder(R.drawable.ic_user_placeholder)
                    .circleCrop()
                    .into(holder.ivStudentPhoto);
        }

        // ── Click en el estudiante ────────────────────────────────────────────
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onStudentClick(student);
        });
    }

    @Override
    public int getItemCount() {
        return studentList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CircleImageView ivStudentPhoto;
        TextView tvStudentName, tvStudentEmail;
        ImageView ivFaceStatus;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivStudentPhoto = itemView.findViewById(R.id.ivStudentPhoto);
            tvStudentName  = itemView.findViewById(R.id.tvStudentName);
            tvStudentEmail = itemView.findViewById(R.id.tvStudentEmail);
            ivFaceStatus   = itemView.findViewById(R.id.ivFaceStatus);
        }
    }
}