const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("taktDesktop", {
  isDesktop: true,
  platform: process.platform,
  openExternal: (url) => ipcRenderer.invoke("takt:openExternal", url),
});
