package net.minecraftforge.mcpconfig.tasks;

import org.gradle.api.*
import org.gradle.api.tasks.*

class MergeJar extends ToolJarExec {
    @InputFile File client
    @InputFile File server
    @Input String version
    @OutputFile File dest
    
    @Override
    protected void preExec() {
        setArgs(Utils.fillVariables(args, [
            'client': client.absolutePath,
            'server': server.absolutePath,
            'version': version,
            'output': dest.absolutePath
        ]))
    }
}