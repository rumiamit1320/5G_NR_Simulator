const $=id=>document.getElementById(id);
let live=false,timer=null;
const num=(id,d)=>Number($(id).value||d);
function esc(v){return String(v).replace(/[&<>\"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;'}[c]));}
function pct(v){return (100*Number(v||0)).toFixed(2)+'%';}
function renderMap(n,cells){
 const map=$('map');map.querySelectorAll('.node').forEach(e=>e.remove());
 const positions=[];
 for(let i=0;i<cells;i++){const a=(2*Math.PI*i/Math.max(1,cells))-Math.PI/2;positions.push([50+31*Math.cos(a),50+31*Math.sin(a)]);}
 positions.forEach((p,i)=>{const e=document.createElement('div');e.className='node cell';e.style.left=p[0]+'%';e.style.top=p[1]+'%';e.textContent='gNB'+(i+1);map.appendChild(e);});
 n.forEach((u,i)=>{const angle=(2*Math.PI*i/Math.max(1,n.length))+0.25;const radius=16+10*((i%3)/2);const e=document.createElement('div');e.className='node ue';e.style.left=(50+radius*Math.cos(angle))+'%';e.style.top=(50+radius*Math.sin(angle))+'%';e.title=`UE ${u.ueId}: SINR ${Number(u.sinrDb).toFixed(1)} dB, CQI ${Number(u.cqi).toFixed(1)}`;e.textContent=u.ueId;map.appendChild(e);});
}
function render(r){
 $('throughput').textContent=Number(r.metrics.totalThroughputMbps).toFixed(3)+' Mbps';
 $('fairness').textContent=Number(r.metrics.systemFairness).toFixed(4);
 $('crc').textContent=pct(r.metrics.phyCrcPassRate);
 $('ber').textContent=Number(r.metrics.phyBer).toExponential(3);
 $('status').textContent=`${r.config.slots} slots · ${r.config.ueCount} UEs · ${r.config.cells} cells · ${r.config.prbs} PRBs`;
 $('rows').innerHTML=r.ueStates.map(u=>`<tr><td>UE ${esc(u.ueId)}</td><td>${Number(u.sinrDb).toFixed(2)}</td><td>${Number(u.cqi).toFixed(2)}</td><td>${Number(u.mcs).toFixed(2)}</td><td>—</td><td>${u.allocatedPrbs}</td><td>${Number(u.throughputMbps).toFixed(3)}</td><td>${pct(u.bler)}</td><td class="${Number(u.phyCrcPassRate)>=.5?'good':'bad'}">${pct(u.phyCrcPassRate)}</td><td>${Number(u.phyBer).toExponential(2)}</td></tr>`).join('');
 renderMap(r.ueStates,r.config.cells);
}
async function run(){
 $('status').textContent='Running V62/V63…';
 const p=new URLSearchParams({version:'V63',slots:num('slots',20),ue:num('ue',8),cells:num('cells',3),prbs:num('prbs',52),scs:num('scs',30),velocity:num('velocity',30),payloadBits:128,tx:4,rx:4,layers:1});
 try{const r=await fetch('/api/lab?'+p.toString(),{cache:'no-store'}).then(x=>x.json());if(!r.ok)throw Error(r.error||'simulation failed');render(r);}catch(e){$('status').textContent='Error: '+e.message;console.error(e);}
}
$('run').onclick=run;
$('auto').onclick=()=>{live=!live;$('auto').textContent='Live: '+(live?'ON':'OFF');if(live){run();timer=setInterval(run,1500);}else{clearInterval(timer);timer=null;}};
run();
