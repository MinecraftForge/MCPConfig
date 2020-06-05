import sys, os, re, json, copy, shutil, glob, json
import SRGSorter
from pprint import pprint
from hashlib import md5
from zipfile import ZipFile
from collections import OrderedDict

def read_match(file, map):
    if not os.path.isfile(file):
        return
    print('Reading: ' + file)
    
    mode = -1 #0 CLASSES 1 FIELDS 2 METHODS
    lineNum = 0
    with open(file, 'r') as f:
        for line in f.readlines():
            line = line.rstrip('\n')
            lineNum += 1
            if len(line) == 0 or line[0] == '#':
                continue
            if line[0] == '[':
                if line == '[CLASSES]': mode = 0
                elif line == '[FIELDS]': mode = 1
                elif line == '[METHODS]': mode = 2
                else:
                    mode = -1
                    print('  Invalid line #%s: %s' % (lineNum, line))
            else:
                pts = line.replace('.', '/').split(' ')
                key = pts[0]
                value = pts[1]
                if mode == 2:
                    key = '%s %s' % (pts[0], pts[1])
                    value = '%s %s' % (pts[2], pts[3])
                    
                if key in map:
                    if map[key] != value:
                        print('  Conflicting Mapping Line #%s %s -> %s' % (lineNum, map[key], value))
                map[key] = value
    
def split_mtd(line):
    return [line.split(' ')[0].rsplit('/', 1)[0], line.split(' ')[0].rsplit('/', 1)[1], line.split(' ')[1]]
    
def rename_class(map, cls):
    if cls in map:
        return map[cls]
    return cls if not '$' in cls else '%s$%s' % (rename_class(map, cls.rsplit('$', 1)[0]), cls.rsplit('$', 1)[1])
    
def rename_desc(map, desc):
    return reg_desc.sub(lambda match: 'L%s;' % rename_class(map, match.group(1)), desc)

reg_desc = re.compile('L([^;]+);')
new_class_index = 1000

def migrate_mappings(mcp_root, old_version, new_version, output):
    ver_root = os.path.join(mcp_root, 'versions')
    old_root = os.path.join(ver_root, old_version)
    new_root = os.path.join(ver_root, new_version)
    migrate_mappings(old_version, new_version, output, old_root, new_root)

def migrate_mappings(old_version, new_version, output, old_root, new_root):
    files = ['forced.txt', 'class_suggestions.txt', 'field_suggestions.txt', 'method_suggestions.txt', 'new_unmapped_fixed.txt']
    #sides = ['client', 'server']
    sides = ['joined']
    map = {}
    
    if not os.path.exists(new_root):
        os.makedirs(new_root)
    
    #==========================================================================   
    # Read the mapping files from Depigifer, or Magidots. 
    #==========================================================================  
    migrate_root = os.path.join(output, '%s_to_%s' % (old_version, new_version))
    for side in sides:
        for file in files:
            pig = os.path.join(migrate_root, 'pig/%s_%s' % (side, file))
            magi = os.path.join(migrate_root, '%s_%s' % (side, file))
            if (os.path.isfile(pig)):
                read_match(pig, map);
            else:
                read_match(magi, map);

    if (len(map) == 0):
        print('Failed to read any mapping data!')
        return
        
    zip = ZipFile(os.path.join(output, '%s/joined_a.jar' % new_version))
    known_classes = [n[:-6] for n in zip.namelist() if n.endswith('.class') and not 'minecraftforge' in n]
    zip.close()
    
    #==========================================================================   
    # Read the old srg file, and the new obf to notch mappings.
    # Generates the inital new SRG from the matcher output and this data.
    #==========================================================================   
    from SRGSorter import load_srg_file
    old_srg = load_srg_file(os.path.join(old_root, 'joined.tsrg'))
    o_to_n = load_srg_file(os.path.join(output, '%s/joined_o_to_n.tsrg' % new_version))
    srg = {'PK:' : {}, 'CL:': {}, 'FD:': {}, 'MD:':{}}
    
    srg['PK:'] = old_srg['PK:']
    for pt in ['CL:', 'FD:', 'MD:']:
        for k,v in old_srg[pt].items():
            if k in map:
                srg[pt][map[k]] = v
    from SRGSorter import sort_srg_dict
    #sort_srg_dict(srg, 'test.srg')
    
    rg_idx_max = find_max_rg(old_srg, old_root)
    new_classes = {}
    obf_whitelist = [] # Entries that do not need to follow SRG naming, so we don't 'unfix' them below.
    meta = json.loads(open(os.path.join(output, '%s/joined_a_meta.json' % new_version), 'r').read())
    meta = {k:v for k,v in meta.items() if not 'minecraftforge' in k} #Remove Forge's annotations, I should filter this in MappingToy..

    err_f = open(os.path.join(migrate_root, 'migrate_errors.txt'), 'wb')

    add_new_classes(o_to_n, srg, new_classes, known_classes)
    fix_enums(obf_whitelist, srg, meta)
    fix_method_names(obf_whitelist, srg, meta)
    rg_idx_max = fix_override_methods(rg_idx_max, meta, srg, err_f, obf_whitelist, o_to_n)
    rg_idx_max = fix_unobfed_names(rg_idx_max, known_classes, new_classes, srg, o_to_n, obf_whitelist)
    fix_inner_class_shuffle(srg)
    rg_idx_max = update_constructors(rg_idx_max, old_root, new_root, srg, meta)
    rg_idx_max = create_new_entries(rg_idx_max, srg, o_to_n, meta, err_f)
    
    if len(new_classes) != 0:
        with open(os.path.join(migrate_root, 'new_classes.txt'), 'wb') as f:
            for cls in sorted(new_classes.values()):
                if 'C_' in cls:
                    if not '$' in cls:
                        f.write(('%s\n' % cls).encode())
                    else:
                        try:
                            int(cls.rsplit('$', 1)[1])
                        except ValueError:
                            f.write(('%s\n' % cls).encode())
                            
    from SRGSorter import dump_tsrg
    dump_tsrg(srg, os.path.join(new_root, 'joined.tsrg'))
    
