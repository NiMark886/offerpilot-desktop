const { spawn } = require('child_process')
const fs = require('fs')
const os = require('os')
const path = require('path')
const crypto = require('crypto')

const MODEL_FILE = 'ggml-small-q5_1.bin'
const MAX_AUDIO_BYTES = 24 * 1024 * 1024
const TRANSCRIBE_TIMEOUT_MS = 300000
const PROMPTS = {
  zh: '以下是中文技术面试回答，请使用简体中文，保留英文技术术语。',
  en: 'The following is an English technical interview answer. Keep technical terms accurate.'
}

function normalizeLanguage(value) {
  return value === 'en' || value === 'auto' ? value : 'zh'
}

function toBuffer(data) {
  if (Buffer.isBuffer(data)) return data
  if (data instanceof ArrayBuffer) return Buffer.from(new Uint8Array(data))
  if (ArrayBuffer.isView(data)) {
    return Buffer.from(data.buffer, data.byteOffset, data.byteLength)
  }
  if (Array.isArray(data)) return Buffer.from(data)
  return null
}

function isWav(buffer) {
  return buffer.length >= 12
    && buffer.toString('ascii', 0, 4) === 'RIFF'
    && buffer.toString('ascii', 8, 12) === 'WAVE'
}

function cleanTranscript(raw) {
  const lines = raw
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line)
    .filter((line) => !/^\[.*\]$/.test(line))
    .filter((line) => !/^\(.*\)$/.test(line))
    .filter((line) => !/^(blank_audio|music|silence|applause)$/i.test(line))

  let text = ''
  for (const line of lines) {
    if (!text) {
      text = line
    } else if (/[A-Za-z0-9]$/.test(text) && /^[A-Za-z0-9]/.test(line)) {
      text += ` ${line}`
    } else {
      text += line
    }
  }
  return text
    .replace(/\s+/g, ' ')
    .replace(/^["'“”‘’]+|["'“”‘’]+$/g, '')
    .trim()
}

function createSpeechService({ resourceRoot, tempRoot }) {
  let active = false

  function whisperDir() {
    const root = typeof resourceRoot === 'function' ? resourceRoot() : resourceRoot
    return path.join(root, 'whisper')
  }

  function binaryPath() {
    return path.join(whisperDir(), process.platform === 'win32' ? 'whisper-cli.exe' : 'whisper-cli')
  }

  function modelPath() {
    return path.join(whisperDir(), 'models', MODEL_FILE)
  }

  function status() {
    const binary = binaryPath()
    const model = modelPath()
    if (!fs.existsSync(binary)) {
      return { ready: false, reason: '语音识别组件缺失，请重新安装应用' }
    }
    if (!fs.existsSync(model)) {
      return { ready: false, reason: '语音模型缺失，请重新安装应用' }
    }
    return { ready: true, reason: '' }
  }

  async function transcribe(data, options = {}) {
    if (active) {
      throw new Error('正在识别上一条语音，请稍候')
    }
    const language = normalizeLanguage(options.language)
    const ready = status()
    if (!ready.ready) {
      throw new Error(ready.reason)
    }

    const buffer = toBuffer(data)
    if (!buffer || buffer.length === 0) {
      throw new Error('没有收到录音数据')
    }
    if (buffer.length > MAX_AUDIO_BYTES) {
      throw new Error('录音过长，请缩短到 10 分钟以内')
    }
    if (!isWav(buffer)) {
      throw new Error('录音格式无效')
    }

    active = true
    const wavPath = path.join(tempRoot, `voice-${crypto.randomUUID()}.wav`)
    try {
      await fs.promises.mkdir(tempRoot, { recursive: true })
      await fs.promises.writeFile(wavPath, buffer)

      const threads = Math.max(2, Math.min(8, os.cpus().length))
      const args = [
        '-m', modelPath(),
        '-f', wavPath,
        '-l', language,
        '-nt',
        '-np',
        '-t', String(threads)
      ]
      if (PROMPTS[language]) {
        args.push('--prompt', PROMPTS[language])
      }

      const result = await new Promise((resolve, reject) => {
        const child = spawn(binaryPath(), args, {
          cwd: whisperDir(),
          windowsHide: true,
          stdio: ['ignore', 'pipe', 'pipe']
        })
        let stdout = ''
        let stderr = ''
        let settled = false
        let timer = null

        const finish = (error, value) => {
          if (settled) return
          settled = true
          if (timer) clearTimeout(timer)
          if (error) reject(error)
          else resolve(value)
        }

        timer = setTimeout(() => {
          try {
            child.kill()
          } catch {
            // process may already be gone
          }
          finish(new Error('语音识别超时，请缩短回答或重试'))
        }, TRANSCRIBE_TIMEOUT_MS)

        child.stdout.on('data', (chunk) => {
          stdout += chunk.toString('utf8')
          if (stdout.length > 2 * 1024 * 1024) {
            try {
              child.kill()
            } catch {
              // ignore
            }
            finish(new Error('语音识别结果过大'))
          }
        })
        child.stderr.on('data', (chunk) => {
          stderr += chunk.toString('utf8')
          if (stderr.length > 2 * 1024 * 1024) stderr = stderr.slice(-2 * 1024 * 1024)
        })
        child.on('error', (error) => {
          finish(new Error(`无法启动语音识别：${error.message}`))
        })
        child.on('close', (code) => {
          if (code !== 0) {
            const detail = stderr.trim().split(/\r?\n/).slice(-1)[0] || `退出码 ${code}`
            finish(new Error(`语音识别失败：${detail}`))
            return
          }
          const text = cleanTranscript(stdout)
          if (!text) {
            finish(new Error('没有识别到语音，请靠近麦克风重试'))
            return
          }
          finish(null, { text })
        })
      })

      return result
    } finally {
      active = false
      await fs.promises.unlink(wavPath).catch(() => {})
    }
  }

  return { status, transcribe }
}

module.exports = { createSpeechService, cleanTranscript }
