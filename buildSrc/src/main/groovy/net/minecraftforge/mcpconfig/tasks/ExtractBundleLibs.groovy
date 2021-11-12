package net.minecraftforge.mcpconfig.tasks;

import org.gradle.api.*
import org.gradle.api.tasks.*

class ExtractBundleLibs extends ToolJarExec {
    @InputFile File input
    @OutputDirectory File dest
    
    @Override
    protected void preExec() {
        standardOutput = JarExec.NULL_OUTPUT
        setArgs(Utils.fillVariables(args, [
            'input': input.absolutePath,
            'output': dest.absolutePath
        ]))
    }
}