// V64 additive PDF report layer.
// Collects the current HMI state/results without changing V61/V62/V63/V64 engines.
(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const esc = v => String(v ?? '').replace(/\s+/g,' ').trim();
  const val = id => { const e=$(id); return e ? esc(e.value ?? e.textContent) : '—'; };

  function loadJsPdf(done) {
    if (window.jspdf?.jsPDF) return done();
    const s=document.createElement('script');
    s.src='https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.2/jspdf.umd.min.js';
    s.onload=done;
    s.onerror=()=>alert('PDF library could not be loaded. Check the network connection and try again.');
    document.head.appendChild(s);
  }

  function addText(doc, text, x, y, width, lineHeight=13) {
    const lines=doc.splitTextToSize(String(text||'—'),width);
    for(const line of lines){
      if(y>275){doc.addPage();y=22;}
      doc.text(line,x,y);y+=lineHeight;
    }
    return y;
  }
  function heading(doc,title,y){
    if(y>260){doc.addPage();y=22;}
    doc.setFontSize(14);doc.setFont(undefined,'bold');doc.text(title,14,y);doc.setFont(undefined,'normal');doc.setFontSize(9);return y+8;
  }
  function section(doc,title,body,y){
    y=heading(doc,title,y);return addText(doc,body,14,y,182,12)+5;
  }

  function config(){
    return `Cells: ${val('cells')} | UEs: ${val('ue')} | PRBs/cell: ${val('prbs')} | SCS: ${val('scs')} kHz | UE velocity: ${val('velocity')} km/h | Simulation slots: ${val('slots')}`;
  }

  function ueTable(doc,data,y){
    const us=data?.ueStates||[];
    if(!us.length)return section(doc,'Per-UE Results','No V63/V64 UE result is currently available.',y);
    y=heading(doc,'Per-UE Radio / PHY Results',y);
    const headers=['UE','SINR dB','CQI','MCS','PRBs','Throughput Mbps','BLER','CRC'];
    const xs=[14,31,51,67,84,104,143,165];
    doc.setFont(undefined,'bold');headers.forEach((h,i)=>doc.text(h,xs[i],y));doc.setFont(undefined,'normal');y+=6;
    us.forEach(u=>{
      if(y>278){doc.addPage();y=22;doc.setFont(undefined,'bold');headers.forEach((h,i)=>doc.text(h,xs[i],y));doc.setFont(undefined,'normal');y+=6;}
      const row=[u.ueId,u.sinrDb??u.meanSinrDb,u.cqi??u.meanCqi,u.mcs??u.meanMcs,u.allocatedPrbs??u.totalAllocatedPrbs,u.throughputMbps,u.bler??u.meanBler,u.phyCrcPassRate==null?'—':(Number(u.phyCrcPassRate)*100).toFixed(2)+'%'];
      row.forEach((v,i)=>doc.text(String(v??'—').slice(0,18),xs[i],y));y+=5;
    });
    return y+5;
  }

  function rawBlock(doc,title,id,y){
    const e=$(id);if(!e)return y;
    const text=esc(e.textContent||e.value||'');
    if(!text||text==='—'||text==='Running…')return y;
    return section(doc,title,text,y);
  }

  function logs(doc,y){
    const rows=Array.from(document.querySelectorAll('#logBody tr[data-v64-log]'));
    if(!rows.length)return section(doc,'Simulation Log','No log events recorded.',y);
    y=heading(doc,'Simulation Log',y);
    rows.forEach(r=>{
      const c=r.querySelectorAll('td');
      const line=`[${esc(c[0]?.textContent)}] [${esc(c[1]?.textContent)}] ${esc(c[2]?.textContent)}`;
      const detail=esc(c[3]?.textContent);
      y=addText(doc,line,14,y,182,11);
      if(detail)y=addText(doc,'  '+detail,20,y,176,10);
      y+=2;
    });
    return y;
  }

  function report(){
    loadJsPdf(()=>{
      const jsPDF=window.jspdf.jsPDF;
      const doc=new jsPDF({unit:'mm',format:'a4'});
      const data=window.__v64LastData||{};
      const metrics=data.metrics||{};
      let y=18;
      doc.setFontSize(20);doc.setFont(undefined,'bold');doc.text('5G NR Simulator',14,y);y+=8;
      doc.setFontSize(13);doc.text('V64 Radio Environment Lab — Simulation Report',14,y);doc.setFont(undefined,'normal');y+=6;
      doc.setFontSize(8);doc.text(`Generated: ${new Date().toLocaleString()}`,14,y);y+=10;

      y=section(doc,'Simulation Configuration',config(),y);
      y=section(doc,'System Results',
        `Total throughput: ${metrics.totalThroughputMbps==null?'—':Number(metrics.totalThroughputMbps).toFixed(3)} Mbps\n`+
        `System fairness: ${metrics.systemFairness==null?'—':Number(metrics.systemFairness).toFixed(5)}\n`+
        `PHY CRC pass rate: ${metrics.phyCrcPassRate==null?'—':(Number(metrics.phyCrcPassRate)*100).toFixed(3)+'%'}\n`+
        `PHY BER: ${metrics.phyBer==null?'—':Number(metrics.phyBer).toExponential(4)}\n`+
        `Samples retained by live performance plots: ${window.__v64Performance?.history?.length??0}`,y);
      y=ueTable(doc,data,y);
      y=rawBlock(doc,'PHY Pipeline Result','phyResult',y);
      y=rawBlock(doc,'MIMO / CSI Result','mimoResult',y);
      y=rawBlock(doc,'Scheduler Experiment Result','schedulerResult',y);
      y=rawBlock(doc,'Parameter Sweep Result','sweepResult',y);
      y=logs(doc,y);

      doc.addPage();y=18;
      y=heading(doc,'Report Notes',y);
      y=addText(doc,'This report is a client-side snapshot of the current V64 HMI state. It includes the active simulation configuration, latest V63/V64 radio/PHY metrics, per-UE results, available PHY/MIMO/scheduler/sweep outputs, and the recorded simulation log. Existing V61/V62/V63/V64 computation engines are not modified by the report feature.',14,y,182,12);
      const history=window.__v64Performance?.history||[];
      if(history.length){
        y=heading(doc,'Live Performance History',y+7);
        const h=['Sample','Throughput Mbps','Avg SINR dB','Avg BLER %','PRB Util %'];
        const xs=[14,42,80,118,154];doc.setFont(undefined,'bold');h.forEach((v,i)=>doc.text(v,xs[i],y));doc.setFont(undefined,'normal');y+=6;
        history.forEach((s,i)=>{if(y>278){doc.addPage();y=22;}doc.text(String(i+1),xs[0],y);doc.text(Number(s.throughput||0).toFixed(2),xs[1],y);doc.text(Number(s.sinr||0).toFixed(2),xs[2],y);doc.text(Number(s.bler||0).toFixed(2),xs[3],y);doc.text(Number(s.prb||0).toFixed(2),xs[4],y);y+=5;});
      }
      const pages=doc.getNumberOfPages();for(let p=1;p<=pages;p++){doc.setPage(p);doc.setFontSize(7);doc.text(`V64 Simulation Report | Page ${p} / ${pages}`,14,290);}
      doc.save(`5G_NR_V64_Report_${new Date().toISOString().replace(/[:.]/g,'-')}.pdf`);
    });
  }

  function install(){
    const actions=document.querySelector('.actions');
    if(!actions||$('downloadReport'))return !!actions;
    const b=document.createElement('button');b.id='downloadReport';b.className='btn';b.textContent='▣ Download Report';b.title='Download all current simulation results as a PDF report';b.onclick=report;actions.appendChild(b);
    return true;
  }
  let attempts=0;const timer=setInterval(()=>{if(install()||++attempts>100)clearInterval(timer)},100);
})();
