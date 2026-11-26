import { createHash, X509Certificate } from 'node:crypto';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { createServer } from 'node:https';
import { createRequire } from 'node:module';
import { createConnection } from 'node:net';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { checkSharedChat } from './chat-shared-tabs.mjs';

const web = process.env.MIK_CHAT_WEB_PROJECT;
const publicKey = process.env.MIK_CHAT_PUBLIC_KEY;
const target = new URL(process.env.MIK_CHAT_INTEROP_URL);
const { CHAT_PROTOCOL_VERSION: version } = await import(join(web, 'src/shared/config/service-config.ts'));
const { chromium } = createRequire(join(web, 'package.json'))('playwright-core');
const bundle = await Bun.build({
  entrypoints: [join(import.meta.dir, 'chat-browser.mjs')],
  target: 'browser',
  tsconfig: join(web, 'tsconfig.json'),
  plugins: [{ name: 'paired-client', setup(build) {
    build.onResolve({ filter: /^mikweb-chat\// }, ({ path }) => ({
      path: join(web, 'src/modules/chat/lib/connection', path.split('/')[1] + '.ts'),
    }));
  } }],
});
if (!bundle.success) throw new AggregateError(bundle.logs, 'Could not bundle the browser client');
const source = await bundle.outputs[0].text();

async function openTunnel(directory) {
  const keyFile = join(directory, 'tunnel.key');
  const certFile = join(directory, 'tunnel.crt');
  const openssl = Bun.spawnSync(['openssl', 'req', '-x509', '-newkey', 'ec',
    '-pkeyopt', 'ec_paramgen_curve:P-256', '-noenc', '-days', '1',
    '-subj', '/CN=127.0.0.1', '-addext', 'subjectAltName=IP:127.0.0.1',
    '-out', certFile, '-keyout', keyFile]);
  if (openssl.exitCode !== 0) throw Error(openssl.stderr.toString());
  const [key, cert] = await Promise.all([readFile(keyFile), readFile(certFile)]);
  const spki = new X509Certificate(cert).publicKey.export({ type: 'spki', format: 'der' });
  const pin = createHash('sha256').update(spki).digest('base64');
  const gateway = createServer({ key, cert });
  const sockets = new Set();
  gateway.on('upgrade', (request, client, head) => {
    const upstream = createConnection(Number(target.port), target.hostname);
    sockets.add(client);
    client.on('close', () => { sockets.delete(client); upstream.destroy(); });
    upstream.on('close', () => client.destroy());
    client.on('error', () => upstream.destroy());
    upstream.on('error', () => client.destroy());
    const headers = Object.entries(request.headers).filter(([name]) => name !== 'x-forwarded-proto');
    headers.push(['x-forwarded-proto', 'https']);
    upstream.write([`GET ${request.url} HTTP/1.1`, ...headers.map(([name, value]) => `${name}: ${value}`),
      '', ''].join('\r\n'));
    if (head.length) upstream.write(head);
    client.pipe(upstream).pipe(client);
  });
  await new Promise((resolve, reject) => {
    gateway.once('error', reject);
    gateway.listen(0, '127.0.0.1', resolve);
  });
  const origin = 'wss://127.0.0.1:' + gateway.address().port;
  return { origin, pin, close() {
    for (const socket of sockets) socket.destroy();
    return new Promise(resolve => gateway.close(resolve));
  } };
}

const directory = await mkdtemp(join(tmpdir(), 'mik-chat-tunnel-'));
let tunnel;
let browser;
try {
  tunnel = await openTunnel(directory);
  browser = await chromium.launch({
    executablePath: process.env.MIK_CHAT_CHROMIUM,
    args: ['--ignore-certificate-errors-spki-list=' + tunnel.pin],
  });
  const context = await browser.newContext();
  const outside = 'https://untrusted.example';
  await context.grantPermissions(['local-network-access'], { origin: outside });
  const denied = await context.newPage();
  await denied.route(outside + '/', route => route.fulfill({ contentType: 'text/html', body: '<title>Origin check</title>' }));
  await denied.goto(outside + '/');
  const accepted = await denied.evaluate(({ origin, version }) => new Promise(resolve => {
    const socket = new WebSocket(origin + '/ws', version);
    socket.onopen = () => { socket.close(); resolve(true); };
    socket.onerror = () => resolve(false);
  }), { origin: tunnel.origin, version });
  if (accepted) throw Error('Wrong website origin was accepted');
  await denied.close();
  await context.grantPermissions(['local-network-access'], { origin: 'https://mcmik.top' });
  const page = await context.newPage();
  await page.route('https://mcmik.top/', route => route.fulfill({ contentType: 'text/html', body: '<title>Chat interop</title>' }));
  await page.goto('https://mcmik.top/');
  page.on('console', msg => { if (msg.type() === 'log') console.log(msg.text()); else console.error(msg.text()); });
  await page.evaluate(config => { globalThis.interop = config; }, {
    origin: tunnel.origin, publicKey, version, uncompressed: process.env.MIK_CHAT_NO_DEFLATE === 'true',
  });
  await checkSharedChat(context, page, web, tunnel.origin, publicKey);
  await page.evaluate(async code => {
    const url = URL.createObjectURL(new Blob([code], { type: 'text/javascript' }));
    try { await import(url); } finally { URL.revokeObjectURL(url); }
  }, source);
} finally {
  await browser?.close();
  await tunnel?.close();
  await rm(directory, { recursive: true, force: true });
}
