import { encodeWav } from './wav'

const TARGET_SAMPLE_RATE = 16000

export interface VoiceRecorderHandle {
  stop(): Promise<ArrayBuffer>
  cancel(): void
}

export function recordingErrorMessage(error: unknown): string {
  if (error instanceof DOMException) {
    if (error.name === 'NotAllowedError' || error.name === 'SecurityError') {
      return '麦克风权限被拒绝，请在 Windows 设置中允许 OfferPilot 使用麦克风'
    }
    if (error.name === 'NotFoundError' || error.name === 'DevicesNotFoundError') {
      return '没有检测到可用的麦克风'
    }
    if (error.name === 'NotReadableError' || error.name === 'TrackStartError') {
      return '麦克风被其他程序占用，请关闭占用麦克风的程序后重试'
    }
  }
  return error instanceof Error ? error.message : '无法启动录音'
}

async function blobToWav(blob: Blob): Promise<ArrayBuffer> {
  const encoded = await blob.arrayBuffer()
  const audioContext = new AudioContext()
  try {
    const decoded = await audioContext.decodeAudioData(encoded)
    const frameCount = Math.max(1, Math.ceil(decoded.duration * TARGET_SAMPLE_RATE))
    const offline = new OfflineAudioContext(1, frameCount, TARGET_SAMPLE_RATE)
    const source = offline.createBufferSource()
    source.buffer = decoded
    source.connect(offline.destination)
    source.start(0)
    const rendered = await offline.startRendering()
    return encodeWav(rendered.getChannelData(0), TARGET_SAMPLE_RATE)
  } finally {
    await audioContext.close().catch(() => {})
  }
}

function preferredMimeType(): string | undefined {
  const candidates = [
    'audio/webm;codecs=opus',
    'audio/webm',
    'audio/ogg;codecs=opus'
  ]
  return candidates.find((type) => MediaRecorder.isTypeSupported(type))
}

export async function openMicrophoneStream(deviceId?: string): Promise<MediaStream> {
  if (!navigator.mediaDevices?.getUserMedia) {
    throw new Error('当前环境不支持录音')
  }

  const baseAudio: MediaTrackConstraints = {
    channelCount: 1,
    echoCancellation: true,
    noiseSuppression: true,
    autoGainControl: true
  }
  const audio: MediaTrackConstraints = deviceId
    ? { ...baseAudio, deviceId: { exact: deviceId } }
    : baseAudio

  try {
    return await navigator.mediaDevices.getUserMedia({ audio })
  } catch (error) {
    if (
      deviceId
      && error instanceof DOMException
      && (error.name === 'OverconstrainedError' || error.name === 'NotFoundError')
    ) {
      throw new Error('所选麦克风不可用，请在设置中重新选择输入设备')
    }
    throw new Error(recordingErrorMessage(error))
  }
}

export async function startVoiceRecording(deviceId?: string): Promise<VoiceRecorderHandle> {
  const stream = await openMicrophoneStream(deviceId)
  const mimeType = preferredMimeType()
  const recorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream)
  const chunks: BlobPart[] = []
  let stopPromise: Promise<ArrayBuffer> | null = null
  let cancelled = false

  recorder.addEventListener('dataavailable', (event) => {
    if (event.data.size > 0) chunks.push(event.data)
  })

  const release = () => {
    for (const track of stream.getTracks()) track.stop()
  }

  recorder.start(250)

  const stop = (): Promise<ArrayBuffer> => {
    if (stopPromise) return stopPromise
    stopPromise = new Promise<ArrayBuffer>((resolve, reject) => {
      const finish = async () => {
        release()
        if (cancelled) {
          reject(new Error('录音已取消'))
          return
        }
        try {
          const blob = new Blob(chunks, { type: recorder.mimeType || 'audio/webm' })
          if (blob.size === 0) {
            reject(new Error('没有录到声音，请重试'))
            return
          }
          resolve(await blobToWav(blob))
        } catch (error) {
          reject(new Error(recordingErrorMessage(error)))
        }
      }

      if (recorder.state === 'inactive') {
        void finish()
        return
      }
      recorder.addEventListener('stop', () => void finish(), { once: true })
      recorder.stop()
    })
    return stopPromise
  }

  return {
    stop,
    cancel() {
      cancelled = true
      if (recorder.state !== 'inactive') {
        recorder.stop()
      }
      release()
    }
  }
}
