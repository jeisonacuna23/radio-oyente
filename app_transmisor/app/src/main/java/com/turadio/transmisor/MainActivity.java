package com.turadio.transmisor;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // Usamos 127.0.0.1 porque la app y el servidor están en el mismo teléfono
    private static final String API_BASE = "http://127.0.0.1:8080/api/";

    private TextView tvStatus;
    private TextView tvNowPlaying;
    private Button btnPlayPause;
    private Button btnPrev;
    private Button btnNext;

    private boolean isPlaying = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvNowPlaying = findViewById(R.id.tvNowPlaying);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);

        btnPlayPause.setOnClickListener(v -> {
            if (isPlaying) {
                sendCommand("pause");
            } else {
                sendCommand("play");
            }
        });

        btnPrev.setOnClickListener(v -> sendCommand("prev"));
        btnNext.setOnClickListener(v -> sendCommand("next"));

        // Iniciar actualización del estado
        handler.post(updateStatusRunnable);
    }

    private final Runnable updateStatusRunnable = new Runnable() {
        @Override
        public void run() {
            fetchStatus();
            handler.postDelayed(this, 3000); // Se actualiza cada 3 segundos
        }
    };

    private void sendCommand(String command) {
        executor.execute(() -> {
            try {
                URL url = new URL(API_BASE + command);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.getInputStream().close(); // Ejecuta el comando
                
                // Refrescar estado inmediatamente después del comando
                fetchStatus(); 
            } catch (Exception e) {
                runOnUiThread(() -> tvStatus.setText("Error: Sin conexión al servidor"));
            }
        });
    }

    private void fetchStatus() {
        executor.execute(() -> {
            try {
                URL url = new URL(API_BASE + "status");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(response.toString());
                final String current = json.optString("current", "Desconocido");
                final String statusText = json.optString("status", "");

                // Determinar si está reproduciendo basado en la respuesta de mpc
                isPlaying = statusText.contains("[playing]");

                runOnUiThread(() -> {
                    tvNowPlaying.setText(current.isEmpty() ? "Sin reproducción" : current);
                    
                    if (isPlaying) {
                        btnPlayPause.setText("⏸");
                        tvStatus.setText("Estado: Transmitiendo en vivo");
                    } else {
                        btnPlayPause.setText("▶");
                        tvStatus.setText("Estado: En pausa");
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvStatus.setText("Estado: Servidor desconectado");
                    tvNowPlaying.setText("Verifica que Termux esté abierto");
                    isPlaying = false;
                    btnPlayPause.setText("▶");
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateStatusRunnable);
        executor.shutdownNow();
    }
}
