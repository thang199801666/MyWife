package com.example.videoshield

import org.json.JSONArray
import org.json.JSONObject

/** Strip explicit ad fields before the website constructs its media player. */
object EarlyAdScript {
    fun build(preferences: ShieldPreferences): String {
        val policy = JSONObject().apply {
            put("enabled", preferences.shieldEnabled)
            put("safeMode", preferences.safeMode)
            put("whitelist", JSONArray(preferences.whitelistedChannels.toList()))
        }
        return """
        (() => {
          window.__voTuibeEarlyPolicy=$policy;
          if (window.__voTuibeEarlyInstalled) return;
          window.__voTuibeEarlyInstalled=true;
          window.__voTuibeEarlyAdFieldsRemoved=0;
          const parse=JSON.parse;
          const enabledFor=player=>{
            const cfg=window.__videoShieldCfg || window.__voTuibeEarlyPolicy;
            if (!cfg.enabled || cfg.safeMode || cfg.bypassAds) return false;
            const whitelist=window.__voTuibeEarlyPolicy.whitelist || [];
            const author=String(player.videoDetails?.author || '').trim().toLowerCase().slice(0,160);
            return !whitelist.includes(author) && !!(author || !whitelist.length);
          };
          const clean=value=>{
            if (!value || typeof value!=='object') return value;
            const player=value.playerResponse || value;
            if (!player.playabilityStatus || (!player.videoDetails && !player.streamingData)) return value;
            if (!enabledFor(player)) return value;
            for (const key of ['playerAds','adPlacements','adSlots','adBreakHeartbeatParams']) {
              if (Object.prototype.hasOwnProperty.call(player,key) && delete player[key]) {
                window.__voTuibeEarlyAdFieldsRemoved++;
              }
            }
            return value;
          };
          const playerEndpoint=input=>{
            try {
              const url=new URL(String(input),location.href);
              return url.origin===location.origin && /^\/youtubei\/v1\/(player|next)$/.test(url.pathname);
            } catch (_) { return false; }
          };
          // Only complete, successful player JSON is transformed. Repeated reads of
          // the same XHR response reuse a bounded cache, never reparse the whole body.
          const textCache=new WeakMap();
          const cleanText=(owner,text)=>{
            const cfg=window.__videoShieldCfg || window.__voTuibeEarlyPolicy;
            if (!cfg.enabled || cfg.safeMode || cfg.bypassAds) return text;
            if (typeof text!=='string' || text.length>4*1024*1024) return text;
            let cached=textCache.get(owner);
            if (!cached || cached.raw!==text) {
              try {
                const value=parse(text);
                cached={raw:text,value,filtered:null};
                textCache.set(owner,cached);
              } catch (_) { return text; }
            }
            const player=cached.value?.playerResponse || cached.value;
            if (!player || !player.playabilityStatus || (!player.videoDetails && !player.streamingData) || !enabledFor(player)) return text;
            if (cached.filtered!==null) return cached.filtered;
            const before=window.__voTuibeEarlyAdFieldsRemoved;
            clean(cached.value);
            cached.filtered=before===window.__voTuibeEarlyAdFieldsRemoved ? text : JSON.stringify(cached.value);
            return cached.filtered;
          };
          JSON.parse=function(...args){ return clean(parse.apply(this,args)); };
          const descriptor=Object.getOwnPropertyDescriptor(window,'ytInitialPlayerResponse');
          if (!descriptor || (descriptor.configurable && !descriptor.get && !descriptor.set)) {
            let response=clean(window.ytInitialPlayerResponse);
            Object.defineProperty(window,'ytInitialPlayerResponse',{configurable:true,enumerable:true,
              get:()=>response,set:value=>{response=clean(value);}});
          }
          const originalFetch=window.fetch;
          if (typeof originalFetch==='function') window.fetch=async function(input,...args){
            const response=await originalFetch.call(this,input,...args);
            try {
              const target=typeof input==='string'?input:(input.url || input.href);
              if (!playerEndpoint(target) || (response.url && !playerEndpoint(response.url)) || !response.ok) return response;
              const data=await response.clone().json();
              const before=window.__voTuibeEarlyAdFieldsRemoved;
              clean(data);
              if (before===window.__voTuibeEarlyAdFieldsRemoved) return response;
              const headers=new Headers(response.headers); headers.delete('content-length');
              headers.delete('content-encoding');
              const filtered=new Response(JSON.stringify(data),{status:response.status,statusText:response.statusText,headers});
              // A constructed Response otherwise loses the URL and redirect metadata.
              for (const key of ['url','redirected','type']) Object.defineProperty(filtered,key,{value:response[key]});
              return filtered;
            } catch (_) { return response; }
          };
          if (typeof XMLHttpRequest==='function') {
            const proto=XMLHttpRequest.prototype;
            for (const key of ['responseText','response']) {
              const descriptor=Object.getOwnPropertyDescriptor(proto,key);
              if (!descriptor?.get || !descriptor.configurable) continue;
              Object.defineProperty(proto,key,{...descriptor,get:function(){
                // Preserve native exceptions, partial responses and binary data.
                const value=descriptor.get.call(this);
                try {
                  if (this.readyState!==4 || this.status<200 || this.status>=300 || !playerEndpoint(this.responseURL)) return value;
                  if (key==='response' && this.responseType==='json') return clean(value);
                  if (!this.responseType || this.responseType==='text') return cleanText(this,value);
                } catch (_) {}
                return value;
              }});
            }
          }
        })();
        """.trimIndent()
    }
}
