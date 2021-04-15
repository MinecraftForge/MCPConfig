package net.minecraftforge.mcpconfig.tasks

import java.util.HashMap
import java.util.ArrayList
import java.util.List
import org.gradle.api.*
import org.gradle.api.tasks.*
import groovy.json.JsonSlurper
import net.minecraftforge.srgutils.IMappingFile

public class CreateProjectTemplate extends DefaultTask {
    @Input String distro
    @InputFile File template
    @Optional @InputFile File meta
    @Input List<String> libraries = new ArrayList<>()
    @Input Map<String, String> replace = new HashMap<>()
    
    @OutputDirectory File dest
    
    def library(lib) {
        libraries.add(lib)
    }
    
    def libraryFile(lib) {
        libraries.add("files('" + lib.absolutePath.replace('\\', '/') + "')")
    }
    
    def replace(key, value) {
        replace.put(key, value)
    }
    
    def replaceFile(key, value) {
        replace(key, value != null && value.exists() ? "'" + value.absolutePath.replace('\\', '/') + "'" : 'null')
    }
    
    @TaskAction
    protected void exec() {
        if (!dest.exists())
            dest.mkdirs()

        new File(dest, 'settings.gradle').withWriter('UTF-8') { 
            it.write("rootProject.name = '${project.name}-${distro}'")
        }
        
        def data = template.text
        def libs = []
        
        if (meta != null) {
            def json = new JsonSlurper().parse(meta)
            libs += json.libraries.findAll { Utils.testJsonRules(it.rules) }.collect { lib -> "'${lib.name}' " }.unique { a, b -> a <=> b }
        }
        libs += libraries

        data = data.replace('{libraries}', 'compile ' + libs.join('\n    compile '))
        data = data.replace('{distro}', distro)
        for (def k : replace.keySet()) {
            data = data.replace(k, replace.get(k))
        }
        
        new File(dest, 'build.gradle').withWriter('UTF-8') { it.write(data) }
    }
}