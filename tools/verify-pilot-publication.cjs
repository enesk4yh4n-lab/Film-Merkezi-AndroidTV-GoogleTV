'use strict';
const fs=require('node:fs'),crypto=require('node:crypto');
const root='https://raw.githubusercontent.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV',addons=root+'-Eklentiler/main/',expected=['p002','r002','p003','r003','p004','r004','p005','r005'];
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
async function get(url){const r=await fetch(url,{signal:AbortSignal.timeout(30000),headers:{'Cache-Control':'no-cache'}});if(!r.ok)throw Error(`${r.status} ${url}`);return Buffer.from(await r.arrayBuffer());}
function verify(raw,sig,key){if(!crypto.verify('sha256',raw,crypto.createPublicKey({key:Buffer.from(key,'base64'),type:'spki',format:'der'}),Buffer.from(sig.toString().trim(),'base64')))throw Error('Signature mismatch');}
(async()=>{
 const name='filmmerkezi-android-repository.json',raw=await get(addons+name),index=JSON.parse(raw);verify(raw,await get(addons+name.replace('.json','.sig')),index.publicKey);
 if(index.revision!==35||index.packages.map(p=>p.id).join(',')!==expected.join(','))throw Error('Unexpected public pilot scope/revision');
 const fp=sha(Buffer.from(index.publicKey,'base64'));if(fp!=='d31d23cccc49043e3846a9b799e1cea9464f8cd29e04daa55dd5d05c1a96fc1f')throw Error('Publisher mismatch');
 let pos=0;const entries=[];
 await Promise.all([0,1].map(async()=>{while(pos<index.packages.length){const e=index.packages[pos++],r=await get(addons+e.manifest);if(sha(r)!==e.manifestSha256)throw Error('Manifest hash mismatch');verify(r,await get(addons+e.manifest.replace('.json','.sig')),index.publicKey);const m=JSON.parse(r);if(m.minAppVersionCode!==38)throw Error('Core 38 required');const artifact=await get(addons+m.artifact.path);if(sha(artifact)!==m.artifact.sha256||artifact.length!==m.artifact.size)throw Error('DEX mismatch');if(m.type==='site'&&(m.dependencies.length!==1||m.dependencies[0].id!=='r'+m.id.slice(1)||m.dependencies[0].publisher!==fp))throw Error('Resolver dependency mismatch');entries.push({id:e.id,version:m.version,bytes:artifact.length});}}));
 const feed=await get(root+'/main/update.json');verify(feed,await get(root+'/main/update.sig'),index.publicKey);const app=JSON.parse(feed);if(app.versionCode!==38||app.versionName!=='0.12.2')throw Error('Unexpected public app feed');
 const apk=await get(app.url);if(apk.length!==app.size||sha(apk)!==app.sha256||app.certificateSha256!=='73753d46c206a8f651067541b12f5e038a5d207cca4bee21f8f984a27ed197ed')throw Error('APK mismatch');
 const report={verified:true,indexRevision:index.revision,packages:entries.sort((a,b)=>expected.indexOf(a.id)-expected.indexOf(b.id)),app:{version:app.versionName,code:app.versionCode,bytes:apk.length,sha256:sha(apk)}};
 fs.mkdirSync('reports',{recursive:true});fs.writeFileSync('reports/pilot-publication-verified.json',JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
})().catch(e=>{console.error(e.message);process.exitCode=1;});
