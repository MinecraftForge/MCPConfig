package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.*
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*

import java.util.zip.*

import net.minecraftforge.srgutils.IMappingBuilder
import net.minecraftforge.srgutils.IMappingFile.Format

abstract class ListClasses extends DefaultTask {
    @InputFile abstract RegularFileProperty getInput();
    @OutputFile abstract RegularFileProperty getOutput();

    ListClasses() {
        output.convention(project.rootProject.layout.buildDirectory.file('versions/' + project.name + '/joined.tsrg'))
    }
    
    @TaskAction
    def exec() {
        def map = IMappingBuilder.create()

        new ZipInputStream(input.asFile.get().newInputStream()).withCloseable{ jin ->
            for (def entry = jin.nextEntry; entry != null; entry = jin.nextEntry) {
                if (entry.name.endsWith('.class')) {
                    def name = entry.name.substring(0, entry.name.length() - 6)
                    map.addClass(name, name)
                }
            }
        }

        map.build().write(output.asFile.get().toPath(), Format.TSRG)
    }
}