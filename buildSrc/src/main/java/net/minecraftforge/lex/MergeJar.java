package net.minecraftforge.lex;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.Set;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.base.Equivalence;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.*;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SuppressWarnings("unchecked")
public class MergeJar extends DefaultTask
{
    private static final boolean DEBUG = false;
    public static boolean ANNOTATE = true;
    
    @InputFile
    private File client;
    public File getClient(){ return client; }
    public void setClient(File v){ client = v; }
    
    @InputFile
    private File server;
    public File getServer(){ return server; }
    public void setServer(File v){ server = v; }
    
    @Optional
    @InputFile
    private File mappings;
    public File getMappings(){ return mappings; }
    public void setMappings(File v){ mappings = v; }
    
    @OutputFile
    private File output;
    public File getOutput(){ return output; }
    public void setOutput(File v){ output = v; }
    
    @TaskAction
    public void doTask() throws IOException
    {
        Set<String> classes = null;
        if (getMappings() != null)
        {
            classes = new HashSet<>();
            for (String line : Files.readAllLines(getMappings().toPath(), StandardCharsets.UTF_8))
            {
                if (line.indexOf('#') != -1)
                    line = line.substring(0, line.indexOf('#'));
                if (line.trim().isEmpty())
                    continue;
                if (!line.startsWith("\t"))
                    classes.add(line.split(" ")[0]);
            }
        }
        
        processJar(getClient(), getServer(), getOutput(), classes);
    }

