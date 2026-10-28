const {chromium}=require('playwright');
(async()=>{const b=await chromium.launch();const p=await b.newPage();
await p.goto('file://'+process.argv[2]+'/pendon.html');await p.evaluate(()=>document.fonts.ready);
const over=await p.evaluate(()=>{const z=document.querySelector('.zona');return [z.scrollHeight,z.clientHeight]});console.log('zona',over);
await p.pdf({path:process.argv[2]+'/capa.pdf',width:'80cm',height:'180cm',printBackground:true,preferCSSPageSize:true});await b.close();})();
