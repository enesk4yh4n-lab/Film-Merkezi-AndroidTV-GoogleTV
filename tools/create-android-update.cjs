'use strict';
// Run locally after signing and verifying the release APK. Never receives a JKS password.
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process'),crypto=require('node:crypto');
const args=process.argv.slice(2),o={};for(let i=0;i<args.length;i+=2){if(!args[i].startsWith('--')||!args[i+1])throw Error('Expected --option value');o[args[i].slice(2)]=args[i+1];}
for(const name of ['apk','key','out','notes'])if(!o[name])throw Error('--'+name+' required');
const root=path.resolve(__dirname,'..'),privatePath=fs.realpathSync(o.key);
if(privatePath.toLowerCase().startsWith(root.toLowerCase()+path.sep))throw Error('Private key must stay outside source checkout');
const sdk=process.env.ANDROID_HOME||process.env.ANDROID_SDK_ROOT;if(!sdk)throw Error('ANDROID_HOME required');
const tools=path.join(sdk,'build-tools/36.1.0'),java=process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin',process.platform==='win32'?'java.exe':'java'):'java';
const apk=path.resolve(o.apk);
const certificate=cp.execFileSync(java,['-jar',path.join(tools,'lib/apksigner.jar'),'verify','--print-certs',apk],{encoding:'utf8',stdio:['ignore','pipe','pipe']});
const cert=/Signer #1 certificate SHA-256 digest: ([a-f0-9]{64})/.exec(certificate)?.[1];if(!cert||certificate.includes('Signer #2'))throw Error('Exactly one verified signer required');
const aapt=cp.execFileSync(path.join(tools,process.platform==='win32'?'aapt.exe':'aapt'),['dump','badging',apk],{encoding:'utf8',stdio:['ignore','pipe','pipe']});
const packageLine=/package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'/.exec(aapt),minSdk=/sdkVersion:'(\d+)'/.exec(aapt);
if(packageLine?.[1]!=='app.filmmerkezi.tv'||!minSdk)throw Error('APK identity could not be verified');
const version=packageLine[3],code=Number(packageLine[2]);if(!/^\d+\.\d+\.\d+$/.test(version)||code<36)throw Error('Only permanent-key release versions are accepted');
const payload=fs.readFileSync(apk),notes=fs.readFileSync(o.notes,'utf8').trim();if(!notes||notes.length>4000||payload.length>64*1024*1024)throw Error('Feed limits');
const feed={schemaVersion:1,packageName:packageLine[1],versionCode:code,versionName:version,minSdk:Number(minSdk[1]),url:`https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV/releases/download/v${version}/${path.basename(apk)}`,size:payload.length,sha256:crypto.createHash('sha256').update(payload).digest('hex'),certificateSha256:cert,notes};
const raw=Buffer.from(JSON.stringify(feed,null,2)+'\n'),key=crypto.createPrivateKey(fs.readFileSync(privatePath)),signature=crypto.sign('sha256',raw,key).toString('base64')+'\n';
fs.mkdirSync(o.out,{recursive:true});fs.writeFileSync(path.join(o.out,'update.json'),raw);fs.writeFileSync(path.join(o.out,'update.sig'),signature);
console.log(JSON.stringify({version,code,bytes:payload.length,sha256:feed.sha256,certificate:cert}));
