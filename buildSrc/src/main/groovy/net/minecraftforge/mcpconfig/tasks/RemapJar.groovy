package net.minecraftforge.mcpconfig.tasks;

import org.gradle.api.*
import org.gradle.api.tasks.*
import net.md_5.specialsource.SpecialSource

//TODO: Convert to JarExec and download SpecialSource?
class RemapJar extends SingleFileOutput {
    @InputFile File mappings
    @InputFile File input
    
    @TaskAction
    def doTask() {
        String[] args = ['--in-jar', input.absolutePath, '--out-jar', dest.absolutePath, '--srg-in', mappings.absolutePath]
        SpecialSource.main(args)
    }
}