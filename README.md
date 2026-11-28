# BlackCore Remote

Consent-based remote device management, rebuilt from a clean slate.

## MVP
- Pairing flow initiated on the target device with a short-lived code.
- Authenticated relay and device heartbeat.
- Initial allowlisted commands: PING, STATUS, RING.
- Visible foreground notification on Android while connected.
- No stealth, arbitrary shell, covert camera/microphone, clipboard harvesting, or file exfiltration.

## Structure
- server/: relay prototype
- android/: client planned next

## Local relay
1. cd server && npm install
2. Copy .env.example to .env and set a strong random OWNER_TOKEN.
3. npm start

Prototype uses volatile memory only. Do not expose publicly until TLS, persistence, rate limiting, and audit logging are configured.
