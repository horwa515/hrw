'use strict';
const $ = id => document.getElementById(id);
const token = document.querySelector('meta[name="app-token"]').content;
let selected = [], batchId = null, busy = false, pollTimer = null, latest = null;
const names = {uploading:'上传中', queued:'等待转换', converting:'转换中', done:'已完成', failed:'失败'};
function message(text) { $('message').textContent = text; $('message').hidden = !text; }
function node(tag, cls, text) { const n = document.createElement(tag); if(cls)n.className=cls; if(text!==undefined)n.textContent=text; return n; }
async function api(path, options={}) {
  const headers = new Headers(options.headers || {}); headers.set('X-App-Token', token);
  const response = await fetch(path, {...options, headers});
  if(!response.ok) { let error; try { error=(await response.json()).error; } catch { error='本地服务没有正常响应'; } throw new Error(error || '请求失败'); }
  return response;
}
function addFiles(list) {
  if(busy || batchId)return;
  const errors=[];
  for(const file of list) {
    if(!/\.pptx$/i.test(file.name)) { errors.push(`${file.name}：仅支持 .pptx`); continue; }
    if(file.size===0 || file.size>128*1024*1024) { errors.push(`${file.name}：文件为空或超过 128 MB`); continue; }
    if(file.name.length>180) { errors.push(`${file.name}：文件名过长`); continue; }
    if(selected.some(f=>f.name===file.name && f.size===file.size && f.lastModified===file.lastModified))continue;
    if(selected.length>=20 || selected.reduce((s,f)=>s+f.size,0)+file.size>256*1024*1024) { errors.push('每批最多 20 个文件，总计不超过 256 MB'); break; }
    selected.push(file);
  }
  message(errors.join('；')); renderSelected();
}
function renderSelected() {
  $('files').replaceChildren(); $('empty').hidden=selected.length>0;
  $('count').textContent=`${selected.length} 个文件`; $('convert').disabled=!selected.length||busy;
  selected.forEach((f,i)=>{
    const card=node('article','file-card'), head=node('div','file-head');
    head.append(node('span','file-symbol','PPTX'),node('span','file-name',f.name),node('span','file-state',`${(f.size/1024/1024).toFixed(2)} MB`));
    const remove=node('button','remove','×'); remove.type='button'; remove.setAttribute('aria-label',`移除 ${f.name}`);
    remove.disabled=busy; remove.onclick=()=>{selected.splice(i,1);renderSelected();}; head.append(remove);card.append(head);$('files').append(card);
  });
}
function renderStatus(data) {
  latest=data; $('files').replaceChildren();$('empty').hidden=true;
  $('count').textContent=`${data.files.length} / ${selected.length} 个文件`;
  for(const f of data.files) {
    const card=node('article','file-card'),head=node('div','file-head');
    head.append(node('span','file-symbol','PPTX'),node('span','file-name',f.name),node('span',`file-state ${f.status}`,names[f.status]||f.status));card.append(head);
    if(!['done','failed'].includes(f.status)) { const bar=node('div','progress'),p=node('progress');p.max=100;p.value=f.progress;p.setAttribute('aria-label',`${f.name} 转换进度 ${f.progress}%`);bar.append(p);card.append(bar); }
    if(f.status==='done') {
      const toCm=n=>(n/360000).toFixed(2);
      card.append(node('p','detail',`${toCm(f.width)} × ${toCm(f.height)} cm → ${toCm(f.newWidth)} × ${toCm(f.height)} cm · ${f.resized?'已扩展至 16:9':'原尺寸保留'} · 替换 ${f.fontReplacements} 处字体`));
      const btn=node('button','secondary download-one','下载 PPTX');btn.onclick=()=>download(`/api/batches/${batchId}/files/${f.id}/download`,f.outputName);card.append(btn);
    }
    if(f.error)card.append(node('p','detail',f.error));
    if(f.warnings?.length) { const details=node('details','warnings');details.append(node('summary','',`${f.warnings.length} 条兼容性提示`)); const list=node('ul');f.warnings.forEach(w=>list.append(node('li','',w)));details.append(list);card.append(details); }
    $('files').append(card);
  }
  const done=data.files.filter(f=>f.status==='done').length,failed=data.files.filter(f=>f.status==='failed').length;
  const complete=!busy&&data.complete;
  $('download-all').hidden=!complete||!done;$('download-log').hidden=!complete;$('reset').hidden=!complete;
  $('summary').hidden=!complete;
  if(complete)$('summary').textContent=`处理完成：${done} 个成功，${failed} 个失败。${selected.length-data.files.length>0?`另有 ${selected.length-data.files.length} 个文件未成功上传。`:''} 原文件未修改。`;
}
async function poll() {
  if(!batchId)return;
  try { const data=await(await api(`/api/batches/${batchId}`)).json();renderStatus(data);
    if(!busy&&data.complete){clearInterval(pollTimer);pollTimer=null;}
  } catch(e) {message(`${e.message}。请确认程序窗口仍在运行。`);}
}
async function convert() {
  if(busy||!selected.length)return;
  busy=true;message('');$('convert').disabled=true;$('convert').textContent='正在处理…';$('file-input').disabled=true;
  try {
    batchId=(await(await api('/api/batches',{method:'POST'})).json()).id;
    pollTimer=setInterval(poll,800);
    const errors=[];
    for(const file of selected) {
      try { await api(`/api/batches/${batchId}/files?name=${encodeURIComponent(file.name)}`,{method:'POST',body:file,headers:{'Content-Type':'application/octet-stream'}}); }
      catch(e){errors.push(`${file.name}：${e.message}`);}
    }
    if(errors.length)message(errors.join('；'));
  }catch(e){message(e.message);}
  finally{busy=false;$('convert').textContent='开始转换';if(batchId)await poll();else{$('file-input').disabled=false;renderSelected();}}
}
async function download(path,name) {
  try{const blob=await(await api(path)).blob(),url=URL.createObjectURL(blob),a=node('a');a.href=url;a.download=name;document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),60000);}
  catch(e){message(e.message);}
}
$('file-input').addEventListener('change',e=>{addFiles(e.target.files);e.target.value='';});
const zone=$('dropzone');
zone.addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();$('file-input').click();}});
['dragenter','dragover'].forEach(type=>zone.addEventListener(type,e=>{e.preventDefault();zone.classList.add('dragging');}));
['dragleave','drop'].forEach(type=>zone.addEventListener(type,e=>{e.preventDefault();zone.classList.remove('dragging');}));
zone.addEventListener('drop',e=>addFiles(e.dataTransfer.files));
$('convert').onclick=convert;
$('download-all').onclick=()=>download(`/api/batches/${batchId}/download`,'converted-pptx.zip');
$('download-log').onclick=()=>download(`/api/batches/${batchId}/log`,'conversion-log.txt');
$('reset').onclick=async()=>{
  if(!confirm('请确认已下载需要的结果。开始新批次会删除当前批次的临时文件。'))return;
  try{await api(`/api/batches/${batchId}`,{method:'DELETE'});}catch(e){message(e.message);return;}
  clearInterval(pollTimer);pollTimer=null;selected=[];batchId=null;latest=null;$('file-input').disabled=false;
  ['download-all','download-log','reset','summary'].forEach(id=>$(id).hidden=true);message('');renderSelected();
};
window.addEventListener('beforeunload',e=>{if(busy||latest?.files.some(f=>['queued','converting','uploading'].includes(f.status))){e.preventDefault();e.returnValue='';}});
