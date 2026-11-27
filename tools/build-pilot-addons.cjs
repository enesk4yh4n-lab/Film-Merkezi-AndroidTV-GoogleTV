'use strict';
// Deliberately scoped: cannot regenerate or republish the withdrawn bulk catalog.
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process'),crypto=require('node:crypto');
const {pack,INDEX,hash}=require('./android-distribution.cjs');
const root=path.resolve(__dirname,'..'),args={};
for(let i=2;i<process.argv.length;i+=2){if(!process.argv[i].startsWith('--')||!process.argv[i+1])throw Error('Expected --name value');args[process.argv[i].slice(2)]=process.argv[i+1];}
if(!args.key||!args.out||args.scope!=='p002,p003,p004,p005')throw Error('Required: --key <external key> --out <new pilot directory> --scope p002,p003,p004,p005');
const addons=path.resolve(args.addons||process.env.FM_ADDONS_DIR||path.join(root,'../Android-Eklentiler')),out=path.resolve(args.out);
if(out.toLowerCase()===addons.toLowerCase())throw Error('Build into a separate reviewable pilot directory, never the live repository');
if(fs.existsSync(path.join(out,INDEX)))throw Error('Use a fresh output directory; signed package versions are immutable');
const sdk=process.env.ANDROID_HOME||process.env.ANDROID_SDK_ROOT;if(!sdk)throw Error('ANDROID_HOME required');
const privateKey=crypto.createPrivateKey(fs.readFileSync(args.key)),publicKey=crypto.createPublicKey(privateKey).export({type:'spki',format:'der'}),publisher=hash(publicKey);
const build=path.join(root,'android/native/build/pilot-addons');fs.mkdirSync(build,{recursive:true});
const classes=path.join(build,'classes.jar');
cp.execFileSync('python',['-c','import zipfile,sys;open(sys.argv[2],"wb").write(zipfile.ZipFile(sys.argv[1]).read("classes.jar"))',path.join(addons,'runtime/build/outputs/aar/addonRuntime-debug.aar'),classes]);
const java=process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin',process.platform==='win32'?'java.exe':'java'):'java';
const platform=['android-37.0','android-37'].map(p=>path.join(sdk,'platforms',p,'android.jar')).find(fs.existsSync);if(!platform)throw Error('Android 37 platform required');
const jar=path.join(build,'plugin.jar');
cp.execFileSync(java,['-cp',path.join(sdk,'build-tools/36.1.0/lib/d8.jar'),'com.android.tools.r8.D8','--release','--min-api','23','--lib',platform,'--classpath',path.join(root,'android/native/build/intermediates/compile_app_classes_jar/debug/bundleDebugClassesToCompileJar/classes.jar'),'--classpath',path.join(root,'android/pluginApi/build/libs/pluginApi.jar'),'--output',jar,classes],{stdio:'inherit'});
const definitions=JSON.parse(fs.readFileSync(path.join(addons,'pilot/packages.json'),'utf8'));
if(definitions.map(x=>x.id).join(',')!=='p002,r002,p003,r003,p004,r004,p005,r005')throw Error('Unexpected pilot scope/order');
const provenance=[];
for(const original of definitions){
 const meta=JSON.parse(JSON.stringify(original));meta.dependencies=meta.type==='site'?[{publisher,id:'r'+meta.id.slice(1),minVersion:'1.0.0',maxVersion:'2.0.0'}]:[];
 const file=path.join(build,meta.id+'.json');fs.writeFileSync(file,JSON.stringify(meta,null,2)+'\n');
 pack({key:args.key,out,manifest:file,jar,id:'film-merkezi-android-eklentiler',name:'Film Merkezi Android Eklentiler'});
 if(args.tizen){const dir=path.join(args.tizen,'kaynak',meta.type==='site'?'saglayicilar':'cozumleyiciler',meta.id);const source=JSON.parse(fs.readFileSync(path.join(dir,'addon.json'),'utf8'));provenance.push({id:meta.id,tizenVersion:source.version,files:source.modules.map(name=>({name,sha256:hash(fs.readFileSync(path.join(dir,name)))}))});}
}
const indexFile=path.join(out,INDEX),index=JSON.parse(fs.readFileSync(indexFile));
index.revision=Number(args.revision||index.revision);if(!Number.isSafeInteger(index.revision)||index.revision<1)throw Error('Invalid index revision');
const raw=Buffer.from(JSON.stringify(index,null,2)+'\n');fs.writeFileSync(indexFile,raw);fs.writeFileSync(indexFile.replace('.json','.sig'),crypto.sign('sha256',raw,privateKey).toString('base64')+'\n');
fs.writeFileSync(path.join(out,'pilot-build.json'),JSON.stringify({scope:definitions.map(x=>x.id),publisher,jarBytes:fs.statSync(jar).size,jarSha256:hash(fs.readFileSync(jar)),revision:index.revision,provenance},null,2)+'\n');
console.log(JSON.stringify({packages:index.packages.length,revision:index.revision,jarBytes:fs.statSync(jar).size,publisher}));
