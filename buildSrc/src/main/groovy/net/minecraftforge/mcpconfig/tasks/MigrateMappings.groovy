package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.objectweb.asm.Type
import groovy.io.FileType
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import net.minecraftforge.srgutils.IMappingFile
import net.minecraftforge.srgutils.IMappingBuilder
import net.minecraftforge.srgutils.INamedMappingFile
import java.io.*
import java.util.function.Function
import java.util.function.BiFunction
import java.util.function.ToIntFunction
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.zip.ZipFile

import static org.objectweb.asm.Opcodes.*

public class MigrateMappings extends DefaultTask {
    public class Data {
        @InputFile meta
        @InputFile official
        @InputFile map
    }
    
    @Nested List<Data> parents = new ArrayList<>()
    @Nested Data current
    // Output
    @OutputFile newChain
    @OutputFile newMappings
    
    
    void parent(Closure config) {
        def ret = new Data()
        project.configure(ret, config)
        parents.add(ret)
    }
    
    void current(Closure config) {
        current = new Data()
        project.configure(current, config)
    }
    
    @TaskAction
    protected void exec() {
        Utils.init()
    
        def official = loadMappings(current.official).getMap('left', 'right')
        def matches = loadMappings(current.map).getMap('right', 'left')
        def meta = new JsonSlurper().parse(current.meta)
        meta.removeAll{ it.key.contains('minecraftforge') }
        
        def off2id = buildOfficialToId()
        def current_id = off2id.isEmpty() ? 1 : off2id.max{ it.value }.value + 1
        //logger.lifecycle('Max ID: ' + current_id)
        def ids = [:]
        
        def parentMap = (parents.isEmpty() ? IMappingBuilder.create('obf', 'srg', 'id').build() : loadMappings(parents.get(0).map))
        if (parentMap.names.size() == 2) // Old style without unique ids, start fresh
            parentMap = IMappingBuilder.create('left', 'right').getMap('left', 'right')
        else
            parentMap = parentMap.getMap('obf', 'id')
        def parentOff = (parents.isEmpty() ? IMappingBuilder.create('left', 'right').build() : loadMappings(parents.get(0).official)).getMap('right', 'left')

        // Obfed name to id function, searches parents and others, hence lambda to collect locals
        ToIntFunction<String> genClassId = { cls ->
            def mc = matches.getClass(cls)
            if (mc != null) {
                def pc = parentMap.getClass(mc.mapped)
                if (pc != null)
                    return Integer.parseInt(pc.mapped)
            }
            def off = official.getClass(cls)
            if (off != null) {
                if (off2id.containsKey(off.mapped))
                    return off2id.get(off.mapped)
            }
            if (mc != null) {
                def po = parentOff.getClass(mc.mapped)
                if (po != null && off2id.containsKey(po.mapped))
                    return off2id.get(po.mapped)
            }
            return current_id++
        }        
        ToIntFunction<String> findClassId = { cls -> return ids.computeIfAbsent(cls, {k -> genClassId.applyAsInt(cls)}) }
        ToIntFunction<Map> genFieldId = { info ->
            def mc = matches.getClass(info.owner)
            def mf = mc?.getField(info.name)
            
            if (mc != null && mf != null) {
                def id = parentMap.getClass(mc.mapped)?.getField(mf.mapped)?.mapped
                if (id != null)
                    return Integer.parseInt(id)
            }
            
            def off = official.getClass(info.owner)
            def offF = off?.getField(info.name)
            if (off != null && offF != null) {
                def key = off.mapped + '/' + offF.mapped
                if (off2id.containsKey(key))
                    return off2id.get(key)
            }
            
            if (mc != null && mf != null) {
                def po = parentOff.getClass(mc.mapped)
                def poF = po?.getField(mf.mapped)
                if (po != null && poF != null) {
                    def key = po.mapped + '/' + poF.mapped
                    if (off2id.containsKey(key))
                        return off2id.get(key)
                }
            }
            return current_id++
        }
        ToIntFunction<Map> findFieldId = { info -> return ids.computeIfAbsent(info.owner + '/' + info.name, {k -> genFieldId.applyAsInt(info)}) }
        ToIntFunction<Map> genMethodId = { mtd -> 
            def mc = matches.getClass(mtd.owner)
            def mm = mc?.getMethod(mtd.name, mtd.desc)
            if (mc != null && mm != null) {
                def id = parentMap.getClass(mc.mapped)?.getMethod(mm.mapped, mm.mappedDescriptor)?.mapped
                if (id != null)
                    return Integer.parseInt(id)
            }
            
            def off = official.getClass(mtd.owner)
            def offM = off?.getMethod(mtd.name, mtd.desc)
            if (off != null && offM != null) {
                def key = off.mapped + '/' + offM.mapped + offM.mappedDescriptor
                if (off2id.containsKey(key))
                    return off2id.get(key)
            }
            
            if (mc != null && mm != null) {
                def po = parentOff.getClass(mc.mapped)
                def poM = po?.getMethod(mm.mapped, mm.mappedDescriptor)
                if (po != null && poM != null) {
                    def key = po.mapped + '/' + poM.mapped + poM.mappedDescriptor
                    if (off2id.containsKey(key))
                        return off2id.get(key)
                }
            }
            
            return current_id++
        }
        ToIntFunction<Map> findMethodId = { mtd -> return ids.computeIfAbsent(mtd.owner + '/' + mtd.name + mtd.desc, {k -> genMethodId.applyAsInt(mtd)}) }
        ToIntFunction<Map> findParamId = { par ->            
            def oc = official.getClass(par.owner)
            def om = oc.getMethod(par.name, par.desc)
            return ids.computeIfAbsent(oc.mapped + '/' + om.mapped + om.mappedDescriptor + '.' + par.index, {k -> current_id++})
        }

        IMappingBuilder builder = IMappingBuilder.create('obf', 'srg', 'id')
        def classes = [:]
        
        for (def cls : meta.keySet())
            addClass(cls, classes, builder, official, meta, findClassId)
           
        def forced = buildForcedMethods(findMethodId, meta)
        
        //logger.lifecycle(new JsonBuilder(roots).toPrettyString())
            
        for (def cls : meta.keySet()) {
            def data = meta.get(cls)
            def clsBuilder = classes[cls].cls
            if (data.fields != null) {
                for (def fld : data.fields.keySet())
                    addField(cls, clsBuilder, fld, data.fields.get(fld), findFieldId)
            }
            if (data.methods != null) {
                for (def mtd : data.methods.keySet())
                    addMethod(classes, cls, clsBuilder, mtd, data.methods.get(mtd), findMethodId, findParamId, forced)
            }
        }
        builder.build().write(newMappings.toPath(), IMappingFile.Format.TSRG2)
    }
    
