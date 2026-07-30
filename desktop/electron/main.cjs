const { app, BrowserWindow, ipcMain, shell, session } = require("electron");
const path = require("node:path");

const isAllowedExternalUrl = (value) => {
  try {
    const parsed = new URL(value);
    return parsed.protocol === "https:" || parsed.protocol === "mailto:";
  } catch {
    return false;
  }
};

let mainWindow;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1540,
    height: 940,
    minWidth: 1060,
    minHeight: 700,
    backgroundColor: "#10131d",
    title: "Такт",
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, "preload.cjs"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });

  const devServerUrl = process.env.TAKT_DEV_SERVER_URL;
  if (devServerUrl) {
    mainWindow.loadURL(devServerUrl);
  } else {
    mainWindow.loadFile(path.join(__dirname, "..", "dist", "index.html"));
  }

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (isAllowedExternalUrl(url)) void shell.openExternal(url);
    return { action: "deny" };
  });
}

const gotLock = app.requestSingleInstanceLock();
if (!gotLock) {
  app.quit();
} else {
  app.on("second-instance", () => {
    if (!mainWindow) return;
    if (mainWindow.isMinimized()) mainWindow.restore();
    mainWindow.focus();
  });

  app.whenReady().then(() => {
    app.setAppUserModelId("app.takt.desktop");

    // The renderer has no Node access. Only media permissions required for a
    // call are granted; navigation and all external links are kept outside it.
    session.defaultSession.setPermissionRequestHandler((_contents, permission, callback) => {
      callback(["media", "display-capture", "notifications"].includes(permission));
    });

    ipcMain.handle("takt:openExternal", (_event, url) => {
      if (!isAllowedExternalUrl(url)) return false;
      return shell.openExternal(url).then(() => true).catch(() => false);
    });

    createWindow();
    app.on("activate", () => {
      if (BrowserWindow.getAllWindows().length === 0) createWindow();
    });
  });

  app.on("window-all-closed", () => {
    if (process.platform !== "darwin") app.quit();
  });
}
