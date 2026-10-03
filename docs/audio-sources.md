# Bundled soundscapes

Hush bundles three edited field recordings in `app/src/main/res/raw/`. Playback is offline and repeats until a preview is stopped or the session ends. The recordings contain no added music. Rain includes occasional distant thunder.

## Sources and license

All three source pages identify their sound as [Creative Commons Zero 1.0](https://creativecommons.org/publicdomain/zero/1.0/), allowing modification and redistribution, including commercial use. Credits are retained here for provenance even though CC0 does not require attribution. Source pages and licenses were checked on October 2, 2026.

| File | Recording and author | Bundled duration | Size |
| --- | --- | --- | --- |
| `ocean.ogg` | [Calm ocean waves — SamsterBirdies, Freesound #578524](https://freesound.org/people/SamsterBirdies/sounds/578524/) | 2:56.710 | 2,388,582 bytes |
| `rain.ogg` | [Rain and Thunder.mp3 — pmstrain, Freesound #348805](https://freesound.org/people/pmstrain/sounds/348805/) | 2:57.000 | 1,986,215 bytes |
| `fireplace.ogg` | [fireplace — martats, Freesound #138018](https://freesound.org/people/martats/sounds/138018/) | 2:47.696 | 2,842,866 bytes |

Total bundled audio is approximately 6.9 MiB. The high-quality MP3 previews of the licensed recordings were used as inputs, rather than the original WAV/FLAC downloads:

- Ocean: https://cdn.freesound.org/previews/578/578524_5487341-hq.mp3
- Rain: https://cdn.freesound.org/previews/348/348805_53958-hq.mp3
- Fireplace: https://cdn.freesound.org/previews/138/138018_464357-hq.mp3

## Editing

The audio was decoded to signed 16-bit stereo PCM using FFmpeg. Rain uses the first 180 seconds; Ocean and Fireplace use their full decoded recordings. For each clip, the first three seconds are removed from the beginning and blended into the final three seconds with an equal-power crossfade: `tail * cos(t * pi / 2) + head * sin(t * pi / 2)`, where `t` advances from 0 to 1. The next repetition therefore continues from the end of the blended head into the original fourth second. This avoids inserting a silent fade between repetitions.

A 40 Hz high-pass filter reduces rumble. Before its crossfade, Fireplace also uses FFmpeg `alimiter=limit=0.1:level=false:attack=5:release=80:latency=true` to reduce sudden loud crackles. FFmpeg loudness analysis then determines a constant gain targeting -24 LUFS while keeping the pre-encode true peak at or below -4 dBTP. The peak constraint leaves Fireplace quieter (approximately -28.5 LUFS); Ocean and Rain are approximately -24 LUFS. The final files are stereo, 44.1 kHz Ogg Vorbis at quality 4, with inherited metadata removed. Audio editing and downloading are build-independent; the resulting Ogg files are checked in, and no authoring tools are needed to build or run Hush. Temporary authoring files stay outside the checkout; Gradle outputs remain under the ignored `app/build/` directory.

## Playback and compatibility

`AmbientAudioEngine` uses Android `MediaPlayer` to prepare asynchronously and loop the packaged resource. The existing service remains the owner of session audio; the activity owns sheet previews. Each owner releases its player on stop. Sessions and playback commands support only Rain, Ocean, and Fireplace.

## Validation

The focused device test checks all three packaged recordings for successful decoding, duration of at least two minutes, pause/resume position, and continued playback after seeking across the repeat boundary. It plays at zero volume and opens no activity:

```powershell
./gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.blue.hush.SoundscapePlaybackTest"
```

The broader existing screen tests cover soundscape selection and preview dismissal. Manually listen to each preview and its loop boundary with headphones on a target device, then check session volume, pause/resume, and playback while locked. Automated decoding and loop checks cannot establish perceived recording quality or inaudibility of the loop seam.

Session start/end cues are original generated PCM WAV chimes (44.1 kHz mono, 2.2 seconds), bundled as session_start.wav and session_end.wav. Start uses two rising notes; completion uses three descending notes. They play once at media-stream volume with fixed player gain, independent of ambient-track volume; system mute still applies. Playback does not restart on pause/resume or replay. Perceived volume and clarity require listening on the target device.
