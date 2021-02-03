package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.*
import org.gradle.api.tasks.*

import java.util.zip.*

import net.minecraftforge.mappingverifier.MappingVerifier

public class VerifyMappings extends DefaultTask {
    @InputFile mappings
    @InputFile joined
    
    @TaskAction
    def exec() {
        Utils.init()

        MappingVerifier mv = new MappingVerifier()
        mv.loadMap(mappings)
        mv.loadJar(joined)
        mv.addDefaultTasks()

        def die = false
        if (!mv.verify()) {
            for (def t : mv.tasks) {
                if (!t.errors.isEmpty()) {
                    logger.lifecycle('Task: ' + t.name)
                    t.errors.each{ logger.lifecycle('  ' + it.stripIndent()) }
                    die = true
                }
            }
        }
        if (die)
            throw new RuntimeException('Verification failed')
    }
}