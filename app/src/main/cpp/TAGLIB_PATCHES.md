# Changes to the bundled TagLib / 同梱TagLibへの変更点

Source: TagLib v2.3.2 (https://github.com/taglib/taglib), licensed under LGPL-2.1 / MPL-1.1.
The directories `tests`, `examples`, `bench`, `doc` and `bindings` were removed. All other files are unchanged except for the two changes below.
Each change is marked with `[musicapp patch]` in the source.

同梱元：TagLib v2.3.2（LGPL-2.1 / MPL-1.1）。`tests`・`examples`・`bench`・`doc`・`bindings` を削除した以外は、次の2か所だけを変更しています。変更箇所にはソース中で `[musicapp patch]` と記しています。

## 1. MP4: merge repeated atoms into multiple values / 同名アトムの繰り返しを複数値として結合

- File: `taglib/taglib/mp4/mp4tag.cpp` (`MP4::Tag::addItem`)
- Why: MusicBee stores multiple values in M4A files (Artist, Album Artist, Composer, Artist Sort, …) as repeated atoms with the same name (for example three `©ART` atoms). The original TagLib discards every duplicate after the first as a "duplicate atom", so only the first artist could be read.
- Change: when the existing item and the new item are both string lists, the values are appended in order. Duplicates of other types are still ignored as before.
- 理由：MusicBeeはM4Aの複数値を同名アトムの繰り返しで保存する（例：`©ART` が3個）。元のTagLibは2個目以降を捨てるため、先頭の1人しか読めない。
- 変更：既存項目と新しい項目がともに文字列リストのとき、出現順に値を連結する。それ以外の型の重複は従来どおり無視する。

## 2. MP4: sample rate of 88.2/96 kHz AAC / 88.2・96kHz の AAC のサンプリング周波数

- File: `taglib/taglib/mp4/mp4properties.cpp` (`MP4::Properties::read`)
- Why: the sample-rate field of the AAC `AudioSampleEntry` is 16 bits wide, so rates of 65536 Hz and above are truncated (96000 Hz is reported as 30464 Hz).
- Change: when the reported rate equals the media time scale (`mdhd`) truncated to 16 bits, and the time scale is between 65536 and 768000, the time scale is used as the sample rate.
- 理由：AACの `AudioSampleEntry` の周波数欄は16ビットのため、65536Hz以上は切り捨てられる（96000Hz が 30464Hz と報告される）。
- 変更：報告値がメディアのタイムスケール（`mdhd`）を16ビットに切り詰めた値と一致し、タイムスケールが65536〜768000のときは、タイムスケールを周波数として使う。
