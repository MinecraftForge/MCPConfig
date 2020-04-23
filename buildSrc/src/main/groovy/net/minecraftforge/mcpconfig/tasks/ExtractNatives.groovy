package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.*
import org.gradle.api.tasks.*
import groovy.io.FileType
import groovy.json.JsonSlurper
import net.minecraftforge.srgutils.IMappingFile
import java.util.zip.ZipFile

import static org.objectweb.asm.Opcodes.*

public class ExtractNatives extends DefaultTask {
    @InputFile File meta
    @InputFiles File cache
    @OutputDirectory File dest
    
    @TaskAction
    protected void exec() {
        def json = new JsonSlurper().parse(meta)
        json.libraries.findAll{ Utils.testJsonRules(it.rules) && it.natives != null && it.natives.containsKey(Utils.osName) }
        .each { lib -> 
            def art = lib.downloads.classifiers[lib.natives[Utils.osName]]
            if (art != null) {
                def jar = new File(cache, art.path)
                if (jar.exists()) {
                    new ZipFile(jar).withCloseable{ zip ->
                        zip.entries().findAll{ !it.isDirectory() && !it.name.startsWith('META-INF/')  && !it.name.endsWith('.sha1') && !it.name.endsWith('.git') }.each { entry ->
                            def target = new File(dest, entry.name)
                            if (!target.exists()) {
                                target.parentFile.mkdirs()
                                target.bytes = zip.getInputStream(entry).bytes
                            }
                        }
                    }
                }
            }
        }
    }
}