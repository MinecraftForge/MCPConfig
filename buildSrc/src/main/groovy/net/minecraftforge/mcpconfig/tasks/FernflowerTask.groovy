package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.tasks.*

public class FernflowerTask extends JarExec {
    @InputFile File libraries
    @InputFile File input
    @OutputFile File log
    @OutputFile File dest
    
    @Override
    protected void preExec() {
        standardOutput log.newOutputStream()
        setArgs(Utils.fillVariables(args, [
            'libraries': libraries,
            'input': input,
            'output': dest
        ]))
    }
}