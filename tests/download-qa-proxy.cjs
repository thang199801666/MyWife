// Temporary loopback-only CONNECT proxy for diagnosing the emulator's broken DNS.
// Never bundled or used by the app. Stop this process after QA.
const http = require('node:http');
const net = require('node:net');
const server = http.createServer((req, res) => { res.writeHead(405); res.end(); });
server.on('connect', (req, client, head) => {
  const [host, port] = req.url.split(':');
  if (port !== '443' || !/(^|\.)(youtube\.com|googlevideo\.com|ytimg\.com|google\.com|github\.com|githubusercontent\.com)$/.test(host)) {
    client.end('HTTP/1.1 403 Forbidden\r\n\r\n'); return;
  }
  const upstream = net.connect({ host, port: 443 }, () => {
    client.write('HTTP/1.1 200 Connection Established\r\n\r\n');
    if (head.length) upstream.write(head);
    upstream.pipe(client); client.pipe(upstream);
  });
  upstream.setTimeout(30000, () => upstream.destroy());
  client.on('error', () => upstream.destroy());
  upstream.on('error', () => client.destroy());
  client.on('close', () => upstream.destroy());
});
server.listen(8889, '127.0.0.1', () => console.log('Download QA proxy listening on loopback:8889'));
