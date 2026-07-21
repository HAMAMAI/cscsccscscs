# Сервер аудиозвонков Такт

Один узел LiveKit с аудио-only WebRTC, TURN/UDP на `443`, TURN/TLS на `5349` и WebSocket-сигналингом на HTTPS `443`. Встроенный token-service проверяет комнатный ключ через Supabase и выдаёт только короткоживущие токены с правом публикации микрофона.

Нужны два DNS-имени, направленные A-записями на VPS:

- `call.example.com` — WebSocket/API LiveKit;
- `turn.example.com` — TURN/TLS.

`bootstrap-vps.sh` устанавливает Docker Compose, `envsubst` и Certbot, получает сертификаты для обоих имён без email и запускает сервисы. Открытые входящие порты:

- TCP `80`, `443`, `7881`;
- UDP `443`, `50000:50100`;
- SSH только с доверенных адресов, если это возможно.

Секреты хранятся только в `infra/livekit/.env` на сервере и в переменных Vercel. Файл `.env` и сгенерированные конфигурации не коммитятся.

Первый запуск на чистом Ubuntu VPS:

```bash
cp .env.example .env
# заполнить .env
chmod +x bootstrap-vps.sh deploy.sh check.sh
sudo ./bootstrap-vps.sh
```

Повторное обновление конфигурации выполняется через `./deploy.sh`, проверка — через `./check.sh`. Certbot автоматически обновляет сертификаты и перезапускает медиасервер.

В Vercel задаются те же `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` и `LIVEKIT_WS_URL=wss://call.example.com`.
