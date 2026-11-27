'use strict';
// Same recorded network bodies, original Tizen modules, Chromium DOM. No live browser navigation.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const args={};for(let i=2;i<process.argv.length;i+=2)args[process.argv[i].slice(2)]=process.argv[i+1];
if(!args.tizen||!args.captures)throw Error('--tizen and --captures required');
const tizen=path.resolve(args.tizen),captures=path.resolve(args.captures);
const {chromium}=require(path.join(tizen,'tools/node_modules/playwright'));
function key(url,fields={}){const u=new URL(url);u.searchParams.sort();return u.href+'|'+JSON.stringify(Object.fromEntries(Object.entries(fields).sort(([a],[b])=>a.localeCompare(b))));}
const fold=s=>s.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/ı/g,'i').replace(/ş/g,'s').replace(/[^a-z0-9]+/g,' ').trim();
const semantic=c=>({url:c.pageUrl||c.ref.pageUrl,type:c.type||c.ref.kind,title:c.title,year:c.year||null,imdb:c.imdb||null});
(async()=>{const browser=await chromium.launch();const report=[];try{
 for(const [code,id,term]of [['p002','p002','Şahsiyet'],['p003','p003','Yeraltı'],['p004','filmcibaba','American Psycho 1'],['p005','p005','Sevdiğim Sensin']]){
  const dir=path.join(captures,id),requests=JSON.parse(fs.readFileSync(path.join(dir,'requests.json'))),responses=new Map(requests.map(r=>[key(r.request.url,r.request.fields),r]));
  const page=await browser.newPage();await page.route('**/*',r=>r.abort());
  const row={id:code,reference:'Original Tizen JS in Chromium, captured bodies; not Tizen device'};
  try{
   await page.exposeFunction('__read',(url,fields)=>{const r=responses.get(key(url,fields));if(!r)throw Error('CAPTURE MISSING '+new URL(url).origin+new URL(url).pathname);if(r.code!==200)throw Error('HTTP '+r.code);return {body:fs.readFileSync(path.join(dir,r.file),'utf8'),url,status:200,method:'fixture'};});
   const androidSearch=JSON.parse(fs.readFileSync(path.join(dir,'search.json'))),base=new URL(androidSearch.cards[0].ref.pageUrl).origin;
   await page.evaluate(({code,base})=>{
    window.__client={get:(u)=>__read(u,{}),post:(u,body)=>__read(u,Object.fromEntries(new URLSearchParams(body))),cancel(){}};
    window.__ctx={site:{id:code,name:code,base,prefix:code+':'},key:'fixture/'+code,createClient:()=>__client,registerResolver:d=>window.__definition=d,createResolver:()=>__definition.create(__ctx,()=>{},Date.now)};
    window.FilmPlugins={context:()=>__ctx};window.SiteRegistry={register:d=>window.__site=d};
   },{code,base});
   for(const file of ['kaynak/uygulama/ortak/katalog/catalog-utils.js','kaynak/uygulama/ortak/player/player-core.js','kaynak/uygulama/vendor/crypto-js.js',`kaynak/cozumleyiciler/r${code.slice(1)}/resolver.js`,`kaynak/saglayicilar/${code}/site.js`])await page.addScriptTag({content:fs.readFileSync(path.join(tizen,file),'utf8')});
   const effectiveTerm=code==='p004'?requests.map(r=>new URL(r.request.url).searchParams.get('s')).find(Boolean)||term:term;
   const result=await page.evaluate(async({term,firstUrl})=>{
    const p=new __site.Provider(),search=await p.search(term);if(!search.cards.length)throw Error('Reference search empty');
    const target=search.cards.find(c=>c.pageUrl===firstUrl)||search.cards[0];const detail=await p.detail(target);
    let sources,sourceError;try{sources=await __definition.create(__ctx,()=>{},Date.now).resolve(detail.episodes&&detail.episodes[0]||detail,Date.now()+60000,()=>{});}catch(e){sourceError=e.message;}
    return {search,detail,sources,sourceError};
   },{term:effectiveTerm,firstUrl:JSON.parse(fs.readFileSync(path.join(dir,'detail.json'))).card.ref.pageUrl});
   fs.writeFileSync(path.join(dir,'tizen-reference.json'),JSON.stringify(result,null,2));
   const expected=androidSearch.cards.map(semantic),actual=result.search.cards.map(semantic);
   row.searchExact=JSON.stringify(expected)===JSON.stringify(actual);row.androidSearch=expected.length;row.tizenSearch=actual.length;
   if(!row.searchExact)row.searchDifferences={android:expected,tizen:actual};
   const androidDetail=JSON.parse(fs.readFileSync(path.join(dir,'detail.json'))),aEpisodes=androidDetail.episodes.map(e=>({url:e.card.ref.pageUrl,season:e.season,number:e.number})),tEpisodes=(result.detail.episodes||[]).map(e=>({url:e.pageUrl,season:e.episode.season,number:e.episode.number}));
   row.episodesExact=JSON.stringify(aEpisodes)===JSON.stringify(tEpisodes);row.androidEpisodes=aEpisodes.length;row.tizenEpisodes=tEpisodes.length;
   if(!row.episodesExact)row.episodeDifferences={android:aEpisodes,tizen:tEpisodes};
   const androidSource=JSON.parse(fs.readFileSync(path.join(dir,'source.json')));row.sourceExact=!!result.sources?.some(s=>s.url===androidSource.url&&s.referer===androidSource.referer&&s.type===androidSource.type);
   if(result.sourceError)row.sourceError=result.sourceError;
   row.passed=row.searchExact&&row.episodesExact&&row.sourceExact;
  }catch(e){row.error=e.message;row.passed=false;}finally{await page.close();report.push(row);}
 }
}finally{await browser.close();fs.writeFileSync(path.join(captures,'tizen-comparison.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report.map(({searchDifferences,episodeDifferences,...r})=>r),null,2));if(report.some(r=>!r.passed))process.exitCode=1;}})().catch(e=>{console.error(e);process.exitCode=1;});
