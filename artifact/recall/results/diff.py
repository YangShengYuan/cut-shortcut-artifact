import os
import sys
from colorama import init, Fore, Style

APP = ['eclipse', 'freecol', 'briss', 'hsqldb', 'jedit', 'gruntspud', 'soot', 'columba', 'jython', 'findbugs']
ANALYSIS = ['ci', 'ze-2obj' , 'ze-2type', '2type', '2obj']
EDGD_SUFFIX = '-missed-call-edge.txt'
MTD_SUFFIX = '-missed-reach-mtd.txt'

CSC_MISSED_EDGES = []
CSC_MISSED_MTDS = []

def color(s):
  return Fore.GREEN + s + Style.RESET_ALL

def compare(app):
    mtds = []
    edges = []
    analyses = []
    csc_mtd_path = './' + app + '-csc' + MTD_SUFFIX
    csc_edge_path = './' + app + '-csc' + EDGD_SUFFIX
    if (not os.path.exists(csc_edge_path)) or (not os.path.exists(csc_mtd_path)):
        print("please run csc and recall.py for " + app + ' first')
        sys.exit()
    else:
        # identify compared analysis
        for ana in ANALYSIS:
            if os.path.exists('./' + app + '-' + ana + EDGD_SUFFIX):
                analyses.append(ana)
        if len(analyses) == 0 :
            print("please run any compared analysis for " + app + " first")
            sys.exit()
        csc_missed_mtds = open(csc_mtd_path).readlines()
        csc_missed_edges = open(csc_edge_path).readlines()
        ana_missed_mtds_matrix = []
        ana_missed_edgs_matrix = []
        for ana in analyses:
            # compare for one analysis
            ana_mtd_path = './' + app + '-' + ana + MTD_SUFFIX
            ana_edge_path = './'+ app + '-' + ana + EDGD_SUFFIX
            ana_missed_mtds = open(ana_mtd_path).readlines()
            ana_missed_mtds_matrix.append(ana_missed_mtds)
            ana_missed_edges = open(ana_edge_path).readlines()
            ana_missed_edgs_matrix.append(ana_missed_edges)
        # compare mtd
        for m in csc_missed_mtds:
            flag = True
            for ana_missed_mtds in ana_missed_mtds_matrix:
                if m in ana_missed_mtds:
                    flag = False
            if(flag):
                mtds.append(m[:-1])
        # compare edge
        for e in csc_missed_edges:
            flag = True
            for ana_missed_edges in ana_missed_edgs_matrix:
                if e in ana_missed_edges:
                    flag = False
            if(flag):
                edges.append(e[:-1])
    for m in mtds:
        if m not in CSC_MISSED_MTDS:
            CSC_MISSED_MTDS.append(m)
    for e in edges:
        if e not in CSC_MISSED_EDGES:
           CSC_MISSED_EDGES.append(e)
    return mtds, edges, analyses

def output(app, mtds, edges, analyses):
    print('For benchmark %s, compared with ' %(color(app)), end='')
    for ana in analyses:
        print(color(ana), end='')
        print(' ', end='')
    print()
    print(color('csc') + ' missed reachable methods: ')
    if len(mtds)==0:
        print('none')
    else:
        for m in mtds:
            print(m)
    print(color('csc') + ' missed call graph edges: ')
    if len(edges)==0:
        print('none')
    else:
        for e in edges:
            print(e)    
    print()

def outfile():
    mtd_path = './csc-missed/'+'csc-missed-reach-mtd.txt'
    edge_path = './csc-missed/'+'csc-missed-call-edge.txt'
    f1 = open(mtd_path,'w')
    for m in CSC_MISSED_MTDS:
        f1.write(m+'\n')
    f1.close()
    print('the de-duplicated missing reachable methods (%s in total) are output to ' % 
          (color(str(len(CSC_MISSED_MTDS)))), end='')
    print(mtd_path)
    f2 = open(edge_path,'w')
    for e in CSC_MISSED_EDGES:
        f2.write(e+'\n')
    f2.close()
    print('the de-duplicated missing call graph edges (%s in total) are output to ' % 
          (color(str(len(CSC_MISSED_EDGES)))), end='')
    print(edge_path)

if __name__ == '__main__':
    # python diff.py eclipse
    # python diff.py all
    init()
    apps = sys.argv[1:]
    if len(apps) == 1 and apps[0] == 'all':
        apps = APP
    for app in apps:
        if app not in APP:
            print('wrong benchmark')
            sys.exit()
    for app in apps:
        mtds, edges, analyses = compare(app)
        output(app,mtds,edges,analyses)
    outfile()


        
    
