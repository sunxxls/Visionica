package com.example.visionica;

import android.content.Intent;
import android.os.Bundle;
import com.bumptech.glide.Glide;
import android.os.Handler;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DURATION = 10000; // 5 segundos

    private ImageView ivLogo;
    private TextView tvAppName;
    private TextView tvSubtitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // No hacer nada
            }
        });

        // Ocultar la barra de acción si existe
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Inicializar vistas
        initViews();

        // Aplicar animaciones
        applyAnimations();

        // Navegar a la siguiente actividad después de 5 segundos
        navigateToNextScreen();
    }

    private void initViews() {
        ivLogo = findViewById(R.id.ivLogo);
        tvAppName = findViewById(R.id.tvAppName);
        tvSubtitle = findViewById(R.id.tvSubtitle);

        // Cargar el GIF animado
        Glide.with(this)
                .asGif()
                .load(R.drawable.ave)
                .into(ivLogo);
    }

    private void applyAnimations() {
        // Animación de zoom in para el logo
        Animation zoomIn = AnimationUtils.loadAnimation(this, R.anim.zoom_in);
        ivLogo.startAnimation(zoomIn);

        // Animación de fade in para el nombre
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        tvAppName.startAnimation(fadeIn);
        tvSubtitle.startAnimation(fadeIn);
    }

    private void navigateToNextScreen() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // Cambiar a la actividad principal (LoginActivity o MainActivity)
                Intent intent = new Intent(SplashActivity.this, WelcomeActivity.class);
                startActivity(intent);
                finish(); // Finalizar SplashActivity para que no vuelva al presionar atrás
            }
        }, SPLASH_DURATION);
    }

}