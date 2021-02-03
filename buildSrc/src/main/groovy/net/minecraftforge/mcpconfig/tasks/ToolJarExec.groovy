package net.minecraftforge.mcpconfig.tasks;

import org.gradle.api.*
import org.gradle.api.tasks.*

class ToolJarExec extends JavaExec {
    def config(def cfg, def task) {
        classpath = project.files(task.dest)
        args = cfg.args
        jvmArgs = cfg.jvmargs
    }
    
    @Override
    public final void exec() {
        this.preExec()
        super.exec()
    }
    
    protected void preExec(){}
}