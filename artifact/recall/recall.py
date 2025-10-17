import os
import sys
from colorama import init, Fore, Style

RECALL_HOME = os.path.dirname(os.path.realpath(__file__))
RESULT_HOME = os.path.join(RECALL_HOME, 'results')
OUTPUT_MISSED = True
DYNAMIC = 'dynamic'
STATICS = ['ci', '2obj', '2type', 'ze-2obj', 'ze-2type', 'csc']
APPS = ['eclipse', 'freecol', 'briss', 'hsqldb', 'jedit', 'gruntspud', 'soot', 'columba', 'jython', 'findbugs']
SUFFIXES = ['-reach-mtd.txt', '-call-edge.txt']

def compare(dynamic, static, missed_out):
  statics = set()
  with open(static) as f:
    for l in f.readlines():
      statics.add(l)
  missed = []
  n = 0
  with open(dynamic) as f:
    for l in f.readlines():
      n += 1
      if l not in statics:
        missed.append(l)
  hits = n - len(missed)
  print('Recall: %s/%s (%s%.2f%%%s)' % (Fore.YELLOW + str(hits) + Style.RESET_ALL, Fore.YELLOW + str(n) + Style.RESET_ALL, Fore.YELLOW, float(hits) / n * 100, Style.RESET_ALL))
  if OUTPUT_MISSED:
    with open(missed_out, 'w') as out:
      print('Output missed items to %s' % missed_out)
      for l in missed:
        out.write(l)
  return missed

def recall(app):
  print('Recall on %s' % (Fore.CYAN + Style.BRIGHT + app + Style.RESET_ALL))
  for suffix in SUFFIXES:
    dynamic = os.path.join(RECALL_HOME, DYNAMIC, app + suffix)
    for stat in STATICS:
      static = os.path.join(RECALL_HOME, app + '-' + stat + suffix)
      if os.path.exists(static):
        print('Compare %s with %s on %s' % (Fore.GREEN + stat + Style.RESET_ALL, Fore.GREEN + DYNAMIC + Style.RESET_ALL, app + suffix[0:-4]))
        missed_out = os.path.join(RESULT_HOME, '%s-%s-missed%s' % (app,stat, suffix))
        compare(dynamic, static, missed_out)
  print()

if __name__ == '__main__':
  init()
  apps = []
  for arg in sys.argv[1:]:
    if arg == 'all':
      apps = APPS
      break
    else:
      apps.append(arg)
  for app in apps:
    recall(app)
