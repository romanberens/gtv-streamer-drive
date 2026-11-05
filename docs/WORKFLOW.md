# Workflow rozwoju GTV Streamer Drive / Panel NAS

**Obowiązuje od:** 2026-11-05  
**Właściciel decyzji:** Roman Berens  
**Projekt:** Google TV Streamer / Android TV / USB NTFS / transfer LAN  
**Repozytorium:** `romanberens/gtv-streamer-drive`

## Zasada operacyjna

GitHub to źródło prawdy o kodzie; Drive to miejsce na koncepcje, dokumentację projektową i raporty testowe. Kopia z `Komputery → Mój laptop → nas-browser` nie jest automatycznie środowiskiem edycji ani synchronizacją dwukierunkową potwierdzoną testem.

```text
Zgłoszenie / pomysł
        |
        v
Analiza i zakres + kryteria akceptacji
        |
        v
Gałąź feature/*, fix/* lub chore/* od aktualnego main
        |
        v
Zmiana kodu i testy lokalne, bez zmian w main
        |
        v
Build APK + testy statyczne/jednostkowe + review diffu
        |
        v
Draft PR -> przegląd -> test na Google TV Streamer
        |
        v
Akceptacja właściciela -> merge do main
        |
        v
Tag / APK / instrukcja i archiwizacja raportu na Drive
```

## Nazewnictwo

- `feature/nas-dashboard-v2` — nowy UI.
- `fix/saf-navigation` — naprawa uprawnień/nawigacji.
- `fix/upload-auth-limits` — bezpieczeństwo uploadu.
- `chore/ci-build` — testy i pipeline.
- `chore/handover-2026-11-05` — inicjalna dokumentacja tego etapu.

Commity: `feat:`, `fix:`, `docs:`, `test:`, `chore:`. Jeden logiczny temat na PR.

## Minimalne kryteria akceptacji (Definition of Done)

1. W PR jest opis celu, zakresu i wpływu na bezpieczeństwo/USB.
2. Pliki `build/`, `.gradle/`, `local.properties`, APK i dane urządzenia nie trafiają do commita.
3. Kompilacja `gradle -c settings.gtv.gradle.kts :nas-browser:assembleDebug` zakończona powodzeniem; dla modyfikacji OTG Master także jego build.
4. Wyniki testów są udokumentowane; jeżeli testów nie uruchomiono, w PR jest to wyraźnie zaznaczone.
5. Scenariusze krytyczne: montaż NTFS, odczyt katalogów i plików, uprawnienia SAF, upload i odmowa zapisu, parowanie QR i wygaśnięcie, Back/pilot, odłączenie USB.
6. Zmiana jest sprawdzona na prawdziwym Google TV Streamer przed wydaniem; bez sprzętu PR pozostaje kandydatem do przeglądu.
7. Merge i wdrożenie wymagają świadomej decyzji właściciela projektu.

## Priorytety

| Etap | Zakres | Uznajemy za ukończony, gdy |
| --- | --- | --- |
| P0 | SAF i otwieranie plików | foldery i zewnętrzne aplikacje poprawnie otwierają URI |
| P0 | Auth, limity i bezpieczeństwo uploadu | nieautoryzowany klient nie wymusza dużych uploadów, próby kodu ograniczone |
| P1 | Stan serwera i odświeżanie UI | przesłany plik i odłączony dysk mają aktualny stan |
| P1 | Dashboard V2 / D-pad | przejrzyste ekrany z dostępną nawigacją TV |
| P2 | CI, regresja, wersjonowanie | przewidywalne buildy, raport testowy i artefakt wydania |

## Google Drive — katalog projektu

[Folder główny projektu](https://drive.google.com/drive/folders/1CXQ9x6WFa4dCxm0wwwFrKTwmxjEvnO6l) zawiera:
- `01 — Koncepcje i UI UX`
- `02 — Architektura i dokumentacja`
- `03 — Plan rozwoju`
- `04 — Testy i diagnostyka`
- `05 — Wydania i wdrożenia`

Osobno potwierdzono możliwość utworzenia katalogu kontrolnego `_workflow_2026-11-05` w zsynchronizowanym `Komputery → Mój laptop → nas-browser`. To **nie** potwierdza jeszcze automatycznego odzwierciedlenia zapisu na lokalnym dysku laptopa.

## Zakres pracy asystenta

Asystent może tworzyć i modyfikować kod przez podłączone repozytorium, przygotowywać gałęzie i Pull Requesty oraz dokumentację na Drive w ramach zleconego zakresu. Nie ma potwierdzonego dostępu do lokalnego filesystemu laptopa, nie potwierdza kompilacji ani testu sprzętowego bez ich uruchomienia. Nie publikuje wersji i nie scala do `main` bez decyzji użytkownika.
