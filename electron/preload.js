const { contextBridge } = require('electron')

contextBridge.exposeInMainWorld('offerpilotDesktop', {
  isElectron: true,
  apiBase: 'http://127.0.0.1:18080'
})
