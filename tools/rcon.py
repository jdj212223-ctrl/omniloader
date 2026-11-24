"""Tiny RCON client for testing: python3 tools/rcon.py "command" [port] [password]"""
import socket, struct, sys

def packet(rid, kind, body):
    data = struct.pack('<ii', rid, kind) + body.encode() + b'\x00\x00'
    return struct.pack('<i', len(data)) + data

def recv(s):
    n = struct.unpack('<i', s.recv(4))[0]
    buf = b''
    while len(buf) < n:
        buf += s.recv(n - len(buf))
    rid, kind = struct.unpack('<ii', buf[:8])
    return rid, buf[8:-2].decode(errors='replace')

cmd = sys.argv[1]
port = int(sys.argv[2]) if len(sys.argv) > 2 else 25599
pw = sys.argv[3] if len(sys.argv) > 3 else 'omnitest'
s = socket.create_connection(('127.0.0.1', port), timeout=10)
s.send(packet(1, 3, pw)); rid, _ = recv(s)
if rid == -1: sys.exit('bad rcon password')
s.send(packet(2, 2, cmd)); print(recv(s)[1])
