package com.example.visionica;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class ClassAdapter extends RecyclerView.Adapter<ClassAdapter.ClassViewHolder> {

    private Context context;
    private List<ClassModel> classList;
    private FirebaseFirestore db;

    private boolean archivedMode = false;

    public ClassAdapter(Context context, List<ClassModel> classList) {
        this(context, classList, false);
    }

    public ClassAdapter(Context context, List<ClassModel> classList, boolean archivedMode) {
        this.context = context;
        this.classList = classList;
        this.archivedMode = archivedMode;
        this.db = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public ClassViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_class, parent, false);
        return new ClassViewHolder(view);

    }

    @Override
    public void onBindViewHolder(@NonNull ClassViewHolder holder, int position) {
        ClassModel classModel = classList.get(position);

        // Datos básicos
        holder.tvClassName.setText(classModel.getName());
        holder.tvClassSection.setText(classModel.getSection());
        holder.tvTeacherName.setText(classModel.getTeacherName());
        holder.tvClassCode.setText(classModel.getCode());

        // Contar estudiantes
        int studentCount = classModel.getStudents() != null ? classModel.getStudents().size() : 0;
        holder.tvStudentCount.setText(studentCount + " estudiantes");

        // Click en la tarjeta - abrir detalles de clase
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ClassDetailActivity.class);
            intent.putExtra("classId", classModel.getId());
            intent.putExtra("className", classModel.getName());
            intent.putExtra("classCode", classModel.getCode());
            context.startActivity(intent);
        });

        if (classModel.getBannerUrl() != null && !classModel.getBannerUrl().isEmpty()) {
            Glide.with(context)
                    .load(classModel.getBannerUrl())
                    .placeholder(R.drawable.bg_class_header)
                    .into(holder.ivClassBanner);
        } else {
            // ← AGREGAR ESTO
            Glide.with(context)
                    .clear(holder.ivClassBanner);
            holder.ivClassBanner.setImageResource(R.drawable.bg_class_header);
        }

        // Menú de opciones
        holder.btnClassOptions.setOnClickListener(v -> showOptionsMenu(v, classModel, position));
    }

    private void showOptionsMenu(View view, ClassModel classModel, int position) {
        PopupMenu popupMenu = new PopupMenu(context, view);
        popupMenu.inflate(R.menu.class_options_menu);

        MenuItem archiveItem = popupMenu.getMenu().findItem(R.id.menu_archive);
        MenuItem unarchiveItem = popupMenu.getMenu().findItem(R.id.menu_unarchive);

        // AQUÍ ESTABA TU ERROR: faltaba ocultar según el modo
        if (archiveItem != null && unarchiveItem != null) {
            if (archivedMode) {
                archiveItem.setVisible(false);
                unarchiveItem.setVisible(true);
            } else {
                archiveItem.setVisible(true);
                unarchiveItem.setVisible(false);
            }
        }

        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();

            if (id == R.id.menu_view_details) {
                Intent intent = new Intent(context, ClassDetailActivity.class);
                intent.putExtra("classId", classModel.getId());
                intent.putExtra("className", classModel.getName());
                intent.putExtra("classCode", classModel.getCode());
                context.startActivity(intent);
                return true;

            } else if (id == R.id.menu_copy_code) {
                android.content.ClipboardManager clipboard =
                        (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);

                android.content.ClipData clip =
                        android.content.ClipData.newPlainText("Código de clase", classModel.getCode());

                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, "Código copiado: " + classModel.getCode(), Toast.LENGTH_SHORT).show();
                return true;

            } else if (id == R.id.menu_archive) {
                archiveClass(classModel, position);
                return true;

            } else if (id == R.id.menu_unarchive) {
                unarchiveClass(classModel, position);
                return true;

            } else if (id == R.id.menu_delete) {
                deleteClass(classModel, position);
                return true;
            }

            return false;
        });

        popupMenu.show();
    }

    private void archiveClass(ClassModel classModel, int position) {
        // Elimina visualmente de inmediato
        classList.remove(position);
        notifyItemRemoved(position);

        // Limpia caché
        android.content.SharedPreferences prefs =
                context.getSharedPreferences("visionica_prefs", Context.MODE_PRIVATE);
        prefs.edit().remove("cached_classes_" +
                com.google.firebase.auth.FirebaseAuth.getInstance()
                        .getCurrentUser().getUid()).apply();

        db.collection("classes").document(classModel.getId())
                .update("archived", true)
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(context, "Clase archivada", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> {
                    // Si falla, regresa la clase
                    classList.add(position, classModel);
                    notifyItemInserted(position);
                    Toast.makeText(context, "Error al archivar", Toast.LENGTH_SHORT).show();
                });
    }

    private void unarchiveClass(ClassModel classModel, int position) {
        // Elimina visualmente de inmediato
        classList.remove(position);
        notifyItemRemoved(position);

        // Limpia caché
        android.content.SharedPreferences prefs =
                context.getSharedPreferences("visionica_prefs", Context.MODE_PRIVATE);
        prefs.edit().remove("cached_classes_" +
                com.google.firebase.auth.FirebaseAuth.getInstance()
                        .getCurrentUser().getUid()).apply();

        db.collection("classes").document(classModel.getId())
                .update("archived", false)
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(context, "Clase desarchivada", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> {
                    // Si falla, regresa la clase
                    classList.add(position, classModel);
                    notifyItemInserted(position);
                    Toast.makeText(context, "Error al desarchivar", Toast.LENGTH_SHORT).show();
                });
    }

    private void deleteClass(ClassModel classModel, int position) {
        // Elimina visualmente de inmediato
        classList.remove(position);
        notifyItemRemoved(position);

        // Limpia caché
        android.content.SharedPreferences prefs =
                context.getSharedPreferences("visionica_prefs", Context.MODE_PRIVATE);
        prefs.edit().remove("cached_classes_" +
                com.google.firebase.auth.FirebaseAuth.getInstance()
                        .getCurrentUser().getUid()).apply();

        db.collection("classes").document(classModel.getId())
                .delete()
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(context, "Clase eliminada", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> {
                    // Si falla, regresa la clase
                    classList.add(position, classModel);
                    notifyItemInserted(position);
                    Toast.makeText(context, "Error al eliminar", Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public int getItemCount() {
        return classList.size();
    }

    static class ClassViewHolder extends RecyclerView.ViewHolder {
        TextView tvClassName, tvClassSection, tvTeacherName, tvStudentCount, tvClassCode;
        ImageView btnClassOptions, ivClassBanner;

        public ClassViewHolder(@NonNull View itemView) {
            super(itemView);
            tvClassName = itemView.findViewById(R.id.tvClassName);
            tvClassSection = itemView.findViewById(R.id.tvClassSection);
            tvTeacherName = itemView.findViewById(R.id.tvTeacherName);
            tvStudentCount = itemView.findViewById(R.id.tvStudentCount);
            tvClassCode = itemView.findViewById(R.id.tvClassCode);
            btnClassOptions = itemView.findViewById(R.id.btnClassOptions);

            ivClassBanner = itemView.findViewById(R.id.ivClassBanner);
        }
    }
}