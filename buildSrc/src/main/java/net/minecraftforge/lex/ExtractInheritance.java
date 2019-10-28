package net.minecraftforge.lex;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Constructor;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import static org.objectweb.asm.Opcodes.*;

public class ExtractInheritance extends DefaultTask
{
    private static final Gson GSON = new GsonBuilder().excludeFieldsWithModifiers(Modifier.PRIVATE).setPrettyPrinting().create();
    
    private File input;
    @InputFile
    public File getInput(){ return input; }
    public void setInput(File v){ input = v; }
        
    private File dest;
    @OutputFile
    public File getDest(){ return dest; }
    public void setDest(File v){ dest = v; }
    
    private List<File> libraries = new ArrayList<>();
    public void addLibrary(File lib){ libraries.add(lib); }

    private Map<String, ClassInfo> inClasses = new HashMap<>();
    private Map<String, ClassInfo> libClasses = new HashMap<>();
    
    @TaskAction
    public void doTask() throws IOException
    {
        readJar(getInput(), inClasses);
        for (File lib : libraries)
            readJar(lib, libClasses);
        
        for (Entry<String, ClassInfo> entry : inClasses.entrySet())
            resolveClass(entry.getValue());
        
        Files.write(getDest().toPath(), GSON.toJson(inClasses).getBytes(StandardCharsets.UTF_8));
        
        inClasses = null; //Dump memory so gradle can release it.
        libClasses = null; 
    }

    private void readJar(File input, Map<String, ClassInfo> classes) throws IOException
    {
        ZipFile inJar = null;

        try
        {
            try
            {
                inJar = new ZipFile(input);
            }
            catch (FileNotFoundException e)
            {
                throw new FileNotFoundException("Could not open input file: " + e.getMessage());
            }
            
            for (ZipEntry entry : Collections.list((Enumeration<ZipEntry>)inJar.entries()))
            {
                if (!entry.getName().endsWith(".class") || entry.getName().startsWith("."))
                    continue;
                ClassReader reader = new ClassReader(readFully(inJar.getInputStream(entry)));
                ClassNode classNode = new ClassNode();
                reader.accept(classNode, 0);
                ClassInfo info = new ClassInfo(classNode);
                classes.put(info.name, info);
            }
        }
        finally
        {
            if (inJar != null)
                try { inJar.close(); } catch (IOException e){}
        }
    }
    
    private static byte[] readFully(InputStream stream) throws IOException
    {
        byte[] data = new byte[4096];
        ByteArrayOutputStream entryBuffer = new ByteArrayOutputStream();
        int len;
        do
        {
            len = stream.read(data);
            if (len > 0)
                entryBuffer.write(data, 0, len);
        } while (len != -1);

        return entryBuffer.toByteArray();
    }
    
