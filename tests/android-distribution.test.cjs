'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),os=require('node:os'),crypto=require('node:crypto');
const {validateManifest,validateJar,pack,INDEX}=require('../tools/android-distribution.cjs');
const fixture=path.resolve(__dirname,'../android/native/src/androidTest/assets/independent-test');
const jar=path.join(fixture,'packages/independent-example/1.0.0/plugin.jar');
const metadata=()=>({id:'sdk-test',version:'1.0.0',name:'SDK test',type:'resolver',minAppVersionCode:36,minSdk:23,entryClass:'app.filmmerkezi.addon.Example',dependencies:[],origins:['https://example.com']});
test('SDK rejects traversal, invalid ranges and open network declarations',()=>{
 for(const patch of [{id:'../bad'},{version:'1.0.0/../../'},{entryClass:'java.lang.Runtime'},{origins:['https://*']},{dependencies:[{publisher:'0'.repeat(64),id:'resolver',minVersion:'2.0.0',maxVersion:'1.0.0'}]}])assert.throws(()=>validateManifest({...metadata(),...patch}));
 validateManifest(metadata());
});
test('site category declarations accept bounded same-origin paths',()=>{
 const site={id:'example-site',name:'Example site',base:'https://example.com'};
 const validate=catalogSections=>validateManifest({...metadata(),type:'site',site:{...site,catalogSections}});
 validate({ANIMATION:[{path:'/animation/'}],DRAMA_SHORT:[{path:'/shows/',kinds:['SERIES'],pageParameter:'page'}]});
 validate({});
 for(const route of [{path:'//another.example/'},{path:'/../shows'},{path:'/shows/?page=1'},{path:'/shows/',kinds:['EPISODE']},{path:'/shows/',pageParameter:'a&b'}])
  assert.throws(()=>validate({ANIMATION:[route]}),/catalog/);
 assert.throws(()=>validate({ANIMATION:[{path:'/shows/'},{path:'/shows'}]}),/Duplicate/);
 assert.throws(()=>validate({MOVIES:[{path:'/movies/'}]}),/catalog/);
});
test('DEX archive budget and paths are checked before signing',()=>{
 const valid=fs.readFileSync(jar);validateJar(valid);
 assert.throws(()=>validateJar(Buffer.alloc(4194305)));assert.throws(()=>validateJar(Buffer.from('not-a-dex')));
 const unsafe=Buffer.from(valid.toString('latin1').replaceAll('classes.dex','../evil.dex'),'latin1');assert.throws(()=>validateJar(unsafe));
});
test('publisher key pin, immutable versions and exact-byte signatures',()=>{
 const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'fm-distribution-test-'));
 try{
  const pair=crypto.generateKeyPairSync('rsa',{modulusLength:2048}),key=path.join(tmp,'publisher.pem');fs.writeFileSync(key,pair.privateKey.export({format:'pem',type:'pkcs8'}));
  const manifest=path.join(tmp,'addon.json'),out=path.join(tmp,'repo');fs.writeFileSync(manifest,JSON.stringify(metadata()));
  pack({key,manifest,jar,out});assert.throws(()=>pack({key,manifest,jar,out}),/immutable/);
  const indexFile=path.join(out,INDEX),raw=fs.readFileSync(indexFile),index=JSON.parse(raw);assert.equal(index.packages.length,1);
  assert(crypto.verify('sha256',raw,pair.publicKey,Buffer.from(fs.readFileSync(indexFile.replace('.json','.sig'),'utf8'),'base64')));
  fs.appendFileSync(indexFile,' ');fs.writeFileSync(manifest,JSON.stringify({...metadata(),version:'1.0.1'}));assert.throws(()=>pack({key,manifest,jar,out}),/signature/);
  assert(!fs.existsSync(path.join(out,'packages/sdk-test/1.0.1')));
 }finally{assert(tmp.startsWith(path.join(os.tmpdir(),'fm-distribution-test-')));fs.rmSync(tmp,{recursive:true,force:true});}
});
