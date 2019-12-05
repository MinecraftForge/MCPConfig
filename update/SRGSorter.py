# Reads and sorts a SRG file by Obf name

import json, sys, os, re
from pprint import pprint

def load_srg_file(input):
    f = open(input, 'r')
    data = f.readlines()
    f.close()

    srg = {'PK:' : {}, 'CL:': {}, 'FD:': {}, 'MD:':{}}
    
    data = [x.rstrip() for x in data if not x[0] == '#' and len(x.strip()) > 0]
    if 'PK:' in data[0] or 'CL:' in data[0] or 'FD:' in data[0] or 'MD:' in data[0]: #Normal SRG
        for line in data:
            pts = line.split()
            #print line
            if pts[0] == 'PK:' or pts[0] == 'CL:' or pts[0] == 'FD:':
                srg[pts[0]][pts[1]] = pts[2]
            elif pts[0] == 'MD:':
                srg[pts[0]]['%s %s' % (pts[1], pts[2])] = '%s %s' % (pts[3], pts[4])
    else:
        srg['CL:'] = {k:v for k,v in (l.split(' ') for l in data if len(l.split(' ')) == 2 and not l[0] == '\t')}
        
        def remap(cls):
            return cls if not cls in srg['CL:'] else srg['CL:'][cls]
                    
        def remap_desc(desc):
            reg = re.compile('L([^;]+);')
            return reg.sub(lambda m: 'L%s;' % remap(m.group(1)), desc)
            
        current_cls = None
        for line in data:
            if line[0] == '\t':
                if current_cls == None:
                    raise Exception('Invalid TSRG Line, Missing Current class: ' + line)
                pts = line.strip().split(' ')
                if len(pts) == 2:
                    srg['FD:']['%s/%s' % (current_cls, pts[0])] = '%s/%s' % (remap(current_cls), pts[1])
                elif len(pts) == 3:
                    srg['MD:']['%s/%s %s' % (current_cls, pts[0], pts[1])] = '%s/%s %s' % (remap(current_cls), pts[2], remap_desc(pts[1]))
                else:
                    raise Exception('Invalid TSRG Line, To many peices: ' + line)
            else:
                pts = line.strip().split(' ')
                if len(pts) == 2:
                    current_cls = pts[0] # Save class for TSRG, we already built CL mapping above.
                elif len(pts) == 3:
                    srg['FD:']['%s/%s' % (pts[0], pts[1])] = '%s/%s' % (remap(pts[0]), pts[2])
                elif len(pts) == 4:
                    srg['MD:']['%s/%s %s' % (pts[0], pts[1], pts[2])] = '%s/%s %s' % (remap(pts[0]), pts[3], remap_desc(pts[2]))
                else:
                    raise Exception('Invalid CSRG Line, To many peices: ' + line)
        
    return srg
    
def reverse_srg(input):
    return {
        'PK:': {v: k for k,v in input['PK:'].iteritems()},
        'CL:': {v: k for k,v in input['CL:'].iteritems()}, 
        'FD:': {v: k for k,v in input['FD:'].iteritems()}, 
        'MD:': {v: k for k,v in input['MD:'].iteritems()}
    }
    
def chain_srg(left, right):
    return {
        #'PK:': {k: right['PK:'][v] for k,v in left['PK:'].iteritems()},
        'CL:': {k: right['CL:'][v] for k,v in left['CL:'].iteritems()},
        'FD:': {k: right['FD:'][v] for k,v in left['FD:'].iteritems()},
        'MD:': {k: right['MD:'][v] for k,v in left['MD:'].iteritems()}
    }
    
def sort_srg_file(input, output):
    srg = load_srg_file(input)
    sort_srg_dict(srg, output)

def sort_srg_dict(srg, output):
    f = open(output, 'w')
    if len(srg['PK:']) > 0:
        f.write('\n'.join(['PK: %s %s' % (v, srg['PK:'][v]) for v in sorted(srg['PK:'].keys())]))
        f.write('\n')
    if len(srg['CL:']) > 0:
        f.write('\n'.join(['CL: %s %s' % (v, srg['CL:'][v]) for v in sorted(srg['CL:'].keys(), key=format_class)]))
        f.write('\n')
    if len(srg['FD:']) > 0:
        f.write('\n'.join(['FD: %s %s' % (v, srg['FD:'][v]) for v in sorted(srg['FD:'].keys(), key=format_field)]))
        f.write('\n')
    if len(srg['MD:']) > 0:
        f.write('\n'.join(['MD: %s %s' % (v, srg['MD:'][v]) for v in sorted(srg['MD:'].keys(), key=format_method)]))
        f.write('\n')
    f.close()
    
