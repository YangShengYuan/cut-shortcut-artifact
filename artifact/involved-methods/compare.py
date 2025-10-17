import os
import sys
from colorama import init, Fore, Style

INVOLVED_HOME = os.path.dirname(os.path.realpath(__file__))
APPS = ['eclipse', 'freecol', 'briss', 'hsqldb', 'jedit', 'gruntspud', 'soot', 'columba', 'jython', 'findbugs']

def compare(app):
    print('Compare methods on %s' % (Fore.CYAN + Style.BRIGHT + app + Style.RESET_ALL))
    # read zippere results
    zippere_file = os.path.join(INVOLVED_HOME, '%s-zippere-selected-methods.txt' % (app))
    zippere_method = set()
    with open(zippere_file) as f:
        for l in f.readlines():
            zippere_method.add(l)
    # read csc results
    csc_file = os.path.join(INVOLVED_HOME, '%s-csc-involved-methods.txt' % (app))
    csc_method = set()
    with open(csc_file) as f:
        for l in f.readlines():
            csc_method.add(l)
    # compare
    print('%s selected %s methods on %s' % (Fore.GREEN + 'zippere' + Style.RESET_ALL, Fore.YELLOW + str(len(zippere_method)) + Style.RESET_ALL, app))
    print('%s selected %s methods on %s' % (Fore.GREEN + 'csc' + Style.RESET_ALL, Fore.YELLOW + str(len(csc_method)) + Style.RESET_ALL, app))
    count = 0
    for m in zippere_method:
        if m in csc_method:
            count += 1
    overlap = count/len(csc_method)
    print( Fore.YELLOW + f'{overlap * 100:.2f}%' + Style.RESET_ALL + 'of csc involved methods are also selected by zippere')

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
    compare(app)