const $=id=>document.getElementById(id);
let live=false,timer=null;
const num=(id,d)=>Number($(id).value||d);
function esc(v){return String(v).replace(/[&<>\"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;'}[c]));}
function pct(v){return (100*Number(v||0)).toFixed(2)+'%';}
function renderMap(n,cells){
 const map=$('map');map.querySelectorAll('.node,.link').forEach(e=>e.remove());
 const positions=[];
 for(let i=0;i<cells;i++){const a=(2*Math.PI*i/Math.max(1,cells))-Math.PI/2;positions.push([50+31*Math.cos(a),50+31*Math.sin(a)]);}
 positions.forEach((p,i)=>{const e=document.createElement('div');e.className='node cell';e.style.left=p[0]+'%';e.style.top=p[1]+'%';e.textContent='gNB'+(i+1);map.appendChild(e);});
 n.forEach((u,i)=>{const angle=(2*Math.PI*i/Math.max(1,n.length))+0.25;const radius=16+10*((i%3)/2);const x=50+radius*Math.cos(angle),y=50+radius*Math.sin(angle);const e=document.createElement('div');e.className='node ue';e.style.left=x+'%';e.style.top=y+'%';e.title=`UE ${u.ueId}: SINR ${Number(u.sinrDb).toFixed(1)} dB, CQI ${Number(u.cqi).toFixed(1)}`;e.textContent=u.ueId;map.appendChild(e);});
}
function bars(id,values,max){const e=$(id);if(!e)return;e.innerHTML=values.slice(0,12).map(v=>`<i style="height:${Math.max(5,Math.min(100,100*Number(v||0)/Math.max(max,1)))}%"></i>`).join('');}
function render(r){
 $('throughput').textContent=Number(r.metrics.totalThroughputMbps).toFixed(3)+' Mbps';
 $('fairness').textContent=Number(r.metrics.systemFairness).toFixed(4);
 $('crc').textContent=pct(r.metrics.phyCrcPassRate);
 $('ber').textContent=Number(r.metrics.phyBer).toExponential(3);
 if($('activeUes'))$('activeUes').textContent=r.ueStates.length;
 $('status').textContent=`${r.config.slots} slots · ${r.config.ueCount} UEs · ${r.config.cells} cells · ${r.config.prbs} PRBs`;
 if($('topStatus'))$('topStatus').innerHTML='<i></i> RUNNING';
 $('rows').innerHTML=r.ueStates.map(u=>`<tr><td>UE ${esc(u.ueId)}</td><td>${Number(u.sinrDb).toFixed(2)}</td><td>${Number(u.cqi).toFixed(2)}</td><td>${Number(u.mcs).toFixed(2)}</td><td>—</td><td>${u.allocatedPrbs}</td><td>${Number(u.throughputMbps).toFixed(3)}</td><td>${pct(u.bler)}</td><td class="${Number(u.phyCrcPassRate)>=.5?'good':'bad'}">${pct(u.phyCrcPassRate)}</td><td>${Number(u.phyBer).toExponential(2)}</td></tr>`).join('');
 renderMap(r.ueStates,r.config.cells);
 const sinr=r.ueStates.map(u=>Number(u.sinrDb)),thr=r.ueStates.map(u=>Number(u.throughputMbps)),prb=r.ueStates.map(u=>Number(u.allocatedPrbs)),bler=r.ueStates.map(u=>Number(u.bler));
 bars('chartThroughput',thr,Math.max(...thr,1));bars('chartSinr',sinr.map(x=>Math.max(0,x)),Math.max(...sinr.map(x=>Math.max(0,x)),1));bars('chartPrb',prb,Math.max(...prb,1));bars('chartBler',bler,Math.max(...bler,1));
 if($('dSlots'))$('dSlots').textContent=r.config.slots;
 if($('dUes'))$('dUes').textContent=r.config.ueCount;
 if($('dCells'))$('dCells').textContent=r.config.cells;
 if($('dPrbs'))$('dPrbs').textContent=r.config.prbs;
 if($('meanSinr'))$('meanSinr').textContent=(sinr.reduce((a,b)=>a+b,0)/Math.max(1,sinr.length)).toFixed(2)+' dB';
 if($('meanCqi'))$('meanCqi').textContent=(r.ueStates.reduce((a,u)=>a+Number(u.cqi),0)/Math.max(1,r.ueStates.length)).toFixed(2);
}
async function run(){
 $('status').textContent='Running V62/V63…';
 const p=new URLSearchParams({version:'V63',slots:num('slots',20),ue:num('ue',8),cells:num('cells',3),prbs:num('prbs',52),scs:num('scs',30),velocity:num('velocity',30),payloadBits:128,tx:4,rx:4,layers:1});
 try{const r=await fetch('/api/lab?'+p.toString(),{cache:'no-store'}).then(x=>x.json());if(!r.ok)throw Error(r.error||'simulation failed');render(r);}catch(e){$('status').textContent='Error: '+e.message;if($('topStatus'))$('topStatus').innerHTML='<i></i> ERROR';console.error(e);}
}
function ensureLiveButton(){
 let b=$('auto');if(b)return b;
 b=document.createElement('button');b.id='auto';b.className='button';b.textContent='LIVE: OFF';
 const actions=document.querySelector('.actions');if(actions)actions.insertBefore(b,actions.firstChild);else document.body.appendChild(b);
 return b;
}
const auto=ensureLiveButton();
auto.onclick=()=>{live=!live;auto.textContent='LIVE: '+(live?'ON':'OFF');if(live){run();timer=setInterval(run,1500);}else{clearInterval(timer);timer=null;}};
$('run').onclick=run;
const syncOutputs=()=>{['slots','ue','cells','prbs','velocity'].forEach(id=>{const e=$(id),o=$(id+'Out');if(e&&o)o.textContent=id==='velocity'?e.value+' km/h':e.value;});};
['slots','ue','cells','prbs','velocity'].forEach(id=>$(id)?.addEventListener('input',syncOutputs));
syncOutputs();
run();
