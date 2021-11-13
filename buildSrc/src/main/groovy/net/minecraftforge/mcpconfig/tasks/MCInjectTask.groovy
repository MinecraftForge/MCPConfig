package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.tasks.*

//Deprecated.. Only here until we migrate all the old versions to not use MCInject
public abstract class MCInjectTask extends ToolJarExec {
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