    def loadMappings(def file) {
        def is = file.newInputStream()
        def map = INamedMappingFile.load(is)
        is.close()
        return map
    }
    
    // Gather the official name to our ID map. This allows us to 'resusitate' values that got lost 
    // due to being pulled out in one version then added again in the next
    def buildOfficialToId() {
        def ret = [:]
        parents.forEach{ p ->
            def o2n = loadMappings(p.official).getMap('left', 'right')
            def o2s2i = loadMappings(p.map)
            if (o2s2i.names.size() != 3) // Old things
                return
            def o2s = o2s2i.getMap('obf', 'srg')
            def o2i = o2s2i.getMap('obf', 'id')
            o2s.classes.each{ oc ->
                def nc = o2n.getClass(oc.original)
                def ic = o2i.getClass(oc.original)
                if (nc != null) {
                    ret.putIfAbsent(nc.mapped, Integer.valueOf(ic.mapped))
                    oc.fields.each{ _of ->
                        def nf = nc.getField(_of.original)
                        def _if = ic.getField(_of.original)
                        if (nf != null)
                            ret.putIfAbsent(nc.mapped + '/' + nf.mapped, Integer.valueOf(_if.mapped))
                    }
                    oc.methods.each{ om ->
                        def nm = nc.getMethod(om.original, om.descriptor)
                        def im = ic.getMethod(om.original, om.descriptor)
                        if (nm != null) {
                            def key = nc.mapped + '/' + nm.mapped + nm.mappedDescriptor
                            if (!ret.containsKey(key)) {
                                ret.put(key, Integer.valueOf(im.mapped))
                                im.parameters.each{ ret.put(key + '.' + it.index, Integer.valueOf(it.mapped)) }
                            }
                        }
                    }
                }
            }
        }
        return ret
    }
    
    /* These are methods that must have their names linked, because they are defined in an obf class/interface,
     * but there is some child class that satisfies both with the same method. 
     * interface A { void foo(); }
     * interface B { void foo(); }
     * class C implements A,B { void foo(){} }
     **/
    def buildForcedMethods(def findId, def meta) {
        def roots = [] as HashSet
        def links = [:]
        for (def cls : meta.keySet()) {
            def data = meta.get(cls)
            if (data.methods != null) {
                for (def mtd : data.methods.keySet()) {
                    def mtdd = data.methods.get(mtd)
                    if (mtdd.overrides == null)
                        continue
                    def self = [owner: cls, name: mtd.split('\\(')[0], desc: '(' + mtd.split('\\(')[1]]

                    for (def override : mtdd.overrides) {
                        roots.add(override)
                        //links.computeIfAbsent(override, {k -> [k] as Set}).add(self)
                            
                        for (def other : mtdd.overrides) {
                            if (override == other)
                                continue
                            links.computeIfAbsent(other, {k -> [k] as Set}).add(override)
                            links.computeIfAbsent(override, {k -> [k] as Set}).add(other)
                        }
                    }
                }
            }
        }
        def linkSets = []
        for (def link : links.keySet()) {
            def sets = [] as HashSet
            def l = links.get(link)
            l.each {c -> linkSets.findAll{ it.contains(c) }.each{ sets.add(it) } }
            linkSets.findAll{ it.contains(link) }.each{ sets.add(it) }
            
            l.add(link)
            if (sets.size() == 0) {
                linkSets.add(l)
            } else {
                linkSets.removeAll(sets)
                sets.add(l)
                linkSets.add(l.flatten())
            }
        }
        
        def forced = [:]
        for (def set : linkSets) {
            def sids = set.stream().map{ findId.applyAsInt(it) }.collect()
            def name = null
            for (def ord : set) {
                if (!meta.containsKey(ord.owner)) // Things outside our codebase are not obfed
                    name = ord.name
            }
            if (name == null)
                name = 'm_' + sids.min() + '_'
            sids.each{ forced.put(it, name) }
        }
        roots.each{ findId.applyAsInt(it) }
        return forced
    }
    
