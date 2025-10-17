import os
import sys
import shutil
from pathlib import Path
from colorama import init, Fore, Style

STM_OUT_HOME = os.path.dirname(os.path.realpath(__file__))
RESULT_HOME = os.path.join(STM_OUT_HOME, 'results')
EXPECTED_HOME = os.path.join(RESULT_HOME, 'expected')
# analysis
MICRO_STATICS = ['ci', '1obj', 'stream-4obj-3h', 'csc', 'csc-s']
APP_STATICS = ['ci', '1obj', 'stream-4obj-3h', 'csc', 'csc-s']
# benchmarks
SYNTHETICS = ['simple-struc', 'build-stm', 'simple-func', 'stm-map', 'stm-flatMap',
              'stm-reduce', 'stm-collect', 'stm-coll-interact', 'primitive-stm', 'conserv-stm']
MOCK_DB = ['mockDB-1', 'mockDB-1-str', 'mockDB-2', 'mockDB-2-str', 'mockDB-3',
           'mockDB-3-str', 'mockDB-4', 'mockDB-4-str', 'mockDB-5', 'mockDB-5-str']
PROGRAMS = ['renai-stm', 'ws4j', 'amazon-sqs', 'jbayes', 'finmath']

def count(app, statics):
    countInterested = False
    if (app in SYNTHETICS) or (app in MOCK_DB):
        countInterested = True
    stm_target = set()
    interested_var = set()
    
    # get stm-target var name
    stream_target_file = os.path.join(STM_OUT_HOME, '%s-csc-s-stream-target.txt' % (app))
    with open(stream_target_file, encoding='latin1') as f:
        for l in f.readlines():
            l = l.strip()
            if "<=>" not in l :
                stm_target.add(l)
            else:
                lamMockArg = l.split(" <=> ")[1]
                stm_target.add(lamMockArg)

    stream_coll_out_target_file = os.path.join(STM_OUT_HOME, '%s-csc-s-stm-related-container-target.txt' % (app))
    with open(stream_coll_out_target_file, encoding='latin1') as f:
        for l in f.readlines():
            l = l.strip()
            stm_target.add(l)
    
    if (countInterested):
        # for these cases, we count the interested var, not all stream targets, because the latter merge too many things and is not suitable for manually examining
        dynamic_expected_file = os.path.join(EXPECTED_HOME, '%s-expected.txt' % (app))
        with open(dynamic_expected_file, encoding='latin1') as f:
            for l in f.readlines():
                l = l.strip()
                var_name = l.split(" -> (")[0]
                interested_var.add(var_name)

    # count and output target pts
    for analysis in statics:
        # 1obj unscalable benchmarks
        if (app == 'func-motivating' or app == 'jbayes') and (analysis == '1obj'):
            continue
        pts_file = os.path.join(STM_OUT_HOME, '%s-%s-pts.txt' % (app, analysis))
        target_pts = {}
        with open(pts_file, encoding='latin1') as f:
            for l in f.readlines():
                l = l.strip()
                p_name = l.split(" -> (")[0]
                if countInterested:
                    if (p_name in interested_var):
                        objs = l.split(")objs: ")[1].split(" |,| ")
                        target_pts[p_name] = objs
                else:
                    if (p_name in stm_target):
                        objs = l.split(")objs: ")[1].split(" |,| ")
                        target_pts[p_name] = objs
        
        # output stastics on console
        sum_pts = 0
        for key, value in target_pts.items():
            sum_pts += len(value)
        out_str = "stream-out"
        if countInterested:
            out_str = "interested"
        print('total points-to size of %s pointers under %s for %s is %s' % (out_str, Fore.GREEN + analysis + Style.RESET_ALL, Fore.YELLOW + app + Style.RESET_ALL, Fore.YELLOW + str(sum_pts) + Style.RESET_ALL))
        
        stm_target_pts_file = os.path.join(RESULT_HOME, app + '-' + analysis + '-stream-out.txt')
        if not countInterested:
            # output stream target pts to file
            with open(stm_target_pts_file, 'w', encoding='latin1') as out:
                print('Output stream target pts to %s' % stm_target_pts_file)
                for key, value in target_pts.items():
                    out.write(key + " -> (" + str(len(value))+ ")objs: ")
                    for i in range(0, len(value)):
                        obj = value[i]
                        if i != len(value) - 1 :
                            out.write(obj + " |,| ")
                        else:
                            out.write(obj)
                    out.write('\n')
            # print('delete' + str(pts_file))
            # os.remove(pts_file)
        else:
            # move pta-ci-results to results.
            shutil.move(pts_file, stm_target_pts_file)

if __name__ == '__main__':
    init()
    apps = []
    arg = sys.argv[1]
    if arg == 'synthetic':
        apps = SYNTHETICS
        statics = MICRO_STATICS
    elif arg == 'mockDB':
        apps = MOCK_DB
        statics = MICRO_STATICS
    elif arg == 'program':
        apps = PROGRAMS
        statics = APP_STATICS
    else:
        if arg in SYNTHETICS:
            apps.append(arg)
            statics = MICRO_STATICS
        elif arg in MOCK_DB:
            apps.append(arg)
            statics = MICRO_STATICS
        elif arg in PROGRAMS:
            apps.append(arg)
            statics = APP_STATICS
        else:
            print("wrong app")
            exit()
    for app in apps:
        count(app, statics)
