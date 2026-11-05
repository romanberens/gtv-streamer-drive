# Panel NAS 0.3.1 — plan i kryteria odbioru UI

Baza: `feature/nas-file-manager-v1` (0.3.0). Zmiany tylko w module `nas-browser`.

## Cel
Zdjęcia i pliki mają dominować na ekranie Google TV. Panele pomocnicze można zwijać pilotem, bez utraty dostępu do transferów i kodu.

## Architektura
- `MainActivity.kt`: stan ekranu i nawigacji; `viewMode` ma wartości 0=lista, 1=siatka, 2=duże zdjęcia.
- `NasRightPanel.kt`: niezależny moduł prawego panelu. Szerokość animowana 250dp → 68dp, zawartość sekcji jako akordeony.
- `NasIcons.kt`: nowa wektorowa ikona bocznego panelu.
- `SharedPreferences("nas_ui")`: trwały zapis `view_mode`, `right_collapsed`, `phone_expanded`, `transfers_expanded`, `code_expanded`.
- `BuildConfig` nie jest wymagany: napis wersji pobieramy z menedżera pakietów, więc nie zostanie na ekranie stary numer.

## Layout
1. Na lewej stronie pozostaje menu 185dp.
2. Środkowa przeglądarka zajmuje `weight(1f)`; przełączanie przyciskiem cyklicznym: Lista → Siatka → Duże zdjęcia.
3. Prawy panel startuje domyślnie zwinięty, żeby oddać przestrzeń zdjęciom.
4. Na pasku zwiniętym: Rozwiń, Telefon, Transfery, Kod. Naciśnięcie konkretnej ikony rozwija panel i właściwą sekcję.
5. Rozwinięty panel ma osobne akordeony: Telefon → NAS, Ostatnie transfery, Kod awaryjny.
6. Kafelki w siatce normalnej: min. 160dp, podgląd 115dp; w dużej: min. 230dp, podgląd 151dp; nazwa i metadane po 1 linii.
7. Przycisk `Opcje` przeniesiony do narożnika miniatury (36dp), dostępny osobnym fokusem pilota.

## Animacje
- Szerokość prawego panelu: `animateDpAsState`, 240ms.
- Akordeony: `AnimatedVisibility` oraz `animateContentSize`, 190–220ms.
- Zmiana listy/siatki: `Crossfade`, 190ms.
- Fokus: `animateFloatAsState` skala 1.018–1.05 w zależności od elementu, czas 140–180ms.
- Compose respektuje systemową skalę czasu animacji.

## Bezpieczeństwo i kompatybilność
- Nie zmieniamy `PhoneUploadServer`, `NasStorageOps`, `NasThumbnail` ani `third_party/otgmaster`.
- Operacje plikowe 0.3.0 pozostają dostępne; usuwanie nadal wymaga potwierdzenia.
- Brak usuwania danych aplikacji (`adb install -r`).

## Testy akceptacyjne
1. Po kompilacji uruchamia się Panel NAS, wersja wyświetlana na ekranie to 0.3.1.
2. Na małym ekranie i TV 1080p kafelki nie są ucięte; przełączanie widoków działa.
3. Zwiń panel → przeglądarka wyraźnie się poszerza; rozwiń → wraca do wcześniejszej szerokości.
4. Każdy z 3 akordeonów działa niezależnie i zachowuje stan po restarcie.
5. Z paska zwiniętego można otworzyć Telefon, Transfery i Kod bez użycia myszy.
6. Miniatura ma odrębny fokus OK=otwórz i Opcje=zarządzaj.
7. Nie zmienia się funkcjonalność tworzenia folderów, rename, kopiowania/przenoszenia i parowania telefonu.
8. Test na fizycznym Google TV Streamer z dyskiem testowym przed merge do bazy.

## Status
CI sprawdza kompilację, nie potwierdza jeszcze wyglądu i działania pilota na fizycznym urządzeniu.
