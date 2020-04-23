package net.minecraftforge.mcpconfig.tasks

import java.util.HashSet
import org.gradle.api.tasks.*
import groovy.io.FileType
import groovy.json.JsonSlurper
import net.minecraftforge.srgutils.IMappingFile

import static org.objectweb.asm.Opcodes.*

public class DumpStatics extends SingleFileOutput {
    @InputFile File srg
    @InputFile File meta
    
    @TaskAction
    protected void exec() {
        def srgO = IMappingFile.load(srg)
        def json = new JsonSlurper().parseText(meta.text)
        
        def methods = [] as HashSet

        json.each{ cls,data ->
            data['methods']?.findAll{k,v -> (v['access'] & ACC_STATIC) != 0}.each{ sig,__ ->
                def name = sig.split(' ')[0]
                def desc = sig.split(' ')[1]
                def mapped = srgO.getClass(cls)?.remapMethod(name, desc)
                if (mapped != null && !desc.contains('()') && mapped.contains('func_'))
                    methods.add(mapped)
            }
        }

        dest.withWriter('UTF-8') { writer ->
            methods = methods.sort{it}
            methods.each{ writer.write(it + '\n') }
        }
    }
}