def srg_to_tsrg(srg):
    tsrg = {}
    
    for k,v in srg['CL:'].items():
        tsrg[k] = {'name':v}
        
    for k,v in srg['FD:'].items():
        ocls, ofd = k.rsplit('/', 1)
        mcls, mfd = v.rsplit('/', 1)
        if not ocls in tsrg:
            tsrg[ocls] = {'name': mcls}
        if not 'fd' in tsrg[ocls]:
            tsrg[ocls]['fd'] = {}
        tsrg[ocls]['fd'][ofd] = mfd
        
    def split_md(md):
        parts = md.split(' ')
        return {'cls': parts[0].rsplit('/', 1)[0],
                'name': parts[0].rsplit('/', 1)[1],
                'desc': parts[1]}
    for k,v in srg['MD:'].items():
        o = split_md(k)
        m = split_md(v)
        
        if not o['cls'] in tsrg:
            tsrg[o['cls']] = {'name': m['cls']}
        if not 'md' in tsrg[o['cls']]:
            tsrg[o['cls']]['md'] = {}
        tsrg[o['cls']]['md']['%s %s' % (o['name'], o['desc'])] = m['name']
    return tsrg

def dump_tsrg(srg, output):
    f = open(output, 'w')
    tsrg = srg_to_tsrg(srg)
    
    for cls in sorted(tsrg.keys(), key=format_class):
        if not 'name' in tsrg[cls]:
            print('Error: Missing class name for %s' % cls)
            continue
        f.write('%s %s\n' % (cls, tsrg[cls]['name']))
        
        if 'fd' in tsrg[cls]:
            for fn in sorted(tsrg[cls]['fd'].keys(), key=format_field_csrg):
                f.write('\t%s %s\n' % (fn, tsrg[cls]['fd'][fn]))
        
        if 'md' in tsrg[cls]:
            for mn in sorted(tsrg[cls]['md'].keys(), key=format_method_csrg):
                f.write('\t%s %s\n' % (mn, tsrg[cls]['md'][mn]))
    f.close()

def dump_csrg(srg, output):    
    f = open(output, 'w')
    tsrg = srg_to_tsrg(srg)
    
    for cls in sorted(tsrg.keys(), key=format_class):
        if not 'name' in tsrg[cls]:
            print('Error: Missing class name for %s' % cls)
            continue
        f.write('%s %s\n' % (cls, tsrg[cls]['name']))
        
        if 'fd' in tsrg[cls]:
            for fn in sorted(tsrg[cls]['fd'].keys(), key=format_field_csrg):
                f.write('%s %s %s\n' % (cls, fn, tsrg[cls]['fd'][fn]))
        
        if 'md' in tsrg[cls]:
            for mn in sorted(tsrg[cls]['md'].keys(), key=format_method_csrg):
                f.write('%s %s %s\n' % (cls, mn, tsrg[cls]['md'][mn]))
    f.close()
    
def format_class(cls):
    if '/' in cls:
        return cls #Unobfed names should be formatted fine
    ret = ''
    for pt in cls.split('$'):
        ret = ret + '$' + pt.rjust(1000, ' ')
    return ret[1:]

def format_field(fld):
    return format_class(fld.rsplit('/', 1)[0]) + '/' + fld.rsplit('/', 1)[1].rjust(1000, ' ')
    
def format_method(mtd):
    return format_field(mtd.split(' ')[0]) + ' ' + mtd.split(' ')[1]
    
def format_field_csrg(fld):
    return fld.swapcase().rjust(1000, ' ')
    
def format_method_csrg(mtd):
    return format_field_csrg(mtd.split(' ')[0]) + ' ' + mtd.split(' ')[1]
    
if __name__ == '__main__':
    sort_srg_file(sys.argv[1], sys.argv[2])


