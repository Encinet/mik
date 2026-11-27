import { ChatSession } from 'mikweb-chat/chat-session';
import { ChatTransport } from 'mikweb-chat/chat-transport';

const assert = {
  ok(value, message) { if (!value) throw Error(message ?? 'Expected a truthy value'); },
  equal(actual, expected, message) {
    if (!Object.is(actual, expected)) throw Error(message ?? `Expected ${expected}, got ${actual}`);
  },
  deepEqual(actual, expected) { this.equal(JSON.stringify(actual), JSON.stringify(expected)); },
};

const NativeWebSocket = globalThis.WebSocket;
const {origin, publicKey, version, uncompressed} = globalThis.interop;
const writes = [];
let historyGate;
let historyRead;
globalThis.WebSocket = class extends NativeWebSocket {
  constructor(url, protocols) { super(origin + new URL(url).pathname, protocols); }
};
const encrypt = SubtleCrypto.prototype.encrypt;
SubtleCrypto.prototype.encrypt = function(algorithm, key, bytes) {
  if (algorithm.name === 'AES-GCM' && bytes.byteLength > 1) {
    const [kind] = new Uint8Array(bytes.buffer ?? bytes, bytes.byteOffset ?? 0, bytes.byteLength);
    writes.push({lane: kind === 2 ? 1 : 0, kind});
  }
  return encrypt.call(this, algorithm, key, bytes);
};
const compressedEvents = [];
if (uncompressed) {
  globalThis.DecompressionStream = undefined;
  const readHistory = ChatTransport.prototype.history;
  ChatTransport.prototype.history = async function(request, signal, receive) {
    const pause = historyGate;
    if (!pause) return readHistory.call(this, request, signal, receive);
    const records = [];
    await readHistory.call(this, request, signal, (record) => {
      records.push(record);
      historyRead();
    });
    await pause;
    for (const record of records) receive(record);
  };
} else {
  const NativeDecompressionStream = globalThis.DecompressionStream;
  globalThis.DecompressionStream = class extends NativeDecompressionStream {
    get readable() {
      const decoder = new TextDecoder('utf-8', { fatal: true });
      let json = '';
      return super.readable.pipeThrough(new TransformStream({
        transform(chunk, controller) {
          json += decoder.decode(chunk, { stream: true });
          controller.enqueue(chunk);
        },
        async flush() {
          const event = JSON.parse(json + decoder.decode());
          compressedEvents.push(event);
          if (event.type === 'history' && historyGate) {
            historyRead();
            await historyGate;
          }
        },
      }));
    }
  };
}
const OWNER = '11111111-1111-4111-8111-111111111111';
const WRITER = '22222222-2222-4222-8222-222222222222';
const OTHER = '33333333-3333-4333-8333-333333333333';

async function rawRequest(playerUuid, request, matches) {
  const transport = new ChatTransport(publicKey);
  const reply = Promise.withResolvers();
  const timeout = setTimeout(() => reply.reject(Error('Missing raw ' + request.type + ' reply')), 5000);
  transport.onclose = () => reply.reject(Error('Raw transport closed before ' + request.type + ' reply'));
  transport.onmessage = event => {
    if (event.type === 'hello') {
      console.log('AUTHORIZE ' + JSON.stringify({
        connectionId: event.connectionId, clientPublicKey: transport.authorizationKey, playerUuid,
      }));
    } else if (event.type === 'authorized') {
      transport.send(request);
    } else if (event.type === 'error') {
      reply.reject(Error(JSON.stringify(event)));
    } else if (matches(event)) {
      reply.resolve(event);
    }
  };
  try {
    return await reply.promise;
  } finally {
    clearTimeout(timeout);
    transport.close();
  }
}