def find_max_rg(old_srg, old_root):
    #==========================================================================    
    # Find the highest rg id number, This is used elseware if we 
    # need to create new SRG names.
    #==========================================================================
    def max_rg(old, name):
        if not name.startswith('field_') and not name.startswith('func_'):
            return old
        new = int(name.split('_')[1])
        return old if old > new else new
    
    rg_idx_max = 0
    for k,v in old_srg['FD:'].items():
        rg_idx_max = max_rg(rg_idx_max, v.rsplit('/', 1)[1])
    for k,v in old_srg['MD:'].items():
        rg_idx_max = max_rg(rg_idx_max, v.split(' ')[0].rsplit('/', 1)[1])
        
    old_ctrs = os.path.join(old_root, 'constructors.txt')
    if os.path.exists(old_ctrs):
        with open(old_ctrs, 'r') as f:
            for line in f.readlines():
                line = line.rstrip('\n')
                if len(line) == 0:
                    continue
                id = int(line.split(' ')[0])
                if id > rg_idx_max:
                    rg_idx_max = id
        
    return rg_idx_max
        
def add_new_classes(o_to_n, srg, new_classes, known_classes):
    #==========================================================================
    # Add any new classes that are obfed and don't have a target name.
    # Names them C_\d++_obf
    # Done here so that RG doesn't fail on them.
    #==========================================================================
    print('Adding new classes:')
    global new_class_index
    new_class_index = 1000
    
    def add_class(cls):
        global new_class_index
        if cls in srg['CL:']:
            return srg['CL:'][cls]
            
        if '$' in cls:
            parent, child = cls.rsplit('$', 1)
            pobf = parent
            parent = add_class(parent)
            
            #We have to make sure the new name we are giving it doesn't collide with any existing names
            #This is only really a issue for anon inner classes but I added both branches for completeness
            if child.isdigit():
                child = int(child)
                ret = '%s$%s' % (parent, child)
                while ret in srg['CL:'].values():
                    child += 1
                    ret = '%s$%s' % (parent, child)
            else:
                if o_to_n['CL:'][cls] == cls: #Unobfed name, so the name is correct!
                    ret = cls
                    new_class_index += 1
                else:
                    ret = '%s$C_%s_%s' % (parent, new_class_index, child)
                    new_class_index += 1
                while ret in srg['CL:'].values():
                    ret = '%s$C_%s_%s' % (parent, new_class_index, child)
                    new_class_index += 1
                    
        elif '/' in cls:
            # If we're in a package assume that package is correct but the name is wrong.
            package, name = cls.rsplit('/', 1)
            if o_to_n['CL:'][cls] == cls: #Unobfed name, so the name is correct!
                ret = cls
            else:
                ret = '%s/C_%s_%s' % (package, new_class_index, name)
                new_class_index += 1
        else:
            ret = 'net/minecraft/src/C_%s_%s' % (new_class_index, cls)
            new_class_index += 1
            
        print('  Added: %s' % ret)
        new_classes[cls] = ret
        srg['CL:'][cls] = ret
        return ret
        
    from SRGSorter import format_class
    for cls in sorted(o_to_n['CL:'], key=format_class):
        if cls in known_classes:
            add_class(cls)
            
