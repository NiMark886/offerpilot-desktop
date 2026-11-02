const fs = require('fs')
const path = require('path')
const { createSpeechService } = require('../speech')

const wavPath = process.argv[2]
const language = process.argv[3] || 'zh'
if (!wavPath) {
  console.error('Usage: node scripts/test-speech.js <path-to-wav> [zh|en|auto]')
  process.exit(1)
}

const service = createSpeechService({
  resourceRoot: path.join(__dirname, '..', 'resources'),
  tempRoot: path.join(__dirname, '..', '..', '.tmp', 'speech-test')
})

service.transcribe(fs.readFileSync(wavPath), { language })
  .then((result) => {
    console.log(result.text)
  })
  .catch((error) => {
    console.error(error.message)
    process.exit(1)
  })