    def addClass(def cls, def classes, def builder, def official, def meta, def findId) {
        def ret = classes.get(cls)
        if (ret != null)
            return ret.name
            
        def qualified = null
        def id = -1
        
        if (cls.indexOf('$') != -1) {
            def (parent, child) = cls.rsplit('$')
            parent = addClass(parent, classes, builder, official, meta, findId)
                
            def name = null
            id = findId.applyAsInt(cls)
                
            if (child.isInteger()) {
                /* Anon subclasses are obfuscicated in simple alphabetical order.
                 *   AE: 1 10 11 2 3 4 5 6 7 8 9
                 * This means if there are more then 9 inner classes, they do not
                 * match the origial id list. So here we reverse it to the original index.
                 * We *could* reconstruct the index by finding the highest id. Building a 
                 * list of all those numbers, doing a simple alphabetical sort, then 
                 * taking the entry at our existing index. But i'm lazy so we just steal
                 * it from the official names.
                 **/
                name = official.getClass(cls).mapped.rsplit('$')[1]
            } else {
                name = 'C_' + id + '_'
            }
            qualified = parent + '$' + name
        } else {
            id = findId.applyAsInt(cls)
                
            /* Note: We are intentionally ignoring/not checking if the class in unobfusicated.
             * This is because if you want to use official names, you can manually apply those
             * mappings yourself and ignore our names.
             */
            qualified = 'net/minecraft/src/C_' + id + '_'
        }
        
        classes.put(cls, [name: qualified, id: id, cls: builder.addClass(cls, qualified, Integer.toString(id))])
        return qualified
    }

    // Fields are the most simple, they can't be overriden, so all we are about
    // is making sure enums get the correct names. Which is set in the 'force' 
    // entry from Mapping toy. Else we just set it to 'f_{ID}_'
    def addField(def cls, def builder, def fld, def data, def findId) {
        def id = findId.applyAsInt([owner: cls, name: fld])
        def name = data.force ?: 'f_' + id + '_'
        builder.field(fld, name, id.toString())
    }
    
    def addMethod(def classes, def cls, def builder, def mtd, def data, def findId, def findParId, def forced) {
        def debug = false //'aqa.d()Lnr;'.equals(cls + '.' + mtd)
        def (mname, desc) = mtd.rsplit('(')
        desc = '(' + desc
        def self = [owner: cls, name: mname, desc: desc]
        def id = findId.applyAsInt(self)
        
        def name = data.force ?: forced.get(id)
        if (debug) logger.lifecycle('A: ' + name)
        
        if (mname.charAt(0) == '<') //  <init> and <clinit> are special and must be named this
            name = mname
        if (debug) logger.lifecycle('B: ' + name)
            
        if ('main([Ljava/lang/String;)V'.equals(mtd) && (data.access & (ACC_STATIC | ACC_PUBLIC)) == (ACC_STATIC | ACC_PUBLIC))
            name = 'main'
        if (debug) logger.lifecycle('C: ' + name)
            
        if (name == null && data.overrides != null) {
            def oids = []
            for (def ord : data.overrides) {
                // if the class isn't in our codebase, then trust the name
                if (!classes.containsKey(ord.owner)) {
                    name = ord.name
                    if (debug) logger.lifecycle('D: ' + name + ' ' + ord)
                    break
                } else { // If it is, then it should of been assigned an ID earlier so use it's id.
                    def oid = findId.applyAsInt(ord)
                    if (debug) logger.lifecycle('O: ' + oid + ' ' + ord)
                    if (forced.containsKey(oid)) {
                        name = forced.get(oid)
                        if (debug) logger.lifecycle('E: ' + name + ' ' + ord)
                        break
                    } else
                        oids.add(oid)
                }
            }
            if (name == null)
                name = 'm_' + oids.min() + '_'
            if (debug) logger.lifecycle('F: ' + name + ' ' + oids)
        }
        if (name == null)
            name = 'm_' + id + '_'
        if (debug) logger.lifecycle('G: ' + name)
            
        //forced.put(id, name) // Track all assigned names to make sure children can get the forced name.
        def mbuilder = builder.method(desc, mname, name, id.toString())
        
        if (data.access != null && (data.access & ACC_STATIC) != 0) {
            mbuilder.meta('is_static', 'true')
        }

        int idx = 0
        for (def par : Type.getArgumentTypes(desc)) {
            def pid = findParId.applyAsInt([owner: cls, name: mname, desc: desc, index: idx])
            mbuilder.parameter(idx++, 'o', 'p_' + pid + '_', pid.toString())
        }
        
    }
}