function browser(playerUuid) {
  let ready;
  let reject;
  const online = new Promise((resolve, fail) => {
    ready = resolve;
    reject = fail;
  });
  const events = [];
  const session = new ChatSession(
    playerUuid,
    {
      onConnection: (state) => {
        if (state.status === 'online') ready();
        else if (state.status === 'error') reject(new Error(JSON.stringify(state)));
      },
      onEvent: (event) => events.push(event),
      onDisconnect: () => reject(new Error('Integration connection closed')),
      onRefreshAccount: () => {},
      onDeliveryTimeout: () => {},
    },
    {
      createTransport: () => new ChatTransport(publicKey),
      authorize: async (connectionId, clientPublicKey) => {
        console.log(`AUTHORIZE ${JSON.stringify({ connectionId, clientPublicKey, playerUuid })}`);
        return { ok: true };
      },
      now: () => Date.now(),
      schedule: (callback, delay) => {
        const id = setTimeout(callback, delay);
        return () => clearTimeout(id);
      },
    },
  );
  return { session, online, events };
}
const { session, online, events } = browser(OWNER);
try {
  for (const [path, protocol] of [['/chat', version], ['/ws', '2026-11-26-4'], ['/ws', undefined]]) {
    const wrong = new WebSocket('wss://chat.mcmik.top' + path, protocol);
    const rejected = await new Promise(resolve => {
      wrong.onopen = () => resolve(false);
      wrong.onerror = () => resolve(true);
    });
    wrong.close();
    assert.ok(rejected, 'Unsupported WebSocket upgrade was accepted');
  }
  // The grant names a different valid key. The signed transport must never become authorized.
  const unrelated = await crypto.subtle.generateKey({name: 'ECDSA', namedCurve: 'P-256'}, false, ['sign','verify']);
  const raw = new Uint8Array(await crypto.subtle.exportKey('raw', unrelated.publicKey));
  const clientPublicKey = btoa(String.fromCharCode(...raw)).replaceAll('+','-').replaceAll('/','_').replaceAll('=','');
  const wrongKey = new ChatTransport(publicKey);
  let receivedHello = false;
  await new Promise((resolve, reject) => {
    wrongKey.onmessage = event => {
      if (event.type !== 'hello') { reject(Error('Unbound transport received application data')); return; }
      receivedHello = true;
      console.log('AUTHORIZE ' + JSON.stringify({connectionId: event.connectionId, clientPublicKey, playerUuid: OWNER}));
    };
    wrongKey.onclose = resolve;
  });
  assert.ok(receivedHello);
  session.retry();
  await online;
  const historyPages = () => compressedEvents.filter(event => event.type === 'history');
  let page;
  const messages = new Map();
  let requests = 0;
  do {
    page = await session.readHistory({
      channel: 'public',
      ...(page?.nextPage ? { page: page.nextPage } : {}),
    });
    requests++;
    if (page.nextPage) assert.equal(page.checkpoint, undefined);
    for (const message of page.messages) messages.set(message.id, message);
  } while (page.nextPage);
  assert.ok(requests >= 2);
  assert.equal(page.aligned, true, 'This connection received the entire page chain');
  const compressedPages = historyPages().length;
  if (uncompressed) assert.equal(compressedPages, 0);
  else assert.ok(compressedPages > 0, 'Expected at least one compressed public history page');

  const history = [...messages.values()];
  const posts = history.filter((message) => message.kind === 'user');
  assert.equal(posts.length, 271);
  assert.equal(posts.every(message => message.sender.id === message.sender.playerUuid
    && message.sender.name === message.sender.playerName), true);
  if (!uncompressed) {
    const wireMessages = historyPages().flatMap(event => event.messages);
    assert.ok(wireMessages.every(message => !('channel' in message)));
    const userMessages = wireMessages.filter(message => message.kind === 'user');
    assert.ok(userMessages.every(message => !('mentioned' in message)));
    const senders = userMessages.map(message => message.sender);
    assert.ok(senders.length > 0);
    assert.ok(senders.every(sender => sender.playerUuid && sender.playerName && !('id' in sender) && !('name' in sender)));
    const notice = wireMessages.find(message => message.kind === 'server');
    assert.equal(notice.content, 'gameJoined');
    assert.equal(notice.countsAsUnread, undefined);
  }
  assert.equal(posts.filter((message) => message.order && message.content[0].text.includes('跨端历史')).length, 270);
  const wiki = posts.find((message) => message.content[0].text === 'Minecraft Wiki: 钻石');
  assert.equal(wiki?.content[0].href, 'https://zh.minecraft.wiki/w/%E9%92%BB%E7%9F%B3');
  const notices = history.filter((message) => message.kind === 'server');
  assert.equal(notices.length, 1);
  assert.equal(notices[0].subject.name, 'Writer');
  assert.equal(notices[0].countsAsUnread, false);
  assert.equal(notices[0].content.template, 'gameJoined');
  assert.equal(notices[0].content.values.name, notices[0].subject.name);
  const delta = await session.readHistory({ channel: 'public', since: page.checkpoint });
  assert.equal(delta.messages.length, 0);
  assert.equal(delta.checkpoint, page.checkpoint);
  const beforePrivate = historyPages().length;
  const privatePage = await session.readHistory({ channel: 'private', targetUuid: '22222222-2222-4222-8222-222222222222' });
  assert.equal(privatePage.messages.length, 1);
  const privateText = privatePage.messages[0].content.map((part) => part.text).join('');
  assert.equal(privateText.trim(), '仅接收者可读 '.repeat(160).trim());
  assert.equal(historyPages().length - beforePrivate, uncompressed ? 0 : 1);
  const beforeMixed = historyPages().length;
  const mixed = await session.readHistory({ channel: 'private' });
  assert.equal(mixed.messages.length, 2);
  assert.equal(historyPages().length, beforeMixed, 'Different private peers must not share a compression context');
  const sentBeforeResume = writes.length;
  const current = await Promise.all(session.readHistories([
    { channel: 'public', since: page.checkpoint }, { channel: 'private', since: mixed.checkpoint },
  ]));
  assert.deepEqual(current.map(result => result.checkpoint), [page.checkpoint, mixed.checkpoint]);
  assert.ok(current.every(result => !result.messages.length));
  assert.equal(writes.length - sentBeforeResume, 1);
  assert.equal(writes.at(-1).lane, 1);
  console.log('ADD_PEER');
  const isOther = player => player.name === 'Other';
  const hasOther = event => event.type === 'presence' && event.players.some(isOther);
  const rosterDeadline = Date.now() + 5000;
  while (!events.some(hasOther) && Date.now() < rosterDeadline) {
    await new Promise(resolve => setTimeout(resolve, 5));
  }
  assert.ok(events.some(hasOther), 'Updated roster did not reach the browser');
  assert.equal(compressedEvents.some(event => hasOther(event) ||
    (event.type === 'presenceDelta' && event.upsert.some(({player}) => isOther(player)))), !uncompressed);
  const beforeSend = writes.length;
  const text = '跨端发送 🌍 minecraft.wiki/w/Diamond help@example.org @ Writer' + ' 好'.repeat(64);
  const sent = session.send({ channel: 'public' }, text);
  assert.equal(sent.ok, true);
  const deadline = Date.now() + 5000;
  // The durable receipt and live echo are independent events.
  let echo;
  let accepted;
  let checkpoint;
  while (Date.now() < deadline) {
    echo ??= events.find(
      (event) => event.type === 'message' && event.message.requestId === sent.requestId,
    );
    accepted ??= events.find(
      (event) => event.type === 'accepted' && event.requestId === sent.requestId,
    );
    checkpoint ??= events.find(event => event.type === 'checkpoint' && event.channel === 'public' && event.from === delta.checkpoint);
    if (echo && accepted) break;
    await new Promise((resolve) => setTimeout(resolve, 5));
  }
  assert.equal(writes.length - beforeSend, 1);
  assert.deepEqual(writes.at(-1), {lane: 0, kind: uncompressed ? 0 : 1});
  assert.equal(compressedEvents.some(event => event.type === 'message' && event.message.requestId === sent.requestId), !uncompressed);
  assert.ok(accepted, 'Missing encrypted acknowledgement');
  assert.ok(echo, 'Missing encrypted live echo');
  assert.equal(echo.message.id, accepted.messageId);
  assert.equal(echo.message.content.map((part) => part.text).join(''), '跨端发送 🌍 Minecraft Wiki: Diamond Email: help@example.org @ Writer' + ' 好'.repeat(64));
  assert.equal(echo.message.content[1].href, 'https://minecraft.wiki/w/Diamond');
  assert.equal(echo.message.content[3].href, 'mailto:help@example.org');
  assert.equal(echo.message.content.find(part => part.text === '@ Writer').mention, true);
  assert.equal(echo.message.mentioned, false, 'Do not notify the sender');
  // A checkpoint is coalesced for up to five seconds. Time it separately from message delivery.
  const progressDeadline = Date.now() + 6500;
  while (!checkpoint && Date.now() < progressDeadline) {
    checkpoint = events.find(event => event.type === 'checkpoint' && event.channel === 'public' && event.from === delta.checkpoint);
    if (!checkpoint) await new Promise(resolve => setTimeout(resolve, 10));
  }
  const progressEvents = events.filter(event => event.type === 'checkpoint').map(event =>
    ({ channel: event.channel, matchesStart: event.from === delta.checkpoint }));
  assert.ok(checkpoint, 'Missing progress after encrypted live delivery: ' + JSON.stringify(progressEvents));
  assert.ok(events.indexOf(echo) < events.indexOf(checkpoint));
  const resumed = await session.readHistory({ channel: 'public', since: checkpoint.to });
  assert.equal(resumed.reset, false);
  assert.deepEqual(resumed.messages, []);
  const [newPublic, samePrivate] = await Promise.all(session.readHistories([
    { channel: 'public', since: page.checkpoint }, { channel: 'private', since: mixed.checkpoint },
  ]));
  assert.ok(newPublic.messages.some(message => message.id === echo.message.id));
  assert.deepEqual(samePrivate.messages, []);
  assert.equal(samePrivate.checkpoint, mixed.checkpoint);
  // The public post advanced the database, but the private conversation has not changed.
  const quiet = await session.readHistory({
    channel: 'private', targetUuid: '22222222-2222-4222-8222-222222222222', since: privatePage.checkpoint,
  });
  assert.deepEqual(quiet.messages, []);
  assert.equal(quiet.checkpoint, privatePage.checkpoint);
  // Interrupt one page chain by starting another on the same channel.
  let stale = await session.readHistory({ channel: 'public' });
  const other = await session.readHistory({ channel: 'public' });
  assert.ok(stale.nextPage && other.nextPage && stale.nextPage !== other.nextPage);
  while (stale.nextPage) stale = await session.readHistory({ channel: 'public', page: stale.nextPage });
  assert.equal(stale.aligned, undefined);
  const caughtUp = await session.readHistory({ channel: 'public', since: stale.checkpoint });
  assert.equal(caughtUp.checkpoint, stale.checkpoint);
  assert.ok(session.hasHistory);
  // Discard the sender's accepted frame and close after the durable live echo. Two fresh
  // authenticated transports probe the same IDs without retransmitting the private text.
  const requestId = crypto.randomUUID();
  const posted = await rawRequest(WRITER, {
    type: 'send', channel: 'private', targetUuid: OWNER, requestId, text: '回执恢复',
  }, event => event.type === 'message' && event.message.requestId === requestId);
  const lookup = { type: 'receipts', requestIds: [requestId, crypto.randomUUID()] };
  const [recovered, foreign] = await Promise.all([WRITER, OTHER].map(player =>
    rawRequest(player, lookup, event => event.type === 'receipts')));
  assert.deepEqual(recovered.items.map(item => [item.requestId, item.messageId]), [[requestId, posted.message.id]]);
  assert.deepEqual(foreign.items, []);
  // Each staff browser has its own authenticated WebSocket. A public message marks the end
  // of the ordered broadcast so the ordinary member's absence check never races delivery.
  const staff = [browser(WRITER), browser(OTHER)];
  try {
    for (const client of staff) client.session.retry();
    await Promise.all(staff.map(client => client.online));
    // Stop consumption of the next history record and observe live delivery before releasing it.
    const paused = Promise.withResolvers();
    historyRead = paused.resolve;
    const resume = Promise.withResolvers();
    historyGate = resume.promise;
    const pendingPage = session.readHistory({channel: 'public'});
    await paused.promise;
    console.log('BROADCAST_STAFF');
    const streams = [events, ...staff.map(client => client.events)];
    const received = (stream, id) => stream.find(e => e.type === 'message' && e.message.id === id);
    const deadline = Date.now() + 5000;
    while (streams.some(stream => !received(stream, 'after-staff')) && Date.now() < deadline)
      await new Promise(resolve => setTimeout(resolve, 5));
    assert.ok(streams.every(stream => received(stream, 'after-staff')));
    const writerNotices = events.filter(event => event.type === 'message' &&
      event.message.kind === 'server' && event.message.subject.name === 'Writer');
    assert.deepEqual(writerNotices.map(event => event.message.content.template), ['webJoined']);
    resume.resolve();
    historyGate = undefined;
    assert.ok((await pendingPage).messages.length);
    assert.equal(received(events, 'staff-live'), undefined);
    const messages = staff.map(client => received(client.events, 'staff-live')?.message);
    assert.ok(messages.every(message => message?.channel === 'staff'));
    assert.deepEqual(messages[0], messages[1]);
    assert.equal(messages[0].content.map(part => part.text).join(''), 'management broadcast '.repeat(12).trim());
  } finally {
    for (const client of staff) client.session.stop();
  }
  console.log('REVOKE_OWNER');
  const revoked = () => events.some(event => event.type === 'closed' && event.reason === 'session_revoked');
  const revokeDeadline = Date.now() + 5000;
  while (!revoked() && Date.now() < revokeDeadline) await new Promise(resolve => setTimeout(resolve, 5));
  assert.ok(revoked(), 'Revocation must reach the application before transport teardown');
  assert.equal(session.hasHistory, false);
  console.log('PASSED');
} finally {
  session.stop();
}
