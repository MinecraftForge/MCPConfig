package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.tasks.*
import java.util.ArrayDeque
import java.util.HashSet
import java.util.TreeMap
import java.util.TreeSet
import org.objectweb.asm.*
import groovy.io.FileType
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

public class CheckAPI extends SingleFileOutput {
    @InputFiles Set<File> apis = new HashSet<>()
    @InputFiles File input
    
    @TaskAction
    protected void exec() {
        def api = [] as TreeMap
         
        apis.each {
            api << new JsonSlurper().parseText(it.text)
        }

        def errors = [:] as TreeMap
        input.eachFileRecurse(FileType.FILES) {
            if (!it.name.endsWith('.class'))
                return
            
            it.withInputStream { ins -> 
                def clsName = null                
                def cr = new ClassReader(ins)
                cr.accept(new ClassVisitor(Opcodes.ASM9) {
                    def errs = [:] as TreeMap
                    @Override
                    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                        clsName = name
                    }

                    @Override
                    public MethodVisitor visitMethod(int access, String mtdName, String mtdDesc, String mtdSignature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            private def flds = [] as TreeSet
                            private def mtds = [] as TreeSet
                            
                            @Override
                            public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                                def apio = api.get(owner)
                                if (apio != null) {
                                    def apif = apio?.fields?.get(name)
                                    if (apif == null) {
                                        flds.add(owner + ' ' + name)
                                        logger.lifecycle(owner + ' ' + name)
                                    }
                                }
                            }
                            
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
                                def apio = api.get(owner)
                                if (apio != null) {
                                    def apim = apio?.methods?.get(name + desc)
                                    if (apim == null) {
                                        for (def parent : getParents(api, owner)) {
                                            def apip = api.get(parent)
                                            apim = apip.methods?.get(name + desc)
                                            if (apim != null) {
                                                if (!apio.containsKey('methods'))
                                                    apio.methods = [:] as TreeMap
                                                apio.methods[name + desc] = apim
                                                break
                                            }
                                        }
                                    }
                                    if (apim == null) {
                                        if ('java/lang/invoke/MethodHandle'.equals(owner) && 
                                           ('invoke'.equals(name) || 'invokeExact'.equals(name))) {
                                            //I can't find the documentation on this, but it looks like MethodHandle is special
                                            //Method handle's invoke methods are compiled to the exact arumgnets of the targeted method
                                            //So obviously it won't be in the API.
                                        } else {
                                            mtds.add(owner + ' ' + name + desc)
                                            //logger.lifecycle(owner + ' ' + name + desc)
                                        }
                                    }
                                }
                            }
                            
                            @Override
                            public void visitEnd() {
                                if (!flds.isEmpty() || !mtds.isEmpty()) {
                                    def ret = [:]
                                    if (!flds.isEmpty())
                                        ret['fields'] = flds
                                    if (!mtds.isEmpty())
                                        ret['methods'] = mtds
                                    errs[mtdName + mtdDesc] = ret
                                }
                            }
                        }
                    }
                    @Override
                    public void visitEnd() {
                        if (!errs.isEmpty())
                            errors[clsName] = errs
                    }
                }, ClassReader.SKIP_FRAMES)
            }
        }
        dest.write(new JsonBuilder(errors).toPrettyString())
        if (!errors.isEmpty())
            throw new IllegalStateException("Compiled classes referenced illegal API, check ${dest}")
    }
    
    private Set<String> getParents(def api, def cls) {
        def apic = api.get(cls)
        if (apic == null)
            return []
        if (apic.parents != null)
            return apic.parents
        
        def parents = new LinkedHashSet<>()
        def que = new ArrayDeque<>()
        que.add(cls)
        
        while (!que.isEmpty()) {
            def qcls = api.get(que.poll())
            if (qcls == null)
                continue
            
            def parent = qcls.super == null ? 'java/lang/Object' : qcls.super
            if (parents.add(parent))
                que.add(parent)
            if (qcls.interfaces != null) {
                qcls.interfaces.each { it -> 
                    if (parents.add(it))
                        que.add(it)
                }
            }
        }
        parents.retainAll { api.containsKey(it) }
        apic.parents = parents
        return parents
    }
}