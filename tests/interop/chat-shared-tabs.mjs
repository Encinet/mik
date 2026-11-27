import { join } from 'node:path';

// Only the test endpoint, its signing public key and the private grant callback are supplied by the fixture.
export async function checkSharedChat(context, controlPage, web, endpoint, publicKey) {
  async function bundle(entry) {
    const result = await Bun.build({
      entrypoints: [join(web, 'src/modules/chat', entry)], target: 'browser', tsconfig: join(web, 'tsconfig.json'),
      define: { 'process.env.NEXT_PUBLIC_WEB_CHAT_PUBLIC_KEY': JSON.stringify(publicKey) },
    });
    if (!result.success) throw new AggregateError(result.logs, 'Shared chat client build failed');
    return result.outputs[0].text();
  }
  const [client, worker] = await Promise.all([
    bundle('lib/connection/shared-chat-client.ts'), bundle('worker/chat-session.worker.ts'),
  ]);
  let grants = 0;
  const owner = '11111111-1111-4111-8111-111111111111';
  await controlPage.exposeFunction('grantSharedChat', grant => {
    if (grant.chatScope !== 's'.repeat(43)) throw Error('Missing website session binding');
    grants++;
    console.log('AUTHORIZE ' + JSON.stringify({ connectionId: grant.connectionId, clientPublicKey: grant.clientPublicKey, playerUuid: owner }));
  });
  const url = await controlPage.evaluate(({ worker, endpoint }) => {
    const grants = new BroadcastChannel('interop-grant');
    grants.onmessage = ({ data }) => globalThis.grantSharedChat(data);
    const setup = ({ endpoint }) => {
      const Native = globalThis.WebSocket;
      globalThis.WebSocket = class extends Native {
        constructor(url, protocols) { super(endpoint + new URL(url).pathname, protocols); }
      };
      const grants = new BroadcastChannel('interop-grant');
      globalThis.fetch = async (path, options) => {
        if (path !== '/api/chat/connect') throw Error('Unexpected worker fetch ' + path);
        grants.postMessage(JSON.parse(options.body));
        return Response.json({ ok: true });
      };
    };
    return URL.createObjectURL(new Blob(['(' + setup + ')(' + JSON.stringify({ endpoint }) + ');\n', worker],
      { type: 'text/javascript' }));
  }, { worker, endpoint });
  const attach = async () => {
    const page = await context.newPage();
    await page.route('https://mcmik.top/shared-chat/', r => r.fulfill({contentType:'text/html', body:'<title>Chat tab</title>'}));
    await page.route('https://mcmik.top/client.js', r => r.fulfill({contentType:'text/javascript', body:client}));
    await page.goto('https://mcmik.top/shared-chat/');
    await page.evaluate(async ({ url, owner }) => {
      const Native = globalThis.SharedWorker;
      globalThis.SharedWorker = class extends Native {
        constructor(_, options) { super(url, options); }
      };
      const {createPageChat} = await import('/client.js');
      const ready = Promise.withResolvers();
      const session = createPageChat(owner, 's'.repeat(43), {
        onEvent(event) { if (event.type === 'authorized') ready.resolve(); },
        onConnection(state) { if (state.status === 'error') ready.reject(Error(state.error)); },
        onDisconnect() {}, onDeliveryTimeout() {}, onRefreshAccount() {},
      });
      globalThis.testChat = session;
      session.retry();
      await ready.promise;
    }, { url, owner });
    return page;
  };
  try {
    const first = await attach();
    const second = await attach();
    if (grants !== 1) throw Error('Two tabs triggered ' + grants + ' authorizations');
    await first.close();
    const page = await second.evaluate(() => testChat.readHistory({ channel: 'private' }));
    if (page.messages.length !== 2 || grants !== 1)
      throw Error('The remaining tab lost its authorized private history after closing the first tab');
    await second.evaluate(() => testChat.stop());
    await second.close();
  } finally {
    await controlPage.evaluate(url => URL.revokeObjectURL(url), url);
  }
}
