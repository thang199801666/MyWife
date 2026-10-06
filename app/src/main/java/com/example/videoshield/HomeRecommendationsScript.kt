package com.example.videoshield

import org.json.JSONArray
import org.json.JSONObject

/** Render local rankings in Home; result links enter the existing native player. */
object HomeRecommendationsScript {
    fun build(
        enabled: Boolean,
        rows: List<SuggestedVideo>,
        heading: String,
        hint: String,
        lightTheme: Boolean = false
    ): String {
        val headingJson = JSONObject.quote(heading)
        val hintJson = JSONObject.quote(hint)
        val background = if (lightTheme) "#ffffff" else "#0f0f0f"
        val text = if (lightTheme) "#0f0f0f" else "#f1f1f1"
        val secondary = if (lightTheme) "#606060" else "#aaaaaa"
        val chip = if (lightTheme) "#f2f2f2" else "#272727"
        val payload = JSONArray()
        rows.take(12).forEach { row ->
            if (Regex("[A-Za-z0-9_-]{11}").matches(row.video.videoId)) payload.put(JSONObject().apply {
                put("id", row.video.videoId)
                put("title", row.video.title)
                put("channel", row.video.channel)
                put("reason", row.reason)
            })
        }
        return """
            (()=>{
              if(!['/',''].includes(location.pathname)) return;
              const id='votuibe-home-recommendations';
              let section=document.getElementById(id);
              if(!$enabled) { section?.remove(); return; }
              const rows=$payload;
              const fingerprint=JSON.stringify([rows,$headingJson,$hintJson,${if (lightTheme) "1" else "0"}]);
              if(section?.dataset.fingerprint===fingerprint) return;
              if(!section) {
                section=document.createElement('section');
                section.id=id;
                const app=document.querySelector('ytm-app');
                if(app?.parentElement) app.parentElement.insertBefore(section,app); else document.body.prepend(section);
              }
              section.dataset.fingerprint=fingerprint;
              section.replaceChildren();
              section.style.cssText='box-sizing:border-box;background:$background;color:$text;font-family:Roboto,Arial,sans-serif;padding:4px 0 2px;width:100%';

              const headingRow=document.createElement('div');
              headingRow.style.cssText='display:flex;align-items:center;justify-content:space-between;padding:12px 16px 10px';
              const title=document.createElement('h2');
              title.textContent=$headingJson;
              title.style.cssText='font-size:19px;line-height:24px;font-weight:700;margin:0;color:$text';
              headingRow.append(title);
              section.append(headingRow);

              if(!rows.length) {
                const empty=document.createElement('p');
                empty.textContent=$hintJson;
                empty.style.cssText='color:$secondary;font-size:14px;line-height:20px;margin:0;padding:4px 16px 20px';
                section.append(empty);
                return;
              }

              for(const row of rows) {
                const card=document.createElement('article');
                card.style.cssText='display:block;margin:0 0 18px;background:$background';
                const link=document.createElement('a');
                link.href='/watch?v='+encodeURIComponent(row.id);
                link.style.cssText='display:block;color:inherit;text-decoration:none';

                const image=document.createElement('img');
                image.src='https://i.ytimg.com/vi/'+row.id+'/hqdefault.jpg';
                image.alt=''; image.loading='lazy'; image.decoding='async';
                image.style.cssText='display:block;width:100%;aspect-ratio:16/9;object-fit:cover;background:$chip';
                link.append(image);

                const meta=document.createElement('div');
                meta.style.cssText='display:grid;grid-template-columns:44px minmax(0,1fr) 32px;gap:10px;padding:10px 12px 0;align-items:start';
                const avatar=document.createElement('div');
                const initial=(row.channel||row.title||'?').trim().charAt(0).toUpperCase()||'?';
                avatar.textContent=initial;
                avatar.style.cssText='width:38px;height:38px;border-radius:50%;display:flex;align-items:center;justify-content:center;background:$chip;color:$text;font-size:16px;font-weight:700;user-select:none';

                const copy=document.createElement('div'); copy.style.minWidth='0';
                const videoTitle=document.createElement('h3');
                videoTitle.textContent=row.title;
                videoTitle.style.cssText='font-size:16px;line-height:21px;font-weight:500;margin:0 0 4px;color:$text;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden';
                const detail=document.createElement('div');
                detail.textContent=[row.channel,row.reason].filter(Boolean).join(' · ');
                detail.style.cssText='font-size:13px;line-height:18px;color:$secondary;white-space:nowrap;overflow:hidden;text-overflow:ellipsis';
                copy.append(videoTitle,detail);

                const more=document.createElement('span');
                more.textContent='⋮';
                more.setAttribute('aria-hidden','true');
                more.style.cssText='font-size:25px;line-height:26px;color:$text;text-align:center';
                meta.append(avatar,copy,more);
                link.append(meta);
                card.append(link);
                section.append(card);
              }
            })();
        """.trimIndent()
    }
}
