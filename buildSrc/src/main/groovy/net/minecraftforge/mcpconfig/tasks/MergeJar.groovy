package net.minecraftforge.mcpconfig.tasks;

import org.gradle.api.*
import org.gradle.api.tasks.*
import net.minecraftforge.mergetool.Merger
import net.minecraftforge.mergetool.AnnotationVersion

class MergeJar extends SingleFileOutput {
    @InputFile File client
    @InputFile File server
    //@InputFile File mappings
    @Input String version
    @Input boolean annotate = true
    
    @TaskAction
    def doTask() {
        Merger merger = new Merger(client, server, dest)
        
        /*
        mappings.eachLine { line ->
            line = (line + '#').split('#')[0].stripIndent()
            if (line.startsWith('\t') || line.indexOf(' ') == -1)
                return
            merger.whitelist(line.split(' ')[0])
        }
        */
        if (annotate)
            merger.annotate(AnnotationVersion.fromVersion(version))
        merger.keepData()
        merger.skipMeta()
        merger.process()
    }
}