package net.minecraftforge.mcpconfig.tasks

import java.util.HashSet
import org.gradle.api.tasks.*
import groovy.io.FileType
import groovy.json.JsonSlurper
import net.minecraftforge.srgutils.IMappingFile

import static org.objectweb.asm.Opcodes.*

public class DumpOverrides extends SingleFileOutput {
    @InputFile File srg
    @InputFile File meta
    
    @TaskAction
    protected void exec() {
        def srgO = IMappingFile.load(srg)
        def json = new JsonSlurper().parseText(meta.text)
        
        def methods = [] as HashSet

        json.each{ k,v ->
            v.methods?.each{ sig,data ->
                if (data['override']) {
                    def name = sig.split(' ')[0]
                    def desc = sig.split(' ')[1]
                    def mapped = srgO.getClass(k)?.remapMethod(name, desc)
                    if (mapped == null) {
                        print('Missing srg mapping for access: ' + k + '/' + sig + '\n')
                    } else {
                        methods.add(srgO.remapClass(k) + '/' + mapped + ' ' + srgO.remapDescriptor(desc))
                    }
                }
            }
        }

        dest.withWriter('UTF-8') { writer ->
            methods = methods.sort{it}
            methods.each{ writer.write(it + '\n') }
        }
    }
}