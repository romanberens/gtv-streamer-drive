# Panel NAS 0.3.0 — menedżer plików (wersja testowa)

## Funkcje TV (pilot)
- `Nowy folder` tworzy podfolder w aktualnym katalogu.
- `Opcje` przy pliku lub folderze: zmiana nazwy, usuwanie po potwierdzeniu.
- Przy pliku można wybrać `Kopiuj do…` lub `Przenieś do…`, przejść do katalogu docelowego i użyć `Kopiuj tutaj` / `Przenieś tutaj`.
- `Miniatury` przełącza listę plików na siatkę. Miniatury obrazów i wideo dekodowane są poza wątkiem UI; jeśli odczyt się nie powiedzie, wyświetlana jest ikona.
- Opcje dostępne są na pilocie przez fokus przycisku `Opcje` obok pliku.

## Przenoszenie
Backend DocumentsProvider nie implementuje `moveDocument`. Dlatego dla **pojedynczego pliku**:
1. Tworzymy nowy plik bez nadpisania istniejącego.
2. Kopiujemy strumień źródłowy.
3. Odczytujemy zapisaną kopię ponownie i porównujemy rozmiar oraz SHA-256 z oryginałem.
4. Tylko po powodzeniu usuwamy oryginał.

Operacja może być wolna i wymaga wystarczającej ilości wolnego miejsca na drugą kopię. Jeśli usunięcie oryginału się nie uda, oba pliki pozostają na dysku. Przenoszenie folderów nie jest dostępne. Kopiowanie folderów także nie jest dostępne.

## Funkcje telefonu
- Strona lokalna `http://ADRES_STREAMERA:8789/` — responsywna, z drag & drop, wyborem kilku plików i postępem kolejki.
- Sparowana przeglądarka może przeglądać katalogi, wybierać folder docelowy, tworzyć foldery, zmieniać nazwy i usuwać pliki/katalogi.
- Zarządzanie wymaga sparowanego ciasteczka i tokenu CSRF; ścieżki i identyfikatory SAF są ograniczone do udostępnionego dysku.
- Niesparowany użytkownik może wysłać plik do katalogu głównego awaryjnym kodem, ale nie może zdalnie zarządzać plikami.
- Przesyłane pliki nie nadpisują istniejących plików o tej samej nazwie.

## Bezpieczeństwo i ograniczenia
- **Usuwanie jest trwałe** — brak kosza/undo. Sprawdzić na plikach testowych.
- Operacje zapisu zależą od uprawnień SAF i obsługi systemu NTFS.
- Panel telefonu działa po HTTP w zaufanej sieci lokalnej, nie wystawiać portu 8789 na Internet.
- Gdy kopia nie przejdzie walidacji, źródło nie jest usuwane.
- Miniatury wideo są best-effort: backend nie oferuje natywnych miniaturek, więc nie każdy kodek/plik pokaże podgląd.
- Trwałe zmiany wymagają testów integracyjnych na rzeczywistym Google TV Streamer + dysku testowym.

## Test akceptacyjny
1. Sprawdź wersję `0.3.0` w `dumpsys package pl.local.nasbrowser`.
2. Utwórz folder `TEST_NAS` i wewnątrz plik testowy; sprawdź widoczność na liście.
3. Zmień nazwę folderu, przełącz listę na siatkę i sprawdź miniaturę JPG/MP4.
4. Skopiuj mały plik JPG do innego folderu; porównaj zawartość.
5. Przenieś mały plik TXT; upewnij się, że źródło znika wyłącznie po potwierdzonym zapisie kopii.
6. Usuń wyłącznie przygotowany plik testowy po wyświetleniu potwierdzenia.
7. Otwórz stronę telefonu, sparuj, wybierz folder i wyślij dwa różne pliki; obserwuj postęp.
8. Z telefonu utwórz katalog, zmień nazwę i usuń wyłącznie plik testowy.
9. Sprawdź, że niesparowana przeglądarka nie może wyświetlać listy katalogów przez `/api/list`.

## Stan
Kod zapisany w PR, **test fizyczny jeszcze niepotwierdzony**. Nie scalać do `main` bez testów.
