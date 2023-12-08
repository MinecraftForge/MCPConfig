package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.*
import org.gradle.api.tasks.*

import java.nio.file.Files

import net.minecraftforge.srgutils.*

public class RenameSources extends DefaultTask {
    @InputDirectory input
    @InputFile srg
    @InputFile official
    @OutputDirectory dest
    
    @TaskAction
    def exec() {
        Utils.init()
        def names = loadMappings()
        def root = input.toPath()
        
        Files.walk(root).withCloseable{ stream ->
            stream.each { entry ->
                if(!Files.isDirectory(entry)) {
                    def path = root.relativize(entry).toString()
                    def out = new File(dest, path)
                    if (!out.parentFile.exists())
                        out.parentFile.mkdirs();
                        
                    def data
                    if (path.endsWith('.java')) {
                        data = entry.toFile().text.replaceAll(/f_\d+_|m_\d+_|func_\d+_[a-zA-Z_]+|field_\d+_[a-zA-Z_]+/) { 
                            name -> names.getOrDefault(name, name)
                        } 
                    } else
                        data = entry.toFile().text
                        
                    Files.newBufferedWriter(out.toPath()).withCloseable { writer ->
                        writer.write(data)
                    }
                }
            }
        }
    }
    
    def loadMappings() {
        def ret = [:]
        def msrg = IMappingFile.load(srg)
        def moff = IMappingFile.load(official).reverse()
        msrg.classes.each{scls -> 
            def ocls = moff.getClass(scls.original)
            if (ocls != null) {
                scls.fields.each{ sfld ->
                    if (sfld.mapped.startsWithAny('f_', 'field_'))
                        ret.put(sfld.mapped, ocls.remapField(sfld.original))
                }
                scls.methods.each{ smtd ->
                    if (smtd.mapped.startsWithAny('m_', 'func_'))
                        ret.put(smtd.mapped, ocls.remapMethod(smtd.original, smtd.descriptor))
                }
            }
        }
        return ret
    }
}