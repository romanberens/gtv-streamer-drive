# Testowanie

## Testy wykonane na urządzeniu

| Scenariusz | Wynik |
| --- | --- |
| Wykrycie adaptera Ugreen USB | potwierdzone przez `dumpsys usb` |
| Montaż dysku NTFS | potwierdzony w panelu NAS |
| Odczyt wolnego miejsca | potwierdzone, około 2,79 TB w czasie testu |
| Zapis na dysk | potwierdzony jako `Odczyt i zapis` |
| Lista katalogów | potwierdzona przez `DocumentsProvider` |
| HTTP `GET /` | odpowiedź `200` |
| Serwer po wyjściu z aplikacji | port `8789` nadal nasłuchuje |
| QR | ekran QR wyświetlony, link odrzucony po pierwszym użyciu |
| Upload z telefonu | potwierdzony ręcznie |

## Lista regresji przed wydaniem

1. Podłącz czysty dysk NTFS i sprawdź przejście z `Niepodłączony` do gotowości.
2. Podłącz dysk z nieczystym NTFS i potwierdź odmowę zapisu z instrukcją `chkdsk /f`.
3. Wyślij plik z niesparowanego telefonu: wymagany powinien być kod.
4. Sparuj telefon QR i potwierdź brak pola kodu.
5. Otwórz ten sam QR drugi raz: powinien być nieważny.
6. Wróć na ekran główny Google TV i sprawdź port `8789`.
7. Wyślij plik większy niż dostępne miejsce: zapis ma być odrzucony bez częściowego pliku.
8. Odłącz dysk po zakończeniu transferu, podłącz ponownie i odśwież panel.
9. Otwórz plik multimedialny i sprawdź obsługę jego typu MIME przez odtwarzacz.

## Automatyzacja

Repozytorium nie ma jeszcze automatycznych testów end-to-end panelu NAS. Backend OTG Master ma własne testy, ale ich dane i wymagania sprzętowe są szersze niż scenariusz NAS. Następnym krokiem powinny być testy instrumentacyjne mostu i test HTTP serwera uploadu.
