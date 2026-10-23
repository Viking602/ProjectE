from pathlib import Path
import socket,struct,sys
props=dict(line.split('=',1) for line in Path('../test-projecte-server/server.properties').read_text(encoding='utf-8').splitlines() if '=' in line and not line.startswith('#'))
sock=socket.create_connection(('127.0.0.1',int(props['rcon.port'])),timeout=30)
def read(n):
 data=b''
 while len(data)<n:
  part=sock.recv(n-len(data))
  if not part:raise EOFError('RCON disconnected')
  data+=part
 return data
def packet(kind,text):
 body=struct.pack('<ii',7,kind)+text.encode()+b'\0\0';sock.sendall(struct.pack('<i',len(body))+body)
 size=struct.unpack('<i',read(4))[0];payload=read(size)
 ident,kind=struct.unpack('<ii',payload[:8])
 if ident<0:raise RuntimeError('RCON authentication failed')
 return payload[8:-2].decode('utf-8')
packet(3,props['rcon.password'])
for command in sys.argv[1:]:print(command+'\n'+packet(2,command))
sock.close()
