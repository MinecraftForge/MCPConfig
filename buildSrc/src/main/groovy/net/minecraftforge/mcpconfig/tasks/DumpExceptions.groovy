package net.minecraftforge.mcpconfig.tasks

import java.util.HashSet
import java.util.TreeMap
import org.gradle.api.tasks.*
import groovy.json.JsonSlurper
import net.minecraftforge.srgutils.IMappingFile

public class DumpExceptions extends SingleFileOutput {
    @InputFile File exec
    @InputFile File srg
    @InputFile File meta
    
    @TaskAction
    protected void exec() {
        def srgO = IMappingFile.load(srg)
        def known = [:]
        
        srgO.classes.each{ cls -> 
            cls.methods.each{ mtd -> 
                known[cls.mapped + '/' + mtd.mapped + ' ' + mtd.mappedDescriptor] = cls.original + '/' + mtd.original + ' ' + mtd.descriptor
            }
        }
        
        def methods = [:] as TreeMap

        def current_class = null
        def line_num = 0

        exec.eachLine { line ->
            line_num++
            if (line.indexOf('#') != -1)
                line = line.substring(0, line.indexOf('#'))
            if (line.trim().isEmpty())
                return

            def pts = line.trim().split(' ')
            if (line.startsWith('\t')) {
                if (current_class == null)
                    throw new Exception('Invalid exceptions.txt format on line #' + line_num + ': ' + line)
                def key = current_class + '/' + pts[0] + ' ' + pts[1]
                if (!methods[key])
                    methods[key] = [] as HashSet
                for (int x = 2; x < pts.length; x++)
                    methods[key].add(pts[x])
            } else if (pts.length == 1) {
                current_class = pts[0]
            } else if (pts.length >= 3) {
                current_class = null
                def key = pts[0] + '/' + pts[1] + ' ' + pts[2]
                if (!methods[key])
                    methods[key] = [] as HashSet
                for (int x = 3; x < pts.length; x++)
                    methods[key].add(pts[x])
            } else
                throw new Exception('Invalid exceptions.txt format on line #' + line_num + ': ' + line)
        }

        def json = new JsonSlurper().parseText(meta.text)
        json.each{ k,v ->
            v['methods']?.each{ sig,data ->
                if (data['override']) {
                    def name = sig.split(' ')[0]
                    def desc = sig.split(' ')[1]
                    def descM = srgO.remapDescriptor(desc)
                    def cls = srgO.getClass(k)
                    def clsO = srgO.getClass(data.override)
                    
                    def mtd = (cls == null ? k + '/' + name : cls.mapped + '/' + cls.remapMethod(name, desc)) + ' ' + descM
                    def ord = (clsO == null ? data.override + '/' + name : clsO.mapped + '/' + clsO.remapMethod(name, desc)) + ' ' + descM

                    if (methods[ord]) {
                        if (!methods[mtd])
                            methods[mtd] = [] as HashSet
                        methods[ord].each{ methods[mtd].add(it) }

                        if (data.bouncer != null) {
                            def bnc = (cls == null ? k + '/' + data.bouncer.name : cls.mapped + '/' + cls.remapMethod(data.bouncer.name, data.bouncer.desc)) + ' ' + srgO.remapDescriptor(data.bouncer.desc)
                            if (!methods[bnc])
                                methods[bnc] = [] as HashSet
                            methods[ord].each{ methods[bnc].add(it) }
                        }
                    }
                }
            }
        }

        dest.withWriter('UTF-8') { writer ->
            methods.keySet().findAll{known.containsKey(it) || it.contains('/<init> ')}.each{ writer.write(it + ' ' + methods[it].join(' ') + '\n') }
        }
    }
}