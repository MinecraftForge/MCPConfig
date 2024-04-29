package net.minecraftforge.mcpconfig.tasks;

import org.gradle.api.*
import org.gradle.api.tasks.*

class MakeMappingsCsv extends ToolJarExec {
    @InputFile File srg
    @InputFile File client
    @InputFile File server
    @OutputFile File dest
    
    @Override
    protected void preExec() {
        setArgs(Utils.fillVariables(args, [
            'srg': srg.absolutePath,
            'client': client.absolutePath,
            'server': server.absolutePath,
            'output': dest.absolutePath
        ]))
    }
}