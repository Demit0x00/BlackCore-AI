import { createServer } from 'node:http';
import { randomInt, timingSafeEqual } from 'node:crypto';
import { WebSocketServer, WebSocket } from 'ws';

const PORT = Number(process.env.PORT || 8080);
const OWNER_TOKEN = process.env.OWNER_TOKEN || '';
const CODE_TTL = Number(process.env.PAIR_CODE_TTL_SECONDS || 180);
if (OWNER_TOKEN.length < 32) { console.error('OWNER_TOKEN must be at least 32 characters; configure .env'); process.exit(1); }

const devices = new Map();
const pending = new Map();
const server = createServer((req, res) => {
  if (req.url === '/health' && req.method === 'GET') {
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end(JSON.stringify({ ok: true, onlineDevices: devices.size }));
    return;
  }
  res.writeHead(404); res.end('not found');
});
const wss = new WebSocketServer({ server, maxPayload: 16 * 1024, perMessageDeflate: false });
const auth = (token) => {
  if (!token || token.length !== OWNER_TOKEN.length) return false;
  return timingSafeEqual(Buffer.from(token), Buffer.from(OWNER_TOKEN));
};
const send = (ws, obj) => { if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(obj)); };
function cleanup() { const now = Date.now(); for (const [code, entry] of pending) if (entry.expiresAt <= now || entry.ws.readyState !== WebSocket.OPEN) pending.delete(code); }

wss.on('connection', (ws) => {
  let role = null, id = null;
  ws.on('message', (raw) => {
    let msg; try { msg = JSON.parse(raw.toString()); } catch { send(ws, { type: 'ERROR', message: 'Invalid JSON' }); return; }
    if (!role) {
      if (ws._pairApproved && msg.type === 'DEVICE_READY') {
        role = 'device'; id = ws._pairedDeviceId; devices.set(id, ws);
        broadcast({ type: 'DEVICE_ONLINE', deviceId: id }); return;
      }
      if (msg.type === 'OWNER_AUTH' && auth(msg.token)) { role = 'owner'; send(ws, { type: 'AUTH_OK', devices: [...devices.keys()] }); return; }
      if (msg.type === 'DEVICE_PAIR_REQUEST') {
        cleanup(); const code = String(randomInt(100000, 999999));
        pending.set(code, { expiresAt: Date.now() + CODE_TTL * 1000, ws });
        send(ws, { type: 'PAIR_CODE', code, expiresIn: CODE_TTL }); return;
      }
      send(ws, { type: 'ERROR', message: 'Authenticate or pair first' }); return;
    }
    if (role === 'device') {
      if (msg.type === 'HEARTBEAT') { send(ws, { type: 'HEARTBEAT_ACK', at: Date.now() }); return; }
      if (msg.type === 'COMMAND_RESULT' && typeof msg.commandId === 'string') {
        broadcast({ type: 'COMMAND_RESULT', deviceId: id, commandId: msg.commandId, result: String(msg.result || '').slice(0, 1000) }); return;
      }
      send(ws, { type: 'ERROR', message: 'Unsupported device message' }); return;
    }
    if (role === 'owner') {
      if (msg.type === 'LIST_DEVICES') { send(ws, { type: 'DEVICES', devices: [...devices.keys()] }); return; }
      if (msg.type === 'PAIR_DEVICE' && typeof msg.code === 'string' && typeof msg.deviceId === 'string' && /^[a-zA-Z0-9_-]{1,64}$/.test(msg.deviceId)) {
        cleanup(); const entry = pending.get(msg.code);
        if (!entry || entry.expiresAt <= Date.now()) { send(ws, { type: 'ERROR', message: 'Pairing code expired or invalid' }); return; }
        if (devices.has(msg.deviceId)) { send(ws, { type: 'ERROR', message: 'Device ID already paired/online' }); return; }
        pending.delete(msg.code); const deviceSocket = entry.ws; deviceSocket._pairedDeviceId = msg.deviceId;
        deviceSocket._pairApproved = true; deviceSocket._pairCode = msg.code;
        send(deviceSocket, { type: 'PAIRED', deviceId: msg.deviceId });
        send(ws, { type: 'PAIR_SUCCESS', deviceId: msg.deviceId });
        return;
      }

      const allowed = new Set(['PING', 'STATUS', 'RING']);
      if (msg.type === 'COMMAND' && allowed.has(msg.command) && typeof msg.deviceId === 'string' && typeof msg.commandId === 'string' && /^[a-zA-Z0-9_-]{1,64}$/.test(msg.commandId)) {
        const target = devices.get(msg.deviceId);
        if (!target || target.readyState !== WebSocket.OPEN) { send(ws, { type: 'ERROR', message: 'Device offline' }); return; }
        send(target, { type: 'COMMAND', command: msg.command, commandId: msg.commandId });
        send(ws, { type: 'COMMAND_SENT', commandId: msg.commandId }); return;
      }
      send(ws, { type: 'ERROR', message: 'Unsupported command' });
    }
  });
  ws.on('close', () => {
    if (role === 'device' && devices.get(id) === ws) { devices.delete(id); broadcast({ type: 'DEVICE_OFFLINE', deviceId: id }); }
  });
});
function broadcast(obj) { for (const ws of wss.clients) if (ws.readyState === WebSocket.OPEN) send(ws, obj); }
server.listen(PORT, '0.0.0.0', () => console.log(`Relay listening on :${PORT}; use TLS proxy in production`));
