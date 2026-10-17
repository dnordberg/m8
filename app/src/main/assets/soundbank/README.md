# M8 recorded instrument soundbank

13 real recorded one-shots: six acoustic drum kit pieces and seven fingered electric bass notes. Sources are Karoryfer Samples Big Rusty Drums and Black And Blue Basses. Bass recordings: Drogomir Smolken (credited in the upstream user guide). Drum library credited to Karoryfer Samples; no unsupported individual recording credit is inferred.

All audio is CC0-1.0 (public-domain dedication and fallback license); see LICENSE-CC0-1.0.txt. Redistribution, modification and commercial use are permitted. No Marie Ork material is included. Attribution is supplied for provenance, not as an additional license condition. No endorsement implied.

Format: 44100 Hz, mono, signed PCM16 WAV; peak headroom 6 dB; loop=false. Manifest includes immutable source URLs, original/final SHA-256, Git blob identity, processing bounds, sizes, and measured signal/pitch statistics. provenance/ contains publisher license statements, both source licenses, extracted source user-guide text and bass mapping evidence.

Bass rootMidi is SOUNDING MIDI: 24, 28, 31, 36, 40, 43, 48. The upstream filenames/SFZ use notes one octave above sounding pitch (e.g. source c3 is sounding C2/MIDI36); the raw samples have not been transposed. Names deliberately use MIDI integers to avoid octave-label ambiguity. Natural tuning deviations are preserved. Drum roots are playback reference/GM-style triggers, not claims about a drum's fundamental.

This is a compact single-velocity/single-round-robin selection, not the full multi-mic libraries. Long open-hat/cymbal decays are retained. Bass source files already end at five seconds; a 50 ms ending fade prevents a discontinuity without extending or synthesizing sustain. Playback integration should select the closest bass root and apply its own note-off envelope; no loops are provided.

See docs/soundbank-provenance.md in the source repository for the full audit table. Objective decode/signal checks were run; no human listening review or Android playback is claimed by this asset preparation task.
