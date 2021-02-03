package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.*
import org.gradle.api.tasks.*

import java.util.zip.*

import net.minecraftforge.srgutils.*

public class RenameMappings extends SingleFileOutput {
    @InputFile intermediate
    @InputFile @Optional official
    
    @TaskAction
    def exec() {
        Utils.init()
        def ret
        if (official != null) {
            def inter = INamedMappingFile.load(intermediate).getMap('obf', 'srg')
            def offic = IMappingFile.load(official).reverse()
            ret = inter.rename(new IRenamer() {
                String rename(IMappingFile.IClass cls) {
                    return offic.remapClass(cls.original)
                }
            })
        } else {
            ret = IMappingFile.load(intermediate)
        }
        
        ret.write(dest.toPath(), IMappingFile.Format.TSRG2, false)
    }
}