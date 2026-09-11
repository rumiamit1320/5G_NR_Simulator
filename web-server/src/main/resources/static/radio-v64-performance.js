// V64 additive live-performance presentation layer.
// Replaces only the Radio Environment plot rendering; V61/V62/V63/V64 engines remain unchanged.
(() => {
  'use strict';
  const MAX = 60;
  const history = [];
  const $ = id => document.getElementById(id);
  const N = (v,d=0) => Number.isFinite(Number(v)) ? Number(v) : d;
  const pct = v => Math.max(0, Math.min(100, N(v) * 100));
  const avg = (us,key) => us.reduce((a,u)=>a+N(typeof key==='function'?key(u):u[key]),0)/Math.max(1,us.length);

  function sample(data) {
    if (!data || !Array.isArray(data.ueStates)) return;
    const us = data.ueStates;
    const cfg = data.config || {};
    const m = data.metrics || {};
    const slots = Math.max(1,N(cfg.slots,1));
    const cells = Math.max(1,N(cfg.cells,1));
    const prbs = Math.max(1,N(cfg.prbs,1));
    const allocated = us.reduce((a,u)=>a+N(u.totalAllocatedPrbs ?? u.allocatedPrbs),0);
    history.push({
      t:new Date(),
      throughput:N(m.totalThroughputMbps),
      sinr:avg(us,u=>u.meanSinrDb ?? u.sinrDb),
      bler:pct(avg(us,u=>u.meanBler ?? u.bler)),
      prb:Math.max(0,Math.min(100,allocated/(prbs*cells*slots)*100))
    });
    while(history.length>MAX) history.shift();
  }

  // The existing HMI uses DIV containers for these four chart IDs.
  // Create a real canvas inside each container instead of assuming the container itself is a canvas.
  function canvasFor(id) {
    const host=$(id);
    if(!host)return null;
    if(host instanceof HTMLCanvasElement)return host;
    let c=host.querySelector('canvas[data-v64-performance-canvas]');
    if(!c){
      host.innerHTML='';
      c=document.createElement('canvas');
      c.setAttribute('data-v64-performance-canvas','1');
      c.style.width='100%';
      c.style.height='150px';
      c.style.display='block';
      host.appendChild(c);
    }
    return c;
  }

  function resizeCanvas(c) {
    if (!c || !(c instanceof HTMLCanvasElement)) return null;
    const d=window.devicePixelRatio||1, r=c.getBoundingClientRect();
    const w=Math.max(180,r.width), h=Math.max(110,r.height);
    if(c.width!==Math.round(w*d)||c.height!==Math.round(h*d)){c.width=Math.round(w*d);c.height=Math.round(h*d)}
    const x=c.getContext('2d');
    if(!x)return null;
    x.setTransform(d,0,0,d,0,0);
    return {x,w,h};
  }

  function draw(id,key,label,unit,min,max) {
    const c=canvasFor(id); if(!c)return;
    const q=resizeCanvas(c); if(!q)return; const {x,w,h}=q;
    x.clearRect(0,0,w,h); x.fillStyle='#04101b'; x.fillRect(0,0,w,h);
    const pad={l:42,r:12,t:18,b:25}, pw=w-pad.l-pad.r, ph=h-pad.t-pad.b;
    const vals=history.map(z=>N(z[key]));
    let lo=min, hi=max;
    if(min===null||max===null){
      const finite=vals.filter(Number.isFinite), a=finite.length?Math.min(...finite):0,b=finite.length?Math.max(...finite):1;
      const span=Math.max(.5,b-a); lo=Math.max(0,a-span*.15); hi=b+span*.15;
      if(key==='throughput') lo=0;
    }
    if(hi<=lo)hi=lo+1;
    x.strokeStyle='#15344d';x.lineWidth=1;
    for(let i=0;i<=4;i++){const yy=pad.t+ph*i/4;x.beginPath();x.moveTo(pad.l,yy);x.lineTo(w-pad.r,yy);x.stroke();x.fillStyle='#6f879b';x.font='9px system-ui';x.textAlign='right';x.fillText((hi-(hi-lo)*i/4).toFixed(key==='throughput'?0:1),pad.l-6,yy+3)}
    x.fillStyle='#7f9ab1';x.font='9px system-ui';x.textAlign='left';x.fillText(label,pad.l,pad.t-6);x.textAlign='right';x.fillText(unit,w-pad.r,pad.t-6);
    if(vals.length>1){x.beginPath();vals.forEach((v,i)=>{const xx=pad.l+pw*i/(vals.length-1),yy=pad.t+ph*(1-(v-lo)/(hi-lo));i?x.lineTo(xx,yy):x.moveTo(xx,yy)});x.strokeStyle='#16a5ff';x.lineWidth=2;x.stroke();
      const last=vals[vals.length-1],xx=w-pad.r,yy=pad.t+ph*(1-(last-lo)/(hi-lo));x.fillStyle='#dff5ff';x.beginPath();x.arc(xx,yy,3,0,Math.PI*2);x.fill();
    } else {x.fillStyle='#587187';x.font='10px system-ui';x.textAlign='center';x.fillText('Waiting for live samples…',pad.l+pw/2,pad.t+ph/2)}
    x.fillStyle='#587187';x.font='8px system-ui';x.textAlign='left';x.fillText(history.length?'-'+Math.max(0,history.length-1)+' samples':'',pad.l,h-7);x.textAlign='right';x.fillText('now',w-pad.r,h-7);
  }

  function render() {
    draw('chartThroughput','throughput','THROUGHPUT','Mbps',0,null);
    draw('chartSinr','sinr','AVERAGE SINR','dB',null,null);
    draw('chartBler','bler','AVERAGE BLER','%',0,100);
    draw('chartPrb','prb','PRB UTILIZATION','%',0,100);
  }

  function reset() { history.length=0; render(); }
  function ingest(data){sample(data);render()}
  window.__v64Performance = {ingest,reset,render,history};

  const style=document.createElement('style');
  style.textContent=`#chartThroughput,#chartSinr,#chartBler,#chartPrb{width:100%;height:150px;display:block;background:#04101b;border-radius:4px}.perf-live-caption{font-size:9px;color:#6f879b;margin-top:5px}`;
  document.head.appendChild(style);

  function hook() {
    const d=window.__v64LastData;
    if(d && window.__v64PerformanceLast!==d){window.__v64PerformanceLast=d;ingest(d)}
  }
  const timer=setInterval(hook,250);
  window.addEventListener('resize',render);
  const resetButton=$('reset'); if(resetButton)resetButton.addEventListener('click',()=>setTimeout(reset,0));
  setTimeout(render,100);
})();