def fix_enums(obf_whitelist, srg, meta):
    #==========================================================================
    # Enums encode their field names as strings in the class, so we can force
    # their names to the real ones.
    # We also fix sythetic 'bouncer' methods. To make sure they have the same
    # name as the method they bounce to. As this is how the compiler deals with
    # generic overrides.
    #==========================================================================
    print('Fixing Enum Names:')
    for cls,v in meta.items():
        if 'fields' in v:
            for obf, fld in v['fields'].items():
                if 'force' in fld:
                    force = fld['force']
                    key = '%s/%s' % (cls, obf)
                    if key in srg['FD:']:
                        old = srg['FD:'][key]
                        new = '%s/%s' % (old.rsplit('/', 1)[0], force)
                        obf_whitelist.append(new)
                        if not old == new:
                            print('  %s -> %s' % (old, force))
                            srg['FD:'][key] = new
                    else:
                        new = '%s/%s' % (srg['CL:'][key.rsplit('/', 1)[0]], force) #The class should always exist, as we *should* add it above.
                        obf_whitelist.append(new)
                        print('  %s -> %s' % (key, force))
                        srg['FD:'][key] = new
    
def fix_method_names(obf_whitelist, srg, meta):
    print('Fixing Method Names')
    for cls,v in meta.items():
        if 'methods' in v:
            for key,mtd in v['methods'].items():
                name,desc = key.replace('(', ' (').split(' ')
                key = '%s/%s %s' % (cls, name, desc)
                if 'force' in mtd:
                    force = mtd['force']
                    if key in srg['MD:']:
                        old = srg['MD:'][key]
                        new = '%s/%s %s' % (old.split(' ')[0].rsplit('/', 1)[0], force, old.split(' ')[1])
                        obf_whitelist.append(new)
                        if not old == new:
                            srg['MD:'][key] = new
                            print('  %s -> %s' % (old.split(' ')[0], force))
                    else:
                        new = '%s/%s %s' % (srg['CL:'][key.split(' ')[0].rsplit('/', 1)[0]], force, rename_desc(srg['CL:'], desc))
                        obf_whitelist.append(new)
                        print('  %s -> %s' % (key, force))
                        srg['MD:'][key] = new

