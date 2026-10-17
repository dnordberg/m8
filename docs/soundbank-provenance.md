# Soundbank provenance and validation


13 real recorded one-shots: six acoustic drum kit pieces and seven fingered electric bass notes. Sources are Karoryfer Samples Big Rusty Drums and Black And Blue Basses. Bass recordings: Drogomir Smolken (credited in the upstream user guide). Drum library credited to Karoryfer Samples; no unsupported individual recording credit is inferred.

All audio is CC0-1.0 (public-domain dedication and fallback license); see LICENSE-CC0-1.0.txt. Redistribution, modification and commercial use are permitted. No Marie Ork material is included. Attribution is supplied for provenance, not as an additional license condition. No endorsement implied.

Format: 44100 Hz, mono, signed PCM16 WAV; peak headroom 6 dB; loop=false. Manifest includes immutable source URLs, original/final SHA-256, Git blob identity, processing bounds, sizes, and measured signal/pitch statistics. provenance/ contains publisher license statements, both source licenses, extracted source user-guide text and bass mapping evidence.

Bass rootMidi is SOUNDING MIDI: 24, 28, 31, 36, 40, 43, 48. The upstream filenames/SFZ use notes one octave above sounding pitch (e.g. source c3 is sounding C2/MIDI36); the raw samples have not been transposed. Names deliberately use MIDI integers to avoid octave-label ambiguity. Natural tuning deviations are preserved. Drum roots are playback reference/GM-style triggers, not claims about a drum's fundamental.

This is a compact single-velocity/single-round-robin selection, not the full multi-mic libraries. Long open-hat/cymbal decays are retained. Bass source files already end at five seconds; a 50 ms ending fade prevents a discontinuity without extending or synthesizing sustain. Playback integration should select the closest bass root and apply its own note-off envelope; no loops are provided.

See docs/soundbank-provenance.md in the source repository for the full audit table. Objective decode/signal checks were run; no human listening review or Android playback is claimed by this asset preparation task.

## Publisher evidence

Retrieved 2026-10-16T00:21:07.558541+00:00.

Publisher statement at https://shop.karoryfer.com/pages/free-samples:

> All our free sample libraries are under a Creative Commons Zero license. Older downloads may include a less permissive license, but we changed that and now they are all CC0. Only Marie Ork has a different license.

Both selected repositories additionally contain the complete CC0 1.0 Universal legal text, bundled here. The official individual product pages link to these same sfzinstruments repositories. Individual pinned-commit audio files were downloaded directly, rather than multi-gigabyte release ZIPs. Every response was verified against its Git tree blob SHA-1, then hashed with SHA-256; no Git LFS pointers or fabricated recordings were accepted.

## Processing

FFmpeg float mono 44100 Hz decode; relative -66 dB silence trim with 2 ms pre-roll and 100 ms tail padding; 0.5 ms linear attack fade, 50 ms linear release fade; per-sample -6 dBFS peak normalization; rounded PCM16. No synthesis, pitch shifting, time stretching, EQ, limiting, or looping.

The source FLAC drum files are lossless acoustic recordings; bass sources are WAV recordings. Exact decoded trim bounds, gain factors, original and final sizes are in manifest.json. All sources fully decoded successfully. Final files were independently decoded using Python wave and FFmpeg; all samples are finite, non-silent, with zero endpoint samples and no full-scale integer samples. Bass roots were checked against recorded harmonic spectra and autocorrelation, rather than blindly copying the octave-transposing upstream key map. The root correction is metadata only.

## Sources and final artifacts

