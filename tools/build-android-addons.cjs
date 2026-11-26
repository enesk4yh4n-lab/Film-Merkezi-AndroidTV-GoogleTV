'use strict';
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process'),os=require('node:os');
const root=path.resolve(__dirname,'..'),options={};for(let i=2;i<process.argv.length;i+=2)options[process.argv[i].replace(/^--/,'')]=process.argv[i+1];
const addons=path.resolve(options.addons||path.join(root,'../Android-Eklentiler'));
const sdk=process.env.ANDROID_HOME||process.env.ANDROID_SDK_ROOT;if(!sdk)throw Error('ANDROID_HOME required');
const key=options.key;if(!key)throw Error('--key outside repositories required');
const crypto=require('node:crypto');const privateKey=crypto.createPrivateKey(fs.readFileSync(key));const pub=crypto.createPublicKey(privateKey).export({type:'spki',format:'der'});const fingerprint=crypto.createHash('sha256').update(pub).digest('hex');
const build=path.join(root,'android/native/build/addons');fs.mkdirSync(build,{recursive:true});
const runtime=path.join(addons,'runtime/build/outputs/aar/addonRuntime-debug.aar');
cp.execFileSync('python',['-c','import zipfile,sys;open(sys.argv[2],"wb").write(zipfile.ZipFile(sys.argv[1]).read("classes.jar"))',runtime,path.join(build,'classes.jar')]);
const java=process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin/'+(process.platform==='win32'?'java.exe':'java')):'java';
const jar=path.join(build,'plugin.jar');
const platform=['android-37','android-37.0'].map(n=>path.join(sdk,'platforms',n,'android.jar')).find(p=>fs.existsSync(p));if(!platform)throw Error('Android 37 platform required');
cp.execFileSync(java,['-cp',path.join(sdk,'build-tools/36.1.0/lib/d8.jar'),'com.android.tools.r8.D8','--release','--min-api','23','--lib',platform,'--classpath',path.join(root,'android/native/build/intermediates/compile_app_classes_jar/debug/bundleDebugClassesToCompileJar/classes.jar'),'--classpath',path.join(root,'android/pluginApi/build/libs/pluginApi.jar'),'--output',jar,path.join(build,'classes.jar')],{stdio:'inherit'});
const specs=JSON.parse(fs.readFileSync(path.join(root,'android/native/build/site-specs.json'),'utf8'));
let cdn={};if(options['cdn-registry'])cdn=JSON.parse(fs.readFileSync(options['cdn-registry'],'utf8'));
const common=path.join(addons,'runtime/origins.json');
if(!fs.existsSync(common)){
 const origins=[...Object.values(cdn).flat(),...specs.map(s=>s.base),...['ksdpictures.site','streamcorecdn.com','videoplays.cfd','hotstream.club','hdfilmcehennemi.mobi','dbx.molystream.org','vidmoly.biz','vidmoly.net','vidmoly.me','vidmoly.to','ok.ru','odnoklassniki.ru','my.mail.ru','videoseyred.in','vidara.to','gasobni.com','play2.pilavyerplay.top','play.pilavyerplay.top','ha.vixolity.com','pl.vixolity.com','myplayersvideo.xyz','matrudas.com','player.filmizle.in','p.playturka.space','n.4turk.space','rapidvid.org','rapidvid.net','as9u7bgh9.com','www.youtube.com','youtube.com'].map(h=>'https://'+h)];
 fs.writeFileSync(common,JSON.stringify([...new Set(origins)].sort(),null,2)+'\n');
}
const origins=JSON.parse(fs.readFileSync(common,'utf8'));if(origins.length>256)throw Error('Origin budget');
for(const site of specs){
 for(const type of ['resolver','site']){
  const id=(type==='site'?'site-':'resolver-')+site.id,dir=path.join(addons,'sources',id);fs.mkdirSync(dir,{recursive:true});const file=path.join(dir,'addon.json');
  if(!fs.existsSync(file))fs.writeFileSync(file,JSON.stringify({id,name:site.name+(type==='resolver'?' kaynakları':''),type,version:'1.0.0',minAppVersionCode:36,minSdk:23,entryClass:'app.filmmerkezi.addon.BuiltinEntry',...(type==='site'?{site}:{}),dependencies:type==='site'?[{publisher:fingerprint,id:'resolver-'+site.id,minVersion:'1.0.0',maxVersion:'2.0.0'}]:[],origins},null,2)+'\n');
  const meta=JSON.parse(fs.readFileSync(file,'utf8'));const target=path.join(addons,'packages',id,meta.version,'manifest.json');
  if(fs.existsSync(target))continue;
  cp.execFileSync(process.execPath,[path.join(root,'tools/android-distribution.cjs'),'pack','--key',key,'--manifest',file,'--jar',jar,'--out',addons,'--id','film-merkezi-android-eklentiler','--name','Film Merkezi Android Eklentiler'],{stdio:'pipe'});
 }
}
console.log(JSON.stringify({sites:specs.length,packages:specs.length*2,jarBytes:fs.statSync(jar).size,fingerprint}));