def fix_unobfed_names(rg_idx_max, known_classes, new_classes, srg, o_to_n, obf_whitelist):
    #==========================================================================
    #  Use the unobfed names from the class/mappings if it matches Notch names
    #  Also attempts to detect things that 'lost' their unobfed names, and 
    #  creates new SRG names for them.
    #==========================================================================
    print('Fixing unobfed names:')
    unobfed = [] #Gather all entries that are the same in notch and obf.
    obfed = [] #Entires that WERE unobfed, but now have obfed names.
        
    for k,v in o_to_n['CL:'].items():
        if k == v:
            unobfed.append(k)
    for k in sorted(o_to_n['FD:'], key=SRGSorter.format_field):
        v = o_to_n['FD:'][k]
        obf_cls, obf_name = k.rsplit('/', 1)
        notch_cls, notch_name = v.rsplit('/', 1)
        srg_cls, srg_name = ['', ''] if not k in srg['FD:'] else srg['FD:'][k].rsplit('/', 1)
        if obf_name == notch_name:
            unobfed.append(k)
            if obf_cls in new_classes or not k in srg['FD:'] and len(notch_name) > 1:
                srg['FD:'][k] = '%s/%s' % (rename_class(srg['CL:'], obf_cls), notch_name)
                print('  FD: NULL -> %s'  % srg['FD:'][k])
        elif k in srg['FD:'] and not srg['FD:'][k] in obf_whitelist and not srg_name.startswith('field_'):
            new_name = 'field_%s_%s_' % (rg_idx_max, obf_name)
            rg_idx_max += 1
            srg['FD:'][k] = '%s/%s' % (rename_class(srg['CL:'], obf_cls), new_name)
            print('  FD: %s/%s -> %s' % (rename_class(srg['CL:'], obf_cls), srg_name, new_name))
            
    for k in sorted(o_to_n['MD:'], key=SRGSorter.format_method):
        v = o_to_n['MD:'][k]
        obf_cls, obf_name, obf_desc = split_mtd(k)
        srg_cls, srg_name, srg_desc = [rename_class(srg['CL:'], obf_cls), '', rename_desc(srg['CL:'], obf_desc)] if not k in srg['MD:'] else split_mtd(srg['MD:'][k])
        if not obf_cls in known_classes:
            continue
        if obf_name == v.split(' ')[0].rsplit('/', 1)[1] and not 'lambda$' in obf_name and len(obf_name) > 1 and obf_name[0] != '<':
            unobfed.append(k)
            if obf_cls in new_classes or not k in srg['MD:']:
                srg['MD:'][k] = '%s/%s %s' % (rename_class(srg['CL:'], obf_cls), obf_name, rename_desc(srg['CL:'], obf_desc))
                filter = ['valueOf', 'values', 'main']
                if not obf_name in filter: #Just spam, it gets lost from RG's gen.. Figure a way to force these names to be in the list?
                    print('  MD: NULL -> %s' % srg['MD:'][k])
        elif k in srg['MD:'] and not srg['MD:'][k] in obf_whitelist and not srg_name.startswith('func_') and not srg_name.startswith('access$'): # access$ method are synthetic bridges. Dont give them a srg name.
            new_name = 'func_%s_%s_' % (rg_idx_max, obf_name)
            rg_idx_max += 1
            print('  MD: %s -> %s' % (srg['MD:'][k], new_name))
            srg['MD:'][k] = '%s/%s %s' % (rename_class(srg['CL:'], obf_cls), new_name, srg_desc)
        elif obf_name.startswith('lambda$'): #Unobfed lambdas, they are sythetic, so we care to add srg names to rename params?
            new_name = None
            old_key = '%s/NULL %s' % (srg_cls, srg_desc)
            
            if k in srg['MD:']:
                if not srg_name.startswith('func_'):
                    new_name = 'func_%s_lam_' % (rg_idx_max)
                    rg_idx_max += 1
                elif not srg_name.endswith('_lam_'):
                    new_name = 'func_%s_lam_' % (srg_name.split('_')[1])
                old_key = srg['MD:'][k]
            else:
                new_name = 'func_%s_lam_' % (rg_idx_max)
                rg_idx_max += 1
                
            if new_name != None:
                srg['MD:'][k] = '%s/%s %s' % (srg_cls, new_name, srg_desc)
                print('  MD: %s -> %s' % (old_key, new_name))
    
    renames = {}
    for k in sorted(srg['CL:'], key=SRGSorter.format_class):
        v = srg['CL:'][k]
        if k in unobfed and v != k:
            renames[v] = k
            srg['CL:'][k] = k
            print('  CL: %s -> %s' % (v, k))
    
    for k in sorted(srg['CL:'], key=SRGSorter.format_class):
        v = srg['CL:'][k]
        fixed = rename_class(renames, v)
        if v != fixed:
            renames[v] = fixed
            srg['CL:'][k] = fixed
            print('  CL: %s -> %s' % (v, fixed))
        
    for k in sorted(srg['FD:'], key=SRGSorter.format_field):
        v = srg['FD:'][k]
        n = '%s/%s' % (rename_class(renames, v.rsplit('/', 1)[0]), v.rsplit('/', 1)[1] if not k in unobfed else k.rsplit('/', 1)[1])
        if not v == n:
            print('  FD: %s -> %s' % (v, n))
            srg['FD:'][k] = n

    for k in sorted(srg['MD:'], key=SRGSorter.format_method):
        v = srg['MD:'][k]
        cls = rename_class(renames, v.split(' ')[0].rsplit('/', 1)[0])
        name = v.split(' ')[0].rsplit('/', 1)[1] if not k in unobfed else k.split(' ')[0].rsplit('/', 1)[1]
        n = '%s/%s %s' % (cls, name, rename_desc(renames, v.split(' ')[1]))
        if not v == n:
            print('  MD: %s -> %s' % (v, n))
            srg['MD:'][k] = n
    return rg_idx_max

