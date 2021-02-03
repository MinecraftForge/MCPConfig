package net.minecraftforge.mcpconfig.tasks;

import org.gradle.api.*
import org.gradle.api.tasks.*

import java.util.TreeSet

class CreateFernflowerLibraries extends SingleFileOutput {
    @InputFile meta
    @Input config
    @Input side
    @Input root
    @InputFiles File[] extras = []
    
    @TaskAction
    def exec() {
        Utils.init()
        def libs = new TreeSet<>()
        
        libs.addAll(extras)
        
        if ('client'.equals(side) || 'joined'.equals(side)) {
            meta.json.libraries.each{ lib ->
                if (lib.downloads.artifact != null)
                    libs.add(new File(root, lib.downloads.artifact.path))
                
                lib.downloads.get('classifiers', [:]).forEach{ k,v ->
                    if (!k.contains('natives') && !'sources'.equals(k) && !'javadoc'.equals(k))
                        libs.add(new File(root, v.path))
                }
            }
        }
        
        config.libraries.get(side)?.collect{ it.toMavenPath() }?.each { libs.add(new File(root, it)) }
        dest.text = libs.collect{ '-e=' + it.absolutePath }.join('\n')
    }
}