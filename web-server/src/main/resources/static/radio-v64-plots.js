// V64 additive visualization layer.
// Does not modify the V61/V62/V63/V64 simulation engines or existing HMI controller.
(()=>{'use strict';
const $=id=>document.getElementById(id);
const H={slots:[],ues:{},max:60,lastSignature:''};
const num=v=>Number.isFinite(Number(v))?Number(v):0;

// Additive web-only MIMO request adapter. V21/V28 keep their existing contracts;
// the selectable V64 controls are mapped to the parameters those implementations
// actually consume: effective rank -> layers/ports, TX/RX -> array dimensions,
// and beam-sweep OFF -> the minimum supported beam scan.
const mimoNativeFetch=window.fetch.bind(window);
window.fetch=async(input,init)=>{
  let requestInput=input;
  try{
    const originalUrl=typeof input==='string'?input:input?.url||'';
    if(originalUrl.includes('/api/lab')&&window.__v64MimoState){
      const u=new URL(originalUrl,window.location.href),version=u.searchParams.get('version'),m=window.__v64MimoState;
      if(version==='V21'||version==='V28'){
        const requestedRank=m.rankAdaptive?Math.min(m.tx,m.rx):m.rank;
        const effectiveRank=Math.max(1,Math.min(4,m.tx,m.rx,requestedRank));
        u.searchParams.set('layers',String(effectiveRank));
        u.searchParams.set('tx',String(m.tx));
        u.searchParams.set('rx',String(m.rx));
        if(version==='V28') u.searchParams.set('beams',String(m.beamSweep?Math.max(2,m.beams):2));
        requestInput=u.toString();
      }
    }
  }catch(_){}
  return mimoNativeFetch(requestInput,init);
};
function ensure(){
  const panel=$('panel-radio'); if(!panel||$('v64LivePlots')) return !!$('v64LivePlots');
  const card=document.createElement('div'); card.id='v64LivePlots'; card.className='card v64-live-plots';
  card.innerHTML=`<div class="title"><div><h2>Live Radio Performance</h2><small>Slot history · updated from V63 simulation results</small></div><small id="plotWindow">0 samples</small></div><div class="v64-plot-grid">
  <div class="v64-plot-card"><div class="v64-plot-title">Average SINR <b id="plotSinrValue">—</b></div><canvas id="plotSinr"></canvas></div>
  <div class="v64-plot-card"><div class="v64-plot-title">System Throughput <b id="plotThrValue">—</b></div><canvas id="plotThr"></canvas></div>
  <div class="v64-plot-card"><div class="v64-plot-title">Average BLER <b id="plotBlerValue">—</b></div><canvas id="plotBler"></canvas></div>
  <div class="v64-plot-card"><div class="v64-plot-title">PRB Utilization <b id="plotPrbValue">—</b></div><canvas id="plotPrb"></canvas></div>
  </div><div class="v64-plot-legend"><span>● SINR</span><span>● Throughput</span><span>● BLER</span><span>● PRB utilization</span></div>`;
  const style=document.createElement('style'); style.textContent=`.v64-live-plots{margin-top:10px;padding:12px}.v64-plot-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px;margin-top:10px}.v64-plot-card{height:190px;padding:10px;border:1px solid #123554;border-radius:6px;background:#041522;min-width:0}.v64-plot-title{font-size:11px;color:#9bb0c3;display:flex;justify-content:space-between}.v64-plot-title b{color:#e8f5ff}.v64-plot-card canvas{width:100%;height:145px;display:block;margin-top:5px}.v64-plot-legend{display:flex;gap:18px;flex-wrap:wrap;color:#7891a8;font-size:9px;margin-top:8px}.v64-plot-legend span:first-child{color:#16a9ff}.v64-plot-legend span:nth-child(2){color:#00e994}.v64-plot-legend span:nth-child(3){color:#ff5264}.v64-plot-legend span:nth-child(4){color:#ffb632}@media(max-width:900px){.v64-plot-grid{grid-template-columns:1fr}}`;
  document.head.appendChild(style);
  panel.querySelector('.layout')?.querySelector('.main')?.appendChild(card);
  return true;
}
function push(data){
  const us=data?.ueStates||[],m=data?.metrics||{},avg=k=>us.reduce((a,u)=>a+num(u[k]),0)/Math.max(1,us.length);
  const sample={sinr:avg('meanSinrDb'),thr:num(m.totalThroughputMbps),bler:avg('meanBler')*100,prb:us.reduce((a,u)=>a+num(u.totalAllocatedPrbs),0)/Math.max(1,num(data?.config?.prbs)*num(data?.config?.cells))*100};
  H.slots.push(sample); if(H.slots.length>H.max)H.slots.shift();
  if($('plotWindow'))$('plotWindow').textContent=H.slots.length+' samples';
  if($('plotSinrValue'))$('plotSinrValue').textContent=sample.sinr.toFixed(1)+' dB';
  if($('plotThrValue'))$('plotThrValue').textContent=sample.thr.toFixed(1)+' Mbps';
  if($('plotBlerValue'))$('plotBlerValue').textContent=sample.bler.toFixed(2)+' %';
  if($('plotPrbValue'))$('plotPrbValue').textContent=sample.prb.toFixed(1)+' %';
  draw('plotSinr',H.slots.map(x=>x.sinr),'sinr'); draw('plotThr',H.slots.map(x=>x.thr),'thr'); draw('plotBler',H.slots.map(x=>x.bler),'bler'); draw('plotPrb',H.slots.map(x=>x.prb),'prb');
}
function draw(id,values,type){const c=$(id);if(!c)return;const d=devicePixelRatio||1,w=c.clientWidth,h=c.clientHeight;c.width=w*d;c.height=h*d;const x=c.getContext('2d');x.setTransform(d,0,0,d,0,0);x.clearRect(0,0,w,h);x.fillStyle='#04101b';x.fillRect(0,0,w,h);x.strokeStyle='#123554';x.lineWidth=1;for(let i=1;i<5;i++){const y=i*h/5;x.beginPath();x.moveTo(0,y);x.lineTo(w,y);x.stroke()}if(values.length<1)return;let min=Math.min(...values),max=Math.max(...values);if(type==='bler'){min=0;max=Math.max(1,max)}else if(type==='prb'){min=0;max=100}else if(Math.abs(max-min)<1e-9){min-=1;max+=1}const pad=8,span=Math.max(1,max-min);x.beginPath();values.forEach((v,i)=>{const px=pad+(w-2*pad)*(values.length===1?1:i/(values.length-1));const py=h-pad-(h-2*pad)*(v-min)/span;i?x.lineTo(px,py):x.moveTo(px,py)});x.strokeStyle=type==='sinr'?'#16a9ff':type==='thr'?'#00e994':type==='bler'?'#ff5264':'#ffb632';x.lineWidth=2;x.stroke();values.forEach((v,i)=>{if(i!==values.length-1)return;const px=pad+(w-2*pad)*(values.length===1?1:i/(values.length-1));const py=h-pad-(h-2*pad)*(v-min)/span;x.fillStyle=x.strokeStyle;x.beginPath();x.arc(px,py,3,0,Math.PI*2);x.fill()})}
function observe(){ensure();const fixed=$('throughput');if(!fixed)return setTimeout(observe,300);let last='';const tick=()=>{const main=window.__v64LastData;if(main&&main!==last){last=main;try{push(main)}catch(_){}}else{const root=$('panel-radio');const text=root?.querySelector('#ueRows')?.textContent||'';if(text&&text!==H.lastSignature){H.lastSignature=text;const rows=[...document.querySelectorAll('#ueRows tr')];if(rows.length){const us=rows.map(r=>{const c=r.querySelectorAll('td');return{meanSinrDb:num(c[2]?.textContent),totalAllocatedPrbs:num(c[5]?.textContent),throughputMbps:num(c[6]?.textContent),meanBler:num(c[7]?.textContent)/100}});push({ueStates:us,config:{prbs:num($('prbs')?.value),cells:num($('cells')?.value)},metrics:{totalThroughputMbps:us.reduce((a,u)=>a+u.throughputMbps,0)}})}}}setTimeout(tick,300)};tick()}
function start(){let n=0;const t=setInterval(()=>{if(ensure()){clearInterval(t);observe()}if(++n>100)clearInterval(t)},100)}
start();
})();
