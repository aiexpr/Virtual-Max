/**
 * VirtualMax — Пользовательские метки (галочки / бейджи).
 *
 * Инжектирует в страницу мессенджера движок, который рисует метки
 * (галочку «проверен», цветной лейбл и т.п.) рядом с именами пользователей.
 *
 * Список меток подгружается из централизованного JSON (по ID пользователя),
 * скачивается в основном процессе (вне страницы — нет проблем с CORS/CSP)
 * и передаётся сюда уже готовым массивом. Формат элемента:
 *   { id: "123", name: "Иван", check: true, label: "Проверен", color: "#00b0ff", title: "..." }
 *
 * Движок идемпотентен: повторная инъекция (смена настроек / новый список)
 * не накладывает хуки, а обновляет данные через window.__VM_BADGES_SET__().
 */

// Канонический JavaScript движка. Плейсхолдер __VM_LIST__ заменяется на
// JSON-массив меток. Используются только одинарные кавычки и без ${},
// поэтому шаблонная строка безопасна.
const BADGES_ENGINE = `(function(){
'use strict';
if(window.__VM_BADGES__){window.__VM_BADGES_SET__(__VM_LIST__);return;}
window.__VM_BADGES__=true;
var LIST=__VM_LIST__;
var GEN=0;
var byId={};
var byName={};
var byIdList=[];
var observer=null;
var pending=[];
var timer=null;
function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
function buildIndexes(){
  byId={};byName={};byIdList=[];
  if(!LIST||!LIST.length){return;}
  for(var i=0;i<LIST.length;i++){
    var u=LIST[i];if(!u)continue;
    var id=String(u.id==null?'':u.id).trim().toLowerCase();
    var name=String(u.name==null?'':u.name).trim().toLowerCase();
    if(id){byId[id]=u;}
    if(name&&name.length>1){byName[name]=u;}
  }
  for(var k in byId){
    var theId=k;
    byIdList.push({id:theId,re:new RegExp('(^|[^0-9])'+theId+'([^0-9]|$)'),badge:byId[k]});
  }
}
function badgeHtml(b){
  var color=(b.color&&/^#?[0-9a-fA-F]{3,8}$/.test(b.color))?b.color:'#00b0ff';
  if(color.charAt(0)!=='#'){color='#'+color;}
  var label=b.label?String(b.label):'';
  var check=b.check!==false;
  var title=b.title?' title="'+esc(b.title)+'"':'';
  var h='<span class="vm-badge"'+title+' style="display:inline-flex;align-items:center;gap:4px;margin-left:5px;vertical-align:middle;font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,Arial,sans-serif;line-height:1;">';
  if(check){h+='<span style="display:inline-flex;align-items:center;justify-content:center;min-width:16px;height:16px;padding:0 2px;border-radius:50%;background:'+color+';color:#fff;font-size:11px;font-weight:700;">&#10003;</span>';}
  if(label){h+='<span style="font-size:10px;font-weight:600;color:'+color+';background:'+color+'22;padding:1px 6px;border-radius:8px;white-space:nowrap;">'+esc(label)+'</span>';}
  h+='</span>';
  return h;
}
function idBadge(v){
  v=String(v||'');
  for(var i=0;i<byIdList.length;i++){
    var it=byIdList[i];
    if(v.indexOf(it.id)===-1){continue;}
    if(it.re.test(v)){return it.badge;}
  }
  return null;
}
var ATTRS=['href','data-user-id','data-peer-id','data-id','data-uid','data-mid','data-member-id','data-user','data-conversation','data-from','data-author','user-id','peer-id'];
function badgeFor(el){
  if(el.nodeType!==1){return null;}
  for(var a=0;a<ATTRS.length;a++){
    var v=el.getAttribute(ATTRS[a]);
    if(v){var b=idBadge(v);if(b){return b;}}
  }
  if(el.children&&el.children.length===0){
    var t=el.textContent;
    if(t&&t.length>=2&&t.length<=40){
      var n=String(t).trim().toLowerCase();
      if(byName[n]){return byName[n];}
    }
  }
  return null;
}
function decorate(el){
  if(el.nodeType!==1){return;}
  if(el.__vmGen===GEN){return;}
  var b=badgeFor(el);
  if(!b){return;}
  el.__vmGen=GEN;
  try{el.insertAdjacentHTML('beforeend',badgeHtml(b));}catch(e){}
}
function walk(root){
  if(!root){return;}
  if(root.nodeType===1){decorate(root);}
  if(!root.querySelectorAll){return;}
  var els=root.querySelectorAll(ATTRS.join(','));
  for(var i=0;i<els.length;i++){decorate(els[i]);}
  var leaves=root.querySelectorAll('*');
  for(var j=0;j<leaves.length;j++){decorate(leaves[j]);}
}
function flush(){
  var nodes=pending;pending=[];
  for(var i=0;i<nodes.length;i++){walk(nodes[i]);}
}
function schedule(node){
  pending.push(node);
  if(timer){return;}
  timer=setTimeout(function(){timer=null;flush();},200);
}
function init(){
  if(!document.body){setTimeout(init,300);return;}
  if(observer){try{observer.disconnect();}catch(e){}}
  observer=new MutationObserver(function(muts){
    for(var i=0;i<muts.length;i++){
      var m=muts[i];
      if(m.addedNodes&&m.addedNodes.length){
        for(var j=0;j<m.addedNodes.length;j++){schedule(m.addedNodes[j]);}
      }
      if(m.type==='characterData'&&m.target&&m.target.parentNode){schedule(m.target.parentNode);}
    }
  });
  observer.observe(document.body,{childList:true,subtree:true,characterData:true});
  walk(document.body);
}
window.__VM_BADGES_SET__=function(newList){
  LIST=newList||[];
  GEN++;
  var old=document.querySelectorAll('.vm-badge');
  for(var i=0;i<old.length;i++){if(old[i].parentNode){old[i].parentNode.removeChild(old[i]);}}
  buildIndexes();
  if(LIST&&LIST.length){init();}
  else{try{if(observer)observer.disconnect();}catch(e){}observer=null;}
};
buildIndexes();
if(LIST&&LIST.length){init();}
})();`;

/**
 * Собирает JS-инъекцию движка меток для переданного списка.
 * @param {Array} list Нормализованный список меток [{id,name,check,label,color,title}]
 * @returns {string} IIFE-строка для executeJavaScript
 */
function buildBadgesJs(list) {
  const json = JSON.stringify(Array.isArray(list) ? list : []);
  return BADGES_ENGINE.split('__VM_LIST__').join(json);
}

module.exports = {
  buildBadgesJs
};
