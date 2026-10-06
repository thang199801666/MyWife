// Evaluate a supplied expression in the debug player WebView; never bundled in the APK.
// Usage: node tests/webview-evaluate.cjs path/to/expression.js [target URL substring]
const fs = require('node:fs');
(async () => {
  const targets = await (await fetch('http://127.0.0.1:9228/json')).json();
  const target = targets.find(item => process.argv[3] === 'home' ? new URL(item.url).pathname === '/' : process.argv[3] ? item.url.includes(process.argv[3]) :
    (item.url.includes('/watch?') || item.url.includes('/shorts/')));
  if (!target) throw new Error('No player WebView target');
  const socket = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
  try {
    const result = await new Promise((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Evaluation timed out')), 15000);
      socket.onmessage = event => {
        const message = JSON.parse(event.data);
        if (message.id === 1) { clearTimeout(timeout); resolve(message.result); }
      };
      socket.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: {
        returnByValue: true, awaitPromise: true, expression: fs.readFileSync(process.argv[2], 'utf8')
      } }));
    });
    if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
    console.log(JSON.stringify(result.result?.value ?? result));
  } finally { socket.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
