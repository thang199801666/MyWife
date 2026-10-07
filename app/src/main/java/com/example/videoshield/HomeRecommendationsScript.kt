package com.example.videoshield

import org.json.JSONArray
import org.json.JSONObject

/**
 * Small native-data supplements for Home. YouTube still owns the feed itself; these rows only
 * surface local resume/recommendation state that the web document cannot know about.
 *
 * The script deliberately relies on browser-native lazy image loading and static skeletons. There
 * are no animation timers, MutationObservers or scroll polling loops in this layer.
 */
object HomeRecommendationsScript {
    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")

    fun clear(): String = "document.getElementById('votuibe-home-recommendations')?.remove();"

    fun loading(enabled: Boolean, lightTheme: Boolean = false): String {
        if (!enabled) return clear()
        val background = if (lightTheme) "#ffffff" else "#0f0f0f"
        val placeholder = if (lightTheme) "#e5e5e5" else "#272727"
        val placeholderSoft = if (lightTheme) "#eeeeee" else "#202020"
        return """
            (()=>{
              if(!['/',''].includes(location.pathname)) return;
              const id='votuibe-home-recommendations';
              if(document.getElementById(id)) return;
              const section=document.createElement('section');
              section.id=id;
              section.dataset.loading='1';
              section.style.cssText='box-sizing:border-box;background:$background;width:100%;padding:8px 0 2px;contain:layout paint style';
              const row=document.createElement('div');
              row.style.cssText='display:flex;gap:12px;overflow:hidden;padding:0 12px 12px';
              for(let i=0;i<2;i++){
                const card=document.createElement('div');
                card.style.cssText='flex:0 0 min(78vw,320px)';
                const image=document.createElement('div');
                image.style.cssText='width:100%;aspect-ratio:16/9;border-radius:12px;background:$placeholder';
                const line1=document.createElement('div');
                line1.style.cssText='height:13px;width:86%;border-radius:7px;background:$placeholder;margin-top:10px';
                const line2=document.createElement('div');
                line2.style.cssText='height:11px;width:58%;border-radius:6px;background:$placeholderSoft;margin-top:7px';
                card.append(image,line1,line2); row.append(card);
              }
              section.append(row);
              const app=document.querySelector('ytm-app');
              if(app?.parentElement) app.parentElement.insertBefore(section,app); else document.body.prepend(section);
            })();
        """.trimIndent()
    }

