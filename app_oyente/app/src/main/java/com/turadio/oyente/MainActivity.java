package com.turadio.oyente;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // CONFIGURACIÓN: Cambia esta IP por la de tu teléfono servidor
    private static final String SERVER_IP = "127.0.0.1";
    private static final String STREAM_URL = "http://" + SERVER_IP + ":8000/";
    private static final String API_URL = "http://" + SERVER_IP + ":8080/api/status";

    private ExoPlayer player;
    private Button btnPlayPause;
    private TextView tvNowPlaying;
    private boolean isPlaying = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnPlayPause = findViewById(R.id.btnPlayPause);
        tvNowPlaying = findViewById(R.id.tvNowPlaying);

        player = new ExoPlayer.Builder(this).build();
        MediaItem mediaItem = MediaItem.fromUri(STREAM_URL);
        player.setMediaItem(mediaItem);

        btnPlayPause.setOnClickListener(v -> {
            if (isPlaying) {
                player.pause();
                isPlaying = false;
                btnPlayPause.setText("▶ Reproducir");
            } else {
                player.prepare();
                player.play();
                isPlaying = true;
                btnPlayPause.setText("⏸ Pausar");
            }
        });

        // Iniciar actualización del estado cada 5 segundos
        handler.post(updateStatusRunnable);
    }

    private final Runnable updateStatusRunnable = new Runnable() {
        @Override
        public void run() {
            fetchNowPlaying();
            handler.postDelayed(this, 5000);
        }
    };

    private void fetchNowPlaying() {
        executor.execute(() -> {
            try {
                URL url = new URL(API_URL);
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
                final String status = json.optString("status", "");

                final String displayText = status.contains("[playing]") 
                        ? "Sonando: " + current 
                        : "En pausa";

                runOnUiThread(() -> tvNowPlaying.setText(displayText));

            } catch (Exception e) {
                runOnUiThread(() -> tvNowPlaying.setText("Desconectado de la radio"));
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
        handler.removeCallbacks(updateStatusRunnable);
        executor.shutdownNow();
    }
}
