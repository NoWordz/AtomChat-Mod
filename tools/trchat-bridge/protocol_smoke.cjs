const fs = require('node:fs');
const path = require('node:path');
const {randomBytes} = require('node:crypto');
const assert = require('node:assert/strict');

const root = path.resolve(__dirname, '../..');
const mc = require(process.env.ATOMCHAT_PROTOCOL_MODULE
  || path.join(root, 'build/v097-server-lab/drivers/node_modules/minecraft-protocol'));
const evidence = process.env.ATOMCHAT_CHANNEL_EVIDENCE || path.join(root, 'build/trchat-bridge');
const channel = 'atomchat:trchat_channel';
const timeline = [];
const players = [];
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

function record(event) {
  timeline.push(event);
  fs.appendFileSync(path.join(evidence, 'protocol-smoke.ndjson'), JSON.stringify(event) + '\n');
}

function unpack(buf) {
  assert.equal(buf[0], 1);
  const kind = buf[1];
  let pos = 2;
  const uuid = () => { const value = buf.subarray(pos, pos + 16).toString('hex'); pos += 16; return value; };
  const utf = () => {
    const length = buf.readUInt16BE(pos);
    pos += 2;
    const val = buf.subarray(pos, pos + length).toString();
    pos += length;
    return val;
  };
  const session = uuid();
  if (kind === 1) return {kind, session};
  const nonce = uuid();
  const sequence = Number(buf.readBigInt64BE(pos));
  pos += 8;
  if (kind === 3) {
    const count = buf[pos++];
    const channels = Array.from({length: count}, () => ({backend: utf(), channel: utf(), label: utf()}));
    return {kind, session, nonce, sequence, channels};
  }
  assert.equal(kind, 4);
  return {kind, session, nonce, sequence, backend: utf(), channel: utf(),
          id: uuid(), author: uuid(), authorName: utf(), component: utf()};
}

async function waitFor(label, predicate, duration = 20000) {
  const deadline = Date.now() + duration;
  while (Date.now() < deadline) {
    if (predicate()) return;
    await sleep(100);
  }
  throw new Error('Timed out waiting for ' + label);
}

async function play(name, advertise = true) {
  const client = mc.createClient({host: '127.0.0.1', port: Number(process.env.ATOMCHAT_CHANNEL_PORT || 25727),
    auth: 'offline', username: name, version: process.env.ATOMCHAT_CHANNEL_MC || '1.21.1', disableChatSigning: true});
  players.push(client);
  const nonce = randomBytes(16);
  client.on('packet', (data, packet) => {
    if (packet.name === 'custom_payload' && data.channel === 'minecraft:register' && data.data.toString().includes(channel)) {
      record({name, serverAdvertised: channel});
    }
    if (packet.name === 'custom_payload' && data.channel === channel) {
      const frame = unpack(data.data);
      record({name, ...frame});
      if (frame.kind === 1) {
        client.write('custom_payload', {channel, data: Buffer.concat([
          Buffer.from([1, 2]), Buffer.from(frame.session, 'hex'), nonce
        ])});
      }
    }
    if (['system_chat', 'profileless_chat', 'player_chat'].includes(packet.name)) {
      const plain = JSON.stringify(data);
      if (plain.includes('ACCH-')) record({name, packet: packet.name, plain});
    }
  });
  client.on('error', error => record({name, error: error.message}));
  if (advertise) client.on('login', () => {
    client.write('custom_payload', {channel:'minecraft:register', data:Buffer.from(channel)});
  });
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Login timeout for ' + name)), 20000);
    client.once('login', () => {clearTimeout(timer);resolve();});
  });
  if (advertise) {
    await waitFor(name + ' current snapshot', () => frames(name, 3).some(frame => frame.nonce === nonce.toString('hex')));
  }
  return client;
}

const frames = (name, kind) => timeline.filter(e => e.name === name && e.kind === kind);
const observed = (name, kind, marker) => frames(name, kind).filter(e => e.component && e.component.includes(marker));
const ordinary = (name, marker) => timeline.filter(e => e.name === name && e.plain && e.plain.includes(marker));
const readyFile = name => path.join(evidence, name);
const keys = name => frames(name, 3).at(-1).channels.map(x => x.channel);

