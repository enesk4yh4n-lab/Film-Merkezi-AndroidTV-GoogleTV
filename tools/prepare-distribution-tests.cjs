'use strict';
// Builds an independent SDK-only publisher fixture. No production private key is used.
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),crypto=require('node:crypto'),cp=require('node:child_process');
const {pack}=require('./android-distribution.cjs');
const root=path.resolve(__dirname,'..'),temp=fs.mkdtempSync(path.join(os.tmpdir(),'fm-publisher-test-'));
const sdk=process.env.ANDROID_HOME||process.env.ANDROID_SDK_ROOT;
const platform=path.join(sdk,'platforms/android-37.0/android.jar');
const api=path.join(root,'android/pluginApi/build/libs/pluginApi.jar');
const java=process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin/'+(process.platform==='win32'?'java.exe':'java')):'java';
const javac=process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin/'+(process.platform==='win32'?'javac.exe':'javac')):'javac';
const jar=process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin/'+(process.platform==='win32'?'jar.exe':'jar')):'jar';
const source=path.join(temp,'Independent.java'),classes=path.join(temp,'classes');fs.mkdirSync(classes);
fs.writeFileSync(source,`package app.filmmerkezi.addon;
import app.filmmerkezi.tv.pluginapi.*;
import org.json.JSONObject;
public final class Independent implements Plugin {
 public String call(String operation,String payload,PluginHost host)throws Exception {
  JSONObject value=new JSONObject(payload);
  if(value.optBoolean("spin")){long n=0;while(true){n++;if(n==Long.MAX_VALUE)n=0;}}
  if(value.optBoolean("typedFailure"))throw new PluginException("ACCESS","Publisher test","publisher",403);
  return new JSONObject().put("sdk",1).put("independent",true).toString();
 }
}`);
cp.execFileSync(javac,['-source','8','-target','8','-cp',api+path.delimiter+platform,'-d',classes,source],{stdio:'inherit'});
cp.execFileSync(jar,['cf',path.join(temp,'classes.jar'),'-C',classes,'.']);
const artifact=path.join(temp,'plugin.jar');
cp.execFileSync(java,['-cp',path.join(sdk,'build-tools/36.1.0/lib/d8.jar'),'com.android.tools.r8.D8','--release','--min-api','23','--lib',platform,'--classpath',api,'--output',artifact,path.join(temp,'classes.jar')],{stdio:'inherit'});
const key=path.join(temp,'publisher.pem');const pair=crypto.generateKeyPairSync('rsa',{modulusLength:2048});
fs.writeFileSync(key,pair.privateKey.export({format:'pem',type:'pkcs8'}));
const manifest=path.join(temp,'addon.json');fs.writeFileSync(manifest,JSON.stringify({id:'independent-example',version:'1.0.0',type:'site',name:'Independent SDK fixture',minAppVersionCode:36,minSdk:23,entryClass:'app.filmmerkezi.addon.Independent',site:{id:'independent-example',name:'Independent fixture',base:'https://example.com'},dependencies:[],origins:['https://example.com']}));
const out=path.join(root,'android/native/src/androidTest/assets/independent-test');
if(fs.existsSync(out))throw Error('Fixture exists: do not silently replace signed test history');
console.log(JSON.stringify(pack({key,manifest,jar:artifact,out,id:'independent-test',name:'SDK fixture publisher'})));
// Test-only ephemeral key is outside the checkout; remove only our verified temporary subtree.
if(!temp.startsWith(path.join(os.tmpdir(),'fm-publisher-test-')))throw Error('Temporary path check');
fs.rmSync(temp,{recursive:true,force:true});
