package com.turadio.oyente;

import android.content.ComponentName;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import org.json.JSONArray;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private static final String SUPABASE_URL = "https://hbrdeegsmhdhhopvavmf.supabase.co/rest/v1/stream_config?id=eq.1&select=url";
    private static final String SUPABASE_KEY = "sb_publishable_SdESX92YcDSOTlOhNfTNKA_1SQcbFTh";

    private Button btnPlayPause;
    private TextView tvNowPlaying;
    private MediaController mediaController;
    private ListenableFuture<MediaController> controllerFuture;
    private String currentStreamUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnPlayPause = findViewById(R.id.btnPlayPause);
        tvNowPlaying = findViewById(R.id.tvNowPlaying);

        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        controllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();

        controllerFuture.addListener(() -> {
            try {
                mediaController = controllerFuture.get();
                mediaController.addListener(new Player.Listener() {
                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                        runOnUiThread(() -> {
                            if (isPlaying) btnPlayPause.setText("⏸ Pausar");
                            else btnPlayPause.setText("▶ Reproducir");
                        });
                    }
                });
                
                // Si la URL ya se descargó de Supabase, la preparamos aquí
                if (!currentStreamUrl.isEmpty()) {
                    prepararReproductor();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, MoreExecutors.directExecutor());

        btnPlayPause.setOnClickListener(v -> {
            if (mediaController != null) {
                if (mediaController.isPlaying()) {
                    mediaController.pause();
                } else {
                    if (currentStreamUrl.isEmpty()) {
                        tvNowPlaying.setText("Buscando señal...");
                        fetchStreamUrl();
                    } else {
                        prepararReproductor();
                        mediaController.play();
                    }
                }
            }
        });

        fetchStreamUrl();
    }

    private void prepararReproductor() {
        if (mediaController != null && !currentStreamUrl.isEmpty()) {
            if (mediaController.getCurrentMediaItem() == null) {
                mediaController.setMediaItem(MediaItem.fromUri(currentStreamUrl));
                mediaController.prepare();
            }
        }
    }

    private void fetchStreamUrl() {
        new Thread(() -> {
            try {
                URL url = new URL(SUPABASE_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("apikey", SUPABASE_KEY);
                conn.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY);
                conn.setConnectTimeout(5000);
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONArray jsonArray = new JSONArray(response.toString());
                if (jsonArray.length() > 0) {
                    String streamUrl = jsonArray.getJSONObject(0).getString("url");
                    
                    if (!streamUrl.equals("vacio") && !streamUrl.isEmpty()) {
                        currentStreamUrl = streamUrl;
                        runOnUiThread(() -> {
                            tvNowPlaying.setText("Señal encontrada. Listo para reproducir.");
                            prepararReproductor(); // Lo preparamos apenas llega
                        });
                    } else {
                        runOnUiThread(() -> tvNowPlaying.setText("Radio fuera de línea (Sin URL)"));
                    }
                }
            } catch (Exception e) {
                runOnUiThread(() -> tvNowPlaying.setText("Error de conexión a la nube"));
            }
        }).start();
    }
}
