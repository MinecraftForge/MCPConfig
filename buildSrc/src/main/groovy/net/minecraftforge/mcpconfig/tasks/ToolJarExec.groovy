package net.minecraftforge.mcpconfig.tasks;

import org.gradle.api.tasks.*
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

import javax.inject.Inject

abstract class ToolJarExec extends JavaExec {
    def config(def cfg, def task) {
        classpath = project.files(task.dest)
        args = cfg.args
        jvmArgs = cfg.jvmargs
    }

    ToolJarExec() {
        def javaTarget = project.ext.JAVA_TARGET
        if (javaTarget != null) {
            javaLauncher = javaToolchainService.launcherFor {
                it.languageVersion = JavaLanguageVersion.of(javaTarget)
            }
        }
    }

    @Inject
    abstract JavaToolchainService getJavaToolchainService()

    @Override
    public final void exec() {
        this.preExec()
        super.exec()
        this.postExec()
    }
    
    protected void preExec(){}
    protected void postExec(){}
}