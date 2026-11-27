'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process');
const {validateManifest}=require('../tools/android-distribution.cjs');
const root=path.resolve(__dirname,'..'),addons=path.resolve(process.env.FM_ADDONS_DIR||path.join(root,'../Android-Eklentiler'));
test('pilot codes and order match Tizen; each provider has exactly its own resolver',()=>{
 const items=JSON.parse(fs.readFileSync(path.join(addons,'pilot/packages.json'),'utf8'));
 assert.deepEqual(items.map(x=>x.id),['p002','r002','p003','r003','p004','r004','p005','r005']);
 for(const meta of items){assert.equal(meta.minAppVersionCode,38);assert.equal(meta.minSdk,23);assert.equal(meta.version,'1.0.0');validateManifest({...meta,dependencies:meta.type==='site'?[{publisher:'a'.repeat(64),id:'r'+meta.id.slice(1),minVersion:'1.0.0',maxVersion:'2.0.0'}]:[]});}
 assert.equal(items.find(x=>x.id==='p004').site.id,'filmcibaba','Old library/source IDs stay usable');
});
test('pilot builder refuses a missing/expanded scope before doing any build work',()=>{
 for(const args of [[],['--key','unused','--out','unused','--scope','p002,p003,p004,p005,p006']]){
  const r=cp.spawnSync(process.execPath,[path.join(root,'tools/build-pilot-addons.cjs'),...args],{encoding:'utf8'});
  assert.notEqual(r.status,0);assert.match(r.stderr,/Required:/);
 }
});
test('pilot builder refuses the live repository as build output',()=>{
 const r=cp.spawnSync(process.execPath,[path.join(root,'tools/build-pilot-addons.cjs'),'--key','unused','--out',addons,'--scope','p002,p003,p004,p005'],{encoding:'utf8'});
 assert.notEqual(r.status,0);assert.match(r.stderr,/never the live repository/);
});