| ID | Source path | Original bytes / SHA-256 | Final bytes / SHA-256 | Seconds | RMS dBFS |
|---|---|---|---|---:|---:|
| acoustic_kick | [Samples/kick_24/kick/kick/k_vl11_rr1.flac](https://raw.githubusercontent.com/sfzinstruments/karoryfer.big-rusty-drums/f07ce00df34a46b6b08375be56fe116cf15782bc/Samples/kick_24/kick/kick/k_vl11_rr1.flac) | 40893 / `64eccf96a6a028c856f3b34cbbd906a4c311ff2402dccf71f540f0c7c67b4f92` | 100000 / `483a6ac3338c540881e7ebd461e68518402ee71209441aac459fa4a420266a23` | 1.1333 | -27.84 |
| acoustic_snare | [Samples/snare_14/center/top/sn_center_vl8_rr1.flac](https://raw.githubusercontent.com/sfzinstruments/karoryfer.big-rusty-drums/f07ce00df34a46b6b08375be56fe116cf15782bc/Samples/snare_14/center/top/sn_center_vl8_rr1.flac) | 45943 / `9b9996fd46e2d9c208a1080f53311db324423641a28c8657292242cbf99cce57` | 123934 / `c422862db5dcdd064541756b84757f178bc08e2791a286c45a9253599f6f577f` | 1.4046 | -33.20 |
| acoustic_hat_closed | [Samples/hihat_14/cl/cl/ht_cl_vl5_rr1.flac](https://raw.githubusercontent.com/sfzinstruments/karoryfer.big-rusty-drums/f07ce00df34a46b6b08375be56fe116cf15782bc/Samples/hihat_14/cl/cl/ht_cl_vl5_rr1.flac) | 41474 / `87b23f751a0437ddab419489e2cbd14a79b7955d29034af513a77de96770efd4` | 95126 / `a5fe5eac191f6c58a87135722cfb922ff9041fd1d5e7b767a0ba18fbb7bf572a` | 1.0780 | -33.60 |
| acoustic_hat_open | [Samples/hihat_14/open/cl/ht_open_vl5_rr1.flac](https://raw.githubusercontent.com/sfzinstruments/karoryfer.big-rusty-drums/f07ce00df34a46b6b08375be56fe116cf15782bc/Samples/hihat_14/open/cl/ht_open_vl5_rr1.flac) | 224519 / `38f3565f2c23803205e7483a3e992f33e041dd3af2e340e38b1486cc3663f856` | 874262 / `de2699a29a16048b48bd671ceb5c17df9b67e8156a7d8d24241c5e203284594b` | 9.9118 | -35.14 |
| acoustic_tom | [Samples/tom_14/center/cl/t14_vl5_rr1.flac](https://raw.githubusercontent.com/sfzinstruments/karoryfer.big-rusty-drums/f07ce00df34a46b6b08375be56fe116cf15782bc/Samples/tom_14/center/cl/t14_vl5_rr1.flac) | 54846 / `7aabe82391ff96cda9203cee81fc72fa1665ca225ddfab8bc05abd883c18c727` | 179748 / `1e7c204d10ebc5310273d8b00dbb0fdb59bf2431a1f977788578a978d8f1f03a` | 2.0375 | -28.89 |
| acoustic_cymbal | [Samples/crash_17/cr/cl/cr_vl4_rr1.flac](https://raw.githubusercontent.com/sfzinstruments/karoryfer.big-rusty-drums/f07ce00df34a46b6b08375be56fe116cf15782bc/Samples/crash_17/cr/cl/cr_vl4_rr1.flac) | 317377 / `79afe7dc4a68a733d89207184f2e64e9815fb45ce82901a8c7e883562e584a9d` | 979622 / `3dcd469ae2112dbd09808f0e6f5641713522153b34be53182ebf1fb4959b1862` | 11.1063 | -36.27 |
| acoustic_bass_24 | [Samples/darkblack/reg/darkblack_c2_f_rr1.wav](https://raw.githubusercontent.com/sfzinstruments/karoryfer.black-and-blue-basses/6e7d674cdb41be7a54dbccb15472401ad01099b9/Samples/darkblack/reg/darkblack_c2_f_rr1.wav) | 661580 / `55c124de81e46562177d39d8baf08e2db1f4454a4eae0ffa5921b8dfa7eaa3b0` | 441044 / `a078db1469f6b2ee429a7bed7f8606a3a399418b0836cb65b0c5e73b31b6ee0c` | 5.0000 | -22.46 |
| acoustic_bass_28 | [Samples/darkblack/reg/darkblack_e2_f_rr1.wav](https://raw.githubusercontent.com/sfzinstruments/karoryfer.black-and-blue-basses/6e7d674cdb41be7a54dbccb15472401ad01099b9/Samples/darkblack/reg/darkblack_e2_f_rr1.wav) | 661580 / `5546aeae5697f721422aae2a9badbd9a8ce2f2fb724003c4c2ff509a1c35e602` | 441044 / `0976a16110bc1376999bde6100f098050de9cc8b7ff8f16e0d05add1baa799b8` | 5.0000 | -23.18 |
| acoustic_bass_31 | [Samples/darkblack/reg/darkblack_g2_f_rr1.wav](https://raw.githubusercontent.com/sfzinstruments/karoryfer.black-and-blue-basses/6e7d674cdb41be7a54dbccb15472401ad01099b9/Samples/darkblack/reg/darkblack_g2_f_rr1.wav) | 661580 / `ba6d831c4a61edbec2c9dc1a1232a0cf0bf6f3e1ecc04c883fb8a6aa004afff2` | 441044 / `8fdfe6ff4926214780e7a6e60fae49c00a38b5f92ed2ce9421764b3b1cd27a28` | 5.0000 | -25.64 |
| acoustic_bass_36 | [Samples/darkblack/reg/darkblack_c3_f_rr1.wav](https://raw.githubusercontent.com/sfzinstruments/karoryfer.black-and-blue-basses/6e7d674cdb41be7a54dbccb15472401ad01099b9/Samples/darkblack/reg/darkblack_c3_f_rr1.wav) | 661580 / `2475b600fae4653310a7c9da3313c333cb9733a5b508e3b8708d0bd78f839c22` | 441044 / `aeb54b4f4f0b564f2f58c90e353e0183fcb236168b90ddafa92a0bf130f60462` | 5.0000 | -28.54 |
| acoustic_bass_40 | [Samples/darkblack/reg/darkblack_e3_f_rr1.wav](https://raw.githubusercontent.com/sfzinstruments/karoryfer.black-and-blue-basses/6e7d674cdb41be7a54dbccb15472401ad01099b9/Samples/darkblack/reg/darkblack_e3_f_rr1.wav) | 661580 / `9cf98829175d9a5c474210f8bc36f5f68f912263a333a77521a4bc1a40168d14` | 441044 / `4bf72b88529afabf86c8e9682e7c1776a78112525527180fa5e72ae78204c87d` | 5.0000 | -29.86 |
| acoustic_bass_43 | [Samples/darkblack/reg/darkblack_g3_f_rr1.wav](https://raw.githubusercontent.com/sfzinstruments/karoryfer.black-and-blue-basses/6e7d674cdb41be7a54dbccb15472401ad01099b9/Samples/darkblack/reg/darkblack_g3_f_rr1.wav) | 661580 / `018376f19d3c68ad4530eebcc0f54dce79dfdd877532abe16fd84472d0c93462` | 441044 / `a763f162837bba7f17731e93110fd89ac743f95b45a75eb93c20c3bbd34f91ed` | 5.0000 | -28.42 |
| acoustic_bass_48 | [Samples/darkblack/reg/darkblack_c4_f_rr1.wav](https://raw.githubusercontent.com/sfzinstruments/karoryfer.black-and-blue-basses/6e7d674cdb41be7a54dbccb15472401ad01099b9/Samples/darkblack/reg/darkblack_c4_f_rr1.wav) | 661580 / `322f7b28fad2cac71e861a5954f097e219db467525fa386dde7d3ae67ccece7a` | 441044 / `85ba7e8728ece9b37f7d94a5508f40e466f33f27777a45c5c6954c16fa060938` | 5.0000 | -23.27 |

## Evidence snapshots

Text extracted from upstream PDF guides is labelled as an extraction; hashes below refer to the bundled text, not to PDF bytes.

- [provenance/free-samples.txt](https://shop.karoryfer.com/pages/free-samples): 3620 bytes; SHA-256 `939f8106fb883f671d06f90ba360637e0b295f5a460310efc21ac90156eec6dd`.
- [provenance/free-big-rusty-drums.txt](https://shop.karoryfer.com/pages/free-big-rusty-drums): 2936 bytes; SHA-256 `c3ad6c73119a78bd3a50438f8aaf77b95655468f6d0cb04db82768a589234dbf`.
- [provenance/free-black-and-blue-basses.txt](https://shop.karoryfer.com/pages/free-black-and-blue-basses): 391 bytes; SHA-256 `4c730a0ba251014d4cea657dd0ebd9c1e0dc12b4469af5e7e51d2fef784aba66`.
- [provenance/drums-karoryfer.big-rusty-drums-LICENSE](https://raw.githubusercontent.com/sfzinstruments/karoryfer.big-rusty-drums/f07ce00df34a46b6b08375be56fe116cf15782bc/LICENSE): 7048 bytes; SHA-256 `a2010f343487d3f7618affe54f789f5487602331c0a8d03f49e9a7c547cf0499`.
- [provenance/drums-drum-guide.txt](https://raw.githubusercontent.com/sfzinstruments/karoryfer.big-rusty-drums/f07ce00df34a46b6b08375be56fe116cf15782bc/Big_Rusty_Drums_user_guide_1.000.pdf): 28613 bytes; SHA-256 `2416691fedae589761675c3e0d6552a0b518753bbbde7f851c888c73e6e6dbbf`.
- [provenance/bass-bass-license.txt](https://raw.githubusercontent.com/sfzinstruments/karoryfer.black-and-blue-basses/6e7d674cdb41be7a54dbccb15472401ad01099b9/license): 7047 bytes; SHA-256 `6d489af6292662d9e36d34ce49423784984a5f6e41d7b58f49b01264df59fa03`.
- [provenance/bass-bass-guide.txt](https://raw.githubusercontent.com/sfzinstruments/karoryfer.black-and-blue-basses/6e7d674cdb41be7a54dbccb15472401ad01099b9/Black_And_Blue_Basses_user_guide.pdf): 9836 bytes; SHA-256 `d7153a0837a4eed6b728d6cc0634b141b71ca7e4bbbfaeb4631df436f6b21d0a`.
- [provenance/bass-karoryfer.black-and-blue-basses-Programs_maps_darkblack_reg_f_map.sfz](https://raw.githubusercontent.com/sfzinstruments/karoryfer.black-and-blue-basses/6e7d674cdb41be7a54dbccb15472401ad01099b9/Programs/maps/darkblack_reg_f_map.sfz): 19018 bytes; SHA-256 `d32ea260b627f6ccfe29a6325140e8f3e7c2b75c50e2e433c4f242d9154c8ce3`.

## Result and limitations

13 WAVs, 5,440,000 bytes of audio. All peak at approximately -6.0005 dBFS. No licensing/download blockers remain. No Kotlin/build files modified; no APK build or audible device review performed in this asset-only workstream. Single-hit selection intentionally omits velocity layers, round robins, additional microphones and SFZ effects. Individual source bass tuning is retained, not corrected to mathematically perfect equal temperament.
