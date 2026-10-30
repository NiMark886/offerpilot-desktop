# OfferPilot Desktop (Electron)

This is the active desktop shell for the local-only OfferPilot build.

## Architecture

```text
Electron main process
├── BrowserWindow + preload
├── Spring Boot sidecar (bundled jlink JRE)
│   ├── H2 file database
│   ├── local resume storage
│   └── Spring AI / provider calls
└── Local data: %USERPROFILE%\.offerpilot
```

- No OfferPilot server is required.
- No MySQL, Java, Node.js, or Maven is required on the target machine.
- The backend listens only on `127.0.0.1:18080`.
- Desktop mode auto-authenticates as the local admin, so the app opens directly on the home page.
- The only optional outbound calls are to the model provider configured by the user.

## Run in development

```powershell
cd ..\frontend
$env:OFFERPILOT_DESKTOP="true"
npm run build
Remove-Item Env:OFFERPILOT_DESKTOP

cd ..\backend
mvn -DskipTests package

cd ..\electron
npm install
npm start
```

## Build installer

```powershell
cd E:\work app\electron
.\build-installer.ps1
```

The script:

1. Builds the frontend with relative asset paths.
2. Builds and copies the backend jar.
3. Copies the jlink runtime.
4. Packages Electron + backend + runtime.
5. Produces `E:\work app\OfferPilot-Electron-0.1.0-x64-setup.exe`.

The current installer is about 215 MB; the installed app is about 484 MB because it includes Chromium/Electron plus the Spring Boot jar and JRE.

## Local data

```text
%USERPROFILE%\.offerpilot\
├── data\offerpilot.mv.db
├── uploads\
├── logs\
└── secrets\
```

The API key is stored in the local H2 database, encrypted with the per-install settings secret. No API key is sent to an OfferPilot server. If a model provider is configured, the backend calls that provider directly.

Resume assessment is now four-part: education 25%, relevant skills 25%, merged internship/work experience 40%, layout/structure 10%. AI evaluates the first three against the target job; layout is scored by deterministic structural checks (file format, contact info, section headings, length, bullets, dates).

There is also a standalone resume review page at `#/resume-review`: choose/upload a resume, optionally select a job template or paste a JD, then get scores plus a detailed issue list with severity, evidence and fix suggestions. Backend endpoint: `POST /api/resumes/{id}/evaluation`.

## Verified

- Installed with the generated NSIS installer.
- Starts without system Java/MySQL.
- Auto-login opens the home page.
- `app.ai.enabled=true` by default.
- Saved provider key test succeeds (`DeepSeek`, `deepseek-v4-flash`).
- Closing the window stops the Java sidecar and releases port 18080.

## Remaining work

- Random local port instead of fixed 18080.
- OS credential manager instead of encrypted database storage.
- Code signing and auto-update.
- First-run onboarding.
