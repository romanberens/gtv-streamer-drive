package pl.local.nasbrowser

/** Responsive standalone LAN page. Dynamic data only enters HTML through escaped placeholders;
 * file/folder names from the API are assigned with textContent (never innerHTML). */
internal fun renderPhonePage(status: String, paired: Boolean, csrf: String, note: String?): String {
    return PHONE_HTML
        .replace("__DISK_STATUS__", status)
        .replace("__PAIRED__", if (paired) "true" else "false")
        .replace("__CSRF__", csrf)
        .replace("__AUTH_HINT__", if (paired) "Sparowany telefon" else "Wpisz kod z telewizora")
        .replace("__MESSAGE__", htmlEscapePhone(note ?: "Wybierz pliki, aby rozpocząć."))
}

private fun htmlEscapePhone(text: String): String = text.replace("&", "&amp;")
    .replace("<", "&lt;").replace(">", "&gt;").replace("\\"", "&quot;")

private val PHONE_HTML = """<!doctype html>
<html lang="pl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="theme-color" content="#0c1826">
<title>Panel NAS — transfer z telefonu</title>
<style>
:root{color-scheme:dark;--bg:#081321;--surface:#122638;--border:#294252;--text:#f2f8fb;--muted:#aac0ce;--accent:#71e4cc;--warn:#ffbbac}
*{box-sizing:border-box}
body{margin:0;min-height:100vh;background:radial-gradient(circle at 80% 0%,#1a3d48 0%,transparent 42%),var(--bg);font:15px/1.5 system-ui,-apple-system,Segoe UI,sans-serif;color:var(--text)}
main{max-width:1000px;margin:auto;padding:23px 16px 70px}
header{display:flex;align-items:center;gap:14px;margin-bottom:28px}
.logo{width:47px;height:47px;display:grid;place-items:center;border-radius:14px;background:var(--accent);color:#09212c;font-size:24px;font-weight:900}
h1{margin:0;font-size:22px;letter-spacing:-.6px}
p{margin:5px 0;color:var(--muted)}
.chip{margin-left:auto;border:1px solid #32685e;background:#143e3e;color:#c0f7e8;padding:7px 11px;border-radius:40px;font-size:12px;white-space:nowrap}
.layout{display:grid;grid-template-columns:1.1fr .9fr;gap:18px}
.card{background:rgba(19,40,56,.97);border:1px solid var(--border);border-radius:20px;padding:22px;min-width:0;box-shadow:0 15px 35px rgba(0,0,0,.13)}
h2{margin:0 0 9px;font-size:21px;letter-spacing:-.4px}
.sub{font-size:13px;margin-bottom:18px}
.drop{border:2px dashed #4a7580;border-radius:16px;background:#0e2636;min-height:185px;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;padding:17px;cursor:pointer;transition:background .15s,border-color .15s}
.drop.drag,.drop:focus-visible{border-color:var(--accent);background:#1a3a43;outline:0}
.drop strong{font-size:17px}
.drop p{font-size:13px}
.drop .symbol{font-size:38px;color:var(--accent);margin-bottom:7px}
input[type=file]{display:none}
input[type=text],input[type=password]{width:100%;border-radius:10px;border:1px solid #526a79;background:#102b39;color:white;padding:13px;font-size:16px;outline-offset:2px}
label{font-size:13px;color:var(--muted);display:block;margin:15px 0 7px}
button{font:inherit;border:0;cursor:pointer;border-radius:10px;padding:12px 15px;background:#284455;color:white;font-weight:600;transition:filter .15s,background .15s}
button:focus-visible{outline:3px solid var(--accent);outline-offset:2px}
button:hover{filter:brightness(1.13)}
button.primary{width:100%;background:var(--accent);color:#09212c;margin-top:16px;font-weight:800;font-size:16px}
button[disabled]{opacity:.5;cursor:not-allowed}
.toolbar{display:flex;gap:8px;flex-wrap:wrap;align-items:center;margin:14px 0}
.toolbar button,.row-actions button{font-size:12px;padding:8px 10px}
.path{color:var(--accent);font-size:12px;word-break:break-word;flex:1;min-width:110px}
.items{max-height:360px;overflow:auto;display:flex;flex-direction:column;gap:8px}
.item{border:1px solid #284455;background:#102536;border-radius:12px;padding:10px;display:flex;align-items:center;gap:9px;min-width:0}
.fileicon{background:#23465a;color:var(--accent);width:36px;height:36px;border-radius:10px;display:grid;place-items:center;flex:none}
.itemtext{min-width:0;flex:1}
.itemtext strong{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:13px}
.itemtext small{display:block;color:var(--muted);font-size:11px}
.row-actions{display:flex;gap:5px}
.row-actions .danger{color:var(--warn)}
.messages{font-size:13px;margin:13px 0;padding:11px 13px;border-radius:10px;background:#204553;color:#d7fff5;min-height:42px}
.messages.error{background:#5a2932;color:#ffdedb}
.progress{height:8px;background:#294757;border-radius:99px;overflow:hidden;margin-top:12px}
.progress>div{height:100%;width:0%;background:var(--accent);transition:width .15s}
.note{font-size:12px;margin-top:12px}
.empty{color:var(--muted);font-size:13px;padding:18px;text-align:center}
@media(max-width:740px){.layout{grid-template-columns:1fr}.card{padding:18px}.chip{font-size:10px;padding:5px 8px}header{gap:10px}main{padding-top:15px}.row-actions{flex-wrap:wrap;justify-content:flex-end}}
@media(prefers-reduced-motion:reduce){*,*:before,*:after{transition:none!important}}
</style>
</head>
<body>
<main>
<header><div class="logo" aria-hidden="true">▤</div><div><h1>Panel NAS</h1><p>Bez chmury • bezpośrednio na dysk USB</p></div><span id="auth" class="chip">__AUTH_HINT__</span></header>
<div class="layout">
<section class="card">
<h2>Wyślij pliki</h2>
<p class="sub">Wybierz materiały lub przeciągnij je tutaj. Kolejka wysyłana jest po jednym pliku.</p>
<label class="drop" for="picker" id="drop" tabindex="0">
<span class="symbol" aria-hidden="true">⇧</span>
<strong>Dotknij, aby wybrać pliki</strong>
<p id="selectedLabel">Zdjęcia, filmy, dokumenty — bez ograniczenia typu</p>
</label>
<input type="file" id="picker" multiple>
<p class="note">Dysk: <strong id="status">__DISK_STATUS__</strong></p>
<p class="note" id="targetText">Folder docelowy: katalog główny</p>
<div id="codeArea" style="display:none"><label for="code">Awaryjny kod z ekranu TV</label><input id="code" type="password" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" autocomplete="off"></div>
<button class="primary" id="send" type="button">Wyślij na dysk</button>
<div class="progress" role="progressbar" aria-label="Postęp wysyłania"><div id="bar"></div></div>
<div class="messages message" id="message" role="status" aria-live="polite">__MESSAGE__</div>
<p class="note">Nie odłączaj dysku USB podczas przesyłania. Połączenie działa w Twojej sieci lokalnej.</p>
</section>
<section class="card">
<h2>Pliki na dysku</h2>
<p class="sub">Wybierz folder docelowy, utwórz katalog lub uporządkuj pliki.</p>
<div id="pairedArea" style="display:none">
<div class="toolbar">
<button id="up" type="button">← Wyżej</button>
<button id="refresh" type="button">↻ Odśwież</button>
<button id="mkdir" type="button">+ Nowy folder</button>
</div>
<div class="path" id="path">/</div>
<div class="items" id="items"></div>
<p class="note">Zmiana nazwy i usuwanie są dostępne tylko dla sparowanych telefonów. Usunięcie jest trwałe.</p>
</div>
<div id="notPaired" class="empty">Sparuj telefon kodem QR na telewizorze, aby przeglądać pliki i zarządzać katalogami. Wysyłanie awaryjnym kodem nadal jest dostępne.</div>
</section>
</div>
</main>
<script>
(function(){
'use strict';
const PAIRED=__PAIRED__;
const CSRF="__CSRF__";
const $=id=>document.getElementById(id);
let files=[],folder="",root="",sending=false;
$('pairedArea').style.display=PAIRED?'block':'none';
$('notPaired').style.display=PAIRED?'none':'block';
$('codeArea').style.display=PAIRED?'none':'block';
function message(text,error){$('message').textContent=text;$('message').classList.toggle('error',!!error)}
function size(n){if(n<1024)return n+' B';if(n<1048576)return (n/1024).toFixed(1)+' KB';if(n<1073741824)return (n/1048576).toFixed(1)+' MB';return (n/1073741824).toFixed(1)+' GB'}
function choose(list){files=Array.from(list||[]);$('selectedLabel').textContent=files.length?files.length+' wybranych plików • '+files.map(f=>f.name).slice(0,2).join(', '):'Zdjęcia, filmy, dokumenty — bez ograniczenia typu'}
$('picker').addEventListener('change',e=>choose(e.target.files));
$('drop').addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();$('picker').click()}});
for(const e of ['dragenter','dragover'])$('drop').addEventListener(e,ev=>{ev.preventDefault();$('drop').classList.add('drag')});
for(const e of ['dragleave','drop'])$('drop').addEventListener(e,ev=>{ev.preventDefault();$('drop').classList.remove('drag')});
$('drop').addEventListener('drop',ev=>choose(ev.dataTransfer.files));
async function api(path,fields){
const options=fields?{method:'POST',body:new URLSearchParams(Object.assign({csrf:CSRF},fields))}:{};
const response=await fetch(path,options);const data=await response.json();
if(!response.ok||!data.ok)throw Error(data.error||'Błąd operacji');return data;
}
function node(tag,cls,text){const e=document.createElement(tag);if(cls)e.className=cls;if(text!==undefined)e.textContent=text;return e}
function button(label,action,danger){const b=node('button',danger?'danger':'',label);b.type='button';b.addEventListener('click',action);return b}
async function loadFolder(id){
if(!PAIRED)return;
try{
const data=await api('/api/list'+(id?'?folder='+encodeURIComponent(id):''));
root=data.root;folder=data.folder;
$('path').textContent=folder===root?'/':('/'+folder.substring(root.length));
$('targetText').textContent='Folder docelowy: '+$('path').textContent;
const list=$('items');list.replaceChildren();
if(!data.items.length)list.append(node('p','empty','Katalog jest pusty'));
for(const entry of data.items){
const row=node('div','item');
const symbol=node('div','fileicon',entry.directory?'▣':'▤');
const txt=node('div','itemtext');
txt.append(node('strong','',entry.name),node('small','',entry.directory?'Folder':size(entry.size)));
const actions=node('div','row-actions');
if(entry.directory)actions.append(button('Otwórz',()=>loadFolder(entry.id)));
actions.append(button('Zmień',async()=>{
const name=prompt('Nowa nazwa:',entry.name);if(name===null||name===entry.name)return;
try{await api('/api/rename',{id:entry.id,name});await loadFolder(folder);message('Zmieniono nazwę')}catch(e){message(e.message,true)}
}));
actions.append(button('Usuń',async()=>{
if(!confirm('Usunąć na stałe: '+entry.name+'?\\nOperacji nie można cofnąć.'))return;
try{await api('/api/delete',{id:entry.id});await loadFolder(folder);message('Usunięto: '+entry.name)}catch(e){message(e.message,true)}
},true));
row.append(symbol,txt,actions);list.append(row);
}
}catch(e){message(e.message,true)}
}
$('up').addEventListener('click',()=>{
if(folder===root)return;
let cut=folder.lastIndexOf('/');let parent=folder.substring(0,cut);
if(parent.endsWith(':'))parent+='/';loadFolder(parent||root);
});
$('refresh').addEventListener('click',()=>loadFolder(folder));
$('mkdir').addEventListener('click',async()=>{
const name=prompt('Nazwa nowego katalogu:');if(!name)return;
try{await api('/api/mkdir',{folder:folder||root,name});await loadFolder(folder);message('Utworzono katalog: '+name)}catch(e){message(e.message,true)}
});
function upload(file,index){
return new Promise((resolve,reject)=>{
const form=new FormData();form.append('file',file,file.name);
form.append('folder',folder||root);
if(!PAIRED)form.append('code',$('code').value.trim());
const xhr=new XMLHttpRequest();xhr.open('POST','/');
xhr.upload.onprogress=e=>{
if(e.lengthComputable)$('bar').style.width=Math.round(e.loaded*100/e.total)+'%';
message('Wysyłanie '+index+'/'+files.length+': '+file.name+' • '+Math.round(e.loaded*100/e.total)+'%');
};
xhr.onload=()=>{
const doc=new DOMParser().parseFromString(xhr.responseText,'text/html');
const msg=doc.querySelector('.message')?.textContent?.trim()||'Brak odpowiedzi serwera';
if(xhr.status===200&&msg.startsWith('Zapisano'))resolve(msg);else reject(Error(msg));
};
xhr.onerror=()=>reject(Error('Połączenie zostało przerwane'));
xhr.send(form);
});
}
$('send').addEventListener('click',async()=>{
if(sending)return;
if(!files.length){message('Najpierw wybierz pliki.',true);return}
if(!PAIRED&&!/^[0-9]{6}$/.test($('code').value.trim())){message('Podaj sześciocyfrowy kod z TV.',true);return}
sending=true;$('send').disabled=true;
try{
for(let i=0;i<files.length;i++){await upload(files[i],i+1);$('bar').style.width='0%'}
message('Wysłano '+files.length+' plików na dysk.');
choose([]);$('picker').value='';
if(PAIRED)await loadFolder(folder);
}catch(e){message(e.message,true)}
finally{sending=false;$('send').disabled=false}
});
if(PAIRED)loadFolder('');
})();
</script>
</body></html>
"""
