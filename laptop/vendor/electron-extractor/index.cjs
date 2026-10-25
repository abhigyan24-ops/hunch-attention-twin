// Windows-only installer adapter: avoids Electron's native Rust .node extractor.
// Electron verifies the official archive checksum before calling this API.
const { execFile } = require('node:child_process');
const path = require('node:path');
const fs = require('node:fs/promises');
async function extract(zipPath, options) {
 if(process.platform !== 'win32') throw new Error('Dhyan laptop installer supports Windows only');
 if(typeof zipPath !== 'string' || !path.isAbsolute(zipPath) || !options || typeof options.dir !== 'string' || !path.isAbsolute(options.dir)) throw new Error('Absolute archive and destination paths required');
 await fs.mkdir(options.dir,{recursive:true});
 const systemRoot=process.env.SystemRoot || 'C:\\Windows';
 const executable=path.join(systemRoot,'System32','WindowsPowerShell','v1.0','powershell.exe');
 const script="$ErrorActionPreference='Stop'; Expand-Archive -LiteralPath $env:DHYAN_ARCHIVE -DestinationPath $env:DHYAN_DESTINATION -Force";
 await new Promise((resolve,reject)=>execFile(executable,['-NoLogo','-NoProfile','-NonInteractive','-Command',script],{windowsHide:true,env:{...process.env,DHYAN_ARCHIVE:zipPath,DHYAN_DESTINATION:options.dir}},error=>error?reject(new Error('Electron archive extraction failed')):resolve()));
}
module.exports = {extract,default:extract};
