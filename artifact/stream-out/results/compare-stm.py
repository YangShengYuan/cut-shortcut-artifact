import os
import sys
from colorama import init, Fore, Style

STMOUT_HOME = os.path.dirname(os.path.realpath(__file__))
EXPECTED_HOME = os.path.join(STMOUT_HOME, 'expected')
SYNTHETICS = ['simple-struc', 'build-stm', 'simple-func', 'stm-map', 'stm-flatMap',
              'stm-reduce', 'stm-collect', 'stm-coll-interact', 'primitive-stm', 'conserv-stm']
MOCK_DB = ['mockDB-1', 'mockDB-1-str', 'mockDB-2', 'mockDB-2-str', 'mockDB-3',
           'mockDB-3-str', 'mockDB-4', 'mockDB-4-str', 'mockDB-5', 'mockDB-5-str']
STATIC = ['ci', '1obj', 'stream-4obj-3h', 'csc', 'csc-s']

def compare(app):
    print('Compare stream out points-to result with expected on %s.' % (Fore.CYAN + Style.BRIGHT + app + Style.RESET_ALL))
    # read expected file
    expected_file = os.path.join(EXPECTED_HOME, '%s-expected.txt' % (app))
    expected_out = {}
    with open(expected_file, encoding='latin1') as f:
        for l in f.readlines():
            l = l.strip()
            var = l.split(' -> (')[0]
            raw_objs = l.split(')objs: ')[1]
            expected_out[var] = raw_objs.split(' |,| ')
    for analysis in STATIC:
        if app in MOCK_DB and '-str' in app and analysis == '1obj':
           continue
        analysis_out = {}
        # read static analysis output file
        static_file = os.path.join(STMOUT_HOME, '%s-%s-stream-out.txt' % (app, analysis))
        with open(static_file, encoding='latin1') as f:
            for l in f.readlines():
                l = l.strip()
                var = l.split(' -> (')[0]
                raw_objs = l.split(')objs: ')[1]
                analysis_out[var] = raw_objs.split(' |,| ')
        recall_count = 0
        expected_count = 0
        for (var, static_objs) in analysis_out.items():
            if var in expected_out.keys():
              expected_objs = expected_out[var]
              expected_count += len(expected_objs)
              for obj in expected_objs:
                  if obj in static_objs:
                      recall_count += 1
                  else:
                      print(var + ' -> ' + obj)
        print('%s objs out of %s expected objs are found under %s' % (Fore.YELLOW + str(recall_count) + Style.RESET_ALL, Fore.YELLOW + str(expected_count) + Style.RESET_ALL, Fore.CYAN + Style.BRIGHT + analysis + Style.RESET_ALL))

if __name__ == '__main__':
  init()
  apps = []
  for arg in sys.argv[1:]:
    if arg == 'synthetic':
      apps = SYNTHETICS
      break
    elif arg == 'mockDB':
      apps = MOCK_DB
      break
    else:
      apps.append(arg)
  for app in apps:
    compare(app)