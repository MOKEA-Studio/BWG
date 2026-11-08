"""Test-only transparent CONNECT relay. End-to-end TLS and authentication remain unchanged.

The Work egress proxy takes slightly over Geyser's fixed five-second CONNECT timeout.
Pre-open authorized upstream TCP tunnels for its two public discovery endpoints.
No TLS interception, certificate changes, public-key substitution, or authentication bypass.
Never distribute this relay as part of the server installation.
"""
import os,socket,socketserver,threading,time,collections,urllib.parse,base64,select
up=urllib.parse.urlparse(os.environ['HTTPS_PROXY'])
targets=['client.discovery.minecraft-services.net:443','authorization.franchise.minecraft-services.net:443']
pools={t:collections.deque() for t in targets};lock=threading.Lock()
def tunnel(target):
 s=socket.create_connection((up.hostname,up.port or 80),timeout=45);s.settimeout(45)
 auth=''
 if up.username is not None:
  token=base64.b64encode((urllib.parse.unquote(up.username)+':'+urllib.parse.unquote(up.password or '')).encode()).decode();auth='Proxy-Authorization: Basic '+token+'\r\n'
 s.sendall(('CONNECT '+target+' HTTP/1.1\r\nHost: '+target+'\r\n'+auth+'\r\n').encode());header=b''
 while not header.endswith(b'\r\n\r\n'):
  data=s.recv(1)
  if not data or len(header)>65536:raise OSError('upstream proxy closed CONNECT')
  header+=data
 if header.split(b' ',2)[1]!=b'200':s.close();raise OSError('upstream CONNECT was not allowed')
 s.settimeout(None);return s
def fill(target):
 while True:
  with lock:
   q=pools[target]
   while q and time.monotonic()-q[0][0]>30:q.popleft()[1].close()
   needed=len(q)<2
  if needed:
   try:
    s=tunnel(target)
    with lock:pools[target].append((time.monotonic(),s))
   except OSError:time.sleep(.5)
  else:time.sleep(.1)
class Handler(socketserver.BaseRequestHandler):
 def handle(self):
  upstream=None
  try:
   header=b'';self.request.settimeout(45)
   while not header.endswith(b'\r\n\r\n'):
    data=self.request.recv(1)
    if not data or len(header)>65536:return
    header+=data
   method,target,_=header.split(b'\r\n',1)[0].decode('ascii').split(' ',2)
   if method!='CONNECT':self.request.sendall(b'HTTP/1.1 405 Method Not Allowed\r\n\r\n');return
   with lock:
    q=pools.get(target)
    if q:
     while q and time.monotonic()-q[0][0]>30:q.popleft()[1].close()
     if q:upstream=q.popleft()[1]
   if upstream is None:upstream=tunnel(target)
   self.request.sendall(b'HTTP/1.1 200 Connection Established\r\n\r\n');self.request.settimeout(None)
   sockets=[self.request,upstream]
   while True:
    ready,_,_=select.select(sockets,[],[],60)
    if not ready:return
    for s in ready:
     data=s.recv(65536)
     if not data:return
     (upstream if s is self.request else self.request).sendall(data)
  except (OSError,ValueError):return
  finally:
   if upstream:upstream.close()
class Server(socketserver.ThreadingTCPServer):allow_reuse_address=True;daemon_threads=True
for target in targets:threading.Thread(target=fill,args=(target,),daemon=True).start()
deadline=time.monotonic()+60
while time.monotonic()<deadline:
 with lock:ready=all(pools[t] for t in targets)
 if ready:break
 time.sleep(.1)
if not ready:raise SystemExit('Could not prime public-discovery CONNECT tunnels')
with Server(('127.0.0.1',int(os.environ.get('SMP_TEST_PROXY_PORT','26880'))),Handler) as server:print('READY',flush=True);server.serve_forever()
