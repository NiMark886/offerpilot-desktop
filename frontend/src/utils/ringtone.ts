import { encodeWav } from './wav'

const SAMPLE_RATE = 44100
const DURATION_SECONDS = 2.2

export function createRingtoneUrl(): string {
  const frameCount = Math.floor(SAMPLE_RATE * DURATION_SECONDS)
  const samples = new Float32Array(frameCount)
  const notes = [
    { start: 0, frequency: 880 },
    { start: 0.55, frequency: 1174.66 },
    { start: 1.1, frequency: 880 }
  ]

  for (let index = 0; index < frameCount; index += 1) {
    const time = index / SAMPLE_RATE
    let value = 0
    for (const note of notes) {
      const elapsed = time - note.start
      if (elapsed < 0 || elapsed > 0.5) continue
      value += Math.sin(2 * Math.PI * note.frequency * elapsed)
        * Math.exp(-6 * elapsed)
        * 0.32
    }
    samples[index] = Math.max(-1, Math.min(1, value))
  }

  return URL.createObjectURL(new Blob([encodeWav(samples, SAMPLE_RATE)], { type: 'audio/wav' }))
}
