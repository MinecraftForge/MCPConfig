package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.*
import org.gradle.api.tasks.*

import java.util.zip.*

import net.minecraftforge.srgutils.IMappingFile

public class SplitJar extends DefaultTask {
    @InputFile mappings
    @InputFile source
    @OutputFile File slim
    @OutputFile File extra
    
    @TaskAction
    def exec() {
        Utils.init()
        def classes = IMappingFile.load(mappings).classes.collect{ it.original + '.class' }.toSet()
        
        new ZipOutputStream(slim.newOutputStream()).withCloseable{ so ->
            new ZipOutputStream(extra.newOutputStream()).withCloseable{ eo ->
                new ZipInputStream(source.newInputStream()).withCloseable{ jin ->
                    for (def entry = jin.nextEntry; entry != null; entry = jin.nextEntry) {
                        def out = classes.contains(entry.name) ? so : eo
                        def oentry = new ZipEntry(entry.name)
                        oentry.lastModifiedTime = entry.lastModifiedTime
                        out.putNextEntry(oentry)
                        out << jin
                    }
                }
            }
        }
    }
}