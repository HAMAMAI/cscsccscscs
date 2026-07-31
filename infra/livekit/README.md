# Сервер аудиозвонков Такт

Один узел LiveKit с TURN/UDP на `443`, TURN/TLS на `5349` и изолированным HTTPS Caddy на `8443`. Клиенты обращаются напрямую к этому HTTPS-адресу, не затрагивая независимый HAProxy на TCP `443`.

Нужны два DNS-имени, направленные A-записями на VPS:

- `call.example.com` — WebSocket/API LiveKit; в `CALL_DOMAIN` укажите `call.example.com:8443`. Клиенты используют `https://call.example.com:8443` и `wss://call.example.com:8443` напрямую;
- `turn.example.com` — TURN/TLS.

`bootstrap-vps.sh` устанавливает Docker Compose, `envsubst` и Certbot, получает сертификаты для обоих имён без email и запускает сервисы. Открытые входящие порты:

- TCP `80`, `8443`, `5349`, `7881`;
- UDP `443`, `50000:50100`;
- SSH только с доверенных адресов, если это возможно.

Проверенный Android APK раздаётся Caddy по `/downloads/takt-android.apk` с поддержкой докачки HTTP Range. Для браузеров, блокирующих прямые APK, там же доступен ZIP-архив `/downloads/takt-android.zip`.

Секреты хранятся только в `infra/livekit/.env` на сервере и в зашифрованных секретах GitHub Actions. Файл `.env` и сгенерированные конфигурации не коммитятся.

Для адреса DuckDNS добавьте токен аккаунта как секрет окружения GitHub Actions `DUCKDNS_TOKEN`. Сертификат выпускается через DNS-01, поэтому порты `80` и `443` и другие службы VPS не останавливаются.

Первый запуск на чистом Ubuntu VPS:

```bash
cp .env.example .env
# заполнить .env
chmod +x bootstrap-vps.sh deploy.sh check.sh
sudo ./bootstrap-vps.sh
```

Повторное обновление конфигурации выполняется через `./deploy.sh`, проверка — через `./check.sh`. Certbot автоматически обновляет сертификаты и перезапускает медиасервер.