    fun build(
        enabled: Boolean,
        rows: List<SuggestedVideo>,
        continueRows: List<VideoItem>,
        continueHeading: String,
        heading: String,
        hint: String,
        lightTheme: Boolean = false
    ): String {
        val headingJson = JSONObject.quote(heading)
        val hintJson = JSONObject.quote(hint)
        val continueHeadingJson = JSONObject.quote(continueHeading)
        val background = if (lightTheme) "#ffffff" else "#0f0f0f"
        val text = if (lightTheme) "#0f0f0f" else "#f1f1f1"
        val secondary = if (lightTheme) "#606060" else "#aaaaaa"
        val chip = if (lightTheme) "#f2f2f2" else "#272727"
        val track = if (lightTheme) "#d9d9d9" else "#5a5a5a"

        val payload = JSONArray()
        rows.take(24).forEach { row ->
            if (VIDEO_ID.matches(row.video.videoId)) payload.put(JSONObject().apply {
                put("id", row.video.videoId)
                put("title", row.video.title)
                put("channel", row.video.channel)
                put("reason", row.reason)
            })
        }

        val resumePayload = JSONArray()
        continueRows.take(8).forEach { item ->
            if (VIDEO_ID.matches(item.videoId) && item.durationMs > 0L && item.positionMs > 0L) {
                resumePayload.put(JSONObject().apply {
                    put("id", item.videoId)
                    put("title", item.title)
                    put("channel", item.channel)
                    put("position", item.positionMs.coerceAtLeast(0L))
                    put("duration", item.durationMs.coerceAtLeast(1L))
                })
            }
        }

        return """
            (()=>{
              const id='votuibe-home-recommendations';
              let section=document.getElementById(id);
              if(!['/',''].includes(location.pathname)) { section?.remove(); return; }
              if(!$enabled) { section?.remove(); return; }
              const rows=$payload;
              const resumeRows=$resumePayload;
              const fingerprint=JSON.stringify([rows,resumeRows,$continueHeadingJson,$headingJson,$hintJson,${if (lightTheme) "1" else "0"}]);
              if(section?.dataset.fingerprint===fingerprint) return;
              if(!rows.length && !resumeRows.length) { section?.remove(); return; }
              if(!section) {
                section=document.createElement('section');
                section.id=id;
                const app=document.querySelector('ytm-app');
                if(app?.parentElement) app.parentElement.insertBefore(section,app); else document.body.prepend(section);
              }
              const wasLoading=section.dataset.loading==='1';
              section.dataset.fingerprint=fingerprint;
              delete section.dataset.loading;
              section.replaceChildren();
              section.style.cssText='box-sizing:border-box;background:$background;color:$text;font-family:Roboto,Arial,sans-serif;padding:2px 0 0;width:100%;contain:layout paint style';

              const formatDuration=ms=>{
                const total=Math.max(0,Math.floor(Number(ms||0)/1000));
                const h=Math.floor(total/3600),m=Math.floor((total%3600)/60),s=total%60;
                return h>0 ? h+':'+String(m).padStart(2,'0')+':'+String(s).padStart(2,'0') : m+':'+String(s).padStart(2,'0');
              };
              const addHeading=value=>{
                const label=String(value||'').trim(); if(!label) return;
                const headingRow=document.createElement('div');
                headingRow.style.cssText='display:flex;align-items:center;justify-content:space-between;padding:12px 12px 10px';
                const title=document.createElement('h2');
                title.textContent=label;
                title.style.cssText='font-size:18px;line-height:24px;font-weight:700;margin:0;color:$text';
                headingRow.append(title); section.append(headingRow);
              };

              if(resumeRows.length){
                addHeading($continueHeadingJson);
                const shelf=document.createElement('div');
                shelf.className='votuibe-home-resume-shelf';
                shelf.style.cssText='display:flex;gap:12px;overflow-x:auto;overscroll-behavior-inline:contain;scroll-snap-type:x proximity;scrollbar-width:none;padding:0 12px 14px;-webkit-overflow-scrolling:touch';
                resumeRows.forEach((row,index)=>{
                  const card=document.createElement('a');
                  card.href='/watch?v='+encodeURIComponent(row.id);
                  card.style.cssText='display:block;flex:0 0 min(78vw,320px);scroll-snap-align:start;color:inherit;text-decoration:none;content-visibility:auto;contain-intrinsic-size:230px';
                  const media=document.createElement('div');
                  media.style.cssText='position:relative;width:100%;aspect-ratio:16/9;border-radius:12px;overflow:hidden;background:$chip';
                  const image=document.createElement('img');
                  image.src='https://i.ytimg.com/vi/'+row.id+'/hqdefault.jpg';
                  image.alt=''; image.decoding='async';
                  image.loading=index===0?'eager':'lazy';
                  image.fetchPriority=index===0?'high':'low';
                  image.style.cssText='display:block;width:100%;height:100%;object-fit:cover;background:$chip';
                  const duration=document.createElement('span');
                  duration.textContent=formatDuration(row.duration);
                  duration.style.cssText='position:absolute;right:6px;bottom:6px;padding:2px 4px;border-radius:4px;background:rgba(0,0,0,.8);color:#fff;font-size:12px;line-height:16px;font-weight:600';
                  const progressTrack=document.createElement('div');
                  progressTrack.style.cssText='position:absolute;left:0;right:0;bottom:0;height:3px;background:$track';
                  const progress=document.createElement('div');
                  const ratio=Math.max(0,Math.min(1,Number(row.position||0)/Math.max(1,Number(row.duration||1))));
                  progress.style.cssText='height:100%;width:'+(ratio*100).toFixed(2)+'%;background:#ff0033';
                  progressTrack.append(progress); media.append(image,duration,progressTrack);

                  const title=document.createElement('h3');
                  title.textContent=row.title;
                  title.style.cssText='font-size:15px;line-height:20px;font-weight:500;margin:8px 0 2px;color:$text;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden';
                  const detail=document.createElement('div');
                  detail.textContent=row.channel||'';
                  detail.style.cssText='font-size:13px;line-height:18px;color:$secondary;white-space:nowrap;overflow:hidden;text-overflow:ellipsis';
                  card.append(media,title,detail); shelf.append(card);
                });
                section.append(shelf);
              }

              const headingText=$headingJson.trim();
              if(rows.length && headingText) addHeading(headingText);

              rows.forEach((row,index)=>{
                const card=document.createElement('article');
                card.style.cssText='display:block;margin:0 0 18px;background:$background;content-visibility:auto;contain-intrinsic-size:300px;contain:layout paint style';
                const link=document.createElement('a');
                link.href='/watch?v='+encodeURIComponent(row.id);
                link.style.cssText='display:block;color:inherit;text-decoration:none';

                const image=document.createElement('img');
                image.src='https://i.ytimg.com/vi/'+row.id+'/hqdefault.jpg';
                image.alt=''; image.decoding='async';
                image.loading=index===0?'eager':'lazy';
                image.fetchPriority=index===0?'high':'low';
                image.style.cssText='display:block;width:calc(100% - 20px);margin:0 10px;aspect-ratio:16/9;object-fit:cover;background:$chip;border-radius:12px';
                link.append(image);

                const meta=document.createElement('div');
                meta.style.cssText='display:grid;grid-template-columns:38px minmax(0,1fr) 32px;gap:10px;padding:10px 12px 0;align-items:start';
                const avatar=document.createElement('div');
                const initial=(row.channel||row.title||'?').trim().charAt(0).toUpperCase()||'?';
                avatar.textContent=initial;
                avatar.style.cssText='width:36px;height:36px;border-radius:50%;display:flex;align-items:center;justify-content:center;background:$chip;color:$text;font-size:15px;font-weight:700;user-select:none';

                const copy=document.createElement('div'); copy.style.minWidth='0';
                const videoTitle=document.createElement('h3');
                videoTitle.textContent=row.title;
                videoTitle.style.cssText='font-size:16px;line-height:21px;font-weight:500;margin:0 0 3px;color:$text;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden';
                const detail=document.createElement('div');
                detail.textContent=[row.channel,row.reason].filter(Boolean).join(' · ');
                detail.style.cssText='font-size:13px;line-height:18px;color:$secondary;white-space:nowrap;overflow:hidden;text-overflow:ellipsis';
                copy.append(videoTitle,detail);

                const more=document.createElement('span');
                more.textContent='⋮';
                more.setAttribute('aria-hidden','true');
                more.style.cssText='font-size:25px;line-height:26px;color:$text;text-align:center';
                meta.append(avatar,copy,more);
                link.append(meta); card.append(link); section.append(card);
              });

              // Cold-load skeletons are replaced in one DOM transaction. A single compositor
              // animation softens that handoff without a timer, observer or per-card animation.
              if(wasLoading && typeof section.animate==='function') {
                section.animate(
                  [{opacity:.72,transform:'translateY(2px)'},{opacity:1,transform:'translateY(0)'}],
                  {duration:160,easing:'cubic-bezier(.20,0,0,1)'}
                );
              }
            })();
        """.trimIndent()
    }
}