def fix_inner_class_shuffle(srg):
    #==========================================================================
    # The obfusicator renames classes alphabetically, and doesn't care about 
    # sorting numeric names by length. So we need to fix the order of inner
    # classes:
    # Correct: 1, 2, 3, 4, 5, 6, 7, 8,  9, 10
    # Obf:     1, 3, 4, 5, 6, 7, 8, 9, 10,  2
    #==========================================================================
    print('Fixing Inner Class Shuffeling')
    inners = {}
    for k,v in srg['CL:'].items():
        if '$' in k:
            parent,child = k.rsplit('$', 1)
            if child.isdigit():
                if not parent in inners:
                    inners[parent] = []
                inners[parent].append(int(child))
                
    inners = {k:v for (k,v) in inners.items() if len(v) >= 10}
                
    for k,v in inners.items():
        inners[k] = sorted(v)
        if not len(v) == inners[k][-1]:
            print('  Missing inners? %s %s' % (k, pprint(inners[k])))
                
    renames = {}
    for k,v in inners.items():
        tmp = sorted([str(i) for i in v])
        needed = False
        for i in v:
            if not srg['CL:']['%s$%s' % (k, i)] == '%s$%s' % (srg['CL:'][k], tmp[i-1]):
                print('%s %s -> %s$%s' % (i, srg['CL:']['%s$%s' % (k, i)], srg['CL:'][k], tmp[i-1]))
                needed = True
                break
        if needed:
            for i in v:
                renames[srg['CL:']['%s$%s' % (k, i)]] = '%s$%s' % (srg['CL:'][k], tmp[i-1])
                print('  %s -> %s' % (srg['CL:']['%s$%s' % (k, i)], tmp[i-1]))
    
    if len(renames) > 0:
        for k,v in srg['CL:'].items():
            srg['CL:'][k] = rename_class(renames, v)
        for k,v in srg['FD:'].items():
            srg['FD:'][k] = '%s/%s' % (rename_class(renames, v.rsplit('/', 1)[0]), v.rsplit('/', 1)[1])
        for k,v in srg['MD:'].items():
            srg['MD:'][k] = '%s/%s %s' % (rename_class(renames, v.split(' ')[0].rsplit('/', 1)[0]), v.split(' ')[0].rsplit('/', 1)[1], rename_desc(renames, v.split(' ')[1]))
    
def update_constructors(rg_idx_max, old_root, new_root, srg, meta):
    old_ctrs = os.path.join(old_root, 'constructors.txt')
    new_ctrs = os.path.join(new_root, 'constructors.txt')
    if os.path.exists(old_ctrs):
        print('Updating Constructors')
            
        ctrs = {}
        with open(old_ctrs, 'r') as f:
            for line in f.readlines():
                line = line.rstrip('\n')
                
                if len(line) == 0:
                    continue
                
                id,cls,desc = line.split(' ')
                id = int(id)
                
                if not cls in ctrs:
                    ctrs[cls] = {}
                if desc in ctrs[cls]:
                    print('  Duplicate Ctr: %s %s %s -> %s' % (cls, desc, ctrs[cls][desc], id))
                ctrs[cls][desc] = id
            
        for cls,v in meta.items():
            if 'methods' in v:
                for mtd in v['methods'].keys():
                    name,desc = mtd.replace('(', ' (').split(' ')
                    if name == '<init>' and not '()' in desc:
                        desc = rename_desc(srg['CL:'], desc)
                        mcls = rename_class(srg['CL:'], cls)
                        if not mcls in ctrs:
                            ctrs[mcls] = {}
                        if not desc in ctrs[mcls]:
                            print('  New Ctr: %s %s %s' % (mcls, desc, rg_idx_max))
                            ctrs[mcls][desc] = rg_idx_max
                            rg_idx_max += 1
                                
        data = {}
        for cls,d in ctrs.items():
            for k,v in d.items():
                data[v] = '%s %s' % (cls, k)
        
        with open(new_ctrs, 'wb') as f:
            for id in sorted(data.keys()):
                f.write(('%s %s\n' % (id, data[id])).encode())
                
        return rg_idx_max

