package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.tasks.*
import java.util.zip.*

class FernflowerTask extends ToolJarExec {
    @InputFile File libraries
    @InputFile File input
    @OutputFile File log
    @OutputFile File dest
    
    @Override
    protected void preExec() {
        var logStream = log.newOutputStream()
        standardOutput = logStream
        errorOutput = logStream
        setArgs(Utils.fillVariables(args, [
            'libraries': libraries,
            'input': input,
            'output': dest
        ]))
    }
    
    @Override
    protected void postExec() {
        if (!dest.exists())
            throw new IllegalStateException('Could not find fernflower output: ' + dest)
        List<String> failed = []
        new ZipFile(dest).withCloseable { zip ->
            zip.entries().findAll { !it.directory && it.name.endsWith('.java') }.each { e ->
                String data = zip.getInputStream(e).text
                if (data.isEmpty() || data.contains("\$FF: Couldn't be decompiled"))
                    failed.add(e.name)
            }
        }
        if (!failed.isEmpty()) {
            logger.lifecycle('Failed to decompile: ')
            failed.each { logger.lifecycle('  ' + it) }
            throw new IllegalStateException('Decompile failed')
        }
    }
}