async function run() {
  const a = await play('BridgeA');
  const b = await play('BridgeB');
  const c = await play('BridgeC');
  await play('BridgeD', false);
  fs.writeFileSync(readyFile('protocol-joined'), '');
  await waitFor('permissions configured', () => fs.existsSync(readyFile('protocol-granted')), 60000);
  const chat = async (client, text, delay = 2250) => {client.chat(text); await sleep(delay);};
  await chat(a, '/channel join alpha', 650);
  await chat(b, '/channel join alpha', 650);
  await chat(c, '/channel join alpha', 650);
  await chat(a, 'ACCH-SAME: [body] text');
  await waitFor('alpha delivery', () => observed('BridgeB', 4, 'ACCH-SAME').length === 1);
  assert.equal(observed('BridgeB', 4, 'ACCH-SAME')[0].channel, 'alpha');
  assert.equal(ordinary('BridgeB', 'ACCH-SAME').length, 0);
  assert.equal(ordinary('BridgeD', 'ACCH-SAME').length, 1);
  assert.equal(observed('BridgeC', 4, 'ACCH-SAME').length, 0);
  assert.equal(ordinary('BridgeC', 'ACCH-SAME').length, 0);
  await chat(a, '/channel join beta', 650);
  await chat(b, '/channel join beta', 650);
  await chat(a, 'ACCH-SAME: [body] text');
  await waitFor('beta delivery', () => observed('BridgeB', 4, 'ACCH-SAME').length === 2);
  const messages = observed('BridgeB', 4, 'ACCH-SAME');
  assert.deepEqual(messages.map(m => m.channel), ['alpha','beta']);
  assert.notEqual(messages[0].id, messages[1].id);
  assert.equal(messages[0].author, messages[1].author);
  assert.equal(messages[1].authorName, 'BridgeA');
  assert.equal(messages[1].backend, 'local');
  assert.equal(ordinary('BridgeB', 'ACCH-SAME').length, 0);
  await chat(a, '/msg BridgeB ACCH-PRIVATE');
  assert.equal(observed('BridgeB', 4, 'ACCH-PRIVATE').length, 0);
  assert.equal(ordinary('BridgeB', 'ACCH-PRIVATE').length, 1);
  assert.equal(ordinary('BridgeC', 'ACCH-PRIVATE').length, 0);
  fs.writeFileSync(readyFile('protocol-beta-ready'), '');
  await waitFor('revocation configured', () => fs.existsSync(readyFile('protocol-revoked')), 60000);
  await waitFor('authority snapshot revoked beta', () => !keys('BridgeB').includes('beta'));
  await chat(a, 'ACCH-REVOKED: [body] text');
  assert.equal(observed('BridgeB', 4, 'ACCH-REVOKED').length + ordinary('BridgeB', 'ACCH-REVOKED').length, 0);
  const previous = frames('BridgeB', 3).at(-1).session;
  b.end('reconnect');
  await sleep(600);
  await play('BridgeB');
  assert.notEqual(frames('BridgeB', 3).at(-1).session, previous);
  assert(!keys('BridgeB').includes('beta'));
  await chat(a, 'ACCH-RECONNECT: [body] text');
  assert.equal(observed('BridgeB', 4, 'ACCH-RECONNECT').length + ordinary('BridgeB', 'ACCH-RECONNECT').length, 0);
  record({result:'pass', scope:['same-text-two-channels','negotiated-no-ordinary-copy','vanilla-fallback',
    'unjoined-denial','authors','private-plain-isolated','revocation','reconnection']});
  console.log('Channel protocol smoke passed');
}
async function velocity() {
  const a = await play('BridgeA');
  const b = await play('BridgeB');
  const c = await play('BridgeC');
  const oldSession = frames('BridgeB', 3).at(-1).session;
  b.chat('/server west');
  c.chat('/server west');
  await waitFor('receiver backend session', () => frames('BridgeB', 3).at(-1).session !== oldSession);
  fs.writeFileSync(readyFile('protocol-joined'), '');
  await waitFor('receiving backend granted separately', () => fs.existsSync(readyFile('protocol-granted')), 60000);
  b.chat('/channel join Global');
  await waitFor('sender scope in receiver snapshot', () =>
    frames('BridgeB', 3).at(-1).channels.some(c => c.backend === 'east' && c.channel === 'global'));
  a.chat('/global ACCH-PROXY: exact cross-backend body');
  await waitFor('cross-backend delivery', () => observed('BridgeB', 4, 'ACCH-PROXY').length === 1);
  const [msg] = observed('BridgeB', 4, 'ACCH-PROXY');
  assert.equal(msg.backend, 'east');
  assert.equal(msg.channel, 'global');
  assert.equal(msg.authorName, 'BridgeA');
  assert.equal(ordinary('BridgeB', 'ACCH-PROXY').length, 0);
  assert.equal(ordinary('BridgeC', 'ACCH-PROXY').length, 0);
  assert.equal(observed('BridgeC', 4, 'ACCH-PROXY').length, 0);
  fs.writeFileSync(readyFile('protocol-beta-ready'), '');
  await waitFor('receiver permission revoked', () => fs.existsSync(readyFile('protocol-revoked')), 60000);
  await waitFor('receiver scope removed', () => !keys('BridgeB').includes('global'));
  await sleep(2400);
  a.chat('/global ACCH-PROXY-REVOKED: body');
  await waitFor('sender receives the post-revocation message', () =>
    observed('BridgeA', 4, 'ACCH-PROXY-REVOKED').length === 1);
  await sleep(1200);
  assert.equal(observed('BridgeB', 4, 'ACCH-PROXY-REVOKED').length + ordinary('BridgeB', 'ACCH-PROXY-REVOKED').length, 0);
  record({result: 'pass', scope:['actual-velocity-double-transfer','sender-stable-backend','receiver-permissions',
    'receiver-session-reset','receiver-revocation']});
  console.log('Velocity channel protocol smoke passed');
}