def fix_override_methods(rg_idx_max, meta, srg, err_f, obf_whitelist, o_to_n):
    print('Fixing overriden methods')
    linked = OrderedDict()
    roots = OrderedDict()
    for cls_name,cls in meta.items():
        if 'methods' in cls:
            #Meta has methods listing what methods they override, lets change that to list all child methods
            for mtd_key,mtd in cls['methods'].items():
                if '<' in mtd_key: #<init>/<cinit> dont need names
                    continue
                    
                mtd_name,mtd_desc = mtd_key.replace('(', ' (').split(' ')
                if 'overrides' in mtd:
                    mkey = '%s/%s %s' % (cls_name, mtd_name, mtd_desc)
                    for ord in mtd['overrides']:
                        key = '%s%s' % (ord['name'], ord['desc'])
                        if ord['owner'] in meta:
                            omtd = meta[ord['owner']]['methods'][key]
                            if not 'children' in omtd:
                                omtd['children'] = set()
                            omtd['children'].add('%s/%s %s' % (cls_name, mtd_name, mtd_desc))
                            
                        okey = '%s/%s %s' % (ord['owner'], ord['name'], ord['desc'])
                        if not mkey in linked:
                            linked[mkey] = set()
                        linked[mkey].add(okey)
                        if not okey in linked:
                            linked[okey] = set()
                            roots[okey] = linked[okey]
                        linked[okey].add(mkey)
        
    #Fill out linked values
    for k,v in linked.items():
        for t in v:
            linked[t].add(k)
    
    #for root in roots:
    #    print('  Root: %s' % (root))
            
    #Find any methods that Mojang merged in the new version but have different names in the srg.
    for key in roots:
        owner,desc = key.split(' ')
        owner,name = owner.rsplit('/', 1)
        obfed = True
        
        ids = set()
        for child in sorted(roots[key]):
            if child in srg['MD:']:
                id = srg['MD:'][child].split(' ')[0].rsplit('/', 1)[1]
                if len(id) == 1:
                    error(err_f, 'Short: %s -> %s' % (child, id))
                    
                ids.add(id)
        
        if key in o_to_n['MD:']:
            nname = o_to_n['MD:'][key].split(' ')[0].rsplit('/', 1)[1]
            if nname == name: #The root is unobfed
                ids = {nname}
                obfed = False
                
        if len(ids) > 1:
            error(err_f, 'Conflicting IDS: %s' % (key)) #We need to pick one and fix it
            for child in sorted(roots[key]):
                if child in srg['MD:']:
                    error(err_f, '  %s -> %s' % (child, srg['MD:'][child].split(' ')[0].rsplit('/', 1)[1]))
                else:
                    error(err_f, '  %s -> NULL' % (child))
                    
        if not owner in meta: #Outside MC codebase, everything needs to use unobfed names
            for child in sorted(roots[key]):
                cowner,cdesc = child.split(' ')
                cowner,cname = cowner.rsplit('/', 1)
                if child in srg['MD:']:
                    oowner,odesc = srg['MD:'][child].split(' ')
                    oowner,oname = oowner.rsplit('/', 1)
                    
                    new = '%s/%s %s' % (oowner, name, odesc)
                    obf_whitelist.append(new)
                    
                    if oname != name:
                        print('  %s %s -> %s' % (child, oname, name))
                        srg['MD:'][child] = new
                else:
                    new = '%s/%s %s' % (rename_class(srg['CL:'], cowner), name, rename_desc(srg['CL:'], cdesc))
                    obf_whitelist.append(new)
                    
                    print('  %s NULL -> %s' % (child, name))                    
                    srg['MD:'][child] = new
        else:
            new_name = None
            if len(ids) == 1:
                new_name = ids.pop()
            elif len(ids) == 0:
                new_name = 'func_%s_%s_' % (rg_idx_max, name)
                rg_idx_max += 1
            else: # Existing methods got merged, make a new name
                new_name = 'func_%s_%s_' % (rg_idx_max, name)
                rg_idx_max += 1
                error(err_f, 'Failed to find ID: %s -> %s %s' % (key, new_name, sorted(ids)))
            
            if not new_name is None:
                if key in srg['MD:']:
                    oowner,odesc = srg['MD:'][key].split(' ')
                    oowner,oname = oowner.rsplit('/', 1)
                    
                    new = '%s/%s %s' % (oowner, new_name, odesc)
                    if not obfed:
                        obf_whitelist.append(new)
                        
                    if oname != new_name:
                        print('  %s %s -> %s' % (key, oname, new_name))
                        srg['MD:'][key] = new
                else:
                    new = '%s/%s %s' % (rename_class(srg['CL:'], owner), new_name, rename_desc(srg['CL:'], desc))
                    if not obfed:
                        obf_whitelist.append(new)
                     
                    print('  %s NULL -> %s' % (key, new_name))   
                    srg['MD:'][key] = new
                    
                for child in sorted(roots[key]):
                    cowner,cdesc = child.split(' ')
                    cowner,cname = cowner.rsplit('/', 1)
                    if child in srg['MD:']:
                        oowner,odesc = srg['MD:'][child].split(' ')
                        oowner,oname = oowner.rsplit('/', 1)
                        
                        new = '%s/%s %s' % (oowner, new_name, odesc)
                        if not obfed:
                            obf_whitelist.append(new)
                            
                        if oname != new_name:
                            print('  %s %s -> %s' % (child, oname, new_name))
                            srg['MD:'][child] = new
                    else:
                        new = '%s/%s %s' % (rename_class(srg['CL:'], cowner), new_name, rename_desc(srg['CL:'], cdesc))
                        if not obfed:
                            obf_whitelist.append(new)
                        
                        print('  %s NULL -> %s' % (child, new_name))
                        srg['MD:'][child] = new
                    
    return rg_idx_max

