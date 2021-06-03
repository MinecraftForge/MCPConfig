package net.minecraftforge.mcpconfig.tasks

import org.gradle.api.tasks.*
import java.util.HashMap
import java.util.HashSet
import java.util.TreeMap
import java.util.LinkedHashMap
import java.util.zip.*
import org.objectweb.asm.*
import groovy.json.JsonBuilder

import groovy.io.FileType
import static groovy.io.FileVisitResult.*

/*
Whelp this is fucked, Java changed to a propritary format so there is no way to grab the API without running the runtime itself and pulling that data from inside it. And I just can't fucking be asked to do that right now.

https://stackoverflow.com/questions/46438557/how-to-extract-the-file-jre-9-lib-modules
*/
public class ExtractAPI extends SingleFileOutput {
    @InputFiles Map<String, File> roots = new HashMap<>()
    @OutputFiles Map<String, File> apis = new HashMap<>()
    
    @TaskAction
    protected void exec() {
        roots.forEach{ name, root ->
            //extract(root, apis.get(name))
        }
    }
    /*
    def extract(root, output) {
        def BAD_ACCESS = Opcodes.ACC_PRIVATE //Filter synthetic?
        def BLACKLIST = ['<clinit>()V']
        
        def api = [] as TreeMap
        
        root.traverse(type: FileType.FILES, filter: 
        
        archive.withInputStream{ ain ->
            def lzma = new LZMAInputStream(ain)
            //def input = new ArchiveStreamFactory().createArchiveInputStream(lzma)
            new ZipInputStream(lzma).withCloseable{ jin ->
                for (def entry = jin.nextEntry; entry != null; entry = jin.nextEntry) {
                    if (!entry.name.startsWith('lib/') || !entry.name.endsWith('.jar'))
                        continue
                    
                    def lin = new ZipInputStream(jin)
                    for (def lentry = lin.nextEntry; lentry != null; lentry = lin.nextEntry) {
                        if (!lentry.name.endsWith('.class'))
                            continue
                        
                        def clsName = null
                        def data = [:] as LinkedHashMap
                        def fields = [:] as TreeMap
                        def methods = [:] as TreeMap
                        
                        def cr = new ClassReader(lin)
                        cr.accept(new ClassVisitor(Opcodes.ASM9) {
                            @Override
                            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                                if ((access & BAD_ACCESS) == 0) {
                                    clsName = name
                                    
                                    if (access != 0)
                                        data['access'] = access
                                    if (signature != null)
                                        data['signature'] = signature
                                    if (!'java/lang/Object'.equals(superName))
                                        data['super'] = superName
                                    if (interfaces != null && interfaces.length != 0)
                                        data['interfaces'] = interfaces
                                }
                            }
                            
                            @Override
                            public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                                if (clsName != null && (access & BAD_ACCESS) == 0) {
                                    def ret = [:]
                                    if (access != 0)
                                        ret['access'] = access
                                    if (signature != null && !signature.equals(desc))
                                        ret['signature'] = signature
                                    fields[name] = ret
                                }
                                return super.visitField(access, name, desc, signature, value)
                            }

                            @Override
                            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                                if (clsName != null && (access & BAD_ACCESS) == 0 && !BLACKLIST.contains(name + desc)) {
                                    def ret = [:]
                                    if (access != 0)
                                        ret['access'] = access
                                    if (signature != null && !signature.equals(desc))
                                        ret['signature'] = signature
                                    methods[name + desc] = ret
                                }
                                return super.visitMethod(access, name, desc, signature, exceptions)
                            }
                        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)
                        
                        if (clsName != null) {
                            if (!fields.isEmpty())
                                data['fields'] = fields
                            if (!methods.isEmpty())
                                data['methods'] = methods
                               
                            api[clsName] = data
                        }
                    }
                }
            }
        }
        
        dest.write(new JsonBuilder(api).toPrettyString())
    }
    */
}