# Bezpieczeństwo

## Założenia

Projekt jest przeznaczony dla zaufanej domowej sieci LAN. Nie wystawia portu do Internetu i nie zapewnia kont użytkowników ani TLS.

## Zastosowane zabezpieczenia

- QR zawiera kryptograficznie losowy token i wygasa po 120 sekundach;
- token QR jest jednorazowy;
- token urządzenia ma 32 losowe bajty, a na streamerze przechowywany jest wyłącznie jego SHA-256;
- ciasteczko parowania jest `HttpOnly` i `SameSite=Strict`;
- backend udostępnia dane wyłącznie aplikacji podpisanej tym samym kluczem;
- serwer odmawia zapisu, jeśli dysk jest odłączony, tylko do odczytu lub ma za mało wolnego miejsca;
- częściowo utworzony dokument jest usuwany po błędzie kopiowania.

## Ograniczenia

- Transfer jest HTTP, nie HTTPS. Osoba mająca dostęp do tej samej sieci może potencjalnie podsłuchać ruch lub token, dlatego nie używaj sieci gościnnej ani publicznej i nie przekierowuj portu `8789` na routerze.
- Token jest związany z przeglądarką, nie z fizycznym telefonem. Usunięcie danych przeglądarki wymaga ponownego QR.
- Nie ma jeszcze limitu liczby sparowanych urządzeń ani panelu cofania dostępu dla pojedynczego urządzenia.

## Zalecenia

- używaj WPA2/WPA3 i silnego hasła Wi-Fi;
- nie konfiguruj przekierowania portów do streamera;
- po zgubieniu telefonu wyczyść dane aplikacji Panel NAS i sparuj urządzenia ponownie;
- rozważ dodanie TLS, nazw urządzeń i widoku unieważniania tokenów przed udostępnieniem rozwiązania poza domową siecią.
