import os
import sys
import shutil
from pathlib import Path
from colorama import init, Fore, Style

APPS = {'eclipse':'eclipse', 'freecol':'freecol-0.10.3', 'briss':'briss-0.9', 'hsqldb':'hsqldb', 'jedit':'jedit-3.0', 
        'gruntspud':'gruntspud-0.4.6', 'soot':'soot-2.3.0', 'columba':'columba-1.4', 'jython':'jython', 'findbugs':'findbugs-3.0'}
ANALYSIS = ['ci', '2obj', '2type', 'ze-2obj', 'ze-2type', 'csc']
ANALYSIS_PATTERN = ['csc-f', 'csc-fc', 'csc-fcl', 'ci']
ANALYSIS_COMPARE = ['csc', 'ze-2obj']
UNSCALABLE = [['eclipse','2obj'], ['freecol','2obj'], ['freecol','2type'],['briss','2obj'], ['briss','2type'], ['hsqldb','2obj'], ['jedit','2obj'],
              ['gruntspud','2obj'], ['jython','2obj'], ['soot','2obj'], ['columba','2obj'], ['gruntspud','2type'], ['soot','2type'], ['columba','2type'],['jython','2type']]
FORCED = False

def exec(app, jdk, analysis, involved = False):
    cmd = 'java -Xms128g -Xmx128g -XX:+UseG1GC -jar tai-e-csc.jar '
    # app
    if [app, analysis] in UNSCALABLE and not FORCED:
        return
    else:
        app_name = APPS[app]
        cmd += '%s ' % (app_name)
    # jdk
    if jdk == 6:
        cmd += '-java=6 '
    elif jdk == 8:
        cmd += '-java=8 '
    else:
        "wrong jdk version"
    # analysis
    if analysis == 'ci':
        cmd += '-cs=ci '
    elif analysis == '2obj':
        cmd += '-cs=2-obj'
    elif analysis == '2type':
        cmd += '-cs=2-type'
    elif analysis == 'ze-2obj':
        if involved:
            cmd += '-cs=2-obj -advanced=zipper-e-dump'
        else:
            cmd += '-cs=2-obj -advanced=zipper-e'
    elif analysis == 'ze-2type':
        cmd += '-cs=2-type -advanced=zipper-e'
    elif analysis == 'csc':
        if involved:
            cmd += '-cs=ci -advanced=cut-shortcut-involved'
        else:
            cmd += '-cs=ci -advanced=cut-shortcut'
    elif analysis == 'csc-f':
        cmd += '-cs=ci -advanced=cut-shortcut-f'
    elif analysis == 'csc-c':
        cmd += '-cs=ci -advanced=cut-shortcut-c'
    elif analysis == 'csc-l':
        cmd += '-cs=ci -advanced=cut-shortcut-l'
    elif analysis == 'csc-fc':
        cmd += '-cs=ci -advanced=cut-shortcut-fc'
    elif analysis == 'csc-fcl':
        cmd += '-cs=ci -advanced=cut-shortcut-fcl'
    else:
        print("wrong analysis")
    print(cmd)
    print(Fore.YELLOW + Style.BRIGHT + 'Running ' + analysis + ' for ' + app + ' on Tai-e ... ' + Style.RESET_ALL)
    os.system(cmd)
    # move call-edge and reach-mtd outputs
    base_dir = os.path.dirname(os.path.abspath(__file__))
    edge_src_path = os.path.join(base_dir, 'output', 'call-edges.txt')
    edge_dst_path = os.path.join(base_dir, 'recall', '%s-%s-call-edge.txt' % (app, analysis))
    shutil.move(edge_src_path, edge_dst_path)
    mtd_src_path = os.path.join(base_dir, 'output', 'reachable-methods.txt')
    mtd_dst_path = os.path.join(base_dir, 'recall', '%s-%s-reach-mtd.txt' % (app, analysis))
    shutil.move(mtd_src_path, mtd_dst_path)
    # move involved methods outputs
    if analysis == 'ze-2obj' and involved:
        zippere_selected_src_path = os.path.join(base_dir, 'output', 'zippere-selected-methods.txt')
        zippere_selected_dst_path = os.path.join(base_dir, 'involved-methods', '%s-zippere-selected-methods.txt' % (app))
        shutil.move(zippere_selected_src_path, zippere_selected_dst_path)
    if analysis == 'csc' and involved:
        merged_src_path = os.path.join(base_dir, 'output', 'csc-involved-methods.txt')
        with open(merged_src_path, 'w', encoding='utf-8') as merged_methods:
            for part in ['store', 'load', 'c', 'l']:
                infile = os.path.join(base_dir, 'output', 'csc-%s-involved-methods.txt'% (part))
                with open(infile, 'r', encoding='utf-8') as part_methods:
                    for line in part_methods:
                        merged_methods.write(line)
        merged_dst_path = os.path.join(base_dir, 'involved-methods', '%s-csc-involved-methods.txt' % (app))
        shutil.move(merged_src_path, merged_dst_path)

def run_csc_exp():
    apps = APPS.keys()
    jdk = 6
    for app in apps:
        for analysis in ANALYSIS:
            exec(app, jdk, analysis)
    
def run_pattern_exp():
    apps = APPS.keys()
    jdk = 6
    for app in apps:
        for analysis in ANALYSIS_PATTERN:
            exec(app, jdk, analysis)

def run_involved_compare():
    apps = APPS.keys()
    jdk = 6
    for app in apps:
        for analysis in ANALYSIS_COMPARE:
            exec(app, jdk, analysis, True)

def run(args):
    # run pta on 10 benchmarks for evaluating csc (table 1)
    # run-csc.py table1
    if args[0] == 'table1':
        run_csc_exp()
    # effectiveness of each pattern
    # run-csc.py pattern
    elif args[0] == 'pattern':
        run_pattern_exp()
    # compare involved methods between csc and zipper-e
    # run-csc.py involved
    elif args[0] == 'involved':
        run_involved_compare()
    # run a single analyses on one benchmark
    # run-csc.py jdk6 csc eclipse
    elif args[0] == 'jdk6' or args[0] == 'jdk8':
        FORCED = True
        exec(args[2], int(args[0][-1]), args[1])
    else:
        print("wrong task")

if __name__ == '__main__':
    init()
    run(sys.argv[1:])