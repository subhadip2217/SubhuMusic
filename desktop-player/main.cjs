const { app, BrowserWindow, ipcMain, Menu } = require("electron");
const path = require("node:path");
const { spawn } = require("node:child_process");
const APP_NAME = "SubhuMusic";
const YTDLP = app.isPackaged ? path.join(process.resourcesPath, "yt-dlp.exe") : path.join(__dirname, "bin", "yt-dlp.exe");
function runYtDlp(args) {
  return new Promise((resolve, reject) => {
    const child = spawn(YTDLP, args, { windowsHide: true, stdio: ["ignore", "pipe", "pipe"] });
    let stdout = "", stderr = "";
    child.stdout.setEncoding("utf8"); child.stderr.setEncoding("utf8");
    child.stdout.on("data", chunk => { stdout += chunk; if (stdout.length > 15000000) child.kill(); });
    child.stderr.on("data", chunk => { stderr += chunk; if (stderr.length > 250000) stderr = stderr.slice(-250000); });
    child.on("error", err => reject(new Error("Could not start the stream engine: " + err.message)));
    child.on("close", code => {
      if (code !== 0) {
        const detail = stderr.trim();
        if (/requested format is not available/i.test(detail)) return reject(new Error("YouTube did not provide a compatible stream for this video. Try another result."));
        if (/sign in|bot|confirm you.re not a bot/i.test(detail)) return reject(new Error("YouTube is restricting this video right now. Try another result."));
        return reject(new Error(detail || "Stream extraction failed."));
      }
      try { resolve(JSON.parse(stdout)); } catch { reject(new Error("Invalid response from stream engine.")); }
    });
  });
}
function safeEntry(e) {
  return { id:String(e.id||""), title:String(e.title||"Unknown title"), artist:String(e.artist||e.uploader||e.channel||"Unknown artist"), thumbnail:String(e.thumbnail||e.thumbnails?.at(-1)?.url||""), duration:Number(e.duration||0) };
}
ipcMain.handle("music:search", async (_e, query) => {
  const q=String(query||"").trim().slice(0,180); if(!q)return [];
  const data=await runYtDlp(["--dump-single-json","--flat-playlist","--no-warnings","--no-playlist","ytsearch15:"+q]);
  return (Array.isArray(data.entries)?data.entries:[]).filter(e=>e&&e.id).map(safeEntry);
});
ipcMain.handle("music:resolve", async (_e,id,mode) => {
  const videoId=String(id||"").trim(); if(!/^[A-Za-z0-9_-]{11}$/.test(videoId))throw new Error("Invalid video ID.");
  // Avoid hard-coded extension/codec requirements: YouTube may only expose WebM/Opus or
  // separate adaptive streams for a particular video. Chromium can play common WebM/MP4.
  const format=mode==="video"?"best[height<=1080]/best":"bestaudio/best";
  const data=await runYtDlp(["--dump-single-json","--no-warnings","--no-playlist","--format",format,"--no-check-formats","https://www.youtube.com/watch?v="+videoId]);
  if(!data.url)throw new Error("No direct stream is available for this video. Try another result.");
  return {url:data.url,duration:Number(data.duration||0),ext:String(data.ext||""),title:String(data.title||"")};
});
function createWindow(){
 const win=new BrowserWindow({width:1440,height:900,minWidth:1050,minHeight:680,title:APP_NAME,backgroundColor:"#0d0d10",autoHideMenuBar:true,webPreferences:{preload:path.join(__dirname,"preload.cjs"),contextIsolation:true,nodeIntegration:false,sandbox:true,webSecurity:true}});
 win.loadFile(path.join(__dirname,"index.html"));
}
app.whenReady().then(()=>{app.setName(APP_NAME);Menu.setApplicationMenu(null);createWindow();app.on("activate",()=>{if(!BrowserWindow.getAllWindows().length)createWindow()})});
app.on("window-all-closed",()=>{if(process.platform!=="darwin")app.quit()});
