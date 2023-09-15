package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.*
import org.gradle.api.tasks.*

import java.util.zip.*

public class DeduplicateJars extends DefaultTask {
    @InputFile clientIn
    @InputFile joinedIn
    @InputFile serverIn
    
    @OutputFile duplicates
    
    @OutputFile clientOut
    @OutputFile joinedOut
    @OutputFile serverOut
    
    @TaskAction
    def exec() {
        Utils.init()
        
        def inputs = [
            client: loadZip(clientIn),
            server: loadZip(serverIn),
            joined: loadZip(joinedIn)
        ]
        
        def outputs = [
            client: clientOut,
            server: serverOut,
            joined: joinedOut
        ]
        
        List dupes = []
        
        new ZipOutputStream(duplicates.newOutputStream()).withCloseable{ du ->
            inputs.client.each { name, data ->
                if (inputs.server.containsKey(name) && inputs.joined.containsKey(name)) {
                    if (inputs.client[name] == inputs.server[name] &&
                        inputs.client[name] == inputs.joined[name]) {
                        du.putNextEntry(new ZipEntry(name))
                        du.write(inputs.client[name])
                        dupes.add(name)
                    }
                }
            }
        }
        inputs.each{ side ->
            dupes.each{ side.value.remove(it) }
        }
        
        inputs.each{ side, files ->
            new ZipOutputStream(outputs[side].newOutputStream()).withCloseable{ out ->
                files.each{ name, data -> 
                    out.putNextEntry(new ZipEntry(name))
                    out.write(data)
                }
            }
        }
    }
    
    def loadZip(def file) {
        def ret  = [:]
        def zip = new ZipFile(file)
        zip.entries().each{ entry ->
            if (!entry.isDirectory())
                ret[entry.name] = zip.getInputStream(entry).bytes
        }
        zip.close()
        return ret
    }
}