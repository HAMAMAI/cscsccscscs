# Такт

Аудио‑ориентированный мессенджер для веба и Android: вход по инвайт‑ссылке без Gmail/email, сообщения в реальном времени, фото, файлы, голосовые и WebRTC‑звонки без видео.

## Стек

- Next.js + TypeScript
- Supabase Postgres, RPC и Realtime Broadcast/Presence
- LiveKit/WebRTC audio-only с TURN/UDP и TURN/TLS
- Полностью нативный Android-клиент на Kotlin и Jetpack Compose
- Vercel

## Запуск

1. Примените SQL из `supabase/migrations` к проекту Supabase.
2. Скопируйте `.env.example` в `.env.local` и заполните URL и publishable key.
3. Выполните `npm install && npm run dev`.

## Android

Нативный проект находится в `native-android/`. Он не использует WebView и включает комнаты по ссылке, чат, фото, файлы, голосовые сообщения и групповые аудиозвонки без разрешения камеры. GitHub Actions собирает устанавливаемый `takt-android.apk` при каждом изменении Android-кода.

```bash
cd native-android
./gradlew :app:assembleDebug
```

Инфраструктура звонков находится в `infra/livekit/`. LiveKit API secret хранится только на VPS и в зашифрованном секрете GitHub Actions; Android получает короткоживущий токен после проверки комнатного ключа через `/api/livekit-token`.

Доступ к данным и бинарным вложениям закрыт RLS и отозван у клиентских ролей. Клиенты работают только через RPC-функции, которые проверяют комнатный токен. Видео не запрашивается и не передаётся; Android-манифест не содержит разрешения камеры.
