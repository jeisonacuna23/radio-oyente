#!/bin/bash
echo "🚀 Iniciando túnel de Cloudflare..."
cloudflared tunnel --url http://localhost:8000 > ~/ProyectoRadio/tunnel.log 2>&1 &
TUNNEL_PID=$!
sleep 12

echo "🔍 Extrayendo URL..."
NEW_URL=$(grep -o 'https://[a-zA-Z0-9.-]*\.trycloudflare\.com' ~/ProyectoRadio/tunnel.log | head -n 1)

if [ -n "$NEW_URL" ]; then
    echo "✅ URL generada: $NEW_URL"
    echo "☁️ Guardando en Supabase..."
    curl -s -X PATCH "https://hbrdeegsmhdhhopvavmf.supabase.co/rest/v1/stream_config?id=eq.1" \
    -H "apikey: sb_publishable_SdESX92YcDSOTlOhNfTNKA_1SQcbFTh" \
    -H "Authorization: Bearer sb_publishable_SdESX92YcDSOTlOhNfTNKA_1SQcbFTh" \
    -H "Content-Type: application/json" \
    -H "Prefer: return=representation" \
    -d "{\"url\": \"$NEW_URL\"}" > /dev/null
    echo "🎉 ¡URL publicada con éxito en Supabase!"
else
    echo "❌ Error: No se pudo extraer la URL del túnel."
fi
