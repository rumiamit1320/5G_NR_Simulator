(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const state = { data:null, selected:0, running:false, timer:null, logs:[] };
  const esc = v => String(v ?? '').replace(/[&<>\"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;'}[c]));
  const n = (v,d=0) => Number.isFinite(Number(v)) ? Number(v) : d;
  const pct = v => (100*n(v)).toFixed(1)+' %';
  const api = async (version, params={}) => {
    const q = new URLSearchParams({version, ...params});
    const r = await fetch('/api/lab?'+q.toString(), {cache:'no-store'});
    const j = await r.json();
    if(!j.ok) throw new Error(j.error || 'API request failed');
    return j;
  };
  const log = (message, level='INFO') => {
    state.logs.unshift({time:new Date().toLocaleTimeString(), level, message});
    state.logs = state.logs.slice(0,200);
    renderLogs();
  };
  function controls(){
    return {slots:+$('slots').value, ue:+$('ue').value, cells:+$('cells').value, prbs:+$('prbs').value, scs:+$('scs').value, velocity:+$('velocity').value};
  }
  function syncControls(){
    [['slots','slotsOut',''],['ue','ueOut',''],['cells','cellsOut',''],['prbs','prbsOut',''],['scs','scsOut',' kHz'],['velocity','velocityOut',' km/h']].forEach(([a,b,s])=>$(b).textContent=$(a).value+s);
  }
  function renderBars(id, values, suffix=''){
    const el=$(id); if(!el) return;
    const a=values.map(n); const max=Math.max(...a,1);
    el.innerHTML=a.map((v,i)=>`<div class="bar-col"><i style="height:${Math.max(3,Math.min(100,100*v/max))}%"></i><small>UE${i+1}</small><em>${v.toFixed(v<1?2:1)}${suffix}</em></div>`).join('');
  }
  function renderMap(ues,cells){
    const canvas=$('topologyCanvas'); if(!canvas) return;
    const ctx=canvas.getContext('2d'); const w=canvas.width=canvas.clientWidth*devicePixelRatio; const h=canvas.height=canvas.clientHeight*devicePixelRatio; ctx.scale(devicePixelRatio,devicePixelRatio);
    const W=canvas.clientWidth,H=canvas.clientHeight; ctx.clearRect(0,0,W,H);
    ctx.fillStyle='#07111d';ctx.fillRect(0,0,W,H);
    ctx.strokeStyle='rgba(46,100,145,.28)';ctx.lineWidth=1;
    for(let x=40;x<W;x+=58){ctx.beginPath();ctx.moveTo(x,0);ctx.lineTo(x,H);ctx.stroke()}
    for(let y=32;y<H;y+=52){ctx.beginPath();ctx.moveTo(0,y);ctx.lineTo(W,y);ctx.stroke()}
    const cellPos=[]; const cx=W/2,cy=H/2,rad=Math.min(W,H)*.29;
    for(let i=0;i<cells;i++){const a=-Math.PI/2+i*2*Math.PI/cells;cellPos.push({x:cx+rad*Math.cos(a),y:cy+rad*Math.sin(a)});}
    cellPos.forEach((p,i)=>{ctx.beginPath();ctx.arc(p.x,p.y,Math.min(W,H)*.22,0,Math.PI*2);ctx.setLineDash([7,7]);ctx.strokeStyle=i%2?'rgba(0,153,255,.75)':'rgba(0,235,130,.7)';ctx.stroke();ctx.setLineDash([]);});
    const points=ues.map((u,i)=>{const p=cellPos[i%cellPos.length];const a=i*2.399;const rr=Math.min(W,H)*(.18+.035*(i%3));return {x:p.x+rr*Math.cos(a),y:p.y+rr*Math.sin(a),u};});
    points.forEach((p,i)=>{const cell=cellPos[i%cellPos.length];ctx.beginPath();ctx.moveTo(cell.x,cell.y);ctx.lineTo(p.x,p.y);ctx.setLineDash([5,6]);ctx.strokeStyle='rgba(129,191,241,.35)';ctx.stroke();ctx.setLineDash([]);});
    cellPos.forEach((p,i)=>{ctx.shadowBlur=18;ctx.shadowColor='#ff4757';ctx.fillStyle='#ff5362';ctx.beginPath();ctx.arc(p.x,p.y,10,0,Math.PI*2);ctx.fill();ctx.shadowBlur=0;ctx.fillStyle='#f5f8ff';ctx.font='700 12px Inter,system-ui';ctx.textAlign='center';ctx.fillText('gNB '+(i+1),p.x,p.y+31);});
    points.forEach((p,i)=>{ctx.shadowBlur=13;ctx.shadowColor='#009dff';ctx.fillStyle=i===state.selected?'#ffffff':'#078ff0';ctx.beginPath();ctx.arc(p.x,p.y,i===state.selected?9:7,0,Math.PI*2);ctx.fill();ctx.shadowBlur=0;ctx.fillStyle='#dbeeff';ctx.font='600 11px Inter,system-ui';ctx.textAlign='left';ctx.fillText('UE'+p.u.ueId,p.x+11,p.y+4);});
    canvas._points=points;
  }
  function renderSelected(){
    const u=(state.data?.ueStates||[])[state.selected] || (state.data?.ueStates||[])[0]; if(!u) return;
    $('selectedUe').value='UE '+u.ueId;
    const items=[['Serving Cell','gNB '+((u.ueId-1)%Math.max(1,n(state.data.config.cells,1))+1)],['Position','dynamic V62 geometry'],['Velocity',n(state.data.config.velocityKmh).toFixed(1)+' km/h'],['Mean SINR',n(u.meanSinrDb).toFixed(2)+' dB'],['Mean CQI',n(u.meanCqi).toFixed(1)],['Mean MCS',n(u.meanMcs).toFixed(1)],['MIMO Rank','adaptive'],['Allocated PRBs',n(u.totalAllocatedPrbs).toFixed(0)],['Throughput',n(u.throughputMbps).toFixed(2)+' Mbps'],['BLER',pct(u.meanBler)],['CRC Pass Rate',pct(u.phyCrcPassRate)],['BER',n(u.phyBer).toExponential(2)],['HARQ Status',u.meanBler<.1?'ACK':'NACK']];
    $('selectedDetails').innerHTML=items.map(x=>`<div><span>${x[0]}</span><b class="${x[0].includes('Rate')||x[0]=='Throughput'||x[0]=='Mean SINR'?'good':''}">${esc(x[1])}</b></div>`).join('');
  }
  function renderTable(){
    const rows=$('ueRows'); if(!rows) return;
    rows.innerHTML=(state.data?.ueStates||[]).map((u,i)=>`<tr class="${i===state.selected?'selected-row':''}" data-i="${i}"><td>${u.ueId}</td><td>gNB ${((u.ueId-1)%Math.max(1,n(state.data.config.cells,1))+1)}</td><td>${n(u.meanSinrDb).toFixed(1)}</td><td>${n(u.meanCqi).toFixed(1)}</td><td>${n(u.meanMcs).toFixed(1)}</td><td>${n(u.totalAllocatedPrbs).toFixed(0)}</td><td>${n(u.throughputMbps).toFixed(1)}</td><td class="${u.meanBler<.03?'good':'bad'}">${pct(u.meanBler)}</td><td class="${u.phyCrcPassRate>=.95?'good':'bad'}">${pct(u.phyCrcPassRate)}</td><td>${n(u.phyBer).toExponential(2)}</td><td class="${u.meanBler<.03?'good':'bad'}">${u.meanBler<.03?'ACK':'NACK'}</td></tr>`).join('');
    rows.querySelectorAll('tr').forEach(tr=>tr.onclick=()=>{state.selected=+tr.dataset.i;renderAll();});
  }
  function renderAll(){
    const r=state.data; if(!r) return; const m=r.metrics, us=r.ueStates||[];
    $('throughput').textContent=(n(m.totalThroughputMbps)/1000).toFixed(2)+' Gbps';
    $('spectral').textContent=(n(m.totalThroughputMbps)/Math.max(1,n(r.config.prbs)*n(r.config.cells)*.18)).toFixed(2)+' bps/Hz';
    $('fairness').textContent=n(m.systemFairness).toFixed(2);
    $('bler').textContent=pct(us.reduce((a,u)=>a+n(u.meanBler),0)/Math.max(1,us.length));
    $('crc').textContent=pct(m.phyCrcPassRate); $('avgSinr').textContent=(us.reduce((a,u)=>a+n(u.meanSinrDb),0)/Math.max(1,us.length)).toFixed(1)+' dB';
    $('activeUes').textContent=us.length; $('systemUes').textContent=us.length; $('systemCells').textContent=r.config.cells; $('systemPrb').textContent=r.config.prbs; $('systemThr').textContent=n(m.totalThroughputMbps).toFixed(1)+' Mbps';
    $('systemFair').textContent=n(m.systemFairness).toFixed(3); $('systemCqi').textContent=(us.reduce((a,u)=>a+n(u.meanCqi),0)/Math.max(1,us.length)).toFixed(1); $('systemBler').textContent=pct(us.reduce((a,u)=>a+n(u.meanBler),0)/Math.max(1,us.length)); $('systemCrc').textContent=pct(m.phyCrcPassRate);
    $('statusText').textContent=`${r.config.slots} slots • ${r.config.ueCount} UEs • ${r.config.cells} cells • ${r.config.prbs} PRBs`;
    $('lastRun').textContent=new Date().toLocaleTimeString();
    renderTable();renderSelected();renderMap(us,r.config.cells);
    renderBars('chartThroughput',us.map(u=>n(u.throughputMbps)));
    renderBars('chartSinr',us.map(u=>n(u.meanSinrDb)),' dB');
    renderBars('chartBler',us.map(u=>100*n(u.meanBler)),' %');
    renderBars('chartPrb',us.map(u=>n(u.totalAllocatedPrbs)));
    renderBars('perfThroughput',us.map(u=>n(u.throughputMbps)));
    renderBars('perfSinr',us.map(u=>n(u.meanSinrDb)),' dB');
    renderBars('perfPrb',us.map(u=>n(u.totalAllocatedPrbs)));
    renderBars('perfBler',us.map(u=>100*n(u.meanBler)),' %');
    renderBars('topSinr',us.map(u=>n(u.meanSinrDb)),' dB');
  }
  async function run(){
    if(state.running)return; state.running=true; $('topStatus').innerHTML='<i></i> RUNNING';$('statusText').textContent='Running V62 → V63 → V61…';
    try{const c=controls();const r=await api('V63',{slots:c.slots,ue:c.ue,cells:c.cells,prbs:c.prbs,scs:c.scs,velocity:c.velocity,payloadBits:128,tx:4,rx:4,layers:1});state.data=r;state.selected=Math.min(state.selected,r.ueStates.length-1);renderAll();log(`Simulation completed: ${c.slots} slots, ${c.ue} UEs, ${c.cells} cells`);}
    catch(e){$('topStatus').innerHTML='<i class="err"></i> ERROR';$('statusText').textContent='Error: '+e.message;log(e.message,'ERROR');}
    finally{state.running=false;if($('liveToggle').checked)startLive();else $('topStatus').innerHTML='<i></i> READY';}
  }
  function startLive(){clearTimeout(state.timer);state.timer=setTimeout(()=>{run();},1500)}
  function reset(){clearTimeout(state.timer);state.data=null;state.selected=0;['throughput','spectral','fairness','bler','crc','avgSinr','activeUes'].forEach(id=>$(id).textContent='—');$('ueRows').innerHTML='';$('selectedDetails').innerHTML='<div class="empty">Run a simulation to populate live UE data.</div>';$('topStatus').innerHTML='<i></i> READY';$('statusText').textContent='Ready';log('Dashboard reset');}
  async function phyRun(){
    const snr=+$('phySnr').value,mcs=+$('phyMcs').value,layers=+$('phyLayers').value,prb=+$('phyPrb').value;
    $('phyResult').textContent='Executing…';
    try{const r=await api('V20',{snr, mcs, layers, prbs:prb,payloadBits:12000});const s=String(r.result);$('phyResult').textContent=s; $('phyState').textContent='PASS';log(`PHY pipeline check: SNR ${snr} dB, MCS ${mcs}, ${layers} layer(s)`);}catch(e){$('phyState').textContent='ERROR';$('phyResult').textContent=e.message;log(e.message,'ERROR');}
  }
  async function mimoRun(){
    $('mimoResult').textContent='Running SRS / beam sweep…';
    try{const snr=+$('mimoSnr').value;const a=await api('V21',{snr,layers:4,prbs:+$('mimoPrb').value});const b=await api('V28',{azimuth:+$('azimuth').value,layers:4,beams:+$('beams').value});$('mimoResult').textContent=`SRS: ${a.result}\nBeam sweep: ${b.result}`;log('MIMO / CSI experiment completed');}catch(e){$('mimoResult').textContent=e.message;log(e.message,'ERROR');}
  }
  async function schedulerRun(){
    $('schedulerResult').textContent='Running proportional-fair scheduler…';
    try{const r=await api('V22',{ue:+$('expUe').value,cqi:+$('expCqi').value,ueSinr:+$('expSinr').value,prbs:+$('expPrb').value});$('schedulerResult').textContent=String(r.result);log('Scheduler experiment completed');}catch(e){$('schedulerResult').textContent=e.message;log(e.message,'ERROR');}
  }
  async function sweep(){
    const vals=[10,14,18,22,26];const out=$('sweepBody');out.innerHTML='<tr><td colspan="5">Running sweep…</td></tr>';const rows=[];
    for(const snr of vals){try{const r=await api('V63',{slots:Math.min(20,+$('sweepSlots').value),ue:+$('sweepUe').value,cells:2,prbs:+$('sweepPrb').value,scs:30,velocity:+$('sweepVel').value,payloadBits:128,tx:4,rx:4,layers:1,snrOffset:snr-18});rows.push([snr,n(r.metrics.totalThroughputMbps),n(r.metrics.systemFairness),n(r.metrics.phyCrcPassRate),n(r.metrics.phyBer)]);}catch(e){rows.push([snr,NaN,NaN,NaN,NaN]);}}
    out.innerHTML=rows.map(x=>`<tr><td>${x[0]} dB</td><td>${Number.isFinite(x[1])?x[1].toFixed(2):'—'}</td><td>${Number.isFinite(x[2])?x[2].toFixed(3):'—'}</td><td>${Number.isFinite(x[3])?pct(x[3]):'—'}</td><td>${Number.isFinite(x[4])?x[4].toExponential(2):'—'}</td></tr>`).join('');log('Experiment sweep completed');
  }
  function renderLogs(){const b=$('logBody');if(!b)return;b.innerHTML=state.logs.map(x=>`<tr><td>${esc(x.time)}</td><td class="${x.level==='ERROR'?'bad':''}">${x.level}</td><td>${esc(x.message)}</td></tr>`).join('') || '<tr><td colspan="3">No events.</td></tr>';}
  function exportData(){const payload={generatedAt:new Date().toISOString(),config:state.data?.config||controls(),metrics:state.data?.metrics||null,ueStates:state.data?.ueStates||[],logs:state.logs};const blob=new Blob([JSON.stringify(payload,null,2)],{type:'application/json'});const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='5g-nr-v64-simulation.json';a.click();URL.revokeObjectURL(a.href);log('Exported simulation JSON');}
  function tabs(){document.querySelectorAll('.tab').forEach(t=>t.addEventListener('click',()=>{document.querySelectorAll('.tab').forEach(x=>x.classList.remove('active'));document.querySelectorAll('.panel').forEach(x=>x.classList.remove('active'));t.classList.add('active');$('panel-'+t.dataset.tab).classList.add('active');if(t.dataset.tab==='network'&&state.data)renderMap(state.data.ueStates,state.data.config.cells);log(`Opened ${t.textContent.trim()} tab`);}));}
  $('run').onclick=run;$('reset').onclick=reset;$('export').onclick=exportData;$('liveToggle').onchange=()=>{if($('liveToggle').checked){run();}else clearTimeout(state.timer);};
  ['slots','ue','cells','prbs','scs','velocity'].forEach(id=>$(id).addEventListener('input',syncControls));
  $('phyRun').onclick=phyRun;$('mimoRun').onclick=mimoRun;$('schedulerRun').onclick=schedulerRun;$('sweepRun').onclick=sweep;$('clearLogs').onclick=()=>{state.logs=[];renderLogs();};
  $('selectedUe').onchange=e=>{state.selected=Math.max(0,[...$('selectedUe').options].findIndex(o=>o.value===e.target.value));renderAll();};
  $('topologyCanvas').addEventListener('click',e=>{const c=$('topologyCanvas'),r=c.getBoundingClientRect();const x=e.clientX-r.left,y=e.clientY-r.top;const p=c._points||[];let best=-1,d=22;p.forEach((q,i)=>{const z=Math.hypot(q.x-x,q.y-y);if(z<d){d=z;best=i}});if(best>=0){state.selected=best;renderAll();}});
  tabs();syncControls();renderLogs();log('V64 HMI initialized');run();
})();
