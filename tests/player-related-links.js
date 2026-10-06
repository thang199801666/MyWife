Array.from(document.querySelectorAll('a[href*="watch?v="]')).map(a => ({url:a.href,title:a.textContent.trim().slice(0,120)})).filter(a => !a.url.includes('aqz-KE-bpKQ')).slice(0,5)
