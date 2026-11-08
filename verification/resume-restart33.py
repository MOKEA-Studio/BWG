import importlib.util,pathlib,threading,signal
spec=importlib.util.spec_from_file_location('fixture',str(pathlib.Path(__file__).with_name('run-update33.py')));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
original=m.subprocess.Popen

def popen(args,*a,**kw):
 p=original(args,*a,**kw)
 if '-jar' in args and 'paper.jar' in args:
  def diagnose():
   if p.poll() is None and 'Done (' not in m.text(m.r/'restart33-resume.log'):
    p.send_signal(signal.SIGQUIT)
  timer=threading.Timer(65,diagnose);timer.daemon=True;timer.start()
 return p
m.subprocess.Popen=popen
m.standalone('restart33-resume','restart','RESTART_READY')
assert not (m.r/'FAILED').exists()
(m.r/'ALL_PASSED').write_text('OK')
print('ALL NATIVE CHECKS PASSED AFTER RESTART VERIFICATION',flush=True)
