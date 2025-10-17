import os
import sys
import shutil
from pathlib import Path
from colorama import init, Fore, Style

MICRO_ANALYSIS = ['ci', '1obj', 'stream-4obj-3h', 'csc', 'csc-s']
ANALYSIS = ['ci', '1obj', 'stream-4obj-3h', 'csc', 'csc-s']

SYNTHETICS = ['simple-struc', 'build-stm', 'simple-func', 'stm-map','stm-flatMap', 
              'stm-reduce', 'stm-collect', 'stm-coll-interact', 'conserv-stm', 'primitive-stm']
MOCK_DB = ['mockDB-1', 'mockDB-1-str', 'mockDB-2', 'mockDB-2-str', 'mockDB-3',
           'mockDB-3-str', 'mockDB-4', 'mockDB-4-str', 'mockDB-5', 'mockDB-5-str']
PROGRAMS = ['renai-stm', 'ws4j', 'amazon-sqs', 'jbayes', 'finmath']

def exec(app, analysis):
    # exampler java execution argument
    # stm-map -java=8 -cs=ci -advanced=cut-shortcut-S -distinguishStringConstant=null -stream=1
    cmd = 'java -Xms100g -Xmx100g -XX:+UseG1GC -jar tai-e-csc.jar '
    # app
    cmd += '%s ' % (app)
    # jdk
    cmd += '-java=8 '
    # analysis
    if analysis == 'ci':
        cmd += '-cs=ci '
    elif analysis == '1obj':
        if app == 'func-motivating' or app == 'jbayes' or app == 'rstreamer':
            return
        cmd += '-cs=1-obj '
        if app == 'amazon-sqs':
            os.system("rmdir /s/q cache") # delete cache, to bypass a runtime bug 
    elif analysis == 'stream-4obj-3h':
        cmd += '-cs=4-obj-3h -advanced=stream '
    elif analysis == 'stream-3call-2h':
        cmd += '-cs=3-call-2h -advanced=stream '
    elif analysis == 'ze-2obj':
        if app == 'finmath' or app == 'jbayes':
            return
        cmd += '-cs=2-obj -advanced=zipper-e '
    elif analysis == 'ze-2type':
        cmd += '-cs=2-type -advanced=zipper-e '
    elif analysis == 'csc':
        cmd += '-cs=ci -advanced=cut-shortcut '
    elif analysis == 'csc-s':
        cmd += '-cs=ci -advanced=cut-shortcut-S '
    # handle string constant
    if app in SYNTHETICS:
        if app == 'func-motivating':
            cmd += '-distinguishStringConstant=all '
        else:
            cmd += '-distinguishStringConstant=null '
    elif app in MOCK_DB:
        if 'str' in app:
            cmd += '-distinguishStringConstant=all '
        else:
            cmd += '-distinguishStringConstant=null '
    elif app in PROGRAMS:
        if app == 'renai-stm':
            # the renai-stm benchmark use stream to process string type elements
            cmd += '-distinguishStringConstant=all '
        else:
            cmd += '-distinguishStringConstant=reflection '
    else:
        print("wrong app")
        exit()
    if app in PROGRAMS:
        cmd += '-stream=2'
    else:
        cmd += '-stream=1'
    print(cmd)
    print(Fore.YELLOW + Style.BRIGHT + 'Running ' + analysis + ' for ' + app + ' on Tai-e ... ' + Style.RESET_ALL)
    os.system(cmd)
    # move pta-ci-results and stream-target infos
    base_dir = os.path.dirname(os.path.abspath(__file__))
    pts_src_path = os.path.join(base_dir, 'output', 'pta-ci-results.txt')
    pts_dst_path = os.path.join(base_dir, 'stream-out', '%s-%s-pts.txt' % (app, analysis))
    shutil.move(pts_src_path, pts_dst_path)
    if analysis == 'csc-s':
        stm_out_src_path = os.path.join(base_dir, 'output', 'stream-target.txt')
        stm_out_dst_path = os.path.join(base_dir, 'stream-out', '%s-%s-stream-target.txt' % (app, analysis))
        shutil.move(stm_out_src_path, stm_out_dst_path)
        stm_coll_out_src_path = os.path.join(base_dir, 'output', 'stm-related-container-target.txt')
        stm_coll_out_dst_path = os.path.join(base_dir, 'stream-out', '%s-%s-stm-related-container-target.txt' % (app, analysis))
        shutil.move(stm_coll_out_src_path, stm_coll_out_dst_path)

def run_synthetics():
    for app in SYNTHETICS:
        for analysis in MICRO_ANALYSIS:
            exec(app, analysis)

def run_mockDB_queries():
    for app in MOCK_DB:
        for analysis in MICRO_ANALYSIS:
            exec(app, analysis)

def run_apps():
    for app in PROGRAMS:
        for analysis in ANALYSIS:
            exec(app, analysis)

def run(args):
    # run-csc-s.py synthetic
    if args[0] == 'synthetic':
        run_synthetics()
    # run-csc-s.py mockDB
    elif args[0] == 'mockDB':
        run_mockDB_queries()
    # run-csc-s.py program
    elif args[0] == 'program':
        run_apps()
    else:
        app = args[0]
        static = []
        if (app not in SYNTHETICS) and (app not in MOCK_DB) and (app not in PROGRAMS):
            print("wrong app")
            print(app)
            exit()
        if (app in SYNTHETICS) or (app in MOCK_DB):
            static = MICRO_ANALYSIS
        else:
            static = ANALYSIS
        for analysis in static:
            exec(app, analysis)

if __name__ == '__main__':
    init()
    run(sys.argv[1:])