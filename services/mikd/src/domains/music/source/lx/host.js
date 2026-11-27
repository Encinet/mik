((host, scriptInfo) => {
  let handler;
  let initialized;
  let ready;
  let activeAction;
  const initialization = new Promise(resolve => { ready = resolve; });
  const finish = () => { if (handler && initialized) ready(initialized); };
  const events = Object.freeze({ request: 'request', inited: 'inited', updateAlert: 'updateAlert' });
  const bytes = value => value instanceof Uint8Array ? value : new Uint8Array(value ?? []);
  // Copy the view in QuickJS; Rust never borrows a buffer the script can detach or resize.
  const toBase64 = Function.call.bind(Uint8Array.prototype.toBase64);
  const byteLength = Function.call.bind(Object.getOwnPropertyDescriptor(
    Object.getPrototypeOf(Uint8Array.prototype), 'byteLength').get);
  const pack = value => {
    const view = bytes(value);
    if (byteLength(view) > host.maxBytes) throw new RangeError('oversized LX byte array');
    return toBase64(view);
  };
  const decode = (value, encoding) => host.bufferFrom(value, encoding);
  // LX desktop's Node cipher interprets string inputs as UTF-8, not byte array lengths.
  const packAesInput = value => {
    if (typeof value !== 'string') return pack(value);
    if (value.length > host.maxBytes) throw new RangeError('oversized LX byte array');
    return pack(decode(value, 'utf8'));
  };
  const encode = (value, encoding) => {
    const packed = pack(value);
    return encoding === 'base64' ? packed : host.bufferToString(packed, encoding);
  };
  const timers = new Map();
  let nextTimer = 0;
  const clear = id => {
    const cancel = timers.get(id);
    timers.delete(id);
    cancel?.();
  };
  const timer = (repeat, callback, delay, args) => {
    if (typeof callback !== 'function' || timers.size >= 128) throw new Error('invalid or excessive LX timers');
    const id = ++nextTimer;
    delay = Math.min(86400000, Math.max(repeat ? 1 : 0, Math.trunc(Number(delay)) || 0));
    const schedule = () => {
      let cancelled = false;
      timers.set(id, () => { cancelled = true; host.cancelTimer(id); });
      host.sleep(id, delay).then(() => {
        if (cancelled || !timers.has(id)) return;
        if (!repeat) timers.delete(id);
        try { callback(...args); } finally { if (repeat && !cancelled) schedule(); }
      }, () => { timers.delete(id); });
    };
    schedule();
    return id;
  };
  globalThis.setTimeout = (callback, delay, ...args) => timer(false, callback, delay, args);
  globalThis.setInterval = (callback, delay, ...args) => timer(true, callback, delay, args);
  globalThis.clearTimeout = globalThis.clearInterval = clear;
  const discardLog = () => {};
  globalThis.console = {
    debug: discardLog,
    log: discardLog,
    info: discardLog,
    warn: discardLog,
    error: discardLog,
    trace: discardLog,
    group: discardLog,
    groupCollapsed: discardLog,
    groupEnd: discardLog,
  };
  globalThis.window = globalThis;
  const request = (url, options, callback) => {
    let cancelled = false;
    const id = ++nextTimer;
    const action = activeAction;
    const body = options?.body;
    const safe = {
      ...options,
      body: ArrayBuffer.isView(body) || body instanceof ArrayBuffer
        ? { __mikBytesBase64: encode(body, 'base64') } : body,
    };
    host.request(id, String(url), JSON.stringify(safe)).then(text => {
      if (cancelled) return;
      const response = JSON.parse(text);
      if (response.error) { callback(new Error(response.error), null, null); return; }
      if (action) {
        const code = response.body?.code;
        action.response = `HTTP ${response.statusCode}`;
        if (Number.isSafeInteger(code) && Math.abs(code) <= 99999) action.response += `, body.code ${code}`;
      }
      response.raw = decode(response.raw, 'base64');
      callback(null, response, response.body);
    }, error => { if (!cancelled) callback(error, null, null); });
    return () => { cancelled = true; host.cancelRequest(id); };
  };
  globalThis.lx = Object.freeze({
    EVENT_NAMES: events,
    on(event, callback) { if (event === events.request) { handler = callback; finish(); } return Promise.resolve(); },
    send(event, info) {
      if (event === events.inited) {
        if (initialized) throw new Error('LX inited event already sent');
        initialized = info;
        finish();
      }
      return Promise.resolve();
    },
    request,
    setTimeout, setInterval, clearTimeout, clearInterval,
    currentScriptInfo: Object.freeze(JSON.parse(scriptInfo)),
    version: '2.0.0',
    env: 'desktop',
    utils: Object.freeze({
      buffer: Object.freeze({
        from: (value, encoding = 'utf8') => typeof value === 'string' ? decode(value, encoding) : Uint8Array.from(bytes(value)),
        bufToString: (value, encoding = 'utf8') => encode(value, encoding),
      }),
      crypto: Object.freeze({
        md5: value => host.md5(String(value)),
        randomBytes: size => host.randomBytes(size),
        aesEncrypt: (value, mode, key, iv) => host.aes(packAesInput(value), mode, packAesInput(key), packAesInput(iv)),
        rsaEncrypt: (value, key) => host.rsa(pack(value), key),
      }),
      zlib: Object.freeze({
        inflate: async value => host.compress(pack(value), true),
        deflate: async value => host.compress(pack(value), false),
      }),
    }),
  });
  return Object.freeze({
    initialized: initialization.then(value => JSON.stringify(value)),
    async dispatch(value) {
      const action = {};
      activeAction = action;
      try {
        return JSON.stringify(await handler(JSON.parse(value))) ?? 'null';
      } catch (error) {
        if (!(error instanceof Error) || error.message !== '') throw error;
        const response = action.response ? ` (last response ${action.response})` : '';
        throw new Error(`LX script failed without an error message${response}`);
      } finally {
        if (activeAction === action) activeAction = undefined;
      }
    },
  });
})
