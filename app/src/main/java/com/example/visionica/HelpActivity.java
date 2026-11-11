package com.example.visionica;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import android.widget.Toast;

public class HelpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Ayuda");
        }

        // Setup opciones
        CardView btnFAQ = findViewById(R.id.btnFAQ);
        CardView btnContact = findViewById(R.id.btnContact);
        CardView btnTutorial = findViewById(R.id.btnTutorial);

        btnFAQ.setOnClickListener(v ->
                Toast.makeText(this, "Preguntas frecuentes - Próximamente", Toast.LENGTH_SHORT).show());

        btnContact.setOnClickListener(v ->
                Toast.makeText(this, "Contactar soporte - Próximamente", Toast.LENGTH_SHORT).show());

        btnTutorial.setOnClickListener(v ->
                Toast.makeText(this, "Tutorial - Próximamente", Toast.LENGTH_SHORT).show());
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}