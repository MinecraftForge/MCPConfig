package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*

import java.lang.reflect.*
import java.util.function.Function
import java.util.TreeMap
import java.util.zip.*
import org.objectweb.asm.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*

import static org.objectweb.asm.Opcodes.*

public class ExtractInheritance extends SingleFileOutput {
    @InputFile def input
    @InputFiles def libraries = []
    
    def library(def lib){ libraries.add(lib) }
    
    @TaskAction
    def exec() {
        def inClasses = [:] as TreeMap
        def libClasses = [:]
        def failedClasses = []
        
        Function<String, ClassInfo> getClassInfo = {name ->
            def ret = inClasses.get(name)
            if (ret != null)
                return ret
            ret = libClasses.get(name)
            if (ret == null && !failedClasses.contains(name)) {
                try {
                    Class<?> cls = Class.forName(name.replaceAll('/', '.'), false, this.class.classLoader)
                    ret = new ClassInfo(cls)
                    libClasses.put(name, ret)
                } catch (ClassNotFoundException ex) {
                    logger.lifecycle('Cant Find Class: ' + name)
                    failedClasses.add(name)
                }
            }
            return ret
        }
        
        
        readJar(input, inClasses)
        libraries.each{ readJar(it, libClasses) }
        inClasses.values().each{ resolveClass(getClassInfo, it) }
        dest.json = inClasses
    }

    def readJar(def input, def classes) {
        new ZipInputStream(input.newInputStream()).withCloseable{ jin ->
            for (def entry = jin.nextEntry; entry != null; entry = jin.nextEntry) {
                if (!entry.name.endsWith('.class') || entry.name.startsWith('.'))
                    continue
                def reader = new ClassReader(jin)
                def node = new ClassNode()
                reader.accept(node, 0)
                def info = new ClassInfo(node)
                classes.put(info.name, info)
            }
        }
    }
    def resolveClass(def getClassInfo, def cls) {
        if (cls == null || cls.resolved)
            return
            
        if (!cls.name.equals('java/lang/Object') && cls.superName != null)
            resolveClass(getClassInfo, getClassInfo.apply(cls.superName))
        
        cls.interfaces.each{ resolveClass(getClassInfo, getClassInfo.apply(it)) }
        
        cls.methods.values().each{ mtd ->
            if ('<init>'.equals(mtd.name) || '<cinit>'.equals(mtd.name) || ((mtd.access ?: 0) & (ACC_PRIVATE | ACC_STATIC)) != 0)
                return
            
            def override
            def que = new ArrayDeque<>()
            def processed = [] as Set
            
            addQueue(getClassInfo, cls.superName, processed, que)
            cls.interfaces.each{ addQueue(getClassInfo, it, processed, que) }
                
            while (!que.empty) {
                def c = que.poll()
                addQueue(getClassInfo, c.superName, processed, que)
                c.interfaces.each{ addQueue(getClassInfo, it, processed, que) }
                    
                def m = c.getMethod(mtd.name, mtd.desc)
                
                int bad_flags = ACC_PRIVATE | ACC_FINAL | ACC_STATIC;
                if (m == null || (m.access & bad_flags) != 0)
                    continue
                
                override = m
            }
            
            if (override != null)
                mtd.override = override.parent.name
        }
        
        cls.methods.values().findAll{ it.bouncer != null && it.override != null }.each{ mtd ->
            def bouncer = cls.methods.get(mtd.bouncer.name + mtd.bouncer.desc)
            if (bouncer != null && bouncer.override == null)
                bouncer.override = mtd.override
        }
    
        cls.resolved = true
    }
    
    def addQueue(def getClassInfo, def cls, def visited, def que) {
        if (!visited.contains(cls) && cls != null) {
            ClassInfo ci = getClassInfo.apply(cls)
            if (ci != null)
                que.add(ci)
            visited.add(cls)
        }
    }
    
    static class ClassInfo {
        def name, access, superName, interfaces, methods
        def resolved = false
        
        ClassInfo(ClassNode node) {
            this.name = node.name
            this.access = node.access
            this.superName = node.superName
            this.interfaces = node.interfaces
            this.methods = node.methods.collect{ new MethodInfo(this, it) }.collectEntries{ [(it.name + it.desc): it] } as TreeMap
        }
        
        ClassInfo(Class<?> node) {
            this.name = node.name.replace('.', '/')
            this.access = node.modifiers
            this.superName = node.superclass?.name?.replace('.', '/')
            this.interfaces = node.interfaces.collect{ it.name.replace('.', '/') }
            this.methods = (node.constructors.collect{ new MethodInfo(this, it) } +
                node.declaredMethods.collect{ new MethodInfo(this, it) })
                .flatten().collectEntries{ [(it.name + it.desc): it] } as TreeMap
        }
        
        def getMethod(def name, def desc) {
            return methods.get(name + desc)
        }
    }
    
    static class MethodInfo {
        def name, desc, access, exceptions, bouncer, override
        private def parent
        
        MethodInfo(ClassInfo parent, MethodNode node){
            this.name = node.name
            this.desc = node.desc
            this.access = node.access
            this.exceptions = node.exceptions.collect()
            this.parent = parent
            
            def bounce = null
            if ((node.access & (ACC_SYNTHETIC | ACC_BRIDGE)) != 0 && (node.access & (ACC_STATIC | ACC_PRIVATE)) == 0) {
                def start = node.instructions.first
                if (start instanceof LabelNode && start.next instanceof LineNumberNode)
                    start = start.next.next
                
                if (start instanceof VarInsnNode) {
                    if (start.var == 0 && start.opcode == ALOAD) {
                        def end = node.instructions.last
                        if (end instanceof LabelNode)
                            end = end.previous
                        
                        if (end.opcode >= IRETURN && end.opcode <= RETURN)
                            end = end.previous
                        
                        if (end instanceof MethodInsnNode) {
                            while (start != end) {
                                if (!(start instanceof VarInsnNode) && start.opcode != INSTANCEOF && start.opcode != CHECKCAST) {
                                    end = null
                                    break
                                    /* We're in a lambda. so lets exit.
                                    System.out.println("Bounce? " + parent.name + "/" + name + desc);
                                    for (AbstractInsnNode asn : node.instructions.toArray())
                                        System.out.println("  " + asn);
                                    */
                                }
                                start = start.next
                            }
                            
                            if (end != null && end.owner.equals(parent.name) &&
                                Type.getArgumentsAndReturnSizes(node.desc) == Type.getArgumentsAndReturnSizes(end.desc))
                                bounce = [name: end.name, desc: end.desc]
                        }
                    }
                }
            }
            this.bouncer = bounce
        }
        
        MethodInfo(ClassInfo parent, Method node) {
            this.name = node.name
            this.desc = Type.getMethodDescriptor(node)
            this.access = node.modifiers
            this.exceptions = node.exceptionTypes.collect{ it.name.replace('.', '/') }
            this.parent = parent
            this.bouncer = null
        }
        
        MethodInfo(ClassInfo parent, Constructor<?> node) {
            this.name = '<init>'
            this.desc = Type.getConstructorDescriptor(node)
            this.access = node.modifiers
            this.exceptions = node.exceptionTypes.collect{ it.name.replace('.', '/') }
            this.parent = parent
            this.bouncer = null
        }
        
        String toString() { return parent.name + '/' + name + desc }
    }
}