    public static void processJar(File clientInFile, File serverInFile, File outFile, Set<String> onlyThese) throws IOException
    {
        ZipFile cInJar = null;
        ZipFile sInJar = null;
        ZipOutputStream outJar = null;

        try
        {
            if (outFile.exists()) outFile.delete();

            try
            {
                cInJar = new ZipFile(clientInFile);
                sInJar = new ZipFile(serverInFile);
            }
            catch (FileNotFoundException e)
            {
                throw new FileNotFoundException("Could not open input file: " + e.getMessage());
            }
            try
            {
                outJar = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outFile)));
            }
            catch (FileNotFoundException e)
            {
                throw new FileNotFoundException("Could not open output file: " + e.getMessage());
            }
            Hashtable<String, ZipEntry> cClasses = getClassEntries(cInJar);
            Hashtable<String, ZipEntry> sClasses = getClassEntries(sInJar);

            for (Entry<String, ZipEntry> entry : cClasses.entrySet())
            {
                String name = entry.getKey();
                ZipEntry cEntry = entry.getValue();
                ZipEntry sEntry = sClasses.get(name);

                if (sEntry == null)
                {
                    if (DEBUG)
                    {
                        System.out.println("Copy class c->s : " + name);
                    }
                    copyClass(cInJar, cEntry, outJar, true);
                    continue;
                }

                if (DEBUG)
                {
                    System.out.println("Processing class: " + name);
                }

                sClasses.remove(name);

                byte[] cData = readEntry(cInJar, entry.getValue());
                byte[] sData = readEntry(sInJar, sEntry);
                byte[] data = processClass(cData, sData);

                ZipEntry newEntry = new ZipEntry(cEntry.getName());
                outJar.putNextEntry(newEntry);
                outJar.write(data);
            }

            for (Entry<String, ZipEntry> entry : sClasses.entrySet())
            {
                if (onlyThese != null && !onlyThese.contains(entry.getKey()))
                {
                    continue;
                }
                if (DEBUG)
                {
                    System.out.println("Copy class s->c : " + entry.getKey());
                }
                copyClass(sInJar, entry.getValue(), outJar, false);
            }

            for (String name : new String[]{SideOnly.class.getName(), Side.class.getName()})
            {
                if (!ANNOTATE) continue;
                String eName = name.replace(".", "/");
                byte[] data = getClassBytes(name);
                ZipEntry newEntry = new ZipEntry(name.replace(".", "/").concat(".class"));
                outJar.putNextEntry(newEntry);
                outJar.write(data);
            }

        }
        finally
        {
            if (cInJar != null)
            {
                try { cInJar.close(); } catch (IOException e){}
            }

            if (sInJar != null)
            {
                try { sInJar.close(); } catch (IOException e) {}
            }
            if (outJar != null)
            {
                try { outJar.close(); } catch (IOException e){}
            }
        }
    }

    private static void copyClass(ZipFile inJar, ZipEntry entry, ZipOutputStream outJar, boolean isClientOnly) throws IOException
    {
        ClassReader reader = new ClassReader(readEntry(inJar, entry));
        ClassNode classNode = new ClassNode();

        reader.accept(classNode, 0);

        if (ANNOTATE/* && !dontAnnotate.contains(classNode.name)*/)
        {
            if (classNode.visibleAnnotations == null) classNode.visibleAnnotations = new ArrayList<AnnotationNode>();
            classNode.visibleAnnotations.add(getSideAnn(isClientOnly));
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        byte[] data = writer.toByteArray();

        ZipEntry newEntry = new ZipEntry(entry.getName());
        if (outJar != null)
        {
            outJar.putNextEntry(newEntry);
            outJar.write(data);
        }
    }

    private static AnnotationNode getSideAnn(boolean isClientOnly)
    {
        AnnotationNode ann = new AnnotationNode(Type.getDescriptor(SideOnly.class));
        ann.values = new ArrayList<Object>();
        ann.values.add("value");
        ann.values.add(new String[]{ Type.getDescriptor(Side.class), (isClientOnly ? "CLIENT" : "SERVER")});
        return ann;
    }

    private static Hashtable<String, ZipEntry> getClassEntries(ZipFile inFile) throws IOException
    {
        Hashtable<String, ZipEntry> ret = new Hashtable<String, ZipEntry>();
        for (ZipEntry entry : Collections.list((Enumeration<ZipEntry>)inFile.entries()))
        {
            String entryName = entry.getName();
            if (!entry.isDirectory() && entryName.endsWith(".class") && !entryName.startsWith("."))
            {
                ret.put(entryName.replace(".class", ""), entry);
            }
        }
        return ret;
    }
    private static byte[] readEntry(ZipFile inFile, ZipEntry entry) throws IOException
    {
        return readFully(inFile.getInputStream(entry));
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
            {
                entryBuffer.write(data, 0, len);
            }
        } while (len != -1);

        return entryBuffer.toByteArray();
    }
    public static byte[] processClass(byte[] cIn, byte[] sIn)
    {
        ClassNode cClassNode = getClassNode(cIn);
        ClassNode sClassNode = getClassNode(sIn);

        processFields(cClassNode, sClassNode);
        processMethods(cClassNode, sClassNode);
        processInners(cClassNode, sClassNode);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cClassNode.accept(writer);
        return writer.toByteArray();
    }

    private static boolean innerMatches(InnerClassNode o, InnerClassNode o2)
    {
        if (o.innerName == null && o2.innerName != null) return false;
        if (o.innerName != null && !o.innerName.equals(o2.innerName)) return false;
        if (o.name == null && o2.name != null) return false;
        if (o.name != null && !o.name.equals(o2.name)) return false;
        if (o.outerName == null && o2.outerName != null) return false;
        if (o.outerName != null && o.outerName.equals(o2.outerName)) return false;
        return true;
    }
    private static boolean contains(List<InnerClassNode> list, InnerClassNode node)
    {
        for (InnerClassNode n : list)
            if (innerMatches(n, node))
                return true;
        return false;
    }
    private static void processInners(ClassNode cClass, ClassNode sClass)
    {
        List<InnerClassNode> cIners = cClass.innerClasses;
        List<InnerClassNode> sIners = sClass.innerClasses;

        for (InnerClassNode n : cIners)
        {
            if (!contains(sIners, n))
                sIners.add(n);
        }
        for (InnerClassNode n : sIners)
        {
            if (!contains(cIners, n))
                cIners.add(n);
        }
    }

    private static ClassNode getClassNode(byte[] data)
    {
        ClassReader reader = new ClassReader(data);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        return classNode;
    }

    private static void processFields(ClassNode cClass, ClassNode sClass)
    {
        List<FieldNode> cFields = cClass.fields;
        List<FieldNode> sFields = sClass.fields;

        merge(cClass.name, sClass.name, cFields, sFields, Equivalence.equals().onResultOf(FieldName.instance), FieldName.instance, FieldName.instance, FieldName.instance);
    }

    private static void processMethods(ClassNode cClass, ClassNode sClass)
    {
        List<MethodNode> cMethods = (List<MethodNode>)cClass.methods;
        List<MethodNode> sMethods = (List<MethodNode>)sClass.methods;

        merge(cClass.name, sClass.name, cMethods, sMethods, Equivalence.equals().onResultOf(MethodDesc.instance), MethodDesc.instance, MethodDesc.instance, MethodDesc.instance);
    }

    private interface MemberAnnotator<T>
    {
        T process(T member, boolean isClient);
    }

    private enum FieldName implements Function<FieldNode, String>, MemberAnnotator<FieldNode>, Comparator<FieldNode>
    {
        instance;

        public String apply(FieldNode in)
        {
            return in == null ? "null" : in.name;
        }

        public FieldNode process(FieldNode field, boolean isClient)
        {
            if (ANNOTATE)
            {
                if (field.visibleAnnotations == null)
                {
                    field.visibleAnnotations = Lists.newArrayList();
                }
                field.visibleAnnotations.add(getSideAnn(isClient));
            }
            return field;
        }

        @Override
        public int compare(FieldNode a, FieldNode b)
        {
            if (a == b) return 0;
            if (a == null) return 1;
            if (b == null) return -1;
            return a.name.compareTo(b.name);
        }
    }

    private enum MethodDesc implements Function<MethodNode, String>, MemberAnnotator<MethodNode>, Comparator<MethodNode>
    {
        instance;
        public String apply(MethodNode node)
        {
            return node == null ? "null" : node.name + node.desc;
        }

        public MethodNode process(MethodNode node, boolean isClient)
        {
            if (ANNOTATE)
            {
                if (node.visibleAnnotations == null)
                {
                    node.visibleAnnotations = Lists.newArrayListWithExpectedSize(1);
                }
                node.visibleAnnotations.add(getSideAnn(isClient));
            }
            return node;
        }

        private int findLine(MethodNode member)
        {
            for (int x = 0; x < member.instructions.size(); x++)
            {
                AbstractInsnNode insn = member.instructions.get(x);
                if (insn instanceof LineNumberNode)
                {
                    return ((LineNumberNode)insn).line;
                }
            }
            return Integer.MAX_VALUE;
        }

        @Override
        public int compare(MethodNode a, MethodNode b)
        {
            if (a == b) return 0;
            if (a == null) return 1;
            if (b == null) return -1;
            return findLine(a) - findLine(b);
        }
    }

    private static <T> void merge(String cName, String sName, List<T> client, List<T> server, Equivalence<? super T> eq,
            MemberAnnotator<T> annotator, Function<T, String> toString, Comparator<T> compare)
    {
        // adding null to the end to not handle the index overflow in a special way
        client.add(null);
        server.add(null);
        List<T> common = Lists.newArrayList();
        for(T ct : client)
        {
            for (T st : server)
            {
                if (eq.equivalent(ct, st))
                {
                    common.add(ct);
                    break;
                }
            }
        }

        int i = 0, mi = 0;
        for(; i < client.size(); i++)
        {
            T ct = client.get(i);
            T st = server.get(i);
            T mt = common.get(mi);

            if (eq.equivalent(ct, st))
            {
                mi++;
                if (!eq.equivalent(ct, mt))
                    throw new IllegalStateException("merged list is in bad state: " + toString.apply(ct) + " " + toString.apply(st) + " " + toString.apply(mt));
                if (DEBUG)
                    System.out.printf("%d/%d %d/%d Both Shared  : %s %s\n", i, client.size(), mi, common.size(), sName, toString.apply(st));

            }
            else if(eq.equivalent(st, mt))
            {
                server.add(i, annotator.process(ct, true));
                if (DEBUG)
                    System.out.printf("%d/%d %d/%d Server *add* : %s %s\n", i, client.size(), mi, common.size(), sName, toString.apply(ct));
            }
            else if (eq.equivalent(ct, mt))
            {
                client.add(i, annotator.process(st, false));
                if (DEBUG)
                    System.out.printf("%d/%d %d/%d Client *add* : %s %s\n", i, client.size(), mi, common.size(), cName, toString.apply(st));
            }
            else // Both server and client add a new method before we get to the next common method... Lets try and prioritize one.
            {
                int diff = compare.compare(ct,  st);
                if  (diff > 0)
                {
                    client.add(i, annotator.process(st, false));
                    if (DEBUG)
                        System.out.printf("%d/%d %d/%d Client *add* : %s %s\n", i, client.size(), mi, common.size(), cName, toString.apply(st));
                }
                else /* if (diff < 0) */ //Technically this should be <0 and we special case when they can't agree who goes first.. but for now just push the client's first.
                {
                    server.add(i, annotator.process(ct, true));
                    if (DEBUG)
                        System.out.printf("%d/%d %d/%d Server *add* : %s %s\n", i, client.size(), mi, common.size(), sName, toString.apply(ct));
                }
            }
        }
        if (i < server.size() || mi < common.size() || (client.size() != server.size()))
        {
            throw new IllegalStateException("merged list is in bad state: " + i + " " + mi);
        }
        // removing the null
        client.remove(client.size() - 1);
        server.remove(server.size() - 1);
    }

    public static byte[] getClassBytes(String name) throws IOException
    {
        InputStream classStream = null;
        try
        {
            classStream = MergeJar.class.getResourceAsStream("/" + name.replace('.', '/').concat(".class"));
            return readFully(classStream);
        }
        finally
        {
            if (classStream != null)
            {
                try
                {
                    classStream.close();
                }
                catch (IOException e){}
            }
        }
    }
}
