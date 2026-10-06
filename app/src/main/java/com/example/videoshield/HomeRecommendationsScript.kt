package com.example.videoshield

import org.json.JSONArray
import org.json.JSONObject

/** Render local rankings in Home; result links enter the existing native player. */
object HomeRecommendationsScript {
    fun build(enabled: Boolean, rows: List<SuggestedVideo>, heading: String, hint: String): String {
        val headingJson = JSONObject.quote(heading)
        val hintJson = JSONObject.quote(hint)
        val payload = JSONArray()
        rows.take(12).forEach { row ->
            if (Regex("[A-Za-z0-9_-]{11}").matches(row.video.videoId)) payload.put(JSONObject().apply {
                put("id", row.video.videoId); put("title", row.video.title)
                put("channel", row.video.channel); put("reason", row.reason)
            })
        }
        return """
            (()=>{
              if(!['/',''].includes(location.pathname)) return;
              const id='votuibe-home-recommendations';
              let section=document.getElementById(id);
              if(!$enabled) { section?.remove(); return; }
              const rows=$payload;
              const fingerprint=JSON.stringify([rows,$headingJson,$hintJson]);
              if(section?.dataset.fingerprint===fingerprint) return;
              if(!section) { section=document.createElement('section'); section.id=id; document.body.prepend(section); }
              section.dataset.fingerprint=fingerprint;
              section.replaceChildren();
              const style=document.createElement('style');
              style.textContent='body:has(#votuibe-home-recommendations) ytm-feed-filter-chip-bar-renderer,body:has(#votuibe-home-recommendations) ytm-chip-cloud-renderer{display:none!important}';
              section.append(style);
              section.style.cssText='background:#000;color:#fff;padding:16px 12px;font-family:Roboto,Arial,sans-serif';
              const heading=document.createElement('h2'); heading.textContent=$headingJson;
              heading.style.cssText='font-size:20px;margin:0 0 14px'; section.append(heading);
              if(!rows.length) {
                const hint=document.createElement('p');
                hint.textContent=$hintJson;
                hint.style.cssText='color:#aaa;font-size:14px'; section.append(hint); return;
              }
              for(const row of rows) {
                const link=document.createElement('a'); link.href='/watch?v='+row.id;
                link.style.cssText='display:block;color:inherit;text-decoration:none;margin-bottom:20px';
                const image=document.createElement('img');
                image.src='https://i.ytimg.com/vi/'+row.id+'/mqdefault.jpg'; image.alt='';
                image.loading='lazy'; image.decoding='async';
                image.style.cssText='display:block;width:100%;aspect-ratio:16/9;object-fit:cover;border-radius:12px;background:#181818';
                const title=document.createElement('h3'); title.textContent=row.title;
                title.style.cssText='font-size:16px;line-height:1.4;margin:8px 0 4px';
                const detail=document.createElement('div'); detail.textContent=[row.channel,row.reason].filter(Boolean).join(' · ');
                detail.style.cssText='font-size:12px;color:#aaa;line-height:1.4';
                link.append(image,title,detail); section.append(link);
              }
            })();
        """.trimIndent()
    }
}
