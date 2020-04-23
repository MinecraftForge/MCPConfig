package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.tasks.*
import groovy.io.FileType
import groovy.json.JsonSlurper
import net.minecraftforge.srgutils.IMappingFile

import static org.objectweb.asm.Opcodes.*

public class CreateAccessFixes extends SingleFileOutput {
    @InputFile File srg
    @InputFile File meta
    
    @TaskAction
    protected void exec() {
        def srgO = IMappingFile.load(srg)
        def json = new JsonSlurper().parseText(meta.text)
        dest.withWriter('UTF-8') { writer ->
            json.each{ k,v ->
                json[k]['methods']?.each{ sig,data ->
                    if (data['override']) {
                        def access = json[data['override']] ? json[data['override']]['methods'] ? json[data['override']]['methods'][sig] ? json[data['override']]['methods'][sig]['access'] : null : null : null
                        if (access != null) {
                            if ((data['access'] & (ACC_STATIC | ACC_PRIVATE)) == 0) {
                                def old = data['access'] & ACC_PRIVATE ? 0 : data['access'] & ACC_PROTECTED ? 2 : data['access'] & ACC_PUBLIC ? 3 : 1
                                def top =       access   & ACC_PRIVATE ? 0 :       access   & ACC_PROTECTED ? 2 :       access   & ACC_PUBLIC ? 3 : 1
                                def names = ['PRIVATE', 'DEFAULT', 'PROTECTED', 'PUBLIC']
                                if (old < top) {
                                    def name = sig.split(' ')[0]
                                    def desc = sig.split(' ')[1]
                                    def mapped = srgO.class(k)?.remapMethod(name, desc)
                                    if (mapped == null) {
                                        print('Missing srg mapping for access: ' + k + '/' + sig + '\n')
                                    } else {
                                        writer.write(names[top] + ' ' + srgO.remapClass(k) + ' ' + mapped + ' ' + srgO.remapDescriptor(desc) + '\n')
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}