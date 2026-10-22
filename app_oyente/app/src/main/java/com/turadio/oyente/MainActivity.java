package com.turadio.oyente;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
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
    private WebView webViewPlayer;
    private String currentStreamUrl = "";
    private boolean isPlaying = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnPlayPause = findViewById(R.id.btnPlayPause);
        tvNowPlaying = findViewById(R.id.tvNowPlaying);
        webViewPlayer = findViewById(R.id.webViewPlayer);

        // Configurar el WebView para que se comporte como Chrome
        WebSettings webSettings = webViewPlayer.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false); // Permite autoplay
        webSettings.setDomStorageEnabled(true);
        
        webViewPlayer.setWebViewClient(new WebViewClient());
        webViewPlayer.setWebChromeClient(new WebChromeClient());

        btnPlayPause.setOnClickListener(v -> {
            if (currentStreamUrl.isEmpty()) {
                tvNowPlaying.setText("Buscando señal...");
                fetchStreamUrl();
                return;
            }

            if (isPlaying) {
                webViewPlayer.evaluateJavascript("document.getElementById('audioPlayer').pause();", null);
                isPlaying = false;
                btnPlayPause.setText("▶ Reproducir");
                tvNowPlaying.setText("En pausa");
            } else {
                webViewPlayer.evaluateJavascript("document.getElementById('audioPlayer').play();", null);
                isPlaying = true;
                btnPlayPause.setText("⏸ Pausar");
                tvNowPlaying.setText("Conectando al audio...");
            }
        });

        fetchStreamUrl();
    }

    private void loadAudioInWebView(String streamUrl) {
        // Creamos un reproductor HTML5 oculto con la URL
        String html = "<html><body><audio id='audioPlayer' src='" + streamUrl + "' preload='auto'></audio></body></html>";
        webViewPlayer.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
        tvNowPlaying.setText("Señal encontrada. Listo para reproducir.");
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
                        runOnUiThread(() -> loadAudioInWebView(currentStreamUrl));
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
