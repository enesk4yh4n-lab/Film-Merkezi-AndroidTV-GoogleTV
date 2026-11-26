/* SPDX-License-Identifier: MIT */
'use strict';
// Private publisher keys must stay outside both source and publication repositories.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),zlib=require('node:zlib');
const hash=b=>crypto.createHash('sha256').update(b).digest('hex');
const assert=(condition,message)=>{if(!condition)throw Error(message);};
const INDEX='filmmerkezi-android-repository.json';
const id=s=>typeof s==='string'&&/^[a-z][a-z0-9-]{0,63}$/.test(s);
const version=s=>typeof s==='string'&&/^\d{1,4}\.\d{1,4}\.\d{1,4}$/.test(s);
const compare=(a,b)=>{const x=a.split('.').map(Number),y=b.split('.').map(Number);return x[0]-y[0]||x[1]-y[1]||x[2]-y[2];};
const safePath=s=>typeof s==='string'&&s.length<=220&&/^[A-Za-z0-9_./-]+$/.test(s)&&!s.startsWith('/')&&s.split('/').every(x=>x&&x!=='.'&&x!=='..');
function outside(keyFile,out){const key=fs.realpathSync(keyFile).toLowerCase();for(const directory of [path.resolve(__dirname,'..'),path.resolve(out)])assert(!key.startsWith(directory.toLowerCase()+path.sep),'Private key must stay outside repositories');}
function keyInfo(k){const der=crypto.createPublicKey(k).export({type:'spki',format:'der'});return {publicKey:der.toString('base64'),fingerprint:hash(der)};}
function bytes(value){return Buffer.from(JSON.stringify(value,null,2)+'\n');}
function atomic(file,data){fs.mkdirSync(path.dirname(file),{recursive:true});const temporary=file+'.pending';fs.writeFileSync(temporary,data);fs.renameSync(temporary,file);}
function signed(file,raw,k){atomic(file,raw);atomic(file.replace(/\.json$/,'.sig'),crypto.sign('sha256',raw,k).toString('base64')+'\n');}
function validateManifest(meta){
 assert(id(meta.id)&&version(meta.version),'Invalid id/version');assert(['site','resolver'].includes(meta.type),'Invalid package type');
 assert(typeof meta.name==='string'&&meta.name.trim()&&meta.name.length<=200,'Invalid name');
 assert(Number.isInteger(meta.minAppVersionCode)&&meta.minAppVersionCode>0&&Number.isInteger(meta.minSdk)&&meta.minSdk>=23,'Invalid application compatibility');
 assert(/^app\.filmmerkezi\.addon\.[A-Za-z0-9_.$]+$/.test(meta.entryClass),'Invalid entry class');
 assert((meta.type==='site')===!!meta.site,'Site metadata is required only for site packages');
 if(meta.site){const u=new URL(meta.site.base);assert(id(meta.site.id)&&typeof meta.site.name==='string'&&u.protocol==='https:'&&!u.username&&!u.password&&!u.port&&u.pathname==='/'&&!u.search&&!u.hash,'Invalid site metadata');}
 assert(Array.isArray(meta.origins)&&meta.origins.length>0&&meta.origins.length<=256&&meta.origins.every(s=>/^https:\/\/(?:\*\.)?[a-z0-9][a-z0-9.{}-]+$/.test(s)),'Invalid closed network origins');
 assert(Array.isArray(meta.dependencies)&&meta.dependencies.length<=16,'Invalid dependencies');
 const seen=new Set();for(const d of meta.dependencies){const k=d.publisher+'/'+d.id;assert(/^[a-f0-9]{64}$/.test(d.publisher)&&id(d.id)&&version(d.minVersion)&&version(d.maxVersion)&&compare(d.minVersion,d.maxVersion)<0&&!seen.has(k),'Invalid dependency range');seen.add(k);}
}
function validateJar(zip){
 assert(zip.length<=4194304&&zip.length>=22,'Package size budget');let end=-1;
 for(let i=zip.length-22;i>=Math.max(0,zip.length-65557);i--)if(zip.readUInt32LE(i)===0x06054b50){end=i;break;}
 assert(end>=0,'Invalid ZIP');const count=zip.readUInt16LE(end+10),offset=zip.readUInt32LE(end+16);assert(count>0&&count<=64,'ZIP entry budget');
 let position=offset,total=0,dex=false;const names=new Set();
 for(let i=0;i<count;i++){
  assert(position+46<=zip.length&&zip.readUInt32LE(position)===0x02014b50,'Invalid central directory');
  const method=zip.readUInt16LE(position+10),compressed=zip.readUInt32LE(position+20),size=zip.readUInt32LE(position+24),n=zip.readUInt16LE(position+28),extra=zip.readUInt16LE(position+30),comment=zip.readUInt16LE(position+32),local=zip.readUInt32LE(position+42);
  const name=zip.subarray(position+46,position+46+n).toString('utf8');assert(safePath(name.replace(/\/$/,''))&&!name.endsWith('.so')&&!name.startsWith('lib/')&&!names.has(name),'Unsafe/duplicate ZIP entry');names.add(name);
  total+=size;assert(total<=8388608,'Expanded size budget');
  assert(local+30<=zip.length&&zip.readUInt32LE(local)===0x04034b50,'Invalid local ZIP header');
  const start=local+30+zip.readUInt16LE(local+26)+zip.readUInt16LE(local+28);assert(start+compressed<=zip.length,'Truncated ZIP entry');
  if(!name.endsWith('/')){const body=zip.subarray(start,start+compressed);const content=method===0?body:method===8?zlib.inflateRawSync(body,{maxOutputLength:8388609}):null;assert(content&&content.length===size,'Invalid ZIP compression');if(name==='classes.dex'){assert(content.subarray(0,4).equals(Buffer.from('dex\n')),'Invalid DEX magic');dex=true;}}
  position+=46+n+extra+comment;
 }assert(dex,'classes.dex required');
}
function pack(options){
 const out=path.resolve(options.out);outside(options.key,out);const k=crypto.createPrivateKey(fs.readFileSync(options.key)),info=keyInfo(k);
 const meta=JSON.parse(fs.readFileSync(options.manifest,'utf8'));validateManifest(meta);const artifact=fs.readFileSync(options.jar);validateJar(artifact);
 const rel=`packages/${meta.id}/${meta.version}/`,target=path.join(out,rel);assert(!fs.existsSync(target),'Published versions are immutable; increase version');
 const indexPath=path.join(out,INDEX);let index;
 if(fs.existsSync(indexPath)){
  const raw=fs.readFileSync(indexPath);index=JSON.parse(raw);assert(index.publicKey===info.publicKey,'Publisher key differs');
  assert(crypto.verify('sha256',raw,crypto.createPublicKey({key:Buffer.from(index.publicKey,'base64'),format:'der',type:'spki'}),Buffer.from(fs.readFileSync(indexPath.replace(/\.json$/,'.sig'),'utf8'),'base64')),'Existing repository signature differs');
  assert(index.platform==='android'&&index.runtime==='kotlin-dex-v1'&&index.schemaVersion===1&&Number.isInteger(index.revision)&&index.revision>=1,'Invalid existing repository');
 }else{index={schemaVersion:1,platform:'android',runtime:'kotlin-dex-v1',id:options.id||'community',name:options.name||'Independent publisher',publicKey:info.publicKey,revision:0,packages:[]};}
 assert(id(index.id)&&Array.isArray(index.packages)&&index.packages.length<512,'Repository budget');
 assert(!index.packages.some(p=>p.id===meta.id&&p.version===meta.version),'Duplicate version');
 const manifest={...meta,schemaVersion:1,platform:'android',runtime:'kotlin-dex-v1',sdkMin:1,sdkMax:2,artifact:{path:rel+'plugin.jar',size:artifact.length,sha256:hash(artifact)}};
 const raw=bytes(manifest);assert(raw.length<=65536,'Manifest budget');
 index.packages.push({id:meta.id,type:meta.type,name:meta.name,version:meta.version,minAppVersionCode:meta.minAppVersionCode,manifest:rel+'manifest.json',manifestSha256:hash(raw)});index.revision++;
 const indexRaw=bytes(index);assert(indexRaw.length<=1048576,'Repository index budget');
 // Write immutable files first; index is the commit marker for clients.
 atomic(path.join(target,'plugin.jar'),artifact);signed(path.join(target,'manifest.json'),raw,k);signed(indexPath,indexRaw,k);
 return {packed:meta.id,version:meta.version,...info};
}
function main(){const args=process.argv.slice(2),command=args.shift(),o={};for(let i=0;i<args.length;i+=2){assert(args[i].startsWith('--')&&args[i+1],'Expected --name value');o[args[i].slice(2)]=args[i+1];}
 if(command==='keygen'){
  const out=path.resolve(o.out||'');assert(o.out&&!fs.existsSync(out),'Choose a new private key outside repositories');assert(!out.toLowerCase().startsWith(path.resolve(__dirname,'..').toLowerCase()+path.sep),'Private key must stay outside workspace');
  const pair=crypto.generateKeyPairSync('rsa',{modulusLength:3072,privateKeyEncoding:{type:'pkcs8',format:'pem'},publicKeyEncoding:{type:'spki',format:'pem'}});fs.mkdirSync(path.dirname(out),{recursive:true});fs.writeFileSync(out,pair.privateKey,{mode:0o600,flag:'wx'});console.log(JSON.stringify(keyInfo(crypto.createPrivateKey(pair.privateKey))));
 }else if(command==='pack')console.log(JSON.stringify(pack(o)));
 else if(command==='empty-repository'){
  outside(o.key,o.out);const file=path.join(o.out,INDEX);assert(!fs.existsSync(file),'Repository already exists');const k=crypto.createPrivateKey(fs.readFileSync(o.key));const index={schemaVersion:1,platform:'android',runtime:'kotlin-dex-v1',id:o.id||'film-merkezi-android-eklentiler',name:o.name||'Film Merkezi Android Eklentiler',publicKey:keyInfo(k).publicKey,revision:1,packages:[]};assert(id(index.id),'Invalid repository id');signed(file,bytes(index),k);console.log('Created signed empty Android index');
 }else if(command==='sign-json'){
  const output=o.output||o.input.replace(/\.json$/,'.sig');assert(path.resolve(output)!==path.resolve(o.input),'Signature must not overwrite input');const raw=fs.readFileSync(o.input);JSON.parse(raw);atomic(output,crypto.sign('sha256',raw,crypto.createPrivateKey(fs.readFileSync(o.key))).toString('base64')+'\n');console.log('Signed exact JSON bytes');
 }else throw Error('Commands: keygen, empty-repository, sign-json, pack');
}
module.exports={validateManifest,validateJar,pack,hash,INDEX};if(require.main===module)main();