    private void resolveClass(ClassInfo cls)
    {   
        if (cls == null || cls.resolved)
            return;
        
        if (!cls.name.equals("java/lang/Object") && cls.superName != null)
            resolveClass(getClassInfo(cls.superName));
        
        if (cls.interfaces != null)
            for (String intf : cls.interfaces)
                resolveClass(getClassInfo(intf));
            
        if (cls.methods != null)
        {
            for (MethodInfo mtd : cls.methods.values())
            {
                if ("<init>".equals(mtd.getName()) || "<cinit>".equals(mtd.getName()))
                    continue;
                if ((mtd.access & (ACC_PRIVATE | ACC_STATIC)) != 0)
                    continue;
                
                MethodInfo override = null;
                Queue<ClassInfo> que = new ArrayDeque<>();
                Set<String> processed = new HashSet<>();
                
                if (cls.superName != null)
                    addQueue(cls.superName, processed, que);
                if (cls.interfaces != null)
                    for (String intf : cls.interfaces)
                        addQueue(intf, processed, que);
                    
                while (!que.isEmpty())
                {
                    ClassInfo c = que.poll();
                    if (c.superName != null)
                        addQueue(c.superName, processed, que);
                    if (c.interfaces != null)
                        for (String intf : c.interfaces)
                            addQueue(intf, processed, que);
                        
                    MethodInfo m = c.getMethod(mtd.getName(), mtd.getDesc());
                    
                    int bad_flags = ACC_PRIVATE | ACC_FINAL | ACC_STATIC;
                    if (m == null || (m.access & bad_flags) != 0)
                        continue;
                    
                    override = m;
                }
                
                if (override != null)
                    mtd.override = override.getParent().name;
            }
            /*
            for (MethodInfo mtd : cls.methods)
            {
                if (mtd.override != null)
                {
                    ClassInfo root = getClassInfo(mtd.override);
                    MethodInfo override = root.getMethod(mtd.name, mtd.desc);
                    if (override.exceptions != null)
                    {
                        if (mtd.exceptions == null)
                            mtd.exceptions = new ArrayList<>();
                        for (String exc : override.exceptions)
                        {
                            if (!mtd.exceptions.contains(exc))
                            {
                                System.out.println("Adding ExceptionB: " + exc + " to " + mtd + " from " + override);
                                mtd.exceptions.add(exc);
                            }
                        }
                    }
                }
            }
            
            for (MethodInfo mtd : cls.methods)
            {
                if (mtd.bouncer != null)
                {
                    MethodInfo bounce = cls.getMethod(mtd.bouncer.name, mtd.bouncer.desc);
                    if (mtd.exceptions != null)
                    {
                        if (bounce.exceptions == null)
                            bounce.exceptions = new ArrayList<>();
                        for (String exc : mtd.exceptions)
                        {
                            if (!bounce.exceptions.contains(exc))
                            {
                                System.out.println("Adding ExceptionA: " + exc + " to " + cls.name + "/" + bounce.name + bounce.desc);
                                bounce.exceptions.add(exc);
                            }
                        }
                    }
                }
            }
            */
            
        }
    
        cls.resolved = true;
    }
    
    private void addQueue(String cls, Set<String> visited, Queue<ClassInfo> que)
    {
        if (!visited.contains(cls))
        {
            ClassInfo ci = getClassInfo(cls);
            if (ci != null)
                que.add(ci);
            visited.add(cls);
        }
    }
    
    private Set<String> failed_classes = new HashSet<>();
    private ClassInfo getClassInfo(String name)
    {
        ClassInfo ret = inClasses.get(name);
        if (ret != null)
            return ret;
        ret = libClasses.get(name);
        if (ret == null && !failed_classes.contains(name))
        {
            try
            {
                Class<?> cls = Class.forName(name.replaceAll("/", "."), false, this.getClass().getClassLoader());
                ret = new ClassInfo(cls);
                libClasses.put(name, ret);
            }
            catch (ClassNotFoundException ex)
            {
                //throw new RuntimeException(ex);
                System.out.println("Cant Find Class: " + name);
                failed_classes.add(name);
            }
        }
        return ret;
    }
    
    private static class ClassInfo
    {
        public final String name;
        public final int access;
        public final String superName;
        public final List<String> interfaces;
        //public final List<MethodInfo> methods;
        public final Map<String, MethodInfo> methods;
        
        private boolean resolved = false;
        
        private Map<String, MethodInfo> makeMap(List<MethodInfo> lst)
        {
            if (lst.isEmpty())
                return null;
            Map<String, MethodInfo> ret = new HashMap<>();
            for (MethodInfo info : lst)
                ret.put(info.getName() + " " + info.getDesc(), info);
            return ret;
        }
        
        ClassInfo(ClassNode node)
        {
            this.name = node.name;
            this.access = node.access;
            this.superName = node.superName;
            this.interfaces = node.interfaces.isEmpty() ? null : node.interfaces;
            
            List<MethodInfo> lst = new ArrayList<>();
            if (!node.methods.isEmpty())
                for (MethodNode mn : node.methods)
                    lst.add(new MethodInfo(this, mn));
            this.methods = makeMap(lst);
        }
        
        ClassInfo(Class<?> node)
        {
            this.name = node.getName().replace('.', '/');
            this.access = node.getModifiers();
            this.superName = node.getSuperclass() == null ? null : node.getSuperclass().getName().replace('.', '/');
            List<String> intfs = new ArrayList<>();
            for (Class<?> i : node.getInterfaces())
                intfs.add(i.getName().replace('.', '/'));
            this.interfaces = intfs.isEmpty() ? null : intfs;
            
            List<MethodInfo> mtds = new ArrayList<>();
            
            for (Constructor<?> ctr : node.getConstructors())
                mtds.add(new MethodInfo(this, ctr));
            
            for (Method mtd : node.getDeclaredMethods())
                mtds.add(new MethodInfo(this, mtd));
            
            this.methods = makeMap(mtds);
        }
        
