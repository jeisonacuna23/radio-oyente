package com.turadio.oyente;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String SERVER_IP = "127.0.0.1";
    private static final String STREAM_URL = "http://" + SERVER_IP + ":8000/";
    private static final String API_URL = "http://" + SERVER_IP + ":8080/api/status";

    private Button btnPlayPause;
    private TextView tvNowPlaying;
    private MediaController mediaController;
    private ListenableFuture<MediaController> controllerFuture;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnPlayPause = findViewById(R.id.btnPlayPause);
        tvNowPlaying = findViewById(R.id.tvNowPlaying);

        // Conectar con el Servicio en Segundo Plano
        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        controllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();
        
        controllerFuture.addListener(() -> {
            try {
                mediaController = controllerFuture.get();
                MediaItem mediaItem = MediaItem.fromUri(STREAM_URL);
                mediaController.setMediaItem(mediaItem);
                mediaController.prepare();
                
                // Escuchar cambios de estado para actualizar el botón
                mediaController.addListener(new Player.Listener() {
                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                        runOnUiThread(() -> {
                            if (isPlaying) {
                                btnPlayPause.setText("⏸ Pausar");
                            } else {
                                btnPlayPause.setText("▶ Reproducir");
                            }
                        });
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, MoreExecutors.directExecutor());

        btnPlayPause.setOnClickListener(v -> {
            if (mediaController != null) {
                if (mediaController.isPlaying()) {
                    mediaController.pause();
                } else {
                    mediaController.play();
                }
            }
        });

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
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
        }
        handler.removeCallbacks(updateStatusRunnable);
        executor.shutdownNow();
    }
}
