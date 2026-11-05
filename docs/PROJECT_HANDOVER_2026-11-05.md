# Przejęcie organizacji prac — GTV Streamer Drive / Panel NAS

**Data rozpoczęcia uporządkowanego rozwoju:** 2026-11-05 (Europe/Warsaw).  
**Repozytorium:** `romanberens/gtv-streamer-drive`  
**Właściciel projektu i decyzji:** Roman Berens.  
**Wsparcie projektowe:** ChatGPT — analiza, dokumentacja, propozycje zmian i implementacja wyłącznie w zakresie zlecanych prac.  
**Charakter wpisu:** początek nowego etapu prowadzenia projektu, **nie** zmiana praw własności, licencji ani autorstwa wcześniejszych commitów.

## Punkt wyjścia (baseline)

- Gałąź bazowa: `main`.
- Commit bazowy: `ad82d2ccccf1adf2829ebe9a5f13e5cb446aabae` (2026-10-31).
- Wersja panelu według Gradle: `0.1.0`; stan: prototyp.
- Moduł frontendowy: `nas-browser/` (Kotlin, Jetpack Compose, Android TV).
- Backend: `third_party/otgmaster/` (obsługa USB/NTFS, provider i most podpisowy).
- Funkcje według kodu i dokumentacji: przeglądanie katalogów, otwieranie plików, lokalny HTTP upload `:8789`, QR do parowania telefonu, usługa pierwszoplanowa.
- Dostępne: instrukcje architektury, bezpieczeństwa, uruchamiania i regresji w `docs/`.

**Uwaga o weryfikacji:** dokumentacja opisuje wcześniejsze testy ręczne na Google TV Streamer. Ten wpis nie poświadcza nowej kompilacji, instalacji ani testów sprzętowych przeprowadzonych 2026-11-05.

## Cele kolejnego etapu

1. Poprawna, odporna na błędy nawigacja po drzewie dokumentów SAF i przekazywanie URI odtwarzaczom.
2. Bezpieczny serwer uploadu: uwierzytelnienie przed pracochłonnym parsowaniem, ograniczenia transferu/prób, porządne raportowanie błędów.
3. Spójność stanu serwera, odświeżania UI i obsługi odłączenia nośnika.
4. Nowy interfejs TV zgodny z pilotem: stan dysku, przeglądarka, QR, postęp transferu, ekran pustego katalogu i błędów.
5. Testy automatyczne i pipeline CI przed pracami nad wydaniami.

## Ryzyka / hipotezy do sprawdzenia

- `MainActivity.kt`: użycie `buildTreeDocumentUri` przy przechodzeniu w podkatalogi i `buildDocumentUri` podczas otwierania plików — sprawdzić granty i URI w runtime.
- `PhoneUploadServer.kt`: `parseBody()` przed weryfikacją kodu/ciasteczka — sprawdzić ryzyko DoS, limity żądań i pliki tymczasowe.
- `UploadServerRuntime`: singleton uruchamiany najpierw z usługi może nie podłączyć callbacku do późniejszego UI.
- `settings.gradle.kts` zawiera historyczną nazwę `KidsTv` oraz moduły nieobecne w obecnym drzewie; do budowy panelu używać `settings.gtv.gradle.kts`.
- `build/`, `.gradle/` i artefakty APK **nie** są źródłami do synchronizacji ani commitowania. Należy je pozostawić poza repozytorium; w katalogach backupu Drive mogą być już zsynchronizowane.

**Nie klasyfikować tych hipotez jako błędów potwierdzonych na urządzeniu bez reprodukcji.**

## Granice odpowiedzialności i danych

- GitHub: źródło prawdy o kodzie, historia commitów, Pull Requesty.
- Google Drive: dokumentacja zarządcza, wizualizacje, zdjęcia, raporty testów i wersje koncepcyjne.
- Katalog synchronizacji komputera: robocza/backupowa kopia; nie zastępuje Git ani procedury publikacji.
- Zmiany zaczynamy w `feature/*` / `fix/*` / `chore/*`, bez bezpośredniego zapisu na `main`.
- Przed scaleniem: przegląd diffu, wynik kompilacji, testy i akceptacja właściciela.
- Żadnego automatycznego wdrożenia na streamer ani przekierowania portu 8789 do Internetu.
- Nie zapisywać w publicznym repo: haseł, kodów parowania, plików prywatnych, dokładnych danych sieci domowej, prywatnych linków do Drive.

## Powiązane dokumenty

- [WORKFLOW.md](WORKFLOW.md) — procedura realizacji zmian i definicja ukończenia.
- [DEVELOPMENT.md](DEVELOPMENT.md) — budowanie.
- [TESTING.md](TESTING.md) — testy na urządzeniu.
- [SECURITY.md](SECURITY.md) — założenia bezpieczeństwa.

## Historia wpisu

- **2026-11-05:** formalne otwarcie nowego etapu pracy, ustalenie zasad współpracy, bez modyfikacji kodu produkcyjnego.
