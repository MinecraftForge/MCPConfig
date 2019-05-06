package net.minecraftforge.mcpconfig.tasks;

import org.gradle.api.*
import org.gradle.api.tasks.*
import java.io.*
import java.util.jar.*
import java.nio.file.Files

class JarExec extends JavaExec {
    private static final Attributes.Name MAIN_CLASS = Attributes.Name.MAIN_CLASS;
    static final OutputStream NULL_OUTPUT = new OutputStream() { public void write(int b){} }

    @InputFile File jar
    
    @Override
    public final void exec() {
        this.findMainClass()
        this.pushClasspath()
        this.preExec()
        //project.logger.lifecycle('Arguments: ' + args)
        super.exec()
    }
    
    def findMainClass() {
        if (this.getMain() != null)
            return;

        try {
            new JarInputStream(Files.newInputStream(this.jar.toPath())).withCloseable { jis -> this.setMain(jis.getManifest().getMainAttributes().getValue(MAIN_CLASS)) }
        } catch (final IOException e) {
            throw new IllegalStateException("An exception was encountered while trying to locate the " + MAIN_CLASS + " attribute from the manifest of '" + this.jar + '\'', e)
        }

        if (this.getMain() == null)
            throw new IllegalStateException("The specified JAR ('" + this.jar + "') does not have the " + MAIN_CLASS + " attribute defined in the manifest.")
    }
    def pushClasspath() {
        this.classpath(this.jar)
    }
    
    protected void preExec(){
    }
}