# DuoPaper (Android)

Tunt Android-skal för [duopaper.northerngo.com](https://duopaper.northerngo.com) som byter
bakgrundsbild på partnerns telefon.

- Appen laddar webbappen live, så ändringar i NorthernGo når telefonerna utan ny installation.
- `WallpaperSyncWorker` laddar sidan dolt ungefär var 15:e minut och byter bakgrund även när appen är stängd.
- Bilder är krypterade mellan de två parkopplade telefonerna.

**Ladda ner senaste APK:** https://github.com/langbergnicklas-ship-it/duopaper/releases/latest/download/DuoPaper.apk

Bygget körs av GitHub Actions (`.github/workflows/build-apk.yml`) vid varje push till `main`.
Signeringsnyckeln ligger i repots secrets `DUOPAPER_KEYSTORE_B64` och `DUOPAPER_KEYSTORE_PASSWORD`.
