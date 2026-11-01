# GOAT Recorder

A native Android voice recorder, built for Paul. Record something you must
remember, even with no cell service at all.

## Offline first

This is the core reason the app exists. Paul drives Libby to Kalispell and
Libby to Idaho through dead zones with no cell service, and needs to record
something he must remember when he cannot reach any internet service.

Everything in phase 1 works with zero connectivity:

- Recording, with the screen off and in the background
- Home-screen widget, one tap to start, one tap to stop
- Quick Settings tile, same one-tap start and stop
- Playback of saved recordings
- Local storage of recordings and settings

There is no INTERNET permission in the manifest on purpose. Nothing in
phase 1 can reach the network, because nothing in phase 1 needs it.

## Phase 1, what is built

- Gradle Kotlin project, minSdk 26, targetSdk 34, one app module.
- Foreground recording service (`RecordingService`): MediaRecorder, M4A
  output, 128 kbps AAC at 44.1 kHz. A new file starts every 15 minutes,
  so a crash or a dead battery never loses more than one chunk of a
  2-hour recording. Chunk files for one session are grouped back into a
  single recording in the list. A partial wake lock keeps recording going
  with the screen off. A Bluetooth headset mic is used when one is
  connected, through the system audio source and Bluetooth SCO.
  A persistent notification carries a Stop button.
- Home-screen widget (`VoiceNotesWidget`): one big button. Tap to start,
  tap again to stop. It works without opening the app.
- Quick Settings tile (`RecordingTileService`): tap to start or stop,
  shows an active state while recording.
- Main screen (`MainActivity`): plain list of recordings with name, date,
  and duration. Tap to play back (chunk files play in order). Rename and
  delete on each row. Big tap targets throughout.
- Settings screen (`SettingsActivity`): provider list, add, edit, delete.
  A provider is a name plus a base URL plus an API key plus a model name,
  the same pattern Kai and RPClient use. Presets: Local (on-device),
  OpenRouter, OpenAI, DeepSeek, Custom. Local is the offline path: the
  model runs on the phone itself (the LiteRT/Gemma pattern Kai uses), so
  it has no URL and no API key. Providers are stored in SharedPreferences
  as JSON. No network calls are made.

## Offline queue for transcription and AI jobs

Recording never depends on a connection or a provider. The app always
records locally first, no matter what. When a recording stops, a
transcription job is queued (`TranscriptionJob`, stored in
SharedPreferences as JSON, so it survives app restarts and reboots).

- If the selected provider cannot be reached, the job simply waits.
  The recording is safe on the phone, and the list shows
  "Transcription pending" on that recording.
- When connectivity returns, WorkManager fires `JobQueueWorker`
  (network constraint: connected) and phase 2 sends the queued jobs to
  whichever provider is currently selected, whether that is OpenRouter
  or the PC. Both count as online providers from the phone's point of
  view.
- The provider is deliberately NOT fixed on the job. A job with no
  provider set means "use the provider selected when the job runs", so
  there is nothing to remember to switch before driving into a dead
  zone.
- The Local (on-device) provider needs no connection at all, so phase 2
  can run its jobs straight away instead of waiting on the worker.

Phase 1 has the queue data model, the persistent repository, the
WorkManager trigger, and the pending badge. The worker itself only
returns success for now. Sending jobs to a provider is phase 2.

## Building

No Android SDK is installed on the build machine used to write this code,
so the APK has not been built locally yet. Push this folder to a GitHub
repo and the workflow in `.github/workflows/build-apk-debug.yml` builds
a debug APK on every push and uploads it as an artifact. To build
locally, install JDK 17 and the Android SDK (API 34, build tools
34.0.0), then run `gradle assembleDebug` with Gradle 8.7.

## Phase 2, stubbed, not built

- Offline transcription of recordings, chunked, on the phone.
- Offline AI on the phone (LiteRT/Gemma) for summaries and action items,
  using the Local provider settings from phase 1.
- Cloud backends wired up: OpenRouter, OpenAI, DeepSeek, and Custom
  endpoints (localhost, Tailscale address, Ollama on a PC).
- Queue processing: `JobQueueWorker` sends pending jobs to the currently
  selected provider when connectivity returns, retries failures, and
  chains summarize and action-item jobs after transcription. A settings
  control to pick the selected provider.
- Action items pulled out of a transcript and sent to tasks or created
  as calendar events.
- Live transcription while recording, marks while recording, and telling
  two speakers apart.

Offline transcription and offline AI are phase 2, and they are the hard
part: the models are large, they have to run on a phone, and they have to
be fast enough to be useful. That is why phase 1 ships the recording
first and the AI second.