def create_new_entries(rg_idx_max, srg, o_to_n, meta, err_f):
    print('Injecting new SRG names')
       
    for owner,cls in meta.items():
        if 'fields' in cls:
            for name,fld in cls['fields'].items():
                key = '%s/%s' % (owner, name)
                if not key in srg['FD:']:
                    new_name = 'field_%s_%s_' % (rg_idx_max, name)
                    rg_idx_max += 1
                    print('  FD: %s -> %s' % (key, new_name))
                    srg['FD:'][key] = '%s/%s' % (rename_class(srg['CL:'], owner), new_name)
        if 'methods' in cls:
            for name,mtd in cls['methods'].items():
                if '<' in name: #<init>/<cinit> dont need names
                    continue
                    
                name,desc = name.replace('(', ' (').split(' ')
                key = '%s/%s %s' % (owner, name, desc)
                if not key in srg['MD:']:
                    new_name = 'func_%s_%s_' % (rg_idx_max, name)
                    rg_idx_max += 1
                    print('  MD: %s -> %s' % (key, new_name))
                    srg['MD:'][key] = '%s/%s %s' % (rename_class(srg['CL:'], owner), new_name, rename_desc(srg['CL:'], desc))
                    if len(name) > 2:
                        error('Long Name: %s -> %s' % (key, new_name))
    return rg_idx_max

def purge(dir, pattern):
    for f in os.listdir(dir):
        if re.search(pattern, f):
            os.remove(os.path.join(dir, f))
            
def getMinecraftPath():
    if   sys.platform.startswith('linux'):
        return os.path.expanduser("~/.minecraft")
    elif sys.platform.startswith('win'):
        return os.path.join(os.getenv("APPDATA"), ".minecraft")
    elif sys.platform.startswith('darwin'):
        return os.path.expanduser("~/Library/Application Support/minecraft")
    else:
        print('Cannot detect of version : %s. Please report to your closest sysadmin' % sys.platform)
        sys.exit()
        
def error(err_f, line):
    print('  %s' % (line))
    err_f.write(('%s\n' % (line)).encode())
    
if __name__ == '__main__':
    if (len(sys.argv) < 4):
        print('Usage: py MigradeMappings.py <MCPConfig> <OLD_VERSON> <NEW_VERSION> <ToyData>')
    elif (len(sys.argv) == 6):
        migrate_mappings(sys.argv[1], sys.argv[2], os.path.join('.', sys.argv[3]), os.path.join('.', sys.argv[4]), os.path.join('.', sys.argv[5]))
    elif (len(sys.argv) == 5):
        migrate_mappings(os.path.join('.', sys.argv[1]), sys.argv[2], sys.argv[3], os.path.join('.', sys.argv[4]))
    else:
        migrate_mappings(os.path.join('.', sys.argv[1]), sys.argv[2], sys.argv[3], 'output')