package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.*
import org.gradle.api.tasks.*
import java.util.zip.*

public class CompareJars extends DefaultTask {
    @InputFile expected
    @InputFile actual
    
    @TaskAction
    def exec() {
        Utils.init()
        def failed = false
        def expectedData = loadJar(expected)
        def actualData = loadJar(actual)
        
        def tmp = [] as HashSet
        tmp.addAll(expectedData.keySet())
        tmp.removeAll(actualData.keySet())
        
        if (!tmp.isEmpty()) {
            logger.lifecycle('Actual jar was missing entries:')
            tmp.stream().sorted().each{ logger.lifecycle('  ' + it) }
            failed = true
        }
        
        tmp.clear()
        tmp.addAll(actualData.keySet())
        tmp.removeAll(expectedData.keySet())
        
        if (!tmp.isEmpty()) {
            logger.lifecycle('Actual jar had extra entries:')
            tmp.stream().sorted().each{ logger.lifecycle('  ' + it) }
            failed = true
        }
        
        if (failed)
            throw new GradleException("Comparison failed, see log for details")
            
        tmp.clear()
        expectedData.each{ name, data ->
            def adata = actualData.get(name)
            if (adata != null && !Arrays.equals(data, adata))
                tmp.add(name)
        }
        
        if (!tmp.isEmpty()) {
            logger.lifecycle('Jar Contents did not match: ')
            tmp.stream().sorted().each{ logger.lifecycle('  ' + it) }
            failed = true
        }
        
        if (failed)
            throw new GradleException("Comparison failed, see log for details")
    }
    
    static def loadJar(jar) {
        def ret = [:]
        new ZipFile(jar).withCloseable{ jin -> 
            jin.entries().each { entry ->
                if (!entry.isDirectory()) {
                    jin.getInputStream(entry).withCloseable{ strm ->
                        ret.put(entry.name, strm.bytes)
                    }
                }
            }
        }
        return ret
    }
}