        public MethodInfo getMethod(String name, String desc)
        {
            return methods == null ? null : methods.get(name + " " + desc);
        }
    }
    
    private static class MethodInfo
    {
        private final String name;
        private final String desc;
        public final int access;
        public List<String> exceptions;
        private final ClassInfo parent;
        public final Bouncer bouncer;
        
        public String override = null;        
        
        MethodInfo(ClassInfo parent, MethodNode node)
        {
            this.name = node.name;
            this.desc = node.desc;
            this.access = node.access;
            this.exceptions = node.exceptions.isEmpty() ? null : new ArrayList<>(node.exceptions);
            this.parent = parent;
            Bouncer bounce = null;
            
            if ((node.access & (ACC_SYNTHETIC | ACC_BRIDGE)) != 0 && (node.access & (ACC_STATIC | ACC_PRIVATE)) == 0)
            {
                AbstractInsnNode start = node.instructions.getFirst();
                if (start instanceof LabelNode && start.getNext() instanceof LineNumberNode)
                    start = start.getNext().getNext();
                
                if (start instanceof VarInsnNode)
                {
                    VarInsnNode n = (VarInsnNode)start;
                    if (n.var == 0 && n.getOpcode() == ALOAD)
                    {
                        AbstractInsnNode end = node.instructions.getLast();
                        if (end instanceof LabelNode)
                            end = end.getPrevious();
                        
                        if (end.getOpcode() >= IRETURN && end.getOpcode() <= RETURN)
                            end = end.getPrevious();
                        
                        if (end instanceof MethodInsnNode)
                        {
                            while (start != end)
                            {
                                if (!(start instanceof VarInsnNode) && start.getOpcode() != INSTANCEOF && start.getOpcode() != CHECKCAST)
                                {
                                    end = null;
                                    break;
                                    /* We're in a lambda. so lets exit.
                                    System.out.println("Bounce? " + parent.name + "/" + name + desc);
                                    for (AbstractInsnNode asn : node.instructions.toArray())
                                        System.out.println("  " + asn);
                                    */
                                }
                                start = start.getNext();
                            }
                            
                            MethodInsnNode mtd = (MethodInsnNode)end;
                            if (end != null && mtd.owner.equals(parent.name) &&
                                Type.getArgumentsAndReturnSizes(node.desc) == Type.getArgumentsAndReturnSizes(mtd.desc))
                            {
                                bounce = new Bouncer(mtd.name, mtd.desc);
                            }
                        }
                    }
                }
            }
            this.bouncer = bounce;
        }
        
        MethodInfo(ClassInfo parent, Method node)
        {
            this.name = node.getName();
            this.desc = Type.getMethodDescriptor(node);
            this.access = node.getModifiers();
            List<String> execs = new ArrayList<>();
            for (Class<?> e : node.getExceptionTypes())
                execs.add(e.getName().replace('.', '/'));
            this.exceptions = execs.isEmpty() ? null : execs;
            this.parent = parent;
            this.bouncer = null;
        }
        
        MethodInfo(ClassInfo parent, Constructor<?> node)
        {
            this.name = "<init>";
            this.desc = Type.getConstructorDescriptor(node);
            this.access = node.getModifiers();
            List<String> execs = new ArrayList<>();
            for (Class<?> e : node.getExceptionTypes())
                execs.add(e.getName().replace('.', '/'));
            this.exceptions = execs.isEmpty() ? null : execs;
            this.parent = parent;
            this.bouncer = null;
        }
        
        public ClassInfo getParent()
        {
            return parent;
        }
        
        public String toString()
        {
            return parent.name + "/" + name + desc;
        }
        
        public String getName()
        {
            return name;
        }
        
        public String getDesc()
        {
            return desc;
        }
    }
    
    public static class Bouncer
    {
        public final String name;
        public final String desc;
        public Bouncer(String name, String desc)
        {
            this.name = name;
            this.desc = desc;
        }
    }
}
