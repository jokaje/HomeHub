# HomeHub

Eine native Android-App (Kotlin + Jetpack Compose), die deine Self-Hosted-Dienste bündelt:

| Dienst | Integration |
|---|---|
| **Hermes Agent** | Nativ: Chat mit lebendigem Partikel-Orb-Avatar, Spracheingabe (STT) & Sprachausgabe (TTS) |
| **Immich** | Nativ: Timeline, Alben, KI-Suche, Personen, Karte, Favoriten/Archiv/Papierkorb, Teilen, manueller & automatischer Upload |
| **Home Assistant** | Integrierter Browser (Login bleibt erhalten) |
| **Open WebUI** | Integrierter Browser |
| **ComfyUI** | Nativ: Workflows ausführen, Ergebnisse als Galerie |

Jeder Dienst unterstützt **zwei URLs** (lokal + remote). Die App prüft automatisch, ob der Server im Heimnetz erreichbar ist, und fällt sonst auf die Remote-URL zurück.

---

## 1. Bauen

1. [Android Studio](https://developer.android.com/studio) (Koala oder neuer) installieren.
2. Diesen Ordner mit **File → Open** öffnen. Android Studio richtet Gradle (8.7) automatisch ein.
   - Falls nach dem Gradle-Wrapper gefragt wird: bestätigen, Android Studio erzeugt ihn.
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)** – das fertige APK liegt danach unter
   `app/build/outputs/apk/debug/app-debug.apk`.
4. APK aufs Handy kopieren und installieren (Installation aus unbekannten Quellen erlauben), oder das Gerät per USB verbinden und auf ▶ Run drücken.

Mindestversion: **Android 8.0** (API 26). Empfohlen: Android 13+.

## 2. Dienste einrichten (in der App: Mehr → Einstellungen)

Pro Dienst trägst du ein:
- **Lokale URL** – z.B. `http://192.168.1.10:2283`
- **Remote-URL** – z.B. `https://immich.meinedomain.de` (optional)
- **API-Key / Token** – siehe unten

### Immich
- API-Key erstellen: Immich-Weboberfläche → Avatar → **Kontoeinstellungen → API-Schlüssel → Neuer Schlüssel**.
- Die App nutzt die Immich-REST-API (Stand ~v1.120+). Da Immich die API laufend weiterentwickelt:
  Liefert ein Bereich Fehler (z.B. 404), öffne `http://DEIN-SERVER:2283/api/docs` und gleiche die Pfade in
  `app/src/main/java/com/homehub/data/immich/ImmichApi.kt` ab – alle Endpunkte sind dort zentral gesammelt.
- **Auto-Upload**: In den Einstellungen aktivieren. Neue Fotos (optional Videos) werden alle 6 Stunden im WLAN gesichert. „Jetzt sichern" stößt einen sofortigen Lauf an. Beim ersten Start fragt Android nach der Medien-Berechtigung.

### Hermes Agent
- URL des Servers eintragen; die App ruft `{URL}/v1/chat/completions` auf (OpenAI-kompatibel, Streaming).
- **Modellname** im Feld „Modellname" eintragen (das, was deine API im Feld `model` erwartet).
- API-Key nur nötig, wenn dein Server einen verlangt (wird als `Authorization: Bearer …` gesendet).
- **Orb**: Der Avatar hört zu (Mikrofon-Taste), denkt und spricht. Erkennt er Themen wie Schiff, Herz, Stern, Haus, Rakete, Baum, Wolke oder Feuer im Gespräch, formt sich die Punktwolke entsprechend. Weitere Formen kannst du in `ui/hermes/ShapeLibrary.kt` ergänzen (Stichwortliste + Punktgenerator).
- Sprachausgabe nutzt die Android-TTS-Stimme deines Geräts (Lautsprecher-Symbol zum Stummschalten).

### Home Assistant & Open WebUI
- Nur die URL(s) eintragen. Der Login passiert im integrierten Browser und bleibt gespeichert.
- Das Long-Lived-Token bei Home Assistant ist optional (derzeit nur für künftige Erweiterungen gedacht).

### ComfyUI
- URL(s) eintragen.
- In ComfyUI: Zahnrad → **Dev-Mode** aktivieren → Workflow laden → **Save (API Format)**.
- Das JSON in der App auf der ComfyUI-Seite einfügen. Platzhalter:
  - `{{PROMPT}}` → wird durch deinen eingegebenen Prompt ersetzt
  - `{{SEED}}` → Zufallszahl pro Lauf
- Beispiel: Im Workflow-JSON beim CLIP-Text-Encode-Knoten `"text": "{{PROMPT}}"` setzen.

## 3. Hinweise

- **HTTP im Heimnetz** ist erlaubt (`usesCleartextTraffic`). Für den Remote-Zugriff empfehle ich HTTPS (Reverse Proxy, z.B. Caddy/Traefik/Nginx Proxy Manager).
- Alle Tokens werden **verschlüsselt** gespeichert (Android Keystore / EncryptedSharedPreferences).
- Die Karte (Immich) nutzt OpenStreetMap-Kacheln (osmdroid).
- Dieses Projekt wurde ohne Netzwerkzugriff erstellt und konnte daher **nicht testkompiliert** werden. Beim ersten Sync/Build können vereinzelt kleine Korrekturen nötig sein – die Struktur ist bewusst so gehalten, dass Android Studio Fehler präzise anzeigt.

## 4. Projektstruktur (Kurzüberblick)

```
app/src/main/java/com/homehub/
├── core/ServiceLocator.kt          – verdrahtet alles
├── data/
│   ├── settings/                   – verschlüsselte Einstellungen
│   ├── network/UrlResolver.kt      – lokal/remote-Logik
│   ├── hermes/HermesClient.kt      – OpenAI-API + Streaming
│   ├── immich/                     – API, Modelle, Repository
│   └── comfy/ComfyClient.kt        – /prompt, /history, /view
├── work/AutoUploadWorker.kt        – Foto-Backup im Hintergrund
└── ui/
    ├── dashboard/                  – Startseite mit Status
    ├── hermes/                     – Orb, STT/TTS, Chat
    ├── immich/                     – Timeline, Alben, Suche, …
    ├── comfy/                      – Workflow-Runner
    ├── web/                        – WebView (HA, Open WebUI)
    └── settings/                   – Einstellungen
```
