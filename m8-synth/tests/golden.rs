use m8_synth::SynthEngine;
use std::collections::hash_map::DefaultHasher;
use std::hash::{Hash, Hasher};

fn render_chunks(engine: &mut SynthEngine, count: usize) -> Vec<i16> {
    let mut samples = Vec::new();
    for _ in 0..count {
        let pcm = engine.generate_chunk();
        for frame in pcm.chunks_exact(4) {
            samples.push(i16::from_le_bytes([frame[0], frame[1]]));
            samples.push(i16::from_le_bytes([frame[2], frame[3]]));
        }
    }
    samples
}

fn hash_samples(samples: &[i16]) -> u64 {
    let mut hasher = DefaultHasher::new();
    samples.hash(&mut hasher);
    hasher.finish()
}

fn rms(samples: &[i16]) -> f64 {
    let sum: f64 = samples.iter().map(|s| (*s as f64).powi(2)).sum();
    (sum / samples.len() as f64).sqrt()
}

#[test]
fn golden_saw_lead_c4_is_deterministic() {
    let render = || {
        let mut engine = SynthEngine::new();
        engine.trigger_row(&[60, 0, 0, 0, 0, 0, 0, 0], &[204, 0, 0, 0, 0, 0, 0, 0]);
        render_chunks(&mut engine, 4)
    };
    let a = render();
    let b = render();
    assert_eq!(hash_samples(&a), hash_samples(&b), "renders must be bit-identical");
    assert!(rms(&a) > 100.0, "saw lead at C4 must produce audible output");
}

#[test]
fn golden_fm_bell_e5_is_deterministic() {
    let render = || {
        let mut engine = SynthEngine::new();
        engine.trigger_row(&[0, 0, 0, 0, 76, 0, 0, 0], &[0, 0, 0, 0, 200, 0, 0, 0]);
        render_chunks(&mut engine, 4)
    };
    let a = render();
    let b = render();
    assert_eq!(hash_samples(&a), hash_samples(&b), "renders must be bit-identical");
    assert!(rms(&a) > 50.0, "FM bell at E5 must produce audible output");
}

#[test]
fn golden_silence_is_true_zero() {
    let mut engine = SynthEngine::new();
    let silence = render_chunks(&mut engine, 2);
    assert!(silence.iter().all(|s| *s == 0), "no notes triggered must produce digital zero");
}

#[test]
fn golden_silence_then_note_produces_audio() {
    let mut engine = SynthEngine::new();
    let silence = render_chunks(&mut engine, 2);
    assert!(silence.iter().all(|s| *s == 0));

    engine.trigger_row(&[48, 0, 0, 0, 0, 0, 0, 0], &[180, 0, 0, 0, 0, 0, 0, 0]);
    let audio = render_chunks(&mut engine, 2);
    assert!(audio.iter().any(|s| s.abs() > 100), "must produce audio after trigger");
}

#[test]
fn golden_note_off_decays_to_silence() {
    let mut engine = SynthEngine::new();
    engine.trigger_row(&[60, 0, 0, 0, 0, 0, 0, 0], &[204, 0, 0, 0, 0, 0, 0, 0]);
    let _ = render_chunks(&mut engine, 2);

    engine.trigger_row(&[0xFF, 0, 0, 0, 0, 0, 0, 0], &[0, 0, 0, 0, 0, 0, 0, 0]);
    // Render enough for the release to complete
    let after_off = render_chunks(&mut engine, 30);
    let tail_rms = rms(&after_off[after_off.len() - 2940..]);
    assert!(tail_rms < 10.0, "note-off tail must decay near zero, got rms={}", tail_rms);
}

#[test]
fn golden_all_notes_off_immediate_silence() {
    let mut engine = SynthEngine::new();
    engine.trigger_row(&[60, 48, 72, 36, 84, 55, 67, 43], &[200; 8]);
    let _ = render_chunks(&mut engine, 2);

    engine.all_notes_off();
    let after = render_chunks(&mut engine, 2);
    assert!(after.iter().all(|s| *s == 0), "all_notes_off must produce immediate digital zero");
}
