package com.example.visionica;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class ArchivedClassesActivity extends AppCompatActivity {

    private RecyclerView rvArchivedClasses;
    private LinearLayout emptyArchived;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    private List<ClassModel> archivedClassesList;
    private ClassAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_archived_classes);

        // Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Clases Archivadas");
        }

        // Views
        rvArchivedClasses = findViewById(R.id.rvArchivedClasses);
        emptyArchived = findViewById(R.id.emptyArchived);

        // Setup RecyclerView
        archivedClassesList = new ArrayList<>();
        adapter = new ClassAdapter(this, archivedClassesList, true);
        rvArchivedClasses.setLayoutManager(new LinearLayoutManager(this));
        rvArchivedClasses.setAdapter(adapter);

        loadArchivedClasses();
    }

    private void loadArchivedClasses() {
        String userId = mAuth.getCurrentUser().getUid();

        // Cargar clases archivadas por el usuario
        db.collection("classes")
                .whereEqualTo("archived", true)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    archivedClassesList.clear();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String teacherId = doc.getString("teacherId");
                        List<String> students = (List<String>) doc.get("students");

                        // Verificar si el usuario está en la clase
                        if ((teacherId != null && teacherId.equals(userId)) ||
                                (students != null && students.contains(userId))) {

                            ClassModel classModel = doc.toObject(ClassModel.class);
                            classModel.setId(doc.getId());
                            archivedClassesList.add(classModel);
                        }
                    }

                    updateUI();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al cargar clases archivadas", Toast.LENGTH_SHORT).show();
                    updateUI();
                });
    }

    private void updateUI() {
        if (archivedClassesList.isEmpty()) {
            rvArchivedClasses.setVisibility(View.GONE);
            emptyArchived.setVisibility(View.VISIBLE);
        } else {
            rvArchivedClasses.setVisibility(View.VISIBLE);
            emptyArchived.setVisibility(View.GONE);
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}