async function failures() {
  const a = await play('BridgeA');
  const b = await play('BridgeB');
  await play('BridgeD', false);
  fs.writeFileSync(readyFile('protocol-joined'), '');
  await waitFor('permissions configured', () => fs.existsSync(readyFile('protocol-granted')), 60000);
  b.chat('/channel join beta');
  await waitFor('beta membership', () => keys('BridgeB').includes('beta'));
  b.chat('/channel join alpha');
  await waitFor('alpha membership', () => keys('BridgeB').includes('alpha'));
  fs.writeFileSync(readyFile('failure-fixture-ready'), '');
  await waitFor('healthy control after negative cases', () => observed('BridgeB', 4, 'ACCH-HEALTHY').length === 1);
  assert.equal(ordinary('BridgeD', 'ACCH-ORDINARY').length, 1);
  const blocked = ['MISSING', 'OVERSIZED', 'SNAPSHOT', 'UNREGISTERED'].map(name => ({
    name, native: ordinary('BridgeB', 'ACCH-' + name).length,
    channel: observed('BridgeB', 4, 'ACCH-' + name).length
  }));
  record({blocked});
  assert.deepEqual(blocked.map(e => [e.native, e.channel]), blocked.map(() => [0, 0]));
  record({result:'pass', scope:['unresolved-channel','oversized-component','snapshot-unavailable',
    'unregister-cannot-downgrade','eligible-plain-control','healthy-negotiated-control']});
  console.log('Channel failure boundaries passed');
}

const scenario = process.env.ATOMCHAT_CHANNEL_SCENARIO;
(scenario === 'velocity' ? velocity() : scenario === 'failures' ? failures() : run()).catch(error => {
  record({result:'fail', error:error.message});
  console.error(error);
  process.exitCode = 1;
}).finally(() => players.forEach(player => player.end('finished')));
