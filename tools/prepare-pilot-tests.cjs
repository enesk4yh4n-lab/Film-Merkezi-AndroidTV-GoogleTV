'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {INDEX,hash}=require('./android-distribution.cjs');
const root=path.resolve(__dirname,'..'),args={};for(let i=2;i<process.argv.length;i+=2)args[process.argv[i].slice(2)]=process.argv[i+1];
if(!args.packages||!args.captures)throw Error('--packages and --captures required');
const source=path.resolve(args.packages),captures=path.resolve(args.captures),out=path.join(root,'android/native/src/androidTest/assets/pilot-test');
const raw=fs.readFileSync(path.join(source,INDEX)),index=JSON.parse(raw),sig=fs.readFileSync(path.join(source,INDEX.replace('.json','.sig')));
if(index.packages.map(x=>x.id).join(',')!=='p002,r002,p003,r003,p004,r004,p005,r005')throw Error('Exactly eight pilot entries required');
if(!crypto.verify('sha256',raw,crypto.createPublicKey({key:Buffer.from(index.publicKey,'base64'),type:'spki',format:'der'}),Buffer.from(sig.toString().trim(),'base64')))throw Error('Invalid index signature');
function copy(from,to){fs.mkdirSync(path.dirname(to),{recursive:true});fs.copyFileSync(from,to);}
copy(path.join(source,INDEX),path.join(out,INDEX));copy(path.join(source,INDEX.replace('.json','.sig')),path.join(out,INDEX.replace('.json','.sig')));
for(const entry of index.packages){const file=path.join(source,entry.manifest),b=fs.readFileSync(file);if(hash(b)!==entry.manifestSha256)throw Error('Manifest mismatch');const m=JSON.parse(b);copy(file,path.join(out,entry.manifest));copy(file.replace('.json','.sig'),path.join(out,entry.manifest.replace('.json','.sig')));copy(path.join(source,m.artifact.path),path.join(out,m.artifact.path));}
for(const id of ['p002','p003','filmcibaba','p005']){
 const dir=path.join(captures,id),requests=JSON.parse(fs.readFileSync(path.join(dir,'requests.json')));
 const first=requests[0];copy(path.join(dir,first.file),path.join(out,id+'/catalog.html'));
 for(const name of ['browse.json','search.json','detail.json','option.json'])copy(path.join(dir,name),path.join(out,id+'/'+name));
 fs.writeFileSync(path.join(out,id+'/catalog-url.txt'),first.request.url);
}
console.log('Prepared eight signed DEX packages and four captured catalog flows for Android tests only');
