# AzsaPlayer

[日本語](README.ja.md)

A music library player for Android that interprets your tags the same way [MusicBee](https://getmusicbee.com/) does: multiple artists, sort tags and ReplayGain.
It was built for the HiBy R4 digital audio player (Android 12), but runs on any Android 12+ device.

The app is designed to be minimal by default and deeply customizable from the settings.

## Features

- **Multiple artists, the MusicBee way** — values stored as real multi-values (ID3v2 NULL-separated, repeated Vorbis comments, repeated MP4 atoms) and values separated by `;` are split into separate artists. `AC/DC` or `Earth, Wind & Fire` are never split.
- **Sort tags** — `ARTISTSORT`, `ALBUMARTISTSORT`, `ALBUMSORT`, `TITLESORT`, `COMPOSERSORT` control the order; the display name stays as tagged. Sorting uses the ICU collator (Latin → kana → kanji, like MusicBee on Windows).
- **ReplayGain** — track / album / auto, preamp, clipping prevention. Applied digitally, sample-accurately at track boundaries during gapless playback. No system volume or audio effects are used.
- **Audio quality** — decoded to 32-bit float and output as float; nothing but ReplayGain is applied. AAC DRC and loudness normalization in the decoder are disabled.
- **Gapless playback**, queue editing, shuffle by track or album, repeat, resume after restart.
- **Customizable home** — choose and reorder tabs (Album Artist, Artist, Album, Track, Composer, Genre, Year, Decade, Folder, Playlist, Recently Added, Album Flow, or any custom tag), the levels down to tracks, list or grid, artwork, and two-line templates such as `<Artist> — <Year>`.
- **Album Flow** — browse albums by their covers, with a virtual scroll wheel.
- **Home screen widgets** — standard playback controls, a record player whose tonearm drops onto the record (with the album art as its label) while playing, and a large album art widget.
- **Choice of app icons** — the default icon, plus an AZ monogram, a record, and a wave whose bars draw an "A" (Settings > Display > App icon).
- **Search** that ignores hiragana/katakana, full/half width and case, and also matches sort tags.
- Playlists, M3U/M3U8 import with Windows path replacement (for playlists exported from MusicBee).
- English and Japanese.

## Supported formats

Library: FLAC, MP3, M4A (AAC/ALAC), Ogg Vorbis, Opus, WAV, AIFF, APE, WavPack, DSF/DFF, Musepack (tags are read with TagLib).
Playback: formats that Android's standard decoders can play (FLAC, MP3, AAC, Vorbis, Opus, WAV, and ALAC on devices that support it). Other formats are listed and marked as unplayable.

## Install

1. Download the latest `AzsaPlayer-x.y.z.apk` from [Releases](../../releases).
2. Open it on your device and allow installation from this source when asked.
3. On first launch, choose how much access to allow:
   - **Read music files only (recommended)** — reads common audio files and album art. Formats Android does not treat as audio (APE, DSD, WavPack, …) and M3U files may not appear.
   - **Access to all files** — reads every format. You can change this later in *Settings > Library*.
4. In *Settings > Library*, add your music folder and press **Scan**.

The app never writes to or deletes your music files, has no internet permission, and collects no data.

### Tips for MusicBee users

- Sort values shown in **gray** in MusicBee's tag editor are MusicBee's own custom sort values; they are not written to the files. Type the value into the *Sort* field and save so that it is stored in the file (`ARTISTSORT` etc.).
- To import playlists, export them as M3U from MusicBee and add a path replacement rule such as `D:\Music\` → `/storage/XXXX-XXXX/Music/`.

## Build from source

Requirements: Android Studio (with its bundled JDK 17+), Android SDK 37, NDK 30.0.16248370, CMake 4.1.2.

```bash
./gradlew assembleDebug
```

Tests:

```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

The specification (in Japanese) is in [SPEC.md](SPEC.md).

## License

AzsaPlayer is licensed under the [Apache License 2.0](LICENSE).

It bundles [TagLib](https://taglib.org/) 2.3.2 (LGPL-2.1 / MPL-1.1) with two small modifications, described in [TAGLIB_PATCHES.md](app/src/main/cpp/TAGLIB_PATCHES.md); the modified source is in `app/src/main/cpp/taglib`. The licenses of all bundled software are shown in the app under *Settings > Advanced > Open source licenses*.

## Disclaimer

AzsaPlayer is an independent project. It is not affiliated with, endorsed by, or sponsored by any company, product or trademark holder mentioned in this repository. Names such as MusicBee and HiBy are used only to describe compatibility and intended use; all product names and trademarks belong to their respective owners.
