package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.tasks.*

public class MCInjectTask extends JarExec {
    @InputFile File access
    @InputFile File constructors
    @InputFile File exceptions
    @InputFile File input
    @OutputFile File log
    @OutputFile File dest
    
    @Override
    protected void preExec() {
        setArgs(Utils.fillVariables(args, [
            'access': access,
            'constructors': constructors,
            'exceptions': exceptions,
            'log': log,
            'input': input,
            'output': dest
        ]))
    }
}